# Headless 战斗桥：普通决策点与精确 RNG 离线验收

> 本文保留 2026-11-16 离线阶段的范围和测试计数。后续实局、八流 RNG 策略及四角色采集进展见 [实局验收记录](D:/code/aiplayspire/docs/HEADLESS_RUNTIME_ACCEPTANCE.md)。

2026-11-16：已接通 Java 源事实→Python 契约→C++ 导入／搜索→独立合成后帧验证。四个角色的基础 Strike 夹具均通过，真实开局与采集仍为 0/4；线上继续保持 BOOT、动作关闭、Agent 关闭及采集闸门缺失。

## 实现

| 文件 | 职责 |
|---|---|
| `D:/code/aiplayspire/deploy/headless/HeadlessCombatExport.java` | 普通战斗决策点快照、真实 `canUse` 动作矩阵、原生怪物槽位、14 条 RNG、真实运行时卡池与源私有原始字段 |
| `D:/code/aiplayspire/sts_ai_framework/headless/combat.py` | 不可变帧、合法动作计划、执行前 token、源快照一致性与后置证据校验；没有 HTTP、worker 实例或执行器 |
| `D:/code/aiplayspire/battle_engine/src/HeadlessRng.h` | 版本化的 64 位状态词校验和直接 RNG 恢复 |
| `D:/code/aiplayspire/battle_engine/src/Snapshot.cpp` | 接入直接恢复、`fingerprint` 诊断、搜索的逐步预测与暂时关闭的 Headless 路径缓存 |
| `D:/code/aiplayspire/deploy/headless/CombatExportFixtureTest.java` | 局部卡牌、怪物和 RNG 对象，构造四角色合成观察，实际引擎保持 BOOT |
| `D:/code/aiplayspire/deploy/headless/check_combat_fixture.py` | 通过管道运行 C++ worker；独立按 Strike 规则构造后帧，与搜索预测核对 |

`capture()` 在普通 `PLAYER_TURN` 才附加 `headless-combat-v1`。动作队列、待打牌、limbo、结束回合等过渡状态被挡在该边界外。牌堆保留真实 UUID、升级、永久／回合费用、基础值与执行标志；怪物包含死亡历史槽位，保持药水和出牌的原生目标索引。

Java 序列化使用 `HeadlessCombatExport.toJson(kernel)`，显式 `serializeNulls()`。这保证 `card_in_use:null` 等字段保留，避免 Gson 默认省略 null 后破坏现有 C++ 快照契约。真实运行时卡池直接复用按字节复制的 `BattleCardPools.java`，没有改动原 mod 源码或版本。

## 合法动作与生效证据

- Java 在普通决策点读取卡牌／药水的真实可用性。目标牌枚举每个有效怪物原生槽位；无目标牌使用 -1。`playCard`、`usePotion` 的手牌／药水／怪物索引均为原生 0-based，结束回合对应 `endTurn()`。
- Python 对比快照与 GameState 的角色、幕、楼层、进阶、玩家资源、牌 UUID／顺序／费用、遗物和全部怪物／药水槽位。重复动作、过期 UUID、错误目标、布尔值冒充整数等触发契约错误。
- `prepare(frame, native_response)` 要求恰好一条合法动作，以及匹配的 `expected_before`、`expected_context` 和完整预测。`validate_before(plan,current)` 校验同 run、顺序与冻结快照 token。
- 生效要求后续真实观察重新导入 C++ 得到的 `fingerprint`，同时匹配公共投影、原生状态键、7 条已建模 RNG 的完整状态、其余随机流不变量和未适配的源私有 primitive 字段。ACK、单个 HP 变化或切屏不单独算成功。
- `APPLIED` 是单步证据匹配。原生 key 为 null 的选择／胜利／清理边界暂不释放为可执行计划；这些还需要独立 continuation 契约。

`CombatFrame.snapshot`、`.state`、`.legal_actions` 返回新的视图。失败映射不提交基础 mapper 的钥匙记忆；原模块的非战斗 BOOT 行为保持原样。模块只生成方法／参数计划，线上服务没有导入或注册这一执行路径。

## RNG 契约

- 格式 `headless-rng-v1`，policy `headless-b354a9ee-v1`，绑定服务器固定上游提交 `b354a9ee8e118e906d9b9f55ba043100cc1ac6c7`。
- 每条流的两个 state word 使用 16 位小写十六进制字符串，完整保留高于 2^53 的位模式；MathUtils 的两个 state word 独立保存。
- 14 条游戏流都必须存在，counter 为范围内整数，并与兼容快照的 counter 字段逐项一致；双零状态、类型错误、未知 policy 和缺失流均报错。
- C++ 对 ai、card_random、misc、monster_hp、potion、shuffle、card 七条现有流直接设置内部状态和 counter。带 `_engine=headless` 的快照必须有精确 RNG，旧 SaveState 继续沿用原 counter 重建路径。
- Headless 快照使用显式 `act_num`，不再单凭 floor 推断幕。Headless 路径缓存暂时关闭；旧链缓存行为保持原样。

**精确恢复和 RNG 调用策略是两项独立工作。** 上游 headlessRng 参与回合末弃牌顺序、部分随机选牌／目标等路径，现有模拟器尚未完成这些路径的迁移。因此该流等未建模流发生变化时返回 `UNVERIFIED`，后续继续补策略调用点、私有状态与选择 continuation。

## 已执行的验证

| 项目 | 结果 |
|---|---|
| 本轮 Python 战斗契约 | 17 项通过 |
| 本轮 C++ 精确 RNG 回归 | 6 项通过 |
| Framework 全量 | 364 项通过，失败／错误／跳过均为 0 |
| 服务器 Headless 合同回归 | 基础 28 + 菜单 41 + 生命周期 38 + 战斗 17，共 124 项通过 |
| 服务器原生后端全量 | 195 项通过，失败／错误／跳过均为 0 |
| C++ 构建 | Windows MSVC 和服务器 GCC Release 均通过，未安装新依赖 |
| Java→C++ RNG | 四角色各 6 流 × 16 个样本，共 384 个样本一致；使用高位状态与不等于 seed/counter 重建结果的局部 RNG |
| 四角色合成动作闭环 | 每个角色使用实际基础 Strike 卡对象，经搜索得到 `playCard(0,0)`，独立按 6 点伤害／费用／牌堆／历史规则构造后帧，验证 `APPLIED` |
| 引擎约束 | Java 重复读取 200 轮；实际引擎仍 BOOT，无挂载 player／map／seed／run RNG；引擎游戏动作执行 0 次 |

四角色夹具共用局部 Cultist 测试对象；该对象的行动和 roll 方法会直接抛错，测试没有推进怪物回合。C++ 确实执行模拟搜索，但没有向 Java 游戏引擎提交出牌或其他指令。四个合成成功记录与真实端到端角色验收分开计数。

Windows 后端运行 195 项，其中 1 项 g++ 专项因 PATH 没有 g++ 而跳过；该执行预算生命周期专项已包含在服务器的 195 项无跳过回归中，复验命令见下节。

## 部署、复验与报告

隔离目录：`/home/ubuntu/headless-spire-poc/offline-combat-contract-v1`。原 `probe-classes`、上游代码和已安装的只读服务保持原样，编译／测试仅写入隔离目录。原版卡牌源码作为只读参考复制，用于现有费用表回归。

原生构建使用单核、1280 MiB 内存上限和零 swap。正式离线回归置于同上限的临时 systemd unit，PrivateNetwork、PrivateDevices、PrivateTmp、系统与 home 只读；测试用 mock 网关只在该私有网络内存在，结束后回收。

本机复验（已有候选二进制）：

```powershell
cd D:/code/aiplayspire
$env:BATTLE_ENGINE_WORKER='D:/code/aiplayspire/debug/headless-combat-contract/native/battle-engine-worker.exe'
& D:/miniconda3/envs/spire/python.exe -m unittest sts_ai_framework.tests.test_headless_combat battle_engine.tests.test_headless_rng -v
& D:/miniconda3/envs/spire/python.exe -m unittest discover -s sts_ai_framework/tests -t .
& D:/miniconda3/envs/spire/python.exe -m unittest discover -s battle_engine/tests -t .
```

服务器固定输入目录复验：

```bash
P=/home/ubuntu/headless-spire-poc
W="$P/offline-combat-contract-v1"
sudo systemd-run --unit=headless-offline-combat-contract --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$W/repo" \
  -p MemoryMax=1280M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=64 \
  -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes \
  -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$W" \
  -p NoNewPrivileges=yes /bin/bash "$W/tools/run-combat-contract.sh" "$P" "$W"
```

本地报告：`D:/code/aiplayspire/debug/headless-combat-contract`；服务器原件：`/home/ubuntu/headless-spire-poc/offline-combat-contract-v1/reports`。保存 Java 编译／夹具、原生构建／测试、跨语言结果、源码与二进制 SHA-256、最后线上只读核对。

下一步继续补 headlessRng 语义、怪物／能力私有字段和战斗选择／终局边界，再接串行执行屏障、其他角色非战斗桥及采集数据闭环。四角色服务器采集目标保持 active。
