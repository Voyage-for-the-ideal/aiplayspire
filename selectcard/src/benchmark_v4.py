"""Measure V4 inference on synthetic fixed workloads; never trains a model."""

import argparse
import time

import numpy as np
import torch

try:
    from .checkpointing import load_checkpoint
    from .experiments import configure_experiment, write_json
    from .model import STSValueNetwork
except ImportError:
    from checkpointing import load_checkpoint
    from experiments import configure_experiment, write_json
    from model import STSValueNetwork


def synthetic_inputs(model, batch, device):
    cfg = model.model_config
    seq = torch.randint(1, cfg["vocab_size"], (batch, cfg["item_token_budget"]), device=device)
    base = (seq, torch.zeros_like(seq), torch.ones_like(seq),
            torch.zeros(batch, cfg["num_global_features"], device=device),
            torch.zeros(batch, dtype=torch.long, device=device),
            torch.zeros(batch, dtype=torch.long, device=device))
    context = {}
    if model.use_map_context:
        outgoing = torch.full((batch, 105), 2, dtype=torch.long, device=device)
        outgoing[:, -7:] = 0
        context = {
            "keys_features": torch.zeros(batch, 9, device=device),
            "map_rooms": torch.ones(batch, 105, dtype=torch.long, device=device),
            "map_flags": torch.full((batch, 105), 1 | 8 | 16, dtype=torch.long, device=device),
            "map_outgoing_mask": outgoing,
        }
    return base, context


def benchmark_model(model, device, batches=(1, 8, 32), iterations=100, warmup=10, need_weights=False):
    if iterations <= 0 or warmup < 0:
        raise ValueError("Invalid benchmark iteration counts")
    model = model.to(device).eval()
    rows = []
    originals = []
    if need_weights:
        for module in model.modules():
            if isinstance(module, torch.nn.MultiheadAttention):
                original = module.forward
                originals.append((module, original))
                def with_weights(*args, _original=original, **kwargs):
                    kwargs["need_weights"] = True
                    return _original(*args, **kwargs)
                module.forward = with_weights
    try:
        with torch.inference_mode():
            for batch in batches:
                base, context = synthetic_inputs(model, batch, device)
                for _ in range(warmup):
                    model(*base, **context)
                if device.type == "cuda":
                    torch.cuda.synchronize(device)
                    torch.cuda.reset_peak_memory_stats(device)
                timings = []
                for _ in range(iterations):
                    start = time.perf_counter()
                    model(*base, **context)
                    if device.type == "cuda":
                        torch.cuda.synchronize(device)
                    timings.append(1000 * (time.perf_counter() - start))
                rows.append({
                    "batch": batch, "median_ms": float(np.median(timings)),
                    "p95_ms": float(np.quantile(timings, 0.95)),
                    "states_per_second": float(1000 * batch / np.mean(timings)),
                    "peak_gpu_memory_bytes": torch.cuda.max_memory_allocated(device) if device.type == "cuda" else None,
                })
    finally:
        for module, original in originals:
            module.forward = original
    return {"workload": "synthetic-full-map-fixed-items", "device": str(device),
            "device_name": torch.cuda.get_device_name(device) if device.type == "cuda" else "CPU",
            "torch_version": str(torch.__version__), "cpu_threads": torch.get_num_threads(),
            "parameters": sum(p.numel() for p in model.parameters()),
            "architecture": model.model_config, "need_weights": need_weights,
            "warmup": warmup, "iterations": iterations, "results": rows}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--experiment", default="baseline")
    parser.add_argument("--checkpoint")
    parser.add_argument("--device", choices=("cpu", "cuda"), required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--iterations", type=int, default=100)
    parser.add_argument("--warmup", type=int, default=10)
    parser.add_argument("--threads", type=int, default=1)
    parser.add_argument("--compare-attention", action="store_true")
    args = parser.parse_args(argv)
    torch.set_num_threads(args.threads)
    torch.manual_seed(42)
    if args.device == "cuda" and not torch.cuda.is_available():
        parser.error("CUDA requested but not available")
    if args.checkpoint:
        ckpt, _, _ = load_checkpoint(args.checkpoint)
        model = STSValueNetwork(**ckpt["model_config"])
        model.load_state_dict(ckpt["model_state_dict"])
    else:
        configure_experiment(args.experiment)
        try:
            from .train import model_config
        except ImportError:
            from train import model_config
        model = STSValueNetwork(**model_config(1000))
    device = torch.device(args.device)
    report = benchmark_model(model, device, iterations=args.iterations, warmup=args.warmup)
    if args.compare_attention:
        report["with_attention_weights"] = benchmark_model(
            model, device, iterations=args.iterations, warmup=args.warmup, need_weights=True)
    write_json(args.output, report)


if __name__ == "__main__":
    main()
