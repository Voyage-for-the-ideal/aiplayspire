# Headless 四角色多种子验收

> 后续用户将执行计划调整为[一次提交的 24 局独立双进程验收](D:/code/aiplayspire/docs/HEADLESS_BATCH24.md)。本目录旧批次停在 7 条终局，历史轨迹及诊断保留，未完成的 40 局计划不再自动推进。

本轮固定计划为 **四角色各 10 局，共 40 局 A10**，与[上一轮每角色一局的基础验收](D:/code/aiplayspire/docs/HEADLESS_RUNTIME_ACCEPTANCE.md)分别计数。当前进度以服务器隔离目录的 `reports/multiseed-status.json` 为准；整批验收要求 40 条真实终局轨迹全部通过最终版本的独立回放。

## 固定计划与隔离

- 隔离根目录：`/home/ubuntu/headless-spire-poc/runtime-acceptance-multiseed-v1`。上一轮 `runtime-acceptance-v3` 原样保留。
- 轮转顺序：IRONCLAD、THE_SILENT、DEFECT、WATCHER。每轮四个不同种子，种子范围 **5050101–5050140**，每角色 10 个。失败修复后重跑同一种子；中止尝试独立留档，不计入完成数。
- 每局复制同一份冻结的 39 文件 profile；不通过编辑解锁状态、重选种子或重置随机流绕过差异。
- 游戏保持单路；代码审查和本地合成回归可并行。临时 `headless-multiseed-acceptance.service` 配额为 1 核、1536 MiB、零 swap，每次 invocation 最长两小时。Java 堆上限 512 MiB，Serial GC，无 DISPLAY。
- 战斗使用现有 native FAST 搜索；非战斗使用冻结的 format-12 CPU 价值网络和现有地图路由。checkpoint SHA-256：`73d092fe8330d916e662bac5e8999eb251b34404085d171abe92389bbeacf0f6`。
- 原 `:5000/state` 服务保持 BOOT／MAIN_MENU 只读，动作端点保持关闭；SoulNPC 和 3681174958 的 Napcat 不参与本批次，也不重启。两道旧全局采集闸门保持关闭，实局仅使用隔离目录内的有限验收许可。
- 数据标记 `headless-native-v1`、`legacy_training_admission=false`，不接入训练。

## 运行与恢复

入口为[串行 suite](D:/code/aiplayspire/sts_ai_framework/headless/suite.py)和[有界启动脚本](D:/code/aiplayspire/deploy/headless/run-multiseed-acceptance.sh)：

```bash
W=/home/ubuntu/headless-spire-poc/runtime-acceptance-multiseed-v1
bash "$W/tools/run-multiseed-acceptance.sh" "$W" --preflight
bash "$W/tools/run-multiseed-acceptance.sh" "$W" --run --retry-failed
```

`--retry-failed` 用于完成诊断和修复后的显式恢复；固定计划保持一致，已完成项跳过。每次尝试在启动引擎前分配 UUID，并原子写入及 fsync `running` 记录。启动、开局请求或动作阶段的异常均保留独立诊断；恢复时悬空记录按删失处理。

每局最多 3000 个动作，遇到未验证生效、状态／RNG 差异、未建模私有状态或循环立即结束该局并停止批次。只有经过合法动作与生效检查的完整观察前缀写入轨迹。失败文件为 `.aborted.jsonl.gz`，不伪造胜负标签。

## 本轮已补齐的契约

- **GoldenWing**：金币描述为包含两端的 50–80 均匀分布，不读取动作前尚未生成的 `goldAmount`，导出不消耗 RNG。支付 HP、确认删牌、返回地图分别按原生阶段验证。
- **事件分步**：Cleric、WomanInBlue、Nest、Sssserpent、Beggar、Mushrooms、FaceTrader、Designer、各神龛等使用明确的类／阶段／按钮转移，不把按钮文字变化当成执行证据。
- **GremlinWheel**：开始、按转盘、显示结果、领取奖励分开；完成前隐藏结果。金币只在源预结算阶段获得一次，其他奖励在领取阶段验证，原生随机调用保持原样。
- **BackToBasics**：升级按真实 STARTER 标签导出的卡牌 ID 集合匹配，支持四角色与显式空集合；保留 UUID、非目标卡牌和升级数量检查。删牌用途根据固定上游重载推导，不更改原生 flags。
- **WeMeetAgain**：显式绑定药水槽、金币或卡牌 UUID 的成本，随机遗物只在生成后读取；专用回执核对奖励及非目标库存，现有 V(S) 多候选分发保持。
- **怪物状态**：地精重复行动历史／RNG、Wizard 充能、死史莱姆分裂标志、Parasite 重掷、BookOfStabbing 多刺计数、Taskmaster 历史和 Champ 独立选招计数按源映射。细节见 [vendored 修改记录](D:/code/aiplayspire/third_party/sts_lightspeed/VENDORED.md)。
- **观者跨回合连牌**：从真实整场出牌历史恢复前一张卡类型，在 Headless 回合边界保留；FollowUp 等连牌按源触发，旧模式保持原样。
- **CentennialPuzzle**：显式只读导出真实私有静态触发状态，恢复原生一次性 availability；不从受伤次数猜测。
- **玩家 Ritual**：精确保留源私有标记，消除内部超宽移位；按源码在首次及后续回合末增加力量，不把怪物的首轮跳过逻辑套到玩家身上。
- **战斗选牌**：Gambler 的 HAND_SELECT 与 LiquidMemories 的 GRID 使用各自明确的源动作契约。核对全部候选／已选 UUID、四堆身份、完整 native guard 及八条 RNG；前者逐张移入选择区并确认弃牌／抽牌，后者在弃牌堆保留引用、选满自动回手并按源调整当回合费用。已在铁甲第二个种子的实局前缀中验证两条路径。
- **伤害与费用边界**：Combust 自损量取真实私有字段；Angry 只对实际正普通伤害增加力量；下一玩家决策边界按源刷新消耗堆临时费用，不提前刷新同回合或中间选牌状态。

这些是代码与契约范围，具体实局覆盖以逐局轨迹为准。

## 最终验收与资源口径

整批完成后，按固定计划筛选 40 个完成文件，逐一核对角色、种子、A10、SHA-256、连续哈希链和真实 TerminalReceipt，再用最终 native worker 重新预测每个已记录战斗动作、重新枚举菜单动作并检查后帧。菜单按明确语义及控制阶段验证；普通战斗另比较八条精确建模 RNG、未建模流不变量及私有状态。

资源统计分别保存 `/proc` 采样的 Python＋Java＋C++ 合计 RSS／PSS，以及 cgroup invocation 内存峰值。两种口径分别报告。按实际 Python PID 分段计算连续运行时长、局后 RSS、FD、Python／系统线程数及 CPU；修复间隔不算连续稳定运行。新资源记录还保留 cgroup OOM 事件和 swap 峰值。

最终收尾核对原只读运行文件 26 项、systemd unit 指纹、固定上游 5041 文件、profile、模型、原服务 PID／重启数和动作端点状态；确认无遗留 worker 后，归档隔离验收许可。原服务及旧全局闸门保持原状态。
