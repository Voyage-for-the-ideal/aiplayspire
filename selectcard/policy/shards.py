"""Disk-backed examples; SQLite stores offsets, never sample payloads in RAM."""
from collections import OrderedDict, defaultdict
from contextlib import closing
import hashlib
import json
from pathlib import Path
import sqlite3

DATA_FORMAT = "run-policy-shards-v1"
REWARD_TASKS = ("CARD_REWARD", "BOSS_REWARD")


def file_hash(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b""):
            h.update(chunk)
    return h.hexdigest()


def ascension_band(a):
    return "0-9" if a < 10 else "10-14" if a < 15 else "15-19" if a < 20 else "20"


def group_key(row):
    state = row["pre_state"]
    return "|".join((state["character"], ascension_band(state["ascension"]), row["decision_type"]))


def validate_min_ascension(value):
    if type(value) is not int or not 0 <= value <= 20:
        raise ValueError("min_ascension must be an integer in 0..20")
    return value


def read_dataset_manifest(root):
    """Verify the manifest envelope without requiring every archived member."""
    from .contracts import digest
    manifest = json.loads((Path(root) / "manifest.json").read_text(encoding="utf-8"))
    if manifest["format"] != DATA_FORMAT:
        raise ValueError("incompatible sharded data")
    specification = {k:v for k,v in manifest.items() if k not in ("source_fingerprint", "fingerprint", "report", "files")}
    if digest(specification) != manifest["source_fingerprint"] or digest([manifest["source_fingerprint"], manifest["files"]]) != manifest["fingerprint"]:
        raise ValueError("dataset manifest fingerprint mismatch")
    validate_min_ascension(manifest.get("min_ascension", 0))
    return manifest


def verify_dataset(root):
    root = Path(root)
    manifest = read_dataset_manifest(root)
    for name, expected in manifest["files"].items():
        if file_hash(root / name) != expected:
            raise ValueError("dataset fingerprint mismatch: " + name)
    if manifest["report"] != json.loads((root / "coverage.json").read_text(encoding="utf-8")):
        raise ValueError("coverage report differs from manifest")
    return manifest


class ShardDataset:
    def __init__(self, root, split, *, cache_size=4):
        self.root, self.split = Path(root).resolve(), split
        self.connection = sqlite3.connect((self.root / "index.sqlite").as_uri()+"?mode=ro", uri=True)
        self.count = self.connection.execute("SELECT count(*) FROM examples WHERE split=?", (split,)).fetchone()[0]
        self.cache_size, self.handles = cache_size, OrderedDict()
        self.groups = None

    def __len__(self):
        return self.count

    def _read(self, metadata):
        name, offset, length = metadata
        if name not in self.handles:
            self.handles[name] = (self.root / name).open("rb")
            if len(self.handles) > self.cache_size:
                self.handles.popitem(last=False)[1].close()
        self.handles.move_to_end(name)
        handle = self.handles[name]
        handle.seek(offset)
        return json.loads(handle.read(length))

    def __getitem__(self, index):
        if isinstance(index, slice):
            start, stop, step = index.indices(len(self))
            if step != 1:
                return [self[i] for i in range(start, stop, step)]
            rows = self.connection.execute("SELECT shard,offset,length FROM examples WHERE split=? AND ordinal>=? AND ordinal<? ORDER BY ordinal",
                                           (self.split, start, stop))
            return [self._read(row) for row in rows]
        if index < 0:
            index += len(self)
        metadata = self.connection.execute("SELECT shard,offset,length FROM examples WHERE split=? AND ordinal=?", (self.split, index)).fetchone()
        if metadata is None:
            raise IndexError(index)
        return self._read(metadata)

    def __iter__(self):
        for row in self.connection.execute("SELECT shard,offset,length FROM examples WHERE split=? ORDER BY ordinal", (self.split,)):
            yield self._read(row)

    def balanced_indices(self, count, rng):
        # At most one tuple per (group, run), rather than all example payloads
        # or a full epoch's sampled indices. SQL queries use covering indexes.
        if self.groups is None:
            groups = defaultdict(list)
            for group, run, n in self.connection.execute("SELECT group_key,run_id,count(*) FROM examples WHERE split=? GROUP BY group_key,run_id", (self.split,)):
                groups[group].append((run, n))
            self.groups = list(groups.items())
        for _ in range(count):
            group, runs = rng.choice(self.groups)
            run, n = rng.choice(runs)
            yield self.connection.execute("SELECT ordinal FROM examples WHERE split=? AND group_key=? AND run_id=? ORDER BY ordinal LIMIT 1 OFFSET ?",
                                          (self.split, group, run, rng.randrange(n))).fetchone()[0]

    def close(self):
        for handle in self.handles.values():
            handle.close()
        self.handles.clear()
        self.connection.close()


def _required_cells(min_ascension):
    from .contracts import CHARACTERS
    bands = ("15-19", "20") if min_ascension < 20 else ("20",)
    return [(s, "|".join((c, b, t))) for s in ("train", "val")
            for c in CHARACTERS for b in bands for t in REWARD_TASKS]


def ascension_coverage(root, min_ascension):
    """Count decisions, runs and seed groups for each train/val reward cell."""
    cells = _required_cells(min_ascension)
    index = (Path(root) / "index.sqlite").resolve()
    with closing(sqlite3.connect(index.as_uri()+"?mode=ro", uri=True)) as connection:
        observed = {(s, g): dict(decisions=n, runs=runs, seed_groups=seeds)
                    for s, g, n, runs, seeds in connection.execute(
                        """SELECT split,group_key,COUNT(*),COUNT(DISTINCT run_id),COUNT(DISTINCT seed_group)
                           FROM examples WHERE split IN ('train','val') GROUP BY split,group_key""")}
    return {f"{s}|{g}": observed.get((s, g), dict(decisions=0, runs=0, seed_groups=0))
            for s, g in cells}


def check_coverage(root, *, min_ascension=0):
    from .contracts import CHARACTERS
    index = (Path(root) / "index.sqlite").resolve()
    with closing(sqlite3.connect(index.as_uri()+"?mode=ro", uri=True)) as connection:
        if min_ascension >= 15:
            observed = set(connection.execute("SELECT DISTINCT split,group_key FROM examples WHERE split IN ('train','val')"))
            missing = [f"{s}/{g.replace('|', '/')}" for s, g in _required_cells(min_ascension)
                       if (s, g) not in observed]
        else:
            observed = set(connection.execute("SELECT DISTINCT split,character,task FROM examples WHERE split IN ('train','val')"))
            missing = ["/".join((s,c,t)) for s in ("train", "val") for c in CHARACTERS
                       for t in REWARD_TASKS if (s,c,t) not in observed]
    if missing:
        raise ValueError("missing required coverage: " + ", ".join(missing))
