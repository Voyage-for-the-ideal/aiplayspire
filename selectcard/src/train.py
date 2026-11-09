import math
import json
from pathlib import Path
import os
import random
import time

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score
from torch.utils.data import DataLoader

try:
    from .checkpointing import load_checkpoint, save_checkpoint
    from .config import Config
    from .data_contract import (
        ASCENSION_BAND_NAMES,
        FILTER_VERSION,
        HAZARD_ENDPOINTS,
        HAZARD_NAMES,
        HAZARD_OUTPUT_DIM,
    )
    from .dataset import (
        ChunkShuffleSampler,
        CONTEXTUAL_BASELINE_SCHEMA,
        STSDataset,
        build_training_artifacts,
        load_dataset_manifest,
    )
    from .model import STSValueNetwork
except ImportError:
    from checkpointing import load_checkpoint, save_checkpoint
    from config import Config
    from data_contract import (
        ASCENSION_BAND_NAMES,
        FILTER_VERSION,
        HAZARD_ENDPOINTS,
        HAZARD_NAMES,
        HAZARD_OUTPUT_DIM,
    )
    from dataset import (
        ChunkShuffleSampler,
        CONTEXTUAL_BASELINE_SCHEMA,
        STSDataset,
        build_training_artifacts,
        load_dataset_manifest,
    )
    from model import STSValueNetwork


def set_seed(seed):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def dataloader_worker_count():
    override = os.environ.get("STS_DATALOADER_WORKERS")
    if override is not None:
        try:
            workers = int(override)
        except ValueError as exc:
            raise ValueError("STS_DATALOADER_WORKERS must be an integer") from exc
        if workers < 0:
            raise ValueError("STS_DATALOADER_WORKERS must be non-negative")
        return workers

    raw_cpus = os.environ.get("SLURM_CPUS_PER_TASK")
    if raw_cpus is None:
        available_cpus = os.cpu_count() or 1
    else:
        try:
            available_cpus = int(raw_cpus)
        except ValueError as exc:
            raise ValueError("SLURM_CPUS_PER_TASK must be an integer") from exc
        if available_cpus <= 0:
            raise ValueError("SLURM_CPUS_PER_TASK must be positive")
    return min(
        max(available_cpus - 1, 0), Config.MAX_AUTO_DATALOADER_WORKERS
    )


def expected_calibration_error(labels, probabilities, bins=10):
    labels = np.asarray(labels)
    probabilities = np.asarray(probabilities)
    edges = np.linspace(0.0, 1.0, bins + 1)
    score = 0.0
    for lower, upper in zip(edges[:-1], edges[1:]):
        include = (probabilities >= lower) & (probabilities < upper)
        if upper == 1.0:
            include |= probabilities == 1.0
        if include.any():
            score += include.mean() * abs(
                probabilities[include].mean() - labels[include].mean()
            )
    return float(score)


def ascension_band_tensor(global_features):
    try:
        from .value_loss import band_tensor
    except ImportError:
        from value_loss import band_tensor
    return band_tensor(global_features)


def difficulty_weights(manifest):
    try:
        raw_counts = manifest["distributions"][
            "train_valid_samples_by_ascension_band"
        ]
        counts = [int(raw_counts.get(str(index), 0)) for index in range(6)]
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError("Dataset manifest difficulty counts are invalid") from exc
    if any(count < 0 for count in counts):
        raise ValueError(f"Ascension band sample counts cannot be negative: {counts}")
    active_count = sum(count > 0 for count in counts)
    if active_count == 0:
        raise ValueError("The training split has no valid samples in any ascension band")
    total = sum(counts)
    return [total / (active_count * count) if count else 0.0 for count in counts]


def constant_hazard_baseline(manifest):
    """Build calibrated constant rates from training risk-set frequencies."""
    distributions = manifest.get("distributions", {})
    positives = distributions.get("positive_samples_by_target", {})
    valid = distributions.get("valid_samples_by_target", {})
    names = (*HAZARD_NAMES, "heart_win")
    rates = []
    for name in names:
        denominator = int(valid.get(name, 0))
        numerator = int(positives.get(name, 0))
        if denominator <= 0 or not 0 <= numerator <= denominator:
            raise ValueError(f"Invalid training baseline counts for {name}")
        rates.append(numerator / denominator)
    return rates


def hazard_predictions(outputs, floors):
    try:
        from .multiscale import distribution
    except ImportError:
        from multiscale import distribution
    d = distribution(outputs, floors, Config.RESOLUTION_MODE)
    return torch.sigmoid(outputs.hazards), d["survival"], d["heart_probability"]


try:
    from .value_loss import value_loss_components
except ImportError:
    from value_loss import value_loss_components


def weighted_hazard_loss(*args, **kwargs):
    kwargs["loss_mode"] = "micro_nll"
    return value_loss_components(*args, **kwargs)["legacy_value"]


def _binary_entropy(rates, *, dtype, device):
    p = torch.as_tensor(rates, dtype=dtype, device=device)
    if not torch.isfinite(p).all() or ((p < 0) | (p > 1)).any():
        raise ValueError("baseline rates must be finite probabilities")
    p = p.clamp(1e-7, 1-1e-7)
    return (-(p*p.log()+(1-p)*(1-p).log())).clamp_min(Config.BASELINE_ENTROPY_FLOOR)


def _contextual_baseline_probabilities(statistics, floors, bands):
    if statistics.get("schema_version") != "floor-ascension-band-v1":
        raise ValueError("Contextual baseline schema is incompatible")
    rates = statistics["global"]["rates"]
    return np.asarray([statistics.get("strata", {}).get(f"{int(f)}:{int(b)}", {}).get("rates", rates)
                       for f, b in zip(floors, bands)], dtype=float)


def save_training_report(history, output_path):
    """Save epoch-level loss and learning-rate history as a PNG report."""
    if not history:
        return

    try:
        import matplotlib
    except ImportError:
        # matplotlib is a plotting-only dependency; a server without it must
        # not block the final test evaluation.  History stays in the log.
        print(
            "WARNING: matplotlib is unavailable; skipping training report PNG. "
            "Epoch loss history remains in the training log.",
            flush=True,
        )
        return

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    epochs = [entry["epoch"] for entry in history]
    train_losses = [entry.get("train_total", entry.get("train_loss")) for entry in history]
    train_values = [entry.get("train_value", entry.get("train_loss")) for entry in history]
    val_losses = [
        entry.get("val_relative_value_nll", entry.get("val_loss")) for entry in history
    ]
    baseline_losses = [
        entry.get("contextual_baseline_relative_nll", float("nan"))
        for entry in history
    ]
    learning_rates = [entry["learning_rate"] for entry in history]
    eligible = [
        index for index, entry in enumerate(history)
        if entry.get("checkpoint_eligible", True)
    ]
    best_index = min(eligible, key=lambda index: val_losses[index]) if eligible else None

    def finite_values(values):
        return [
            float(value)
            for value in values
            if value is not None and np.isfinite(value)
        ]

    def focus_y_axis(axis, *series):
        values = finite_values(value for line in series for value in line)
        if not values:
            return
        low, high = min(values), max(values)
        span = high - low
        padding = max(span * 0.18, abs(low) * 0.015, 0.01)
        axis.set_ylim(low - padding, high + padding)

    figure, axes = plt.subplots(3, 2, figsize=(16, 14), sharex=True)
    objective_axis, hazard_axis, heart_axis, key_axis, mae_axis, lr_axis = axes.flat

    objective_axis.plot(epochs, train_losses, marker="o", label="Train Total")
    objective_axis.plot(epochs, train_values, marker="o", label="Train Value")
    objective_axis.plot(
        epochs, val_losses, marker="o", label="Validation Common Milestone NLL"
    )
    if best_index is not None:
        objective_axis.scatter(
            [epochs[best_index]], [val_losses[best_index]], color="tab:red",
            marker="*", s=180, zorder=3,
            label=f"Best Eligible Checkpoint (Epoch {epochs[best_index]})",
        )
    focus_y_axis(objective_axis, train_losses, train_values, val_losses)
    baseline_value = next(
        (
            value
            for value in reversed(baseline_losses)
            if value is not None and np.isfinite(value)
        ),
        None,
    )
    if baseline_value is not None:
        objective_axis.text(
            0.02, 0.02, f"Contextual baseline NLL: {baseline_value:.3f}",
            transform=objective_axis.transAxes, fontsize="small",
            bbox={"facecolor": "white", "alpha": 0.8, "edgecolor": "0.8"},
        )
    objective_axis.set(title="Training Objective / Validation Common NLL", ylabel="Objective / Common NLL")
    objective_axis.legend(fontsize="small")

    component_specs = (
        (
            hazard_axis,
            "Near Risk-cell NLL",
            "train_near_nll",
            "val_near_nll",
        ),
        (
            heart_axis,
            "Heart Relative NLL",
            "train_heart_relative_nll",
            "val_heart_relative_nll",
        ),
        (key_axis, "Key BCE", "train_key_bce", "val_key_bce"),
    )
    for axis, title, train_key, val_key in component_specs:
        train_series = [entry.get(train_key, float("nan")) for entry in history]
        val_series = [entry.get(val_key, float("nan")) for entry in history]
        axis.plot(epochs, train_series, marker="o", label="Train")
        axis.plot(epochs, val_series, marker="o", label="Validation")
        focus_y_axis(axis, train_series, val_series)
        axis.set(title=title, ylabel="Relative NLL" if "Relative" in title else "NLL" if "NLL" in title else "BCE")
        axis.legend(fontsize="small")

    far_train = [entry.get("train_far_nll", float("nan")) for entry in history]
    far_val = [entry.get("val_far_nll", float("nan")) for entry in history]
    if finite_values(far_train+far_val):
        hazard_axis.plot(epochs, far_train, marker="s", label="Train Far")
        hazard_axis.plot(epochs, far_val, marker="s", label="Validation Far")
        hazard_axis.set_title("Near/Far Risk-cell NLL")
        focus_y_axis(hazard_axis, [entry.get("train_near_nll") for entry in history],
                     [entry.get("val_near_nll") for entry in history], far_train, far_val)
        hazard_axis.legend(fontsize="small")

    model_mae = [entry.get("terminal_floor_mae", float("nan")) for entry in history]
    mae_axis.plot(epochs, model_mae, marker="o", label="Model")
    focus_y_axis(mae_axis, model_mae)
    baseline_mae = next(
        (
            entry.get("contextual_baseline_terminal_floor_mae")
            for entry in reversed(history)
            if entry.get("contextual_baseline_terminal_floor_mae") is not None
            and np.isfinite(entry["contextual_baseline_terminal_floor_mae"])
        ),
        None,
    )
    if baseline_mae is not None:
        mae_axis.text(
            0.02, 0.02, f"Contextual baseline MAE: {baseline_mae:.2f} floors",
            transform=mae_axis.transAxes, fontsize="small",
            bbox={"facecolor": "white", "alpha": 0.8, "edgecolor": "0.8"},
        )
    mae_axis.set(title="Terminal-floor MAE (focused scale)", ylabel="Floors")
    mae_axis.legend(fontsize="small")

    lr_axis.plot(
        epochs, learning_rates, color="green", marker="s", label="Learning Rate"
    )
    lr_axis.ticklabel_format(axis="y", style="sci", scilimits=(0, 0))
    lr_axis.set(title="Learning Rate Schedule", ylabel="Learning rate")
    lr_axis.legend(fontsize="small")

    for axis in axes.flat:
        axis.set_xlabel("Epoch")
        axis.grid(alpha=0.25)
        axis.margins(x=0.06)

    figure.suptitle("Training Metrics by Epoch", fontsize=16)
    figure.tight_layout(rect=(0, 0, 1, 0.98))
    figure.savefig(output_path, dpi=200)
    plt.close(figure)


def _classification_metrics(labels, probabilities):
    labels = np.asarray(labels)
    probabilities = np.asarray(probabilities)
    if labels.size == 0:
        return {name: float("nan") for name in ("roc_auc", "pr_auc", "brier", "ece")}
    metrics = {
        "brier": float(brier_score_loss(labels, probabilities)),
        "ece": expected_calibration_error(labels, probabilities),
    }
    if np.unique(labels).size < 2:
        metrics.update({"roc_auc": float("nan"), "pr_auc": float("nan")})
    else:
        metrics.update({
            "roc_auc": float(roc_auc_score(labels, probabilities)),
            "pr_auc": float(average_precision_score(labels, probabilities)),
        })
    return metrics


def model_config(vocab_size):
    try:
        from .boss_context import NUM_BOSS_IDS
        from .character_context import NUM_CHARACTER_IDS
    except ImportError:
        from boss_context import NUM_BOSS_IDS
        from character_context import NUM_CHARACTER_IDS
    return {
        "vocab_size": vocab_size,
        "max_upgrade": Config.MAX_UPGRADE,
        "max_count": Config.MAX_COUNT,
        "d_model": Config.D_MODEL,
        "n_heads": Config.N_HEADS,
        "n_layers": Config.N_LAYERS,
        "num_global_features": Config.NUM_GLOBAL_FEATURES,
        "resolution_mode": Config.RESOLUTION_MODE,
        "num_characters": NUM_CHARACTER_IDS,
        "num_bosses": NUM_BOSS_IDS,
        "dropout": Config.DROPOUT,
        "global_conditioning": Config.GLOBAL_CONDITIONING,
        "norm_position": Config.NORM_POSITION,
        "use_map_context": Config.USE_MAP_CONTEXT,
        "ablate_map_history": Config.ABLATE_MAP_HISTORY,
        "num_key_features": 9,
        "ffn_type": Config.FFN_TYPE,
        "norm_type": Config.NORM_TYPE,
        "map_encoder": Config.MAP_ENCODER,
        "map_latents": Config.MAP_LATENTS,
        "coordinate_embedding": Config.COORDINATE_EMBEDDING,
        "pooling": Config.POOLING,
        "register_tokens": Config.REGISTER_TOKENS,
        "item_token_budget": Config.MAX_ITEM_TOKENS if Config.USE_MAP_CONTEXT else Config.MAX_SEQ_LEN,
    }


def make_dataset(split, vocabulary, feature_encoder, progress=None):
    return STSDataset(
        Config.DATA_DIR,
        vocabulary,
        feature_encoder,
        split=split,
        max_seq_len=Config.MAX_ITEM_TOKENS if Config.USE_MAP_CONTEXT else Config.MAX_SEQ_LEN,
        max_upgrade=Config.MAX_UPGRADE,
        max_count=Config.MAX_COUNT,
        progress=progress,
        use_map_context=Config.USE_MAP_CONTEXT,
        run_fraction=Config.RUN_FRACTION,
        resolution_mode=Config.RESOLUTION_MODE,
    )


def _split_batch(batch, use_map):
    expected = 18 if use_map else 10
    if len(batch) != expected:
        raise ValueError(f"Format-12 batches must carry {expected} tensors")
    base = tuple(batch[:9]) + (batch[-1],)
    if not use_map:
        return base, None, None
    keys, rooms, flags, history, distance, labels, masks, outgoing = batch[9:-1]
    return base, (keys, rooms, flags, history, distance, outgoing), (labels, masks)


def _key_head_loss(key_logits, key_labels, key_masks):
    """Masked BCE for the auxiliary key-progress head (0 where unsupervised)."""
    losses = nn.functional.binary_cross_entropy_with_logits(
        key_logits, key_labels, reduction="none"
    )
    weighted = losses * key_masks
    return weighted.sum() / key_masks.sum().clamp_min(1.0)


EGG_RELIC_IDS = ("Molten Egg 2", "Frozen Egg 2", "Toxic Egg 2")


def scalar_state_value(outputs, floors, resolution=None):
    try:
        from .multiscale import distribution
    except ImportError:
        from multiscale import distribution
    d = distribution(outputs, floors, resolution or Config.RESOLUTION_MODE)
    return d["expected_floor"] + Config.HEART_WIN_BONUS_FLOORS * d["heart_probability"]


def _forward_value_model(
    model, seq, upgrades, counts, globals_, character_ids, boss_ids, context=None
):
    if context is None:
        return model(seq, upgrades, counts, globals_, character_ids, boss_ids)
    keys_features, map_rooms, map_flags, history_rooms, history_dist = context[:5]
    outgoing = context[5] if len(context) > 5 else None
    return model(
        seq, upgrades, counts, globals_, character_ids, boss_ids,
        keys_features=keys_features, map_rooms=map_rooms, map_flags=map_flags,
        history_rooms=history_rooms, history_dist=history_dist, map_outgoing_mask=outgoing,
    )


def monotonic_counterfactual_loss(
    model,
    observed_logits,
    seq,
    upgrades,
    counts,
    globals_,
    character_ids,
    boss_ids,
    floors,
    egg_token_ids,
    *,
    context=None,
    key_labels=None,
    key_masks=None,
):
    """Zero-margin loss for free Eggs and known collected keys.

    Each comparison removes one observed Egg or one observed, explicitly known
    key from the otherwise identical state.  Synthetic states are used only
    for ordering; terminal labels remain attached solely to observed states.
    """
    resolution = getattr(model, "model_config", {}).get("resolution_mode", Config.RESOLUTION_MODE)
    observed_value = scalar_state_value(observed_logits, floors, resolution)
    terms = []

    for token_id in egg_token_ids:
        matches = seq.eq(int(token_id))
        egg_rows = matches.any(dim=1)
        if not egg_rows.any():
            continue
        rows = egg_rows.nonzero(as_tuple=False).squeeze(1)
        positions = matches[rows].to(torch.int64).argmax(dim=1)
        altered_seq = seq[egg_rows].clone()
        altered_upgrades = upgrades[egg_rows].clone()
        altered_counts = counts[egg_rows].clone()
        local_rows = torch.arange(len(rows), device=seq.device)
        altered_seq[local_rows, positions] = 0
        altered_upgrades[local_rows, positions] = 0
        altered_counts[local_rows, positions] = 0
        subset_context = (
            tuple(value[egg_rows] for value in context) if context is not None else None
        )
        without_egg = _forward_value_model(
            model,
            altered_seq, altered_upgrades, altered_counts,
            globals_[egg_rows], character_ids[egg_rows], boss_ids[egg_rows],
            subset_context,
        )
        terms.append(
            nn.functional.relu(
                scalar_state_value(without_egg, floors[egg_rows], resolution)
                - observed_value[egg_rows]
            )
        )

    if context is not None and key_labels is not None and key_masks is not None:
        eligible = key_labels.gt(0.5) & key_masks.gt(0.5)
        for key_index in range(eligible.shape[1]):
            key_rows = eligible[:, key_index]
            if not key_rows.any():
                continue
            altered_keys = context[0][key_rows].clone()
            altered_keys[:, key_index] = 0.0
            # Features 3:6 are the known-mask bits; feature 7 is the number of
            # known collected keys.  Preserve observability while clearing value.
            altered_keys[:, 7] = (
                altered_keys[:, :3] * altered_keys[:, 3:6]
            ).sum(dim=1) / 3.0
            # Clearing Emerald makes the reconstructed burning elite pending.
            if key_index == 1:
                burning = context[2][key_rows].bitwise_and(2).ne(0).any(dim=1)
                altered_keys[:, 6] = burning.to(altered_keys.dtype)
            without_key_context = (
                altered_keys,
                *(value[key_rows] for value in context[1:]),
            )
            without_key = _forward_value_model(
                model,
                seq[key_rows], upgrades[key_rows], counts[key_rows], globals_[key_rows],
                character_ids[key_rows], boss_ids[key_rows], without_key_context,
            )
            terms.append(
                nn.functional.relu(
                    scalar_state_value(without_key, floors[key_rows], resolution)
                    - observed_value[key_rows]
                )
            )

    if not terms:
        return observed_logits.sum() * 0.0
    return torch.cat(terms).mean()


def _numpy_entropy(rates):
    probabilities = np.clip(np.asarray(rates, dtype=np.float64), 1e-7, 1.0 - 1e-7)
    entropy = -(
        probabilities * np.log(probabilities)
        + (1.0 - probabilities) * np.log1p(-probabilities)
    )
    return np.maximum(entropy, Config.BASELINE_ENTROPY_FLOOR)


def _nll_metrics(probabilities, targets, masks, baseline_rates, sample_weights=None):
    try:
        from .value_metrics import nll_metrics
    except ImportError:
        from value_metrics import nll_metrics
    return nll_metrics(probabilities, targets, masks, baseline_rates, sample_weights)


def training_objective_metadata(baseline_rates, baseline_statistics, selection=None):
    metadata = {
        "schema_version": "multiscale-loss-v1",
        "resolution_mode": Config.RESOLUTION_MODE,
        "far_loss_weight": Config.FAR_LOSS_WEIGHT,
        "mean_loss_weight": Config.MEAN_LOSS_WEIGHT,
        "common_score_heart_weight": 1.0,
        "mode": Config.VALUE_LOSS_MODE,
        "baseline_entropy_floor": Config.BASELINE_ENTROPY_FLOOR,
        "heart_loss_weight": Config.HEART_HEAD_LOSS_WEIGHT,
        "key_loss_weight": Config.KEY_HEAD_LOSS_WEIGHT,
        "monotonic_loss_weight": Config.MONOTONIC_LOSS_WEIGHT,
        "monotonic_constraint": "zero-margin-observed-egg-and-known-key-v1",
        "global_baseline_rates": [float(value) for value in baseline_rates],
        "contextual_baseline_schema": baseline_statistics["schema_version"],
        "contextual_baseline_prior_strength": baseline_statistics["prior_strength"],
    }
    if selection is not None:
        metadata["selection"] = dict(selection)
    return metadata


def checkpoint_eligibility(use_map_context, warmup, model_mae, baseline_mae):
    """Return deployment-checkpoint eligibility and a stable rejection reason."""
    if use_map_context and warmup:
        return False, "warm-up context"
    if not math.isfinite(model_mae) or not math.isfinite(baseline_mae):
        return False, "non-finite terminal-floor MAE"
    if model_mae > baseline_mae:
        return False, "terminal-floor MAE guard"
    return True, None


def configure_context_curriculum(datasets, epoch, use_map_context, curriculum_epochs):
    """Keep train/validation context aligned while test remains full-context."""
    warmup = bool(use_map_context and epoch < curriculum_epochs)
    if use_map_context:
        datasets["train"].context_enabled = not warmup
        datasets["val"].context_enabled = not warmup
        if "test" in datasets:
            datasets["test"].context_enabled = True
    return warmup


def evaluate(*args, **kwargs):
    try:
        from .value_metrics import evaluate as evaluate_distribution
    except ImportError:
        from value_metrics import evaluate as evaluate_distribution
    return evaluate_distribution(*args, **kwargs)


def finish_training(
    model,
    loaders,
    device,
    log,
    best_path,
    final_path,
    manifest,
    raw_band_weights,
    architecture,
    vocabulary,
    feature_encoder,
    baseline_rates,
    baseline_statistics,
    history,
):
    """Publish the selected training checkpoint without touching held-out test."""
    report_path = os.path.join(Config.CHECKPOINT_DIR, Config.TRAINING_REPORT_NAME)
    save_training_report(history, report_path)
    best_checkpoint, _, _ = load_checkpoint(best_path, map_location=device)
    model.load_state_dict(best_checkpoint["model_state_dict"])
    metadata = dict(best_checkpoint.get("metadata", {}))
    metadata["history"] = history
    metadata["held_out_test_evaluated"] = False
    save_checkpoint(final_path, model, architecture, vocabulary, feature_encoder, metadata)
    try:
        from .experiments import write_json
    except ImportError:
        from experiments import write_json
    write_json(Path(Config.CHECKPOINT_DIR) / "training_metrics.json", {
        "history": history, "parameters": sum(p.numel() for p in model.parameters()),
        "architecture": model.model_config, "device": str(device),
        "torch_version": str(torch.__version__), "experiment": Config.EXPERIMENT_METADATA,
        "validation_metrics": metadata.get("validation_metrics"),
    })
    log(f"Saved selected checkpoint to {final_path}; held-out test not evaluated")


def train_model(test_only=False):
    if test_only:
        raise ValueError("Use --evaluate --checkpoint PATH for read-only evaluation")
    started = time.monotonic()

    def log(message):
        elapsed = time.monotonic() - started
        print(
            f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] "
            f"[+{elapsed / 60:.1f}m] {message}",
            flush=True,
        )

    log("Starting training")
    set_seed(Config.RANDOM_SEED)
    selected_device = getattr(Config, "DEVICE", "auto")
    if selected_device == "auto":
        selected_device = "cuda" if torch.cuda.is_available() else "cpu"
    if selected_device == "cuda" and not torch.cuda.is_available():
        raise ValueError("CUDA requested but not available")
    device = torch.device(selected_device)
    if device.type == "cuda":
        log(
            f"Device: {torch.cuda.get_device_name(0)}; "
            f"PyTorch {torch.__version__}; CUDA {torch.version.cuda}"
        )
    else:
        log(f"WARNING: CUDA is unavailable; using CPU with PyTorch {torch.__version__}")
    log(
        f"SLURM_CPUS_PER_TASK={os.environ.get('SLURM_CPUS_PER_TASK', 'unset')}; "
        f"batch_size={Config.BATCH_SIZE}; epochs={Config.EPOCHS}"
    )

    manifest = load_dataset_manifest(Config.DATA_DIR)
    distributions = manifest.get("distributions", {})
    valid_samples = distributions.get("valid_samples_by_split", {})
    log(
        f"Dataset: train={int(valid_samples.get('train', 0)):,}, "
        f"val={int(valid_samples.get('val', 0)):,}, "
        f"test={int(valid_samples.get('test', 0)):,} valid samples"
    )
    vocabulary, feature_encoder, baseline_statistics = build_training_artifacts(
        Config.DATA_DIR, progress=log, include_baselines=True
    )
    egg_token_ids = [
        vocabulary.item2id[name]
        for name in EGG_RELIC_IDS
        if name in vocabulary.item2id
    ]
    log(f"Training artifacts ready; vocabulary_size={len(vocabulary):,}")
    datasets = {
        split: make_dataset(split, vocabulary, feature_encoder, progress=log)
        for split in ("train", "val")
    }
    for split, dataset in datasets.items():
        if not dataset:
            raise ValueError(f"The {split} split has no valid samples")

    generator = torch.Generator().manual_seed(Config.RANDOM_SEED)
    worker_count = dataloader_worker_count()
    loader_options = {
        "batch_size": Config.BATCH_SIZE,
        "num_workers": worker_count,
        "pin_memory": device.type == "cuda",
    }
    if worker_count > 0:
        loader_options["prefetch_factor"] = Config.DATALOADER_PREFETCH_FACTOR
    log(
        f"DataLoader: workers={worker_count}, "
        f"pin_memory={loader_options['pin_memory']}, "
        f"prefetch_factor={loader_options.get('prefetch_factor', 'disabled')}"
    )
    loaders = {
        "train": DataLoader(
            datasets["train"],
            sampler=ChunkShuffleSampler(datasets["train"], generator=generator),
            **loader_options,
        ),
        "val": DataLoader(datasets["val"], **loader_options),
    }
    log(
        "Data loaders ready: "
        + ", ".join(
            f"{split}={len(loader):,} batches" for split, loader in loaders.items()
        )
    )
    architecture = model_config(len(vocabulary))
    model = STSValueNetwork(**architecture).to(device)
    log(
        f"Model initialized with "
        f"{sum(p.numel() for p in model.parameters()):,} parameters"
    )
    optimizer = optim.AdamW(model.parameters(), lr=Config.LEARNING_RATE, weight_decay=0.01)
    decay_epochs = Config.COSINE_DECAY_EPOCHS or Config.EPOCHS
    min_lr_ratio = Config.LR_MIN_RATIO
    if not isinstance(decay_epochs, int) or decay_epochs <= 0:
        raise ValueError("COSINE_DECAY_EPOCHS must be a positive integer or None")
    if not 0.0 <= min_lr_ratio <= 1.0:
        raise ValueError("LR_MIN_RATIO must be between 0 and 1")

    def cosine_decay(epoch):
        progress = min(epoch, decay_epochs) / decay_epochs
        return min_lr_ratio + (1.0 - min_lr_ratio) * 0.5 * (
            1.0 + math.cos(math.pi * progress)
        )

    scheduler = optim.lr_scheduler.LambdaLR(optimizer, lr_lambda=cosine_decay)
    log(
        f"Learning rate: base={Config.LEARNING_RATE}; "
        f"cosine_decay_epochs={decay_epochs}; min_ratio={min_lr_ratio}"
    )
    raw_band_weights = difficulty_weights(manifest)
    band_weights = torch.tensor(raw_band_weights, dtype=torch.float32, device=device)
    baseline_rates = baseline_statistics["resolution_statistics"][Config.RESOLUTION_MODE]["rates"]
    if Config.VALUE_LOSS_MODE not in {"macro_relative_nll", "micro_nll", "multiscale"}:
        raise ValueError(f"Unsupported VALUE_LOSS_MODE: {Config.VALUE_LOSS_MODE}")
    if not 0.0 < Config.BASELINE_ENTROPY_FLOOR:
        raise ValueError("BASELINE_ENTROPY_FLOOR must be greater than zero")
    if Config.MONOTONIC_LOSS_WEIGHT < 0.0:
        raise ValueError("MONOTONIC_LOSS_WEIGHT must be non-negative")
    log(
        f"Training objective: mode={Config.VALUE_LOSS_MODE}; "
        f"entropy_floor={Config.BASELINE_ENTROPY_FLOOR}; "
        f"monotonic_weight={Config.MONOTONIC_LOSS_WEIGHT}; "
        f"contextual_baseline={baseline_statistics['schema_version']}"
    )

    os.makedirs(Config.CHECKPOINT_DIR, exist_ok=True)
    best_path = os.path.join(Config.CHECKPOINT_DIR, "best_" + Config.CHECKPOINT_NAME)
    final_path = os.path.join(Config.CHECKPOINT_DIR, Config.CHECKPOINT_NAME)
    best_val_score = float("inf")
    best_checkpoint_saved = False
    patience_counter = 0
    history = []

    for epoch in range(Config.EPOCHS):
        learning_rate = optimizer.param_groups[0]["lr"]
        epoch_started = time.monotonic()
        last_progress = epoch_started
        if device.type == "cuda":
            torch.cuda.reset_peak_memory_stats()
        warmup = configure_context_curriculum(
            datasets,
            epoch,
            Config.USE_MAP_CONTEXT,
            Config.CONTEXT_CURRICULUM_EPOCHS,
        )
        if Config.USE_MAP_CONTEXT:
            # Curriculum: mask map/history context for warm-up epochs.
            log(
                f"Epoch {epoch + 1}/{Config.EPOCHS}: context="
                f"{'masked' if warmup else 'full'}; "
                f"checkpoint_eligible={'false' if warmup else 'pending_mae_guard'}"
            )
        log(f"Epoch {epoch + 1}/{Config.EPOCHS}: training started")
        model.train()
        running = {name: 0.0 for name in (
            "total", "value", "hazard_micro_nll", "hazard_macro_nll",
            "hazard_relative_nll", "heart_joint_nll", "heart_relative_nll",
            "key_bce", "monotonic", "near_nll", "far_nll", "mean_loss",
        )}
        running_samples = 0
        coverage = dict(near_coverage=0, far_coverage=0, mean_coverage=0, heart_coverage=0)
        for batch_index, batch in enumerate(loaders["train"], start=1):
            (seq, upgrades, counts, globals_, character_ids, boss_ids, floors, targets, masks, terminal_floors), context, key_batch = (
                _split_batch(batch, Config.USE_MAP_CONTEXT)
            )
            seq, upgrades, counts, globals_, character_ids, boss_ids, floors, targets, masks, terminal_floors = (
                value.to(device, non_blocking=device.type == "cuda")
                for value in (
                    seq, upgrades, counts, globals_, character_ids, boss_ids, floors, targets, masks, terminal_floors,
                )
            )
            optimizer.zero_grad()
            device_context = None
            if Config.USE_MAP_CONTEXT:
                (keys_features, map_rooms, map_flags, history_rooms, history_dist, outgoing) = (
                    value.to(device, non_blocking=device.type == "cuda")
                    for value in context
                )
                device_context = (
                    keys_features, map_rooms, map_flags, history_rooms, history_dist, outgoing
                )
                key_labels, key_masks = (
                    value.to(device, non_blocking=device.type == "cuda")
                    for value in key_batch
                )
                logits, key_logits = model(
                    seq, upgrades, counts, globals_, character_ids, boss_ids,
                    keys_features=keys_features, map_rooms=map_rooms,
                    map_flags=map_flags, history_rooms=history_rooms,
                    history_dist=history_dist, map_outgoing_mask=outgoing, return_keys=True,
                )
                components = value_loss_components(
                    logits,
                    targets,
                    masks,
                    floors,
                    globals_,
                    band_weights,
                    baseline_rates,
                    terminal_floors=terminal_floors,
                    key_logits=key_logits,
                    key_labels=key_labels,
                    key_masks=key_masks,
                )
            else:
                logits = model(seq, upgrades, counts, globals_, character_ids, boss_ids)
                components = value_loss_components(
                    logits,
                    targets,
                    masks,
                    floors,
                    globals_,
                    band_weights,
                    baseline_rates,
                    terminal_floors=terminal_floors,
                )
                key_labels = key_masks = None
            monotonic = monotonic_counterfactual_loss(
                model,
                logits,
                seq,
                upgrades,
                counts,
                globals_,
                character_ids,
                boss_ids,
                floors,
                egg_token_ids,
                context=device_context,
                key_labels=key_labels,
                key_masks=key_masks,
            )
            components["monotonic"] = monotonic
            components["total"] = (
                components["total"] + Config.MONOTONIC_LOSS_WEIGHT * monotonic
            )
            loss = components["total"]
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0, error_if_nonfinite=True)
            optimizer.step()
            for field in coverage:
                coverage[field] += int(components[field].item())
            batch_samples = targets.shape[0]
            running_samples += batch_samples
            for component in running:
                running[component] += components[component].item() * batch_samples
            now = time.monotonic()
            if (
                batch_index == 1
                or now - last_progress >= Config.LOG_INTERVAL_SECONDS
                or batch_index == len(loaders["train"])
            ):
                epoch_elapsed = max(now - epoch_started, 1e-9)
                gpu_memory = ""
                if device.type == "cuda":
                    peak_gib = torch.cuda.max_memory_allocated() / (1024**3)
                    gpu_memory = f", peak_gpu_memory={peak_gib:.2f} GiB"
                log(
                    f"Epoch {epoch + 1}/{Config.EPOCHS}: "
                    f"{batch_index:,}/{len(loaders['train']):,} batches "
                    f"({batch_index / len(loaders['train']):.1%}), "
                    f"total={running['total'] / running_samples:.4f}, "
                    f"value={running['value'] / running_samples:.4f}, "
                    f"rate={batch_index / epoch_elapsed:.1f} batches/s"
                    f"{gpu_memory}"
                )
                last_progress = now
        scheduler.step()
        training_seconds = max(time.monotonic() - epoch_started, 1e-9)
        training_peak = torch.cuda.max_memory_allocated() if device.type == "cuda" else None

        log(f"Epoch {epoch + 1}/{Config.EPOCHS}: validation started")
        val_metrics = evaluate(
            model,
            loaders["val"],
            device,
            progress=log,
            name=f"Epoch {epoch + 1}/{Config.EPOCHS} validation",
            baseline_rates=baseline_rates,
            baseline_statistics=baseline_statistics,
            band_weights=raw_band_weights,
            use_map=Config.USE_MAP_CONTEXT,
        )
        train_metrics = {
            name: value / running_samples for name, value in running.items()
        }
        log(f"Epoch {epoch+1} supervision coverage: {coverage}")
        contextual_mae = val_metrics["contextual_baseline"]["terminal_floor_mae"]
        context_eligible = not (Config.USE_MAP_CONTEXT and warmup)
        checkpoint_eligible, rejection_reason = checkpoint_eligibility(
            Config.USE_MAP_CONTEXT,
            warmup,
            val_metrics["terminal_floor_mae"],
            contextual_mae,
        )
        mae_guard = val_metrics["terminal_floor_mae"] <= contextual_mae
        selection_score = val_metrics["relative_value_nll"]
        log(
            f"Epoch {epoch + 1}/{Config.EPOCHS}: "
            f"train_total={train_metrics['total']:.4f} "
            f"train_value={train_metrics['value']:.4f} "
            f"train_hazard_rel={train_metrics['hazard_relative_nll']:.4f} "
            f"train_heart_rel={train_metrics['heart_relative_nll']:.4f} "
            f"train_key_bce={train_metrics['key_bce']:.4f} "
            f"val_relative_nll={selection_score:.4f} "
            f"val_hazard_rel={val_metrics['hazard_relative_nll']:.4f} "
            f"val_heart_rel={val_metrics['heart_relative_nll']:.4f} "
            f"val_key_bce={val_metrics['key_bce']:.4f} "
            f"contextual_baseline={val_metrics['contextual_baseline']['relative_value_nll']:.4f} "
            f"skill={val_metrics['skill']:.4f} "
            f"terminal_floor_mae={val_metrics['terminal_floor_mae']:.4f} "
            f"baseline_mae={contextual_mae:.4f} "
            f"checkpoint_eligible={str(checkpoint_eligible).lower()}"
        )
        metadata = {
            "epoch": epoch + 1,
            "experiment": Config.EXPERIMENT_METADATA,
            "baseline_statistics": baseline_statistics,
            "seed": Config.RANDOM_SEED,
            "split_seed": Config.SPLIT_SEED,
            "validation_metrics": val_metrics,
            "training_objective": training_objective_metadata(
                baseline_rates,
                baseline_statistics,
                selection={
                    "score": selection_score,
                    "skill": val_metrics["skill"],
                    "terminal_floor_mae": val_metrics["terminal_floor_mae"],
                    "contextual_baseline_terminal_floor_mae": contextual_mae,
                    "mae_guard_passed": mae_guard,
                    "context_eligible": context_eligible,
                    "checkpoint_eligible": checkpoint_eligible,
                },
            ),
            "dataset": {
                "selected_samples": {name: len(ds) for name, ds in datasets.items()},
                "filter_version": FILTER_VERSION,
                "selection": manifest.get("selection", {}),
                "runs_by_ascension": manifest.get("distributions", {}).get(
                    "runs_by_ascension", {}
                ),
                "samples_by_ascension": manifest.get("distributions", {}).get(
                    "samples_by_ascension", {}
                ),
                "content_catalog": manifest["content_catalog"],
                "train_valid_samples_by_ascension_band": manifest[
                    "distributions"
                ]["train_valid_samples_by_ascension_band"],
                "difficulty_weights": raw_band_weights,
            },
            "training_config": {
                "data_dir": os.path.basename(os.path.normpath(Config.DATA_DIR)),
                "epochs": Config.EPOCHS,
                "batch_size": Config.BATCH_SIZE,
                "learning_rate": Config.LEARNING_RATE,
                "d_model": Config.D_MODEL,
                "n_heads": Config.N_HEADS,
                "n_layers": Config.N_LAYERS,
                "use_map_context": Config.USE_MAP_CONTEXT,
                "run_fraction": Config.RUN_FRACTION,
                "architecture": architecture,
            },
        }
        is_best = checkpoint_eligible and selection_score < best_val_score
        history.append(
            {
                "epoch": epoch + 1,
                "train_total": train_metrics["total"],
                "train_value": train_metrics["value"],
                "train_hazard_relative_nll": train_metrics[
                    "hazard_relative_nll"
                ],
                "train_heart_relative_nll": train_metrics[
                    "heart_relative_nll"
                ],
                "train_key_bce": train_metrics["key_bce"],
                "val_relative_value_nll": selection_score,
                "contextual_baseline_relative_nll": val_metrics[
                    "contextual_baseline"
                ]["relative_value_nll"],
                "val_hazard_relative_nll": val_metrics["hazard_relative_nll"],
                "val_heart_relative_nll": val_metrics["heart_relative_nll"],
                "val_key_bce": val_metrics["key_bce"],
                "terminal_floor_mae": val_metrics["terminal_floor_mae"],
                "contextual_baseline_terminal_floor_mae": contextual_mae,
                "checkpoint_eligible": checkpoint_eligible,
                "learning_rate": learning_rate,
                "train_samples_per_second": running_samples / training_seconds,
                "peak_gpu_memory_bytes": training_peak,
                "coverage": dict(coverage),
                "train_near_nll": train_metrics["near_nll"],
                "train_far_nll": train_metrics["far_nll"],
                "train_mean_loss": train_metrics["mean_loss"],
                "val_near_nll": val_metrics["near_nll"],
                "val_far_nll": val_metrics["far_nll"],
            }
        )
        if is_best:
            best_val_score = selection_score
            best_checkpoint_saved = True
            patience_counter = 0
            save_checkpoint(
                best_path, model, architecture, vocabulary, feature_encoder, metadata
            )
            log(
                f"Saved new best checkpoint to {best_path}; "
                f"selection_score={selection_score:.4f}"
            )
        elif not checkpoint_eligible:
            log(f"Checkpoint rejected: {rejection_reason}")
        elif not is_best:
            log(
                "Checkpoint not selected: eligible but validation relative NLL "
                f"did not improve best={best_val_score:.4f}"
            )
        if (checkpoint_eligible and not is_best and Config.EARLY_STOPPING_PATIENCE > 0
                and epoch + 1 > Config.EARLY_STOPPING_START_EPOCH):
            patience_counter += 1
            if patience_counter >= Config.EARLY_STOPPING_PATIENCE:
                log(
                    f"Early stopping after {patience_counter} epochs without improvement"
                )
                break

    if not best_checkpoint_saved:
        diagnostic_metadata = {
            "seed": Config.RANDOM_SEED,
            "training_objective": training_objective_metadata(
                baseline_rates,
                baseline_statistics,
                selection={"checkpoint_eligible": False, "reason": "no eligible epoch"},
            ),
            "diagnostic_only": True,
        }
        save_checkpoint(
            final_path,
            model,
            architecture,
            vocabulary,
            feature_encoder,
            diagnostic_metadata,
        )
        raise RuntimeError(
            "No full-context epoch passed the contextual-baseline terminal-floor "
            f"MAE guard; saved diagnostic checkpoint to {final_path}"
        )

    finish_training(
        model,
        loaders,
        device,
        log,
        best_path,
        final_path,
        manifest,
        raw_band_weights,
        architecture,
        vocabulary,
        feature_encoder,
        baseline_rates,
        baseline_statistics,
        history,
    )


def main(argv=None):
    import argparse
    try:
        from .experiments import EXPERIMENTS, configure_experiment, write_json
        from .evaluation_v4 import evaluate_checkpoint
    except ImportError:
        from experiments import EXPERIMENTS, configure_experiment, write_json
        from evaluation_v4 import evaluate_checkpoint
    parser = argparse.ArgumentParser(description="V4 training and read-only checkpoint evaluation")
    parser.add_argument("--list-experiments", action="store_true")
    parser.add_argument("--experiment", default="baseline", help="Registry name(s), comma-separated")
    parser.add_argument("--stage", choices=("pilot", "confirm"), default="pilot")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--epochs", type=int)
    parser.add_argument("--data-dir", default=Config.DATA_DIR)
    parser.add_argument("--output-dir")
    parser.add_argument("--evaluate", "--test-only", dest="evaluate", action="store_true")
    parser.add_argument("--checkpoint", help="Explicit format-12 checkpoint for read-only evaluation")
    parser.add_argument("--evaluation-split", choices=("val", "test"), default="val")
    parser.add_argument("--device", choices=("auto", "cpu", "cuda"), default="auto")
    args = parser.parse_args(argv)
    if args.list_experiments:
        print(json.dumps(EXPERIMENTS, indent=2))
        return
    if args.evaluate:
        if not args.checkpoint or not args.output_dir:
            parser.error("--evaluate requires --checkpoint and a separate --output-dir")
        return evaluate_checkpoint(args.checkpoint, args.data_dir, args.output_dir,
                                   args.evaluation_split, args.device)
    if args.checkpoint:
        parser.error("--checkpoint is only valid with --evaluate")
    if args.stage == "confirm" and args.seed not in (42, 43, 44):
        parser.error("Confirmation seeds are 42, 43, 44")
    if args.epochs is not None and args.epochs <= 0:
        parser.error("--epochs must be positive")
    experiment = configure_experiment(args.experiment, args.stage, args.seed, args.epochs)
    output = Path(args.output_dir) if args.output_dir else Path(Config.BASE_DIR) / "checkpoints" / "v4" / args.experiment.replace(",", "_") / args.stage / str(args.seed)
    output.mkdir(parents=True, exist_ok=False)
    Config.CHECKPOINT_DIR = str(output)
    Config.DATA_DIR = args.data_dir
    Config.DEVICE = args.device
    write_json(output / "experiment.json", experiment)
    try:
        train_model()
    except torch.cuda.OutOfMemoryError as exc:
        write_json(output / "status.json", {"status": "cuda_oom", "error": str(exc), "batch_size": 512})
        raise
    except Exception as exc:
        write_json(output / "status.json", {"status": "failed", "error": str(exc)})
        raise
    write_json(output / "status.json", {"status": "complete", "held_out_test_evaluated": False})


if __name__ == "__main__":
    main()
