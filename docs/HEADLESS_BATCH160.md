# 四角色双进程 160 局

2026-11-18 已单次提交独立服务器任务。两条采集进程各运行 80 局：四角色每个角色各 20 局；总计每角色 40 局、160 局。每个采集进程按需启动自己的 Java 和 native 子进程。

- 运行目录：`/home/ubuntu/headless-spire-poc/runtime-acceptance-batch160-v1`；A10；160 个互不重复的种子为 5050201–5050360。
- 使用星盘返回问题修复后、原种子实局验证通过的版本；冻结 CPU checkpoint 和同一 profile。两路各限 1 核、1 GiB、swap 0；每局最多 3000 个动作。
- 逐动作校验状态、已建模 RNG 和其他受守卫的流；单局出现差异即保留诊断与删失前缀，继续下一局。每路最长 4 小时；supervisor 最长 4 小时 20 分钟，包含结束审计时间。
- 两路结束后自动回放完整轨迹／有效前缀，写入 `reports/batch160-report.md` 与 `reports/batch160-report.json`，记录逐局 CPU／内存及诊断。完成或停止后关闭 scoped 许可。
- 原只读接口、SoulNPC、3681174958 服务及旧全局闸门保持；不接入训练。旧批次和旧报告保留。

提交前 Linux framework **517 项通过**，新增调度／报告目标 **7 项通过**；两条进程线输入一致、固定计划和预检通过。启动时 supervisor PID 896040，lane0 PID 896046、lane1 PID 896047，均为 active；此为启动记录，最终结果以服务器报告为准。

## 结果与首轮修复（2026-11-18）

160 局均已尝试，32 局到达终局，128 局遇到差异或缺失契约后删失；终局不等于胜利。每个角色尝试 40 局，到达终局分别为铁甲 6、静默 12、故障机器人 3、观者 11。两路分别完成 17 和 15 局。所有 160 条终局轨迹或有效删失前缀均通过原版本独立回放，不代表删失局已通过验收。

两路 cgroup 内存峰值分别为 484,433,920 和 488,587,264 字节，swap 峰值均为 0，OOM kill 均为 0；墙钟时间约 32 分 56 秒和 34 分 53 秒，CPU 时间约 1973 和 2090 秒。原始报告和诊断保存在批次目录，未改写。

在独立的 `runtime-acceptance-batch160-repairs-v1` 内构建并做无新对局离线回归，修复四类问题：

1. Knowing Skull 的卡牌选项是直接获得一张稀有度为 UNCOMMON 的无色牌，而不是打开选牌奖励；校验原有牌组、HP、遗物、药水和蛋类升级，兼容旧诊断中的错误描述。
2. 多段攻击击杀带 Curl Up 的虱子时，引擎可保留已经触发但失效的私有标记；只在血量为零时接受该特定标记，活体异常继续拦截。
3. Mantra 的 `PRAYER_REQUIRED` 是固定常量 10；保留常量校验，实际层数、存在性和姿态继续由原生状态键校验。
4. 原生红衣奴隶模拟导入并更新 `usedEntangle`，修正已用缠绕后的随机分支边界 50→55，使用精确 RNG 状态测试 49、50、54、55。
5. Java Pen Nib 计数为 9 时表示已蓄力，而 native 内部使用 -1 哨兵；导入时验证计数和对应能力，再转换表示，正确覆盖 8→9 和 9→0。

`deploy/headless/verify-batch160-repairs.py` 对原报告 SHA-256 绑定的诊断进行离线复核：5 个 Knowing Skull 动作、5 个死亡 Curl Up 动作、3 个 Mantra 动作、4 个红衣奴隶动作和 9 个 Pen Nib 动作，共 26 个原始触发点通过新检查。21 个战斗动作均对照新 worker 的预测、原记录的落点、原生状态键和已建模 RNG，并检查篡改 RNG 会被拒绝；5 个事件动作验证效果，但未对事件 RNG 做独立预测。它们只是触发点回归，不是重跑 26 局。其余至少 102 个原始中止点尚待逐项处理；这些局即使修复原触发点，后续也可能出现新的问题，整体验收仍未通过。

服务器 Linux native 测试 267 项、framework 测试 521 项通过；Java 交互 fixture 无开局、无事件动作，200 次读取前后 RNG 不变。修复审计为 `runtime-acceptance-batch160-repairs-v1/reports/repair-audit.json`。原只读接口、SoulNPC 和 3681174958 服务、全局采集闸门及原批次冻结输入通过只读保全检查；未启动新对局、Agent 或训练。

## 剩余 102 点：冻结后签收与整批重跑

签收目录是独立的 `runtime-acceptance-batch160-repairs-v2`，原 160 局与原 26 点审计均只读。修复台账的 `recorded_step_matches` 仅表示新预测与原动作后观测一致；`original_stop_crossed` 表示原种子、原动作前缀和完整玩法 RNG 已匹配，且实局越过原断点。两者都不是游戏终局。

- `sts_ai_framework.headless.repair_ledger` 检查原报告/诊断的 SHA-256 和身份；对有后快照的动作重新预测。没有后快照的行保持待证。
- `run-repair-pilot.sh` 只在独立的 pilot 目录创建两枚临时许可。一次处理分配给它的种子，单局失败保留最后已核验的前缀、真实落点或错误。外层 systemd 单元负责硬资源限制和退出时关闭许可。
- `run-repair-signoff.sh BUILD_TAG --submit-after-pass` 是冻结版本的单次入口。先跑全套 framework/native、此前 26 点以及 73 个快照，再将 29 个无后快照案例、4 个停在奖励/选牌界面的案例和 4 个需引擎发奖记录补证的事件拆成两路原种子实局。分配按原始报告自动产生。两个进程退出后，单独读取原轨迹与新轨迹，核实命令、UUID 双射、14 路 RNG、下一正常决策、checkpoint/profile 和执行文件身份。缺一项就停在签收阶段。每个 BUILD_TAG 仅能使用一次。
- 只有取得 102 个逐案证据后，才会创建 `runtime-acceptance-batch160-v2`，冻结到两个 lane，并提交 `headless-batch160-r2-supervisor`。两个新 lane 使用原种子和 A10，各角色各 20 局，单 lane 1 核、1 GiB、无 swap。临时许可只在各 lane 运行期间存在。没有训练入口。
- 重跑完毕后，读取新批下的 `reports/repair160-comparison.md` 与 JSON：独立列出旧触发点、真正到达终局的对局、重复差异、新差异和证据不足。只重现相同的错误文本或种子，不等同于重复原差异。

上述属于运行规约。实际是否已经放行，以签收 JSON 的 `passed` 和新批的 `SUBMITTED` 为准；不要把开发中间版本的通过数相加作为最终签收。

## release1 剩余 63 个中止点

release1 的逐局报告记录了 97 个终局和 63 个中止。第二轮用独立的
`runtime-acceptance-batch160-repairs-v3`，不覆盖两次原批次。验收先将逐局报告的
SHA-256 与两路结果、诊断及原轨迹链绑定，再按原种子重放每个已通过的动作。
优先验证三个实为 Seek+ 的选牌循环，再验证结束回合、出牌及事件落点。
重放期间逐个匹配真实决策位置、动作参数、牌 UUID 对应关系和 14 路玩法 RNG；
越过原断点后，独立重验新轨迹。失败的单局保留前缀和诊断，其余案例照常执行。

单次门控入口为 `bash tools/run-release2-signoff.sh --submit`。签收产出在候选的
`reports/release2-signoff.json`。仅 63 点全部通过时写入冻结指纹，并在同一版本
上重验此前 102 点和 26 点及完整测试套件。全部通过后才创建
`runtime-acceptance-batch160-v3`，由 `headless-batch160-r3-supervisor` 提交原
160 个 A10 种子的双进程任务。验收任务已启动并不表示整批已提交；后者以新批
`SUBMITTED` 为准。原只读接口、账号 3681174958 服务和全局闸门不变，输出不接入训练。

首次 63 点签收留下 56 个通过记录。第 0 路在种子 `5050272` 已抵达
`GAME_OVER` 后，因把 `TerminalReceipt` 对象直接交给 JSON 写入器而提前退出。
修复版以字典保存凭证，并将单局序列化错误落为未签收诊断，继续其余案例。

按更新后的重跑范围，使用独立候选 `runtime-acceptance-batch160-repairs-v3-unfinished`。
签收和旧回归仍是提交条件，但正式批次只重跑 release1 报告中的 63 个中止种子
（原第 0 路 30 局、第 1 路 33 局）；已到终局的 97 局不重跑。正式批次目录为
`runtime-acceptance-batch160-v3-unfinished`，运行结束后生成 `reports/unfinished63-report.json`
及 Markdown 报告。两路计划使用同一冻结种子清单；报告也逐路核对该清单。

### 63 局重跑的结果与后续修复

正式重跑 63/63 局，第 0 路 9 局终局、21 局校验中止；第 1 路 10 局终局、
23 局校验中止。19 个终局均为游戏内败北。63 条完整轨迹或有效前缀全部独立回放，
报告校验错误为 0；保全审计通过。

44 个中止点的根因：Chosen 施加 Hex 时只置存在位、未存数值（30）；
Headbutt 打蛇花打开选牌时，排队的 Malleable 格挡未导出（2）；Transient 固定更新意图
误消耗 AI RNG（2）；Hello World 误用源牌池而非实际抽样牌池（1）；
Giant Head 的 Slow 在零层时被误当成不存在（1）；竞技场第一波胜利回到对话而不是
战利品界面（2）；Falling 删牌没绑定实际选定 UUID（2）；Winding Halls 和
Mysterious Sphere 缺少分步对话确认（各 1）；Sensory Stone 的多组无色牌奖励和
RNG 没有单独验证（2）。

`deploy/headless/verify-unfinished63-stops.py` 按 63 局报告的 SHA-256 核对全部 44 条
差异诊断。修复后二次校验：12 个有原后快照的断点均通过，并拒绝额外的 RNG 状态改动；
32 个没有原后快照的断点均能生成有效计划。计划可用不等于实局通过；
`--pilot-root ... --seeds ...` 另外启用隔离的原种子、原动作前缀验证。

服务器隔离环境 `runtime-acceptance-batch160-repairs-r4-targeted` 通过框架
548 项和原生 315 项测试。实局验证 6 个原断点：两局 Headbutt、两局 Chosen、
Hello World 和竞技场第一波结算，均按原种子及原前缀匹配实际落点和 14 路玩法 RNG。
此前 26 点与 102 点中的 73 个有快照断点完成离线回归；102 点的实局部分未重新签收。
未提交新的整批重跑。

### 后续两阶段 A10 批次

`runtime-acceptance-batch63-r4-followup` 固定使用已验证的 r4 游戏构建、原 checkpoint
与 profile。第一阶段 `recheck44` 只重跑上报的 44 个中止种子（原第 0 路 21 局、
第 1 路 23 局）；完成两路并独立回放后，第二阶段 `fresh40` 使用 5050401–5050440，
四角色各 10 局，每路各角色 5 局。两阶段都是进阶 10、每局最多 3000 个动作。
单局遇到新差异时保留前缀与诊断；第一阶段的正常差异不拦截第二阶段，运行或审计错误则停止放行。
报告分别为 `recheck44/reports/recheck44-report.json` 与 `fresh40/reports/fresh40-report.json`。

44 局原种子重跑留下 17 个校验中止点，40 局新种子留下 12 个。后续的
`runtime-acceptance-batch-r5-candidate` 绑定这两份报告的 SHA-256 和原始诊断；
跨批次用来源名区分重复的对局编号。两路先逐局重放原动作前缀，再由独立验证检查
牌 UUID 对应关系、实际落点与 14 路玩法 RNG。只有 `reports/signoff29.json`
确认 29/29 通过时，才将同一构建冻结至 `runtime-acceptance-batch-r5-retry29`，
用原种子和原角色双路重跑（15 局、14 局）。保持 A10、原 checkpoint、选路和
FAST 搜索配置；单局差异留诊断，不接入训练。

提交前 r5 服务器通过 553 项框架测试、325 项原生测试，以及 6 个同一构建的
原种子实局探针（5050203、5050224、5050243、5050300、5050405、5050406）。
服务器服务为 `headless-batch-r5-followup29.service`；服务启动只表示进入逐案签收，
正式双路批次以 `runtime-acceptance-batch-r5-retry29/SUBMITTED` 为准。

### 29 局签收、重跑结果与后续校验

r5 签收 29/29。正式双路重跑也执行了 29/29：第 0 路 9 局到终局、6 局中止，
第 1 路 7 局到终局、7 局中止。16 个终局均为游戏内败北。29 条轨迹或前缀的
独立回放无报告错误，两路临时许可均已关闭。

修复的 13 个差异按根因分为：

- Nemesis（3）：灼伤数量按 A18 分界，镰刀冷却进入原生状态并参与精确意图/RNG 推演。
- 扭曲团块（3）：Wither 是有伤害的攻击意图；Implant 将寄生写入永久牌组，
  当御守仍有次数时消耗一次并挡下诅咒。
- 战斗模拟（4）：Snecko 的困惑后于 Eviscerate 的抽牌减费，Chaos 使用原版球池顺序，
  Master Reality 保留不叠层的 `-1` 标记，Dual Wield 替换所选牌时移除旧实例的计数。
- 界面校验（3）：Mind Bloom 的开战状态计入水银沙漏，Survivor 可被 Burst 重放并再开弃牌框，
  Warcry 放到牌堆顶的牌可被紧随其后的 Dark Embrace 原样抽回。

当时的离线复验用冻结的原始动作和 Java 前后快照重验 13 个断点，
逐动作校验归档前缀，并确认篡改玩法 RNG 后会被拒绝。

服务器隔离构建 `runtime-acceptance-batch-r6-retry29-fixes` 的
`reports/retry29-regression.json` 记录 13/13 个断点及 6,254 条归档前缀动作全部通过；
13/13 个断点均拒绝篡改后的 RNG。该结果是对原始 Java 观测的离线复验，
不是新启动的整局跑分。

`run-retry13-fresh80.sh --submit` 在独立目录
`runtime-acceptance-batch-r6-retry13-fresh80` 冻结同一修复版和两份 A10 种子表。
先按原角色和原种子双路重跑 13 局（6/7）；报告校验通过后，启动
80 个互不重复的新随机种子，每路 40 局、每角色 10 局，两路合计每角色 20 局。
两阶段各有 `SUBMITTED` 和独立报告；单局玩法差异留诊断，运行、回放或保全审计错误
拦截下一阶段。重复提交保留已排的同一任务和种子表。

### r6 双批次结果与 r7 修复

两阶段共执行 93 局 A10，全部留下独立回放通过的轨迹或前缀。13 局重跑中，
第 0 路 4 局终局、2 局中止，第 1 路 5 局终局、2 局中止。随机 80 局中，
第 0 路 28 局终局、12 局中止，第 1 路 31 局终局、9 局中止；铁甲战士、
静默猎手、故障机器人、观者分别有 13、15、16、15 局到终局。68 个终局均为败北。

25 个新断点来自战斗顺序与卡牌状态、事件/选牌校验，以及开局空鸟笼选牌驱动。
战斗修复覆盖 Reboot 洗牌、随机生成牌的 X 费/临时费用、无实体时零伤、
Streamline 重放减费、Ethereal 与抽牌触发顺序、Phantasmal、True Grit 和遗物状态。
开局空鸟笼把最后两张选择交给原牌组移除逻辑，再回到涅奥菜单。

隔离构建 `runtime-acceptance-batch-r7-r6-differences` 对 22 个有后快照的断点
逐条匹配原动作、实际落点和 14 路玩法 RNG，并逐案拒绝篡改后的 RNG。
无后快照的 3 个断点（Empty Cage、Purity、Necronomicon）在原种子、原动作前缀上
实局越过断点，随后再次独立回放。报告分别为 `reports/recorded-v1.json` 和
`reports/no-after-pilots.json`。

下一批使用 r7 原生 worker 和 Java 类，按 r6 两份报告的 SHA-256 绑定原 25 个中止种子：
第 0 路 14 局，第 1 路 11 局。首阶段出报告并通过回放、保全校验后，双路启动
新的随机 80 局，每路每角色 10 局，避开已跑的两批种子。三条实局探针和 22 条
快照复验的凭证参与冻结；整个批次保持 A10、现有 checkpoint 和策略、仅评估。

部署时复制 r7 候选的 `repo` 到独立的 `runtime-acceptance-batch-r7-queue`，
当时只覆盖两个调度模块（现位于 `sts_ai_framework/headless/`），
把调度脚本安装为该目录下的 `tools/run-retry25-fresh80.sh`。运行
`bash tools/run-retry25-fresh80.sh --submit`；服务器任务名为
`headless-batch-r7-retry25-fresh80.service`。相同任务已提交时跳过重复提交。

### r7 结果与 r8 定点修复

r7 于 2026-11-19 13:46:33（北京时间）开始，14:46:48 正常退出。两阶段各路退出码
均为 0，未发生 OOM，许可已收回；105 条完整轨迹或有效前缀全部独立回放通过。

| 阶段 | 执行 | 游戏终局 | 校验中止 |
|---|---:|---:|---:|
| retry25 | 25 | 18（均败北） | 7 |
| fresh80 | 80 | 71（1 胜、70 败北） | 9 |

唯一胜局为故障机器人种子 `1523028348150212`，51 层，未击败心脏。

本轮修复包括：统一等待消耗牌的临时费用重置；绑定 Headbutt 打开选牌时未结算的手里剑
力量、蒸馏混沌未打出的队列及全知的抽牌堆选项；修正 Conclude 与双发的重放限制、
双盗贼攻击次数、尖塔生长体意图、贪婪之手触发飞行减层与蛇女死亡后匕首退场；
补充秘密传送门、蘑菇、神秘球体、匕首投掷与战吼的原版动作回执校验。

服务器隔离目录 `runtime-acceptance-batch-r8-targeted.GUTma38z` 中，
`reports/archived-v5.json` 记录 13 个有后快照的断点全部匹配原动作与结果，并拒绝修改后的
`misc` RNG。`reports/pilots-v5.json` 记录另外 3 个断点（Headbutt、蒸馏混沌、全知）
均按原种子和原动作前缀越过断点，同时验证了秘密传送门从确认对话进入 Boss 战。
4 条探针轨迹均通过独立回放；555 项框架测试（含旧档案）、330 项原生测试和服务器
保全审计通过。没有重新提交整批评估。

### r8 两阶段 A10 评估

按 r7 的 16 个中止局原种子、角色和分路重跑（第 0 路 9 局，第 1 路 7 局）。
同一 r8 构建、原 checkpoint、原选路和 FAST 搜索，每局最多 3000 个动作。两路正常退出，
16 条完整轨迹或有效前缀各自回放通过、保全无差异后，才启动 80 局不重复的新随机种子，
每路各角色 10 局。阶段之间不会复用存档、轨迹或报告。

服务名 `headless-batch-r8-retry16-fresh80.service`。服务器在独立的
`runtime-acceptance-batch-r8-queue` 中从 r8 候选复制 `repo`，只叠加当时的调度模块。
将通用调度脚本安装为
`tools/run-retry16-fresh80.sh` 后以 `bash tools/run-retry16-fresh80.sh --submit` 提交。
新结果位于 `runtime-acceptance-batch-r8-retry16-fresh80` 下的
`retry16/reports/retry16-report.json` 和 `fresh80/reports/fresh80-report.json`。

`retry16` 完成 16/16：12 局到终局（2 胜、10 败），4 局到更后楼层后中止；
16 条轨迹或前缀、8,311 个动作独立回放通过。之后放行 `fresh80`。

本轮 4 个新断点的修复：

- Fiend Fire 消耗最后一张手牌仍推进 `card_random`；不影响真正只剩一张时直接消耗的 True Grit。
- Magnetism 在回合开始从实际无色牌池生成卡，并推进同一玩法 RNG。
- 发现牌奖励结算同时绑定已打出 Discovery 的清理 UUID、选中的新卡 UUID、落点与后续候选重抽。
- Secret Weapon / Secret Technique 使用按类型过滤、按原版 RNG 插入顺序排列的抽牌堆选项；
  恢复空手牌的选牌快照时，不提前触发 Unceasing Top。

服务器隔离构建 `runtime-acceptance-batch-r9-targeted.N71jQIQk` 中，
`reports/recorded-v3.json` 记录 3 个有后快照的断点匹配且均拒绝篡改后的 RNG。
`reports/secret-weapon-pilot-v2.json` 记录故障机器人原种子的 716 步前缀、进入选牌、
选取实际牌实例并回到战斗；这条 718 步轨迹通过独立回放。560 项框架测试和 333 项
原生测试通过。只验证了上述断点，未提交新批次。

### r8 fresh80 结果与 r10 定点修复

`fresh80` 于 2026-11-19 17:11:36（北京时间）结束。双路均退出 0，批次保全通过。
80 局中 66 局到终局（全为败北），14 局因动作差异中止。80 条完整轨迹或有效前缀
共 31,270 个动作，全部通过独立回放。中止动作本身不在已回放前缀中。

r10 修复这些断点：

- Headbutt 选牌保留待结算的苦无敏捷，按遗物顺序恢复苦无与手里剑；识别史莱姆老大已经生效的分裂意图。
- 观星校验区分同一选牌界面和打出副本后新开的界面，校验牌实例、目标、牌堆与 RNG；
  涉及复制药水、全知与《命运之刃》。幸存者的第二次弃牌同时计入爆发、敏捷和余像。
- 修正洗牌时遗物触发顺序、Fission 抽牌和 Chill 充能与 Hex 的队列顺序，以及炸弹触发守护者变形时的格挡顺序。
- 修正三项事件结果：再会时药水腰带增加两个空槽，净化移除寄生时扣除最大生命，
  红面具墓中 Ectoplasm 阻止金币到账。

服务器隔离目录
`/home/ubuntu/headless-spire-poc/runtime-acceptance-batch-r10-targeted.mWvQks8J`
下的 `reports/recorded-final-v2.json` 记录 10 个冻结后快照全部匹配，且拒绝篡改后的
玩法 RNG；另外 4 个原诊断只有前快照。14 个原种子均按原角色、A10 和原动作前缀复跑，
通过各自断点并到达下一次可用决策，最终 worker 独立回放了 4,030 个动作，见
`reports/fresh80-r10-validation.json`。561 项框架、336 项原生测试，
以及四角色 Java 到原生的合成契约校验通过。旧报告与新候选分开保存，未启动新一批评估。

工作区已移除各历史批次的一次性复验入口，服务器归档不变。当前工作区可用回归测试
读取 r8 的原始归档，无需启动实局：

```bash
HEADLESS_R8_FRESH80_SOURCE=/home/ubuntu/headless-spire-poc/runtime-acceptance-batch-r8-retry16-fresh80 \
BATTLE_ENGINE_WORKER=/home/ubuntu/headless-spire-poc/runtime-acceptance-batch-r10-targeted.mWvQks8J/native-bin/battle-engine-worker \
python -m unittest battle_engine.tests.test_headless_r8_fresh80
```
