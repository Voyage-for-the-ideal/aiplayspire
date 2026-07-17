import os
import random

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score
from torch.utils.data import DataLoader

try:
    from .checkpointing import load_checkpoint, save_checkpoint
    from .config import Config
    from .dataset import STSDataset, build_training_artifacts
    from .model import STSValueNetwork
except ImportError:
    from checkpointing import load_checkpoint, save_checkpoint
    from config import Config
    from dataset import STSDataset, build_training_artifacts
    from model import STSValueNetwork


def set_seed(seed):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


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


def make_dataset(split, vocabulary, normalizer):
    return STSDataset(
        Config.DATA_DIR,
        vocabulary,
        normalizer,
        split=split,
        max_seq_len=Config.MAX_SEQ_LEN,
        max_upgrade=Config.MAX_UPGRADE,
        max_count=Config.MAX_COUNT,
    )


def evaluate(model, loader, criterion, device):
    model.eval()
    total_loss = 0.0
    labels = []
    probabilities = []
    with torch.no_grad():
        for seq, upgrades, counts, globals_, batch_labels in loader:
            seq, upgrades, counts, globals_, batch_labels = (
                value.to(device)
                for value in (seq, upgrades, counts, globals_, batch_labels)
            )
            logits = model(seq, upgrades, counts, globals_)
            total_loss += criterion(logits, batch_labels).item()
            probabilities.extend(torch.sigmoid(logits).cpu().numpy().reshape(-1))
            labels.extend(batch_labels.cpu().numpy().reshape(-1))
    if not labels:
        raise ValueError("Evaluation split has no valid samples")
    metrics = {
        "loss": total_loss / len(loader),
        "pr_auc": float(average_precision_score(labels, probabilities)),
        "brier": float(brier_score_loss(labels, probabilities)),
        "ece": expected_calibration_error(labels, probabilities),
    }
    metrics["roc_auc"] = (
        float(roc_auc_score(labels, probabilities))
        if len(set(labels)) > 1
        else float("nan")
    )
    return metrics


def train_model():
    set_seed(Config.RANDOM_SEED)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    vocabulary, normalizer = build_training_artifacts(Config.DATA_DIR)
    datasets = {
        split: make_dataset(split, vocabulary, normalizer)
        for split in ("train", "val", "test")
    }
    for split, dataset in datasets.items():
        if not dataset:
            raise ValueError(f"The {split} split has no valid samples")

    generator = torch.Generator().manual_seed(Config.RANDOM_SEED)
    loaders = {
        "train": DataLoader(
            datasets["train"],
            batch_size=Config.BATCH_SIZE,
            shuffle=True,
            generator=generator,
        ),
        "val": DataLoader(datasets["val"], batch_size=Config.BATCH_SIZE),
        "test": DataLoader(datasets["test"], batch_size=Config.BATCH_SIZE),
    }
    architecture = model_config(len(vocabulary))
    model = STSValueNetwork(**architecture).to(device)
    criterion = nn.BCEWithLogitsLoss()
    optimizer = optim.AdamW(model.parameters(), lr=Config.LEARNING_RATE, weight_decay=0.01)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=Config.EPOCHS)

    os.makedirs(Config.CHECKPOINT_DIR, exist_ok=True)
    best_path = os.path.join(Config.CHECKPOINT_DIR, "best_" + Config.CHECKPOINT_NAME)
    final_path = os.path.join(Config.CHECKPOINT_DIR, Config.CHECKPOINT_NAME)
    best_val_loss = float("inf")
    patience_counter = 0

    for epoch in range(Config.EPOCHS):
        model.train()
        running_loss = 0.0
        for seq, upgrades, counts, globals_, labels in loaders["train"]:
            seq, upgrades, counts, globals_, labels = (
                value.to(device) for value in (seq, upgrades, counts, globals_, labels)
            )
            optimizer.zero_grad()
            loss = criterion(model(seq, upgrades, counts, globals_), labels)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            running_loss += loss.item()
        scheduler.step()

        val_metrics = evaluate(model, loaders["val"], criterion, device)
        train_loss = running_loss / len(loaders["train"])
        print(
            f"Epoch {epoch + 1}/{Config.EPOCHS}: train_loss={train_loss:.4f} "
            f"val_loss={val_metrics['loss']:.4f} pr_auc={val_metrics['pr_auc']:.4f} "
            f"brier={val_metrics['brier']:.4f} ece={val_metrics['ece']:.4f}"
        )
        metadata = {
            "epoch": epoch + 1,
            "seed": Config.RANDOM_SEED,
            "split_seed": Config.SPLIT_SEED,
            "validation_metrics": val_metrics,
        }
        if val_metrics["loss"] < best_val_loss:
            best_val_loss = val_metrics["loss"]
            patience_counter = 0
            save_checkpoint(
                best_path, model, architecture, vocabulary, normalizer, metadata
            )
        else:
            patience_counter += 1
            if patience_counter >= getattr(Config, "EARLY_STOPPING_PATIENCE", 5):
                break

    best_checkpoint, _, _ = load_checkpoint(best_path, map_location=device)
    model.load_state_dict(best_checkpoint["model_state_dict"])
    test_metrics = evaluate(model, loaders["test"], criterion, device)
    save_checkpoint(
        final_path,
        model,
        architecture,
        vocabulary,
        normalizer,
        {"seed": Config.RANDOM_SEED, "test_metrics": test_metrics},
    )
    print(f"Test metrics: {test_metrics}")
    print(f"Saved v2 checkpoints to {Config.CHECKPOINT_DIR}")


if __name__ == "__main__":
    train_model()
