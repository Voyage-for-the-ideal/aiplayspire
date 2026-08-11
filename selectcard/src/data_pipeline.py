import argparse
import concurrent.futures
import gzip
import hashlib
import json
import math
import os
import shutil
import time
import uuid
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

import pandas as pd

try:
    from .config import Config
    from .content_catalog import VanillaContentCatalog, canonical_card_id
    from .data_contract import FILTER_VERSION, PREPROCESSING_VERSION, ascension_band
    from .reconstructor import RunReconstructor
except ImportError:
    from config import Config
    from content_catalog import VanillaContentCatalog, canonical_card_id
    from data_contract import FILTER_VERSION, PREPROCESSING_VERSION, ascension_band
    from reconstructor import RunReconstructor


MIN_GAME_DATE = datetime(2020, 1, 14)
ALLOWED_CHARACTERS = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"}
STANDARD_FALSE_FLAGS = (
    "is_daily",
    "is_trial",
    "is_endless",
    "chose_seed",
    "is_beta",
)
MAX_REJECTION_EXAMPLES = 20
_WORKER_CATALOG = None


class RunRejected(ValueError):
    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


def stable_run_id(event_data):
    payload = json.dumps(event_data, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def assign_split(run_id):
    split_total = Config.TRAIN_SPLIT + Config.VAL_SPLIT + Config.TEST_SPLIT
    if abs(split_total - 1.0) > 1e-9:
        raise ValueError("TRAIN_SPLIT + VAL_SPLIT + TEST_SPLIT must equal 1")
    split_key = f"{Config.SPLIT_SEED}:{run_id}".encode("ascii")
    bucket = int(hashlib.sha256(split_key).hexdigest()[:8], 16) % 10000
    train_end = int(Config.TRAIN_SPLIT * 10000)
    val_end = train_end + int(Config.VAL_SPLIT * 10000)
    if bucket < train_end:
        return "train"
    if bucket < val_end:
        return "val"
    return "test"


def act_target(floor, floor_reached, terminal_known):
    boundary = 17 if floor <= 16 else 34 if floor <= 33 else 50
    if floor_reached >= boundary:
        return 1, True
    return 0, terminal_known


def _reject(reason):
    raise RunRejected(reason)


def _require_list(event_data, name, required=True):
    value = event_data.get(name)
    if value is None and not required:
        return []
    if not isinstance(value, list):
        _reject(f"invalid_{name}")
    return value


def _require_int(value, reason, lower=None, upper=None):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        _reject(reason)
    if not math.isfinite(value) or int(value) != value:
        _reject(reason)
    value = int(value)
    if lower is not None and value < lower:
        _reject(reason)
    if upper is not None and value > upper:
        _reject(reason)
    return value


def _parse_date(value, reason, compact=False):
    if not isinstance(value, str):
        _reject(reason)
    candidate = value[:8] if compact else value[:10]
    pattern = "%Y%m%d" if compact else "%Y-%m-%d"
    try:
        return datetime.strptime(candidate, pattern)
    except ValueError:
        _reject(reason)


def _validate_card(value, catalog, reason="unknown_card"):
    try:
        card_id = canonical_card_id(value)
    except ValueError:
        _reject("invalid_card_id")
    if card_id not in catalog.cards:
        _reject(reason)


def _validate_cards(values, catalog, reason="unknown_card"):
    if not isinstance(values, list):
        _reject("invalid_card_list")
    for value in values:
        _validate_card(value, catalog, reason)


def _validate_relic(value, catalog):
    try:
        relic_id = catalog.canonical_relic_id(value)
    except ValueError:
        _reject("unknown_relic")
    if relic_id not in catalog.relics:
        _reject("unknown_relic")


def _validate_relics(values, catalog):
    if not isinstance(values, list):
        _reject("invalid_relic_list")
    for value in values:
        _validate_relic(value, catalog)


def _validate_item(value, catalog):
    try:
        item_type = catalog.classify_item(value)
    except ValueError:
        _reject("invalid_item_id")
    if item_type is None:
        _reject("unknown_shop_item")


def _validate_parallel_lists(event_data, values_name, floors_name):
    values = _require_list(event_data, values_name, required=False)
    floors = _require_list(event_data, floors_name, required=False)
    if len(values) != len(floors):
        _reject(f"mismatched_{values_name}_and_{floors_name}")
    for floor in floors:
        _require_int(floor, f"invalid_{floors_name}", lower=0, upper=57)
    return values


def _validate_mode_and_version(event_data):
    for name in STANDARD_FALSE_FLAGS:
        if event_data.get(name) is not False:
            _reject(f"nonstandard_{name}")
    if "special_seed" in event_data and event_data["special_seed"] != 0:
        _reject("nonstandard_special_seed")

    ascension_mode = event_data.get("is_ascension_mode")
    if not isinstance(ascension_mode, bool):
        _reject("invalid_is_ascension_mode")
    level = _require_int(
        event_data.get("ascension_level"), "invalid_ascension_level", 0, 20
    )
    if (not ascension_mode and level != 0) or (ascension_mode and level == 0):
        _reject("inconsistent_ascension_mode")

    if _parse_date(event_data.get("local_time"), "invalid_local_time", True) < MIN_GAME_DATE:
        _reject("old_local_time")
    if _parse_date(event_data.get("build_version"), "invalid_build_version") < MIN_GAME_DATE:
        _reject("old_build_version")
    return level


def _validate_content(event_data, catalog):
    master_deck = _require_list(event_data, "master_deck")
    if not master_deck:
        _reject("missing_master_deck")
    _validate_cards(master_deck, catalog)

    relics = _require_list(event_data, "relics")
    if not relics:
        _reject("missing_relics")
    _validate_relics(relics, catalog)
    if "PrismaticShard" in relics:
        _reject("prismatic_shard")

    path_per_floor = _require_list(event_data, "path_per_floor")
    resource_histories = {
        name: _require_list(event_data, name)
        for name in ("current_hp_per_floor", "max_hp_per_floor", "gold_per_floor")
    }
    choices = _require_list(event_data, "card_choices")
    for choice in choices:
        if not isinstance(choice, dict):
            _reject("invalid_card_choice")
        choice_floor = _require_int(
            choice.get("floor"), "invalid_card_choice_floor", 0, 57
        )
        if choice_floor > 0:
            path_index = choice_floor - 1
            if path_index >= len(path_per_floor):
                _reject("incomplete_path_history")
            state_index = (
                choice_floor - 2
                if path_per_floor[path_index] == "?"
                else choice_floor - 1
            )
            if state_index >= 0 and any(
                state_index >= len(values) for values in resource_histories.values()
            ):
                _reject("incomplete_resource_history")
        not_picked = choice.get("not_picked")
        if not isinstance(not_picked, list):
            _reject("invalid_card_choice_candidates")
        _validate_cards(not_picked, catalog)
        picked = choice.get("picked")
        if picked not in {"SKIP", "Singing Bowl"}:
            _validate_card(picked, catalog)

    purged = _validate_parallel_lists(
        event_data, "items_purged", "items_purged_floors"
    )
    _validate_cards(purged, catalog)

    purchased = _validate_parallel_lists(
        event_data, "items_purchased", "item_purchase_floors"
    )
    for item in purchased:
        _validate_item(item, catalog)

    for item in _require_list(event_data, "relics_obtained", required=False):
        if not isinstance(item, dict):
            _reject("invalid_relics_obtained")
        _require_int(item.get("floor"), "invalid_relic_floor", 0, 57)
        _validate_relic(item.get("key"), catalog)

    for choice in _require_list(event_data, "boss_relics", required=False):
        if not isinstance(choice, dict):
            _reject("invalid_boss_relic_choice")
        picked = choice.get("picked")
        if picked:
            _validate_relic(picked, catalog)
        skipped = choice.get("not_picked", []) or []
        _validate_relics(skipped, catalog)

    for event in _require_list(event_data, "event_choices", required=False):
        if not isinstance(event, dict):
            _reject("invalid_event_choice")
        if "floor" in event:
            _require_int(event["floor"], "invalid_event_floor", 0, 57)
        for name in (
            "cards_obtained",
            "cards_removed",
            "cards_upgraded",
            "cards_transformed",
        ):
            if name in event:
                _validate_cards(event[name] or [], catalog)
        for name in ("relics_obtained", "relics_lost"):
            if name in event:
                _validate_relics(event[name] or [], catalog)

    for choice in _require_list(event_data, "campfire_choices", required=False):
        if not isinstance(choice, dict):
            _reject("invalid_campfire_choice")
        _require_int(choice.get("floor"), "invalid_campfire_floor", 0, 57)
        if choice.get("key") in {"SMITH", "PURGE"}:
            _validate_card(choice.get("data"), catalog)

    for potion in _require_list(event_data, "potions_obtained", required=False):
        if not isinstance(potion, dict) or potion.get("key") not in catalog.potions:
            _reject("unknown_potion")

    damage_taken = _require_list(event_data, "damage_taken")
    if not damage_taken:
        _reject("missing_enemy_history")
    for combat in damage_taken:
        if not isinstance(combat, dict):
            _reject("invalid_enemy_history")
        enemy = combat.get("enemies")
        if not isinstance(enemy, str) or enemy not in catalog.enemies:
            _reject("unknown_enemy")

    killed_by = event_data.get("killed_by")
    if killed_by is not None and (
        not isinstance(killed_by, str) or killed_by not in catalog.enemies
    ):
        _reject("unknown_killed_by")


def validate_raw_run(event_data, catalog):
    if not isinstance(event_data, dict):
        _reject("invalid_event")
    level = _validate_mode_and_version(event_data)
    floor_reached = _require_int(
        event_data.get("floor_reached"), "invalid_floor_reached", 1, 57
    )
    character = event_data.get("character_chosen")
    if character not in ALLOWED_CHARACTERS:
        _reject("unknown_character")
    if not isinstance(event_data.get("victory"), bool):
        _reject("invalid_victory")

    for name in ("current_hp_per_floor", "max_hp_per_floor", "gold_per_floor"):
        if not _require_list(event_data, name):
            _reject(f"missing_{name}")

    _validate_content(event_data, catalog)
    return {
        "ascension": level,
        "ascension_band": ascension_band(level),
        "floor_reached": floor_reached,
        "character": character,
    }


def _validate_snapshot(snapshot, expected_ascension):
    floor = _require_int(snapshot.get("floor"), "invalid_snapshot_floor", 0, 57)
    if snapshot.get("ascension") != expected_ascension:
        _reject("invalid_snapshot_ascension")
    for name in ("hp", "max_hp", "gold"):
        value = snapshot.get(name)
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            _reject(f"invalid_snapshot_{name}")
        if not math.isfinite(value):
            _reject(f"invalid_snapshot_{name}")
    if snapshot["hp"] < 0 or snapshot["max_hp"] <= 0 or snapshot["gold"] < 0:
        _reject("invalid_snapshot_resources")
    if snapshot["hp"] > snapshot["max_hp"]:
        _reject("invalid_snapshot_hp_ratio")
    for name in ("deck", "relics", "candidates"):
        values = snapshot.get(name)
        if not isinstance(values, list) or not all(
            isinstance(value, str) and value for value in values
        ):
            _reject(f"invalid_snapshot_{name}")
    if not snapshot["candidates"]:
        _reject("empty_snapshot_candidates")
    return floor


def process_run(event_data, catalog):
    run_id = stable_run_id(event_data)
    validated = validate_raw_run(event_data, catalog)
    recon = RunReconstructor(event_data, content_catalog=catalog)
    snapshots = list(recon.replay())
    if not recon.is_match_with_master_deck():
        _reject("deck_reconstruction_mismatch")
    if not recon.is_match_with_final_relics():
        _reject("relic_reconstruction_mismatch")
    if not snapshots:
        _reject("no_choice_samples")

    split = assign_split(run_id)
    terminal_known = bool(recon.is_victory or recon.killed_by)
    rows = []
    for snapshot in snapshots:
        floor = _validate_snapshot(snapshot, validated["ascension"])
        label, target_valid = act_target(
            floor, validated["floor_reached"], terminal_known
        )
        rows.append(
            {
                **snapshot,
                "label": label,
                "target_valid": target_valid,
                "run_id": run_id,
                "split": split,
                "preprocessing_version": PREPROCESSING_VERSION,
                "filter_version": FILTER_VERSION,
                "ascension_band": validated["ascension_band"],
                "deck": ",".join(snapshot["deck"]),
                "relics": ",".join(snapshot["relics"]),
                "candidates": ",".join(snapshot["candidates"]),
            }
        )
    return {
        "run_id": run_id,
        "character": validated["character"],
        "ascension": validated["ascension"],
        "ascension_band": validated["ascension_band"],
        "split": split,
        "rows": rows,
    }


def _record_rejection(stats, reason, example):
    stats["rejections"][reason] += 1
    examples = stats["examples"][reason]
    if len(examples) < MAX_REJECTION_EXAMPLES:
        examples.append(example)


def _new_file_stats():
    return {
        "raw_runs": 0,
        "rejections": Counter(),
        "examples": defaultdict(list),
    }


def _load_runs(filepath):
    if str(filepath).endswith(".gz"):
        with gzip.open(filepath, "rt", encoding="utf-8") as handle:
            data = json.load(handle)
    else:
        with open(filepath, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    return data if isinstance(data, list) else [data]


def process_file(filepath, catalog=None):
    catalog = catalog or _WORKER_CATALOG or VanillaContentCatalog.from_repo()
    stats = _new_file_stats()
    accepted = []
    try:
        runs = _load_runs(filepath)
    except Exception as exc:
        _record_rejection(
            stats,
            f"archive_error:{type(exc).__name__}",
            hashlib.sha256(str(filepath).encode("utf-8")).hexdigest(),
        )
        return {"accepted": accepted, "stats": stats}

    local_seen = set()
    for wrapper in runs:
        stats["raw_runs"] += 1
        event_data = wrapper.get("event", wrapper) if isinstance(wrapper, dict) else wrapper
        try:
            run_id = stable_run_id(event_data)
        except Exception:
            run_id = hashlib.sha256(repr(event_data).encode("utf-8")).hexdigest()
        if run_id in local_seen:
            _record_rejection(stats, "duplicate_run", run_id)
            continue
        local_seen.add(run_id)
        try:
            accepted.append(process_run(event_data, catalog))
        except RunRejected as exc:
            _record_rejection(stats, exc.reason, run_id)
        except Exception as exc:
            _record_rejection(stats, f"run_error:{type(exc).__name__}", run_id)
    return {"accepted": accepted, "stats": stats}


def _init_worker(catalog_payload):
    global _WORKER_CATALOG
    _WORKER_CATALOG = VanillaContentCatalog.from_payload(catalog_payload)


def _discover_files(data_dir, max_files_per_directory=None):
    files = []
    for directory, _, names in os.walk(data_dir):
        candidates = sorted(
            os.path.join(directory, name)
            for name in names
            if name.endswith(".json") or name.endswith(".json.gz")
        )
        if max_files_per_directory is not None:
            candidates = candidates[:max_files_per_directory]
        files.extend(candidates)
    return sorted(files)


def _iter_file_results(files, catalog, workers):
    if workers == 1:
        for path in files:
            yield path, process_file(path, catalog)
        return

    max_pending = workers * 2
    file_iter = iter(files)
    with concurrent.futures.ProcessPoolExecutor(
        max_workers=workers,
        initializer=_init_worker,
        initargs=(catalog.to_payload(),),
    ) as executor:
        pending = {}
        for _ in range(min(max_pending, len(files))):
            path = next(file_iter)
            pending[executor.submit(process_file, path)] = path

        while pending:
            done, _ = concurrent.futures.wait(
                pending, return_when=concurrent.futures.FIRST_COMPLETED
            )
            for future in done:
                path = pending.pop(future)
                yield path, future.result()
                try:
                    next_path = next(file_iter)
                except StopIteration:
                    continue
                pending[executor.submit(process_file, next_path)] = next_path


def _merge_stats(target, source):
    target["raw_runs"] += source["raw_runs"]
    target["rejections"].update(source["rejections"])
    for reason, values in source["examples"].items():
        remaining = MAX_REJECTION_EXAMPLES - len(target["examples"][reason])
        if remaining > 0:
            target["examples"][reason].extend(values[:remaining])


def _publish_staging(staging, output_dir, replace):
    output = Path(output_dir)
    if output.exists() and not replace:
        raise FileExistsError(f"Output directory already exists: {output}")
    if not output.exists():
        os.replace(staging, output)
        return

    backup = output.with_name(f".{output.name}.backup-{uuid.uuid4().hex}")
    os.replace(output, backup)
    try:
        os.replace(staging, output)
    except Exception:
        os.replace(backup, output)
        raise
    shutil.rmtree(backup)


def _validate_staging(staging, expected_samples):
    parquet_files = sorted(Path(staging).glob("*.parquet"))
    if expected_samples <= 0 or not parquet_files:
        raise ValueError("No accepted training samples were generated")
    required = {
        "run_id",
        "split",
        "target_valid",
        "preprocessing_version",
        "filter_version",
        "ascension_band",
    }
    columns = set(pd.read_parquet(parquet_files[0]).columns)
    missing = required.difference(columns)
    if missing:
        raise ValueError(f"Generated parquet schema is incomplete: {sorted(missing)}")
    manifest_path = Path(staging) / "dataset_manifest.json"
    if not manifest_path.is_file():
        raise ValueError("Generated dataset manifest is missing")


def build_dataset(
    data_dir,
    output_dir,
    chunk_size=50000,
    workers=None,
    max_files_per_directory=None,
    replace=False,
    repo_root=None,
):
    files = _discover_files(data_dir, max_files_per_directory)
    if not files:
        raise ValueError(f"No JSON files found under {data_dir}")
    workers = workers or max(1, (os.cpu_count() or 2) - 2)
    if workers < 1:
        raise ValueError("workers must be at least one")

    output = Path(output_dir).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists() and not replace:
        raise FileExistsError(f"Output directory already exists: {output}")
    staging = output.with_name(f".{output.name}.building-{uuid.uuid4().hex}")
    staging.mkdir()

    catalog = VanillaContentCatalog.from_repo(repo_root)
    started = time.monotonic()
    stats = _new_file_stats()
    dimensions = {
        "runs_by_character": Counter(),
        "runs_by_ascension": Counter(),
        "runs_by_ascension_band": Counter(),
        "runs_by_split": Counter(),
        "samples_by_split": Counter(),
        "samples_by_label": Counter(),
        "valid_samples_by_split": Counter(),
        "train_valid_samples_by_ascension_band": Counter(),
    }
    seen_run_keys = set()
    accepted_runs = 0
    sample_count = 0
    valid_sample_count = 0
    chunks = 0
    partition_chunks = Counter()
    buffers = defaultdict(list)

    def flush(partition):
        nonlocal chunks
        buffer = buffers[partition]
        if not buffer:
            return
        frame = pd.DataFrame(buffer)
        index = partition_chunks[partition]
        frame.to_parquet(
            staging / f"{partition}_chunk_{index:05d}.parquet", index=False
        )
        partition_chunks[partition] += 1
        chunks += 1
        buffers[partition] = []

    try:
        for completed, (_path, result) in enumerate(
            _iter_file_results(files, catalog, workers), start=1
        ):
            _merge_stats(stats, result["stats"])
            for run in result["accepted"]:
                run_key = int(run["run_id"][:32], 16)
                if run_key in seen_run_keys:
                    _record_rejection(stats, "duplicate_run", run["run_id"])
                    continue
                seen_run_keys.add(run_key)
                accepted_runs += 1
                dimensions["runs_by_character"][run["character"]] += 1
                dimensions["runs_by_ascension"][str(run["ascension"])] += 1
                dimensions["runs_by_ascension_band"][str(run["ascension_band"])] += 1
                dimensions["runs_by_split"][run["split"]] += 1
                for row in run["rows"]:
                    sample_count += 1
                    dimensions["samples_by_split"][row["split"]] += 1
                    dimensions["samples_by_label"][str(row["label"])] += 1
                    if row["target_valid"]:
                        valid_sample_count += 1
                        dimensions["valid_samples_by_split"][row["split"]] += 1
                        if row["split"] == "train":
                            dimensions["train_valid_samples_by_ascension_band"][
                                str(row["ascension_band"])
                            ] += 1
                    status = "valid" if row["target_valid"] else "censored"
                    partition = f"{row['split']}_{status}"
                    buffers[partition].append(row)
                    if len(buffers[partition]) >= chunk_size:
                        flush(partition)
            if completed % 100 == 0 or completed == len(files):
                print(
                    f"Processed {completed}/{len(files)} files; "
                    f"accepted_runs={accepted_runs} samples={sample_count}"
                )
        for partition in list(buffers):
            flush(partition)

        elapsed = time.monotonic() - started
        manifest = {
            "preprocessing_version": PREPROCESSING_VERSION,
            "filter_version": FILTER_VERSION,
            "created_at_utc": datetime.utcnow().isoformat(timespec="seconds") + "Z",
            "input": {
                "files": len(files),
                "compressed_bytes": sum(os.path.getsize(path) for path in files),
                "raw_runs": stats["raw_runs"],
            },
            "output": {
                "accepted_runs": accepted_runs,
                "samples": sample_count,
                "valid_samples": valid_sample_count,
                "parquet_files": chunks,
                "parquet_files_by_partition": dict(sorted(partition_chunks.items())),
            },
            "rejections": dict(sorted(stats["rejections"].items())),
            "rejection_examples": {
                reason: values for reason, values in sorted(stats["examples"].items())
            },
            "distributions": {
                name: dict(sorted(values.items())) for name, values in dimensions.items()
            },
            "content_catalog": catalog.summary(),
            "performance": {
                "elapsed_seconds": elapsed,
                "raw_runs_per_second": stats["raw_runs"] / elapsed if elapsed else None,
                "samples_per_second": sample_count / elapsed if elapsed else None,
                "workers": workers,
            },
        }
        with open(staging / "dataset_manifest.json", "w", encoding="utf-8") as handle:
            json.dump(manifest, handle, ensure_ascii=True, indent=2, sort_keys=True)
        _validate_staging(staging, sample_count)
        _publish_staging(staging, output, replace)
    except Exception:
        if staging.exists():
            shutil.rmtree(staging)
        raise

    print(
        f"Generated {sample_count} samples from {accepted_runs} runs in "
        f"{time.monotonic() - started:.1f}s at {output}"
    )
    return manifest


def parse_args(argv=None):
    base_dir = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description="Build the standard-vanilla STS dataset")
    parser.add_argument("--input-dir", default=str(base_dir / "STS Data"))
    parser.add_argument("--output-dir", default=str(base_dir / "processed_data_v2"))
    parser.add_argument("--workers", type=int, default=None)
    parser.add_argument("--chunk-size", type=int, default=50000)
    parser.add_argument("--max-files-per-directory", type=int, default=None)
    parser.add_argument("--replace", action="store_true")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    build_dataset(
        args.input_dir,
        args.output_dir,
        chunk_size=args.chunk_size,
        workers=args.workers,
        max_files_per_directory=args.max_files_per_directory,
        replace=args.replace,
    )


if __name__ == "__main__":
    main()
