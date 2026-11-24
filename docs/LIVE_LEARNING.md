# 实战价值／地图资源学习：第一阶段

本阶段实现独立学习日志、离线导出及 CPU 合成训练闭环。默认 Agent 与地图策略保持原配置；未启动真实对局、GPU 作业、完整数据重建或自动部署。所有输出要求新目录，旧数据与 checkpoint 原样保留。依赖沿用 conda `spire`。

## 入口与阶段边界

| 入口 | 行为 |
|---|---|
| Agent `--learning-trace-dir <新目录>` | 显式记录 `live-learning-v1`，与旧 `--policy-trace-dir` 可同时使用 |
| `python -m selectcard.src.live_value_data` | 从学习日志导出价值 Parquet、独立房间 JSONL、清单及覆盖报告 |
| `python -m selectcard.src.finetune` | 显式加载父 format-12 checkpoint，执行 1–100 步 CPU 冒烟更新 |
| `python -m selectcard.routing_calibration` | 独立的均值／P90／死亡概率线性模型 CPU 冒烟与重载 |
| `python -m selectcard.src.live_smoke` | 生成合成日志和合成父模型，执行上述整个闭环；没有游戏连接或 GPU 路径 |

本阶段的两个训练入口用于验证数据和参数更新契约，产物均标为 `promotable=false`。正式历史回放混合、固定预算微调选模、风险模型在线接入、多组实战评估留待下一阶段。地图冒烟产物格式为 `routing-calibration-smoke-v1`，没有新增在线地图策略开关。

## 学习日志

每局使用独立 UUID，文件以独占创建方式写入。每行包含 schema、run_id、递增 sequence 和单调时间。

- `run_start`：种子分组、角色、实际进阶、接入楼层、运行模式、可选 runtime manifest、价值模型 SHA-256、代码指纹。runtime manifest 是否提供单独标明；采集器保存清单，不宣称已经独立验证 Java 运行预算。
- `value_query`：评分时的公开价值状态、预期楼层、Heart 概率、标量价值，以及可用的 hazard／停止位置分布。查询绑定当时观察状态的指纹；最多保留 512 个待关联查询在内存，完整查询仍落盘。
- `decision_submitted`：完整结构化候选、实际动作、提交前状态、查询引用、behavior、decision_id 和 intent_id。ACK 只产生提交记录。
- `decision`：观察到生效后的快照、确认标记及通过状态核对的查询引用。进入 GRID 的中间步骤不产生价值训练样本；根查询通过 intent_id 延续到实际升级／移除完成。
- `unconfirmed`：超时或终止时尚无生效证据的提交。
- `room_transition`：地图点击前公开状态及牌组摘要、实际目标、首次观察到的战斗奖励边界、下次地图资源或死亡状态。状态分为 survived、died、observation_gap、unconfirmed、censored。
- `run_end`：已知终局、原始楼层、规范化楼层、最后观察楼层、Heart、通关和结束原因。Heart 胜利规范到 57；中断的 Heart 为 null。结算缺少 seed_group 时使用当前已绑定的局身份。

行为来源沿用已有策略 adapter 的记录；普通 baseline 尚无完整的规则／LLM 细分时，明确标作 `baseline_unclassified`。评分查询及其匹配另行记录，不将“查询过价值网络”直接等同于“由价值网络选择”。

价值状态使用字段白名单，排除 seed、RNG、未来 Boss、router plan 和结果标签。与在线共用 value payload；实际进阶缺失时不生成价值状态。全局特征顺序、map/history/key 打包、73-item cap 均沿用 format-12。通用旧客户端的进阶缺省行为仍保留，明确的进阶读取实际值。

## 导出与标签

从仓库根使用现有 `spire` 环境运行，路径均应为新目录：

```powershell
Set-Location D:\code\aiplayspire
conda run --no-capture-output -n spire python -m selectcard.src.live_value_data --trace-dir D:\code\aiplayspire\debug\learning-traces-new --output-dir D:\code\aiplayspire\selectcard\processed_data_v4\live-learning-new
```

价值样本首版覆盖 CARD_REWARD、SHOP_SCREEN、REST 和完成后的 GRID。必须同时满足：候选合法、动作有提交记录、后状态稳定、实际价值状态与当前／根 intent 的评分状态一致。一条确认决策只导出一个实际状态；未选候选、未知菜单、未匹配模拟状态不附加终局标签。超 cap 样本进入排除计数。

输入逐局读取，Parquet 分片缓冲默认 256 条；单局评分记录保留在内存以验证跨 GRID 引用。输出包括：

- `<split>_valid_chunk_*.parquet`、`<split>_censored_chunk_*.parquet`；完全没有可监督未来的样本保存在 censored 分区。
- `rooms.jsonl`：明确 M／E 房间的净 HP 变化、最大 HP 变化、死亡及回归 mask。
- `dataset_manifest.json`：编码规范、源文件 SHA-256、分片指纹、train-only 计数和训练先验审计所需字段。
- `coverage.json`：各 split 样本、角色／进阶／结局覆盖、排除原因和训练目标覆盖。

种子组使用与独立策略模块相同的稳定 80/10/10 hash。整组划分不依赖结局。训练导出要求运行模式明确为标准模式：daily、endless、trial、beta、chose_seed 均为 false，special_seed 为 0；其他模式保留日志并列为排除项。相同 run_id 的完全相同文件副本去重；同 ID 冲突文件报错。导出验证提交／确认一致性、查询所有权、实际状态匹配、终局顺序和源文件稳定性。未关闭日志或末尾截断记录保留已证明的前缀，作为删失处理。房间死亡若已有独立结算证据，即使缺少整局 footer，仍保留该房间的死亡标签；整局价值结果保持删失。

现有历史价值数据按旧 run hash 划分；本阶段没有直接混入这些分片。下一阶段混合历史数据时，先完成历史种子到统一 seed_group 的重分组，避免跨来源泄漏。

房间标签为 `进入前 HP - 离开后 HP`，允许负值。它包含战斗回血和房间内奖励处理，属于净资源变化而非总承伤；死亡记录仅参与死亡分类，净损失回归 mask 为 false。药水记录为槽位净变化，不推断真实使用次数。观察缺口／中断不标成死亡。未来在线使用该净变化模型时，应与现有显式战后回血逻辑分离。

## CPU 合成闭环

以下是本阶段推荐的独立复现命令；全部使用合成记录和新建父模型：

```powershell
Set-Location D:\code\aiplayspire
$env:OMP_NUM_THREADS='1'
$env:MKL_NUM_THREADS='1'
conda run --no-capture-output -n spire python -m selectcard.src.live_smoke --output-dir D:\code\aiplayspire\debug\live-learning-smoke-new
```

该命令构造 train/val/test 种子组、实际选牌、未选候选、净回血和终局死亡，导出后生成 32 维／2 层的合成 format-12 父模型。价值头更新 2 步；独立地图模型更新 20 步。test 不参与拟合或评估。

价值冒烟沿用父模型的架构、词表、归一化、全部损失配置及训练先验，分别审计新 cohort 的计数；默认仅更新 value_head，`--scope last2` 可测试最后两层与 final_norm 更新，embedding 和 key_head 保持冻结。训练使用原 value_loss 和单调机制约束，结束后恢复全局配置。模型重载逐参数核对，父文件 SHA-256 保持一致。零权重的历史进阶段保留；新训练样本若落入该阶段则报错，避免静默零损失。

地图模型在 train 上拟合特征均值／尺度，分别使用 MSE、P90 pinball、死亡 BCE，产物保存独立 JSON；val 报告净变化 MAE、P90 覆盖／pinball、Brier 和死亡 log loss。该报告验证管线，不表示真实胜率收益。

## 回归

```powershell
Set-Location D:\code\aiplayspire
conda run --no-capture-output -n spire python -m unittest discover -s sts_ai_framework/tests -t .
Set-Location D:\code\aiplayspire\selectcard\src
conda run --no-capture-output -n spire python -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api test_live_learning
```

新增回归覆盖 ACK／重试／超时、局身份切换、无种子的死亡结算、删失与 Heart 规范化、跨 GRID intent、隐藏字段、伪造查询、冲突副本、截断日志、独占输出、CPU 训练／重载、冻结参数／先验、输入张量一致性以及 test 极值不影响地图拟合。训练／在线一致性测试同时锁定 pandas NumPy 幕编号的处理。
