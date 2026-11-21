# AIPlaySpire — 仓库指南

AIPlaySpire 让 AI 自动通关《杀戮尖塔》。结构化非战斗决策走规则、独立策略模型或候选动作后的价值评分；地图决策不调用 LLM，LLM 只作非结构化界面的兜底。战斗统一使用同一 C++ 核心：Windows Mod 内嵌 JNI，Linux 无头采集器使用 worker。

## 仓库地图

| 路径 | 职责 |
|---|---|
| `sts_ai_framework/` | Python 游戏循环、动作分发、地图规划和 [非战斗策略接入](docs/NONCOMBAT_POLICY.md) |
| `selectcard/` | 价值网络的数据、训练和推理；`policy/` 是独立非战斗策略，与价值网络分开使用数据和 checkpoint |
| `battle_engine/` | [本地 C++ 回合搜索](docs/BATTLE_ENGINE.md)、Linux 常驻 worker 和 Windows JNI 入口 |
| `StSCommunicationMod/` | 游戏内 HTTP 状态与动作桥（默认 `:5000`） |
| `STSStateSaver/`、`LudicrousSpeed/`、`scumthespire/` | 战斗快照、真实引擎回放和游戏内原生搜索入口；见 [构建与运行](AUTOFIGHT.md) |
| `cardcrawl/` | 只读原版游戏行为参考 |

## 常用命令

在仓库根目录执行：

- `conda activate spire`：Python 项目环境。
- `python -m sts_ai_framework --interval 2.0`：启动游戏 agent。
- `python -m unittest discover -s sts_ai_framework/tests -t .`：Framework 回归。
- `bash build_all.sh`：按依赖顺序构建并部署四个 Mod；仅在需要更新本地游戏 JAR 时运行。
- `python tools/check_agent_guides.py`：检查根目录和 `selectcard/` 的两对指南同步。

## 工作约定

- 保持改动小且易审查；新增依赖前先征求确认。说明改动和验证结果；有测试未运行时给出原因和准确命令。
- 保留凭据、锁文件和生成物，除非任务明确要求修改。`lib/`、`debug/`、`_ModTheSpire/` 等运行时目录保持忽略，不纳入仓库结构表。
- 修改 Mod 代码时同步更新对应版本，并确保描述与实际行为一致。修改任一 `AGENTS.md` 或 `CLAUDE.md` 时，同步同目录的另一份。
- 部署新版 Mod 时直接覆盖游戏目录中同名旧 JAR，不创建或保留旧 JAR 备份；回滚从对应的 Git 提交重新构建。验收后提交对应源码、测试和配置，JAR 产物不进 Git。
- 按需读取：[Mod 发布](.agents/skills/aiplayspire-mod-release/SKILL.md)、[战斗验证](.agents/skills/aiplayspire-battle-validation/SKILL.md)、[价值网络](.agents/skills/aiplayspire-value-model/SKILL.md)。
