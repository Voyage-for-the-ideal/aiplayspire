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
    levels = torch.round(global_features[:, 8] * 20.0).to(torch.long)
    bands = torch.zeros_like(levels)
    bands[(levels >= 1) & (levels <= 5)] = 1
    bands[(levels >= 6) & (levels <= 10)] = 2
    bands[(levels >= 11) & (levels <= 15)] = 3
    bands[(levels >= 16) & (levels <= 19)] = 4
    bands[levels == 20] = 5
    return bands


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


def hazard_predictions(logits, floors):
    """Return hazards, cumulative progress survival, and joint Heart chance."""
    hazard_logits = logits[:, :HAZARD_OUTPUT_DIM]
    heart_conditional = torch.sigmoid(logits[:, HAZARD_OUTPUT_DIM])
    hazards = torch.sigmoid(hazard_logits)
    endpoints = torch.as_tensor(
        HAZARD_ENDPOINTS, dtype=floors.dtype, device=floors.device
    ).unsqueeze(0)
    future = endpoints > floors.unsqueeze(1)
    survival_factors = torch.where(future, 1.0 - hazards, torch.ones_like(hazards))
    survival = torch.cumprod(survival_factors, dim=1)
    heart_probability = survival[:, -1] * heart_conditional
    return hazards, survival, heart_probability


def expected_terminal_floor(survival, floors):
    endpoints = torch.as_tensor(
        HAZARD_ENDPOINTS, dtype=floors.dtype, device=floors.device
    )
    previous = torch.cat([endpoints.new_zeros(1), endpoints[:-1]])
    starts = torch.maximum(previous.unsqueeze(0), floors.unsqueeze(1))
    widths = (endpoints.unsqueeze(0) - starts).clamp_min(0.0)
    return floors + (widths * survival).sum(dim=1)


def weighted_hazard_loss(logits, targets, masks, floors, global_features, band_weights):
    """Risk-set hazard NLL plus calibrated joint Heart-win BCE."""
    hazard_targets = targets[:, :HAZARD_OUTPUT_DIM]
    hazard_masks = masks[:, :HAZARD_OUTPUT_DIM]
    hazard_losses = nn.functional.binary_cross_entropy_with_logits(
        logits[:, :HAZARD_OUTPUT_DIM], hazard_targets, reduction="none"
    )
    sample_weights = band_weights[ascension_band_tensor(global_features)]
    weighted_hazard_masks = hazard_masks * sample_weights.unsqueeze(1)
    hazard_loss = (hazard_losses * weighted_hazard_masks).sum() / (
        weighted_hazard_masks.sum().clamp_min(1.0)
    )

    heart_target = targets[:, HAZARD_OUTPUT_DIM]
    heart_mask = masks[:, HAZARD_OUTPUT_DIM]
    endpoints = torch.as_tensor(
        HAZARD_ENDPOINTS, dtype=floors.dtype, device=floors.device
    ).unsqueeze(0)
    future = endpoints > floors.unsqueeze(1)
    log_survival = torch.where(
        future,
        nn.functional.logsigmoid(-logits[:, :HAZARD_OUTPUT_DIM]),
        torch.zeros_like(logits[:, :HAZARD_OUTPUT_DIM]),
    ).sum(dim=1)
    log_heart_probability = log_survival + nn.functional.logsigmoid(
        logits[:, HAZARD_OUTPUT_DIM]
    )
    negative_heart_nll = -torch.log1p(
        -torch.exp(log_heart_probability).clamp(max=1.0 - 1e-7)
    )
    heart_losses = torch.where(
        heart_target.bool(), -log_heart_probability, negative_heart_nll
    )
    weighted_heart_mask = heart_mask * sample_weights
    heart_loss = (heart_losses * weighted_heart_mask).sum() / (
        weighted_heart_mask.sum().clamp_min(1.0)
    )
    return hazard_loss + Config.HEART_HEAD_LOSS_WEIGHT * heart_loss


def _binary_entropy(rates, *, dtype, device):
    rates = torch.as_tensor(rates, dtype=dtype, device=device)
    if rates.shape != (HAZARD_OUTPUT_DIM + 1,):
        raise ValueError("baseline rates must define every hazard and Heart target")
    if not torch.isfinite(rates).all() or torch.any((rates < 0.0) | (rates > 1.0)):
        raise ValueError("baseline rates must be finite probabilities")
    probabilities = rates.clamp(1e-7, 1.0 - 1e-7)
    entropy = -(
        probabilities * torch.log(probabilities)
        + (1.0 - probabilities) * torch.log1p(-probabilities)
    )
    return entropy.clamp_min(Config.BASELINE_ENTROPY_FLOOR)


def value_loss_components(
    logits,
    targets,
    masks,
    floors,
    global_features,
    band_weights,
    baseline_rates,
    *,
    key_logits=None,
    key_labels=None,
    key_masks=None,
    loss_mode=None,
):
    """Return the training objective and independently inspectable components."""
    mode = Config.VALUE_LOSS_MODE if loss_mode is None else loss_mode
    if mode not in {"macro_relative_nll", "micro_nll"}:
        raise ValueError(f"Unsupported value loss mode: {mode}")

    hazard_targets = targets[:, :HAZARD_OUTPUT_DIM]
    hazard_masks = masks[:, :HAZARD_OUTPUT_DIM]
    hazard_elements = nn.functional.binary_cross_entropy_with_logits(
        logits[:, :HAZARD_OUTPUT_DIM], hazard_targets, reduction="none"
    )
    sample_weights = band_weights[ascension_band_tensor(global_features)]
    weighted_hazard_masks = hazard_masks * sample_weights.unsqueeze(1)
    hazard_numerators = (hazard_elements * weighted_hazard_masks).sum(dim=0)
    hazard_denominators = weighted_hazard_masks.sum(dim=0)
    supervised_heads = hazard_denominators > 0
    if not supervised_heads.any():
        raise ValueError("Batch has no supervised hazard targets")
    per_hazard_nll = hazard_numerators[supervised_heads] / hazard_denominators[
        supervised_heads
    ]
    hazard_micro_nll = hazard_numerators.sum() / hazard_denominators.sum().clamp_min(1.0)
    hazard_macro_nll = per_hazard_nll.mean()

    entropy = _binary_entropy(
        baseline_rates, dtype=logits.dtype, device=logits.device
    )
    hazard_relative_nll = (
        per_hazard_nll / entropy[:HAZARD_OUTPUT_DIM][supervised_heads]
    ).mean()

    heart_target = targets[:, HAZARD_OUTPUT_DIM]
    heart_mask = masks[:, HAZARD_OUTPUT_DIM]
    endpoints = torch.as_tensor(
        HAZARD_ENDPOINTS, dtype=floors.dtype, device=floors.device
    ).unsqueeze(0)
    future = endpoints > floors.unsqueeze(1)
    log_survival = torch.where(
        future,
        nn.functional.logsigmoid(-logits[:, :HAZARD_OUTPUT_DIM]),
        torch.zeros_like(logits[:, :HAZARD_OUTPUT_DIM]),
    ).sum(dim=1)
    log_heart_probability = log_survival + nn.functional.logsigmoid(
        logits[:, HAZARD_OUTPUT_DIM]
    )
    negative_heart_nll = -torch.log1p(
        -torch.exp(log_heart_probability).clamp(max=1.0 - 1e-7)
    )
    heart_elements = torch.where(
        heart_target.bool(), -log_heart_probability, negative_heart_nll
    )
    weighted_heart_mask = heart_mask * sample_weights
    heart_joint_nll = (heart_elements * weighted_heart_mask).sum() / (
        weighted_heart_mask.sum().clamp_min(1.0)
    )
    heart_relative_nll = heart_joint_nll / entropy[HAZARD_OUTPUT_DIM]

    legacy_value = (
        hazard_micro_nll + Config.HEART_HEAD_LOSS_WEIGHT * heart_joint_nll
    )
    relative_value = (
        hazard_relative_nll
        + Config.HEART_HEAD_LOSS_WEIGHT * heart_relative_nll
    ) / (1.0 + Config.HEART_HEAD_LOSS_WEIGHT)
    value = relative_value if mode == "macro_relative_nll" else legacy_value

    key_bce = logits.sum() * 0.0
    if key_logits is not None:
        if key_labels is None or key_masks is None:
            raise ValueError("key labels and masks are required with key logits")
        key_bce = _key_head_loss(key_logits, key_labels, key_masks)
    total = value + Config.KEY_HEAD_LOSS_WEIGHT * key_bce
    return {
        "total": total,
        "value": value,
        "hazard_micro_nll": hazard_micro_nll,
        "hazard_macro_nll": hazard_macro_nll,
        "hazard_relative_nll": hazard_relative_nll,
        "heart_joint_nll": heart_joint_nll,
        "heart_relative_nll": heart_relative_nll,
        "key_bce": key_bce,
        "legacy_value": legacy_value,
        "relative_value": relative_value,
    }


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
        entry.get("contextual_baseline_relative_nll", 1.0) for entry in history
    ]
    learning_rates = [entry["learning_rate"] for entry in history]
    eligible = [
        index for index, entry in enumerate(history)
        if entry.get("checkpoint_eligible", True)
    ]
    best_index = min(eligible, key=lambda index: val_losses[index]) if eligible else None

    figure, axes = plt.subplots(2, 2, figsize=(14, 10))
    loss_axis, component_axis, mae_axis, lr_axis = axes.flat
    loss_axis.plot(epochs, train_losses, marker="o", label="Train Total")
    loss_axis.plot(epochs, train_values, marker="o", label="Train Value")
    loss_axis.plot(epochs, val_losses, marker="o", label="Validation Relative NLL")
    loss_axis.plot(
        epochs, baseline_losses, linestyle="--", label="Contextual Baseline"
    )
    if best_index is not None:
        loss_axis.scatter(
            [epochs[best_index]], [val_losses[best_index]], color="tab:red",
            marker="*", s=160, zorder=3,
            label=f"Best Eligible Checkpoint (Epoch {epochs[best_index]})",
        )
    loss_axis.set(title="Objective History", xlabel="Epoch", ylabel="NLL / Relative NLL")
    loss_axis.legend()
    loss_axis.grid(alpha=0.25)

    component_axis.plot(
        epochs,
        [entry.get("train_hazard_relative_nll", float("nan")) for entry in history],
        marker="o",
        label="Train Hazard Relative NLL",
    )
    component_axis.plot(
        epochs,
        [entry.get("val_hazard_relative_nll", float("nan")) for entry in history],
        marker="o",
        label="Validation Hazard Relative NLL",
    )
    component_axis.plot(
        epochs,
        [entry.get("train_heart_relative_nll", float("nan")) for entry in history],
        marker="o",
        label="Train Heart Relative NLL",
    )
    component_axis.plot(
        epochs,
        [entry.get("val_heart_relative_nll", float("nan")) for entry in history],
        marker="o",
        label="Validation Heart Relative NLL",
    )
    component_axis.plot(
        epochs,
        [entry.get("train_key_bce", float("nan")) for entry in history],
        marker="o",
        label="Train Key BCE",
    )
    component_axis.plot(
        epochs,
        [entry.get("val_key_bce", float("nan")) for entry in history],
        marker="o",
        label="Validation Key BCE",
    )
    component_axis.set(
        title="Objective Components", xlabel="Epoch", ylabel="NLL / BCE"
    )
    component_axis.legend(fontsize="small")
    component_axis.grid(alpha=0.25)

    mae_axis.plot(
        epochs,
        [entry.get("terminal_floor_mae", float("nan")) for entry in history],
        marker="o",
        label="Model Terminal-floor MAE",
    )
    mae_axis.plot(
        epochs,
        [
            entry.get("contextual_baseline_terminal_floor_mae", float("nan"))
            for entry in history
        ],
        linestyle="--",
        label="Contextual Baseline MAE",
    )
    mae_axis.set(title="Terminal-floor MAE", xlabel="Epoch", ylabel="Floors")
    mae_axis.legend()
    mae_axis.grid(alpha=0.25)

    lr_axis.plot(
        epochs, learning_rates, color="green", marker="s", label="Learning Rate"
    )
    lr_axis.set(title="Learning Rate Schedule", xlabel="Epoch", ylabel="Learning Rate")
    lr_axis.legend()
    lr_axis.grid(alpha=0.25)

    figure.tight_layout()
    figure.savefig(output_path, dpi=150)
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
        "num_characters": NUM_CHARACTER_IDS,
        "num_bosses": NUM_BOSS_IDS,
        "dropout": Config.DROPOUT,
        "global_conditioning": Config.GLOBAL_CONDITIONING,
        "norm_position": Config.NORM_POSITION,
        "use_map_context": Config.USE_MAP_CONTEXT,
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
    )


def _split_batch(batch, use_map):
    """Unpack a non-map or V4 map-context dataset batch into common and context parts."""
    if use_map:
        if len(batch) != 17:
            raise ValueError("map-context dataset batches must carry 17 tensors")
        (seq, upgrades, counts, globals_, character_ids, boss_ids, floors, targets, masks,
         keys_features, map_rooms, map_flags,
         history_rooms, history_dist, key_labels, key_masks, outgoing) = batch
        context = (
            keys_features, map_rooms, map_flags, history_rooms, history_dist, outgoing,
        )
        return (seq, upgrades, counts, globals_, character_ids, boss_ids, floors, targets, masks), context, (key_labels, key_masks)
    if len(batch) != 9:
        raise ValueError("non-map dataset batches must carry 9 tensors")
    return tuple(batch), None, None


def _key_head_loss(key_logits, key_labels, key_masks):
    """Masked BCE for the auxiliary key-progress head (0 where unsupervised)."""
    losses = nn.functional.binary_cross_entropy_with_logits(
        key_logits, key_labels, reduction="none"
    )
    weighted = losses * key_masks
    return weighted.sum() / key_masks.sum().clamp_min(1.0)


EGG_RELIC_IDS = ("Molten Egg 2", "Frozen Egg 2", "Toxic Egg 2")


def scalar_state_value(logits, floors):
    """Differentiable counterpart of inference.evaluate_state (unnormalized)."""
    _, survival, heart_probability = hazard_predictions(logits, floors)
    return expected_terminal_floor(survival, floors) + (
        Config.HEART_WIN_BONUS_FLOORS * heart_probability
    )


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
    observed_value = scalar_state_value(observed_logits, floors)
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
                scalar_state_value(without_egg, floors[egg_rows])
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
                    scalar_state_value(without_key, floors[key_rows])
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


def _nll_metrics(
    probabilities, targets, masks, baseline_rates, sample_weights=None
):
    probabilities = np.clip(np.asarray(probabilities, dtype=np.float64), 1e-7, 1.0 - 1e-7)
    targets = np.asarray(targets, dtype=np.float64)
    masks = np.asarray(masks).astype(bool)
    if sample_weights is None:
        sample_weights = np.ones(targets.shape[0], dtype=np.float64)
    sample_weights = np.asarray(sample_weights, dtype=np.float64)
    if sample_weights.shape != (targets.shape[0],):
        raise ValueError("sample weights must define every evaluation sample")
    if not np.isfinite(sample_weights).all() or (sample_weights <= 0.0).any():
        raise ValueError("sample weights must be finite and greater than zero")
    entropy = _numpy_entropy(baseline_rates)
    elements = -(
        targets * np.log(probabilities)
        + (1.0 - targets) * np.log1p(-probabilities)
    )
    hazard_nlls = []
    hazard_relative = []
    for index in range(HAZARD_OUTPUT_DIM):
        include = masks[:, index]
        value = (
            float(
                np.sum(elements[include, index] * sample_weights[include])
                / np.sum(sample_weights[include])
            )
            if include.any()
            else float("nan")
        )
        hazard_nlls.append(value)
        hazard_relative.append(float(value / entropy[index]))
    hazard_mask = masks[:, :HAZARD_OUTPUT_DIM]
    weighted_hazard_mask = hazard_mask * sample_weights[:, None]
    hazard_micro = float(
        np.sum(elements[:, :HAZARD_OUTPUT_DIM] * weighted_hazard_mask)
        / np.sum(weighted_hazard_mask)
    )
    hazard_macro = float(np.nanmean(hazard_nlls))
    hazard_relative_nll = float(np.nanmean(hazard_relative))
    heart_include = masks[:, HAZARD_OUTPUT_DIM]
    heart_nll = float(
        np.sum(
            elements[heart_include, HAZARD_OUTPUT_DIM]
            * sample_weights[heart_include]
        )
        / np.sum(sample_weights[heart_include])
    )
    heart_relative_nll = float(heart_nll / entropy[HAZARD_OUTPUT_DIM])
    relative_value = float(
        hazard_relative_nll
        + Config.HEART_HEAD_LOSS_WEIGHT * heart_relative_nll
    ) / float(1.0 + Config.HEART_HEAD_LOSS_WEIGHT)
    legacy_value = float(
        hazard_micro + Config.HEART_HEAD_LOSS_WEIGHT * heart_nll
    )
    return {
        "hazard_micro_nll": hazard_micro,
        "hazard_macro_nll": hazard_macro,
        "hazard_relative_nll": hazard_relative_nll,
        "hazard_nll_by_target": hazard_nlls,
        "hazard_relative_nll_by_target": hazard_relative,
        "heart_joint_nll": heart_nll,
        "heart_relative_nll": heart_relative_nll,
        "relative_value_nll": relative_value,
        "legacy_value_nll": legacy_value,
    }


def training_objective_metadata(baseline_rates, baseline_statistics, selection=None):
    metadata = {
        "schema_version": "macro-relative-nll-v1",
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


def _contextual_baseline_probabilities(baseline_statistics, floors, bands):
    if baseline_statistics.get("schema_version") != CONTEXTUAL_BASELINE_SCHEMA:
        raise ValueError("Contextual baseline schema is incompatible")
    global_rates = np.asarray(
        baseline_statistics["global"]["rates"], dtype=np.float64
    )
    if global_rates.shape != (HAZARD_OUTPUT_DIM + 1,):
        raise ValueError("Contextual baseline global rates are incomplete")
    predictions = np.broadcast_to(global_rates, (len(floors), len(global_rates))).copy()
    strata = baseline_statistics.get("strata", {})
    for row, (floor, band) in enumerate(zip(floors, bands)):
        key = f"{int(round(float(floor)))}:{int(band)}"
        entry = strata.get(key)
        if entry is not None:
            rates = np.asarray(entry["rates"], dtype=np.float64)
            if rates.shape != global_rates.shape:
                raise ValueError(f"Contextual baseline stratum {key} is incomplete")
            predictions[row] = rates
    return predictions


def _baseline_summary(
    probabilities,
    targets,
    masks,
    floors,
    realized_floors,
    rates,
    sample_weights=None,
):
    values = _nll_metrics(
        probabilities, targets, masks, rates, sample_weights=sample_weights
    )
    future = np.asarray(HAZARD_ENDPOINTS)[None, :] > floors[:, None]
    survival = np.cumprod(
        np.where(future, 1.0 - probabilities[:, :HAZARD_OUTPUT_DIM], 1.0), axis=1
    )
    previous = np.concatenate(([0.0], np.asarray(HAZARD_ENDPOINTS[:-1])))
    widths = np.maximum(
        np.asarray(HAZARD_ENDPOINTS)[None, :]
        - np.maximum(previous[None, :], floors[:, None]),
        0.0,
    )
    predicted_floors = floors + (widths * survival).sum(axis=1)
    terminal = masks[:, HAZARD_OUTPUT_DIM].astype(bool)
    values["terminal_floor_mae"] = float(
        np.abs(predicted_floors[terminal] - realized_floors[terminal]).mean()
    )
    return values


def evaluate(
    model,
    loader,
    device,
    progress=None,
    name="Evaluation",
    baseline_rates=None,
    baseline_statistics=None,
    band_weights=None,
    use_map=False,
    recorder=None,
    egg_token_ids=(),
):
    model.eval()
    targets = []
    masks = []
    hazard_probabilities = []
    heart_probabilities = []
    predicted_floors = []
    realized_floors = []
    snapshot_floors = []
    ascension_bands = []
    ascension_levels = []
    character_values = []
    key_probabilities = []
    key_targets = []
    key_target_masks = []
    started = time.monotonic()
    last_progress = started
    with torch.no_grad():
        for batch_index, batch in enumerate(loader, start=1):
            (seq, upgrades, counts, globals_, character_ids, boss_ids, floors,
             batch_targets, batch_masks), context, key_batch = _split_batch(batch, use_map)
            seq, upgrades, counts, globals_, character_ids, boss_ids, floors, batch_targets, batch_masks = (
                value.to(device, non_blocking=device.type == "cuda")
                for value in (
                    seq, upgrades, counts, globals_, character_ids, boss_ids, floors,
                    batch_targets, batch_masks,
                )
            )
            if use_map:
                (keys_features, map_rooms, map_flags, history_rooms, history_dist, outgoing) = (
                    value.to(device, non_blocking=device.type == "cuda")
                    for value in context
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
            else:
                logits = model(seq, upgrades, counts, globals_, character_ids, boss_ids)
            if use_map:
                key_probabilities.append(torch.sigmoid(key_logits).cpu().numpy())
                key_targets.append(key_labels.cpu().numpy())
                key_target_masks.append(key_masks.cpu().numpy())
            hazards, survival, heart_probability = hazard_predictions(logits, floors)
            character_values.append(character_ids.cpu().numpy())
            if recorder is not None:
                try:
                    from .evaluation_v4 import mechanism_counts
                    from .experiments import selected_run
                except ImportError:
                    from evaluation_v4 import mechanism_counts
                    from experiments import selected_run
                probe_eligible = torch.tensor(
                    [selected_run(rid, 0.01) for rid, _, _ in recorder.identities(len(seq))],
                    device=device, dtype=torch.bool,
                )
                device_ctx = tuple(value.to(device) for value in context) if use_map else None
                pc, pv = mechanism_counts(
                    model, logits, seq, upgrades, counts, globals_, character_ids, boss_ids,
                    floors, device_ctx, key_labels if use_map else None,
                    key_masks if use_map else None, egg_token_ids, probe_eligible,
                )
                probs = torch.cat([hazards, heart_probability[:, None]], dim=1).cpu().numpy()
                batch_bands = ascension_band_tensor(globals_).cpu().numpy()
                weights = np.ones(len(seq)) if band_weights is None else np.asarray(band_weights)[batch_bands]
                recorder.add(probs, batch_targets.cpu().numpy(), batch_masks.cpu().numpy(), weights, pc, pv)
            future = torch.as_tensor(
                HAZARD_ENDPOINTS, dtype=floors.dtype, device=device
            ).unsqueeze(0) > floors.unsqueeze(1)
            realized_factors = torch.where(
                future,
                1.0 - batch_targets[:, :HAZARD_OUTPUT_DIM],
                torch.ones_like(batch_targets[:, :HAZARD_OUTPUT_DIM]),
            )
            realized_survival = torch.cumprod(realized_factors, dim=1)
            hazard_probabilities.append(hazards.cpu().numpy())
            heart_probabilities.append(heart_probability.cpu().numpy())
            predicted_floors.append(
                expected_terminal_floor(survival, floors).cpu().numpy()
            )
            realized_floors.append(
                expected_terminal_floor(realized_survival, floors).cpu().numpy()
            )
            snapshot_floors.append(floors.cpu().numpy())
            targets.append(batch_targets.cpu().numpy())
            masks.append(batch_masks.cpu().numpy())
            ascension_bands.append(
                ascension_band_tensor(globals_).cpu().numpy().reshape(-1)
            )
            ascension_levels.append(
                torch.round(globals_[:, 8] * 20.0).to(torch.long).cpu().numpy().reshape(-1)
            )
            now = time.monotonic()
            if progress and (
                batch_index == 1
                or now - last_progress >= Config.LOG_INTERVAL_SECONDS
                or batch_index == len(loader)
            ):
                elapsed = max(now - started, 1e-9)
                progress(
                    f"{name}: {batch_index:,}/{len(loader):,} batches "
                    f"({batch_index / len(loader):.1%}), "
                    f"rate={batch_index / elapsed:.1f} batches/s"
                )
                last_progress = now
    if not targets:
        raise ValueError("Evaluation split has no valid samples")
    targets_array = np.concatenate(targets)
    hazards_array = np.concatenate(hazard_probabilities)
    heart_array = np.concatenate(heart_probabilities)
    masks_array = np.concatenate(masks).astype(bool)
    bands_array = np.concatenate(ascension_bands)
    ascension_array = np.concatenate(ascension_levels)
    if band_weights is None:
        evaluation_weights = np.ones(len(bands_array), dtype=np.float64)
    else:
        raw_weights = np.asarray(band_weights, dtype=np.float64)
        if raw_weights.shape != (len(ASCENSION_BAND_NAMES),):
            raise ValueError("band weights must define every ascension band")
        evaluation_weights = raw_weights[bands_array]
    floors_array = np.concatenate(snapshot_floors).astype(np.float64)
    realized_floors_array = np.concatenate(realized_floors).astype(np.float64)
    if baseline_rates is None:
        baseline_rates = np.full(HAZARD_OUTPUT_DIM + 1, 0.5, dtype=np.float64)
    baseline_rates = np.asarray(baseline_rates, dtype=np.float64)
    joint_probabilities = np.concatenate(
        [hazards_array, heart_array.reshape(-1, 1)], axis=1
    )
    value_metrics = _nll_metrics(
        joint_probabilities,
        targets_array,
        masks_array,
        baseline_rates,
        sample_weights=evaluation_weights,
    )
    metrics = dict(value_metrics)
    metrics["hazards"] = {}
    for index, component in enumerate(HAZARD_NAMES):
        include = masks_array[:, index]
        component_metrics = _classification_metrics(
            targets_array[include, index], hazards_array[include, index]
        )
        component_metrics["positive_rate"] = float(targets_array[include, index].mean())
        component_metrics["nll"] = value_metrics["hazard_nll_by_target"][index]
        component_metrics["relative_nll"] = value_metrics[
            "hazard_relative_nll_by_target"
        ][index]
        component_metrics["samples"] = int(include.sum())
        metrics["hazards"][component] = component_metrics
    heart_include = masks_array[:, HAZARD_OUTPUT_DIM]
    metrics["heart_win"] = _classification_metrics(
        targets_array[heart_include, HAZARD_OUTPUT_DIM], heart_array[heart_include]
    )
    metrics["heart_win"]["positive_rate"] = float(
        targets_array[heart_include, HAZARD_OUTPUT_DIM].mean()
    )
    metrics["heart_win"]["nll"] = value_metrics["heart_joint_nll"]
    metrics["heart_win"]["relative_nll"] = value_metrics["heart_relative_nll"]
    metrics["heart_win"]["samples"] = int(heart_include.sum())
    predicted_floors_array = np.concatenate(predicted_floors)
    metrics["terminal_floor_mae"] = float(
        np.abs(predicted_floors_array[heart_include] - realized_floors_array[heart_include]).mean()
    )
    if use_map:
        key_array = np.concatenate(key_probabilities)
        key_labels_array = np.concatenate(key_targets)
        key_masks_array = np.concatenate(key_target_masks).astype(bool)
        key_elements = -(
            key_labels_array * np.log(np.clip(key_array, 1e-7, 1.0 - 1e-7))
            + (1.0 - key_labels_array)
            * np.log1p(-np.clip(key_array, 1e-7, 1.0 - 1e-7))
        )
        metrics["key_bce"] = (
            float(key_elements[key_masks_array].mean())
            if key_masks_array.any()
            else 0.0
        )
        metrics["keys"] = {}
        for index, key_name in enumerate(("ruby", "emerald", "sapphire")):
            include = key_masks_array[:, index]
            values = _classification_metrics(
                key_labels_array[include, index], key_array[include, index]
            )
            values["positive_rate"] = (
                float(key_labels_array[include, index].mean())
                if include.any()
                else float("nan")
            )
            values["nll"] = (
                float(key_elements[include, index].mean())
                if include.any()
                else float("nan")
            )
            values["samples"] = int(include.sum())
            metrics["keys"][key_name] = values
    else:
        metrics["key_bce"] = 0.0
        metrics["keys"] = {}
    selected_value = (
        metrics["relative_value_nll"]
        if Config.VALUE_LOSS_MODE == "macro_relative_nll"
        else metrics["legacy_value_nll"]
    )
    metrics["value"] = selected_value
    metrics["total"] = selected_value + Config.KEY_HEAD_LOSS_WEIGHT * metrics["key_bce"]
    metrics["loss"] = metrics["total"]
    if baseline_rates is not None:
        global_predictions = np.broadcast_to(
            baseline_rates, joint_probabilities.shape
        ).copy()
        metrics["constant_baseline"] = _baseline_summary(
            global_predictions,
            targets_array,
            masks_array,
            floors_array,
            realized_floors_array,
            baseline_rates,
            sample_weights=evaluation_weights,
        )
    if baseline_statistics is not None:
        contextual_predictions = _contextual_baseline_probabilities(
            baseline_statistics, floors_array, bands_array
        )
        metrics["contextual_baseline"] = _baseline_summary(
            contextual_predictions,
            targets_array,
            masks_array,
            floors_array,
            realized_floors_array,
            baseline_rates,
            sample_weights=evaluation_weights,
        )
        baseline_score = metrics["contextual_baseline"]["relative_value_nll"]
        metrics["skill"] = float(
            1.0 - metrics["relative_value_nll"] / baseline_score
        )
    def grouped_classification_metrics(include):
        group_metrics = {}
        for index, component in enumerate(HAZARD_NAMES):
            component_include = include & masks_array[:, index]
            values = _classification_metrics(
                targets_array[component_include, index],
                hazards_array[component_include, index],
            )
            values["samples"] = int(component_include.sum())
            group_metrics[component] = values
        heart_group_include = include & heart_include
        heart_values = _classification_metrics(
            targets_array[heart_group_include, HAZARD_OUTPUT_DIM],
            heart_array[heart_group_include],
        )
        heart_values["samples"] = int(heart_group_include.sum())
        group_metrics["heart_win"] = heart_values
        group_metrics["terminal_floor_mae"] = float(np.abs(
            predicted_floors_array[heart_group_include] - realized_floors_array[heart_group_include]
        ).mean()) if heart_group_include.any() else float("nan")
        return group_metrics

    characters_array = np.concatenate(character_values)
    metrics["by_character"] = {
        str(int(character)): grouped_classification_metrics(characters_array == character)
        for character in np.unique(characters_array)
    }
    metrics["by_ascension_band"] = {}
    for band, name in enumerate(ASCENSION_BAND_NAMES):
        include = bands_array == band
        if include.any():
            metrics["by_ascension_band"][name] = grouped_classification_metrics(include)
    metrics["by_ascension"] = {
        str(level): {
            "samples": int((ascension_array == level).sum()),
            **grouped_classification_metrics(ascension_array == level),
        }
        for level in sorted(np.unique(ascension_array).tolist())
    }
    return metrics


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
    baseline_rates = baseline_statistics["global"]["rates"]
    if Config.VALUE_LOSS_MODE not in {"macro_relative_nll", "micro_nll"}:
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
            "key_bce", "monotonic",
        )}
        running_samples = 0
        for batch_index, batch in enumerate(loaders["train"], start=1):
            (seq, upgrades, counts, globals_, character_ids, boss_ids, floors, targets, masks), context, key_batch = (
                _split_batch(batch, Config.USE_MAP_CONTEXT)
            )
            seq, upgrades, counts, globals_, character_ids, boss_ids, floors, targets, masks = (
                value.to(device, non_blocking=device.type == "cuda")
                for value in (
                    seq, upgrades, counts, globals_, character_ids, boss_ids, floors, targets, masks,
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
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
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
    parser.add_argument("--checkpoint", help="Explicit format-11 checkpoint for read-only evaluation")
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
