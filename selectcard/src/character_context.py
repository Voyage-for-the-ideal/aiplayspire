"""Stable character vocabulary shared by data, training, and inference."""

CHARACTER_SCHEMA_VERSION = "character-v1"
CHARACTER_VOCABULARY = {
    "IRONCLAD": 0,
    "THE_SILENT": 1,
    "DEFECT": 2,
    "WATCHER": 3,
}
NUM_CHARACTER_IDS = len(CHARACTER_VOCABULARY)


def character_id(character):
    """Return the canonical integer id, rejecting missing/unknown values."""
    if not isinstance(character, str) or character not in CHARACTER_VOCABULARY:
        raise ValueError(
            "character must be one of: " + ", ".join(CHARACTER_VOCABULARY)
        )
    return CHARACTER_VOCABULARY[character]


def validate_character_vocabulary(value):
    """Validate a serialized vocabulary without accepting reordered ids."""
    if value != CHARACTER_VOCABULARY:
        raise ValueError(
            "Checkpoint character vocabulary is incompatible; retraining required"
        )

