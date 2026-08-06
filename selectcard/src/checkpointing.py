import torch

try:
    from .encoding import PREPROCESSING_VERSION, ItemVocabulary
    from .dataset import GlobalFeatureEncoder
except ImportError:
    from encoding import PREPROCESSING_VERSION, ItemVocabulary
    from dataset import GlobalFeatureEncoder


CHECKPOINT_FORMAT_VERSION = 3


def create_checkpoint(model, model_config, vocabulary, feature_encoder, metadata=None):
    return {
        "format_version": CHECKPOINT_FORMAT_VERSION,
        "preprocessing_version": PREPROCESSING_VERSION,
        "global_feature_schema_version": feature_encoder.schema_version,
        "model_config": dict(model_config),
        "model_state_dict": model.state_dict(),
        "vocabulary": vocabulary.to_dict(),
        "global_feature_encoder": feature_encoder.to_dict(),
        "metadata": dict(metadata or {}),
    }


def save_checkpoint(path, model, model_config, vocabulary, feature_encoder, metadata=None):
    torch.save(
        create_checkpoint(model, model_config, vocabulary, feature_encoder, metadata), path
    )


def load_checkpoint(path, map_location="cpu"):
    checkpoint = torch.load(path, map_location=map_location, weights_only=True)
    if (
        not isinstance(checkpoint, dict)
        or checkpoint.get("format_version") != CHECKPOINT_FORMAT_VERSION
    ):
        raise ValueError(
            f"Unsupported checkpoint format; expected version {CHECKPOINT_FORMAT_VERSION}. "
            "Retrain with selectcard/src/train.py."
        )
    if checkpoint.get("preprocessing_version") != PREPROCESSING_VERSION:
        raise ValueError(
            f"Unsupported preprocessing version: {checkpoint.get('preprocessing_version')}"
        )
    required = {
        "model_config",
        "model_state_dict",
        "vocabulary",
        "global_feature_schema_version",
        "global_feature_encoder",
    }
    missing = required.difference(checkpoint)
    if missing:
        raise ValueError(f"Incomplete checkpoint; missing: {sorted(missing)}")
    if checkpoint["global_feature_schema_version"] != GlobalFeatureEncoder.schema_version:
        raise ValueError("Checkpoint global feature schema is incompatible")
    vocabulary = ItemVocabulary.from_dict(checkpoint["vocabulary"])
    feature_encoder = GlobalFeatureEncoder.from_dict(
        checkpoint["global_feature_encoder"]
    )
    if checkpoint["model_config"].get("num_global_features") != len(
        feature_encoder.feature_names
    ):
        raise ValueError("Checkpoint global feature count is incompatible")
    return checkpoint, vocabulary, feature_encoder
