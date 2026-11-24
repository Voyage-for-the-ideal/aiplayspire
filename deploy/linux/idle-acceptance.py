"""Read-only main-menu soak: GET /state and PING only; never submit actions."""
import argparse
import datetime
import json
import os
from pathlib import Path
import statistics
import socket
import subprocess
import sys
import time
import urllib.request
import uuid

from preflight import inspect_mods, settings, verify_release
from battle_engine import protocol

UNITS = ("aiplayspire-game.service", "aiplayspire-gateway.service", "aiplayspire-collector.service")


def unit_states():
    result = {}
    raw = subprocess.check_output(["systemctl", "show", *UNITS, "-p", "Id", "-p", "ActiveState",
                                   "-p", "MainPID", "-p", "NRestarts", "-p", "InvocationID"], text=True)
    for block in raw.strip().split("\n\n"):
        fields = dict(line.split("=", 1) for line in block.splitlines())
        result[fields["Id"]] = fields
    return result


def assert_menu(state):
    if state.get("screen_type") != "MAIN_MENU" or "play" not in state.get("choice_list", []):
        raise ValueError("expected the actual main menu, not a profile dialog, character selection or run")
    if state.get("floor") not in (None, 0) or state.get("seed_group"):
        raise ValueError("unexpected run identity at idle")


def process_memory():
    rows = []
    prefixes = tuple("/aiplayspire.slice/" + name for name in UNITS[:2])
    for proc in Path("/proc").glob("[0-9]*"):
        try:
            group = (proc / "cgroup").read_text().split("0::", 1)[1].strip()
            if not any(group == prefix or group.startswith(prefix + "/") for prefix in prefixes):
                continue
            row = {"pid": int(proc.name), "name": (proc / "comm").read_text().strip(), "cgroup": group}
            for line in (proc / "smaps_rollup").read_text().splitlines():
                if line.startswith(("Rss:", "Pss:")):
                    key, value, _ = line.split()
                    row[key.rstrip(":").lower() + "_kib"] = int(value)
            rows.append(row)
        except (FileNotFoundError, PermissionError, ProcessLookupError):
            continue
    return rows


def counters(path):
    return dict((name, int(value)) for name, value in (line.split() for line in path.read_text().splitlines()))


def trace_inventory(runtime):
    return {str(p.relative_to(runtime)): [p.stat().st_size, p.stat().st_mtime_ns]
            for p in (runtime / "traces").rglob("*.jsonl")}


def collect_sample(c, opener):
    started = time.monotonic()
    with opener.open(f"http://127.0.0.1:{c['api_port']}/state", timeout=5) as response:
        state = json.load(response)
        status = response.status
    latency = (time.monotonic() - started) * 1000
    assert_menu(state)
    with socket.create_connection(("127.0.0.1", c["port"]), timeout=3) as connection:
        connection.settimeout(3)
        protocol.send(connection, json.dumps({"type": "PING"}))
        pong = json.loads(protocol.receive(connection))
        if pong.get("type") != "PONG" or pong.get("mode") != "cpp-only" or not pong.get("ready"):
            raise ValueError("gateway readiness mismatch")
    units = unit_states()
    for name in UNITS[:2]:
        if units[name]["ActiveState"] != "active" or units[name]["MainPID"] == "0":
            raise ValueError("idle service is not active: " + name)
    if units[UNITS[2]]["ActiveState"] != "inactive" or units[UNITS[2]]["MainPID"] != "0":
        raise ValueError("collector must stay inactive")
    if (c["runtime"] / "ALLOW_COLLECTION").exists():
        raise ValueError("collection gate must stay closed")
    processes = process_memory()
    if sum(row["name"] == "java" for row in processes) != 1:
        raise ValueError("expected exactly one game JVM")
    memory = dict(line.split(":", 1) for line in Path("/proc/meminfo").read_text().splitlines())
    available = int(memory["MemAvailable"].split()[0])
    if available < 512 * 1024:
        raise ValueError("host MemAvailable below 512 MiB")
    slice_path = Path("/sys/fs/cgroup/aiplayspire.slice")
    return {"elapsed": 0, "state": state, "http_status": status, "http_latency_ms": latency,
            "units": units, "processes": processes, "host_available_kib": available,
            "rss_kib": sum(row.get("rss_kib", 0) for row in processes),
            "pss_kib": sum(row.get("pss_kib", 0) for row in processes),
            "slice_memory_bytes": int((slice_path / "memory.current").read_text()),
            "slice_memory_events": counters(slice_path / "memory.events"),
            "slice_cpu": counters(slice_path / "cpu.stat"),
            "host_swap_pages": {k: v for k, v in counters(Path("/proc/vmstat")).items() if k in ("pswpin", "pswpout")},
            "memory_pressure": (slice_path / "memory.pressure").read_text()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--duration", type=float, default=600)
    parser.add_argument("--interval", type=float, default=5)
    args = parser.parse_args()
    if args.duration < 60 or not 1 <= args.interval <= 30:
        parser.error("duration >=60 seconds and interval 1..30 seconds required")
    c = settings()
    folder = c["runtime"] / "reports" / ("idle-" + uuid.uuid4().hex)
    folder.mkdir(parents=True)
    print("REPORT_DIR=" + str(folder), flush=True)
    release = verify_release(c["root"])
    mods = inspect_mods(c["root"], c["game"])
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    traces = trace_inventory(c["runtime"])
    samples, error = [], None
    started = time.monotonic()
    try:
        with (folder / "samples.jsonl").open("x") as handle:
            while True:
                row = collect_sample(c, opener)
                row["elapsed"] = time.monotonic() - started
                if samples:
                    for unit in UNITS[:2]:
                        for key in ("MainPID", "NRestarts", "InvocationID"):
                            if row["units"][unit][key] != samples[0]["units"][unit][key]:
                                raise ValueError("service restarted during soak: " + unit)
                    for key in ("oom", "oom_kill", "max"):
                        if row["slice_memory_events"][key] != samples[0]["slice_memory_events"][key]:
                            raise ValueError("memory limit event during soak: " + key)
                samples.append(row)
                handle.write(json.dumps(row) + "\n")
                handle.flush()
                if trace_inventory(c["runtime"]) != traces:
                    raise ValueError("learning trace changed during idle check")
                if row["elapsed"] >= args.duration:
                    break
                time.sleep(min(args.interval, args.duration - row["elapsed"]))
        # Compare like-for-like main-menu samples after initial warmup.
        stable = [row for row in samples if row["elapsed"] >= 60] or samples
        growth = stable[-1]["pss_kib"] - stable[0]["pss_kib"]
        if growth > 128 * 1024:
            raise ValueError("post-warmup main-menu PSS grew more than 128 MiB")
    except Exception as exception:
        error = f"{type(exception).__name__}: {exception}"
    summary = {"schema": "linux-main-menu-idle-v1", "passed": error is None, "error": error,
               "finished_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
               "duration_seconds": time.monotonic() - started, "samples": len(samples),
               "action_requests_submitted": 0, "real_collection_started": False,
               "release_manifest_sha256": release, "mods": mods, "report_dir": str(folder)}
    if samples:
        summary.update(initial_state=samples[0]["state"], final_state=samples[-1]["state"],
                       peak_rss_mib=max(row["rss_kib"] for row in samples) / 1024,
                       peak_pss_mib=max(row["pss_kib"] for row in samples) / 1024,
                       min_host_available_mib=min(row["host_available_kib"] for row in samples) / 1024,
                       http_latency_median_ms=statistics.median(row["http_latency_ms"] for row in samples),
                       http_latency_max_ms=max(row["http_latency_ms"] for row in samples),
                       final_units=samples[-1]["units"],
                       final_memory_events=samples[-1]["slice_memory_events"],
                       final_processes=samples[-1]["processes"],
                       final_memory_pressure=samples[-1]["memory_pressure"])
    if error is None:
        summary["post_warmup_pss_growth_mib"] = growth / 1024
    (folder / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2), flush=True)
    return int(error is not None)


if __name__ == "__main__":
    sys.exit(main())
