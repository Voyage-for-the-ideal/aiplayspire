import bisect
import glob
import hashlib
import json
import math
import os
from functools import lru_cache

import pandas as pd
import torch
from torch.utils.data import Dataset, Sampler

try:
    from .data_contract import (
        FILTER_VERSION, HAZARD_ENDPOINTS, MASK_COLUMNS, PREPROCESSING_VERSION,
        TARGET_COLUMNS, VALUE_TARGET_SCHEMA,
    )
    from .encoding import ItemVocabulary, encode_items, split_items
    from .boss_context import boss_id
except ImportError:
    from data_contract import (
        FILTER_VERSION, HAZARD_ENDPOINTS, MASK_COLUMNS, PREPROCESSING_VERSION,
        TARGET_COLUMNS, VALUE_TARGET_SCHEMA,
    )
    from encoding import ItemVocabulary, encode_items, split_items
    from boss_context import boss_id


REQUIRED_COLUMNS = {
    "run_id",
    "split",
    *TARGET_COLUMNS,
    *MASK_COLUMNS,
    "preprocessing_version",
    "filter_version",
    "ascension_band",
    "visible_boss",
}

TRAINING_ARTIFACT_CACHE_NAME = "training_artifacts.json"
TRAINING_ARTIFACT_CACHE_VERSION = 1
ARTIFACT_PROGRESS_INTERVAL = 25


class GlobalFeatureEncoder:
    schema_version = "global-features-v6"
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
        if ascension < 0.0 or ascension > 20.0 or int(ascension) != ascension:
            raise ValueError("ascension must be an integer from 0 to 20")

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
            self._clamp(ascension / 20.0),
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
    filter_versions = set(frame["filter_version"].dropna().unique())
    if filter_versions != {FILTER_VERSION}:
        raise ValueError(f"Unsupported filter versions: {sorted(filter_versions)}")
    return frame


def load_dataset_manifest(parquet_dir):
    path = os.path.join(parquet_dir, "dataset_manifest.json")
    try:
        with open(path, "r", encoding="utf-8") as handle:
            manifest = json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"Dataset manifest is missing or invalid: {path}") from exc
    if manifest.get("preprocessing_version") != PREPROCESSING_VERSION:
        raise ValueError("Dataset manifest preprocessing version is incompatible")
    if manifest.get("filter_version") != FILTER_VERSION:
        raise ValueError("Dataset manifest filter version is incompatible")
    if manifest.get("value_target_schema") != VALUE_TARGET_SCHEMA:
        raise ValueError("Dataset manifest value target is incompatible")
    if tuple(manifest.get("hazard_endpoints", ())) != HAZARD_ENDPOINTS:
        raise ValueError("Dataset manifest hazard endpoints are incompatible")
    return manifest


def _artifact_source_fingerprint(files):
    source_files = []
    for path in files:
        stat = os.stat(path)
        source_files.append(
            (os.path.basename(path), stat.st_size, stat.st_mtime_ns)
        )
    encoded = json.dumps(source_files, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _load_training_artifact_cache(cache_path, source_fingerprint):
    with open(cache_path, "r", encoding="utf-8") as handle:
        cached = json.load(handle)
    if cached.get("cache_version") != TRAINING_ARTIFACT_CACHE_VERSION:
        raise ValueError("cache format version is incompatible")
    if cached.get("preprocessing_version") != PREPROCESSING_VERSION:
        raise ValueError("cache preprocessing version is incompatible")
    if cached.get("filter_version") != FILTER_VERSION:
        raise ValueError("cache filter version is incompatible")
    if cached.get("source_fingerprint") != source_fingerprint:
        raise ValueError("training parquet files changed")
    return (
        ItemVocabulary.from_dict(cached["vocabulary"]),
        GlobalFeatureEncoder.from_dict(cached["global_feature_encoder"]),
    )


def _save_training_artifact_cache(
    cache_path, source_fingerprint, vocabulary, feature_encoder
):
    payload = {
        "cache_version": TRAINING_ARTIFACT_CACHE_VERSION,
        "preprocessing_version": PREPROCESSING_VERSION,
        "filter_version": FILTER_VERSION,
        "source_fingerprint": source_fingerprint,
        "vocabulary": vocabulary.to_dict(),
        "global_feature_encoder": feature_encoder.to_dict(),
    }
    temporary_path = f"{cache_path}.{os.getpid()}.tmp"
    try:
        with open(temporary_path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=True, indent=2, sort_keys=True)
        os.replace(temporary_path, cache_path)
    finally:
        if os.path.exists(temporary_path):
            os.remove(temporary_path)


def build_training_artifacts(parquet_dir, progress=None, use_cache=True):
    files = sorted(glob.glob(os.path.join(parquet_dir, "train_valid_chunk_*.parquet")))
    if not files:
        raise ValueError(f"No parquet files found in {parquet_dir}")

    cache_path = os.path.join(parquet_dir, TRAINING_ARTIFACT_CACHE_NAME)
    source_fingerprint = _artifact_source_fingerprint(files)
    if use_cache and os.path.exists(cache_path):
        try:
            artifacts = _load_training_artifact_cache(
                cache_path, source_fingerprint
            )
        except (KeyError, OSError, TypeError, ValueError, json.JSONDecodeError) as exc:
            if progress:
                progress(f"Ignoring invalid training artifact cache: {exc}")
        else:
            if progress:
                progress(f"Loaded training artifacts from {cache_path}")
            return artifacts

    if progress:
        progress(
            f"Building training artifacts from {len(files):,} parquet shards"
        )

    vocabulary = ItemVocabulary()
    feature_frames = []
    for file_index, path in enumerate(files, start=1):
        frame = _read_frame(path)
        train_frame = frame[
            (frame["split"] == "train")
            & frame[list(MASK_COLUMNS)].astype(bool).any(axis=1)
        ]
        feature_frames.append(train_frame[list(GlobalFeatureEncoder.source_feature_names)])
        for column in ("deck", "relics"):
            for raw_items in train_frame[column]:
                for item in split_items(raw_items):
                    vocabulary.add(item)
        if progress and (
            file_index == 1
            or file_index % ARTIFACT_PROGRESS_INTERVAL == 0
            or file_index == len(files)
        ):
            progress(
                f"Artifact scan: {file_index:,}/{len(files):,} shards "
                f"({file_index / len(files):.1%})"
            )
    vocabulary.freeze()
    if progress:
        progress("Computing global feature quantiles")
    feature_encoder = GlobalFeatureEncoder.fit(feature_frames)
    if progress:
        progress("Global feature quantiles ready")
    if use_cache:
        try:
            _save_training_artifact_cache(
                cache_path, source_fingerprint, vocabulary, feature_encoder
            )
        except OSError as exc:
            if progress:
                progress(f"Could not save training artifact cache: {exc}")
        else:
            if progress:
                progress(f"Saved training artifacts to {cache_path}")
    return vocabulary, feature_encoder


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
        progress=None,
    ):
        self.vocabulary = vocabulary
        self.feature_encoder = feature_encoder
        self.split = split
        self.max_seq_len = max_seq_len
        self.max_upgrade = max_upgrade
        self.max_count = max_count
        self.files = sorted(
            glob.glob(os.path.join(parquet_dir, f"{split}_valid_chunk_*.parquet"))
        )
        self.cumulative_lengths = []
        total = 0

        for file_index, path in enumerate(self.files, start=1):
            metadata = _read_frame(
                path,
                columns=[
                    "run_id",
                    "split",
                    *TARGET_COLUMNS,
                    *MASK_COLUMNS,
                    "preprocessing_version",
                    "filter_version",
                    "ascension_band",
                    "visible_boss",
                ],
            )
            if set(metadata["split"].unique()) != {split}:
                raise ValueError(f"Dataset shard has unexpected split: {path}")
            if not metadata[list(MASK_COLUMNS)].astype(bool).any(axis=1).all():
                raise ValueError(f"Dataset shard contains censored samples: {path}")
            total += len(metadata)
            self.cumulative_lengths.append(total)
            if progress and (
                file_index == 1
                or file_index % 100 == 0
                or file_index == len(self.files)
            ):
                progress(
                    f"Indexed {split} dataset: {file_index:,}/{len(self.files):,} "
                    f"shards, {total:,} samples"
                )
        self.total_samples = total

    def __len__(self):
        return self.total_samples

    # Each DataLoader worker owns this cache, so keep it small under multiprocessing.
    @lru_cache(maxsize=2)
    def _get_df(self, file_idx):
        return _read_frame(self.files[file_idx])

    def __getitem__(self, idx):
        if idx < 0:
            idx += self.total_samples
        if idx < 0 or idx >= self.total_samples:
            raise IndexError(idx)
        file_idx = bisect.bisect_right(self.cumulative_lengths, idx)
        offset = 0 if file_idx == 0 else self.cumulative_lengths[file_idx - 1]
        row = self._get_df(file_idx).iloc[idx - offset]

        tokens, upgrades, counts = encode_items(
            row.get("deck", []),
            row.get("relics", []),
            self.vocabulary,
            self.max_seq_len,
            self.max_upgrade,
            self.max_count,
        )
        global_features = self.feature_encoder.transform_row(row)
        encoded_boss = boss_id(row["visible_boss"])
        targets = [float(row[column]) for column in TARGET_COLUMNS]
        masks = [float(bool(row[column])) for column in MASK_COLUMNS]
        return (
            torch.tensor(tokens, dtype=torch.long),
            torch.tensor(upgrades, dtype=torch.long),
            torch.tensor(counts, dtype=torch.long),
            torch.tensor(global_features, dtype=torch.float32),
            torch.tensor(encoded_boss, dtype=torch.long),
            torch.tensor(float(row["floor"]), dtype=torch.float32),
            torch.tensor(targets, dtype=torch.float32),
            torch.tensor(masks, dtype=torch.float32),
        )


class ChunkShuffleSampler(Sampler):
    """Shuffle bounded-size parquet shards without allocating a dataset-size permutation."""

    def __init__(self, dataset, generator=None):
        self.dataset = dataset
        self.generator = generator

    def __len__(self):
        return len(self.dataset)

    def __iter__(self):
        file_count = len(self.dataset.cumulative_lengths)
        file_order = torch.randperm(file_count, generator=self.generator).tolist()
        for file_idx in file_order:
            start = 0 if file_idx == 0 else self.dataset.cumulative_lengths[file_idx - 1]
            stop = self.dataset.cumulative_lengths[file_idx]
            for local_idx in torch.randperm(
                stop - start, generator=self.generator
            ).tolist():
                yield start + local_idx
