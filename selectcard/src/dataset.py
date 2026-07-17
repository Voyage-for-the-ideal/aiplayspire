import bisect
import glob
import os
from functools import lru_cache

import pandas as pd
import torch
from torch.utils.data import Dataset

try:
    from .encoding import ItemVocabulary, PREPROCESSING_VERSION, encode_items, split_items
except ImportError:
    from encoding import ItemVocabulary, PREPROCESSING_VERSION, encode_items, split_items


REQUIRED_V2_COLUMNS = {
    "run_id",
    "split",
    "target_valid",
    "preprocessing_version",
}


class GlobalFeatureNormalizer:
    feature_names = ("floor", "hp", "gold", "ascension")

    def __init__(self, means=None, stds=None):
        self.means = dict(means or {name: 0.0 for name in self.feature_names})
        self.stds = dict(stds or {name: 1.0 for name in self.feature_names})

    @classmethod
    def fit(cls, frames):
        sums = {name: 0.0 for name in cls.feature_names}
        square_sums = {name: 0.0 for name in cls.feature_names}
        count = 0
        for frame in frames:
            if frame.empty:
                continue
            values = []
            for name in cls.feature_names:
                series = pd.to_numeric(frame[name], errors="coerce").fillna(0.0)
                values.append(torch.tensor(series.to_numpy(), dtype=torch.float64))
            stacked = torch.stack(values, dim=1)
            for index, name in enumerate(cls.feature_names):
                sums[name] += stacked[:, index].sum().item()
                square_sums[name] += stacked[:, index].square().sum().item()
            count += len(frame)
        if count == 0:
            raise ValueError("Cannot fit normalization without training samples")

        means = {name: sums[name] / count for name in cls.feature_names}
        stds = {}
        for name in cls.feature_names:
            variance = max(square_sums[name] / count - means[name] ** 2, 1e-8)
            stds[name] = variance**0.5
        return cls(means=means, stds=stds)

    def transform_row(self, row):
        return [
            (float(row.get(name, 0.0)) - self.means[name]) / self.stds[name]
            for name in self.feature_names
        ]

    def transform_state(self, state):
        return [
            (float(state.get(name, 0.0)) - self.means[name]) / self.stds[name]
            for name in self.feature_names
        ]

    def to_dict(self):
        return {
            "feature_names": list(self.feature_names),
            "means": self.means,
            "stds": self.stds,
        }

    @classmethod
    def from_dict(cls, value):
        if tuple(value.get("feature_names", ())) != cls.feature_names:
            raise ValueError("Checkpoint global feature order is incompatible")
        return cls(means=value["means"], stds=value["stds"])


def _read_v2_frame(path, columns=None):
    frame = pd.read_parquet(path, columns=columns)
    missing = REQUIRED_V2_COLUMNS.difference(frame.columns)
    if missing:
        raise ValueError(
            "Processed data is v1 and must be rebuilt with data_pipeline.py; "
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
    normalization_frames = []
    for path in files:
        frame = _read_v2_frame(path)
        train_frame = frame[(frame["split"] == "train") & frame["target_valid"].astype(bool)]
        normalization_frames.append(train_frame[list(GlobalFeatureNormalizer.feature_names)])
        for column in ("deck", "relics"):
            for raw_items in train_frame[column]:
                for item in split_items(raw_items):
                    vocabulary.add(item)
    vocabulary.freeze()
    return vocabulary, GlobalFeatureNormalizer.fit(normalization_frames)


class STSDataset(Dataset):
    def __init__(
        self,
        parquet_dir,
        vocabulary,
        normalizer,
        split,
        max_seq_len=64,
        max_upgrade=15,
        max_count=10,
    ):
        self.vocabulary = vocabulary
        self.normalizer = normalizer
        self.split = split
        self.max_seq_len = max_seq_len
        self.max_upgrade = max_upgrade
        self.max_count = max_count
        self.files = sorted(glob.glob(os.path.join(parquet_dir, "*.parquet")))
        self.file_indices = []
        self.cumulative_lengths = []
        total = 0

        for path in self.files:
            metadata = _read_v2_frame(
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
        return _read_v2_frame(self.files[file_idx])

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
        global_features = self.normalizer.transform_row(row)
        label = float(row["label"])
        return (
            torch.tensor(tokens, dtype=torch.long),
            torch.tensor(upgrades, dtype=torch.long),
            torch.tensor(counts, dtype=torch.long),
            torch.tensor(global_features, dtype=torch.float32),
            torch.tensor([label], dtype=torch.float32),
        )
