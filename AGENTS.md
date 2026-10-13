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

AIPlaySpire：让 AI 自动通关《杀戮尖塔》的研究项目。游戏侧 4 个 Java mod 经 ModTheSpire 加载；Python 侧 agent 对每个非战斗决策执行「枚举候选 → 模拟下一状态 → 价值网络 V(S) 打分取 argmax」；战斗由独立无头游戏实例上的树搜索负责；LLM 只作为非结构化界面的兜底。

| 目录 | 职责 |
|---|---|
| `sts_ai_framework/` | Python agent 主循环：轮询 CommunicationMod (`:5000`) 状态 → 屏幕分类 → 决策分发 → 提交动作并校验生效。`routing/` 负责 6 层有限视野确定性地图规划（`MAP` 决策 0 LLM）；`llm_agent_parts/` 负责动作分发、价值网络候选打分及非地图非结构化界面的 LLM 兜底；回归测试在 `sts_ai_framework/tests/` |
| `selectcard/` | 价值网络 V(S)：Set Transformer + bucket-hazard / Heart 输出头；含重放式数据流水线、训练、进程内推理与可选 FastAPI 服务（细节见下文 "Set Transformer v2"） |
| `StSCommunicationMod/` | Communication Mod：游戏内 HTTP 桥（`:5000`，`/state` `/action` `/card_info`），结构化事件语义与 Cursed Tome 多步本地流程 |
| `STSStateSaver/` | Save State Mod：战斗状态完整序列化/恢复（含 RNG）—— 树搜索的回滚原语 |
| `LudicrousSpeed/` | 无动画、阻塞式执行真实游戏引擎 + JSON 命令接口（含重放状态 diff 校验） |
| `scumthespire/` | Battle Ai Mod：独立无头游戏实例（`:5125`）上的战斗树搜索与战术评估器 |
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

## Set Transformer v4 地图条件化与实验

- New processed datasets live in `selectcard/processed_data_v4/`; generated data must not be committed. Keep older dataset directories unchanged.
- Rebuild it from `selectcard/` with `python src/data_pipeline.py`.
- Run model tests from `selectcard/src/` with `python -m unittest test_value_network_v2.py`（以及地图和 V4 回归：`test_map_context.py`、`test_value_network_v3.py`、`test_value_network_v4.py`；依赖在 conda `spire` 环境）.
- Train from `selectcard/` with `python src/train.py --experiment baseline --stage pilot` (1% run-level subset, 8 epochs); confirmation uses `--stage confirm --seed 42/43/44` (20 epochs). Full data rebuilding and GPU training are separate tasks. Evaluate read-only with `--evaluate --checkpoint <path> --output-dir <new-directory>`; `--test-only` is an alias, not a positional checkpoint argument. Test evaluation requires explicit `--evaluation-split test`.
- The default architecture is one early Global token, Pre-LN attention blocks, and final CLS pooling. Ablations are controlled by `GLOBAL_CONDITIONING` and `NORM_POSITION` in `selectcard/src/config.py`.
- **V4 延续 v3 地图条件化**（`Config.USE_MAP_CONTEXT`，设计见仓库根 `MODEL_V4_DESIGN.md`，历史设计见 `MODEL_V3_DESIGN.md`）：序列为
  `[CLS] + [GLOBAL] + [CHARACTER] + [BOSS] + [KEYS] + [MAP×105] + [HISTORY×8] + item tokens`（token 类型 embedding），并新增 3 路 key-progress 辅助头（权重 `KEY_HEAD_LOSS_WEIGHT`）。
  - `[CHARACTER]` 使用稳定的 `IRONCLAD=0`、`THE_SILENT=1`、`DEFECT=2`、`WATCHER=3` 编码；训练和推理拒绝缺失或未知角色。
  - 地图上下文由 `selectcard/src/map_context.py` 打包（房间/真实唯一边/带火/current_candidate/reachable + 当前幕走过的路径 history 视野 8）；不再包含 `on_plan`、`plan_rooms` 或 router 计划。
    归档 run 经 `sts_mapgen.py` 由 `seed_played` 逐位重建整幕真图（历史回验 99.97%），训练/推理共用同一打包格式。
  - `data_pipeline.py` 为每个样本附加上下文列（`map_rooms/map_flags/history_rooms/key_states/key_known_mask/act`）；历史 `.run` 无法确认的蓝钥匙保持 unknown，训练不使用 plan dropout 或 plan curriculum。
  - V4 checkpoint 延续 post-action 状态：每条训练样本的特征表示历史选择执行后的 `S′`。本轮提升 preprocessing、map-context 和 checkpoint schema 版本，旧 v3 checkpoint 需重新训练，不兼容加载。
  - `use_map_context=False` 时仍使用 `[CLS] + [GLOBAL] + [CHARACTER] + [BOSS] + item tokens`；这是新角色 token 契约，不等同于旧 v2 checkpoint 兼容模式。
  - 框架侧 `sts_ai_framework/llm_agent_parts/map_payload.py` 只注入 GameState 的地图/钥匙/当前幕 path_history，不向价值网络 payload 传递 router 计划。
- 输出头是 20 个楼层桶的 stopping-hazard + 1 个 Heart-win logit；端点与标签契约定义在 `selectcard/src/data_contract.py`（`VALUE_TARGET_SCHEMA = "bucket-hazard-v1"`）。标量价值 V(S) = E[终止楼层] + `HEART_WIN_BONUS_FLOORS` × P(Heart)。
- Format-11 checkpoints freeze complete architecture configuration, the 73-item map budget, vocabulary, normalization, training priors, character/key observability and context schemas. Format 10 and earlier checkpoints require retraining. V4 preprocessing is `set-transformer-v10-current-edges`; map context is `map-context-v5-current-edges`.
- V4 coordinates are float `[x/6,y/14]`. Map bit 2 marks current position candidates only; history is carried by HISTORY tokens. Shared packing derives degrees from final unique edges and adds `map_outgoing_mask[105]` (left/straight/right); row 14 connects to a virtual Boss. Preserve map-generator RNG/bookkeeping behavior.
- V4 experiments and run-cluster bootstrap tools are documented in `MODEL_V4_DESIGN.md`. Optional components default off; MHA uses `need_weights=False`. Preserve item aggregation/order and the 73-item cap. No automatic test-set evaluation or candidate deployment.
- Optional HTTP inference server: `uvicorn src.api:app` from `selectcard/` (`POST /recommend/choice`, `/recommend/shop`).
- Do not commit `selectcard/checkpoints/`, `selectcard/processed_data/`, `selectcard/processed_data_v2/`, `selectcard/processed_data_v3/`, `selectcard/processed_data_v4/`, or `selectcard/experiment_results/` artifacts.
