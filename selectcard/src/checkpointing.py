import torch

try:
    from .encoding import PREPROCESSING_VERSION, ItemVocabulary
    from .dataset import GlobalFeatureNormalizer
except ImportError:
    from encoding import PREPROCESSING_VERSION, ItemVocabulary
    from dataset import GlobalFeatureNormalizer


CHECKPOINT_FORMAT_VERSION = 2


def create_checkpoint(model, model_config, vocabulary, normalizer, metadata=None):
    return {
        "format_version": CHECKPOINT_FORMAT_VERSION,
        "preprocessing_version": PREPROCESSING_VERSION,
        "model_config": dict(model_config),
        "model_state_dict": model.state_dict(),
        "vocabulary": vocabulary.to_dict(),
        "normalization": normalizer.to_dict(),
        "metadata": dict(metadata or {}),
    }


def save_checkpoint(path, model, model_config, vocabulary, normalizer, metadata=None):
    torch.save(
        create_checkpoint(model, model_config, vocabulary, normalizer, metadata), path
    )


def load_checkpoint(path, map_location="cpu"):
    checkpoint = torch.load(path, map_location=map_location, weights_only=True)
    if not isinstance(checkpoint, dict) or checkpoint.get("format_version") != 2:
        raise ValueError(
            "Legacy v1 state_dict checkpoints are incompatible with the v2 model. "
            "Rebuild processed data and retrain with selectcard/src/train.py."
        )
    if checkpoint.get("preprocessing_version") != PREPROCESSING_VERSION:
        raise ValueError(
            f"Unsupported preprocessing version: {checkpoint.get('preprocessing_version')}"
        )
    required = {
        "model_config",
        "model_state_dict",
        "vocabulary",
        "normalization",
    }
    missing = required.difference(checkpoint)
    if missing:
        raise ValueError(f"Incomplete v2 checkpoint; missing: {sorted(missing)}")
    vocabulary = ItemVocabulary.from_dict(checkpoint["vocabulary"])
    normalizer = GlobalFeatureNormalizer.from_dict(checkpoint["normalization"])
    return checkpoint, vocabulary, normalizer
