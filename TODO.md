# TODO

## 高优先级

- [ ] 将 Neow 祝福选择从 LLM 迁移到本地价值网络处理（当前仅硬编码了 talk，实际祝福选择如"选稀有牌""+Max HP""换 Boss 遗物"仍走 LLM）
- [ ] 满血篝火休息问题：模型存在"休息→存活率高"的伪相关，满血时休息 V(S') 仍高于升级。考虑在 `_handle_rest_room` 中满血时剔除 rest 选项，或重新训练模型
- [ ] `_handle_rest_room` 中 ascension 硬编码为 20，应从当前游戏状态动态获取

## 中优先级

- [ ] 本地价值网络添加 `random_relic` 专用 token，替代 `Anchor` 作为随机遗物代理值（需要重训模型 + 扩充词汇表）
- [ ] MAP 选路从 LLM 迁移到本地模型（当前 LLM 总是选 choice_index=0，未实际利用 BFS 路线摘要信息）
- [ ] 事件解析器 `_parse_event_effects` 对复杂事件文本的覆盖率有限，考虑用 LLM 结构化解析替代正则
