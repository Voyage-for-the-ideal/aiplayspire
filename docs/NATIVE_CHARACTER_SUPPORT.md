# 静默／观者原生战斗支持：增量交付

更新：2026-11-04。本批补齐原版 150 张牌的显式分派，并接通一批真实选择快照的独立恢复；完整计划仍有后续验收项。

## 当前能力与边界

| 项目 | 当前状态 |
|---|---|
| 静默／观者原版牌 | 各 75/75 有显式分派，运行时支持目录一致 |
| 源码审计／冒烟 | 150 张源码清单；未升级／升级共 300 个出牌或抽牌触发冒烟子例 |
| 原生选择模拟及快照恢复 | 弃牌、预见、保留、Setup、Meditate、Nightmare、Omniscience、Foreign Influence、Wish |
| typed continuation | 从 SaveState JSON 重建白名单中的剩余队列；未知动作在发出命令前报错 |
| 公共投影 | 仍为 v1；另加 `expected_screen` 与执行前选择上下文 guard，v2 待实现 |
| Java 对照 | 原有策略测试保留；新增 Foreign Influence 双 RNG golden；完整评分／排序 golden 待补齐 |
| 实机逐动作／整场对照 | 本批未启动游戏，由操作者手动验收 |

**分派、效果断言、真实引擎回放是三道独立门槛。** 300 个冒烟子例检查准入、合法执行与升级路径，不等于每张牌的全部交互对等。源码报告继续保留 `real_engine_checked=false`。

## 2026-11-04 实现

### 最后 7 张牌

- Escape Plan：只在实际抽到技能时获得 3/5 格挡；No Draw、空牌堆不复用上次抽牌类型。
- Nightmare（内部 ID `Night Terror`）：选定目标、叠层保留首个目标、下回合复制；恢复能力私有 `card`。
- Deva Form：恢复私有 `energy_gain_amount`，按原版处理每回合增长与叠加；缺私有状态时报明确信息。
- Omniscience：抽牌堆检索、自动施放两次、原牌消耗一次、复制牌 purge；保留嵌套选择所需队列。
- Wish：三种战斗选项及升级效果，强制选一；可跳过的战斗奖励用 `CARD_REWARD_SELECT card_index=-1`，Wish 不接受跳过。
- Lesson Learned：只升级真实 `master_deck` 中的合格牌，非致命、Minion 和半死目标不产生收益；不升级战斗副本。搜索按相对根状态的永久升级增量奖励资源分，既有升级不重复得分。
- Foreign Influence：Java 导出已解锁的真实 CardLibrary 各稀有度攻击池；分别推进 `cardRandomRng` 与独立 `cardRng`，保留 shuffle RNG 消耗、ID 排序、重复排除和升级牌的本回合零费。缺真实池或独立 RNG 计数时停止该原生动作。

### 选择恢复与逐动作执行

新 `SelectionSnapshot.cpp` 从类型化 SaveState JSON 恢复剩余队列，不依赖上一个 worker 留下的函数对象。按真实 UUID 映射 HAND、GRID、战斗奖励的选项和已选牌；覆盖部分已选、多选、零选和确认，每次只提交一个 UI 动作。

- current action 白名单：`DiscardActionState`、`ScryActionState`、`RetainCardsActionState`、`SetupActionState`、`MeditateActionState`、`NightmareActionState`、`OmniscienceActionState`、`ForeignInfluenceActionState`、Wish 对应的 `ChooseOneActionState`。
- 剩余队列白名单包含卡牌收尾、抽弃、能量／格挡、姿态、常用能力、直接伤害、生成牌、排队出牌及部分回合边界动作；完整列表见 `Reader::action`。未知可执行动作在发出命令前报错，limbo 缺收尾动作也报错。
- Java 导出 `fast_hand_confirm`；导入的手选按真实设置决定是否需要单独 `HAND_SELECT_CONFIRM`，不是一律选满即关闭。
- 卡牌队列保留 10 槽热路径，超出后扩展，支持搜索副本复制。恢复的 NewQueueCardAction 按原版插入第 2 项，并对相同卡牌去重。
- 选择界面关闭路径复用；普通决策点继续验证完整原生状态、动态字段和 UUID。
- Java／Python／C++ 执行前 context 加入当前选择、已选牌、limbo 和类型化剩余队列；网关及验收工具同时核对 v1 投影和预测界面。

动作后的全部能力、动态属性、永久收益和终局仍需 v2 统一投影。未识别的队列动作、遗物／药水选择和旧式 selector 继续明确列为未支持，不猜测剩余效果。

### 版本与工作区

- BattleAiMod **1.10.0**：真实 CardLibrary 池、独立 RNG、选择上下文 guard。
- LudicrousSpeed **1.3.0**：可选战斗奖励跳过；保留既有命令格式。
- 两者完成 `mvn package`，核对生成的 `ModTheSpire.json`，部署到仓库配置的 mod 输出位置。没有更新日常游戏目录的 JAR、配置或源存档。
- 没有添加依赖；保留工作区原有改动。两份项目指南仍一致。

## 前批基础（2026-11-03）

- 原生 CharacterStrategy：静默／观者优先级、完整 SilentPlayOrder 回退表、跨色卡／无色卡路由，以及弃牌、消耗、预见、检索、保留的不同目标。排序引导搜索，不删减合法动作。
- 具名机制评分：毒的近两次伤害、小刀／抽弃引擎、未来无实体、幽魂形态负敏捷、平静退出、真言、保留和姿态引擎；`evaluate` 返回独立 `mechanism_rules`。
- Alpha→Beta→Omega、Conjure Blade→Expunger；毒量溢出、零毒催化、Snecko Skull、爆发催化、多段 Vigor、保留成长／Establishment 等回归。
- Vault 的时间吞噬者第 12 张牌、无实体及诸神黄昏边界用例。
- 删除固定 50 动作截断，以路径状态去重及三档节点／墙钟预算约束。旧 Java 搜索与 MCTS 对照入口保留。`step`／`sequence` 是离线入口，和实机回放分开。

## 研究依据

策略基线是本仓库 Java 的 CharacterStrategies、CommandOrdering、SilentPlayOrder；机制以只读 `cardcrawl/` 原版源码为准。

- [静默：毒、防守、小刀和抽弃协作](https://www.reddit.com/r/slaythespire/comments/1czuzoc/tips_for_playing_silent/)
- [观者：姿态切换、退出愤怒与回合规划](https://www.reddit.com/r/slaythespire/comments/16lx8f1/tips_for_playing_as_watcher/)
- [Vault 作为时间吞噬者第 12 张牌](https://www.reddit.com/r/slaythespire/comments/eshdm4/)

论坛意见转成场景与反例，不把构筑建议混入战斗动作排序；伤害、合法姿态退出、时间吞噬者、心脏、反伤和净化由模拟决定。

## 构建与离线验证

候选独立构建，保留原默认 worker 和之前的候选，不停止现有进程。

```powershell
Set-Location D:\code\aiplayspire
& .\battle_engine\build.ps1 -OutputDirectory D:\code\aiplayspire\battle_engine\out\character-support-20261104
$env:BATTLE_ENGINE_WORKER = 'D:\code\aiplayspire\battle_engine\out\character-support-20261104\battle-engine-worker.exe'
conda run --no-capture-output -n spire python -m unittest discover -s battle_engine/tests -t . -v
conda run --no-capture-output -n spire python -m unittest discover -s sts_ai_framework/tests -t .
conda run --no-capture-output -n spire python battle_engine/tools/coverage.py --require-complete --output battle_engine/out/character-source-audit-20261104.json
mvn -f LudicrousSpeed/pom.xml package
mvn -f scumthespire/pom.xml `
  '-Dbattleai.nativeWorker=D:/code/aiplayspire/battle_engine/out/character-support-20261104/battle-engine-worker.exe' `
  '-Dbattleai.realStateManifest=D:/code/aiplayspire/debug/character-strategies/comparison/manifest.json' `
  '-DstateFile=D:/Program Files/Slay the Spire/startstates/119TLMC49V0AB/01/start.txt' `
  '-DstateWarmup=10' '-DstateIterations=10' '-DstateSamples=1' package
```

本批结果：
- MSVC Release 构建通过，候选在 `battle_engine/out/character-support-20261104/`。
- 后端 **177 项**通过，含 300 个逐卡／升级冒烟子例；framework **168 项**通过。
- BattleAiMod **112 项** Maven 测试通过，0 失败、0 跳过；含双 RNG golden、16 份历史快照评分及状态键冒烟。LudicrousSpeed 打包通过。
- 严格分派门槛退出码 **0**：静默／观者各 75/75，运行时目录一致，无“目录已放行但没有分派”的条目。
- 固定历史清单 **14/16** 完成导入及单步命令生成（静默 4/4、观者 4/4）；两份机器人样本缺真实运行时卡池，单列失败，验收总命令退出码 **1**。
- 实机回放未执行，`real_engine_checked=false`；没有把单步生成、Java 接管或边界跳过计作实机通过。

日志／报告在忽略的 `battle_engine/out/`：`test-character-support-final-20261104.log`、`test-framework-20261104.log`、`maven-battleai-full-20261104.log`、`maven-ludicrous-20261104.log`、`character-history-acceptance-20261104.json`、`character-source-audit-20261104.json`。

前批结果保留：2026-11-03 为静默 73/75、观者 70/75，后端 147 项、framework 168 项通过，历史导入 14/16；当时真实选择恢复尚未接通。

## 手动实机验收

先备份并准备匹配 mod 的隔离游戏实例。游戏和 Java 回放端由操作者启动；以下命令本批仅记录，不自动执行。

逐动作对照（回放端口 15125，固定哈希清单）：

```powershell
Set-Location D:\code\aiplayspire
conda run --no-capture-output -n spire python battle_engine/tools/acceptance.py `
  --worker D:\code\aiplayspire\battle_engine\out\character-support-20261104\battle-engine-worker.exe `
  --manifest D:\code\aiplayspire\debug\character-strategies\comparison\manifest.json `
  --oracle-port 15125 --steps 10 `
  --output battle_engine/out/character-live-acceptance-20261104.json
```

整场网关显式选择本批二进制，默认不启用 Java 回退：

```powershell
conda run --no-capture-output -n spire python -m battle_engine.server `
  --worker D:\code\aiplayspire\battle_engine\out\character-support-20261104\battle-engine-worker.exe `
  --port 5125 --no-fallback --log-dir battle_engine/logs/character-20261104
# 另一个窗口，GameDir 指向操作者准备好的测试实例。
.\battle_engine\start-game.ps1 -GameDir <测试游戏目录> -RunSeed CPPBENCH01
```

固定验收清单：
1. HAND 逐次点击、非快速确认、快速单选、部分已选、零选；核对 UUID、能量、抽弃触发次数。
2. Scry／Meditate／Omniscience GRID；选满关闭、零选、Weave／Nirvana、嵌套 Omniscience。
3. Wish 强制选一；Foreign Influence 普通／升级／跳过；核对两个 RNG 计数及真实选项。
4. Nightmare 私有目标、叠层、满手牌；Deva 私有增长；Lesson Learned 永久升级及 Minion 反例。
5. Vault 第 12 张牌、心脏出牌伤害／上限、反伤、Artifact；胜利和死亡收尾单列。
6. 超过 50 动作连段、超过 10 张待施放队列、三档超时；状态变更后确认缓存失效。
7. 同种子整场配对，分别记录角色、档位、胜负、剩余 HP、药水、永久收益及耗时。

缺卡池、未知队列、边界未投影及回放偏差分别记录，和整场胜负分开统计。后续优先补齐公共投影 v2、队列白名单／私有状态审计、完整效果与策略 golden，再完成实机验收；分派达到 150 张不作为完整计划完成标记。
