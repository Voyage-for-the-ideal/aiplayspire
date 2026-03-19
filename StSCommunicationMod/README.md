# Slay the Spire Communication Mod

这是一个为《杀戮尖塔》(Slay the Spire) 制作的 Mod。
它在本地开启 HTTP 服务，用于：

1. 读取当前游戏状态（玩家、卡牌、怪物、可选项等）
2. 向游戏投递操作命令（出牌、结束回合、选择奖励、地图点选、用药水等）
3. 查询任意卡牌 ID 的静态信息

## 项目现状与关键行为

- 服务端口固定为 `5000`（代码中写死）。
- 接口地址：`/state`、`/action`、`/card_info`。
- `/action` 是异步队列模型：
  - HTTP 返回 `{"status": "queued"}` 仅表示命令已入队。
  - 真正执行发生在游戏主线程的 `receivePostUpdate()` 中。
- 大多数索引类操作都依赖最新一次 `/state` 返回结果。

## 目录结构

- `src/main/java`: Java 源码
- `src/main/resources`: Mod 资源（含 `ModTheSpire.json`）
- `pom.xml`: Maven 构建配置
- `Build.java`: 不依赖 Maven 的本地构建脚本（项目目录按脚本位置自动定位）

## 构建与安装

### 1) 配置依赖路径

`pom.xml` 使用 `systemPath` 引用本地文件，请按你的机器修改：

- `<Steam.path>`: SlayTheSpire 安装目录（需包含 `desktop-1.0.jar`）
- `<Steam.workshop>`: Steam Workshop 的 `646570` 目录（需包含 ModTheSpire/BaseMod 的 jar）

默认依赖关系是：

- `${Steam.path}/desktop-1.0.jar`
- `${Steam.workshop}/1605060445/ModTheSpire.jar`
- `${Steam.workshop}/1605833019/BaseMod.jar`

### 2) 编译

方法 A（推荐）：

```bash
mvn package
```

方法 B（不安装 Maven）：

```bash
javac Build.java
java Build
```

注意：`Build.java` 会自动使用当前项目目录，但 Steam 和 Workshop 路径仍需按你本机环境确认。

### 3) 安装

将 `target/CommunicationMod.jar` 复制到 SlayTheSpire 的 `mods` 目录，启动 ModTheSpire 并勾选：

- BaseMod
- Communication Mod

## HTTP API

服务启动后监听：`http://localhost:5000`

### 1) 获取状态

- 方法：`GET`
- 路径：`/state`

返回值包含（按当前实现）：

- `player`: `current_hp`, `max_hp`, `block`, `energy`, `gold`，以及可选 `powers`, `orbs`
- `relics`, `potions`
- `hand`, `draw_pile`, `discard_pile`, `exhaust_pile`
- `draw_pile_size`, `discard_pile_size`, `exhaust_pile_size`
- `monsters`（仅战斗阶段，且只包含存活怪）
- `floor`, `act`, `room_phase`
- `screen_type`, `choice_list`, `can_proceed`, `can_cancel`
- `is_end_turn_button_enabled`
- 地图事实字段（AI 无关的原始状态）：
  - `first_room_chosen`
  - `map_ascii`: 地图的 ASCII 文本渲染
  - `map_position`: 当前位置（楼层 + 从左往右第几个房间）
  - `map_choices_human`: 当前地图可选房间的人类可读列表
  - `map_nodes`: 全图节点列表（内部事实字段，仍含 `x/y/symbol/is_current/children`）
  - `current_map_node`: 当前节点内部事实字段
  - `current_map_choices`: 仅在 `screen_type=MAP` 时提供当前合法下一步节点的内部事实字段

说明：

- `hand` 中卡牌索引从 `0` 开始，可直接用于 `play.card_index`。
- `monsters` 的目标索引基于“存活怪顺序”，可用于 `target_index`。
- 地图展示层默认不再使用坐标，而使用 ASCII 地图与“第几个房间”的文案；`map_nodes/current_map_choices` 仅保留为内部事实字段。
- 若主线程未及时响应，请求可能返回超时错误。

### 2) 执行动作

- 方法：`POST`
- 路径：`/action`
- `Content-Type: application/json`

通用返回（入队成功）：

```json
{"status":"queued"}
```

支持的 `type`：

1. `play`
```json
{
  "type": "play",
  "card_index": 0,
  "target_index": 0
}
```

2. `end_turn`
```json
{
  "type": "end_turn"
}
```

3. `choose`（用于事件/奖励/地图/商店/休息点等选择）
```json
{
  "type": "choose",
  "choice_index": 0
}
```

4. `confirm` 或 `proceed`
```json
{
  "type": "confirm"
}
```

5. `cancel` 或 `skip`
```json
{
  "type": "cancel"
}
```

6. `potion`
```json
{
  "type": "potion",
  "potion_index": 0,
  "target_index": 0
}
```

7. `wait`（空操作）
```json
{
  "type": "wait"
}
```

动作约束：

- `play` / `end_turn` / `potion` 仅在战斗阶段生效。
- 投掷型药水需要 `target_index`。
- 非法索引或非法时机会在游戏日志输出错误，但 HTTP 侧不会同步返回执行失败。

### 3) 查询卡牌信息

- 路径：`/card_info`
- 支持两种调用：

方式 A（推荐）：

- 方法：`GET`
- 示例：`/card_info?id=Strike_R`

方式 B：

- 方法：`POST`
- 请求体：
```json
{
  "id": "Strike_R"
}
```

返回：对应卡牌的静态信息（名称、费用、类型、数值等），若找不到返回错误 JSON。

## 使用建议

- 每次执行动作前先调用一次 `/state`，使用最新 `choice_list` 和索引。
- 对于多步骤交互（如奖励、地图、商店），优先用 `screen_type + choice_list` 驱动决策。
- 若频繁请求 `/state`，建议客户端做重试与短间隔轮询，处理 `timeout`/`busy` 错误。

## 最近更新 (Changelog)

- 修复（2026-03-15）：修复了在地图的最后一步（`y == 14` / `y == 2`）准备进入 Boss 房间时，`getMapScreenNodeChoices()` 会返回空列表导致 AI 端卡死不断尝试 `proceed` 的问题。现在引入了底层鼠标点击 Patch (`DungeonMapPatch`)，能够为 AI 提供虚拟的 Boss 节点事件，并在选择后自动安全打通进入 Boss 的地图连线。
