import math
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
    from .character_context import (
        CHARACTER_SCHEMA_VERSION,
        CHARACTER_VOCABULARY,
        NUM_CHARACTER_IDS,
        validate_character_vocabulary,
    )
    from .config import Config
    from .map_context import context_schema
except ImportError:
    from data_contract import HAZARD_ENDPOINTS, VALUE_TARGET_SCHEMA, BOSS_SCHEMA_VERSION
    from encoding import PREPROCESSING_VERSION, ItemVocabulary
    from dataset import GlobalFeatureEncoder
    from boss_context import BOSS_VOCABULARY, NUM_BOSS_IDS
    from character_context import (
        CHARACTER_SCHEMA_VERSION,
        CHARACTER_VOCABULARY,
        NUM_CHARACTER_IDS,
        validate_character_vocabulary,
    )
    from config import Config
    from map_context import context_schema


CHECKPOINT_FORMAT_VERSION = 12
LEGACY_VALUE_TARGET_ERROR = (
    "Checkpoint uses a legacy or incompatible value target. "
    "Rebuild the processed dataset and retrain the character-conditioned bucket hazard model."
)


def create_checkpoint(model, model_config, vocabulary, feature_encoder, metadata=None):
    supplied = dict(model_config)
    model_config = dict(model.model_config)
    for name, value in supplied.items():
        if name not in model_config or model_config[name] != value:
            raise ValueError(f"Model configuration disagrees with instantiated model: {name}")
    if model_config.get("num_characters") != NUM_CHARACTER_IDS:
        raise ValueError(
            "model_config.num_characters must equal the character vocabulary size"
        )
    if model_config.get("use_map_context") and model_config.get("num_key_features") != context_schema()["key_feature_count"]:
        raise ValueError(
            "model_config.num_key_features must match the key context schema"
        )
    metadata = dict(metadata or {})
    objective = dict(metadata.get("training_objective", {}))
    defaults = dict(schema_version="multiscale-loss-v1", mode=Config.VALUE_LOSS_MODE,
        resolution_mode=model_config["resolution_mode"], far_loss_weight=Config.FAR_LOSS_WEIGHT,
        mean_loss_weight=Config.MEAN_LOSS_WEIGHT, heart_loss_weight=Config.HEART_HEAD_LOSS_WEIGHT,
        key_loss_weight=Config.KEY_HEAD_LOSS_WEIGHT, monotonic_loss_weight=Config.MONOTONIC_LOSS_WEIGHT,
        baseline_entropy_floor=Config.BASELINE_ENTROPY_FLOOR, common_score_heart_weight=1.0)
    for key, value in defaults.items():
        objective.setdefault(key, value)
    metadata["training_objective"] = objective
    return {
        "format_version": CHECKPOINT_FORMAT_VERSION,
        "preprocessing_version": PREPROCESSING_VERSION,
        "value_target_schema": VALUE_TARGET_SCHEMA,
        "hazard_endpoints": list(HAZARD_ENDPOINTS),
        "default_heart_bonus_floors": Config.HEART_WIN_BONUS_FLOORS,
        "global_feature_schema_version": feature_encoder.schema_version,
        "boss_schema_version": BOSS_SCHEMA_VERSION,
        "boss_vocabulary": dict(BOSS_VOCABULARY),
        "character_schema_version": CHARACTER_SCHEMA_VERSION,
        "character_vocabulary": dict(CHARACTER_VOCABULARY),
        "context_schema": context_schema(),
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
        "character_schema_version",
        "character_vocabulary",
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
    required_architecture = {
        "ffn_type", "norm_type", "map_encoder", "map_latents", "coordinate_embedding",
        "pooling", "register_tokens", "item_token_budget", "resolution_mode",
    }
    if not required_architecture.issubset(checkpoint["model_config"]):
        raise ValueError("Incomplete V4 architecture configuration")
    if checkpoint["model_config"].get("num_bosses") != NUM_BOSS_IDS:
        raise ValueError("Checkpoint boss vocabulary size is incompatible; retraining required")
    if checkpoint["character_schema_version"] != CHARACTER_SCHEMA_VERSION:
        raise ValueError("Checkpoint character schema is incompatible; retraining required")
    validate_character_vocabulary(checkpoint["character_vocabulary"])
    if checkpoint["model_config"].get("num_characters") != NUM_CHARACTER_IDS:
        raise ValueError("Checkpoint character vocabulary size is incompatible; retraining required")
    if checkpoint["model_config"].get("use_map_context"):
        if checkpoint.get("context_schema") != context_schema():
            raise ValueError(
                "Checkpoint map-context schema is incompatible "
                f"(expected {context_schema()}, got {checkpoint.get('context_schema')}); "
                "retraining required"
            )
        if checkpoint["model_config"].get("num_key_features") != context_schema()["key_feature_count"]:
            raise ValueError("Checkpoint key feature count is incompatible; retraining required")
    if checkpoint["global_feature_schema_version"] != GlobalFeatureEncoder.schema_version:
        raise ValueError("Checkpoint global feature schema is incompatible")
    objective = checkpoint.get("metadata", {}).get("training_objective", {})
    required_objective = {"mode", "resolution_mode", "far_loss_weight", "mean_loss_weight",
        "heart_loss_weight", "key_loss_weight", "monotonic_loss_weight", "baseline_entropy_floor",
        "common_score_heart_weight"}
    if not required_objective.issubset(objective):
        raise ValueError("Checkpoint missing frozen loss configuration")
    for name in ("far_loss_weight", "mean_loss_weight", "heart_loss_weight", "key_loss_weight",
                 "monotonic_loss_weight", "baseline_entropy_floor"):
        value = objective[name]
        if not isinstance(value, (int,float)) or not math.isfinite(value) or value < 0:
            raise ValueError(f"Invalid frozen loss weight: {name}")
    if objective["baseline_entropy_floor"] <= 0 or objective["common_score_heart_weight"] != 1.0:
        raise ValueError("Invalid frozen entropy or common benchmark weight")
    if objective["mode"] not in {"multiscale", "macro_relative_nll", "micro_nll"}:
        raise ValueError("Invalid frozen loss mode")
    if objective["resolution_mode"] != checkpoint["model_config"]["resolution_mode"]:
        raise ValueError("Checkpoint resolution disagrees with frozen objective")
    if "baseline_statistics" in checkpoint.get("metadata", {}):
        try:
            from .outcome_statistics import validate_statistics
        except ImportError:
            from outcome_statistics import validate_statistics
        validate_statistics(checkpoint["metadata"]["baseline_statistics"])
    vocabulary = ItemVocabulary.from_dict(checkpoint["vocabulary"])
    feature_encoder = GlobalFeatureEncoder.from_dict(
        checkpoint["global_feature_encoder"]
    )
    if checkpoint["model_config"].get("num_global_features") != len(
        feature_encoder.feature_names
    ):
        raise ValueError("Checkpoint global feature count is incompatible")
    return checkpoint, vocabulary, feature_encoder
