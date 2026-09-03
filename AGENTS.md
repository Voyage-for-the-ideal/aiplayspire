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
| `sts_ai_framework/` | Python agent 主循环：轮询 CommunicationMod (`:5000`) 状态 → 屏幕分类 → 决策分发 → 提交动作并校验生效。决策逻辑在 `llm_agent_parts/`（route / decision / choice / action / info_prompt mixin：确定性地图路由、价值网络候选打分、LLM 兜底）；回归测试在 `sts_ai_framework/tests/` |
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

## Set Transformer v2

- The v2 processed dataset lives in `selectcard/processed_data_v2/`; it is generated and must not be committed.
- Rebuild it from `selectcard/` with `python src/data_pipeline.py`.
- Run model tests from `selectcard/src/` with `python -m unittest test_value_network_v2.py`.
- Train from `selectcard/` with `python src/train.py` (evaluate a checkpoint with `--test-only`); use a CUDA-enabled PyTorch environment for the full dataset.
- The default architecture is one early Global token, Pre-LN attention blocks, and final CLS pooling. Ablations are controlled by `GLOBAL_CONDITIONING` and `NORM_POSITION` in `selectcard/src/config.py`.
- 输入序列为 `[CLS] + [GLOBAL] + [BOSS] + item tokens`：`[BOSS]` 是当前幕可见 Boss 的 embedding 上下文 token（数据侧由 `boss_context.py` / `build_boss_context.py` 支持）。
- 输出头是 20 个楼层桶的 stopping-hazard + 1 个 Heart-win logit；端点与标签契约定义在 `selectcard/src/data_contract.py`（`VALUE_TARGET_SCHEMA = "bucket-hazard-v1"`）。标量价值 V(S) = E[终止楼层] + `HEART_WIN_BONUS_FLOORS` × P(Heart)。
- v2 checkpoints embed model configuration, the frozen vocabulary, normalization statistics, and preprocessing version. Legacy bare `state_dict` checkpoints are intentionally incompatible and require retraining.
- Optional HTTP inference server: `uvicorn src.api:app` from `selectcard/` (`POST /recommend/choice`, `/recommend/shop`).
- Do not commit `selectcard/checkpoints/`, `selectcard/processed_data/`, or `selectcard/processed_data_v2/` artifacts.
