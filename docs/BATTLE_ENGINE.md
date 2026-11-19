# 本地 C++ 战斗后端

2026-11-03 初版；2026-11-09 同步当前实现：已加入原生回合搜索、常驻 JSONL worker、三档预算和路径缓存；完整变更、归档与已知缺口见
[LIGHTSPEED_MIGRATION.md](LIGHTSPEED_MIGRATION.md)。四角色导入已接通，静默／观者原版牌已完成显式分派（各 75/75），9 类选择可从 SaveState 恢复；完整交互验收见 [NATIVE_CHARACTER_SUPPORT.md](NATIVE_CHARACTER_SUPPORT.md)。

## 当前实现与边界

搜索由 pinned `Attemory/sts_lightspeed@46e14e4adc23d2c1c738df8902a6f297109fee50` 原生进程执行。
Python 标准库网关负责 Java `writeUTF` 协议、长连接、超时与日志；使用现有 conda `spire`，无新增包。
网关串行管理一个常驻 worker，提交一个动作后重新读取真实状态；状态和卡牌身份一致时复用已搜索路径。
进程崩溃／超时后清空缓存并重启，当前请求不自动重试；离线入口仍支持一次请求一个进程。
网关和启动脚本默认 **C++ 独立模式**：不启动 Java 搜索实例，也不连接 `:5126`。

历史实机测试证据聚焦铁甲战士；当前四角色可导入，静默和观者的原版牌已完成显式分派，但完整交互对等尚未验收。普通决策点由 C++ 搜索；HAND／GRID／战斗奖励选择、弃牌、预见、保留、Setup、Meditate、Nightmare、Omniscience、Foreign Influence、Wish 等 9 类选择可从 SaveState 恢复并逐动作提交。
未知动作、在途卡牌、未知内容、缺失关键状态或预测失配时返回 `ERROR` 并记录原因，战斗会停住，不转交 Java。
最终替换目标是让 `sts_lightspeed` 承担全部四角色战斗搜索；剩余卡牌、选牌及特殊状态覆盖见迁移报告。
游戏本体、SaveState 读取与命令执行仍由 Java Mod 承担，BattleAiMod.jar 继续加载。

### 文件职责

| 文件 | 职责 |
|---|---|
| `battle_engine/src/Snapshot.cpp` | SaveState 转换、能力检查、恢复六条战斗 RNG、单动作搜索及公开状态投影 |
| `battle_engine/src/main.cpp` | 单请求 JSON 与常驻 `--serve` JSONL 入口 |
| `battle_engine/server.py` | 默认 C++ 独立的 loopback TCP 网关、worker 超时、响应检查和 JSONL 遥测 |
| `battle_engine/start.ps1` | 启动 C++ 网关，默认关闭 Java 回退 |
| `battle_engine/start-game.ps1` | 仅启动真实游戏客户端，显式使用 external 后端，跳过旧 Java 搜索启动链 |
| `battle_engine/protocol.py` | modified UTF-8，包括 NUL、UTF-16 surrogate、65,535 字节限制 |
| `battle_engine/state.py` | Python 侧公开投影及原始快照归一化 |
| `battle_engine/tools/acceptance.py` | 快照导入、命令 schema、可选 Java 单步对照 |
| `battle_engine/tools/oracle.py` | 在独立运行目录启动 Java 测试实例并自动清理进程 |

## 构建

在仓库根目录执行：

```powershell
.\battle_engine\build.ps1
mvn -f STSStateSaver/pom.xml package
mvn -f scumthespire/pom.xml package
```

构建脚本优先使用现有 CMake，否则使用已安装的 Visual Studio C++ 工具；不下载工具链。
同时提供 CMake 工程用于 MinGW/GCC。原生输出：`battle_engine/bin/battle-engine-worker.exe`。
MSVC 使用强制头统一上游 class/struct 前置声明并兼容 GCC popcount；vendor 的可移植性与运行时卡池补丁见 VENDORED.md。
BattleAiMod 为 1.10.0，SaveStateMod 为 1.6.1；后者修复 Gson 在嵌套动作中省略空 modifiers 时的兼容读取。
`mvn package` 按现有流程部署到仓库的 `_ModTheSpire/mods/`。
游戏目录的模组应由操作者确认版本后再复制，测试实例使用独立模组副本。

## 铁甲战士 quick start：仅 C++ 搜索

已构建的 worker 和模组可直接复用，本次关闭回退无需重新编译 C++ 或 Java。
首次测试前，关闭游戏并将两个已构建模组复制到游戏目录；备份放在 mods 目录之外：

```powershell
$game = 'D:\Program Files\Slay the Spire'
$backup = "D:\code\aiplayspire\battle_engine\out\manual-backup-$(Get-Date -Format yyyyMMdd_HHmmss)"
New-Item -ItemType Directory -Force $backup | Out-Null
Copy-Item "$game\mods\BattleAiMod.jar","$game\mods\SaveStateMod.jar" $backup
Copy-Item 'D:\code\aiplayspire\_ModTheSpire\mods\BattleAiMod.jar' "$game\mods"
Copy-Item 'D:\code\aiplayspire\_ModTheSpire\mods\SaveStateMod.jar' "$game\mods"
```

**窗口 1：网关。** 即时／平均／质量分别使用 250／2000／10000 ms，默认平均；写入 gateway.log 和 requests.jsonl：

```powershell
& 'D:\code\aiplayspire\battle_engine\start.ps1'
```

看到 `BATTLE_ENGINE_READY ... mode=cpp-only fallback=disabled` 后，再启动游戏。

**窗口 2：游戏客户端。**

```powershell
& 'D:\code\aiplayspire\battle_engine\start-game.ps1'
# 固定种子时使用（可选）：
# & 'D:\code\aiplayspire\battle_engine\start-game.ps1' -RunSeed CPPBENCH01
```

在游戏中选铁甲战士，开启原有 autoStartAi，或在战斗中点击 Start Steve。
游戏脚本显式传入 `-Dbattleai.backend=external -Dbattleai.clientPort=5125`，默认不覆盖所选档位预算，
直接探测网关，不依赖旧 `:5124` 握手，也不自动启动 Java 搜索进程。
默认使用现有 `<Default>` 模组配置；其他安装路径可用 `-GameDir` 指定。
网关和游戏预算可以分别通过 `-MaxTimeMs` 调整，建议保持一致。

**窗口 3（可选）：非战斗自动决策。** 首次进入主菜单后手动点击开始：

```powershell
Set-Location 'D:\code\aiplayspire'
conda run --no-capture-output -n spire python -m sts_ai_framework `
  --character IRONCLAD --ascension 1 --interval 0.5 --no-auto-restart
```

日志保存在 `D:\code\aiplayspire\battle_engine\logs\cpp\`。
`requests.jsonl` 中正常请求记录 `backend=cpp`；未支持状态记录 `backend=cpp_error`、reason 和源快照路径。
出现停滞时保留日志和 source 对应快照，供后续补齐铁甲机制使用。
上述两个启动脚本均由操作者手动运行，本次修改没有启动游戏、网关或整局测试。

### 保留的旧后端对照入口

旧代码仅供显式对照时使用。手动启用需要 `--enable-java-fallback`（或 start.ps1 的 `-EnableJavaFallback`）
并自行启动 Java 服务；单独设置 `--fallback-port` 不会启用它。
`--java-only` 同样要求显式指定 `--enable-java-fallback`。铁甲独立测试不使用这些参数或旧实例。

## 协议与正确性约束

- 搜索成功为 `COMMAND_LIST` 帧后跟独立 `DONE` 帧；错误为 `ERROR` 后跟 `DONE`。
- `PING` 仅回应一帧 `PONG`，与现有 Java 控制端点一致。
- 响应超过 `writeUTF` 上限时，原子写入请求指定的 `command_file`，通过 `command_path` 返回。
- 支持一个连接上连续多个请求；不同连接共享一个搜索执行锁，健康检查独立回应。
- 默认错误路径在创建 Java 连接前返回 `ERROR` / `DONE`。显式开启旧后端对照时，才缓冲完整 Java 响应事务后转发。
- `max_expansions` 在正式回合搜索中计数节点扩展；离线 MCTS 对照仍计数 simulation，两种计数不等价。
- 搜索墙钟预算同时生效；按三档分配，网关默认硬上限 90,000 ms，另给 worker 导入／进程监督 2 秒宽限。
- 每个原生命令带 `expected_before` 和 `expected_context`。Java 在实际出牌前核对公开投影，
  并核对种子、楼层、角色、手牌 UUID、双方能力原始数据、怪物意图和遗物计数；失败设置 mustRestart。
- 非终局、非选择边界还返回原生 `expected_after`。下一请求与真实公开投影不一致时，
  记录 `prediction_mismatch` 并停用当前连接的当前战斗原生搜索；独立模式返回错误，显式启用回退时才转交 Java。
- 公开投影 v1 包括 HP/格挡/能量、四堆牌顺序和费用、怪物 HP、药水槽、球及六条 RNG counter。
  它是差异探针，不是全内部状态等价证明。Java 原有完整 state-diff 路径继续保留。
- six RNG：ai、cardRandom、misc、monsterHp、shuffle 使用 `seed+floor`；potion 使用 `seed`。
  不复制 battle-agent 示例的 RNG 扰动逻辑；Java 合约测试比较六条流的后续输出。
- 客户端随请求导出实际战斗卡池迭代顺序，原生侧用于 Creative AI、Hello World、Discovery 等随机生成。
  缺失卡池且搜索触及相应机制时明确报错；只恢复 seed/counter 仍不足以固定卡牌生成结果。

## `layered-v1` A10 候选

默认仍是原搜索。`search_policy=layered-v1` 启用 1／2／4／6 回合分层搜索；
`tactical_ordering=false` 用于隔离深度变化。Headless 以 `--combat-search layered-v1`
显式选用：普通战 5,000 展开／250 ms，精英（含燃烧精英）和 Boss 10,000 展开／500 ms。
旧式平铺 Headless r10 的接入仅在隔离副本使用：
`git apply --unidiff-zero --ignore-space-change /absolute/path/layered-v1-r10.patch`。
带 `--pilot20 --seed-base <新种子起点>` 的独立双 lane 计划每角色共跑 5 局。

同预算基准：`python -m battle_engine.tools.oracle --balanced-ab formal --game-dir <原版游戏目录> --manifest <冻结清单> --worker <候选二进制>`。
清单结构为 `{"cases":[{"path":"绝对路径","sha256":"…","role":"IRONCLAD","room":"MONSTER","seed":123,"split":"pilot"}]}`；
房间为 MONSTER／ELITE／BOSS。正式模式每角色每房间要求 3 个 pilot 和 20 个 holdout，
两组种子不得交叉。`--balanced-ab pilot --pilot-arm legacy|depth|combined` 用于消融，
`--balanced-ab smoke` 只检查链路。原版回放不一致、超预算和未到终局单独记录。
部署档和双方 15,000／90 s 的完整档均通过后，才进入服务器候选试跑。

用 `python -m battle_engine.tools.a10_corpus <原版 startstates 路径> [<额外采集路径> ...]`
统计每角色每房型唯一种子，补齐后加 `--freeze <新清单>`。用 `--exclude-manifest <冒烟清单>`
排除已用于调试的种子；合成样本不进正式验收集。
`python -m battle_engine.tools.a10_capture --help` 是本机 Windows 原版自动采集入口；
它使用独立偏好、现有 JAR 和显式的价值权重，按角色跑有时间上限的采集，不走外部 LLM。
Java 回放从同目录下的起始快照读取命令 guard。完整状态常规先核对哈希；
哈希不一致时，只归一化已定位的动画几何、空药水槽编号及战斗新卡 UUID，
保留源卡 UUID、相互引用、牌堆顺序、怪物意图和 RNG。

## 验证命令

```powershell
conda run --no-capture-output -n spire python -m unittest battle_engine.tests.test_backend -v
mvn -f scumthespire/pom.xml package
conda run --no-capture-output -n spire python battle_engine/tools/acceptance.py --protocol-only
conda run --no-capture-output -n spire python battle_engine/tools/acceptance.py --manifest debug/character-strategies/comparison/manifest.json
```

上述 manifest 是历史混合链路的 16 个样本。当前严格原生验收结果为 7/16，未支持状态不再当作通过；详细原因见迁移报告。离线旧快照没有运行时卡池时，
触及随机卡牌生成的搜索明确标记 `search=FALLBACK`；它只表示能力路由正常，不计作原生搜索通过。
真实引擎模式先导出当前 Java 卡池，要求这 8 个铁甲/机器人样本均产生命令并通过单步对照。
输出 `real_engine_checked=false` 代表只通过静态接入检查，不代表真实引擎回放通过。

独立真实引擎单步对照（游戏 JRE、模组及 ModTheSpire 配置均使用独立副本）：

```powershell
conda run --no-capture-output -n spire python battle_engine/tools/oracle.py --game-dir "D:\Program Files\Slay the Spire" --manifest debug/character-strategies/comparison/manifest.json
# 连续三步，并实际验证 Armaments 等选择界面的 Java 接管和回放：
conda run --no-capture-output -n spire python battle_engine/tools/oracle.py --game-dir "D:\Program Files\Slay the Spire" --manifest debug/character-strategies/comparison/manifest.json --steps 3 --allow-boundary-fallback
```

也可复用已启动的测试模式 Java 后端：

```powershell
conda run --no-capture-output -n spire python battle_engine/tools/acceptance.py --manifest debug/character-strategies/comparison/manifest.json --oracle-port 15126
```

真实对照要求 Java `REPLAY` 的公开结果与 C++ `expected_after` 一致；只看到 `diff_valid=true` 不算通过。
终局和选择界面的观测边界尚未统一时，工具明确标记 SKIP，并使该次真实对照检查失败。
`--allow-boundary-fallback` 会实际执行打开选择界面的动作，将新快照经网关交给 Java，再回放 Java 返回的选择命令。
通过时单独记录 `BOUNDARY_FALLBACK_PASS`；它是混合链路验证，不计作该边界的 C++ 状态等价。
多步快照和报告只写入输出目录，REPLAY 拒绝用输出覆盖输入快照。
当前 16 个快照主要覆盖第一幕开局，应继续补充中途恢复、洗牌、高进阶、Boss 阶段转换和选牌样本。
搜索质量另按同一真实快照、同一墙钟预算比较胜负、剩余 HP、药水及耗时。

## 故障与旧后端对照验收

铁甲独立测试不需要旧后端。新增回归用例检查默认关闭回退、显式参数冲突、错误路径零 Java 连接和 cpp_error 遥测。
最新离线后端回归已执行；实机由操作者手动运行，测试计数和严格验收结果见迁移报告。
历史四角色混合链路工具会显式启动独立 Java 对照实例，和 quick start 的运行链分开。
原有测试包含 modified UTF-8、分包、断连、长连接复用、超长响应文件、worker 超时、原样回退与预测失配隔离。
worker 常驻，进程崩溃后销毁其缓存并由下一请求重启，不影响网关；网关进程自身应由用户的常用进程管理方式启动。
运行日志和构建/验收生成物均在模块 .gitignore 中排除。

## 2026-11-02 验证记录

- Windows 已有 MSVC 工具链原生构建通过，无新增依赖。
- Python 后端测试 27 项通过；framework 回归 168 项通过。
- SaveStateMod 自带回归及 package 通过；BattleAiMod 108 项测试通过，0 跳过。
  后者显式提供了 realStateManifest 与 stateFile，状态键手工基准使用 warmup=5 / iterations=10 / samples=2 作功能冒烟，不作为性能结论。
- 16 个真实快照的混合链路检查通过：8 个铁甲/机器人入口走原生搜索；8 个静默/观者入口明确路由 Java。
- 连续三步检查完成 22 次原生公开状态对照；Armaments 打开选牌界面后由 Java 接管，并在真实引擎中回放选择路径通过。
- 静默、观者各一次真实 Java 回退事务通过。报告分别保留 native 等价与 boundary fallback 计数。
- 构建产物已进入仓库模组输出目录；日常游戏目录的模组与启动方式保持原状。整局多局长测与同墙钟策略质量对照仍待执行。
