# Headless 铁甲地图、营火与生命周期离线契约

后续进展：[普通战斗决策点与精确 RNG 离线验收](D:/code/aiplayspire/docs/HEADLESS_COMBAT_BRIDGE.md) 已接通四角色基础夹具；实际开局／采集约束保持原样。

2026-11-16：在菜单交互桥上增加地图、营火、跨幕和终局结算契约。当前仍以铁甲为验收对象，线上 BOOT 服务保持原样，动作接口、Agent 和采集闸门继续关闭。

## 本轮实现

| 层 | 位置与作用 |
|---|---|
| 原生事实 | `D:/code/aiplayspire/deploy/headless/HeadlessFlowExport.java`：附加 `headless-flow-v1`，读取真实可达节点、营火选项、当幕路径、营火 metric 和结果屏证据 |
| 原生生命周期 | `D:/code/aiplayspire/deploy/headless/HeadlessFlowKernel.java`：在独立包装类识别完成的胜利屏，等待 Recall 的钥匙效果结束；原上游和线上 class 保持原样 |
| 契约与回执 | `D:/code/aiplayspire/sts_ai_framework/headless/flow.py`：合法动作投影、逐步骤生效验证和纯内存终局回执 |
| 集成 | `D:/code/aiplayspire/sts_ai_framework/headless/interaction.py`：仍由 `observe / prepare / validate_before / verify_effect` 使用现有 `GameAction` 和 `GameState` |
| 基础映射修复 | `D:/code/aiplayspire/sts_ai_framework/headless/state.py`：允许已观测的 Boss／宝箱／胜利房位于图外 `(-1,15)`，包括第四幕；普通悬空坐标继续报错 |

以上代码只生成原生方法名称和参数计划，没有网络提交器、动作执行队列或 Agent 接线。

### 地图

- 第一个房间只开放有出边的 row-0 节点；后续区分真实物理边与 Winged Greaves／Flight 的同层飞跃。排除 backing grid 的无路径填充格。
- 现有 `choice_index` 对应 `chooseMapNode(x,y)`，同时填充 `current_map_choices`，与已有地图 policy 候选兼容。飞靴候选保持独立，不写入物理地图边。
- 生效要求目标坐标、floor +1、当幕 `pathX/pathY` 追加、已选择首房及进入房间共同成立。飞靴走捷径检查真实 counter 扣次，最后一次从 1 变为 -2；正常路径保持次数。
- 普通幕顶层提供图外 Boss 入口；第四幕使用原生短图的精英→心脏节点，不套用 row-14 Boss 出口。

### 营火

- 保留原生选项类名和原生索引，紧凑 API 索引跳过禁用选项。覆盖 Rest、Smith、Recall、Lift、Dig、Toke 六种语义。
- 检查 Coffee Dripper、Fusion Hammer、Girya 三次上限、Shovel、Peace Pipe、可升级牌、可删牌和已持有红钥匙。已选择营火动作或未知选项不生成计划。
- Rest 验证治疗值、Regal Pillow、Mark of the Bloom、Dream Catcher 落点；满血／禁止治疗时仍需新营火记录与房间完成证据。
- Recall 要求真实已知红钥匙到账；Lift 要求 Girya +1；Dig 要求新 RELIC 奖励。metric 必须在当前 floor 追加相应记录，旧记录不算生效。
- Smith／Toke 打开 GRID 是一个步骤，选牌和最终升级／删牌由前一批的 UUID 与张数契约继续验证，分别记录成功。

### 跨幕、双 Boss 与第四幕

- 普通房间“继续”打开地图，floor 不变；地图选房才推进楼层。
- Boss 战后依次区分宝箱、A20 第三幕第二个 Boss、Spire Heart 对话与完整胜利。`GameKernel.proceed()` 的图外房间转换会推进 floor，但不会追加 `pathX/pathY`，验证按原生行为处理。
- 第一／二幕宝箱后的“继续”要求 act +1、新幕地图、首房未选、当幕路径清空、牌组／物品与持有钥匙延续，以及正确的跨幕治疗和金币。
- A20 第三幕首 Boss 后仍为 Act 3，要求第二场 Boss 战及剩余数量从 2 到 1，不提前访问下一 Boss ID。
- Spire Heart 按 INTRO→MIDDLE→MIDDLE_2→DEATH／GO_TO_ENDING 分步验证。三把钥匙和最终幕可用性决定分支；进入第四幕要求新短图与重置路径。TheEnding 构造函数保留旧 VictoryRoom 节点直至首房，本层保留这一事实。

### 终局结算

- 区分战败、第三幕普通胜利、第四幕心脏胜利；`isDungeonBeaten` 或 `RUN_COMPLETE` 标签本身不生成终局确认。
- 第三幕普通胜利会使用 DEATH 屏并设置 player dead，依据 VictoryRoom 和 `GameOverScreen.isVictory` 识别，避免误标战败。心脏胜利要求 TrueVictoryRoom、VICTORY 屏与胜利对象。
- 完整终局提供现有 `proceed` 对应的 `LOCAL_ACK_TERMINAL` 计划。`acknowledge_terminal(plan, frame)` 返回纯内存幂等回执，包含 run/branch、角色、进阶、原生终止楼层、幕、胜负、Heart 和快照 token；保持原生楼层，不提前归一成训练标签。
- 重复确认返回同一回执；已确认后终局内容变化报错。本地确认不调用引擎、不自动重开、不生成采集文件，`verify_effect` 标记 `LOCAL_ONLY`。

## 验证与现场

- 新增 `D:/code/aiplayspire/sts_ai_framework/tests/test_headless_flow.py`：38 项通过。
- Framework 全量：347 项通过，失败、错误、跳过均为 0。
- 服务器基础状态 28 + 菜单交互 41 + 本轮 38，共 107 项通过。
- Java fixture 在真实局部地图节点、营火选项上重复读取 200 轮，检查 36 组终局条件；与既有基础状态 200 轮、事件 200 轮和 95 组 Neow 描述一同通过。
- Java→Python 闭环覆盖地图飞靴落点、营火原生索引与红钥匙到账、跨幕 route、心脏终局回执。前后结果来自合成观察，原生游戏动作执行数为 0。
- 测试实际引擎仍为 BOOT；`NoRunGuard` 前后核对无 player、地图、种子、run RNG，读取未推进全局 RNG。`HeadlessFlowKernel.phase()` 在未开局状态重复读取也保持 BOOT。

服务器隔离目录为 `/home/ubuntu/headless-spire-poc/offline-flow-contract-v1`，复用现有 JDK 21 与 conda spire，新增依赖为 0。临时测试 unit 使用 PrivateNetwork、768 MiB 内存上限、无 swap、单核配额、只读系统目录，结束后无常驻 fixture。没有覆盖原 `probe-classes`，没有重启只读服务。

现场复核记录在报告：原只读服务仍为 PID 74516、自动重启 0，`/state` 为 BOOT、开局与采集初始化标志关闭，GET `/action` 为 404。两个 `ALLOW_COLLECTION` 均缺失；SoulNPC 与保留的 Napcat 运行，第二路 Napcat 保持停止、禁用。

## 复验

本机只运行测试：

```powershell
cd D:/code/aiplayspire
& D:/miniconda3/envs/spire/python.exe -m unittest sts_ai_framework.tests.test_headless_flow -v
& D:/miniconda3/envs/spire/python.exe -m unittest discover -s sts_ai_framework/tests -t .
```

服务器固定输入目录：

```bash
P=/home/ubuntu/headless-spire-poc
W="$P/offline-flow-contract-v1"
sudo systemd-run --unit=headless-offline-flow-contract --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$W/repo" \
  -p MemoryMax=768M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=64 \
  -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes \
  -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$W" \
  -p NoNewPrivileges=yes /bin/bash "$W/tools/run-flow-contract.sh" "$P" "$W"
```

本地报告：`D:/code/aiplayspire/debug/headless-flow-contract`；服务器原件：`/home/ubuntu/headless-spire-poc/offline-flow-contract-v1/reports`。包含全量回归、Java 编译／fixture、跨语言结果、源码指纹与线上只读核对。

## 四角色采集目标的剩余验收

目标保持“服务器上跑通四个角色的数据采集”，端到端实局验收目前仍为 0/4。本轮完成生命周期离线契约，后续继续推进：

1. 战斗全量快照、合法出牌／药水／结束回合、RNG 和搜索桥；逐动作回放验证。
2. 扩展其他三角色的专属状态与选择语义，补未支持事件、效果和存取档连续性。
3. 串行执行队列、去重／生效屏障、采集数据 provenance、终局闭环和失败续跑契约。
4. 依照开局／采集操作约束分阶段做实际四角色验收、数据校验与资源稳定性测试。

当前证据范围为源代码契约、局部对象与合成前后帧。特殊遗物导致的跨幕物品变化、未覆盖的自定义模式保持 `UNVERIFIED`，实际引擎对局行为留待后续实局验收。
