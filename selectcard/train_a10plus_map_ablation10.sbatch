#!/bin/bash
#SBATCH --gres=gpu:1
#SBATCH --output=train_a10plus_map_ablation10_%j.log

# From the cluster's selectcard directory:
#   sbatch train_a10plus_map_ablation10.sbatch
# Default: regression -> audit -> both pilots (8 epochs, 1%) -> both confirms
# (10 epochs, 100%) -> paired validation comparison + Chinese summary.
# All training/validation samples are A10+: require manifest min_ascension=10
# and verify the ascension of every train/val parquet row before training.
# About 26-30 GPU hours, plus pilots/evaluation/audit. No time limit is set here.
# For shorter allocations, first run the pilot and retain its printed RUN ID:
#   sbatch --export=ALL,ABLATION_STAGE=pilot train_a10plus_map_ablation10.sbatch
# Then use ONE fresh common RUN ID and the completed PILOT_RUN for both arms:
#   sbatch --export=ALL,ABLATION_STAGE=confirm,ABLATION_ARM=baseline,ABLATION_RUN=RUN,ABLATION_PILOT_RUN=PILOT_RUN train_a10plus_map_ablation10.sbatch
#   sbatch --export=ALL,ABLATION_STAGE=confirm,ABLATION_ARM=candidate,ABLATION_RUN=RUN,ABLATION_PILOT_RUN=PILOT_RUN train_a10plus_map_ablation10.sbatch
# After BOTH jobs succeed (or use an afterok dependency on both job IDs):
#   sbatch --export=ALL,ABLATION_STAGE=compare,ABLATION_RUN=RUN train_a10plus_map_ablation10.sbatch
# Raw archives: STS_DATA/Monthly_2020_*/*.json.gz (recursive scan).
# Also accept the local directory name "STS Data". Root-level .7z backups
# are ignored; the extracted Monthly_* directories are used directly.
# Paths may be relocated with CONDA_ROOT / SELECTCARD_DIR / STS_RAW_DATA_DIR,
# not training settings. Prefer STS_DATA, falling back to "STS Data".

set -eo pipefail
export PYTHONUNBUFFERED=1

conda_root="${CONDA_ROOT:-/home/scc/pb23151808/software/miniconda3}"
export PATH="${conda_root}/bin:$PATH"
source "${conda_root}/etc/profile.d/conda.sh"
conda activate spire
cd "${SELECTCARD_DIR:-/home/scc/pb23151808/Desktop/selectcard}"

stage="${ABLATION_STAGE:-all}"
arm="${ABLATION_ARM:-both}"
run="${ABLATION_RUN:-seed_42_${SLURM_JOB_ID:-$(date +%Y%m%d_%H%M%S)}}"
pilot_run="${ABLATION_PILOT_RUN:-$run}"
case "$stage" in all|pilot|confirm|compare) ;; *) echo "Invalid ABLATION_STAGE: $stage" >&2; exit 1 ;; esac
case "$arm" in both|baseline|candidate) ;; *) echo "Invalid ABLATION_ARM: $arm" >&2; exit 1 ;; esac
if [[ "$stage" != confirm && "$arm" != both ]]; then
    echo "Single-arm selection is only supported for confirm jobs" >&2
    exit 1
fi
for id in "$run" "$pilot_run"; do
    if [[ ! "$id" =~ ^[A-Za-z0-9_-]+$ ]]; then
        echo "Run IDs must contain only letters, digits, underscores and hyphens" >&2
        exit 1
    fi
done
echo "Map/history ablation: RUN=${run}; PILOT_RUN=${pilot_run}; stage=${stage}; arm=${arm}"

data=processed_data_v4/a10plus_multiscale_v1
raw_data="${STS_RAW_DATA_DIR:-STS_DATA}"
if [[ -z "${STS_RAW_DATA_DIR:-}" && ! -d "$raw_data" && -d "STS Data" ]]; then
    raw_data="STS Data"
fi
checkpoint_root=checkpoints/v4/a10plus_map_ablation10
result_root=experiment_results/a10plus_map_ablation10
comparison="${result_root}/comparison/${run}"
experiments=(multiscale multiscale,map_history0)
if [[ "$arm" == baseline ]]; then experiments=(multiscale); fi
if [[ "$arm" == candidate ]]; then experiments=(multiscale,map_history0); fi

fresh_path() {
    if [[ -e "$1" ]]; then
        echo "Artifact already exists; choose a fresh RUN ID: $1" >&2
        exit 1
    fi
}

# Check every requested destination before spending GPU time. Pilot artifacts
# live under pilot/, so they never mix with the full-data confirmation exports.
stages=()
case "$stage" in all) stages=(pilot confirm) ;; pilot|confirm) stages=("$stage") ;; esac
for phase in "${stages[@]}"; do
    prefix=""; if [[ "$phase" == pilot ]]; then prefix=pilot/; fi
    for experiment in "${experiments[@]}"; do
        label="${experiment//,/_}"
        fresh_path "${checkpoint_root}/${prefix}${label}/${run}"
        fresh_path "${result_root}/${prefix}${label}/${run}"
    done
done
if [[ "$stage" == all || "$stage" == compare || ( "$stage" == confirm && "$arm" == both ) ]]; then
    fresh_path "$comparison"
fi

validate_pair() {
    python - "$checkpoint_root" "$result_root" "$1" "$2" "${3:-}" <<'PY'
import json
import math
import sys
from pathlib import Path

checkpoints, results = map(Path, sys.argv[1:3])
phase, run, report_dir = sys.argv[3:6]
prefix = Path("pilot") if phase == "pilot" else Path()
labels = ("multiscale", "multiscale_map_history0")
epochs, fraction = (8, .01) if phase == "pilot" else (10, 1.)
settings, reports = [], []
for label, ablated in zip(labels, (False, True)):
    training = checkpoints / prefix / label / run
    evaluation = results / prefix / label / run
    status = json.loads((training / "status.json").read_text())
    assert status["status"] == "complete", (training, status)
    assert status["held_out_test_evaluated"] is False
    experiment = json.loads((training / "experiment.json").read_text())
    assert experiment["names"] == (["multiscale", "map_history0"] if ablated else ["multiscale"])
    assert experiment["stage"] == phase and experiment["seed"] == 42
    s = experiment["settings"]
    assert s["ABLATE_MAP_HISTORY"] is ablated and s["USE_MAP_CONTEXT"] is True
    assert s["EPOCHS"] == epochs and s["RUN_FRACTION"] == fraction
    assert s["BATCH_SIZE"] == 512 and s["LEARNING_RATE"] == 2.8e-4
    assert s["CONTEXT_CURRICULUM_EPOCHS"] == 3
    assert s["COSINE_DECAY_EPOCHS"] == epochs and s["EARLY_STOPPING_PATIENCE"] == 0
    settings.append(s)
    report = json.loads((evaluation / "val_metrics.json").read_text())
    header, metrics = report["header"], report["metrics"]
    assert header["seed"] == 42 and header["stage"] == phase
    assert header["completed_epochs"] == header["planned_epochs"] == epochs
    assert header["comparison_contract"]["split"] == "val"
    assert header["comparison_contract"]["run_fraction"] == fraction
    assert all(metrics.get(k) is not None and math.isfinite(metrics[k])
               for k in ("relative_value_nll", "terminal_floor_mae", "key_bce"))
    prediction = evaluation / "val_predictions.jsonl"
    with prediction.open() as handle:
        assert json.loads(next(handle))["header"] == header
        assert next(handle, None) is not None, prediction
    reports.append(report)
assert {k for k in settings[0] if settings[0][k] != settings[1][k]} == {"ABLATE_MAP_HISTORY"}
assert settings[0].keys() == settings[1].keys()
assert reports[0]["header"]["comparison_contract"] == reports[1]["header"]["comparison_contract"]
print(f"Validated paired {phase}: seed=42, epochs={epochs}, fraction={fraction}, only ABLATE_MAP_HISTORY differs")
if not report_dir:
    sys.exit(0)

directory = Path(report_dir)
comparison = json.loads((directory / "comparison.json").read_text())
b, c = (report["metrics"] for report in reports)
def delta(left, right):
    return right - left if left is not None and right is not None else None
def fmt(value):
    return "NA" if value is None else f"{value:.8g}"
ci = comparison["nll_delta_ci95"]
if comparison["status"] != "confirmed" or ci is None:
    verdict = "确认或 bootstrap 证据不足，保留有节点参考组。"
elif ci[1] < 0:
    verdict = "本配方下无节点组 common NLL 更低；地图节点/历史未带来预测收益。"
elif ci[0] > 0:
    verdict = "本配方下有节点组 common NLL 更低；地图节点/历史具有可测出的预测贡献。"
else:
    verdict = "CI95 跨 0；在本次 A10+ 实验中未测出明确贡献。"
lines = ["# V(S) 地图节点与历史输入消融", "", verdict, "",
         "差值方向：无节点 − 有节点；baseline=multiscale，candidate=multiscale,map_history0。", "",
         "| 指标 | 有节点 | 无节点 | 差值 / CI95 |", "|---|---:|---:|---|",
         f"| common NLL | {fmt(b['relative_value_nll'])} | {fmt(c['relative_value_nll'])} | {fmt(comparison['nll_delta'])} / {ci} |",
         f"| terminal floor MAE | {fmt(b['terminal_floor_mae'])} | {fmt(c['terminal_floor_mae'])} | {fmt(comparison['mae_delta'])} / {comparison['mae_delta_ci95']} |",
         f"| key BCE | {fmt(b['key_bce'])} | {fmt(c['key_bce'])} | {fmt(c['key_bce']-b['key_bce'])} |",
         "", "## 共同评分按预测距离分组（描述性差值）", "",
         "| 距离 | 有节点 common NLL | 无节点 common NLL | 差值 | MAE 差值 |", "|---|---:|---:|---:|---:|"]
for distance in ("1-3", "4-8", "9-16", "17+"):
    left, right = (m["common_by_distance"][distance] for m in (b, c))
    lines.append(f"| {distance} | {fmt(left['relative_value_nll'])} | {fmt(right['relative_value_nll'])} | {fmt(delta(left['relative_value_nll'], right['relative_value_nll']))} | {fmt(delta(left['terminal_floor_mae'], right['terminal_floor_mae']))} |")
lines += ["", "## 近端/远端 hazard NLL（描述性，不替代共同主评分）", "",
          "| 范围 | 有节点 | 无节点 | 差值 |", "|---|---:|---:|---:|"]
for name in ("near_nll", "far_nll"):
    lines.append(f"| {name} | {fmt(b[name])} | {fmt(c[name])} | {fmt(delta(b[name], c[name]))} |")
lines += ["", "## 适用前提与检查", "",
          "- [KEYS] 保留（含 burning_pending 地图派生位）、热身 3 轮；测量的是钥匙可见条件下地图节点与最近 8 个历史房间的边际价值。",
          "- 单 seed 42；不确定性为 run-cluster-only，不代表跨种子稳定。全程只评估 val。",
          "- 两臂 key_bce 的预期锚点约为 1e-5；偏离时结合完整指标排查，不调整训练配方或 MAE 保护闸。",
          f"- status={comparison['status']}；promote={comparison['promote']}。晋级遵循共同 NLL CI95 与 MAE/机制保护闸。",
          "- 完整 by_floor、common_by_distance、tail_coverage（37/57 near/far）见两臂的 val_metrics.json。"]
with (directory / "summary_zh.md").open("x", encoding="utf-8") as handle:
    handle.write("\n".join(lines) + "\n")
print(f"Chinese summary: {directory / 'summary_zh.md'}")
PY
}

if [[ "$stage" != compare ]]; then
    python -c 'import torch; assert torch.cuda.is_available(), "CUDA is unavailable"; print(torch.cuda.get_device_name(0))'
    (cd src && python -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api)
    python src/train.py --list-experiments
    if [[ "$stage" == confirm ]]; then validate_pair pilot "$pilot_run"; fi

    # Separate audit paths also permit two split-arm jobs to audit concurrently.
    audit_dir="${result_root}/data_audit/${run}/${stage}_${arm}"
    fresh_path "$audit_dir"
    if [[ -f "${data}/dataset_manifest.json" ]]; then
        echo "Found existing dataset; audit only: ${data}"
    else
        if [[ ! -d "$raw_data" ]]; then
            echo "Raw data directory not found: ${raw_data}; set STS_RAW_DATA_DIR to its path" >&2
            exit 1
        fi
        echo "Manifest missing; recursively reading monthly .json/.json.gz archives under ${raw_data} into ${data} (.7z ignored)"
        python src/rebuild_multiscale.py --input-dir "$raw_data" --output-dir "$data" --workers 16
    fi
    python src/inspect_multiscale.py --data-dir "$data" --split train --min-ascension 10 --output "${audit_dir}/train.json"
    python src/inspect_multiscale.py --data-dir "$data" --split val --min-ascension 10 --output "${audit_dir}/val.json"
fi

for phase in "${stages[@]}"; do
    epochs=10; prefix=""
    if [[ "$phase" == pilot ]]; then epochs=8; prefix=pilot/; fi
    for experiment in "${experiments[@]}"; do
        label="${experiment//,/_}"
        output="${checkpoint_root}/${prefix}${label}/${run}"
        evaluation="${result_root}/${prefix}${label}/${run}"
        echo "Training ${phase}: ${experiment}; seed=42; epochs=${epochs}; output=${output}"
        python src/train.py --experiment "$experiment" --stage "$phase" --seed 42 \
            --epochs "$epochs" --data-dir "$data" --output-dir "$output" --device cuda
        python src/train.py --evaluate --checkpoint "${output}/sts_value_model_final.pth" \
            --data-dir "$data" --evaluation-split val --output-dir "$evaluation" --device cuda
    done
    if [[ "$arm" == both ]]; then validate_pair "$phase" "$run"; fi
done

if [[ "$stage" == all || "$stage" == compare || ( "$stage" == confirm && "$arm" == both ) ]]; then
    validate_pair confirm "$run"
    mkdir -p "$(dirname "$comparison")"
    mkdir "$comparison"
    python src/evaluation_v4.py \
        --baseline "${result_root}/multiscale/${run}/val_predictions.jsonl" \
        --candidate "${result_root}/multiscale_map_history0/${run}/val_predictions.jsonl" \
        --output "${comparison}/comparison.json"
    validate_pair confirm "$run" "$comparison"
    echo "Completed: ${comparison}/comparison.json and ${comparison}/summary_zh.md"
else
    echo "Completed stage=${stage}, arm=${arm}, RUN=${run}; artifacts retained for the next stage."
fi
