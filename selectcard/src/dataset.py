import bisect
import glob
import math
import os
from functools import lru_cache

import pandas as pd
import torch
from torch.utils.data import Dataset

try:
    from .encoding import ItemVocabulary, PREPROCESSING_VERSION, encode_items, split_items
except ImportError:
    from encoding import ItemVocabulary, PREPROCESSING_VERSION, encode_items, split_items


REQUIRED_COLUMNS = {
    "run_id",
    "split",
    "target_valid",
    "preprocessing_version",
}


class GlobalFeatureEncoder:
    schema_version = "global-features-v3"
    quantile = 0.995
    source_feature_names = ("floor", "hp", "max_hp", "gold", "ascension")
    feature_names = (
        "act_1",
        "act_2",
        "act_3_plus",
        "act_progress",
        "hp_ratio",
        "hp_absolute",
        "max_hp_absolute",
        "gold_log",
        "ascension",
    )

    def __init__(self, hp_q995=1.0, max_hp_q995=1.0, gold_q995=1.0):
        self.caps = {
            "hp_q995": self._positive_finite(hp_q995, "hp_q995"),
            "max_hp_q995": self._positive_finite(max_hp_q995, "max_hp_q995"),
            "gold_q995": self._positive_finite(gold_q995, "gold_q995"),
        }

    @staticmethod
    def _positive_finite(value, name):
        value = float(value)
        if not math.isfinite(value) or value <= 0.0:
            raise ValueError(f"{name} must be finite and greater than zero")
        return value

    @classmethod
    def fit(cls, frames):
        values = {name: [] for name in ("hp", "max_hp", "gold")}
        for frame in frames:
            if frame.empty:
                continue
            for name in values:
                series = pd.to_numeric(frame[name], errors="raise").astype("float64")
                if not series.map(math.isfinite).all():
                    raise ValueError(f"Training feature {name} contains non-finite values")
                if (series < 0.0).any() or (name == "max_hp" and (series <= 0.0).any()):
                    raise ValueError(f"Training feature {name} contains invalid values")
                values[name].append(series)
        if not values["hp"]:
            raise ValueError("Cannot fit global features without training samples")

        caps = {}
        for name, series_parts in values.items():
            series = pd.concat(series_parts, ignore_index=True)
            caps[f"{name}_q995"] = max(
                float(series.quantile(cls.quantile, interpolation="linear")), 1.0
            )
        return cls(**caps)

    @staticmethod
    def _clamp(value, lower=0.0, upper=1.0):
        return min(max(value, lower), upper)

    @staticmethod
    def _state_value(state, name):
        try:
            value = float(state[name])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError(f"Global feature {name} is missing or invalid") from exc
        if not math.isfinite(value):
            raise ValueError(f"Global feature {name} must be finite")
        return value

    def _transform(self, state):
        floor = self._state_value(state, "floor")
        hp = self._state_value(state, "hp")
        max_hp = self._state_value(state, "max_hp")
        gold = self._state_value(state, "gold")
        ascension = self._state_value(state, "ascension")
        if floor < 0.0 or hp < 0.0 or gold < 0.0:
            raise ValueError("floor, hp, and gold must be non-negative")
        if max_hp <= 0.0:
            raise ValueError("max_hp must be greater than zero")

        if floor <= 16.0:
            act = (1.0, 0.0, 0.0)
            act_progress = self._clamp(floor / 16.0)
        elif floor <= 33.0:
            act = (0.0, 1.0, 0.0)
            act_progress = self._clamp((floor - 17.0) / 16.0)
        else:
            act = (0.0, 0.0, 1.0)
            act_progress = self._clamp((floor - 34.0) / 16.0)

        return [
            *act,
            act_progress,
            self._clamp(hp / max_hp),
            self._clamp(hp / self.caps["hp_q995"]),
            self._clamp(max_hp / self.caps["max_hp_q995"]),
            self._clamp(
                math.log1p(gold) / math.log1p(self.caps["gold_q995"])
            ),
            self._clamp((ascension - 15.0) / 5.0),
        ]

    def transform_row(self, row):
        return self._transform(row)

    def transform_state(self, state):
        return self._transform(state)

    def to_dict(self):
        return {
            "schema_version": self.schema_version,
            "quantile": self.quantile,
            "feature_names": list(self.feature_names),
            "caps": dict(self.caps),
        }

    @classmethod
    def from_dict(cls, value):
        if not isinstance(value, dict):
            raise ValueError("Checkpoint global feature encoder is invalid")
        if value.get("schema_version") != cls.schema_version:
            raise ValueError("Checkpoint global feature schema is incompatible")
        if value.get("quantile") != cls.quantile:
            raise ValueError("Checkpoint global feature quantile is incompatible")
        if tuple(value.get("feature_names", ())) != cls.feature_names:
            raise ValueError("Checkpoint global feature order is incompatible")
        expected_caps = {"hp_q995", "max_hp_q995", "gold_q995"}
        if set(value.get("caps", {})) != expected_caps:
            raise ValueError("Checkpoint global feature caps are incomplete")
        try:
            return cls(**value["caps"])
        except (KeyError, TypeError) as exc:
            raise ValueError("Checkpoint global feature caps are incomplete") from exc


def _read_frame(path, columns=None):
    frame = pd.read_parquet(path, columns=columns)
    missing = REQUIRED_COLUMNS.difference(frame.columns)
    if missing:
        raise ValueError(
            "Processed data does not match the current schema; "
            "rebuild it with data_pipeline.py; "
            f"missing columns: {sorted(missing)}"
        )
    versions = set(frame["preprocessing_version"].dropna().unique())
    if versions != {PREPROCESSING_VERSION}:
        raise ValueError(f"Unsupported preprocessing versions: {sorted(versions)}")
    return frame


def build_training_artifacts(parquet_dir):
    files = sorted(glob.glob(os.path.join(parquet_dir, "*.parquet")))
    if not files:
        raise ValueError(f"No parquet files found in {parquet_dir}")

    vocabulary = ItemVocabulary()
    feature_frames = []
    for path in files:
        frame = _read_frame(path)
        train_frame = frame[(frame["split"] == "train") & frame["target_valid"].astype(bool)]
        feature_frames.append(train_frame[list(GlobalFeatureEncoder.source_feature_names)])
        for column in ("deck", "relics"):
            for raw_items in train_frame[column]:
                for item in split_items(raw_items):
                    vocabulary.add(item)
    vocabulary.freeze()
    return vocabulary, GlobalFeatureEncoder.fit(feature_frames)


class STSDataset(Dataset):
    def __init__(
        self,
        parquet_dir,
        vocabulary,
        feature_encoder,
        split,
        max_seq_len=64,
        max_upgrade=15,
        max_count=10,
    ):
        self.vocabulary = vocabulary
        self.feature_encoder = feature_encoder
        self.split = split
        self.max_seq_len = max_seq_len
        self.max_upgrade = max_upgrade
        self.max_count = max_count
        self.files = sorted(glob.glob(os.path.join(parquet_dir, "*.parquet")))
        self.file_indices = []
        self.cumulative_lengths = []
        total = 0

        for path in self.files:
            metadata = _read_frame(
                path,
                columns=["run_id", "split", "target_valid", "preprocessing_version"],
            )
            indices = metadata.index[
                (metadata["split"] == split) & metadata["target_valid"].astype(bool)
            ].tolist()
            self.file_indices.append(indices)
            total += len(indices)
            self.cumulative_lengths.append(total)
        self.total_samples = total

    def __len__(self):
        return self.total_samples

    @lru_cache(maxsize=16)
    def _get_df(self, file_idx):
        return _read_frame(self.files[file_idx])

    def __getitem__(self, idx):
        if idx < 0:
            idx += self.total_samples
        if idx < 0 or idx >= self.total_samples:
            raise IndexError(idx)
        file_idx = bisect.bisect_right(self.cumulative_lengths, idx)
        offset = 0 if file_idx == 0 else self.cumulative_lengths[file_idx - 1]
        row = self._get_df(file_idx).loc[self.file_indices[file_idx][idx - offset]]

        tokens, upgrades, counts = encode_items(
            row.get("deck", []),
            row.get("relics", []),
            self.vocabulary,
            self.max_seq_len,
            self.max_upgrade,
            self.max_count,
        )
        global_features = self.feature_encoder.transform_row(row)
        label = float(row["label"])
        return (
            torch.tensor(tokens, dtype=torch.long),
            torch.tensor(upgrades, dtype=torch.long),
            torch.tensor(counts, dtype=torch.long),
            torch.tensor(global_features, dtype=torch.float32),
            torch.tensor([label], dtype=torch.float32),
        )
