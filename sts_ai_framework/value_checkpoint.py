"""Resolve the local value-network checkpoint selected for an agent run."""

import os
from pathlib import Path


def resolve_value_checkpoint(model_base_dir, environ=None):
    """Use STS_VALUE_CHECKPOINT when set, otherwise the general model default."""
    environment = os.environ if environ is None else environ
    configured_path = environment.get("STS_VALUE_CHECKPOINT", "").strip()
    if configured_path:
        path = Path(os.path.expandvars(os.path.expanduser(configured_path)))
        return str(path.resolve())
    return str(
        (Path(model_base_dir) / "checkpoints" / "best_sts_value_model_final.pth").resolve()
    )
