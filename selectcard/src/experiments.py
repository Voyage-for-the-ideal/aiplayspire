"""V4 experiment registry and deterministic run-level sampling protocol."""

import hashlib
import json
import math
from pathlib import Path

try:
    from .config import Config
except ImportError:
    from config import Config


EXPERIMENTS = {
    "baseline": {},
    "depth8": {"N_LAYERS": 8},
    "width256": {"D_MODEL": 256},
    "key0": {"KEY_HEAD_LOSS_WEIGHT": 0.0},
    "key005": {"KEY_HEAD_LOSS_WEIGHT": 0.05},
    "key02": {"KEY_HEAD_LOSS_WEIGHT": 0.2},
    "monotonic0": {"MONOTONIC_LOSS_WEIGHT": 0.0},
    "monotonic003": {"MONOTONIC_LOSS_WEIGHT": 0.03},
    "monotonic01": {"MONOTONIC_LOSS_WEIGHT": 0.1},
}

BASELINE = {
    "D_MODEL": 192, "N_LAYERS": 5, "N_HEADS": 4,
    "FFN_TYPE": "relu", "NORM_TYPE": "layernorm", "MAP_ENCODER": "none",
    "MAP_LATENTS": 0, "COORDINATE_EMBEDDING": "linear", "POOLING": "cls",
    "REGISTER_TOKENS": 0, "KEY_HEAD_LOSS_WEIGHT": 0.2,
    "MONOTONIC_LOSS_WEIGHT": 0.1, "USE_MAP_CONTEXT": True,
    "GLOBAL_CONDITIONING": "token", "NORM_POSITION": "pre", "DROPOUT": 0.1,
    "BATCH_SIZE": 512, "LEARNING_RATE": 2.8e-4,
    "CONTEXT_CURRICULUM_EPOCHS": 3, "LR_MIN_RATIO": 0.0,
    "EARLY_STOPPING_PATIENCE": 0,
}


def selected_run(run_id, fraction=1.0):
    if not 0 < fraction <= 1:
        raise ValueError("run_fraction must be in (0, 1]")
    if fraction == 1:
        return True
    digest = hashlib.sha256(f"aiplayspire-v4-pilot:{run_id}".encode()).digest()
    return int.from_bytes(digest[:8], "big") < int(fraction * (1 << 64))


def configure_experiment(names="baseline", stage="pilot", seed=42, epochs=None):
    """Comma-separated names compose only explicitly selected experiments."""
    if stage not in {"pilot", "confirm"}:
        raise ValueError("stage must be pilot or confirm")
    chosen = names.split(",")
    updates = dict(BASELINE)
    seen = {}
    for name in chosen:
        if name not in EXPERIMENTS:
            raise ValueError(f"Unknown experiment: {name}")
        for key, value in EXPERIMENTS[name].items():
            if key in seen and seen[key] != value:
                raise ValueError(f"Conflicting experimental settings: {key}")
            seen[key] = value
            updates[key] = value
    updates.update(RANDOM_SEED=seed, EPOCHS=epochs or (8 if stage == "pilot" else 20),
                   RUN_FRACTION=0.01 if stage == "pilot" else 1.0)
    if updates["EPOCHS"] <= updates["CONTEXT_CURRICULUM_EPOCHS"]:
        raise ValueError("Training needs at least one full-context epoch after curriculum")
    updates["COSINE_DECAY_EPOCHS"] = updates["EPOCHS"]
    for key, value in updates.items():
        setattr(Config, key, value)
    Config.EXPERIMENT_METADATA = {
        "names": chosen, "stage": stage, "seed": seed,
        "run_selection": "sha256-aiplayspire-v4-pilot-v1",
        "settings": updates,
        "confirmation_seeds": [42, 43, 44],
        "artifact_fit": "shared-full-training-split-only",
    }
    return Config.EXPERIMENT_METADATA


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    def clean(v):
        if isinstance(v, float) and not math.isfinite(v):
            return None
        if isinstance(v, dict):
            return {k: clean(x) for k, x in v.items()}
        if isinstance(v, (tuple, list)):
            return [clean(x) for x in v]
        return v
    path.write_text(json.dumps(clean(value), ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8")
