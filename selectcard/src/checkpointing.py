import torch

try:
    from .data_contract import (
        HAZARD_ENDPOINTS,
        VALUE_TARGET_SCHEMA,
        BOSS_SCHEMA_VERSION,
    )
    from .encoding import PREPROCESSING_VERSION, ItemVocabulary
    from .dataset import GlobalFeatureEncoder
    from .boss_context import BOSS_VOCABULARY, NUM_BOSS_IDS
    from .config import Config
except ImportError:
    from data_contract import HAZARD_ENDPOINTS, VALUE_TARGET_SCHEMA, BOSS_SCHEMA_VERSION
    from encoding import PREPROCESSING_VERSION, ItemVocabulary
    from dataset import GlobalFeatureEncoder
    from boss_context import BOSS_VOCABULARY, NUM_BOSS_IDS
    from config import Config


CHECKPOINT_FORMAT_VERSION = 7
LEGACY_VALUE_TARGET_ERROR = (
    "Checkpoint uses a legacy or incompatible value target. "
    "Rebuild processed_data_v2 and retrain the bucket hazard model."
)


def create_checkpoint(model, model_config, vocabulary, feature_encoder, metadata=None):
    return {
        "format_version": CHECKPOINT_FORMAT_VERSION,
        "preprocessing_version": PREPROCESSING_VERSION,
        "value_target_schema": VALUE_TARGET_SCHEMA,
        "hazard_endpoints": list(HAZARD_ENDPOINTS),
        "default_heart_bonus_floors": Config.HEART_WIN_BONUS_FLOORS,
        "global_feature_schema_version": feature_encoder.schema_version,
        "boss_schema_version": BOSS_SCHEMA_VERSION,
        "boss_vocabulary": dict(BOSS_VOCABULARY),
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
        "value_target_schema",
        "hazard_endpoints",
        "default_heart_bonus_floors",
        "boss_schema_version",
        "boss_vocabulary",
    }
    missing = required.difference(checkpoint)
    if missing:
        raise ValueError(f"Incomplete checkpoint; missing: {sorted(missing)}")
    if checkpoint["value_target_schema"] != VALUE_TARGET_SCHEMA:
        raise ValueError(LEGACY_VALUE_TARGET_ERROR)
    try:
        checkpoint_endpoints = tuple(checkpoint["hazard_endpoints"])
    except (TypeError, ValueError):
        raise ValueError(LEGACY_VALUE_TARGET_ERROR) from None
    if checkpoint_endpoints != tuple(HAZARD_ENDPOINTS):
        raise ValueError(LEGACY_VALUE_TARGET_ERROR)
    if checkpoint["default_heart_bonus_floors"] != Config.HEART_WIN_BONUS_FLOORS:
        raise ValueError(LEGACY_VALUE_TARGET_ERROR)
    if checkpoint["boss_schema_version"] != BOSS_SCHEMA_VERSION or checkpoint["boss_vocabulary"] != BOSS_VOCABULARY:
        raise ValueError("Checkpoint boss schema is incompatible; retraining required")
    if checkpoint["model_config"].get("num_bosses") != NUM_BOSS_IDS:
        raise ValueError("Checkpoint boss vocabulary size is incompatible; retraining required")
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
