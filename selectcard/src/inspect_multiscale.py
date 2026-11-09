"""Read-only outcome/coverage audit. Does not fit priors or inspect held-out test."""
import argparse
from pathlib import Path
import numpy as np

try:
    from .config import Config
    from .data_contract import (HAZARD_ENDPOINTS, COARSE_ENDPOINTS, TARGET_COLUMNS, MASK_COLUMNS, numpy_targets)
    from .dataset import _read_frame, load_dataset_manifest
    from .experiments import write_json
    from .outcome_statistics import OutcomeAccumulator, validate_manifest_outcomes
except ImportError:
    from config import Config
    from data_contract import (HAZARD_ENDPOINTS, COARSE_ENDPOINTS, TARGET_COLUMNS, MASK_COLUMNS, numpy_targets)
    from dataset import _read_frame, load_dataset_manifest
    from experiments import write_json
    from outcome_statistics import OutcomeAccumulator, validate_manifest_outcomes


def inspect(data_dir, split="train", min_ascension=None):
    if split not in {"train","val"}:
        raise ValueError("Coverage audit accepts train/val; test evaluation is a separate explicit action")
    manifest = load_dataset_manifest(data_dir)
    if min_ascension is not None:
        if isinstance(min_ascension, bool) or not isinstance(min_ascension, int) or not 0 <= min_ascension <= 20:
            raise ValueError("min_ascension must be an integer from 0 to 20")
        if manifest.get("selection", {}).get("min_ascension") != min_ascension:
            raise ValueError(f"Dataset manifest must specify min_ascension={min_ascension}")
    files = sorted(Path(data_dir).glob(f"{split}_valid_chunk_*.parquet"))
    if not files:
        raise ValueError(f"No {split} shards found")
    names = [f"fine_{e}" for e in HAZARD_ENDPOINTS]+[f"coarse_{e}" for e in COARSE_ENDPOINTS]
    ends = np.array(HAZARD_ENDPOINTS+COARSE_ENDPOINTS)
    report = dict(split=split, preprocessing_version=manifest["preprocessing_version"],
        states=0, terminal_states=0, censored_states=0, resolutions={},
        floor_min=57.,floor_max=0.)
    if min_ascension is not None:
        report.update(required_min_ascension=min_ascension, ascension_min=20, ascension_max=0)
    train_positive,train_valid = np.zeros(21),np.zeros(21)
    outcomes = OutcomeAccumulator()
    for mode in ("full_fine","multiscale"):
        report["resolutions"][mode] = {name: dict(valid=0,stops=0,by_distance={
            label: dict(valid=0,stops=0) for label in ("1-3","4-8","9-16","17+")}) for name in names}
    for path in files:
        frame = _read_frame(str(path))
        if min_ascension is not None:
            ascension = frame["ascension"].to_numpy(dtype=float)
            if (not np.isfinite(ascension).all() or (ascension < min_ascension).any()
                    or (ascension > 20).any() or (ascension != np.floor(ascension)).any()):
                raise ValueError(f"Invalid or below-A{min_ascension} ascension in {path.name}")
            if len(ascension):
                report["ascension_min"] = min(report["ascension_min"], int(ascension.min()))
                report["ascension_max"] = max(report["ascension_max"], int(ascension.max()))
        outcomes.add(frame)
        f,t = frame["floor"].to_numpy(float),frame["terminal_floor"].to_numpy(float)
        known,heart = frame["terminal_known"].to_numpy(bool),frame["heart_victory"].to_numpy(bool)
        full_y,full_m = numpy_targets(f,t,known,heart,"full_fine")
        stored_y,stored_m = frame[list(TARGET_COLUMNS)].to_numpy(),frame[list(MASK_COLUMNS)].to_numpy(bool)
        expected_y,expected_m = full_y[:,list(range(20))+[23]],full_m[:,list(range(20))+[23]]
        if not np.array_equal(stored_y*stored_m,expected_y*expected_m) or not np.array_equal(stored_m,expected_m):
            raise ValueError(f"Stored fine labels disagree with raw outcomes: {path.name}")
        train_positive += (stored_y*stored_m).sum(0)
        train_valid += stored_m.sum(0)
        report["states"] += len(frame)
        report["terminal_states"] += int(known.sum())
        report["censored_states"] += int((~known).sum())
        report["floor_min"] = min(report["floor_min"],float(f.min()))
        report["floor_max"] = max(report["floor_max"],float(f.max()))
        distance = ends[None]-f[:,None]
        for mode in report["resolutions"]:
            y,m = numpy_targets(f,t,known,heart,mode)
            for i,name in enumerate(names):
                entry = report["resolutions"][mode][name]
                entry["valid"] += int(m[:,i].sum())
                entry["stops"] += int((y[:,i]*m[:,i]).sum())
                for label,low,high in (("1-3",1,3),("4-8",4,8),("9-16",9,16),("17+",17,57)):
                    keep = m[:,i] & (distance[:,i]>=low) & (distance[:,i]<=high)
                    entry["by_distance"][label]["valid"] += int(keep.sum())
                    entry["by_distance"][label]["stops"] += int((y[:,i]*keep).sum())
    if split == "train":
        validate_manifest_outcomes(outcomes.manifest_counts(), manifest)
        dimensions = manifest["distributions"]
        for counts,field in ((train_positive,"train_positive_samples_by_target"),(train_valid,"train_valid_samples_by_target")):
            expected = dimensions[field]
            if any(count != expected.get(name.removeprefix("target_"),0)
                   for name,count in zip(TARGET_COLUMNS,counts)):
                raise ValueError(f"Manifest {field} disagrees with parquet")
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir",default=Config.DATA_DIR)
    parser.add_argument("--split",choices=("train","val"),default="train")
    parser.add_argument("--min-ascension", type=int, help="Require this manifest threshold and verify every audited sample")
    parser.add_argument("--output",required=True)
    args = parser.parse_args(argv)
    if Path(args.output).exists():
        raise FileExistsError("Choose a new audit output path")
    write_json(args.output,inspect(args.data_dir,args.split,args.min_ascension))


if __name__ == "__main__":
    main()
