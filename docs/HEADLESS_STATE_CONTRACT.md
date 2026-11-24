# Headless 对局状态离线契约

后续进展：2026-11-16 已增加独立的铁甲菜单交互离线契约层，见 [铁甲交互桥验收](D:/code/aiplayspire/docs/HEADLESS_IRONCLAD_INTERACTION.md)。本文记录基础映射阶段；原 mapper 的动作关闭行为及线上 BOOT 只读服务保持原样。

2026-11-16 已补齐卡牌、UUID、地图、钥匙与历史的基础对局状态映射，并完成 Python、Java→Python 和现有 format-12 特征链的离线回归。现有只读 HTTP 服务未重建、未重启，仍仅返回 BOOT；动作接口、Agent 与采集闸门保持关闭。

## 数据流与范围

1. `D:/code/aiplayspire/deploy/headless/HeadlessStateExport.java` 补充上游紧凑观察中丢失的真实字段，标记 `bridgeSchema=headless-gamestate-v1`。
2. `D:/code/aiplayspire/sts_ai_framework/headless/state.py` 中的 `HeadlessStateMapper` 只做数据校验和转换，返回现有 `GameState`；没有网络、引擎启动或动作调用。
3. 现有 `value_state_payload`、`map_context_fields`、`inference_context` 接收转换后的状态，模型格式、特征顺序和 checkpoint 均保持原样。

本轮覆盖玩家／角色／进阶、牌组与选择牌、遗物／药水基本信息、怪物基本状态、屏幕分类及下表的重点字段。选择列表、可操作标志、卡牌可打出和药水可使用标志统一保持关闭，不推导动作索引。事件完整语义、商店清单／奖励动作关联、怪物多段招式及战斗快照另行适配。它不是可开局采集的完整通信桥。

## 重点字段契约

| 字段 | 处理规则 |
|---|---|
| 卡牌升级 | 所有牌组、奖励牌、选牌候选和已选牌显式导出 `timesUpgraded`，映射到 `Card.upgrades`；例如 Searing Blow 3 次升级进入现有特征时为 `Searing Blow+3`。不从展示名称或 upgraded 布尔值推测次数 |
| 卡牌永久值 | 分开保留 cost / costForTurn、baseDamage / damage 等；保留真实 0，负数 unavailable 哨兵不伪造为 0 |
| UUID 与索引 | 直接读取 `card.uuid`，不复制卡牌、不生成 UUID、不用 card ID 或数组位置代替身份；组内重复 UUID 报错，跨主牌组／战斗副本的同 UUID 可保留；索引严格为当前组的 0-based 顺序 |
| 地图 | 使用节点自身 x/y、真实唯一边、当前位置和燃烧精英标志；保留有入边的末端节点，剔除无入出边的填充格。winged 候选不混作物理边，越层／悬空边报错；row 14 的 boss 出口统一成图外 `(-1,15)` 节点，format-12 仍是 105 格 |
| 位置 | 原始坐标不预先归一化；由既有特征链转换为 x/6、y/14。地图行与全局 floor 分开，检查 Act 1/2/3 的 1/18/35 起点；Neow 的 y=-1 不伪造成格内位置 |
| 钥匙 | Java 读取真实 Settings 标志；Python 对缺失颜色保留 unknown，通过 `KeyState.model_fields_set` 延续 known mask。确认持有的 true 可在同局跨幕／地图缺失时保留；不保留失去观测的旧 false，不从燃烧节点猜测已拿钥匙 |
| 历史 | 从 metric 的 selected/resolved 两条记录读取，不把问号房的选择符号当作实际结果；保留真实全局楼层，排除当前与未来楼层，不补造缺失记录。既有特征链再筛当幕已离开的房间并取最近 8 个 |

旧版仅有 id/name 的紧凑牌表会明确触发契约错误，而非补造升级、UUID 或永久属性。可见 boss 有 floor 0、boss 奖励与已完成 boss 房的隐藏检查；原始 seed、RNG、未来 boss 和 router plan 不透传到模型输入。

## 顺序、分支与 JSON

每个 mapper 管理一个有序 run/branch 的已确认钥匙。对局调用必须提供显式身份和递增快照序号：

```python
from sts_ai_framework.headless.state import HeadlessStateMapper

mapper = HeadlessStateMapper()
state = mapper.map_state(exported_observation, run_id="offline-branch-A", sequence=0)
payload = mapper.map_payload(exported_observation, run_id="offline-branch-A", sequence=0)
```

同序号、同内容允许重读；乱序、同序号变更、同局楼层回退、确认钥匙从 true 回退到 false 会报错。恢复较早存档／搜索分支时使用新 mapper、`reset()` 或新 branch identity，防止未来钥匙信息回灌。run_id 仅用于隔离上下文，不冒充训练数据的 seed_group。

所有卡牌、地图、历史和现有 Pydantic 模型全部验证完成后才提交钥匙记忆；转换失败不污染下一帧，也不修改原始观察。

JSON 边界使用 `map_payload()` 或 `model_dump(exclude_unset=True)`。直接用默认 model_dump 会把 KeyState 的未观测默认 false 一起展开，丢失 known mask；本轮对此做了完整 JSON 往返回归。

## 离线验证结果

| 检查 | 结果 |
|---|---|
| 新增映射与特征契约 | 33 项通过，无跳过 |
| Framework 全量回归 | 268 项通过，无失败、错误或跳过 |
| 既有 map_context 回归 | 22 项通过，无跳过 |
| 服务器纯映射回归 | 28 项通过 |
| Java 实际对象导出 | 本地卡牌／地图对象重复读取 200 轮，UUID、统计值、边与标志不变，导出未推进全局 RNG |
| Java→Python 闭环 | Java 生成的合成观察成功映射为现有 GameState，并通过 JSON 往返 |
| 现场保持 | 原只读服务 PID 74516、自动重启 0；仍为 BOOT，`/action` 返回 404，两个 ALLOW_COLLECTION 均缺失 |

Java 测试只初始化基础库并构造局部卡牌、节点作为测试对象；没有创建玩家、挂载地图或调用 newRun。对局 phase 标签只存在于合成 JSON 中，`NoRunGuard` 在测试前后确认实际引擎仍未开局。只读服务的工具、class 与安装的 unit 指纹保持一致。

服务器工作区为 `/home/ubuntu/headless-spire-poc/offline-state-contract-v1`。编译输出、合成观察和偏好文件全部写入这个独立目录；没有覆盖现有 `/home/ubuntu/headless-spire-poc/probe-classes` 或上游运行产物。离线测试使用私有网络、设备和临时目录、单核 CPU 配额及 768 MiB 内存上限，临时 unit 已结束。

## 复验命令与报告

本机 conda spire：

```powershell
cd D:/code/aiplayspire
& D:/miniconda3/envs/spire/python.exe -m unittest sts_ai_framework.tests.test_headless_state sts_ai_framework.tests.test_headless_state_features -v
& D:/miniconda3/envs/spire/python.exe -m unittest discover -s sts_ai_framework/tests -t .
cd D:/code/aiplayspire/selectcard/src
& D:/miniconda3/envs/spire/python.exe -m unittest test_map_context -v
```

服务器现有离线目录：

```bash
P=/home/ubuntu/headless-spire-poc
W="$P/offline-state-contract-v1"
sudo systemd-run --unit=headless-offline-state-contract --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$W/repo" \
  -p MemoryMax=768M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=64 \
  -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes \
  -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$W" -p NoNewPrivileges=yes \
  /bin/bash "$W/tools/run-state-contract.sh" "$P" "$W"
```

本地报告：`D:/code/aiplayspire/debug/headless-state-contract`。包含完整 Framework 测试日志／统计、Java 合成观察、映射结果及现场与文件指纹清单；服务器原件在 `/home/ubuntu/headless-spire-poc/offline-state-contract-v1/reports`。
