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
    from .data_contract import ASCENSION_BAND_NAMES, FILTER_VERSION
    from .dataset import (
        ChunkShuffleSampler,
        STSDataset,
        build_training_artifacts,
        load_dataset_manifest,
    )
    from .model import STSValueNetwork
except ImportError:
    from checkpointing import load_checkpoint, save_checkpoint
    from config import Config
    from data_contract import ASCENSION_BAND_NAMES, FILTER_VERSION
    from dataset import (
        ChunkShuffleSampler,
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
    if any(count <= 0 for count in counts):
        raise ValueError(f"Every ascension band needs training samples; got {counts}")
    total = sum(counts)
    return [total / (len(counts) * count) for count in counts]


def weighted_bce_loss(logits, labels, global_features, band_weights):
    losses = nn.functional.binary_cross_entropy_with_logits(
        logits, labels, reduction="none"
    )
    sample_weights = band_weights[ascension_band_tensor(global_features)].unsqueeze(1)
    return (losses * sample_weights).mean()


def _classification_metrics(labels, probabilities):
    labels = np.asarray(labels)
    probabilities = np.asarray(probabilities)
    metrics = {
        "brier": float(brier_score_loss(labels, probabilities)),
        "ece": expected_calibration_error(labels, probabilities),
        "pr_auc": (
            float(average_precision_score(labels, probabilities))
            if np.any(labels == 1)
            else float("nan")
        ),
    }
    metrics["roc_auc"] = (
        float(roc_auc_score(labels, probabilities))
        if len(set(labels.tolist())) > 1
        else float("nan")
    )
    return metrics


def model_config(vocab_size):
    return {
        "vocab_size": vocab_size,
        "max_upgrade": Config.MAX_UPGRADE,
        "max_count": Config.MAX_COUNT,
        "d_model": Config.D_MODEL,
        "n_heads": Config.N_HEADS,
        "n_layers": Config.N_LAYERS,
        "num_global_features": Config.NUM_GLOBAL_FEATURES,
        "dropout": Config.DROPOUT,
        "global_conditioning": Config.GLOBAL_CONDITIONING,
        "norm_position": Config.NORM_POSITION,
    }


def make_dataset(split, vocabulary, feature_encoder, progress=None):
    return STSDataset(
        Config.DATA_DIR,
        vocabulary,
        feature_encoder,
        split=split,
        max_seq_len=Config.MAX_SEQ_LEN,
        max_upgrade=Config.MAX_UPGRADE,
        max_count=Config.MAX_COUNT,
        progress=progress,
    )


def evaluate(model, loader, criterion, device, progress=None, name="Evaluation"):
    model.eval()
    total_loss = 0.0
    labels = []
    probabilities = []
    ascension_bands = []
    started = time.monotonic()
    last_progress = started
    with torch.no_grad():
        for batch_index, (seq, upgrades, counts, globals_, batch_labels) in enumerate(
            loader, start=1
        ):
            seq, upgrades, counts, globals_, batch_labels = (
                value.to(device, non_blocking=device.type == "cuda")
                for value in (seq, upgrades, counts, globals_, batch_labels)
            )
            logits = model(seq, upgrades, counts, globals_)
            total_loss += criterion(logits, batch_labels).item()
            probabilities.extend(torch.sigmoid(logits).cpu().numpy().reshape(-1))
            labels.extend(batch_labels.cpu().numpy().reshape(-1))
            ascension_bands.extend(
                ascension_band_tensor(globals_).cpu().numpy().reshape(-1)
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
                    f"loss={total_loss / batch_index:.4f}, "
                    f"rate={batch_index / elapsed:.1f} batches/s"
                )
                last_progress = now
    if not labels:
        raise ValueError("Evaluation split has no valid samples")
    metrics = {"loss": total_loss / len(loader)}
    metrics.update(_classification_metrics(labels, probabilities))
    labels_array = np.asarray(labels)
    probabilities_array = np.asarray(probabilities)
    bands_array = np.asarray(ascension_bands)
    metrics["by_ascension_band"] = {}
    for band, name in enumerate(ASCENSION_BAND_NAMES):
        include = bands_array == band
        if include.any():
            band_metrics = _classification_metrics(
                labels_array[include], probabilities_array[include]
            )
            band_metrics["samples"] = int(include.sum())
            metrics["by_ascension_band"][name] = band_metrics
    return metrics


def train_model():
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
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
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
    vocabulary, feature_encoder = build_training_artifacts(
        Config.DATA_DIR, progress=log
    )
    log(f"Training artifacts ready; vocabulary_size={len(vocabulary):,}")
    datasets = {
        split: make_dataset(split, vocabulary, feature_encoder, progress=log)
        for split in ("train", "val", "test")
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
        "test": DataLoader(datasets["test"], **loader_options),
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
    criterion = nn.BCEWithLogitsLoss()
    optimizer = optim.AdamW(model.parameters(), lr=Config.LEARNING_RATE, weight_decay=0.01)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=Config.EPOCHS)
    raw_band_weights = difficulty_weights(manifest)
    band_weights = torch.tensor(raw_band_weights, dtype=torch.float32, device=device)

    os.makedirs(Config.CHECKPOINT_DIR, exist_ok=True)
    best_path = os.path.join(Config.CHECKPOINT_DIR, "best_" + Config.CHECKPOINT_NAME)
    final_path = os.path.join(Config.CHECKPOINT_DIR, Config.CHECKPOINT_NAME)
    best_val_loss = float("inf")
    patience_counter = 0

    for epoch in range(Config.EPOCHS):
        epoch_started = time.monotonic()
        last_progress = epoch_started
        if device.type == "cuda":
            torch.cuda.reset_peak_memory_stats()
        log(f"Epoch {epoch + 1}/{Config.EPOCHS}: training started")
        model.train()
        running_loss = 0.0
        for batch_index, (seq, upgrades, counts, globals_, labels) in enumerate(
            loaders["train"], start=1
        ):
            seq, upgrades, counts, globals_, labels = (
                value.to(device, non_blocking=device.type == "cuda")
                for value in (seq, upgrades, counts, globals_, labels)
            )
            optimizer.zero_grad()
            loss = weighted_bce_loss(
                model(seq, upgrades, counts, globals_),
                labels,
                globals_,
                band_weights,
            )
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            running_loss += loss.item()
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
                    f"loss={running_loss / batch_index:.4f}, "
                    f"rate={batch_index / epoch_elapsed:.1f} batches/s"
                    f"{gpu_memory}"
                )
                last_progress = now
        scheduler.step()

        log(f"Epoch {epoch + 1}/{Config.EPOCHS}: validation started")
        val_metrics = evaluate(
            model,
            loaders["val"],
            criterion,
            device,
            progress=log,
            name=f"Epoch {epoch + 1}/{Config.EPOCHS} validation",
        )
        train_loss = running_loss / len(loaders["train"])
        log(
            f"Epoch {epoch + 1}/{Config.EPOCHS}: train_loss={train_loss:.4f} "
            f"val_loss={val_metrics['loss']:.4f} pr_auc={val_metrics['pr_auc']:.4f} "
            f"brier={val_metrics['brier']:.4f} ece={val_metrics['ece']:.4f}"
        )
        metadata = {
            "epoch": epoch + 1,
            "seed": Config.RANDOM_SEED,
            "split_seed": Config.SPLIT_SEED,
            "validation_metrics": val_metrics,
            "dataset": {
                "filter_version": FILTER_VERSION,
                "content_catalog": manifest["content_catalog"],
                "train_valid_samples_by_ascension_band": manifest[
                    "distributions"
                ]["train_valid_samples_by_ascension_band"],
                "difficulty_weights": raw_band_weights,
            },
        }
        if val_metrics["loss"] < best_val_loss:
            best_val_loss = val_metrics["loss"]
            patience_counter = 0
            save_checkpoint(
                best_path, model, architecture, vocabulary, feature_encoder, metadata
            )
            log(f"Saved new best checkpoint to {best_path}")
        else:
            patience_counter += 1
            if patience_counter >= getattr(Config, "EARLY_STOPPING_PATIENCE", 5):
                log(
                    f"Early stopping after {patience_counter} epochs without improvement"
                )
                break

    log(f"Loading best checkpoint from {best_path}")
    best_checkpoint, _, _ = load_checkpoint(best_path, map_location=device)
    model.load_state_dict(best_checkpoint["model_state_dict"])
    log("Test evaluation started")
    test_metrics = evaluate(
        model, loaders["test"], criterion, device, progress=log, name="Test"
    )
    save_checkpoint(
        final_path,
        model,
        architecture,
        vocabulary,
        feature_encoder,
        {
            "seed": Config.RANDOM_SEED,
            "test_metrics": test_metrics,
            "dataset": {
                "filter_version": FILTER_VERSION,
                "content_catalog": manifest["content_catalog"],
                "train_valid_samples_by_ascension_band": manifest[
                    "distributions"
                ]["train_valid_samples_by_ascension_band"],
                "difficulty_weights": raw_band_weights,
            },
        },
    )
    log(f"Test metrics: {test_metrics}")
    log(f"Saved v4 checkpoints to {Config.CHECKPOINT_DIR}")


if __name__ == "__main__":
    train_model()
