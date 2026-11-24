"""Verify the deployed read-only HTTP adapter without launching any agent."""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from urllib.error import HTTPError
from urllib.request import Request, build_opener, ProxyHandler
import uuid

from probe import GATES, JVM_FLAGS, cpu_percent, read_resources, rng_check

UNIT = "headless-spire-state.service"
URL = "http://127.0.0.1:5000"
HTTP = build_opener(ProxyHandler({}))


def unit_state() -> dict:
    result = subprocess.run(["systemctl", "show", UNIT, "-p", "MainPID", "-p", "ActiveState",
                             "-p", "NRestarts", "-p", "ControlGroup", "-p", "UnitFileState"],
                            capture_output=True, text=True, check=True)
    return dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)


def gate_closed() -> bool:
    return not any(p.exists() or p.is_symlink() for p in GATES)


def validate(data: dict) -> None:
    assert "error" not in data
    assert data["screen_type"] == "MAIN_MENU"
    assert data["choice_list"] == []
    assert data["can_proceed"] is False and data["can_cancel"] is False
    assert not {"player", "floor", "act", "seed", "seed_group", "rng_state", "character", "ascension_level"}.intersection(data)
    meta = data["headless"]
    assert meta["engine_phase"] == "BOOT" and meta["read_only"] is True
    assert meta["floor"] == meta["act"] == 0
    assert meta["graphics_type"] == "Mock"
    for name in ("run_started", "collection_enabled", "player_initialized", "map_initialized", "room_initialized",
                 "seed_initialized", "rng_streams_initialized", "gl_present"):
        assert meta[name] is False, (name, meta)


def request(path="/state", method="GET") -> tuple:
    started = time.monotonic()
    # Negative verb probes use an empty body; no game action payload is sent.
    req = Request(URL + path, method=method, data=None if method in ("GET", "HEAD") else b"")
    try:
        response = HTTP.open(req, timeout=3)
    except HTTPError as error:
        response = error
    with response:
        return response.status, {k.lower(): v for k, v in response.headers.items()}, response.read(), (time.monotonic() - started) * 1000


def profile_files(root: Path) -> dict:
    files = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
             for p in root.rglob("*") if p.is_file()}
    assert not any(name.endswith((".autosave", ".run", ".jsonl")) or name.startswith("runs/") for name in files), files
    return files


def check_native_cli(root: Path, reports: Path) -> dict:
    save = root / "saves" / ("native-" + reports.name)
    save.mkdir()
    env = {k: v for k, v in os.environ.items() if k not in
           ("DISPLAY", "WAYLAND_DISPLAY", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    args = [str(root / "toolchains/jdk21/bin/java"), *JVM_FLAGS, "-cp",
            f"{root}/upstream/out:{root}/upstream/lib/gson-2.8.8.jar",
            "com.megacrit.cardcrawl.headless.HeadlessCli", str(root / "upstream/data"), str(save)]
    result = subprocess.run(args, input="state\nquit\n", text=True, capture_output=True,
                            cwd=save, env=env, check=True, timeout=30)
    (reports / "native-cli.log").write_text(result.stdout + result.stderr)
    assert "ERROR:" not in result.stdout and "Exception" not in result.stderr
    state = json.loads(result.stdout[result.stdout.index("{"):result.stdout.rindex("}") + 1])
    assert state["phase"] == "BOOT" and state["floor"] == state["act"] == 0
    assert not state.get("player") and not state.get("room") and not state.get("map")
    profile_files(save)
    return {"passed": True, "commands": ["state", "quit"], "state": state}


def run(root: Path, contract_root: Path, duration: float, interval: float) -> Path:
    if duration < 1 or interval <= 0:
        raise ValueError("Positive duration/interval required")
    assert gate_closed()
    sys.path.insert(0, str(contract_root))
    from sts_ai_framework.models import GameState
    from sts_ai_framework.game_client import GameClient
    os.environ["NO_PROXY"] = "localhost,127.0.0.1"
    reports = root / "reports" / ("http-" + uuid.uuid4().hex)
    reports.mkdir()
    before = unit_state()
    assert before["ActiveState"] == "active" and before["NRestarts"] == "0", before
    pid = int(before["MainPID"])
    assert pid > 0
    args = (Path("/proc") / str(pid) / "cmdline").read_bytes().decode().split("\0")
    assert "ReadonlyStateServer" in args
    savedir = Path(args[args.index("ReadonlyStateServer") + 2])
    assert savedir.resolve().is_relative_to((root / "saves").resolve())
    saved_before = profile_files(savedir)
    samples, latencies = [], []
    reads = 0
    first_bytes = None

    def check_reply(reply):
        nonlocal reads, first_bytes
        status, headers, raw, elapsed = reply
        assert status == 200 and headers["content-type"].startswith("application/json"), (status, headers, raw)
        assert headers["x-headless-read-only"] == "true"
        assert headers["cache-control"] == "no-store"
        if first_bytes is None:
            first_bytes = raw
        assert raw == first_bytes, "Read-only response changed"
        data = json.loads(raw)
        validate(data)
        model = GameState(**data)
        assert model.screen_type == "MAIN_MENU" and model.player is None and model.floor is None
        assert model.character is None and model.ascension_level is None and not model.choice_list
        assert not model.can_proceed and not model.can_cancel
        latencies.append(elapsed)
        reads += 1
        return data

    first = check_reply(request())
    # Exercise the real, unchanged client, not an emulated schema parser.
    client_state = GameClient().get_state()
    assert client_state is not None and client_state.screen_type == "MAIN_MENU" and client_state.player is None
    for _ in range(99):
        check_reply(request())
    with ThreadPoolExecutor(max_workers=4) as pool:
        for reply in pool.map(lambda _: request(), range(80)):
            check_reply(reply)
    negative = []
    for path, method, expected in (("/action", "GET", 404), ("/action", "POST", 404),
                                   ("/card_info", "GET", 404), ("/state", "POST", 405),
                                   ("/state", "PUT", 405), ("/state", "DELETE", 405),
                                   ("/state", "HEAD", 405), ("/state?probe=1", "GET", 400),
                                   ("/state/extra", "GET", 404), ("/%73tate", "GET", 404)):
        status, headers, raw, _ = request(path, method)
        assert status == expected, (path, method, status)
        if method != "HEAD":
            assert "error" in json.loads(raw)
        if status == 405:
            assert headers["allow"] == "GET"
        negative.append({"path": path, "method": method, "status": status, "body_bytes_sent": 0})
    assert check_reply(request()) == first
    samples.append(read_resources(pid))
    idle_first = samples[-1]
    deadline = time.monotonic() + duration
    while time.monotonic() < deadline:
        time.sleep(min(interval, max(0, deadline - time.monotonic())))
        check_reply(request())
        samples.append(read_resources(pid))
        assert gate_closed()
        assert unit_state() == before, "Unit restarted or changed state"
    idle_last = samples[-1]
    after = unit_state()
    assert before == after
    assert saved_before == profile_files(savedir), "GET changed profile files"
    maps = (Path("/proc") / str(pid) / "maps").read_text()
    (reports / "process-maps.txt").write_text(maps)
    assert not any(s in maps.lower() for s in ("libgl.", "libglx", "libegl", "libx11", "liblwjgl", "libopenal"))
    cg = Path("/sys/fs/cgroup") / after["ControlGroup"].lstrip("/")
    events = (cg / "memory.events").read_text()
    counts = dict(line.split() for line in events.splitlines())
    assert int(counts["oom"]) == int(counts["oom_kill"]) == 0
    assert max(s["VmSwap"] for s in samples) == 0
    assert max(s["VmHWM"] for s in samples) / 1024 < 256
    assert (samples[-1]["VmRSS"] - samples[0]["VmRSS"]) / 1024 < 32
    assert cpu_percent(idle_first, idle_last) < 5
    listeners = subprocess.run(["ss", "-H", "-ltn"], capture_output=True, text=True, check=True).stdout
    listeners = [line for line in listeners.splitlines() if line.split()[3].endswith(":5000")]
    assert len(listeners) == 1 and listeners[0].split()[3] == "127.0.0.1:5000", listeners
    native = check_native_cli(root, reports)
    env = {k: v for k, v in os.environ.items() if k not in ("DISPLAY", "WAYLAND_DISPLAY", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    rng = rng_check(root, str(root / "toolchains/jdk21/bin/java"), env, reports)
    assert unit_state() == before and gate_closed()
    summary = {
        "schema": "headless-readonly-http-acceptance-v1", "status": "passed", "url": URL + "/state",
        "duration_seconds": idle_last["time"] - idle_first["time"], "http_state_reads": reads,
        "game_client_reads": 1, "game_client_and_model_compatible": True,
        "response": first, "state_sha256": hashlib.sha256(first_bytes).hexdigest(), "unit_before": before, "unit_after": after,
        "peak_rss_mib": max(s["VmHWM"] for s in samples) / 1024,
        "peak_sampled_pss_mib": max(s["Pss"] for s in samples) / 1024,
        "rss_first_mib": samples[0]["VmRSS"] / 1024, "rss_last_mib": samples[-1]["VmRSS"] / 1024,
        "idle_cpu_percent_one_core": cpu_percent(idle_first, idle_last),
        "peak_swap_mib": max(s["VmSwap"] for s in samples) / 1024,
        "http_latency_p95_ms": sorted(latencies)[int((len(latencies) - 1) * 0.95)], "http_latency_max_ms": max(latencies),
        "memory_events": counts, "negative_http_tests": negative, "listen_addresses": listeners,
        "save_root": str(savedir), "profile_files_unchanged": True, "native_cli": native, "rng": rng,
        "collection_gate_present": False, "collector_started": False, "game_action_payloads_sent": 0,
        "limits": "Pre-run state contract only; no in-run serialization or gameplay acceptance",
    }
    (reports / "resource-samples.json").write_text(json.dumps(samples, indent=2))
    (reports / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    print(json.dumps({"report": str(reports / "summary.json"), **{k: summary[k] for k in
                      ("status", "http_state_reads", "peak_rss_mib", "idle_cpu_percent_one_core", "http_latency_p95_ms")}}, indent=2))
    return reports


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--contract-root", type=Path, required=True)
    parser.add_argument("--duration", type=float, default=600)
    parser.add_argument("--interval", type=float, default=5)
    args = parser.parse_args()
    run(args.root.resolve(strict=True), args.contract_root.resolve(strict=True), args.duration, args.interval)
