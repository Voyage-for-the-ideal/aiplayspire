---
name: aiplayspire-value-model
description: 维护 AIPlaySpire 的 selectcard V4 价值网络。用于变更数据、输入特征、训练、评估或推理后选择回归和审计；独立的 BC/AWR 非战斗策略不在本技能范围内。
---

# V4 价值网络工作流

先读 [V4 模型契约](../../../MODEL_V4_DESIGN.md) 中与改动相关的小节。用 conda `spire`，保留旧数据、checkpoint 和报告；诊断和评估均写入新路径。独立的 BC/AWR 非战斗策略使用[另一套工作流](../../../docs/NONCOMBAT_POLICY.md)。

## 代码回归

从 `selectcard/src/` 执行：

```bash
python -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api
```

原始归档缺失时，报告相关地图回验跳过项，仍执行合成数据闭环：构建数据 → 短训练 → 保存 → 只读评估 → 对候选动作评分。不要用全量重建或 GPU 训练代替本地回归。

## 数据、训练与评估

以下命令从 `selectcard/` 执行；完整重建和 GPU 作业仅在任务明确要求时运行。

- **重建和审计。** 用 `python src/rebuild_multiscale.py --input-dir "STS Data"` 重建 A10+ 数据并注入可见 Boss；已有默认产物时用 `--output-dir <新数据目录>`。用 `python src/inspect_multiscale.py --data-dir <目录> --split train --output <新报告路径>` 审计，必要时另出 val 报告。训练统计只用训练数据，先验计数与 manifest 对账。
- **训练。** 单组调试用 `python src/train.py --experiment multiscale --stage pilot --output-dir <新目录>`；确认运行用 `--stage confirm --seed 42 --epochs 10`。训练非默认数据时传入 `--data-dir <目录>`。完整对照用 `sbatch train_multiscale10.sbatch`，实验组、共同预算和历史对照限制以模型契约为准。
- **评估与比较。** 用 `python src/train.py --evaluate --checkpoint <format12路径> --data-dir <目录> --output-dir <新目录>` 只读评估，默认 `val`。test 评估须显式要求并传入 `--evaluation-split test`。使用模型契约中的共同指标和整局配对方式比较；证据不足保留参考模型，不自动部署。
