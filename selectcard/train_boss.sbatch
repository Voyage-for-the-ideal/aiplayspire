#!/bin/bash
#SBATCH --gres=gpu:1
#SBATCH -c 1
#SBATCH --cpus-per-task=16
#SBATCH --time=24:00:00
#SBATCH --job-name=selectcard-boss

# 三步流程：全量 visible_boss 构建 -> 训练 -> 推理冒烟
# 重跑前注意：processed_data_v3_boss 若已存在会拒绝覆盖（保护旧数据），
# 确认无误后手动删除或改名即可。

set -euo pipefail

export PATH="/home/scc/pb23151808/software/miniconda3/bin:$PATH"
source /home/scc/pb23151808/software/miniconda3/etc/profile.d/conda.sh

conda activate spire

cd /home/scc/pb23151808/Desktop/selectcard

python -c "import torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0))"

# ---- 第 1 步：全量构建 visible_boss 侧车并审计 ----
# 每局每个 Act：有 boss 战斗记录 -> 用记录；没记录（死在 boss 前）-> seed 猜测兜底。
# 审计检查每个 (Act, 升阶) 组合的猜测准确率 >= 90% 且样本数足够，
# 不达标整个作业直接失败退出，不会带病训练。
cd src
python generate_visible_boss_dataset.py \
    "../STS Data" \
    "../processed_data_v2" \
    "../processed_data_v3_boss" \
    --audit-path "../boss_audit.csv"
cd ..

# ---- 第 2 步：用带 boss 的新数据集训练 ----
# 旧数据不动；通过 STS_DATA_DIR 环境变量把训练指向新目录
export STS_DATA_DIR="/home/scc/pb23151808/Desktop/selectcard/processed_data_v3_boss"
python src/train.py

# ---- 第 3 步：推理冒烟（新 checkpoint 可加载、带 visible_boss 前向正常）----
python - <<'PY'
import sys
sys.path.insert(0, "src")
from inference import STSInferenceEngine
engine = STSInferenceEngine("checkpoints/best_sts_value_model_final.pth")
score = engine.evaluate_state({
    "deck": ["Strike_R", "Strike_R", "Defend_R"],
    "relics": ["Burning Blood"],
    "hp": 50, "max_hp": 80, "gold": 100,
    "floor": 5, "ascension": 20,
    "visible_boss": "Hexaghost",
})
print("inference smoke OK, V =", score)
PY
