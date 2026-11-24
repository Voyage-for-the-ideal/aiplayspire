"""Repeat CPU inference/native fixture requests without contacting any game."""
import argparse
import json
import os
from pathlib import Path
import resource
import time

from preflight import check_model, settings


def memory():
    result = {"peak_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss}
    for line in Path("/proc/self/smaps_rollup").read_text().splitlines():
        if line.startswith(("Rss:", "Pss:")):
            key, value, _ = line.split()
            result[key.rstrip(":").lower() + "_kib"] = int(value)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--iterations", type=int, default=200)
    args = parser.parse_args()
    if args.iterations < 40:
        parser.error("use at least 40 iterations to include warmup and repeat measurements")
    c = settings()
    import torch
    from selectcard.src.inference import STSInferenceEngine
    from selectcard.src.live_smoke import fixture_state
    from sts_ai_framework.llm_agent_parts.value_payload import value_state_payload
    from battle_engine.worker import Worker
    from battle_engine.state import load
    torch.set_num_threads(1)
    torch.set_num_interop_threads(1)
    model_info = check_model(c["checkpoint"])
    engine = STSInferenceEngine(c["checkpoint"])
    payload = value_state_payload(fixture_state())
    expected = engine.evaluate_state(payload)
    samples = []
    start = time.monotonic()
    for i in range(args.iterations):
        if abs(engine.evaluate_state(payload) - expected) > 1e-7:
            raise ValueError("repeat inference changed prediction")
        if i % 20 == 19:
            samples.append({"iteration": i + 1, **memory()})
    state = load(c["root"] / "battle_engine/tests/fixtures/ironclad.json")
    worker = Worker(c["worker"])
    try:
        for i in range(40):
            result = worker.execute({"snapshot": state, "operation": "search", "search_profile": "FAST",
                                     "max_expansions": 5000, "timeout_ms": 250,
                                     "session_id": "offline-synthetic"}, 250)
            if not result["supported"]:
                raise ValueError("native fixture failed: " + result.get("reason", "unknown"))
        generation = worker.generation
    finally:
        worker.close()
    # Exclude initial allocator warmup. This is a smoke threshold, not a 24h leak proof.
    growth = samples[-1]["pss_kib"] - samples[1]["pss_kib"]
    if growth > 100 * 1024:
        raise ValueError(f"inference PSS grew by more than 100 MiB after warmup: {growth} KiB")
    report = {"schema": "linux-offline-probe-v1", "real_collection_started": False,
              "torch_cuda": torch.version.cuda, "model": model_info,
              "inference_iterations": args.iterations, "worker_requests": 40,
              "worker_generation": generation, "pss_growth_after_warmup_kib": growth,
              "elapsed_seconds": time.monotonic() - start, "memory_samples": samples,
              "final_memory": memory()}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({k: v for k, v in report.items() if k not in ("model", "memory_samples")}, indent=2))


if __name__ == "__main__":
    main()
