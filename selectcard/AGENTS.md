# selectcard — 价值网络与离线非战斗策略

- 价值网络遵循 [V4 模型契约](../MODEL_V4_DESIGN.md)；变更数据、训练或推理时按需读取[价值网络工作流](../.agents/skills/aiplayspire-value-model/SKILL.md)。独立的 `policy/` 遵循[非战斗策略文档](../docs/NONCOMBAT_POLICY.md)。
- 价值网络旧格式需重建或重训，不隐式转换；保留已有数据、checkpoint 和报告，生成物不提交。
- 全量重建、GPU 训练、test 集评估和部署仅在任务明确要求时执行。
