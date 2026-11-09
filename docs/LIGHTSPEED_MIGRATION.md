# sts_lightspeed 迁移记录（2026-11-04）

## 当前交付状态

2026-11-04 增量：静默／观者显式分派及运行时目录均达到 **75/75**；
补齐最后 7 张牌、真实 CardLibrary 池和独立 RNG，从类型化 SaveState JSON 恢复 9 类选择及白名单剩余队列。
公共投影 v2、全部交互与实机对等仍待完成，
详细能力边界与命令见 [静默／观者增量交付](NATIVE_CHARACTER_SUPPORT.md)。
本批候选 Release 构建、后端 177 项（含 300 个逐卡／升级冒烟子例）、framework 168 项及 Java 112 项回归通过；历史清单 14/16
完成离线导入与单步命令生成，其余两份机器人样本缺真实卡池。尚未执行实机回放。

已完成迁移前归档、原生回合搜索框架、常驻 worker、三档预算和逐动作路径复用。
四角色导入已接通，静默和观者已补齐原版卡牌显式分派；**四角色完整交互验收与替代尚未完成**。
旧 Java 搜索、配置和启动入口保留，日常游戏目录和存档未更新。

| 项目 | 状态 |
|---|---|
| 三个个人 fork 的迁移前分支 | 已推送，462 个本地文件的 Git blob 与远端 tree 逐项一致 |
| C++ 回合内 DFS／回合间价值优先队列 | 已实现，MCTS 保留为显式离线对照 |
| 评分／排序 | 已迁移部分通用和角色机制，尚缺完整 Java 分项、顺序和终局收益对等验证 |
| 常驻进程、超时隔离、请求标识、路径复用 | 已实现，有离线回归 |
| 四角色原版全部机制 | 未完成；分派目录不等于交互正确性 |
| HAND／GRID／战斗奖励选择的恢复与执行 | 9 类选择及显式队列白名单已接通；未知可执行动作在发出命令前报错，完整链路待实机验收 |
| 实机回放、掉血／药水／胜率对照 | 待操作者执行，未启动游戏 |

## 迁移前归档

主仓库基线：`84d10b4e9dd04ec7cc668d7b757e4faeba7cec75`。
三个分支均名为 `codex/pre-lightspeed-migration`，上游默认分支和历史保留，没有创建 PR。

| fork | commit | 版本 | 本地文件数 |
|---|---|---|---:|
| [Voyage-for-the-ideal/scumthespire](https://github.com/Voyage-for-the-ideal/scumthespire/tree/codex/pre-lightspeed-migration) | `d666bbef93474b4e6800c5c82d3fa9e0497484d0` | 1.7.0 | 81 |
| [Voyage-for-the-ideal/STSStateSaver](https://github.com/Voyage-for-the-ideal/STSStateSaver/tree/codex/pre-lightspeed-migration) | `4eb8f17652a7201d67aca6b2dce2690faf4266c1` | 1.6.1 | 345 |
| [Voyage-for-the-ideal/LudicrousSpeed](https://github.com/Voyage-for-the-ideal/LudicrousSpeed/tree/codex/pre-lightspeed-migration) | `47d7b1886afeb6e5143228500a0f829eabdc2df6` | 1.2.0 | 36 |

各分支根目录的 `AIPLAYSpire-SNAPSHOT.json` 包含原路径、SHA-256、Git blob hash、基线和版本。
通过 Git Data API 提交，以保留原始文件字节；核对远端递归 tree 的每个 blob，并再次读取远端分支 SHA。
归档包含当时未提交的新源码及测试，排除了忽略的 JAR、构建、日志和运行目录。
`archive_mods.py` 是一次性冻结工具，遇到已存在的归档分支会停止，避免后续修改覆盖迁移前快照。

## 原生搜索与进程协议

正式实验入口使用 `TurnSearch`，以真实引擎的可枚举动作为边推进 `BattleContext`。
回合内 DFS，下一回合进入战术价值优先队列；有路径内状态去重、预算约束、胜利路径与部分进展返回，不再固定截断为 50 动作。
死亡判断优先于胜利标签。墙钟 deadline 贯穿搜索和动作执行循环，只返回已完整执行的候选。

Java 配置标识仍为 `FAST / BALANCED / DEEP`，默认 `BALANCED`：

| 档位 | C++ 墙钟上限 | 节点上限 |
|---|---:|---:|
| 即时 FAST | 250 ms | 5,000 |
| 平均 BALANCED | 2,000 ms | 15,000 |
| 质量 DEEP | 10,000 ms | 50,000 |

Java 对照后端原来的 10／30／90 秒上限保留。两个启动脚本默认不再设置 2 秒覆盖值；
`-MaxTimeMs` 仅在显式设置时生效。网关默认全局上限为 90 秒，正常预算由请求档位决定。

- `battle-engine-worker.exe`：兼容旧的单次 JSON 输入／输出。
- `battle-engine-worker.exe --serve`：一行一个 JSON 请求和响应，响应回显 `request_id`，stdout 只输出协议；引擎诊断重定向到 stderr。
- 搜索请求：`snapshot`、`session_id`、`search_profile`、`max_expansions`、`timeout_ms`；可显式指定 `search_mode=mcts` 作离线对照。
- 诊断操作：`import`、`rng_probe`、`evaluate`、`step`。`step` 只用于离线单卡效果测试。
- `release_session` 清理所属会话的缓存，不终止 worker。网关在 TCP 会话结束时调用。
- worker 超时、崩溃、错误响应或响应标识失配时清理进程，当前动作不自动重试；下一请求建立新进程。

外部 TCP 仍为 Java modified UTF-8 `writeUTF`，保留 `COMMAND_LIST / ERROR / DONE`，一请求返回一个带前置条件的动作。
网关串行管理一个常驻进程，健康探测不等待搜索锁。

## 路径复用与正确性边界

搜索保留动作路径；每次仍导入真实快照，只有下列条件全部满足才取缓存中的下一步：

- 同一会话、档位、显式预算、搜索模式和真实卡池；
- 正常决策点、空执行队列，以及完整有序的原生可比较状态一致；
- 四堆牌的 UUID 顺序一致，卡牌基础伤害／格挡／magic、misc 和执行标志未变化。

状态键包括能力、姿态、遗物计数、怪物阶段／历史、卡牌动态费用、永久牌组、Nightmare 目标及六条既有 RNG 和可选独立 cardRng 的内部状态。
生成牌的真实 UUID 尚未对齐时不复用；选择界面和终局边界不复用。非包围战斗的朝向字段归一化，避免无关游标导致失效。
缓存未命中则重新搜索；前一条已执行命令的公开预测与真实结果不符时仍隔离本场战斗。
Java 执行前的 context guard 增加姿态、卡牌基础属性和完整选择上下文；动作后的 `expected_screen` 独立校验。
公共投影格式仍为 v1，v2 统一能力／动态卡牌／终局字段是后续交付项。

`CharacterCards` 已补齐静默／观者原版牌的显式分派及一批毒／小刀、抽弃、姿态、真言、衍生牌和永久收益交互。
修复原 vendor 的 8 个卡牌颜色错位；Release 版未实现卡牌改为明确异常，避免静默无效果。
当前分派覆盖：铁甲 75/75、机器人 75/75、静默 75/75、观者 75/75。
这些数字只说明显式分派，未证明所有交互、RNG 或实机等价。

## 离线验证与速度报告

回归和原始测量位于 `battle_engine/out/`，该目录不提交。

```powershell
Set-Location D:\code\aiplayspire
.\battle_engine\build.ps1
conda run --no-capture-output -n spire python -m unittest discover -s battle_engine/tests -t . -v
conda run --no-capture-output -n spire python -m unittest discover -s sts_ai_framework/tests -t .
conda run --no-capture-output -n spire python battle_engine/tools/coverage.py --require-complete
```

最后一条是完整分派与运行时目录一致性门槛，当前返回 0；不代表所有交互或实机通过。
`acceptance.py` 已收紧：未支持角色、缺失运行时卡池、边界跳过及 Java 接管均不算原生通过。

前一轮迁移结果（本批角色增量之前）：

- MSVC Release 原生构建通过；后端 **64** 项测试通过，framework **168** 项通过。
- BattleAiMod **110** 项 Maven 测试通过，0 失败、0 跳过；包含真实快照的离线评分提取和状态键冒烟（不是实机回放）。
- SaveStateMod、LudicrousSpeed 的 `mvn package` 通过；核对生成元数据为 1.6.1／1.2.0／BattleAiMod 1.8.0。
- 16 个历史快照严格验收 **7/16**，退出码 1；4 个静默样本、3 个观者样本因缺失卡牌失败，2 个机器人样本缺少真实卡池。未计作原生通过。
- 项目两份指南一致；没有安装依赖或修改日常游戏目录。

离线速度测量使用原日志中的同一场双虱虫开局快照，固定 seed、15,000 预算计数、2 秒墙钟上限，预热 1 次、测量 5 次。
旧 MCTS 的 simulation 与新搜索的 node expansion 是不同工作单位；此实验比较动作生成延迟，战斗质量另作实机对照。
常驻组每次都重新搜索同一根状态，没有用路径命中冒充搜索性能。

| 处理方式 | p50 | p95 |
|---|---:|---:|
| 迁移前 C++/MCTS，每请求新进程 | 1707.462 ms | 1773.239 ms |
| 回合搜索，每请求新进程 | 93.666 ms | 100.721 ms |
| 回合搜索，常驻进程 | 46.386 ms | 49.002 ms |

上述历史速度实验的候选二进制 SHA-256：`4ac2ebbfb56312b9fec972374e03a19870154820a7d0cb5d14456aecfe85ae43`；不是 2026-11-04 候选。

原始报告 `battle_engine/out/native-benchmark.json` 记录源快照和两个二进制的 SHA-256、所有样本、p50／p95。

```powershell
conda run --no-capture-output -n spire python battle_engine/tools/benchmark_native.py `
  --snapshot 'D:\Program Files\Slay the Spire\startstates\FGZ6WZJ3UVAF\01\0\start.txt' `
  --baseline battle_engine/out/pre-lightspeed-migration/battle-engine-worker-before.exe
```

## 手动实机验收

目前保持实验接入，不切换普通游戏启动的已有后端配置。
本批 BattleAiMod 为 1.10.0、LudicrousSpeed 为 1.3.0。操作者备份并更新隔离测试目录的 JAR 后，按
[角色验收说明](NATIVE_CHARACTER_SUPPORT.md#手动实机验收)显式选择本批 worker；下方旧入口默认使用 `bin/` 中的 worker：

```powershell
Set-Location D:\code\aiplayspire
.\battle_engine\start.ps1
# 另一个窗口；用户手动选择档位。
.\battle_engine\start-game.ps1 -RunSeed CPPBENCH01
```

四角色完整验收前，需要继续完成剩余交互、选择 continuation 白名单审计与实机确认、旧策略完整对等、公共投影 v2、
终局观测边界，并扩充高进阶／后两幕／Heart 快照。之后由操作者执行单步、连续动作和固定种子整场对照，
记录胜负、剩余 HP、药水和耗时；当前不把离线效果测试写成实机通过。
