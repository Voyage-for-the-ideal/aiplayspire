import math


PREPROCESSING_VERSION = "set-transformer-v6-bucket-hazard"
FILTER_VERSION = "standard-vanilla-v1"
BOSS_SCHEMA_VERSION = "visible-boss-v1"

VALUE_TARGET_SCHEMA = "bucket-hazard-v1"
HAZARD_ENDPOINTS = (
    3, 6, 9, 12, 15, 17,
    20, 23, 26, 29, 32, 34,
    37, 40, 43, 46, 49, 51,
    54, 57,
)
HAZARD_NAMES = tuple(f"stop_before_{endpoint:02d}" for endpoint in HAZARD_ENDPOINTS)
HAZARD_TARGET_COLUMNS = tuple(f"target_{name}" for name in HAZARD_NAMES)
HAZARD_MASK_COLUMNS = tuple(f"valid_{name}" for name in HAZARD_NAMES)
HEART_TARGET_COLUMN = "target_heart_win"
HEART_MASK_COLUMN = "valid_heart_win"
TARGET_COLUMNS = (*HAZARD_TARGET_COLUMNS, HEART_TARGET_COLUMN)
MASK_COLUMNS = (*HAZARD_MASK_COLUMNS, HEART_MASK_COLUMN)
HAZARD_OUTPUT_DIM = len(HAZARD_ENDPOINTS)
VALUE_OUTPUT_DIM = HAZARD_OUTPUT_DIM + 1

ASCENSION_BAND_NAMES = (
    "A0",
    "A1-5",
    "A6-10",
    "A11-15",
    "A16-19",
    "A20",
)


def ascension_band(level):
    if isinstance(level, bool) or not isinstance(level, (int, float)):
        raise ValueError("ascension level must be numeric")
    if not math.isfinite(level) or int(level) != level or not 0 <= level <= 20:
        raise ValueError("ascension level must be an integer from 0 to 20")
    level = int(level)
    if level == 0:
        return 0
    if level <= 5:
        return 1
    if level <= 10:
        return 2
    if level <= 15:
        return 3
    if level <= 19:
        return 4
    return 5
