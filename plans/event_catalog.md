# Event Catalog — 事件选项文字替换清单

> 目的: 列出所有 `choice_list` 中选项文本过于模糊(只有动词,无效果关键词)的事件,
> 以便在 `ChoiceScreenUtils.getEventScreenChoices()` 中替换为包含完整效果的文本。

> 当前状态（2026-07-28）: 本文前半部分保留为旧文字解析方案的调查记录。运行时已改为
> `GameState.event` 结构化语义；原版事件不再依赖替换按钮文字。本文末尾的覆盖矩阵是当前实现基线。

## 格式说明

每条记录格式:
- **事件类名** (Event ID): 用于 Java 侧匹配
- **选项索引 / 原始文字(推测)**: 按钮位置和可能的中文/英文文本
- **实际效果**: 从 Java 源码推断
- **正则可解析?**: 当前文本是否能被 `_parse_event_effects` 正确提取
- **替换文本**: 新文本,必须包含关键词(HP/gold/card/relic/curse/upgrade/remove/transform/duplicate)

Regex 匹配关键词参考:
- HP: `max hp`/`maximum hp`/`damage`/`lose hp`/`heal`/`restore`/`regain`
- Gold: `gold`
- 遗物: `relic`/`obtain`
- 诅咒: `curse`/`cursed`
- 卡牌: `card` + (`remove`/`purge`/`transform`/`upgrade`/`duplicate`/`copy`)
- 数值: 所有效果金额/HP 用 `_extract_first_int` (正则 `\d+`)

---

## 第一批: Exordium (第一层) 事件

### 1. LivingWall (Living Wall)

**当前选项** (INTRO 界面, 3 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Forget` / `遗忘` | 删除 1 张卡 |
| 1 | `Change` / `改变` | 变换 1 张卡 |
| 2 | `Grow` / `成长` | 升级 1 张卡 (需有可升级卡) |

**正则状态**: 全部无法解析。三个词都不包含任何效果关键词。

**替换文本**:
```
索引 0 → "[ Remove 1 Card ]"
索引 1 → "[ Transform 1 Card ]"
索引 2 → "[ Upgrade 1 Card ]"
```

---

### 2. Cleric (The Cleric) — 部分模糊

**当前选项** (INTRO 界面, 3 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Heal X HP for 35 Gold` / `治疗 X 点生命 (35 金币)` | 花 35 金币回复 25% HP |
| 1 | `Purify X Gold` / `净化 (50/75 金币)` | 花 50/75 金币删除 1 张卡 |
| 2 | `Leave` / `离开` | 无事发生 |

**正则状态**:
- 索引 0: 可解析 (`heal` + 数字 + `gold` → gain_hp + lose_gold)
- 索引 1: **部分可解析**。能匹配到 `gold` → lose_gold,但 `Purify`/`净化` 不含 `card`/`remove` 关键词,漏掉 card_remove 效果
- 索引 2: 不需要处理 (Leave 无效果)

**替换文本**:
```
索引 1 → "[ Lose X Gold Remove 1 Card ]"
```
(注意: X 在源码中是变量 purifyCost=50/75,需要在 Java 侧动态拼接)

---

### 3. GoldenWing (Golden Wing) — 需要替换

**当前选项** (INTRO 界面, 3 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Suffer X Damage Remove Card` | 受 7 伤害,之后可选删 1 张卡 |
| 1 | `Smash Gold` | 获得 50-80 金币 (需有≥10伤害的卡,否则灰掉) |
| 2 | `Leave` | 无事发生 |

**正则状态**: 索引 0 已拼接 damage 数值,但可能不含 `card`/`remove` 关键词。索引 1 可能不含 `gold` 关键词。

**替换文本**:
```
索引 0 → "[ Take 7 Damage Remove 1 Card ]"
索引 1 → "[ Gain 50-80 Gold ]"
```

---

### 4. ShiningLight (Shining Light) — 需要替换

**当前选项**:

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Enter Light X Damage` | 受 20%/30% maxHP 伤害,随机升级 2 张卡 (无可升级卡时灰掉) |
| 1 | `Leave` | 无事发生 |

**正则状态**: 索引 0 已拼接 damage 数值,但可能缺少 `upgrade` 关键词。

**替换文本**:
```
索引 0 → "[ Take X Damage Upgrade 2 Random Cards ]"
```

---

### 5. Mushrooms — 需要特殊处理

**当前选项**:

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Fight` / `战斗` | 进入战斗(打蘑菇) |
| 1 | `Heal X HP` + Parasite | 回复 25% HP,获得 Parasite 诅咒 |

**问题**: 索引 0 进入战斗,local model 无法评估战斗的风险/收益,无法与索引 1 (治疗+诅咒) 做合理比较。

**结论**: 此事件不能仅靠文本替换解决,需要特殊处理。留待后续设计。

---

### 6. BigFish — 已包含数值

**当前选项**:

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Heal X HP` / `治疗 X` | 回复 1/3 maxHP |
| 1 | `Max HP +5` / `最大生命 +5` | Max HP +5 |
| 2 | `Relic + Regret` / `遗物 + 悔恨` | 获得随机遗物 + Regret 诅咒 |

**正则状态**: 全部可解析。
**建议**: 不需要替换。

---

### 7. ScrapOoze — 已包含数值

**当前选项**: 含 damage 和 relic chance 数值,可解析。
**建议**: 不需要替换。

---

### 8. DeadAdventurer — 需要特殊处理

**当前选项**:

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Search X%` | 25%(A15+为35%) 概率打 Lagavulin,否则获得 30 金币或随机遗物 |
| 1 | `Leave` | 无事发生 |

**问题**: 同 Mushrooms,local model 无法评估战斗概率风险,不能仅靠文本替换解决。

**结论**: 需要特殊处理,留待后续设计。

---

### 9. GoldenIdolEvent — 不需要替换

INTRO 界面 2 个按钮: `Take Golden Idol` (获得 Golden Idol 遗物) / `Leave`。已含遗物名,可解析。

---

### 10. GoopPuddle — 不需要替换

2 个按钮: `Take 75 Gold 11 Damage` (受 11 伤害得 75 金币) / `Lose X Gold` (丢 X 金币离开)。文本已拼接 gold 和 damage 数值,可解析。

---

### 11. Sssserpent — 不需要替换

Event ID: `Liars Game`。2 个按钮: `Agree: Gain X Gold + Doubt` (获得金币 + Doubt 诅咒) / `Disagree` (离开)。文本含 gold 数值 + Doubt 诅咒提示,可解析。

---

## 第二批: Shrine (神龛) 事件

### 12. Bonfire (Bonfire Elementals)

**当前选项** (INTRO 界面, 1 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Offer` / `献上` | 打开 GRID 选择 1 张卡删除,根据稀有度给奖励 |

**效果详情** (根据献上卡牌稀有度):
- Curse → 获得 Spirit Poop 遗物
- Basic → 无额外奖励
- Common/Special → 回复 5 HP
- Uncommon → 回复到满血
- Rare → 回复到满血 + Max HP +10

**正则状态**: 完全无法解析。`Offer`/`献上` 不含任何效果关键词。

**替换文本**:
```
索引 0 → "[ Remove 1 Card Gain Rewards Based on Rarity ]"
```
后续 GRID 打开后由 GRID 通用拦截处理。

---

### 13. PurificationShrine (Purifier)

**当前选项** (INTRO 界面, 2 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Pray` / `祈祷` | 打开 GRID,删除 1 张卡 |
| 1 | `Leave` / `离开` | 无事发生 |

**正则状态**: 索引 0 完全无法解析。

**替换文本**:
```
索引 0 → "[ Remove 1 Card ]"
索引 1 → "[ Leave ]"
```

---

### 14. UpgradeShrine (Upgrade Shrine)

**当前选项** (INTRO 界面, 2 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Pray` / `祈祷` | 打开 GRID,升级 1 张卡 (需有可升级卡) |
| 1 | `Leave` / `离开` | 无事发生 |

**正则状态**: 索引 0 完全无法解析。

**替换文本**:
```
索引 0 → "[ Upgrade 1 Card ]"
索引 1 → "[ Leave ]"
```

---

### 15. Transmogrifier (Transmorgrifier)

**当前选项** (INTRO 界面, 2 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Transform` / `变换` | 打开 GRID,变换 1 张卡 |
| 1 | `Leave` / `离开` | 无事发生 |

**正则状态**: 索引 0 包含 `transform` 关键词,配合 `card` 匹配可能可解析。但如果实际本地化文字只写了 "Transform" 一个词,那么 `_parse_event_effects` 的子句分割后会在 `card` + `transform` 分支中查找,需要有 `card` 关键词。单独 "Transform" 不含 `card`,匹配不到。

**替换文本**:
```
索引 0 → "[ Transform 1 Card ]"
索引 1 → "[ Leave ]"
```

---

### 16. Duplicator

**当前选项** (INTRO 界面, 2 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Duplicate` / `复制` | 打开 GRID,复制 1 张卡 |
| 1 | `Leave` / `离开` | 无事发生 |

**正则状态**: 同 Transmogrifier。需要 `card` + `duplicate`/`copy` 关键词。

**替换文本**:
```
索引 0 → "[ Duplicate 1 Card ]"
索引 1 → "[ Leave ]"
```

---

### 17. AccursedBlacksmith (Accursed Blacksmith)

**当前选项** (INTRO 界面, 3 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Forge` / `锻造` | 打开 GRID,升级 1 张卡 (需有可升级卡) |
| 1 | `Rummage` / `翻找` | 获得 Pain 诅咒 + WarpedTongs 遗物 |
| 2 | `Leave` / `离开` | 无事发生 |

**正则状态**:
- 索引 0: `Forge`/`锻造` 不含 `card`/`upgrade` 关键词 → 无法解析
- 索引 1: `Rummage`/`翻找` 不含 `curse`/`relic` 关键词 → 无法解析
- 索引 2: 不需要处理

**替换文本**:
```
索引 0 → "[ Upgrade 1 Card ]"
索引 1 → "[ Obtain WarpedTongs Relic Gain Pain Curse ]"
索引 2 → "[ Leave ]"
```

---

### 18. FountainOfCurseRemoval (Fountain of Cleansing)

**当前选项** (INTRO 界面, 2 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Pray` / `祈祷` | 移除所有非特殊诅咒 (AscendersBane/CurseOfTheBell/Necronomicurse 除外) |
| 1 | `Leave` / `离开` | 无事发生 |

**正则状态**: 索引 0 完全无法解析。

**替换文本**:
```
索引 0 → "[ Remove All Curses ]"
索引 1 → "[ Leave ]"
```

---

### 19. GoldShrine (Golden Shrine) — 已包含数值

**当前选项** (INTRO 界面, 3 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Pray X Gold` / `祈祷 X 金币` | 获得 100/50 金币 |
| 1 | `Desecrate` + Regret / `亵渎` + 悔恨 | 获得 275 金币 + Regret 诅咒 |
| 2 | `Leave` / `离开` | 无事发生 |

**正则状态**: 索引 0 拼接了 gold 数值和 `Gold` 关键词,可解析; 索引 1 附带 Regret 卡牌提示,但 "Desecrate" 动词本身不含效果关键词。

**替换文本**:
```
索引 1 → "[ Gain 275 Gold Gain Regret Curse ]"
```

---

### 20. Lab — 无法评估效果

**当前选项**:

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Enter` / `进入` | 获得 2-3 瓶随机药水 |

**正则状态**: 无法解析 (关键词不含 `potion`)。
**当前处理**: Lab 事件点击后会直接进入 combat reward 界面(药水奖励),由 COMBAT_REWARD 硬编码处理,因此事件选项本身不需要决策。
**建议**: 不需要替换。

---

### 21. WomanInBlue (The Woman in Blue) — 已包含数值

**当前选项**: 含 `20 Gold`, `30 Gold`, `40 Gold` 价格和药水数量提示,可解析。
**建议**: 不需要替换。

---

### 22. WeMeetAgain — 已包含物品名

**当前选项**: 拼接了药水名/金币数/卡牌名,包含具体信息。
**建议**: 不需要替换。

---

### 23. Nloth (N'loth) — 已包含遗物名

**当前选项**: 拼接了遗物名 + 交换提示,包含 `relic` 相关信息。
**建议**: 不需要替换。

---

### 24. FaceTrader — 后续界面已有数值

**当前选项**: INTRO 界面只有 `Enter`,后续界面有 damage/gold 数值。
**建议**: INTRO 按钮不需要替换(直接 proceed)。

---

### 25. NoteForYourself — 已包含卡牌名

**当前选项**: 拼接了卡牌名,可解析。
**建议**: 不需要替换。

---

### 26. Designer — 已包含详细价格和效果

**当前选项**: 含 `Adjust X Gold (Upgrade)`, `Clean Up X Gold (Remove)`, `Full Service X Gold (Remove+Upgrade)` 等详细文本。
**建议**: 不需要替换。

---

### 27. GremlinMatchGame / GremlinWheelGame — 专用控制器

两个事件不依赖普通对话按钮完成全部流程：

- `GremlinMatchGame` 在 `PLAY` 阶段输出稳定槽位，仅暴露当前翻开的牌面。Python 保存已见槽位，优先完成已知配对，否则翻开未知槽位；动画结算期间等待。
- `GremlinWheelGame` 在转盘按钮出现时输出专用 `spin` 选项。`COMPLETE` 阶段按已经确定的 `result` 输出金币、随机遗物、满血、Decay、删牌 GRID 或 HP 损失语义。
- 两者的 INTRO/说明/结束按钮仍沿用 `choose.action_index`，未知状态安全降级。

---

## 第三批: City (第二层) 事件

### 28. BackToBasics (Back to Basics)

**当前选项** (INTRO 界面, 2 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Elegance` / `优雅` | 打开 GRID,删除 1 张卡 |
| 1 | `Simplicity` / `简约` | 升级所有 Strike/Defend 卡牌 |

**正则状态**: 全部无法解析。

**替换文本**:
```
索引 0 → "[ Remove 1 Card ]"
索引 1 → "[ Upgrade All Starter Strikes and Defends ]"
```

---

### 29. CursedTome (Cursed Tome) — 待定

**INTRO 界面仅有 2 个按钮**:
- 索引 0: `Read` / `阅读` — 选择后自动翻页(PAGE_1/2/3 每页只有 1 个"继续"按钮,无需 AI 决策),累计受 1+2+3=6 伤害,到达 LAST_PAGE
- 索引 1: `Leave` / `离开` — 直接离开,无事发生

**LAST_PAGE 界面有 2 个按钮**:
- 索引 0: `Take X Damage Obtain Book` — 已拼接 damage 数值,可解析 (受 10/15 伤害,获得随机书籍遗物)
- 索引 1: `Stop` / `停止` — 受 3 伤害,无遗物

**问题**: INTRO 的 `Read` 替换为 `[ Take 6 Damage Read Book Get Random Book Relic ]` 后,本地模型 regex 解析器仍无法提取"获得书籍遗物"的价值(不认识 "Book Relic" 对应 Necronomicon/Enchiridion/Nilry's Codex)。此外,LAST_PAGE 选择承受大伤害换遗物的价值也无法被 regex 模型正确评估。

**结论**: 此事件需要特殊处理,不能仅靠文本替换解决。留待后续单独设计。

---

### 30. TheLibrary (The Library)

**当前选项** (INTRO 界面, 2 个按钮):

| 索引 | 原始文字(推测) | 效果 |
|------|---------------|------|
| 0 | `Read` / `阅读` | 从 20 张随机卡中选 1 张获得 |
| 1 | `Sleep X HP` / `睡觉 X 生命` | 回复 20%/33% maxHP |

**正则状态**:
- 索引 0: `Read`/`阅读` → 无法解析
- 索引 1: 拼接了 heal 数值 → 可解析

**替换文本**:
```
索引 0 → "[ Choose 1 Card to Obtain ]"
索引 1 → `Heal X HP` (保持原文本,已含数值,可解析)
```

---

### 其他 City 事件 — 状态评估

| 事件 | 状态 |
|------|------|
| Addict | 拼接了价格和遗物/诅咒提示,基本可解析 |
| Beggar | 需要测试 |
| Colosseum | 战斗事件,需要测试 |
| DrugDealer | 需要测试 |
| ForgottenAltar | 拼接了 HP/MaxHP 数值,可解析 |
| Ghosts | 拼接了 HP loss 数值和 Apparition 卡牌,可解析 |
| KnowingSkull | 拼接了 HP cost 数值和奖励类型,可解析 |
| MaskedBandits | 需要测试 |
| Nest | 需要测试 |
| TheJoust | 需要测试 |
| TheMausoleum | 拼接了 curse 百分比和 Writhe 卡牌,可解析 |
| Vampires | 需要测试 |

---

## 第四批: Beyond (第三层) 事件

需要逐一测试确认,但优先级较低。目前已知可能需要替换的:

| 事件 | 可能的问题 |
|------|-----------|
| Falling | 选项可能是 `Land`/`着陆` 之类的模糊词,效果是掉一张卡 |
| MindBloom | 选项含具体效果描述,可能不需要 |
| MoaiHead | 选项含 Max HP 数值,可能不需要 |
| MysteriousSphere | 选项可能是 `Open`/`打开`,需要替换 |
| SecretPortal | 选项可能是 `Enter`/`进入`,需要替换 |
| SensoryStone | 选项含具体效果,可能不需要 |
| WindingHalls | 选项可能是 `Writhe`/`挣扎`(获得 Madness),需要替换 |

---

## 实施优先级

1. **P0 (必须)**: LivingWall, Bonfire, PurificationShrine, UpgradeShrine, Transmogrifier, Duplicator, AccursedBlacksmith — 这些事件直接触发 GRID,AI 必须正确识别效果才能决策
2. **P1 (高)**: Cleric(索引1), BackToBasics, FountainOfCurseRemoval, TheLibrary(索引0) — 部分选项模糊
3. **P2 (中)**: CursedTome — 多步骤事件,每个步骤选项都模糊
4. **P3 (低)**: Beyond 事件和其他尚未测试的事件

## 实施注意事项

- Java 侧需要区分事件的不同界面状态(同一事件 ID,不同 phase/screen 的选项索引含义不同)
- 替换时需要考虑: 事件 ID + 当前按钮数量 + 选项索引 联合定位
- 部分替换文本含动态数值(如 Cleric 的 purifyCost),需要在 Java 侧获取实际值后拼接
- `_parse_event_effects` 只用于旧服务端兼容；原版事件必须使用结构化 `event.choices[].outcomes[].effects`

---

## 结构化事件覆盖矩阵

状态含义：`D`=确定效果交给价值网络，`C`=战斗/随机/承诺型复杂选择，`F`=无决策空间自动推进。
禁用按钮仍出现在 `event.choices`，但 `action_index=null`；`button_index` 始终保持游戏原始位置。

### Exordium（11/11）

| 类 | 已覆盖阶段 | 类型与关键语义 |
|---|---|---|
| BigFish | INTRO, RESULT | C: 回血、Max HP、随机遗物+Regret |
| Cleric | 0, 1 | D/F: 35 金治疗、动态净化费用+删牌 |
| DeadAdventurer | INTRO, SUCCESS, FAIL, ESCAPE | C/F: 动态遇敌概率及三类搜索结果 |
| GoldenIdolEvent | 0, 1, 2 | C/D/F: 取得遗物后必须承担 Injury/HP/Max HP 之一 |
| GoldenWing | INTRO, PURGE, MAP | D/F: 伤害+删牌、运行时已确定的 50-80 金、GRID |
| GoopPuddle | INTRO, RESULT | D/F: 伤害+75 金或动态失去金币 |
| LivingWall | INTRO, RESULT | D/F: 删除、随机变换、升级 GRID |
| Mushrooms | 0, 1, 2 | C/F: 战斗或治疗+Parasite |
| ScrapOoze | 0, 1 | C/F: 动态伤害和遗物成功概率 |
| ShiningLight | INTRO, COMPLETE | C/F: 动态伤害+随机升级两张 |
| Sssserpent | INTRO, COMPLETE | D/F: 动态金币+Doubt |

### City（15/15）

| 类 | 已覆盖阶段 | 类型与关键语义 |
|---|---|---|
| Addict | 0, 1 | C/F: 85 金或 Shame 换随机遗物 |
| BackToBasics | INTRO, COMPLETE | D/F: 删除一张或升级全部基础 Strike/Defend |
| Beggar | INTRO, GAVE_MONEY, LEAVE | D/F: 75 金后进入删牌 GRID |
| Colosseum | INTRO, FIGHT, POST_COMBAT, LEAVE | C/F: 两段战斗与中途离开 |
| CursedTome | INTRO, PAGE_1/2/3, LAST_PAGE, END | C/F: 阅读承诺、逐页 HP 损失、书籍遗物终局 |
| DrugDealer | 0, 1 | C/F: J.A.X.、随机变换两张、MutagenicStrength |
| ForgottenAltar | 0, 1 | D/F: Golden Idol 交换、HP/Max HP、Decay |
| Ghosts | 0, 1/2 | D/F: 动态 Max HP 损失及 3/5 张 Apparition |
| KnowingSkull | INTRO_1, ASK, COMPLETE | C/F: 动态 HP 费用和药水/金币/卡牌奖励 |
| MaskedBandits | INTRO, PAID_1/2/3, END | C/F: 失去全部金币或战斗 |
| Nest | 0, 1, 2 | D/F: 动态金币或 6 HP+RitualDagger |
| TheJoust | HALT, EXPLANATION, PRE_JOUST, JOUST, COMPLETE | C/F: 两类赌注的真实胜率和收益 |
| TheLibrary | 0, 1 | C/F: 生成卡池选牌或动态治疗 |
| TheMausoleum | INTRO, RESULT | C/F: 动态 Writhe 概率+随机遗物 |
| Vampires | 0, 1/2 | D/F: Max HP/Blood Vial 代价，移除基础 Strike 并固定获得 5 Bite |

### Beyond（9/9）

| 类 | 已覆盖阶段 | 类型与关键语义 |
|---|---|---|
| Falling | INTRO, CHOICE, RESULT | D/F: 按实际卡 ID 失去 Skill/Power/Attack |
| MindBloom | INTRO, FIGHT, LEAVE | C/F: Boss 战、全升级+Mark、999 金或治疗分支 |
| MoaiHead | 0, 1 | D/F: 满血+Max HP 代价或 Golden Idol 换 333 金 |
| MysteriousSphere | INTRO, PRE_COMBAT, END | C/F: 双 Orb Walker 战斗+稀有遗物 |
| SecretPortal | INTRO, ACCEPT, LEAVE | D/F: 跳至 Boss 或离开 |
| SensoryStone | INTRO, INTRO_2, ACCEPT, LEAVE | C/F: 0/5/10 HP 换 1/2/3 无色卡奖励 |
| SpireHeart | 全叙事阶段 | F: 强制叙事、结局或死亡跳转 |
| TombRedMask | INTRO, RESULT | D/F: 已有面具得 222 金，否则全部金币换 Red Mask |
| WindingHalls | 0, 1, 2 | D/F: HP+Madness、治疗+Writhe、Max HP 损失 |

### Shrines（17/17）

| 类 | 已覆盖阶段 | 类型与关键语义 |
|---|---|---|
| AccursedBlacksmith | 0, 2 | D/F: 升级 GRID 或 Pain+WarpedTongs |
| Bonfire | INTRO, CHOOSE, COMPLETE | F/D: 献牌 GRID 及按稀有度计算奖励 |
| Designer | INTRO, MAIN, DONE | C/F: 动态费用、随机模式；Full Service 选删一张并随机升级一张 |
| Duplicator | 0, 2 | D/F: 复制 GRID |
| FaceTrader | INTRO, MAIN, RESULT | C/F: 金币+伤害或随机面具 |
| FountainOfCurseRemoval | 0, 1 | D/F: 移除诅咒并排除三张特殊诅咒 |
| GoldShrine | INTRO, COMPLETE | D/F: 动态金币或 275 金+Regret |
| GremlinMatchGame | INTRO, RULE_EXPLANATION, PLAY, CLEAN_UP, COMPLETE | C/F: 稳定槽位、可见牌面记忆、已知配对优先 |
| GremlinWheelGame | INTRO, SPIN, COMPLETE, LEAVE | C/F: 专用 spin 动作及已确定结果的结构化后续 |
| Lab | INTRO, COMPLETE | C/F: 2/3 个随机药水奖励 |
| Nloth | 0, 1 | D/F: 动态遗物交换；已有 Gift 时获得 Circlet |
| NoteForYourself | INTRO, CHOOSE, COMPLETE | C/F: 固定卡与玩家选择卡交换 |
| PurificationShrine | INTRO, COMPLETE | D/F: 删除 GRID |
| Transmogrifier | INTRO, COMPLETE | C/F: 随机变换 GRID |
| UpgradeShrine | INTRO, COMPLETE | D/F: 升级 GRID |
| WeMeetAgain | INTRO, COMPLETE | D/F: 动态药水/金币/卡牌换指定遗物 |
| WomanInBlue | INTRO, RESULT | C/F: 20/30/40 金换药水；A15+ 离开损失 5% Max HP |

## 运行时约束

- `semantics_status=UNKNOWN` 时不得推测确定效果，必须进入文字+LLM 兜底。
- `decision_kind=FORCED` 仅在当前阶段恰好一个启用按钮时自动执行。
- 随机变换、随机奖励、战斗和迷你游戏均为 `COMPLEX`，本期不伪造期望结果。
- 结构化选择使用 `action_index` 执行；`button_index` 仅用于追踪源事件按钮。
- 所有进入 GRID 的事件保存事件 ID、阶段、用途、数量和目标，离开事件或换楼层时清理。
