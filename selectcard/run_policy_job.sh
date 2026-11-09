#!/usr/bin/env bash
set -euo pipefail
task=$1
project_dir=$2
input_dir=$3
output_root=$4
stage=$5
conda_root=$6
workers=$7
source "$conda_root/etc/profile.d/conda.sh"
conda activate spire
# Both a full checkout and Desktop/selectcard-only copies resolve this package.
cd -- "$project_dir/.."
export PYTHONPATH="$(pwd)${PYTHONPATH:+:$PYTHONPATH}"
export OMP_NUM_THREADS=1
export MKL_NUM_THREADS=1
if [[ $task == prep ]]; then
  allocation=${SLURM_CPUS_PER_TASK:-16}
  ((workers < allocation)) || workers=$((allocation-1))
  ((workers>=1)) || { printf 'At least two prep CPUs required\n' >&2; exit 1; }
  python -m selectcard.policy.from_runs --stage "$stage" --input-dir "$input_dir" --output-dir "$output_root/data" --workers "$workers"
elif [[ $task == train ]]; then
  python -m selectcard.policy.train --data-dir "$output_root/data" --output-dir "$output_root/model" --stage "$stage" --device cuda
  python -m selectcard.policy.archive_run --run-dir "$output_root"
else
  printf 'Unknown job task: %s\n' "$task" >&2
  exit 2
fi
