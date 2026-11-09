"""Disk-backed examples; SQLite stores offsets, never sample payloads in RAM."""
from collections import OrderedDict, defaultdict
import hashlib
import json
from pathlib import Path
import sqlite3

DATA_FORMAT = "run-policy-shards-v1"


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


def verify_dataset(root):
    from .contracts import digest
    root = Path(root)
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    if manifest["format"] != DATA_FORMAT:
        raise ValueError("incompatible sharded data")
    specification = {k:v for k,v in manifest.items() if k not in ("source_fingerprint", "fingerprint", "report", "files")}
    if digest(specification) != manifest["source_fingerprint"] or digest([manifest["source_fingerprint"], manifest["files"]]) != manifest["fingerprint"]:
        raise ValueError("dataset manifest fingerprint mismatch")
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


def check_coverage(root):
    from .contracts import CHARACTERS
    connection = sqlite3.connect(Path(root) / "index.sqlite")
    try:
        observed = set(connection.execute("SELECT DISTINCT split,character,task FROM examples"))
    finally:
        connection.close()
    missing = ["/".join((s,c,t)) for s in ("train", "val") for c in CHARACTERS for t in ("CARD_REWARD", "BOSS_REWARD") if (s,c,t) not in observed]
    if missing:
        raise ValueError("missing required coverage: " + ", ".join(missing))
