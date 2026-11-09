#!/bin/bash
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=16
#SBATCH --time=24:00:00
#SBATCH --job-name=selectcard-a10plus-capacity
#SBATCH --output=train_a10plus_v4_capacity_%j.log

set -eo pipefail

export PATH="/home/scc/pb23151808/software/miniconda3/bin:$PATH"
source /home/scc/pb23151808/software/miniconda3/etc/profile.d/conda.sh
conda activate spire

cd /home/scc/pb23151808/Desktop/selectcard

python -c 'import torch; assert torch.cuda.is_available(), "CUDA is unavailable"; print(torch.cuda.get_device_name(0))'

DATA_DIR=processed_data_a10plus_v4

# Reuse the dataset built by the baseline job. Keep the same seed and pilot
# run-level sample so both candidates are comparable to the baseline.
python src/train.py \
  --experiment depth8 \
  --stage pilot \
  --seed 42 \
  --device cuda \
  --data-dir "${DATA_DIR}" \
  --output-dir checkpoints/v4/a10plus/depth8/pilot/42

python src/train.py \
  --experiment width256 \
  --stage pilot \
  --seed 42 \
  --device cuda \
  --data-dir "${DATA_DIR}" \
  --output-dir checkpoints/v4/a10plus/width256/pilot/42
