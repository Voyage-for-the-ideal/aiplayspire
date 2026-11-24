# Headless 铁甲交互桥：离线契约验收

后续进展：[地图、营火、跨幕与结算离线契约](D:/code/aiplayspire/docs/HEADLESS_IRONCLAD_FLOW.md) 已完成；本文的菜单验证统计保留为该阶段记录。

2026-11-16：已落地铁甲优先的事件、商店、奖励、选牌语义、合法动作计划与生效校验。新增 41 项交互测试、Framework 全量 309 项测试、服务器隔离 69 项测试全部通过。线上继续使用原 BOOT 只读服务，动作接口、Agent 和采集闸门保持关闭。

## 实现与接入方式

数据流：`HeadlessInteractionExport → IroncladInteractionBridge.observe → GameState + LegalAction → ActionPlan → verify_effect`。

| 文件 | 职责 |
|---|---|
| `D:/code/aiplayspire/deploy/headless/HeadlessInteractionExport.java` | 在基础状态上读取真实菜单字段，附加 `interaction.schema=headless-interaction-v1`；读取操作不触发选择或奖励领取 |
| `D:/code/aiplayspire/deploy/headless/semantics/ChoiceScreenUtils.java` | 离线编译用的纯文本清理 shim，复用原格式规则 |
| `D:/code/aiplayspire/sts_ai_framework/headless/interaction.py` | 纯映射、合法动作计划、过期保护、本地删牌意图与后置条件验证 |
| `D:/code/aiplayspire/sts_ai_framework/tests/test_headless_interaction.py` | 合成前后帧回归，覆盖成功、无变化、错误目标与证据缺失 |
| `D:/code/aiplayspire/deploy/headless/InteractionExportFixtureTest.java` | 实际 Java 局部对象读取与合成观察；测试前后校验引擎 BOOT、RNG 和闸门 |
| `D:/code/aiplayspire/deploy/headless/check_interaction_fixture.py` | Java→Python 的菜单索引、原生方法计划和事件生效契约 |
| `D:/code/aiplayspire/deploy/headless/run-interaction-contract.sh` | 在指定隔离目录编译并运行以上回归 |

基础状态映射器仍保持原行为：动作关闭。交互能力由独立的 `IroncladInteractionBridge` 显式投影，线上 HTTP 服务未接入这层。模块中没有 HTTP 客户端、执行器、Agent 实例或开局函数调用。`LegalAction.native_method/native_args` 是数据计划。

离线使用示例（`before_raw`、`after_raw` 为测试观察）：

```python
from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.models import GameAction

bridge = IroncladInteractionBridge()
before = bridge.observe(before_raw, run_id="offline-ironclad-A", sequence=0)
plan = bridge.prepare(before, GameAction(type="choose", choice_index=0))
assert bridge.validate_before(plan, before)
after = bridge.observe(after_raw, run_id="offline-ironclad-A", sequence=1)
result = verify_effect(plan, after)
```

## 菜单语义与合法动作

### 事件与 Neow

- 复用现有 `EventStateExtractor` 的结构化事件模型和概率分支；该 Java 源文件按字节复制到离线输入目录，原 mod 源码保持原样。本轮新增的 Headless 适配不依赖翻译文本推断效果。
- 分开保存原生 `button_index` 和现有 API 的紧凑 `action_index`，禁用按钮留下的索引空洞不会选错按钮。
- 检查语义状态、按钮身份、布尔值及概率合法性；未知语义和小游戏选项不生成执行计划。已知事件中的未实现效果以 `UNVERIFIED` 留待补齐。
- Neow 读取已缓存的类型、代价、HP bonus 和阶段；19 种奖励类型 × 5 种代价共 95 组描述通过构造与序列化检查，过程中没有创建或激活奖励。
- Cursed Tome 的继续阅读、实际损血与最终领奖分步校验；单步控制转换不冒充整条事件完成。

### 商店

- 导出卡牌、遗物、药水的当前价格、售出标志、真实原生索引及卡牌升级数；使用 UUID 标识卡牌槽位。
- 资金不足、售出、药水带已满或持有 Sozu 时，相关购买不进入合法动作集；零价商品保留为有效价格。
- 购买成功要求价格扣除、商品消耗或 Courier 补货、目标物品到账共同成立；卡牌到账升级计入对应蛋遗物效果。
- 原生 `GameKernel.purgeCard(index)` 是原子操作。兼容旧流程的“打开删牌 GRID”通过本地虚拟意图实现：引擎仍在 SHOP、不扣钱；选定 UUID 后才形成 `purgeCard` 计划。本地取消也不调用引擎。
- 删牌候选来自原生可删列表，再检查主牌组 UUID、瓶装牌及三种不可移除诅咒。提交前重新核对原生快照 token，避免价格或列表变动后沿用旧索引。

### 奖励与宝箱

- 区分打开卡牌奖励、实际领取、跳过、Singing Bowl、Boss 遗物和开箱。
- 金币、遗物、药水、钥匙领取同时验证列表消费与实际到账；关联钥匙／遗物要求互斥的两个奖励一起移除。
- 卡牌奖励用真实 offer UUID 列表核对打开结果；领取与跳过有不同后置条件，Singing Bowl 检查 max HP +2。
- 奖励叠加选牌屏时保留父奖励列表。父列表缺失代表证据缺失，不解释为空列表。

### 选牌

- 区分 grid／hand、purpose、所需张数、任意张／零张、自动确认、锁定预览和显式确认。过滤后的紧凑索引仍映射回真实原生索引。
- 升级预览使用原始 hovered 卡牌 UUID；不把升级预览副本的 UUID 当作主牌组身份。打开预览与永久升级分别验收。
- 支持删牌、升级、变化、复制、瓶装、确认获得，以及弃牌／消耗、置顶、取回消耗牌和战斗复制等选择类型。
- 铁甲 Armaments 升级只比较战斗牌副本并保持主牌组不变；Headbutt／Warcry 检查抽牌堆顶 UUID；Exhume 检查离开消耗堆并进入手牌；Dual Wield 读取真实缓存复制数。
- 永久变化／复制按所选 UUID 集与新牌数量验证，避免只处理多选中的最后一张。

## 动作身份与生效判定

每帧冻结原始 JSON、投影状态和合法动作集；读取 `.state` 或 `.api_action` 返回新的模型视图。`prepare()` 只接受当前帧中唯一匹配的现有 `GameAction`。发出动作前的 `validate_before()` 检查 run/branch、顺序和内容 token；同内容重读可复用，内容变化使旧计划失效。映射失败不提交钥匙记忆。

| 状态 | 含义 |
|---|---|
| `APPLIED` | 后续帧满足该原生动作步骤的明确后置条件 |
| `PENDING` | 同局较新帧的原生内容仍未变化 |
| `REJECTED` | run／顺序错误或出现本批未支持的跨房间边界 |
| `UNVERIFIED` | 有变化，但目标、数量、资源或落点证据尚未完整匹配 |
| `LOCAL_ONLY` | 本地删牌意图／取消，没有游戏效果 |

`APPLIED` 的粒度是单个动作步骤。例如打开选择框、确认预览、领取奖励各自独立；它不代表整个意图完成或样本达到训练准入条件。队列 ACK、文字变化或任意切屏均不单独算生效。

## 离线验证结果

| 检查 | 结果 |
|---|---|
| 新增 Python 交互契约 | 41 项通过 |
| 本机 Framework 全量 | 309 项通过，失败／错误／跳过均为 0 |
| 服务器 Python 契约 | 基础状态 28 + 新交互 41，共 69 项通过 |
| Java 实际对象读取 | 基础状态 200 轮、事件 200 轮；95 组 Neow 描述通过 |
| Java→Python 闭环 | 事件生效、Neow 缓存奖励、商店索引空洞、奖励和选牌计划通过 |
| 游戏动作执行数 | 0；未调用 newRun、按钮效果、购买、领取或选牌 |
| 引擎约束 | 测试前后实际 BOOT，无 player／map／seed／run RNG；读取未推进全局 RNG |
| 线上指纹 | 26 个原服务文件及安装的 systemd unit SHA-256 均保持原样 |

Java fixture 只初始化基础库，构造局部卡牌／地图／事件对象；对局阶段和前后结果存在于合成 JSON。事件按钮效果在 fixture 中直接抛异常，防止测试误执行动作。测试明确读取真实 `HeadlessInteractionExport.capture()` 并确认仍为 BOOT。

服务器隔离目录：`/home/ubuntu/headless-spire-poc/offline-interaction-contract-v1`。编译输出、偏好文件和报告都位于该目录，原 `probe-classes`、上游运行产物及线上服务均未覆盖、未重启。临时 unit 使用私有网络、768 MiB 上限、禁用 swap、单核 CPU 配额和只读系统目录，运行完已结束。使用现有 JDK 21 和 conda spire，新增依赖为 0。

最后现场核对：原服务 PID 74516、自动重启 0；`/state` 为 MAIN_MENU／BOOT，所有开局初始化标志为 false；GET `/action` 为 404。以下两个闸门文件均缺失：

- `/home/ubuntu/headless-spire-poc/ALLOW_COLLECTION`
- `/home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION`

SoulNPC、保留账号的 Napcat 继续运行；第二个 Napcat 保持 inactive／disabled。

## 当前验收边界

本批完成铁甲优先菜单的离线契约层。地图／营火编排、跨幕与结算流程、战斗快照／RNG／C++ 搜索桥、动作执行队列及逐局数据验收仍是后续阶段。retain 的最终生效及部分特殊事件选牌来源也待补充。未知效果保持 `UNVERIFIED`，未知选择用途触发契约错误；95 组 Neow 描述检查不等于 95 种实际游玩验收。Headless 真实开局到采集的端到端验收仍为 0/4 角色。

## 复验与报告

本机（只运行 unittest）：

```powershell
cd D:/code/aiplayspire
& D:/miniconda3/envs/spire/python.exe -m unittest sts_ai_framework.tests.test_headless_interaction -v
& D:/miniconda3/envs/spire/python.exe -m unittest discover -s sts_ai_framework/tests -t .
```

服务器（目录中已准备固定版本输入）：

```bash
P=/home/ubuntu/headless-spire-poc
W="$P/offline-interaction-contract-v1"
sudo systemd-run --unit=headless-offline-interaction-contract --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$W/repo" \
  -p MemoryMax=768M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=64 \
  -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes \
  -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$W" \
  -p NoNewPrivileges=yes \
  /bin/bash "$W/tools/run-interaction-contract.sh" "$P" "$W"
```

本地归档：`D:/code/aiplayspire/debug/headless-interaction-contract`。其中根目录保存全量回归日志／统计，`reports` 子目录保存服务器 Java／Python 日志、合成观察、跨语言结果、源码 SHA-256 和最终线上只读核对。服务器原件位于 `/home/ubuntu/headless-spire-poc/offline-interaction-contract-v1/reports`。
