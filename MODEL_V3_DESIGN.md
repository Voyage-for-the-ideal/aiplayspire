# 下一代模型设计方案：v3 地图条件化统一价值网络

> 创建日期: 2026-09-11 | 更新日期: 2026-09-21 | 状态: **纠偏方案已实现**（数据层 `map_context.py`、模型层 [MAP]/[CHARACTER]/[KEYS] token + key 头、
> 目标：让非战斗决策模型覆盖 **选路、选牌、商店、事件、钥匙**（含带火精英），
> 并通过「后续地图上下文」让模型学会 **为未来投资**（先拿牌/攒钱/惜血，兑现于后面的商店、精英、钥匙）。

---

## 0. TL;DR

1. **不推倒重来**：v2 的 Set Transformer + bucket-hazard + Heart 头已验证有效，v3 保留其价值语义
   （`V(S) = E[终止楼层] + HEART_WIN_BONUS_FLOORS × P(Heart)`，标签契约 `bucket-hazard-v1` 不变），只扩输入与辅助头。
2. **一个 backbone，五类决策一个入口**：所有非战斗决策统一为「枚举候选 → 局部模拟下一状态 S′ → V(S′) → argmax」；
   MAP 决策不再走手工权重，改为 **限定深度 expectimax 树搜索 + V(S) 截断**（把战斗侧「真引擎 + 树搜索 + 价值截断」的成功模式复刻到地图上）。
3. **地图上下文 token 化**：新增 `[CHARACTER]`（四角色稳定编码）、`[MAP]`（当前幕全图节点 token，含 burning 标记）和 `[KEYS]`，
   并保留 history 视野；不把 router 的临时计划注入价值模型。选牌模型能「看见」后面有商店/带火精英/宝箱。
4. **钥匙显式建模，退役硬规则**：Ruby(休息点 Recall)/Emerald(带火精英)/Sapphire(宝箱) 三来源全部进入候选集；
   钥匙价值由 P(Heart) 头端到端学出，另加 key-progress 辅助头加速收敛。`choice_mixin.py` 里「Act3 强制 Recall」「CHEST 自动打开」等硬规则逐个替换。
5. **数据分三层**：本地月度归档人类 run（约 2,000 万条，含 `seed_played`）→ **种子→地图重建（✅ 已实现并验证 99.97%，见 §4.2）**
   给全部归档 run 配上真图（含带火精英位置），地图 token 不必等自对弈 → 自对弈 run（分布适配 + 决策候选 dump + ε 分支）。
   战斗模拟器（scumthespire）是推理期特征，非训练数据。迭代 2–3 轮 expert-iteration 式自举。
6. **验收**：除常规离线校准/在线 A/B 外，专设「投资探针」测试集（商店投资、精英惜血、钥匙时机三个对照组），
   直接量化「模型是否意识到可以为未来投资」。

---

## 1. 需求与现状差距

### 1.1 v3 必须覆盖的决策

| 决策 | 现状 (v2) | v3 目标 |
|---|---|---|
| MAP 选路 | `routing/` 手工权重规划器（6 层滚动视野 + 规则分），MAP 不走 LLM | 学习化 expectimax 搜索，与 V 同源；钥匙路线不再是规则过滤 |
| CARD_REWARD / BOSS_REWARD / GRID | 价值网络候选打分 | 不变 + 获得地图上下文（投资未来） |
| SHOP_SCREEN | 价值网络贪心搜索 | 不变 + 地图上下文（「后面还有没有商店」直接影响买/攒） |
| EVENT | 正则解析效果 + 价值网络 | 不变 + 地图上下文（「2 层后是带火精英」→ 惜血） |
| REST | 价值网络；Act3 缺 Ruby 时**硬规则强制 Recall**（`choice_mixin.py:40-47`） | Recall 进入候选集，价值网络自行权衡（模型 key-aware 后退役硬规则） |
| CHEST | **硬编码自动打开** | 激活钥匙后变为真决策：拿 Sapphire 钥匙 vs 拿遗物，两候选进 V(S′) |
| 钥匙/带火精英 | routing 里有 `burning` 标记与 key feasibility 过滤；模型零感知 | 地图 token 携带 burning；钥匙状态入全局特征；「早拿 vs 晚拿」由学习值回答 |

### 1.2 机制事实（本次调研核实）

来源：[wiki.gg Mechanics](https://slaythespire.wiki.gg/wiki/Mechanics)、[wiki.gg Elites](https://slaythespire.wiki.gg/wiki/Elites)、[fandom Keys](https://slay-the-spire.fandom.com/wiki/Keys)。

- **Ruby Key**：任意休息点选 **Recall**（取代 Rest/Smith/Dig 等）。每个休息点都出现，直到拿取。
- **Emerald Key**：击败**带火精英（super/burning elite）**。地图上火焰图标由**系统标记、非玩家选择**；
  未拿取前**每幕出现一个**；带火精英更强（wiki 记载约 +25% 最大生命且随幕数成长，精确数值可在本地 `cardcrawl/` 反编译源码核对）。
- **Sapphire Key**：任意宝箱，**取代箱内遗物**；拿取后本 run 后续宝箱变空。
- 三钥匙集齐且击败 Act 3 Boss 后才能进 Act 4 打 Heart；hazard 端点 57 楼覆盖了 Heart 通道。

设计含义：
1. 「点火」不是玩家动作，**钥匙决策 = 路线决策（绕不绕带火精英）+ 休息点三选一（Recall 与否）+ 宝箱二选一（遗物与与否）**。
   协议已有 `KeyState`（`models.py:60`）与 `MapNodeState.burning`（`models.py:105`），不需要新协议字段就能表达决策输入。
2. 「早拿 vs 晚拿」是真实的价值权衡：Act 1 带火精英更弱（少损失），Act 3 更强（多了先发育的机会）。
   规则写不清（取决于卡组强度、血量、路线成本），正是该交给学习值的问题。

### 1.3 v2 的结构性局限

1. **模型不知道地图**：输入只有 `[CLS]+[GLOBAL]+[BOSS]+items`。选牌/商店/事件决策全部「近视」——
   无法表达「先选这张牌、4 层后商店里买配件」「事件掉 7 HP，但 3 层后是带火精英，惜血」。
2. **两套价值观**：MAP 用手工权重（`routing/evaluator.py` 全是拍出来的系数），其余决策用 V(S)。
   两者对同一状态的评价可能冲突（如 V 认为卡组战力强该打精英，planner 的 risk 权重仍偏保守）。
3. **钥匙靠硬规则兜底**：`choice_mixin.py:40-42` 注释原文即承认「当前价值网络没有钥匙特征，Recall 必须留在硬规则里」。
4. **数据集无地图**：v2 训练数据（heart-rate.net 人类 run 重放）只有 `path_per_floor`（走过的房间序列），没有全图、没有钥匙字段
   ——v3 以 §4.2 种子→地图重建补齐（已验证 99.97%），钥匙状态亦可部分推断（Ruby←RECALL，Emerald←带火精英是否在已走路径上）。

---

## 2. 网络调研结论

### 2.1 Slay the Spire AI 现状

- **无公开的「学习型全流程」方案**。[ForgottenArbiter](https://forgottenarbiter.github.io/about/) 的
  [Communication Mod](https://github.com/ForgottenArbiter/CommunicationMod) / [spirecomm](https://github.com/ForgottenArbiter/spirecomm)
  生态里，首个全随机种子通关的 AI 是**硬编码策略**（无 ML）；"BaalorBot" 实为频道聊天机器人（[Spireblight](https://github.com/Spireblight/Spireblight)），不玩游戏。
- 近期 StS 智能体研究转向 LLM 路线：[arXiv:2505.17673](https://arxiv.org/abs/2505.17673)（bottom-up skill evolution）用视觉感知 + 鼠标操作评估 StS/Civ V 智能体，
  与我们的结构化状态 + 价值网络路线互补，可作对比引用。
- 学术侧最直接的两份先行工作：
  - **[Porenius & Hansson 2021（DIVA 本科论文）](https://www.diva-portal.org/smash/get/diva2:1565751/FULLTEXT02.pdf)**：用 spirelog.com 的 ~17.2 万行人类数据训练 MLP（24 个输入特征，含路径房间 one-hot），**枚举当前位置到 boss 的所有整条路径、逐条预测胜率取 argmax，逐层重规划**；用户实验中 AI 辅助选路 3 对里 2 对胜过人类；结论 Elite 与 Campfire 是最重要的房间类型。→ 验证了「学习值 + 路径枚举」做选路的可行性，也给出了「整条路径打分」这一基线形态。
  - **Alex Driedger 系列**：用 ANN 预测每场战斗的掉血，再以「有/无这张卡」的反事实评估选牌。→ 验证了「用战斗后果反推非战斗决策」；我们的战斗树搜索（scumthespire）是它的精确版模拟器，天然更强。

### 2.2 相邻游戏的方法论

- **Balatro 生态（2024–2025）**：[balatro-gym](https://github.com/cassiusfive/balatro-gym)、
  [jackdaw-balatro](https://github.com/TylerFlar/jackdaw-balatro)（1:1 Python 重实现 + Gym 接口）+ maskable-PPO 是主流；
  LLM 智能体（[balatrollm](https://github.com/coder/balatrollm)）为另一极。启示：**重写引擎 + RL** 适合战斗复杂度高的游戏；
  StS 战斗我们已用真引擎树搜索解决，非战斗决策稀疏、状态可完整结构化、样本少，**离线 value function + 候选模拟**比 PPO 更匹配（不需要不可微战斗的梯度近似）。
- **Decision Transformer**（[Chen et al., NeurIPS 2021](https://proceedings.neurips.cc/paper/2021/hash/7f489f642a0ddb10272b5c31057f0663-Abstract.html)）：
   不采用序列建模出动作（我们的数据规模与可枚举候选结构不适合），但保留由真实地图与 history 提供的可观测上下文。
- **Distributional RL**（C51 一系）：v2 的 20 桶 stopping-hazard 本质是分布价值头，保留并扩展为 v3 的主头；
  hazard 表示还天然给出「风险敞口」——路径风险可用桶上 `P(stop_before_floor+k)` 的变化量表达，替代手工 risk 权重。

### 2.3 对设计的五条启示

1. 选路 = **学习值 + 图搜索**（DIVA 验证），不必端到端输出动作。
2. 非战斗决策的价值锚点是**战斗后果**，scumthespire 精确模拟是本项目的独有优势（DIVA/Driedger 只能预测，我们能真算）。
3. 未来信息进模型的方式是**条件化 token**（Decision Transformer 思想的静态版），不是让模型自己想象未来。
4. 钥匙/Heart 信号稀疏 → 需要**辅助头**把稀疏信号变成密集监督（同 v2 用 [BOSS] token 的设计哲学）。
5. 数据曾是最大瓶颈（地图只有自对弈有）→ **种子→地图重建已解除**（§4.2：2,000 万条外部 run 全量带真图）；
   learned missing-embedding 仍保留，兜底约 0.03% 无法重建的异常样本。

---

## 3. 总体设计

### 3.1 设计原则

- **P1 决策统一**：五类决策共用「候选 → 模拟 → V(S′) → argmax」闭环；MAP 也回到这个闭环，只是候选的「下一状态」由树搜索展开。
- **P2 价值语义不变**：hazard + Heart 头与 V(S) 组合公式原样保留，`VALUE_TARGET_SCHEMA = "bucket-hazard-v1"` 与 20 个端点不动
  ——新旧模型在同一标签契约下可直接对比。
- **P3 搜索与学习分工**：学习负责「房间/状态值」，确定性图搜索负责「组合」（地图是 DAG，组合不需要学习）；
  随机性（? 房间、战斗结果）用 expectimax 概率分支显式表达，不塞进网络猜。
- **P4 渐进替换硬规则**：每类决策保留规则 fallback（模型不可用/低置信时退回 v2 行为），逐类切换、逐类 A/B。

### 3.2 输入表示（token schema）

序列总预算按当前实现配置，item token 上限为 73：

```
[CLS] + [GLOBAL] + [CHARACTER] + [BOSS] + [KEYS] + [MAP×N_map] + [HISTORY×8] + item tokens（≤73）
```

| token 组 | 内容 | 说明 |
|---|---|---|
| `[GLOBAL]` | 现有 9 维 + 新增：`act`、`floor_in_act`、`floors_left_in_act`、`ruby/emerald/sapphire`（3×onehot）、`burning_key_pending_act1..3`（当前幕是否还有带火精英未打）、`deck_size`、`curse_count`、`potion_count` | `NUM_GLOBAL_FEATURES: 9 → ~18` |
| `[BOSS]` | 当前幕可见 Boss（保留 v2 设计） | 不变 |
| `[KEYS]` | 钥匙汇总上下文 token：已集齐数量 + 当前幕带火精英存在性 + Recall 可用性 | 给钥匙信号一个高带宽的 attention 锚点 |
| `[CHARACTER]` | 当前角色的稳定 ID：`IRONCLAD=0`、`THE_SILENT=1`、`DEFECT=2`、`WATCHER=3` | 训练、推理均必填；未知或缺失角色直接拒绝 |
| `[MAP]×N` | **当前幕全图每个节点一个 token**（StS 当前幕全图始终可见）：房间类型 onehot(M/E/R/$/T/?/Boss)、`burning` 标记、楼层 y、归一化列位置 x、出度、入度、`reachable_from_current`、`visited`。外部数据经 §4.2 重建后全量带图；learned missing-embedding 仅兜底约 0.03% 重建异常样本 | N ≈ 16–23/幕；三幕只放当前幕（后续幕地图未生成），跨幕先验由 `act` 全局特征 + `[BOSS]` 学习 |
| `[HISTORY]×8` | 当前幕已走过路径的历史房间上下文 | 只表示已发生信息；不包含 router 计划或 hindsight future |
| items | 卡组/遗物/药水（现有编码，升级/数量 embedding 不变） | 不变 |

架构调整（`selectcard/src/config.py`）：`D_MODEL: 128 → 192`，`N_LAYERS: 3 → 5`，`MAX_SEQ_LEN: 64 → 192`；
新增 **token 类型 embedding**（区分 CLS/global/character/boss/keys/map/history/item 八类），其余（Pre-LN、早期 Global token、CLS pooling）不动。
参数量约 3–5M，RTX 5090 上训练小时级，推理 batch 一次前向毫秒级。

### 3.3 输出头

| 头 | 输出 | 监督 | 用途 |
|---|---|---|---|
| **主头（不变）** | 20 桶 stopping-hazard + 1 Heart logit，端点同 `data_contract.HAZARD_ENDPOINTS` | run 终局（外部 + 自对弈） | `V(S) = E[stop] + 3.0 × P(Heart)`，全部五类决策的打分函数 |
| **key-progress 辅助头（新）** | 3×sigmoid：P(每把钥匙已收集 \| S) | 外部数据：Ruby 由 RECALL 推断、Emerald 由重建带火精英是否在已走路径上推断；自对弈数据：全量 | 把稀疏的 Heart 信号变成密集钥匙监督，给 `[KEYS]/[MAP burning]` token 直接梯度；权重 0.2 |
| **policy 辅助头（可选，v3.1）** | 对决策点候选的偏好分布 | 外部人类 run 的实际选择 | 人类先验正则，压制价值模型早期灾难候选；仅外部数据可训 |

风险语义升级：路径风险评估从手工权重改为「hazard 桶差」——进入房间 r 后 `P(stop_before_floor+k)` 的期望变化量由分布头直接给出，
不需要单独的 risk 回归。

### 3.4 五类决策的决策流

| 决策 | 候选 | S′ 构造 | 打分 |
|---|---|---|---|
| MAP | 下一步房间选择（`current_map_choices`） | **限定深度 expectimax**（见 3.5），叶状态 = 模拟推进 D 个房间后的状态 | 叶 V(S′) 期望，取 argmax；不可用时回退现有 `RoutePlanner` |
| CARD_REWARD / BOSS_REWARD / GRID | 现有枚举 | 现有局部模拟（`_apply_choice`） | V(S′)；输入带 [CHARACTER]/[MAP]/[KEYS] |
| SHOP_SCREEN | 现有贪心搜索（买/删牌组合） | 现有 | 同上 |
| EVENT | 结构化语义（EventStateExtractor）的全部合法选项；随机效果（random_relic/random_upgrade 等）枚举 realization 取加权期望；战斗选项保守规则（避开或强制进入） | per-choice expectimax（已实现于 `sts_ai_framework/llm_agent_parts/event_expectimax.py`，`EVENT_DECISION_POLICY` 可切回 legacy） | 同上 |
| REST | Rest / Smith(逐卡) / **Recall** | Recall 的 S′ = `ruby_key=True`（局部模拟器新增该动作） | V(S′) 与 Rest/Smith 同台比较；无 Ruby 且模型认为路线难以承担损失时自然选 Recall |
| CHEST | **拿钥匙 / 拿遗物**（需 Java 侧确认 choice 暴露形态） | Sapphire 的 S′ = `sapphire_key=True`；遗物的 S′ = 加遗物 | V(S′) 二选一；未激活钥匙前保持自动打开 |

### 3.5 MAP 决策：限定深度 expectimax + V(S) 截断

复刻战斗侧（scumthespire：真引擎 + 树搜索 + 价值截断）的成功模式到地图图上：

```
score(候选首步 n0):
  expand(n0, depth=D):
    若房间为 M/E:
      向 scumthespire 评估器请求该战斗结果分布（P(win), 掉血分位数 q10/q50/q90）
      → 3 个结果分支（低/中/高分位数），各分支局部模拟战后状态（金币/药水/卡奖池近似）
      卡奖后续决策用 greedy best-of-3 by V 内部折叠
    若房间为 ?:
      按 EventRoomChances 协议字段给的概率展开 5 种结果，各自局部模拟
    若房间为 R/$/T: 确定性局部模拟（R 含 Recall 分支）
    depth > D 或到 boss: 用 V(S) 截断
  score = Σ 概率 × 叶 V(S_leaf)
对 current_map_choices 全部候选算分，argmax（保留现有 safe/risk 紧急刹车作为护栏）
```

- **计算量**：分支因子 ≤ 3 outcomes × ~2 children，D=3 → ≤ ~200 叶；V 批量一次前向 < 50ms；
  战斗评估走 `:5125` 池异步批量（同 state+enemy 结果缓存），预计单次决策 0.5–2s，兼容 `--interval 2.0`。
  先做「快模式」（战斗只取 q50 单分支）再视延迟开全模式。
- **确定性**：整棵树由确定性模拟器展开，随机性全部显式化为概率分支——不存在「网络想象未来」的误差。
- **钥匙问题在此自然消解**：带火精英是图上带 `burning` 标记的 E 节点，战斗评估器给出**带火版敌人**的真实胜率/掉血
  （比 v2 的 `burning_elite_extra_risk` 常数精确）；「这幕打还是等下幕」「绕路多少格换钥匙」全部由 expectimax 分数回答。
  v2 的 `_apply_key_feasibility` 过滤、`key_bonus`、`dead_end/optionality` 等手工项全部退役（护栏性 safe 刹车保留）。
- **v3.1 演进项（room-head DP）**：训练一个 per-room 头，输入房间 token + 状态摘要，直接输出 E[ΔV|进房]，
  把 expectimax 换成整幕 DAG 上 O(N) 动态规划。标签用自举蒸馏（房间进出两侧的 V 值差），迭代稳定后再切换。v3.0 先不做。

### 3.6 钥匙决策细节（回应「带火精英要怎么搞」）

| 来源 | 决策点 | v3 处理 |
|---|---|---|
| Emerald（带火精英） | MAP 候选中含 burning E 节点 | expectimax 树里它是普通 E 节点 + burning 标记；战斗评估给出强化版敌人预期；`[KEYS]` token 告诉模型缺 Emerald；「早拿便宜/晚拿发育」由不同幕的战斗分布与 V 差值显式比较。模型学不到的极端情况（缺钥匙且当幕无带火精英）由护栏报警：`key-progress` 头输出与地图 reachable 检查矛盾时打日志并回退规则 |
| Ruby（Recall） | REST 三选一 | Recall 进候选集（S′ 只改 `ruby=True`）；V 比较「少回一次血/少升一次级 vs 多 3 层 Heart 通道」。替代 `choice_mixin.py` 的 Act3 硬规则 |
| Sapphire（宝箱） | CHEST | 两候选进 V；替代「自动打开」。注意：**Sapphire 取代遗物**，故本质是「遗物 vs +Heart 通道」的价值比较 |
| 路线级约束 | Act 3 末缺钥匙 | expectimax 天然覆盖（缺钥匙路径的叶状态 `P(Heart)≈0`，V 自动变低）；护栏层保留 v2 的 feasibility 检查作兜底 |

---

## 4. 数据方案（v3 数据集）

### 4.1 数据来源（2026-09 盘点更新）

heart-rate.net **已关停**；现有外部数据全部在本地 `selectcard/STS Data/Monthly_2020_07..11/`
（16,835 个 gz 文件 × 平均 ~1,211 条，估计 **约 2,000 万条人类 run**，SpireLogs 完整格式）。

| 来源 | 有什么 | 缺什么 | 用途 |
|---|---|---|---|
| 本地月度归档人类 run | 完整终局标签、卡组/遗物/事件/营火决策、`path_per_floor`、**`seed_played`（run 种子）**、`build_version` | 原生无图结构字段（由 §4.2 重建补齐）；Ruby←RECALL、Emerald←重建带火精英可推断，Sapphire 暂不可推断 | 价值核心预训练；post-action 快照与 history 上下文；**种子→图重建（见 4.2）的数据底座** |
| 种子→地图重建（✅ 已实现并验证） | 给归档 run 配上**整幕真图**（节点/边/房间类型 + 带火精英位置），历史回验 99.97% | Sapphire 钥匙状态仍不可推断；约 0.03% 异常段标记为缺失 | 全图 `[MAP]` token 的主数据来源（无需等自对弈） |
| 自对弈 run（framework） | 可 dump 全图/钥匙/带火标记/每步决策与候选 | 分布窄（AI 自己的偏好）、初期模型弱 | 分布适配微调；key-progress 头监督；MAP 搜索的行为数据 |
| scumthespire 战斗模拟 | 精确战斗结果分布 | —— | 推理期特征（MAP 树展开），非训练数据 |

### 4.2 种子→地图重建轨道（已验证 ✅ 2026-09-12）

归档 run JSON **包含 `seed_played`**（如 `'-3775500262246337531'`；此前管线只读 `special_seed`/`chose_seed` 做拒收过滤，误以为无种子）。
StS 的地图由种子确定性生成，**该轨道已实现并验证通过**：

- **实现**：`selectcard/src/sts_mapgen.py`——libGDX `RandomXS128`（xorshift128+，非 java.util.Random）与
  `MapGenerator`/`RoomTypeAssigner`/`AbstractDungeon.generateMap` 的逐位 Python 移植（全部经 javap 反汇编核对，含
  `getCommonAncestor` 的 `node1.x < node2.y` 怪癖、`addEdge` 静默去重、房间池「shop→rest→elite→event」构建顺序、
  `Collections.shuffle` 直接用 RandomXS128.nextInt 等）。带火精英（`setEmeraldElite` 在图生成后用 mapRng 从精英节点抽取）同样可重建。
- **双重验证**：
  1. **金标准**：Java 驱动直接调用 `lib/desktop-1.0.jar`（v2.3.4）里的真实类生成地图与 RNG 序列，Python 移植与之逐位一致
     （3 种子 × 3 幕的网格 + 全部边 + RNG 向量，钉在 `test_sts_mapgen.py`，4/4 绿）。
  2. **历史数据回验**：`validate_map_reconstruction.py` 对月度归档抽样，重建各幕地图并以 DP 校验
     `path_per_floor` 合法性——**61,128 个幕段（36,140 runs，2020-07~11 五个月）通过率 99.97%**，
     各月 99.95–99.99%，2018-2020 各 build 通过率一致。失败段仅 17 个：个别 run 疑似私改客户端/脏数据（同一 run 全幕皆败），
     以及事件传送导致的「超长段」记录异常。
- **验证过程中确认的三项游戏机制**（已编码进校验器）：
  1. `?` 房间进入时由 `EventHelper.roll()`（独立 eventRng，不消耗地图房间池）暗中解析为 M/$/T/?，`path_per_floor` 记录解析后类型；
  2. `WingedGreaves`（翅膀）允许跳到不相邻节点：路径可行性 DP 需带 charges 状态（拾取层起发放、跳数预算的超集模型）；
  3. 事件传送（Winding Halls 等）可产生跨幕/超长段记录异常（占剩余失败段多数）。
- **build_version 风险解除**：数据来自 2020-01-27 与 2020-07-30 两个 build，与 v2.3.4 jar 的地图算法逐位兼容。
- ? 房间的具体事件**不**重建（eventRng 消耗顺序依赖玩家路径），保持 ? 概率分支方案；带火精英是否被击杀可由路径是否经过该节点推断，
  用于后续幕的 burning 标记与 `hasEmeraldKey` 状态链。

### 4.3 地图与历史上下文，以及训练时序

种子重建（§4.2）落地后，外部数据从第一个样本起就带当前幕真图。模型只接收决策时可获得的上下文：

1. **[MAP] 全图通道**：每个决策点附当前幕重建图（节点/边/房间类型/burning/visited/reachable 标记）。训练与推理同构，
   全图 optionality、带火精英路线和钥匙时机由这条通道学习。
2. **[HISTORY×8]**：当前楼层之前本幕实际走过的房间（most-recent-first + 距离 embedding），只表示已发生信息，不含未来路径。
3. **不使用 PLAN**：router 的路线只是临时选路工具，不进入训练数据、模型输入或 value payload；模型根据真实地图自行评估候选。
4. **post-action 训练状态**：每条训练行的状态字段表示该行历史选择已经执行后的 `S′`，与推理阶段对候选执行局部模拟后评分 `V(S′)` 保持一致。
   `state_timing="post_action"` 是新数据契约的一部分，混入旧时序样本时构建/加载必须拒绝。
5. **能学到 / 学不到**：能学到全图 optionality、带火精英/钥匙路线时机以及基于历史的风险；下一幕未生成的地图仍由 `act` 全局特征和 `[BOSS]` token 表达跨幕先验。

### 4.4 自对弈数据捕获（新）

- run 事件 JSON 增加两类记录：
  1. `act_start`：整幕 map dump（`nodes[{x,y,symbol,burning,edges}]` + `keys` + 可见 boss）——直接来自现有 `/state` 的 `map_nodes`，无需改协议。
  2. `decision`：每个决策点的 `(screen_type, 候选列表, 被选动作, 决策前状态摘要)`。
- 每批自对弈用 **ε-greedy（ε≈0.1）** 在候选间随机采样：把贪心轨迹变成有分叉的覆盖树，缓解 off-distribution 打分偏差（value 版的 DAgger 动机）。
- 重建落地后自对弈的角色收窄为：分布适配微调、每步决策与候选 dump、key 全量监督、ε 分支采集与局部模拟保真校验。

### 4.5 数据集构建

- 新目录 `selectcard/processed_data_v3/`（不提交），`PREPROCESSING_VERSION = "set-transformer-v7-map-hazard"`；
  **标签契约、端点、V 组合公式与 v2 完全一致** → 新旧模型同一把尺子。
- 混训采样：每 batch 外部 : 自对弈 ≈ 7 : 3（防自对弈分布坍缩）；`[MAP]` 缺失（约 0.03% 异常段）走 learned missing-embedding。所有样本必须满足 `state_timing="post_action"`。
- 地图附件：对每条外部 run 按幕调用重建器（`sts_mapgen.generate_act_map`），把 `nodes[{x,y,symbol,burning,edges}]` 与推断的钥匙状态链写进 v3 样本列（一次性批处理，约 2,000 万条；抽样回验命令见 §4.2）。
- 过采样钥匙决策点（REST/CHEST/含 burning 路径的 MAP）×3，缓解稀有决策欠拟合。
- 局部模拟保真度沿用仓库既有校验传统：自对弈时把每步 S′ 模拟结果与下一步真实 `/state` 比对，不一致事件进 `find_mismatches.py` 风格的报告。

### 4.6 迭代循环（expert-iteration 式）

```
v3.0（外部全量预训练，含重建地图）→ 自对弈采集（ε=0.1，N≥200 runs）
  → v3.1（混训微调）→ 接管 MAP 搜索 → 再采集 → v3.2 → ...
每轮固定同一批评估 seeds 与 framework 版本，防漂移。
```

---

## 5. 训练

- 损失：`Σ masked hazard BCE + heart BCE + 0.2 × key-progress BCE (+ 0.1 × policy CE)`；
  训练超参沿用 `config.py`（batch 512、cosine、early stopping 起点后移到 epoch 8，序列变长后 dataloader CPU 需求上升，沿用 SLURM 32 核建议）。
- 课程：前若干 epoch 可屏蔽地图/历史上下文做热身，但不引入 PLAN dropout 或 PLAN curriculum。
- 消融开关（`config.py` 新增）：`USE_MAP_TOKENS / USE_CHARACTER_TOKEN / USE_KEY_HEAD` 逐个开关，跑 ±消融。

---

## 6. 评估

### 6.1 离线

| 指标 | 方法 |
|---|---|
| 校准 | 各 hazard 桶 AUC/ECE；Heart 头 AUC（外部 held-out + 自对弈 held-out 分别报告） |
| 决策一致性 | held-out 人类 run 决策点上：模型给实际选择的分数 vs 其他候选，Spearman（对比 v2 基线） |
| 反事实单调性 | 对同一状态注入扰动（加金币/加血/加钥匙/未来路径加商店），V 变化方向必须正确 |

### 6.2 「投资探针」（直接回应本设计核心需求，写成可重复测试）

1. **商店投资**：构造状态对，唯一差异 = 未来路径 4 层后有/无 `$` 节点，玩家持金 150。
   期望 V(有商店) 显著更高，且 SHOP 决策更愿意买高价关键卡。
2. **精英惜血**：唯一差异 = 3 层后是带火精英 vs 普通精英（均缺 Emerald）。
   期望事件「-7 HP 换卡」在带火组得分更低。
3. **钥匙时机**：同卡组同血量，带火精英在 Act 1 vs Act 3。
   期望在路线成本相当时 V 偏好 Act 1 拿取（战斗分布更弱），路线成本过高时能等 Act 3。

### 6.3 在线

- N≥200 自对弈 seeds，v3 vs v2 双臂：win rate、E[stop floor]、Heart rate、**钥匙集齐率与集齐楼层**、死亡时金币（攒钱行为）、带火精英战斗胜率。
- 逐类决策切换 A/B：先 CARD/SHOP/EVENT（只依赖 V），后 REST/CHEST，最后 MAP 搜索。

---

## 7. 实施路线

| 阶段 | 内容 | 验证 |
|---|---|---|
| P0 协议与数据 | **✅ 已实现**：管线按 `seed_played` 重建整幕真图并发射 v3 列（`map_context.py` + `data_pipeline.py`，含 key 状态链与 history）；训练快照统一为 post-action `S′` | 重建抽样回验 99.97%；全量批处理与协议项待执行 |
| P1 模型 | **✅ 已实现**：`[CLS]+[GLOBAL]+[CHARACTER]+[BOSS]+[KEYS]+[MAP×105]+[HISTORY×8]+items` token schema、key-progress 头、课程掩码、train/evaluate/inference 全链路；旧 v3 checkpoint 因新 schema 校验显式失效 | `python -m unittest discover -s selectcard/src -t selectcard/src`（模型测试）+ 真实数据端到端冒烟 |
| P1 模型 | token schema 扩展、key-progress 头、v3 预训练（外部 + 早期自对弈） | 离线指标 ≥ v2；`python -m unittest discover -s selectcard/src -t selectcard/src`（模型测试） |
| P2 决策接入 | CARD/SHOP/EVENT/REST/CHEST 换 v3 输入；Recall/Sapphire 候选实现；局部模拟器新增 recall/sapphire 动作 | 框架回归 `python -m unittest discover -s sts_ai_framework/tests -t .`；探针 1–3 通过 |
| P3 MAP 搜索化 | expectimax 决策器 + 快模式 + 规则 fallback | 延迟 < interval；A/B 200 runs |
| P4 迭代 | ε 自对弈 → v3.1 → 消融 → （可选）room-head DP | 每轮离线+在线报告 |

按仓库规则，涉及 mod 改动时同步版本号：CommunicationMod（暴露钥匙相关选择/细节）与 BattleAiMod（战斗结果分布接口）预计 minor；
SaveStateMod/LudicrousSpeed 预计不动。`selectcard` 侧新增 `MODEL_V3_DESIGN.md` 对应实现前需更新 `AGENTS.md`/`CLAUDE.md` 的 Set Transformer 章节。

---

## 8. 风险与对策

| 风险 | 对策 |
|---|---|
| 地图重建异常段 | 约 0.03% 样本使用 learned missing-embedding；重建统计和 history/post-action 契约纳入数据验收 |
| 自对弈分布坍缩（模型学自己的偏好） | ε-greedy 采集 + 每 batch 30% 外部数据 + 跨轮评估 seeds 固定 |
| 钥匙/Heart 样本稀疏 | key-progress 辅助头 + 决策点过采样；hazard 端点 57 已覆盖 Heart 通道 |
| MAP 搜索延迟（战斗评估 200 叶） | 快模式（q50 单分支）先行；结果缓存；V 批量前向；超时回退现有 planner |
| 局部模拟保真度（候选 S′ 与真实后续不一致） | 沿用重放校验传统：自对弈时逐步比对并出 mismatch 报告 |
| 事件正则覆盖不足（效果解析失败率） | 按 TODO.md 任务 1 的 Java 侧选项文字替换方案推进，属前置依赖 |
| 序列变长后 dataloader 瓶颈 | 沿用现有 32 worker 建议；map token 打包为紧凑特征列 |

---

## 9. 参考来源

- [Slay the Spire Wiki — Keys](https://slay-the-spire.fandom.com/wiki/Keys) / [wiki.gg — Mechanics](https://slaythespire.wiki.gg/wiki/Mechanics) / [wiki.gg — Elites](https://slaythespire.wiki.gg/wiki/Elites)（钥匙与带火精英机制核实）
- [Porenius & Hansson 2021 — Using machine learning to help find paths through the map in Slay the Spire (DIVA)](https://www.diva-portal.org/smash/get/diva2:1565751/FULLTEXT02.pdf)（MLP 整路径胜率 + 枚举选路 + 用户实验）
- [ForgottenArbiter 博客](https://forgottenarbiter.github.io/about/) / [CommunicationMod](https://github.com/ForgottenArbiter/CommunicationMod) / [spirecomm](https://github.com/ForgottenArbiter/spirecomm)（生态与硬编码 AI 现状）
- [arXiv:2505.17673 — From Top-Down Workflows to Bottom-Up Skill Evolution](https://arxiv.org/abs/2505.17673)（近期 StS LLM 智能体评估）
- [cassiusfive/balatro-gym](https://github.com/cassiusfive/balatro-gym) / [TylerFlar/jackdaw-balatro](https://github.com/TylerFlar/jackdaw-balatro) / [coder/balatrollm](https://github.com/coder/balatrollm)（相邻 roguelike 的 RL/LLM 路线）
- [Decision Transformer (Chen et al., NeurIPS 2021)](https://proceedings.neurips.cc/paper/2021/hash/7f489f642a0ddb10272b5c31057f0663-Abstract.html)（未来条件化思想的来源）
- 本地：`cardcrawl/` 反编译源码（带火精英强化精确数值核对）；`lib/desktop-1.0.jar`（v2.3.4）javap 反汇编——RNG 与地图生成的金标准，产出 `selectcard/src/sts_mapgen.py` 及其验证（§4.2）
