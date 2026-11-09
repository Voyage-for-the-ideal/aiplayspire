#!/bin/bash
#SBATCH --gres=gpu:1
#SBATCH --output=train_a10plus_multiscale10_%j.log

set -eo pipefail

export PATH="/home/scc/pb23151808/software/miniconda3/bin:$PATH"
source /home/scc/pb23151808/software/miniconda3/etc/profile.d/conda.sh
conda activate spire

cd /home/scc/pb23151808/Desktop/selectcard

python -c 'import torch; assert torch.cuda.is_available(), "CUDA is unavailable"; print(torch.cuda.get_device_name(0))'

data=processed_data_v4/a10plus_multiscale_v1
run="seed_42_${SLURM_JOB_ID:-$(date +%Y%m%d_%H%M%S)}"
audit_dir="experiment_results/a10plus_multiscale10/data_audit/${run}"

if [[ -e "$audit_dir" ]]; then
    echo "Refusing to replace existing audit reports: ${audit_dir}" >&2
    exit 1
fi

if [[ -f "${data}/dataset_manifest.json" ]]; then
    echo "Found existing dataset; auditing before reuse: ${data}"
else
    echo "Rebuilding A10+ data from STS Data: ${data}"
    python src/rebuild_multiscale.py \
        --input-dir "STS Data" --output-dir "$data" --workers 16
fi

python src/inspect_multiscale.py --data-dir "$data" --split train \
    --output "${audit_dir}/train.json"
python src/inspect_multiscale.py --data-dir "$data" --split val \
    --output "${audit_dir}/val.json"

experiments=(
    full_fine_legacy
    full_fine_new
    multiscale
    multiscale,depth8
    multiscale,width256
)

for experiment in "${experiments[@]}"; do
    label="${experiment//,/_}"
    output="checkpoints/v4/a10plus_multiscale10/${label}/${run}"
    evaluation="experiment_results/a10plus_multiscale10/${label}/${run}"
    if [[ -e "$output" || -e "$evaluation" ]]; then
        echo "Refusing to replace existing experiment artifacts: ${label}/${run}" >&2
        exit 1
    fi

    echo "Training: ${experiment}; seed=42; epochs=10; output=${output}"
    python src/train.py --experiment "$experiment" --stage confirm --seed 42 \
        --epochs 10 --data-dir "$data" --output-dir "$output" --device cuda

    echo "Validation: ${experiment}; output=${evaluation}"
    python src/train.py --evaluate \
        --checkpoint "${output}/sts_value_model_final.pth" --data-dir "$data" \
        --evaluation-split val --output-dir "$evaluation" --device cuda
done

echo "Completed data preparation and all five experiments."
