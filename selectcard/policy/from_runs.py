"""Export causal winning .run prefixes to isolated, auditable policy shards."""
import argparse
from collections import Counter
from concurrent.futures import ProcessPoolExecutor
from functools import lru_cache
import gzip
import json
from pathlib import Path
import sqlite3
from datetime import date

from selectcard.src.content_catalog import VanillaContentCatalog
from selectcard.src.reconstructor import RunReconstructor
from .contracts import CHARACTERS, digest
from .encoding import state_parts
from .profiles import RUN_PROFILE
from .run_replay import MODE_FLAGS, run_identity
from .shards import DATA_FORMAT, check_coverage, file_hash, group_key
from .training import pilot_member


def archive_values(stream):
    """Stream top-level arrays; retain at most one run and a 64-KiB buffer.

    The usual {event: run} envelope is one run. Multi-run array archives do
    not become a full Python list (compressed and plain input share this).
    """
    decoder = json.JSONDecoder()
    buffer = ""
    while not buffer.strip():
        chunk = stream.read(65536)
        if not chunk:
            raise ValueError("empty archive")
        buffer += chunk
    if not buffer.lstrip().startswith("["):
        yield json.loads(buffer+stream.read())
        return
    buffer = buffer.lstrip()[1:]
    eof, need_value, first = False, True, True
    while True:
        buffer = buffer.lstrip()
        if need_value and buffer.startswith("]"):
            if not first:
                raise ValueError("trailing comma in archive")
            if (buffer[1:]+stream.read()).strip():
                raise ValueError("trailing archive data")
            return
        if not need_value:
            if buffer.startswith("]"):
                if (buffer[1:]+stream.read()).strip():
                    raise ValueError("trailing archive data")
                return
            if buffer.startswith(","):
                buffer, need_value, first = buffer[1:], True, False
                continue
            if buffer:
                raise ValueError("missing archive comma")
        else:
            try:
                value, end = decoder.raw_decode(buffer)
                yield value
                buffer, need_value, first = buffer[end:], False, False
                continue
            except json.JSONDecodeError:
                if eof:
                    raise ValueError("invalid archive JSON")
        chunk = stream.read(65536)
        eof = not chunk
        buffer += chunk
        if eof and not buffer.strip():
            raise ValueError("unterminated archive")


class JsonSink:
    def __init__(self, handle):
        self.handle = handle

    def append(self, value):
        self.handle.write(json.dumps(value, separators=(",", ":"), ensure_ascii=True)+"\n")


@lru_cache(maxsize=1)
def catalog():
    path = Path(__file__).with_name("vanilla_catalog.json")
    return VanillaContentCatalog.from_payload(json.loads(path.read_text(encoding="utf-8")))


def unpack(value):
    if isinstance(value, list):
        for item in value:
            yield from unpack(item)
    elif isinstance(value, dict) and "event" in value:
        yield from unpack(value["event"])
    elif isinstance(value, dict):
        yield value
    else:
        raise ValueError("unsupported archive wrapper")


def eligible(run):
    if run.get("victory") is not True:
        return "not_victory"
    if any(key not in run for key in MODE_FLAGS if key != "special_seed"):
        return "unknown_run_mode"
    if any(run.get(key, 0) for key in MODE_FLAGS):
        return "nonordinary"
    if run.get("character_chosen") not in CHARACTERS or not 0 <= int(run.get("ascension_level", -1)) <= 20:
        return "unsupported_character_or_ascension"
    # Watcher-era standard PC rules only; unverified builds do not silently
    # inherit the seed/map assumptions of another game version.
    version = str(run.get("build_version", ""))
    if not version.startswith(("2020-", "2021-", "2022-")):
        return "unsupported_build_version"
    try:
        if date.fromisoformat(version[:10]) < date(2020, 1, 14):
            return "unsupported_build_version"
    except ValueError:
        return "unsupported_build_version"
    if "seed_played" not in run or not run.get("path_per_floor"):
        return "missing_seed_or_path"
    if any(symbol not in ("M", "E", "B", "R", "T", "$", "?", None) for symbol in run["path_per_floor"]):
        return "unsupported_path"
    return None


def process_file(argument):
    path, stage = argument[:2]
    include_map = argument[3] if len(argument) >= 4 else False
    # Worker output is spooled to disk instead of returning a large pickled
    # sample list. Tests can still request the simple in-memory file interface.
    if len(argument) >= 3:
        spool = Path(argument[2])
        with spool.with_suffix(".rows").open("w", encoding="utf-8") as row_file, spool.with_suffix(".audit").open("w", encoding="utf-8") as report_file:
            _process_file(path, stage, JsonSink(row_file), JsonSink(report_file), include_map=include_map)
        return spool.with_suffix(".rows"), spool.with_suffix(".audit")
    report, rows = [], []
    _process_file(path, stage, rows, report)
    return rows, report


def _process_file(path, stage, rows, report, *, include_map=False):
    try:
        opener = gzip.open if str(path).lower().endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8-sig") as stream:
            # Keep the stream open while the generator consumes array members.
            _process_runs((run for value in archive_values(stream) for run in unpack(value)), stage, rows, report, include_map=include_map)
    except (OSError, ValueError, TypeError) as exc:
        report.append(dict(status="invalid_archive", detail=str(exc)))


def _process_runs(runs, stage, rows, report, *, include_map=False):
    seen = set()
    for run in runs:
        try:
            identity = run_identity(run)
            if identity in seen:
                report.append(dict(status="duplicate_in_archive"))
                continue
            seen.add(identity)
            reason = eligible(run)
            if reason:
                report.append(dict(run_id=identity, status=reason))
                continue
            if stage == "pilot" and not pilot_member(identity):
                report.append(dict(run_id=identity, status="outside_pilot"))
                continue
            replayer = RunReconstructor(run, catalog(), mode="policy").policy_replay
            replayer.include_map = include_map
            count = 0
            for row in replayer.replay():
                try:
                    state_parts(row["pre_state"])  # enforce the frozen item cap
                except ValueError as exc:
                    replayer.blocked = str(exc)
                    replayer.stopped_floor = row["pre_state"]["floor"]
                    break
                rows.append(row)
                count += 1
            # This comparison is audit-only: never reconcile or remove an
            # earlier proven prefix because of a terminal mismatch.
            audit = dict(deck=Counter(replayer.deck) == Counter(run.get("master_deck") or []),
                         relics=Counter(replayer.relics) == Counter(run.get("relics") or []))
            report.append(dict(run_id=identity, status="accepted" if count else "no_supported_prefix", samples=count,
                               character=replayer.character, ascension=replayer.ascension, stopped=replayer.blocked,
                               stopped_floor=replayer.stopped_floor, excluded_decisions=replayer.exclusions,
                               replay_stats=dict(replayer.stats), terminal_audit=audit))
        except (ValueError, KeyError, TypeError) as exc:
            report.append(dict(status="invalid_run", detail=str(exc)))


def source_inventory(root):
    # Only the file inventory lives in memory; each archive is replayed once.
    files = sorted(p for p in root.rglob("*") if p.is_file() and str(p).lower().endswith((".run", ".json", ".json.gz")))
    return files, [dict(path=p.relative_to(root).as_posix(), size=p.stat().st_size, sha256=file_hash(p)) for p in files]


def code_fingerprint():
    package = Path(__file__).resolve().parent
    sources = list(package.glob("*.py")) + [package / "vanilla_catalog.json"]
    sources += [package.parent / "src" / name for name in ("reconstructor.py", "map_context.py", "sts_mapgen.py", "boss_context.py", "content_catalog.py")]
    return digest({p.relative_to(package.parent).as_posix(): file_hash(p) for p in sorted(sources)})


def export_runs(input_dir, output_dir, *, stage="pilot", workers=1, shard_size=10000, include_map=False):
    if stage not in ("pilot", "confirm"):
        raise ValueError("unknown export stage")
    if not 1 <= workers <= 15 or shard_size < 1:
        raise ValueError("workers must be 1..15; shard-size must be positive")
    input_dir, output_dir = Path(input_dir).resolve(), Path(output_dir).resolve()
    if not input_dir.is_dir():
        raise ValueError("input archive directory does not exist")
    if input_dir == output_dir or input_dir in output_dir.parents:
        raise ValueError("output must be outside input archive directory")
    files, inventory = source_inventory(input_dir)
    if not files:
        raise ValueError("no .run/.json/.json.gz archives")
    specification = dict(format=DATA_FORMAT, input_profile=RUN_PROFILE, stage=stage, cohort_fraction=.01 if stage == "pilot" else 1.,
                         pilot_sampling="run-hash-once-at-export", split="seed-group-80-10-10", shard_size=shard_size, sources=inventory, code=code_fingerprint())
    if include_map:
        specification["include_map"] = True
    fingerprint = digest(specification)
    if output_dir.exists():
        from .shards import verify_dataset
        existing = verify_dataset(output_dir)
        if existing["source_fingerprint"] != fingerprint:
            raise ValueError("existing output has incompatible inputs/code/profile; choose a new directory")
        if stage == "confirm":
            check_coverage(output_dir)
        elif not existing["report"]["splits"].get("train") or not existing["report"]["splits"].get("val"):
            raise ValueError("empty pilot train/val; choose additional data, not a resample")
        return existing
    output_dir.mkdir(parents=True)
    spool_dir = output_dir / ".spool"
    spool_dir.mkdir()
    connection = sqlite3.connect(output_dir / "index.sqlite")
    connection.executescript("""
        CREATE TABLE runs(run_id TEXT PRIMARY KEY);
        CREATE TABLE examples(split TEXT,ordinal INTEGER,group_key TEXT,run_id TEXT,character TEXT,task TEXT,seed_group TEXT,shard TEXT,offset INTEGER,length INTEGER,PRIMARY KEY(split,ordinal));
        CREATE INDEX sampling ON examples(split,group_key,run_id,ordinal);
    """)
    counts, coverage, reasons, stops, unknowns = (Counter() for _ in range(5))
    shard, shard_index, in_shard, total = None, -1, 0, 0
    executor = ProcessPoolExecutor(max_workers=workers) if workers > 1 else None
    try:
        # map() queues eagerly on older Python. Bound submissions to 2*workers
        # and retrieve in file order for deterministic offsets and fingerprints.
        def results():
            if executor is None:
                for index, path in enumerate(files):
                    yield path, process_file((path, stage, spool_dir / str(index), include_map))
            else:
                pending = {}
                for index in range(min(len(files), workers*2)):
                    pending[index] = executor.submit(process_file, (files[index], stage, spool_dir / str(index), include_map))
                for index, path in enumerate(files):
                    result = pending.pop(index).result()
                    next_index = index + workers*2
                    if next_index < len(files):
                        pending[next_index] = executor.submit(process_file, (files[next_index], stage, spool_dir / str(next_index), include_map))
                    yield path, result
        with (output_dir / "audit.jsonl").open("w", encoding="utf-8") as audit:
            for path, (rows_path, reports_path) in results():
                accepted = set()
                def read_jsonl(path):
                    with path.open(encoding="utf-8") as stream:
                        for line in stream:
                            yield json.loads(line)
                for report in read_jsonl(reports_path):
                    run_id = report.get("run_id")
                    if run_id and report["status"] != "outside_pilot":
                        inserted = connection.execute("INSERT OR IGNORE INTO runs VALUES(?)", (run_id,)).rowcount
                        if not inserted:
                            report = dict(report, status="duplicate_run")
                        elif report["status"] == "accepted":
                            accepted.add(run_id)
                    reasons[report["status"]] += 1
                    if report.get("stopped"):
                        stops[report["stopped"]] += 1
                    audit.write(json.dumps(dict(file=path.relative_to(input_dir).as_posix(), **report), ensure_ascii=True)+"\n")
                for row in read_jsonl(rows_path):
                    if row["run_id"] not in accepted:
                        continue
                    # Rows with equal candidates remain independent decisions.
                    if shard is None or in_shard >= shard_size:
                        if shard is not None:
                            shard.close()
                        shard_index += 1
                        name = f"shard-{shard_index:05d}.jsonl"
                        shard = (output_dir / name).open("wb")
                        in_shard = 0
                    encoded = (json.dumps(row, separators=(",", ":"), ensure_ascii=True)+"\n").encode()
                    offset = shard.tell()
                    shard.write(encoded)
                    split, state = row["split"], row["pre_state"]
                    group = group_key(row)
                    connection.execute("INSERT INTO examples VALUES(?,?,?,?,?,?,?,?,?,?)", (split, counts[split], group, row["run_id"], state["character"], row["decision_type"], row["seed_group"], name, offset, len(encoded)))
                    counts[split] += 1
                    coverage[split+"|"+group] += 1
                    coverage[f'{split}|{state["character"]}|A{state["ascension"]}|{row["decision_type"]}'] += 1
                    heart = row["outcome"]["heart_victory"]
                    coverage[split+"|outcome|"+("heart" if heart is True else "act3" if heart is False else "unknown")] += 1
                    unknowns.update(row["provenance"]["unknown_optional"])
                    in_shard += 1
                    total += 1
                connection.commit()
                rows_path.unlink()
                reports_path.unlink()
    finally:
        if executor:
            executor.shutdown(wait=True, cancel_futures=True)
        if shard:
            shard.close()
        connection.close()
    spool_dir.rmdir()
    report = dict(samples=total, splits=dict(counts), coverage=dict(coverage), reasons=dict(reasons), prefix_stops=dict(stops), unknown_context=dict(unknowns),
                  scope="archive inventory only; local samples are not global coverage", promotion="offline BC evidence only; existing game policy remains default")
    (output_dir / "coverage.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    data_files = {p.name: file_hash(p) for p in sorted(output_dir.iterdir()) if p.is_file()}
    manifest = dict(specification, source_fingerprint=fingerprint, fingerprint=digest([fingerprint, data_files]), report=report, files=data_files)
    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    if stage == "confirm":
        check_coverage(output_dir)
    elif not counts["train"] or not counts["val"]:
        raise ValueError("empty pilot train/val; report saved, no resampling or scope reduction")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--stage", choices=("pilot", "confirm"), default="pilot")
    parser.add_argument("--workers", type=int, default=1)
    parser.add_argument("--shard-size", type=int, default=10000)
    parser.add_argument("--include-map", action="store_true", help="export only unambiguous historical map decisions")
    args = parser.parse_args()
    manifest = export_runs(args.input_dir, args.output_dir, stage=args.stage, workers=args.workers, shard_size=args.shard_size, include_map=args.include_map)
    print(json.dumps(manifest["report"], ensure_ascii=True))


if __name__ == "__main__":
    main()
