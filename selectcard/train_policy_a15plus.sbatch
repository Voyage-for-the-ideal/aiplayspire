#!/bin/bash
#SBATCH --job-name=policy-a15plus
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=16
#SBATCH --mem=32G
#SBATCH --gres=gpu:1
#SBATCH --time=24:00:00
#SBATCH --output=policy_a15plus_%j.log

# From selectcard/: sbatch train_policy_a15plus.sbatch [pilot|confirm]
# This allocation runs both CPU export and GPU training. It submits no jobs.
set -euo pipefail

die() { printf '%s\n' "$*" >&2; exit 2; }
stage=${1:-pilot}
if [[ "$stage" == "--help" ]]; then
    printf '%s\n' 'sbatch train_policy_a15plus.sbatch [pilot|confirm]'
    exit 0
fi
[[ $# -le 1 && ( "$stage" == pilot || "$stage" == confirm ) ]] || die "Expected a single stage: pilot or confirm"

# Slurm copies scripts to its spool directory, so don't resolve paths from $0.
submit_dir=${SLURM_SUBMIT_DIR:-$PWD}
if [[ -n "${SELECTCARD_DIR:-}" ]]; then
    project_dir=$SELECTCARD_DIR
elif [[ -f "$submit_dir/policy/from_runs.py" ]]; then
    project_dir=$submit_dir
else
    project_dir="$submit_dir/selectcard"
fi
[[ -f "$project_dir/policy/from_runs.py" ]] || die "Submit from selectcard/ or set SELECTCARD_DIR"
project_dir="$(cd -- "$project_dir" && pwd -P)"

if [[ -n "${STS_RAW_DATA_DIR:-}" ]]; then
    raw_data=$STS_RAW_DATA_DIR
elif [[ -d "$project_dir/STS_DATA" ]]; then
    raw_data="$project_dir/STS_DATA"
else
    raw_data="$project_dir/STS Data"
fi
[[ "$raw_data" == /* ]] || raw_data="$project_dir/$raw_data"
[[ -d "$raw_data" ]] || die "Missing extracted .run/.json/.json.gz archive directory: $raw_data"

conda_root=${RUN_POLICY_CONDA_ROOT:-${CONDA_ROOT:-/home/scc/pb23151808/software/miniconda3}}
[[ -r "$conda_root/etc/profile.d/conda.sh" ]] || die "Set RUN_POLICY_CONDA_ROOT to the Conda installation"
source "$conda_root/etc/profile.d/conda.sh"
conda activate spire
cd -- "$project_dir/.."
export PYTHONPATH="$PWD${PYTHONPATH:+:$PYTHONPATH}"
export PYTHONUNBUFFERED=1 OMP_NUM_THREADS=1 MKL_NUM_THREADS=1

cpus=${SLURM_CPUS_PER_TASK:-16}
(( cpus >= 2 )) || die "Request at least two CPUs for export"
workers=$((cpus - 1))
if (( workers > 15 )); then workers=15; fi
output_root="$project_dir/experiment_results/run_policy/${stage}-a15plus-${SLURM_JOB_ID:?Submit this file via sbatch}"
mkdir -p -- "$(dirname -- "$output_root")"
mkdir -- "$output_root"
printf 'Stage: %s; minimum ascension: 15; export workers: %s\nArchive: %s\nResults: %s\n' \
    "$stage" "$workers" "$raw_data" "$output_root"

python -m selectcard.policy.from_runs --stage "$stage" --min-ascension 15 \
    --input-dir "$raw_data" --output-dir "$output_root/data" --workers "$workers"
python -m selectcard.policy.train --stage "$stage" --device cuda \
    --data-dir "$output_root/data" --output-dir "$output_root/model"
python -m selectcard.policy.archive_run --run-dir "$output_root"
printf 'Best checkpoint: %s/model/best.pt\n' "$output_root"

# Optional shared-validation comparison using an existing BC checkpoint.
if [[ -n "${POLICY_BASELINE_CHECKPOINT:-}" ]]; then
    baseline=$POLICY_BASELINE_CHECKPOINT
    [[ "$baseline" == /* ]] || baseline="$project_dir/$baseline"
    baseline_data_args=()
    if [[ -n "${POLICY_BASELINE_DATA_DIR:-}" ]]; then
        baseline_data=$POLICY_BASELINE_DATA_DIR
        [[ "$baseline_data" == /* ]] || baseline_data="$project_dir/$baseline_data"
        baseline_data_args=(--baseline-data-dir "$baseline_data")
    fi
    python -m selectcard.policy.compare_bc --device cuda --data-dir "$output_root/data" \
        --baseline "$baseline" --candidate "$output_root/model/best.pt" \
        --output "$output_root/comparison.json" "${baseline_data_args[@]}"
fi
