"""Shared full-training-only sufficient statistics and cache integrity checks."""
import json
import math
from pathlib import Path
import numpy as np

try:
    from .data_contract import numpy_targets, COMMON_TARGET_NAMES
except ImportError:
    from data_contract import numpy_targets, COMMON_TARGET_NAMES


class OutcomeAccumulator:
    def __init__(self):
        self.modes = {mode: {"positive": np.zeros(24), "valid": np.zeros(24)}
                      for mode in ("full_fine", "multiscale")}
        self.positive, self.valid = np.zeros(5), np.zeros(5)
        self.strata, self.terminals = {}, {}

    def add(self, frame):
        floor = frame["floor"].to_numpy(dtype=float)
        terminal = frame["terminal_floor"].to_numpy(dtype=float)
        known = frame["terminal_known"].to_numpy(dtype=bool)
        heart = frame["heart_victory"].to_numpy(dtype=bool)
        for mode, counts in self.modes.items():
            y, mask = numpy_targets(floor, terminal, known, heart, mode)
            counts["positive"] += (y * mask).sum(0)
            counts["valid"] += mask.sum(0)
        reached = terminal[:, None] >= np.array((17, 34, 51, 57))
        y = np.column_stack((reached, heart))
        mask = np.column_stack(((np.array((17, 34, 51, 57))[None] > floor[:, None])
                                & (known[:, None] | reached), known))
        self.positive += (y * mask).sum(0)
        self.valid += mask.sum(0)
        # Group by positions, not pandas index labels, to support arbitrary shard indices.
        pairs = np.column_stack((floor, frame["ascension_band"].to_numpy()))
        for pair in np.unique(pairs, axis=0):
            positions = (pairs == pair).all(1)
            key = tuple(map(int, pair))
            entry = self.strata.setdefault(key, {"positive": np.zeros(5), "valid": np.zeros(5)})
            entry["positive"] += (y[positions] * mask[positions]).sum(0)
            entry["valid"] += mask[positions].sum(0)
            observed = terminal[positions & known]
            totals = self.terminals.setdefault(key, [0., 0])
            totals[0] += float(observed.sum())
            totals[1] += len(observed)

    def manifest_counts(self):
        return {
            "common": {"positive": self.positive.tolist(), "valid": self.valid.tolist()},
            "resolutions": {mode: {name: values.tolist() for name, values in counts.items()}
                            for mode, counts in self.modes.items()},
        }

    def finish(self, statistics, baseline_builder):
        strata = {k: {n: v.tolist() for n, v in counts.items()} for k, counts in self.strata.items()}
        common = baseline_builder(self.positive.tolist(), self.valid.tolist(), strata,
                                  list(COMMON_TARGET_NAMES))
        for (floor, band), (total, count) in self.terminals.items():
            common["strata"][f"{floor}:{band}"]["terminal_mean"] = total / count if count else None
        count = sum(x[1] for x in self.terminals.values())
        common["terminal_mean"] = sum(x[0] for x in self.terminals.values()) / count if count else None
        statistics["common"] = common
        statistics["resolution_statistics"] = {
            mode: {"positive": counts["positive"].tolist(), "valid": counts["valid"].tolist(),
                   "rates": np.divide(counts["positive"], counts["valid"],
                       out=np.full(24, .5), where=counts["valid"] > 0).tolist()}
            for mode, counts in self.modes.items()}


def validate_manifest_outcomes(counts, manifest):
    expected = manifest.get("training_outcome_counts")
    if expected is None:
        raise ValueError("Manifest missing training_outcome_counts; rebuild dataset")
    if counts != expected:
        raise ValueError("Training outcome counts disagree with manifest")


def validate_statistics(statistics, directory=None):
    if set(statistics["resolution_statistics"]) != {"full_fine", "multiscale"}:
        raise ValueError("Missing resolution-specific training statistics")
    groups = [statistics["global"], statistics["common"]["global"],
              *statistics["resolution_statistics"].values()]
    for group, width in zip(groups, (21, 5, 24, 24)):
        if not (len(group["positive"]) == len(group["valid"]) == len(group["rates"]) == width):
            raise ValueError("Training prior widths disagree")
        for positive, valid, rate in zip(group["positive"], group["valid"], group["rates"]):
            if not all(math.isfinite(float(v)) for v in (positive, valid, rate)) or not 0 <= positive <= valid:
                raise ValueError("Invalid training prior counts")
            expected = positive / valid if valid else .5
            if not math.isclose(rate, expected, rel_tol=1e-10, abs_tol=1e-12):
                raise ValueError("Training prior rate disagrees with counts")
    for table in (statistics, statistics["common"]):
        prior = table["prior_strength"]
        if not math.isfinite(prior) or prior <= 0:
            raise ValueError("Invalid prior smoothing strength")
        width = len(table["global"]["rates"])
        for entry in table["strata"].values():
            if not all(len(entry[k]) == width for k in ("positive", "valid", "rates")):
                raise ValueError("Stratum prior widths disagree")
            for positive, valid, rate, base in zip(entry["positive"], entry["valid"], entry["rates"], table["global"]["rates"]):
                if not all(math.isfinite(float(v)) for v in (positive,valid,rate)) or not 0 <= positive <= valid:
                    raise ValueError("Invalid stratum counts")
                if not math.isclose(rate, (positive+prior*base)/(valid+prior), rel_tol=1e-10, abs_tol=1e-12):
                    raise ValueError("Stratum prior rate disagrees with smoothed counts")
    if directory is None:
        return
    path = Path(directory) / "dataset_manifest.json"
    if path.exists():
        manifest = json.loads(path.read_text(encoding="utf-8"))
        validate_manifest_outcomes({
            "common": {name: statistics["common"]["global"][name] for name in ("positive", "valid")},
            "resolutions": {mode: {name: group[name] for name in ("positive", "valid")}
                            for mode, group in statistics["resolution_statistics"].items()},
        }, manifest)
        dimensions = manifest.get("distributions", {})
        for field, name in (("positive", "train_positive_samples_by_target"),
                            ("valid", "train_valid_samples_by_target")):
            expected = dimensions.get(name)
            if expected is None:
                raise ValueError(f"Manifest missing {name}; rebuild dataset")
            observed = dict(zip(statistics["target_names"], statistics["global"][field]))
            if any(observed[k] != expected.get(k, 0) for k in observed):
                raise ValueError(f"Training prior {field} counts disagree with manifest")
