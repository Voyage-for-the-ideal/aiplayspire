"""Gated, hash-chained Headless episodes. Interrupted runs remain censored."""
from __future__ import annotations

import gzip
import hashlib
import json
import os
from pathlib import Path
import time
import uuid

COLLECTION_SCHEMA = "headless-episode-v1"
ENGINE_COMMIT = "b354a9ee8e118e906d9b9f55ba043100cc1ac6c7"


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


class EpisodeWriter:
    def __init__(self, root, *, role, seed, policy, max_bytes=512*1024*1024):
        self.root = Path(root).resolve()
        gate = self.root / "ALLOW_COLLECTION"
        if not gate.is_file() or gate.is_symlink(): raise ValueError("Expected explicit local collection gate")
        self.directory = self.root / "episodes"; self.directory.mkdir(exist_ok=True)
        self.episode_id = uuid.uuid4().hex; self.role = role; self.seed = seed
        self.pending = self.directory / (self.episode_id + ".jsonl.gz.part")
        self.stream = gzip.open(self.pending, "wt", encoding="utf-8", compresslevel=1)
        self.chain = "0"*64; self.count = self.transitions = self.bytes = 0
        self.max_bytes = max_bytes; self.closed = False; self.last_sequence = None; self.last_floor = None
        self.pending_transition = None
        self.write({"type": "header", "schema": COLLECTION_SCHEMA, "episode_id": self.episode_id,
            "role": role, "seed": seed, "seed_group": f'headless:{ENGINE_COMMIT}:{seed}',
            "engine_commit": ENGINE_COMMIT, "rng_policy": "headless-b354a9ee-v1", "policy": policy,
            "started_unix": time.time(), "corpus": "headless-native-v1", "legacy_training_admission": False})

    def write(self, record):
        if self.closed: raise ValueError("Episode already sealed")
        encoded = canonical(record)
        self.bytes += len(encoded.encode("utf-8"))
        if self.bytes > self.max_bytes and record.get("type") != "footer": raise ValueError("Episode byte budget exceeded")
        digest = hashlib.sha256((self.chain + "\n" + encoded).encode("utf-8")).hexdigest()
        self.stream.write(canonical({"index": self.count, "previous": self.chain, "digest": digest, "record": record}) + "\n")
        self.stream.flush(); self.chain = digest; self.count += 1

    def observation(self, reply):
        sequence = reply["sequence"]; state = reply["state"]
        if self.last_sequence is not None and sequence <= self.last_sequence: raise ValueError("Observation sequence did not advance")
        if state.get("player", {}).get("className") != self.role: raise ValueError("Episode role changed")
        if state.get("seed") != self.seed: raise ValueError("Episode seed changed")
        if self.last_floor is not None and state["floor"] < self.last_floor: raise ValueError("Episode floor rewound")
        record = {"type": "observation", "sequence": sequence, "state_token": reply["state_token"], "state": state}
        if self.last_sequence is not None:
            if self.pending_transition is None or sequence != self.pending_transition["after_sequence"]: raise ValueError("Observation lacks a contiguous transition")
            size = len(canonical(self.pending_transition).encode())+len(canonical(record).encode())
            if self.bytes+size > self.max_bytes: raise ValueError("Episode byte budget exceeded")
            self.write(self.pending_transition); self.write(record)
            self.pending_transition = None; self.transitions += 1
        else: self.write(record)
        self.last_sequence, self.last_floor = sequence, state["floor"]

    def transition(self, *, before_sequence, after_sequence, method, args, effect, policy):
        if effect != "APPLIED": raise ValueError("Only verified transitions enter accepted episodes")
        if self.pending_transition is not None: raise ValueError("Previous transition lacks its observation")
        if before_sequence != self.last_sequence or after_sequence != before_sequence + 1: raise ValueError("Transition sequence gap")
        self.pending_transition = {"type": "transition", "before_sequence": before_sequence, "after_sequence": after_sequence,
                    "native_method": method, "native_args": list(args), "effect": effect, "policy": policy}

    def finish(self, terminal=None, error=None):
        if self.closed: raise ValueError("Episode already sealed")
        complete = terminal is not None and error is None
        if complete and self.pending_transition is not None: raise ValueError("Unobserved transition at completion")
        self.pending_transition = None  # A censored file retains only the last observed prefix.
        if complete and terminal.outcome not in ("victory", "defeat"): raise ValueError("Unknown outcome")
        footer = {"type": "footer", "status": "complete" if complete else "aborted", "censored": not complete,
            "transitions": self.transitions, "last_floor": self.last_floor, "ended_unix": time.time(),
            "terminal": terminal.__dict__ if complete else None, "error": str(error) if error else None}
        self.write(footer); self.stream.close(); self.closed = True
        name = self.episode_id + (".jsonl.gz" if complete else ".aborted.jsonl.gz")
        path = self.pending.with_name(name); os.replace(self.pending, path)
        return {"episode_id": self.episode_id, "path": str(path), "role": self.role, "seed": self.seed,
                "status": footer["status"], "censored": footer["censored"], "terminal": footer["terminal"],
                "transitions": self.transitions, "last_floor": self.last_floor,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "final_chain": self.chain}


def validate_episode(path, *, require_complete=True):
    from .flow import terminal_facts
    from .state import HeadlessStateMapper
    mapper = HeadlessStateMapper(); last_state = None
    previous = "0"*64; last_sequence = None; pending = None; transitions = 0; footer = None; header = None
    pending_driver = None; last_token = None; driver_errors = 0
    with gzip.open(path, "rt", encoding="utf-8") as source:
        for index, line in enumerate(source):
            item = json.loads(line); record = item["record"]
            digest = hashlib.sha256((previous + "\n" + canonical(record)).encode("utf-8")).hexdigest()
            if item["index"] != index or item["previous"] != previous or item["digest"] != digest: raise ValueError("Broken episode hash chain")
            if footer is not None: raise ValueError("Records after footer")
            previous = digest
            if index == 0:
                if record["type"] != "header" or record["schema"] != COLLECTION_SCHEMA: raise ValueError("Missing episode header")
                header = record
                if (header.get("engine_commit"), header.get("rng_policy"), header.get("corpus"), header.get("legacy_training_admission")) != (ENGINE_COMMIT, "headless-b354a9ee-v1", "headless-native-v1", False): raise ValueError("Unknown collection engine/corpus contract")
                if header.get("seed_group") != f'headless:{ENGINE_COMMIT}:{header["seed"]}': raise ValueError("Seed group mismatch")
            elif record["type"] == "observation":
                if record["state"]["player"]["className"] != header["role"]: raise ValueError("Role mismatch")
                if record["state"].get("seed") != header["seed"]: raise ValueError("Seed mismatch")
                if header["policy"].get("ascension") is not None and record["state"].get("ascension") != header["policy"]["ascension"]: raise ValueError("Ascension mismatch")
                if last_sequence is not None and (pending is None or record["sequence"] != pending): raise ValueError("Missing transition or observation")
                if pending_driver is not None:
                    driver = record["state"].get("driver_receipt", {})
                    if (driver.get("source_state_token"), driver.get("sequence"), driver.get("method"), driver.get("args")) != pending_driver: driver_errors += 1
                last_sequence = record["sequence"]; pending = None
                last_token = record["state_token"]; pending_driver = None
                last_state = record["state"]; mapper.map_state(last_state, run_id=header["episode_id"], sequence=last_sequence)
            elif record["type"] == "transition":
                if record["effect"] != "APPLIED" or record["before_sequence"] != last_sequence or record["after_sequence"] != last_sequence + 1: raise ValueError("Invalid transition")
                pending = record["after_sequence"]; transitions += 1
                pending_driver = (last_token, pending, record["native_method"], record["native_args"])
            elif record["type"] == "footer": footer = record
            else: raise ValueError("Unknown episode record")
    if header is None or footer is None or pending is not None or footer["transitions"] != transitions: raise ValueError("Incomplete episode")
    if footer["status"] not in ("complete", "aborted") or footer["censored"] is not (footer["status"] == "aborted"): raise ValueError("Invalid episode completion flags")
    if footer["status"] == "aborted" and footer["terminal"] is not None: raise ValueError("Censored episode has a terminal label")
    if last_state is not None and footer["last_floor"] != last_state["floor"]: raise ValueError("Footer floor mismatch")
    if require_complete and (footer["status"] != "complete" or footer["censored"] or footer["terminal"] is None): raise ValueError("Episode is censored")
    if footer["status"] == "complete":
        if driver_errors: raise ValueError("Driver receipt chain is incomplete")
        actual = terminal_facts(last_state); receipt = footer["terminal"]
        if actual is None or transitions == 0: raise ValueError("Missing actual terminal trajectory")
        if (receipt["outcome"], receipt["heart"], receipt["native_floor"], receipt["act"], receipt["character"], receipt["run_id"]) != (actual["outcome"], actual["heart"], last_state["floor"], last_state["act"], header["role"], header["episode_id"]): raise ValueError("Terminal receipt does not match observed result")
        values = {"run_id": header["episode_id"], "character": header["role"], "ascension": last_state["ascension"],
                  "native_floor": last_state["floor"], "act": last_state["act"], "outcome": actual["outcome"], "heart": actual["heart"]}
        identity = hashlib.sha256(json.dumps(values, sort_keys=True).encode()).hexdigest()
        token = hashlib.sha256((header["episode_id"]+"\n"+canonical(last_state)).encode()).hexdigest()
        if receipt.get("receipt_id") != identity or receipt.get("snapshot_token") != token or receipt.get("ascension") != last_state["ascension"]: raise ValueError("Terminal receipt fingerprint mismatch")
    return {"valid": True, "role": header["role"], "transitions": transitions, "status": footer["status"], "final_chain": previous}
