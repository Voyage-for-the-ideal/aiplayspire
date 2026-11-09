import math


PREPROCESSING_VERSION = "set-transformer-v11-multiscale"
FILTER_VERSION = "standard-vanilla-v1"
BOSS_SCHEMA_VERSION = "visible-boss-v1"

VALUE_TARGET_SCHEMA = "multiscale-hazard-v1"
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
COARSE_ENDPOINTS = (34, 51, 57)
STAGE_ENDPOINTS = (17, 34, 51, 57)
MULTISCALE_HAZARD_DIM = HAZARD_OUTPUT_DIM + len(COARSE_ENDPOINTS)
VALUE_OUTPUT_DIM = 2 * MULTISCALE_HAZARD_DIM + 1
COMMON_TARGET_NAMES = tuple(f"reach_{e}" for e in STAGE_ENDPOINTS) + ("heart_win",)
COMMON_ENTROPY_FLOOR = 0.05
OUTCOME_COLUMNS = ("terminal_known", "terminal_floor", "heart_victory")


def progress_stage(floor):
    """Shared progress bands; independent of outcomes and optional map context."""
    if isinstance(floor, bool) or not isinstance(floor, (int, float)):
        raise ValueError("floor must be numeric")
    if not math.isfinite(floor) or not 0 <= floor <= 57:
        raise ValueError("floor must be between 0 and 57")
    return min(sum(floor >= end for end in STAGE_ENDPOINTS), 3)


def active_bins(floor, resolution="multiscale"):
    """(head index, kind, inclusive lower bound, exclusive endpoint)."""
    stage = progress_stage(float(floor))
    if resolution not in {"multiscale", "full_fine"}:
        raise ValueError(f"Unknown resolution: {resolution}")
    entries = []
    for i, end in enumerate(HAZARD_ENDPOINTS):
        if end > floor and (resolution == "full_fine" or progress_stage(end - 1) == stage):
            entries.append((i, "fine", end))
    if resolution == "multiscale":
        entries.extend((HAZARD_OUTPUT_DIM + i, "coarse", end)
                       for i, end in enumerate(COARSE_ENDPOINTS)
                       if end > floor and progress_stage(end - 1) > stage)
    previous = float(floor)
    result = []
    for i, kind, end in sorted(entries, key=lambda entry: entry[2]):
        result.append((i, kind, previous, end))
        previous = end
    return result


def multiscale_targets(floor, terminal_floor, terminal_known, heart_victory,
                       resolution="multiscale"):
    progress_stage(float(floor))
    if terminal_floor < floor or not math.isfinite(float(terminal_floor)) or terminal_floor > 57:
        raise ValueError("terminal floor must be finite and between snapshot floor and 57")
    if heart_victory and (not terminal_known or terminal_floor != 57):
        raise ValueError("Heart victory must have known terminal progress 57")
    targets = [0.] * (MULTISCALE_HAZARD_DIM + 1)
    masks = [False] * len(targets)
    for index, _, _, endpoint in active_bins(floor, resolution):
        if terminal_floor >= endpoint:
            masks[index] = True
        elif terminal_known:
            targets[index], masks[index] = 1., True
            break
        else:
            break
    targets[-1], masks[-1] = float(heart_victory), bool(terminal_known)
    return targets, masks


def common_targets(floor, terminal_floor, terminal_known, heart_victory):
    labels = [float(terminal_floor >= endpoint) for endpoint in STAGE_ENDPOINTS]
    masks = [endpoint > floor and (terminal_known or terminal_floor >= endpoint)
             for endpoint in STAGE_ENDPOINTS]
    return labels + [float(heart_victory)], masks + [bool(terminal_known)]


def numpy_targets(floors, terminal, known, heart, resolution="multiscale"):
    """Vectorized artifact scan; exactly the same labels as multiscale_targets."""
    import numpy as np
    floors, terminal = np.asarray(floors), np.asarray(terminal)
    known, heart = np.asarray(known, dtype=bool), np.asarray(heart, dtype=bool)
    ends = np.asarray(HAZARD_ENDPOINTS + COARSE_ENDPOINTS)
    stages = np.searchsorted([17, 34, 51], floors, side="right")
    head_stages = np.searchsorted([17, 34, 51], ends - 1, side="right")
    fine = np.arange(len(ends)) < HAZARD_OUTPUT_DIM
    if resolution == "full_fine":
        active = (ends[None] > floors[:, None]) & fine[None]
    elif resolution == "multiscale":
        active = (ends[None] > floors[:, None]) & np.where(
            fine[None], head_stages[None] == stages[:, None], head_stages[None] > stages[:, None])
    else:
        raise ValueError("Invalid resolution")
    failed = active & (ends[None] > terminal[:, None])
    first = np.where(failed, ends[None], 58).min(1)
    stop = failed & (ends[None] == first[:, None]) & known[:, None]
    masks = active & ((ends[None] <= terminal[:, None]) | stop)
    return np.column_stack((stop, heart)).astype(float), np.column_stack((masks, known))

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
