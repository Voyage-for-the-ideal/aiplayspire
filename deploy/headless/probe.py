"""Isolated, no-run acceptance. Stdlib only; no HTTP listener or collector."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import subprocess
import threading
import time
import uuid

JVM_FLAGS = ("-Xms32m", "-Xmx512m", "-XX:+UseSerialGC",
             "-XX:ActiveProcessorCount=1", "-Djava.awt.headless=true")
COMMANDS = frozenset(("state", "quit"))
GATES = (Path("/home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION"),
         Path("/home/ubuntu/headless-spire-poc/ALLOW_COLLECTION"))


def send(process: subprocess.Popen, command: str) -> None:
    if command not in COMMANDS:
        raise ValueError("Only state/quit are allowed")
    process.stdin.write(command + "\n")
    process.stdin.flush()


def read_resources(pid: int) -> dict:
    root = Path("/proc") / str(pid)
    values = {}
    for line in (root / "status").read_text().splitlines():
        if line.startswith(("VmRSS:", "VmHWM:", "VmSwap:", "Threads:")):
            key, value, *_ = line.split()
            values[key.rstrip(":")] = int(value)
    rollup = (root / "smaps_rollup").read_text()
    values["Pss"] = int(next(line.split()[1] for line in rollup.splitlines() if line.startswith("Pss:")))
    stat = (root / "stat").read_text().rsplit(")", 1)[1].split()
    values["cpu_seconds"] = (int(stat[11]) + int(stat[12])) / os.sysconf("SC_CLK_TCK")
    values["time"] = time.monotonic()
    return values


def validate_state(state: dict) -> None:
    assert state["lifecycle"] == "BOOT", state
    assert state["floor"] == state["act"] == 0, state
    for key in ("playerInitialized", "mapInitialized", "roomInitialized", "seedInitialized", "seedSet", "glPresent"):
        assert state[key] is False, (key, state)
    assert state["graphicsType"] == "Mock", state
    assert state["nativeObservationAvailable"] is True, state
    assert state["nativeObservation"]["phase"] == "BOOT", state


def cpu_percent(first: dict, last: dict) -> float:
    return 100 * (last["cpu_seconds"] - first["cpu_seconds"]) / (last["time"] - first["time"])


def cgroup_snapshot() -> dict:
    path = next(line.split(":", 2)[2] for line in Path("/proc/self/cgroup").read_text().splitlines() if line.startswith("0::"))
    root = Path("/sys/fs/cgroup") / path.lstrip("/")
    return {"path": path, **{name: (root / name).read_text().strip()
                             for name in ("memory.max", "memory.swap.max", "memory.peak", "memory.events", "cpu.max", "pids.max")
                             if (root / name).exists()}}


def rng_check(root: Path, java: str, env: dict, reports: Path) -> dict:
    # Same compiled probe, same JVM, separate processes and classpaths.
    paths = {"desktop": f"{root}/probe-classes:{root}/reference/desktop-1.0.jar",
             "headless": f"{root}/probe-classes:{root}/upstream/out:{root}/upstream/lib/gson-2.8.8.jar"}
    result = {}
    for mode in ("core", "copy-global"):
        rows = {}
        for name, classpath in paths.items():
            args = [java, *JVM_FLAGS, "-cp", classpath, "RngProbe"]
            if mode != "core":
                args.append(mode)
            completed = subprocess.run(args, text=True, capture_output=True, env=env, timeout=90, check=True)
            (reports / f"rng-{name}-{mode}.log").write_text(completed.stdout + completed.stderr)
            rows[name] = [line for line in completed.stdout.splitlines() if line.startswith("RNG\t")]
            assert rows[name], (name, mode)
        result[mode] = {
            "exact_match": rows["desktop"] == rows["headless"],
            "desktop_rows": len(rows["desktop"]), "headless_rows": len(rows["headless"]),
            "desktop_sha256": hashlib.sha256("\n".join(rows["desktop"]).encode()).hexdigest(),
            "headless_sha256": hashlib.sha256("\n".join(rows["headless"]).encode()).hexdigest(),
            "first_differences": [{"row": i, "desktop": a, "headless": b}
                                  for i, (a, b) in enumerate(zip(rows["desktop"], rows["headless"])) if a != b][:3],
        }
    assert result["core"]["exact_match"], "Pure seeded RNG/math mismatch"
    return result


def run(root: Path, duration: float, interval: float) -> Path:
    assert duration >= 1 and interval > 0
    assert not any(p.exists() for p in GATES), "Collection gate must stay absent"
    for name in ("PocReadProbe.class", "RngProbe.class"):
        assert (root / "probe-classes" / name).is_file(), "Build both probes first"
    report_dir = root / "reports" / ("boot-" + uuid.uuid4().hex)
    report_dir.mkdir(parents=True)
    cgroup_before = cgroup_snapshot()
    save_root = root / "saves" / report_dir.name
    save_root.mkdir(parents=True)
    env = {k: v for k, v in os.environ.items() if k not in ("DISPLAY", "WAYLAND_DISPLAY", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    java = str(root / "toolchains/jdk21/bin/java")
    classpath = f"{root}/probe-classes:{root}/upstream/out:{root}/upstream/lib/gson-2.8.8.jar"
    command = [java, *JVM_FLAGS, "-cp", classpath, "PocReadProbe", str(root / "upstream/data"), str(save_root)]
    started = time.monotonic()
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, bufsize=1, cwd=save_root, env=env)
    replies = queue.Queue()

    def reader():
        with (report_dir / "stdout.log").open("w") as log:
            for line in process.stdout:
                log.write(line)
                log.flush()
                if line.startswith("POC_STATE "):
                    replies.put(json.loads(line.removeprefix("POC_STATE ")))
            replies.put(None)

    thread = threading.Thread(target=reader, daemon=True)
    thread.start()
    samples = []
    reads = 0

    def state():
        nonlocal reads
        assert process.poll() is None, "Probe exited early"
        send(process, "state")
        response = replies.get(timeout=60)
        assert response is not None, "No state response"
        validate_state(response)
        reads += 1
        return response

    try:
        first_state = state()
        ready_seconds = time.monotonic() - started
        samples.append(read_resources(process.pid))
        for _ in range(199):
            assert state() == first_state, "BOOT state changed"
            samples.append(read_resources(process.pid))
        idle_start = read_resources(process.pid)
        deadline = time.monotonic() + duration
        while time.monotonic() < deadline:
            time.sleep(min(interval, max(0, deadline - time.monotonic())))
            assert state() == first_state
            samples.append(read_resources(process.pid))
        idle_end = read_resources(process.pid)
        maps = (Path("/proc") / str(process.pid) / "maps").read_text()
        (report_dir / "process-maps.txt").write_text(maps)
        graphics_libraries = sorted({line.split()[-1] for line in maps.splitlines()
                                    if any(s in line.lower() for s in ("libgl.", "libglx", "libegl", "libx11", "liblwjgl", "libopenal"))})
        assert not graphics_libraries, graphics_libraries
        assert not any(p.exists() for p in GATES)
        send(process, "quit")
        assert process.wait(timeout=20) == 0
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        thread.join(timeout=5)

    saves = sorted(str(p.relative_to(save_root)) for p in save_root.rglob("*") if p.is_file())
    assert not any(p.endswith((".autosave", ".run")) or p.startswith("runs/") for p in saves), saves
    rng = rng_check(root, java, env, report_dir)
    cgroup_after = cgroup_snapshot()
    summary = {
        "schema": "headless-isolated-boot-poc-v1", "status": "native-boot-state-passed",
        "pid": process.pid, "command": command, "save_root": str(save_root),
        "startup_seconds": ready_seconds, "idle_seconds": idle_end["time"] - idle_start["time"],
        "state_reads": reads, "stable_state": first_state,
        "native_state_passed": first_state["nativeObservationAvailable"],
        "peak_rss_mib": max(s["VmHWM"] for s in samples) / 1024,
        "peak_sampled_pss_mib": max(s["Pss"] for s in samples) / 1024,
        "rss_first_mib": samples[0]["VmRSS"] / 1024, "rss_last_mib": samples[-1]["VmRSS"] / 1024,
        "peak_swap_mib": max(s["VmSwap"] for s in samples) / 1024,
        "idle_cpu_percent_one_core": cpu_percent(idle_start, idle_end),
        "idle_cpu_seconds": idle_end["cpu_seconds"] - idle_start["cpu_seconds"],
        "process_cpu_seconds": idle_end["cpu_seconds"], "max_threads": max(s["Threads"] for s in samples),
        "native_graphics_libraries": graphics_libraries, "display_env_present": "DISPLAY" in env or "WAYLAND_DISPLAY" in env,
        "commands_sent": {"state": reads, "quit": 1}, "mutating_commands_sent": 0,
        "collection_gate_present": False, "collector_started": False, "process_exited": process.poll() == 0,
        "save_files": saves, "rng": rng,
        "cgroup_before": cgroup_before, "cgroup_after": cgroup_after,
        "limits": "BOOT only; no generated map, combat, decisions, or outcome parity acceptance",
    }
    (report_dir / "resource-samples.json").write_text(json.dumps(samples, indent=2))
    (report_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    print(json.dumps({"report": str(report_dir / "summary.json"), **{k: summary[k] for k in (
        "status", "startup_seconds", "state_reads", "peak_rss_mib", "peak_sampled_pss_mib", "idle_cpu_percent_one_core", "native_state_passed")}}, indent=2))
    return report_dir


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--duration", type=float, default=300)
    parser.add_argument("--interval", type=float, default=5)
    args = parser.parse_args()
    run(args.root.resolve(strict=True), args.duration, args.interval)
