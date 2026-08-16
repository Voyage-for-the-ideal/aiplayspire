import math


PREPROCESSING_VERSION = "set-transformer-v4-multi-horizon"
FILTER_VERSION = "standard-vanilla-v1"

VALUE_COMPONENT_NAMES = ("reach17", "reach34", "reach50", "win")
HORIZON_BOUNDARIES = (17, 34, 50)
TARGET_COLUMNS = tuple(f"target_{name}" for name in VALUE_COMPONENT_NAMES)
MASK_COLUMNS = tuple(f"valid_{name}" for name in VALUE_COMPONENT_NAMES)
VALUE_OUTPUT_DIM = len(VALUE_COMPONENT_NAMES)

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
