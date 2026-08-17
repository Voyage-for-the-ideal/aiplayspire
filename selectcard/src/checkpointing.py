import torch

try:
    from .data_contract import VALUE_COMPONENT_NAMES
    from .encoding import PREPROCESSING_VERSION, ItemVocabulary
    from .dataset import GlobalFeatureEncoder
except ImportError:
    from data_contract import VALUE_COMPONENT_NAMES
    from encoding import PREPROCESSING_VERSION, ItemVocabulary
    from dataset import GlobalFeatureEncoder


CHECKPOINT_FORMAT_VERSION = 5
LEGACY_VALUE_TARGET_ERROR = (
    "Checkpoint uses legacy single-act value target. "
    "Rebuild processed_data_v2 and retrain the multi-horizon value model."
)


def create_checkpoint(model, model_config, vocabulary, feature_encoder, metadata=None):
    return {
        "format_version": CHECKPOINT_FORMAT_VERSION,
        "preprocessing_version": PREPROCESSING_VERSION,
        "value_components": list(VALUE_COMPONENT_NAMES),
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
    if not isinstance(checkpoint, dict):
        raise ValueError(LEGACY_VALUE_TARGET_ERROR)
    if checkpoint.get("format_version") != CHECKPOINT_FORMAT_VERSION:
        raise ValueError(
            f"{LEGACY_VALUE_TARGET_ERROR} Expected checkpoint format "
            f"version {CHECKPOINT_FORMAT_VERSION}."
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
        "value_components",
    }
    missing = required.difference(checkpoint)
    if missing:
        raise ValueError(f"Incomplete checkpoint; missing: {sorted(missing)}")
    if tuple(checkpoint["value_components"]) != VALUE_COMPONENT_NAMES:
        raise ValueError(LEGACY_VALUE_TARGET_ERROR)
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
