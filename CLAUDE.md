# AIPlaySpire — Agent Working Guide

## Working Preferences

- Prefer small, reviewable changes.
- Ask before adding dependencies.
- Always explain what changed and how it was verified.
- If tests cannot run, state why and provide the exact command to run.
- Do not modify secrets, credentials, lockfiles, or generated files unless explicitly requested.
- python中需要的库在conda中的spire环境。
- `CLAUDE.md` 与本文件内容保持一致，修改任一方时必须同步另一方。

## Project Architecture

AIPlaySpire：让 AI 自动通关《杀戮尖塔》的研究项目。游戏侧 4 个 Java mod 经 ModTheSpire 加载；Python 侧 agent 对每个非战斗决策执行「枚举候选 → 模拟下一状态 → 价值网络 V(S) 打分取 argmax」；战斗后端正在迁移为 sts_lightspeed 原生回合搜索，旧 Java 双实例链保留作对照；LLM 只作为非结构化界面的兜底。

| 目录 | 职责 |
|---|---|
| `sts_ai_framework/` | Python agent 主循环：轮询 CommunicationMod (`:5000`) 状态 → 屏幕分类 → 决策分发 → 提交动作并校验生效。`routing/` 负责 6 层有限视野确定性地图规划（`MAP` 决策 0 LLM）；`llm_agent_parts/` 负责动作分发、价值网络候选打分及非地图非结构化界面的 LLM 兜底；`--policy-checkpoint` / `--policy-mode` / `--policy-types` / `--policy-trace-dir` 接入离线非战斗策略（见 `docs/NONCOMBAT_POLICY.md`）；回归测试在 `sts_ai_framework/tests/` |
| `selectcard/` | 价值网络 V(S)：Set Transformer + bucket-hazard / Heart 输出头；含重放式数据流水线、训练、进程内推理与可选 FastAPI 服务；`policy/` 为独立的离线非战斗策略（`noncombat-policy-v1`，BC/AWR），与价值网络数据/checkpoint 分离（价值网络细节见下文 "Set Transformer v4 多尺度价值网络"） |
| `StSCommunicationMod/` | Communication Mod：游戏内 HTTP 桥（`:5000`，`/state` `/action` `/card_info`），结构化事件语义与 Cursed Tome 多步本地流程 |
| `STSStateSaver/` | Save State Mod：战斗状态完整序列化/恢复（含 RNG）—— 树搜索的回滚原语 |
| `LudicrousSpeed/` | 无动画、阻塞式执行真实游戏引擎 + JSON 命令接口（含重放状态 diff 校验） |
| `scumthespire/` | Battle Ai Mod：真实游戏的状态／动作桥及 UI，保留旧 Java 搜索与战术评估器作对照 |
| `battle_engine/` | 本地 C++ 回合搜索＋常驻 worker＋Python 标准库网关：三档预算、逐动作校验和路径复用，默认关闭 Java 回退；四角色完整模拟仍在补齐，进度见 `docs/LIGHTSPEED_MIGRATION.md` |
| `cardcrawl/` | 反编译的原版游戏源码，只读内容目录（数据校验与内容对照用） |

- 战斗流水线（双实例协作、存档目录、mod 构建顺序）见 [AUTOFIGHT.md](AUTOFIGHT.md)；`./build_all.sh` 构建全部 mod 并输出到 `_ModTheSpire/mods/`。
- Agent 入口（仓库根）：`python -m sts_ai_framework --interval 2.0`。
- Framework 回归测试（仓库根）：`python -m unittest discover -s sts_ai_framework/tests -t .`。
- `lib/`、`debug/`、`_ModTheSpire/` 是 gitignore 的运行时产物/依赖目录：不要提交，也不要写进文档的目录结构表。

## Slay the Spire Mods

目前部署 4 个 mod，每次对应代码改动后必须同步更新其版本号，并保持描述与实际功能一致：

| Mod | 目录 | Mod ID | 版本/描述位置 |
|---|---|---|---|
| STS Save State Mod | `STSStateSaver/` | `SaveStateMod` | `pom.xml` 的 `<version>` / `<description>`（过滤注入 `ModTheSpire.json`） |
| Ludicrous Speed | `LudicrousSpeed/` | `LudicrousSpeed` | 同上 |
| Battle Ai Mod | `scumthespire/` | `BattleAiMod` | 同上 |
| Communication Mod | `StSCommunicationMod/` | `CommunicationMod` | `src/main/resources/ModTheSpire.json`（硬编码） |

版本更新规则（semver）：破坏性变更 → major；新功能（feat）→ minor；修复/性能（fix/perf）→ patch。`pom.xml` 中 `ModTheSpire.json` 的 `description` 使用 `${project.description}` 占位符，故描述也写在 `pom.xml`（注意 XML 转义）；Communication Mod 直接改 JSON。

更新后验证：`mvn package` 并确认 `target/classes/ModTheSpire.json` 中的 `version` 和 `description` 已正确生成，再部署到 `_ModTheSpire/mods/`。

## Set Transformer v4 多尺度价值网络

- 新数据放在 `selectcard/processed_data_v4/a10plus_multiscale_v1/`；旧数据、checkpoint 原样保留，生成物不提交。
- Python 依赖使用 conda `spire`，不添加依赖。完整数据重建和 GPU 训练是独立任务，不自动执行。
- 从 `selectcard/` 执行 `python src/rebuild_multiscale.py --input-dir "STS Data"`：A10+ 原始结果重建后在新目录注入 visible Boss。审计用 `python src/inspect_multiscale.py --data-dir <目录> --split train --output <新报告路径>`。
- Agent 仍对候选 post-action 状态评分。`V(S)=(E[终止楼层]+3*P(Heart))/60`，终止楼层来自原始结果，Heart 胜利规范到 57。中途退出保留删失，不伪造失败标签。
- 默认多尺度目标 `multiscale-hazard-v1`：当前进度段保留细桶，未来按 34/51/57 合并粗桶。模型输出 20 个细 hazard、3 个粗 hazard、23 个条件停止位置和 1 个条件 Heart；训练、探针和推理共用分布合成。
- 四段边界为 17/34/51/57。全局特征为四段 one-hot、段内进度、floor/57、HP 比例/绝对值、max HP、金币、进阶，共 11 维；训练与推理共享顺序，禁止硬编码字段索引。
- 序列保持 `[CLS]+[GLOBAL]+[CHARACTER]+[BOSS]+[KEYS]+[MAP×105]+[HISTORY×8]+item tokens`。角色编码稳定为 IRONCLAD=0、THE_SILENT=1、DEFECT=2、WATCHER=3，缺失和未知角色拒绝。
- 地图采用浮点 `[x/6,y/14]`、真实唯一边、current_candidate、reachable、三个方向出边；history 仅当前幕已离开的 8 个房间。不得加入 router 计划，不改变地图生成 RNG/bookkeeping。
- 钥匙使用 value＋known mask；已确认绿钥匙跨幕持有，后续地图缺失也不能丢失。无法确认的状态保持 unknown，蓝钥匙在历史记录进入 Act IV 前保持 unknown。
- 格式为 checkpoint **12**、preprocessing `set-transformer-v11-multiscale`、global features `global-features-v7-floor`、map context `map-context-v6-persistent-keys`、training artifact cache **4**。旧数据和格式 11 及更早 checkpoint 需要重建／重训，不进行隐式转换。
- Checkpoint 冻结架构、resolution、全部损失权重、词表、归一化、训练先验和 **73-item cap**。缓存先验的正例/有效数与 manifest 训练计数逐项校验；不得用验证或测试集拟合统计。
- 默认结构为 192 维、5 层、4 heads、ReLU、Pre-LN、early Global、CLS；额外结构实验默认关闭，MHA 使用 `need_weights=False`，item 聚合和顺序保持。
- 初筛为固定 run hash 的 1% train/val、8 轮；确认默认完整 train/val、**10 轮**，batch 512、AdamW lr 2.8e-4、前三轮屏蔽地图/history，余弦覆盖实际预算。seed 默认 42，可独立使用 43/44 检验稳定性。
- `train_multiscale10.sbatch` 准备五组：full_fine_legacy、full_fine_new、multiscale、multiscale+depth8、multiscale+width256；同数据、同训练统计、同 10 轮预算。旧 pilot/confirm15 的全零先验结果仅为历史参考。
- 共同评分固定使用 17/34/51/57 到达概率与 Heart，不随候选训练损失改变；同时报告真实楼层 MAE、角色/进阶/楼层/预测距离分组及 37/57 的近远端覆盖。
- 评估用 `python src/train.py --evaluate --checkpoint <format12路径> --data-dir <目录> --output-dir <新目录>`，默认 val。`--test-only` 是布尔别名，test 必须明确 `--evaluation-split test`。不得改写源 checkpoint，不自动部署或评估 test。
- 比较工具按整局配对 bootstrap；要求相同已完成预算、数据、共同评分与种子集合，不再硬编码 20 轮/三个种子。共同 NLL 改善且无显著 MAE/机制排序退化才报告可晋级；证据不足保留参考组，单种子明确 run-cluster-only 不确定性。
- 回归从 `selectcard/src/` 执行 `python -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api`。原始归档缺失时仍必须执行合成数据闭环，并说明跳过项。
- HTTP 推理入口仍为 `uvicorn src.api:app`，`POST /recommend/choice` 与 `/recommend/shop`。设计与命令见根目录 `MODEL_V4_DESIGN.md`。
- 不提交 `selectcard/checkpoints/`、各版本 processed_data 或 `selectcard/experiment_results/`。
