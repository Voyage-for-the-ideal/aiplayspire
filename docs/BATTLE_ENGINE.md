# 共享战斗内核

Windows BattleAiMod 的 JNI 动态库和 Linux 无头 JSONL worker 均链接同一个 C++ `battle_core`。每个战斗客户端独占搜索会话和缓存。前端负责把真实状态送入搜索、执行并校验动作；没有独立 Java 搜索服务，也没有 TCP 网关。Windows 安装见[三 Mod 说明](../AUTOFIGHT.md)。

## 运行与配置

Linux 在仓库根目录构建 C++17 worker，无需 JNI/JDK 头文件：

```sh
bash battle_engine/build.sh 2
```

产物为 `battle_engine/bin/battle-engine-worker` 和配套的 `.release.json`，原有 `--serve` JSONL 协议保持不变。上述构建命令只生成候选产物，不启动或部署采集；生产预检同时核对内嵌构建类型和 SHA-256。

### Linux 当前采集入口

**服务器只留一套可运行项目。** `current-collector` 指向现有生产采集目录；发布原地更新，不切换入口、不新建并行发布目录、不留旧版备份。保持生产模型、搜索参数和冻结档案原样。历史批次只保留轨迹、配置和报告，不留可启动的项目副本。

执行 `bash /home/ubuntu/headless-spire-poc/tools/run-current-collector.sh --preflight` 只读检查当前入口。验收使用同一项目下的临时 session，验收轨迹归入报告，不混进采集集。发布默认不启动采集或重启只读服务；整局验收结束后收回临时许可。早期验收报告引用的独立运行目录和 `run-collection-acceptance.sh <runtime>` 命令仅为历史记录。

当前配置沿用桌面 `legacy / DEEP` 的搜索行为，按要求将 Linux 预算固定为每次 **15,000 次节点展开**。采集根目录的 `combat-search.json` 中 `timeout_ms=0` 表示没有搜索墙钟截止，Python worker 等待也不设搜索超时；关闭采集闸门或发出停止信号仍可取消。其他游戏驱动的超时不变。已封存的旧批次配置和数据不改写。

| 参数 | Windows JVM 属性 | 原生请求 / Linux agent |
|---|---|---|
| 遍历方式 | `battleai.searchPolicy` | `search_policy` / `--combat-search legacy或layered-v1` |
| 首动作排序（layered） | `battleai.tacticalOrdering` | `tactical_ordering` / `--[no-]tactical-ordering` |
| 回合内复用步数上限 | `battleai.segmentLength` | `segment_length` / `--segment-length 1、2或4` |
| 去重 | `battleai.dedupMode` | `dedup_mode` / `--dedup-mode default或new_turn` |
| 预算 | Mod 内 FAST/BALANCED/DEEP；`battleai.maxExpansions`、`battleai.clientBudgetMs` 可显式覆盖 | `max_expansions`、`timeout_ms`；采集器按角色无关的房间/策略配置传参 |

`legacy` 在此指 **C++ 基线算法**，不是旧 Java 搜索。仓库默认不代表服务器实际配置。两端即使同源，在预算、完整状态、RNG 或墙钟截止时刻不同时，也不保证逐步选择一样。若比较训练质量，固定模型、拆分、预算和种子。

默认单动作提交；不代表每步从头遍历。状态和身份匹配时可命中已有搜索路径。可选分段仅预览本回合可验证的保守后缀，当前限基础打击、防御；每步仍核对真实状态、牌实例、目标和执行结果。出现选牌、召唤、阶段切换、终局等边界会截断；后缀不匹配则丢弃并重新搜索，已经执行的动作失配则停止并留样。新回合去重仅是 legacy 的可选实验：减少构键也可能重复展开同回合不同顺序到达的节点；默认策略未切换。

## 验证命令

```sh
python -m unittest discover -s battle_engine/tests -t .
python -m unittest discover -s sts_ai_framework/tests -t .
```

Maven JNI 契约和隔离的游戏实战见[实机步骤](../AUTOFIGHT.md)。Linux 整局验收单独进行：验证胜、负、异常隔离及下一局继续；检查 episode 验证器的终局证据。正常战败属于有效完整对局；中断、引擎失配或缺失终局不属于有效样本。

历史故障及可复验入口见[防错验收证据](REGRESSION_EVIDENCE.md)。单步回执优先离线回放，只有最终发布验收需要整局。

## 第二阶段：对照实测后再切策略

1. 在实际服务器用 `python -m battle_engine.tools.native_evidence freeze --help` 冻结两个实验臂。提供部署 worker、由对应源码构建的 Windows DLL、模型、真实策略与开关、预算 JSON（MONSTER/ELITE/BOSS 各含 max_expansions、timeout_ms）、运行配置 JSON（机器、CPU 配额、并发、游戏版本、非战斗策略、进阶）。保存输出中的 runtime_id；从服务器取得实际值，不从仓库默认值反推。
2. `python -m battle_engine.tools.a10_corpus <快照目录> --holdout 10 --freeze a10.json`。四角色 × 普通/精英/Boss，每格 3 个调试种子、至少 10 个独立留出种子；既有每格 20 个的清单也支持。只在 pilot 调参。样本不足以证明非劣时增加独立留出集，勿反复调参后继续使用同一留出集。
3. 逐个留出快照以两个冻结配置运行 Windows oracle：传各自 `--battle-jar --search-policy --profile --budget-ms --max-expansions --dedup-mode --segment-length --full-fight` 等参数。每次只改变一个因素。用 `native_evidence assemble` 将真实 `battle-result.json` 合为一臂；检查快照、DLL、配置、样本覆盖。一臂的单点冒烟不是 A/B 证据。
4. 用相同的 Linux 运行配置、预算及种子采集基线与候选，给采集器显式传 `--runtime-id`、同一份 `--budgets-file` 和足够的 `--rounds`（每轮每个指定角色一个独立种子）。保留每个 collection-batch 报告（勿用末次记录覆盖历史），固定并发，按包含失败尝试的总墙钟计算吞吐。
5. `python -m battle_engine.tools.native_gate --manifest a10.json --baseline baseline.json --candidate candidate.json --baseline-batch baseline-batch.json --candidate-batch candidate-batch.json --output decision.json`。胜率差点估计须非负，95% 区间下界至少 −2 个百分点；共同获胜局的失血和用药不得恶化。在未完成率、整局胜率和终局楼层无退步的前提下，每小时有效完整局数还须增加。不自动发布候选。

引擎角色和机制覆盖记录见 [NATIVE_CHARACTER_SUPPORT.md](NATIVE_CHARACTER_SUPPORT.md)；[旧架构报告](BATTLE_ENGINE_HISTORICAL.md)中的端口和命令不再是启动方式。
