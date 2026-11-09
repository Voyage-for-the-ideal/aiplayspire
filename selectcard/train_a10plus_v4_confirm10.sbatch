#!/bin/bash
#SBATCH --array=0-8%1
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=16
#SBATCH --time=24:00:00
#SBATCH --job-name=selectcard-a10plus-confirm10
#SBATCH --output=train_a10plus_v4_confirm10_%A_%a.log

set -eo pipefail

export PATH="/home/scc/pb23151808/software/miniconda3/bin:$PATH"
source /home/scc/pb23151808/software/miniconda3/etc/profile.d/conda.sh
conda activate spire

cd /home/scc/pb23151808/Desktop/selectcard

python -c 'import torch; assert torch.cuda.is_available(), "CUDA is unavailable"; print(torch.cuda.get_device_name(0))'

DATA_DIR=processed_data_a10plus_v4
CACHE="${DATA_DIR}/training_artifacts.json"

# The parquet fingerprint does not cover reader code, so a degenerate cache from
# the pre-fix runs (all-zero positives) would be reused silently. Refuse to start
# on one: deleting it is a one-off, concurrent starts then share the rebuilt file.
python - "$CACHE" <<'PY'
import json
import os
import sys

path = sys.argv[1]
if not os.path.exists(path):
    print(f"No artifact cache yet at {path}; it will be built from the parquet shards.")
    raise SystemExit(0)

with open(path, "r", encoding="utf-8") as handle:
    cached = json.load(handle)

version = cached.get("cache_version")
positives = cached.get("baseline_statistics", {}).get("global", {}).get("positive", [])
if version != 3:
    raise SystemExit(
        f"Artifact cache {path} has version {version}, expected 3. "
        f"Delete it and resubmit so positives are recounted from the parquet data."
    )
if not positives or sum(int(value) for value in positives) <= 0:
    raise SystemExit(
        f"Artifact cache {path} records all-zero positives; it predates the "
        f"positive-count fix. Delete it and resubmit."
    )
print(f"Artifact cache {path} is version 3 with {sum(int(v) for v in positives)} positives.")
PY

experiments=(baseline baseline baseline depth8 depth8 depth8 width256 width256 width256)
seeds=(42 43 44 42 43 44 42 43 44)

task_id="${SLURM_ARRAY_TASK_ID}"
experiment="${experiments[$task_id]}"
seed="${seeds[$task_id]}"
output_dir="checkpoints/v4/a10plus_confirm10/${experiment}/seed_${seed}_${SLURM_ARRAY_JOB_ID}"

python src/train.py \
  --experiment "${experiment}" \
  --stage confirm \
  --seed "${seed}" \
  --epochs 10 \
  --data-dir "${DATA_DIR}" \
  --output-dir "${output_dir}" \
  --device cuda
