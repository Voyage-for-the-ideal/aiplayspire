# TODO: Event Regex + GRID Testing

> 创建日期: 2026-05-07 | 更新日期: 2026-05-14
> **每个任务均独立可验证**,建议按顺序完成。

---

## 背景

AI bot 的 `choose_action` 决策链路对全部 14 个 `ChoiceType` screen_type 的拦截现状:

**已拦截 (9 个)**:
- `NONE` (战斗) → WAIT,等待外部 BattleAiMod
- `REST` → `_handle_rest_room` (价值网络)
- `EVENT` → `_get_model_event_decision` (价值网络,需先正则解析选项效果)
- `CHEST` → 硬编码自动打开
- `COMBAT_REWARD` → 硬编码优先级: 遗物 > 金币 > 药水 > 卡牌
- `SHOP_SCREEN` → `_get_model_shop_decision` (价值网络贪心搜索)
- `CARD_REWARD` → `_get_model_card_decision` (价值网络)
- `BOSS_REWARD` → `_get_model_boss_reward_decision` (价值网络) ✅
- `GRID` → 通用拦截,支持 purge/upgrade/transform/duplicate ✅

**未拦截/部分拦截 (5 个)**:
- `MAP` → 掉到 LLM (prompt 中含 BFS 距离,LLM 可接受)
- `HAND_SELECT` → 掉到 LLM
- `SHOP_ROOM` → 仅拦截从 `SHOP_SCREEN` 返回后自动 proceed;初次进入商店房间掉到 LLM
- `GAME_OVER` → 掉到 LLM/fallback
- `COMPLETE` → 掉到 LLM/fallback

**剩余问题**:
1. 大量事件选项文本只有动词("献上"/"Offer"/"Pray"/"Drink"),不含效果关键词,`_parse_event_effects` 正则匹配失败 → 采用 Java 侧打补丁替换事件选项文字方案
2. GRID 通用拦截已实现,需要按场景逐一测试验证

---

## 任务 1 : 事件选项文字替换 — Java 侧补丁

### 问题

部分事件选项文本只有模糊动词("献上"/"Offer"/"Pray"/"Drink"/"Touch"),不含
HP/金币/卡牌/遗物等效果关键词,导致 `_parse_event_effects` 正则匹配失败,
进而价值网络无法正确评估选项。

### 方案

在 Java 侧 `ChoiceScreenUtils.getEventScreenChoices()` 中,打完游戏原版
选项文字后,替换为包含完整效果描述的自定义文本。这样 Python 侧的正则解析器
可以直接从替换后的 choice_list 中提取所有效果,无需修改 Python 代码也无需
序列化事件 body 文本。

### 实现位置

**文件**: `StSCommunicationMod/src/main/java/com/example/communicationmod/ChoiceScreenUtils.java`

`getEventScreenChoices()` 方法 (~221-233行),在 `b.msg` 加入 choices 之前进行替换。

### 流程

1. 在 `plans/event_catalog.md` 中列出所有需要替换的事件及选项
2. 为每个模糊选项编写包含完整效果的替换文本,格式与现有正则兼容
   (如 `"[ Lose 75 Gold Remove 1 Card ]"` 而非只有 "Purify")
3. 在 `getEventScreenChoices()` 中加一个映射表,对匹配的事件ID+选项索引进行替换
4. 验证: 进入各事件,观察 Python 控制台输出的事件选项 effects 是否正确

### 注意事项

- 替换仅在 `choice_list` 序列化时生效,不影响游戏 UI 显示
- 事件 ID 通过 `AbstractDungeon.getCurrRoom().event.getClass().getSimpleName()` 获取

---

## 任务 2: GRID 通用拦截 — 场景测试

### 背景

GRID 通用拦截已实现 (commit `7e73fda`),支持 4 种目的:
- **purge**: 价值网络评估每张卡,rank_cards_for_purpose 选最差卡删除
- **upgrade**: 选最佳卡升级
- **transform**: 选最差卡变换(排除诅咒)
- **duplicate**: 选最佳卡复制

Java 侧通过反射读取 `forPurge`/`forUpgrade`/`forTransform` 字段 + confirm 按钮
文字,生成 `grid_purpose` 和 `grid_num_cards`。

### 测试方法

启动游戏 + CommunicationMod + sts_ai_framework,触发以下场景,
观察控制台输出确认:
1. `grid_purpose` 是否正确识别
2. 目标卡牌选择是否符合预期
3. 多卡选择 (Empty Cage 2张、Astrolabe 3张) 是否正确循环选择 + confirm

### 测试场景清单

| # | 场景 | grid_purpose | num_to_select | 验证点 |
|---|------|-------------|---------------|--------|
| 1 | Bonfire Spirits 献上 | purge | 1 | 选最差卡删除 |
| 2 | Living Wall Forget | purge | 1 | 选最差卡删除 |
| 3 | Cleric 净化(付费) | purge | 1 | 选最差卡删除 |
| 4 | Designer 移除卡牌 | purge | 1 | 选最差卡删除 |
| 5 | GoldenWing 删除 | purge | 1 | 选最差卡删除 |
| 6 | BackToBasics 删除 | purge | 1 | 选最差卡删除 |
| 7 | PurificationShrine 净化 | purge | 1 | 选最差卡删除 |
| 8 | Empty Cage 遗物 | purge | 2 | 选 2 张最差卡删除,验证 confirm |
| 9 | UpgradeShrine 强化 | upgrade | 1 | 选最佳卡升级 |
| 10 | AccursedBlacksmith 锻造 | upgrade | 1 | 选最佳卡升级 |
| 11 | LivingWall Grow | upgrade | 1 | 选最佳卡升级 |
| 12 | Transmogrifier 变换 | transform | 1 | 选最差卡变换(排除诅咒) |
| 13 | LivingWall Change | transform | 1 | 选最差卡变换(排除诅咒) |
| 14 | Astrolabe 遗物 | transform | 3 | 选 3 张卡变换,验证 confirm |
| 15 | Duplicator 复制 | duplicate | 1 | 选最佳卡复制 |
| 16 | Dolly's Mirror 遗物 | duplicate | 1 | 选最佳卡复制 |
| 17 | 营火敲牌(回归) | upgrade | 1 | intended_smith_card 传递正常 |
| 18 | 营火删牌(回归) | purge | 1 | intended_purge_card 传递正常 |
| 19 | 商店删牌(回归) | purge | 1 | intended_purge_card 传递正常 |

---
