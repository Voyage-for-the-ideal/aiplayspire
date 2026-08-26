"""Run one reproducible model-size comparison experiment.

Submit this script once per preset.  It reuses the production training path so
the dataset contract, loss, metrics, and checkpoint format stay identical to
``train.py``.  Only model width/depth/head count and output names differ.

Examples:
    python src/train_com.py --preset baseline
    python src/train_com.py --preset medium
    python src/train_com.py --preset large
"""

import argparse

try:
    from .config import Config
    from .train import train_model
except ImportError:
    from config import Config
    from train import train_model


PRESETS = {
    # Keep 32 dimensions per attention head across all three comparisons.
    "baseline": {"d_model": 128, "n_heads": 4, "n_layers": 3},
    "medium": {"d_model": 256, "n_heads": 8, "n_layers": 4},
    "large": {"d_model": 384, "n_heads": 12, "n_layers": 6},
}


def parse_args():
    parser = argparse.ArgumentParser(
        description="Train one fixed-budget Set Transformer size-comparison preset."
    )
    parser.add_argument(
        "--preset",
        choices=tuple(PRESETS),
        required=True,
        help="Model size to train; run each preset as a separate job.",
    )
    parser.add_argument(
        "--test-only",
        action="store_true",
        help=(
            "Skip training; load the best checkpoint for this preset and run "
            "the final test evaluation, then save the final checkpoint."
        ),
    )
    return parser.parse_args()


def configure_preset(name):
    """Apply a comparison preset before the shared training loop is created."""
    preset = PRESETS[name]
    Config.D_MODEL = preset["d_model"]
    Config.N_HEADS = preset["n_heads"]
    Config.N_LAYERS = preset["n_layers"]

    # Every comparison receives the same batch size and 20 full passes over
    # the training split.  train.py derives cosine T_max from Config.EPOCHS.
    Config.BATCH_SIZE = 512
    Config.EPOCHS = 20
    Config.COSINE_DECAY_EPOCHS = 10
    Config.LR_MIN_RATIO = 0.10
    # A comparison has a fixed 20-epoch sample budget, so an early validation
    # plateau must not shorten one preset relative to the others.
    Config.EARLY_STOPPING_START_EPOCH = Config.EPOCHS
    Config.EARLY_STOPPING_PATIENCE = Config.EPOCHS + 1
    Config.CHECKPOINT_NAME = f"sts_value_model_{name}.pth"
    Config.TRAINING_REPORT_NAME = f"training_report_{name}.png"


def main():
    args = parse_args()
    configure_preset(args.preset)
    preset = PRESETS[args.preset]
    print(
        "Size comparison preset: "
        f"{args.preset}; d_model={preset['d_model']}; "
        f"n_heads={preset['n_heads']}; n_layers={preset['n_layers']}; "
        f"batch_size={Config.BATCH_SIZE}; epochs={Config.EPOCHS}; "
        f"learning_rate={Config.LEARNING_RATE}; "
        f"cosine_decay_epochs={Config.COSINE_DECAY_EPOCHS}; "
        f"min_lr_ratio={Config.LR_MIN_RATIO}",
        flush=True,
    )
    train_model(test_only=args.test_only)


if __name__ == "__main__":
    main()
