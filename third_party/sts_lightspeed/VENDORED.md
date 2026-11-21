# Vendored: sts_lightspeed

## 来源

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/Attemory/sts_lightspeed |
| 分支 | `master` |
| commit | `46e14e4adc23d2c1c738df8902a6f297109fee50` |
| 上游基线 | https://github.com/gamerpuppy/sts_lightspeed @ `7476a81954020087da31d41d16fddf475746ec2d` |
| 许可证 | MIT（见 `LICENSE.md`） |
| vendored 日期 | 2026-11-02 |

选 Attemory 的 fork 而不是 gamerpuppy 的上游，原因：

1. 它在铁甲之外补齐了全部**机器人**卡牌效果（上游只到铁甲）。
2. 它自带 `include/convert/BattleConverter.h` + `src/convert/BattleConverter.cpp`（约 43 KB），
   能把一个 JSON 战局快照直接转成 `BattleContext`。这是接真实游戏状态的关键件。
3. 它自带 `apps/battle-sim.cpp`、`apps/battle-agent.cpp`、`apps/card-reward-eval.cpp` 三个
   由 JSON 驱动的可执行入口，可作为我们服务端的行为参照。

## 内容裁剪

- 删除了原仓库的 `.git`。
- `json/` 原本是 git submodule（nlohmann/json），改为平铺 vendor：只保留
  `json/include/` 和 `json/LICENSE.MIT`，删掉了 upstream 的测试与文档。
- 删除了 `.gitmodules`（已无 submodule）。

## 本地改动

- `include/data_structure/fixed_list.h`：四个迭代器访问器使用 `std::array::data()` 返回声明的裸指针，修复 MSVC 的标准库迭代器类型与裸指针不兼容；GCC 行为保持一致。
- `include/game/Game.h`、`src/game/Game.cpp`：添加默认关闭的线程局部运行时战斗卡池。native worker 显式开启严格模式并采用 Java 导出的真实迭代顺序；原版独立程序继续使用原有静态池。严格模式缺失卡池时明确报错，避免 Creative AI 等效果在 RNG 一致时仍抽到不同卡。
- 2026-11-03：新增 `CharacterCards` 部分静默／观者效果和严格分派目录；未实现的出牌在 Release 版抛错。补齐基础姿态切换、真言进入神格、生成牌 Master Reality 升级；这些只做了离线效果校验，交互覆盖仍待补齐。
- 新增 `ExecutionBudget`，回合搜索 deadline 可中断动作队列；执行器循环上限改为异常，避免 Release 版持续循环污染 JSONL。
- `Cards.h` 的 `getCardColor` 修正 8 个静态表错位；`Player.h` 的遗物模板用 `if constexpr` 避免无效移位分支编译告警；`CardInstance` 补不可手动打出、Grand Finale、Signature Move 合法性检查。
- 回合搜索、评分、路径缓存均放在 `battle_engine/`，保留上游 MCTS；四角色完整迁移状态见根目录 `docs/LIGHTSPEED_MIGRATION.md`。

优先做法是：能在 `battle_engine/` 里适配的，就不要改 `third_party/`。

### 静默／观者增量（2026-11-03）

- 扩充 `CharacterCards` 至静默 73/75、观者 70/75 的显式分派和运行时入口。
- 新增原生 `CharacterStrategy` 与 `CharacterSelection`，移植 Java 策略及进程内选择模拟；真实屏幕恢复仍保持严格边界。
- 修复毒量溢出、动态卡牌属性、姿态回调、保留及相关卡牌效果；本地修改不回写只读原版 Java 目录。
- 完整进度及离线／实机验证边界见根目录 `docs/NATIVE_CHARACTER_SUPPORT.md`。

### 静默／观者分派补齐及选择恢复（2026-11-04）

- 静默／观者各 75/75 显式分派与运行时入口一致：补 Escape Plan、Nightmare、Deva Form、Omniscience、Wish、Lesson Learned、Foreign Influence。150 张牌的未升级／升级共 300 个冒烟子例通过，完整交互与实机对等仍是独立门槛。
- `BattleContext` 增加 Nightmare 目标、真实永久牌组和独立 cardRng；恢复 Deva 私有增长字段。Foreign Influence 使用 Java 导出的真实已解锁 CardLibrary 攻击池，分离两条 RNG，增加 Java/C++ golden。
- `CardSelectInfo` 增加真实手选确认和导入顺序标记；`CardInstance` 跟踪 Omniscience 强制消耗，纳入去重／状态键／MCTS hash。真实选择 JSON 的白名单读取器放在 `battle_engine/src/SelectionSnapshot.cpp`。
- `CardQueue` 保留 10 槽环形热路径，增加可复制的扩展队列及有序遍历，修复 containsCardWithId；离线覆盖 12 张排队和搜索副本。
- 公共投影仍为 v1，当前仅另加界面预测及执行前选择上下文 guard；完整边界、私有状态与 v2 迁移进度见根目录 `docs/NATIVE_CHARACTER_SUPPORT.md`。

### 历史故障回归修复（2026-11-03）

- 2026-11-07：补齐 Vault、Eruption 等观者变费升级，按原版 `upgradeBaseCost` 保留回合内费用调整；群体升级跳过不可升级的状态牌与诅咒。
- 2026-11-07：对照原版校正四角色、无色及衍生牌的初始／升级费用，包含 X 费与不可打出牌；Force Field 升级保留已有费用。药水发现的牌保留基础费用，并只将可调整的当回合费用设为 0。
- 2026-11-07：原生模拟中液态记忆取回弃牌时统一设置当回合费用，并处理圣树皮的两张牌；将已有卡牌移回手牌时不再误触发具象现实的造牌升级。真实选牌界面仍遵循 bridge 的既有支持范围。

- 普通怪物连击在攻击者被反伤击杀后取消剩余命中；THORNS／纯 HP 损失仍走独立动作。
- Rampage 从 `base_damage` 恢复成长量，缺失动态伤害时明确报错；不同成长量参与现有候选去重与状态键。
- 移除“没有牌即判负”的近似规则，保留药水、球、能力及真实终局结算；搜索预算继续约束必败展开。
- 修正升级 Disarm、Bias 的 Artifact 交互，以及 Wraith Form 的有符号敏捷损失；不扩大静默／观者卡牌准入目录。
- `native_slot` 是 bridge → converter 的可选内部字段，用于恢复召唤战斗的固定槽位。默认调用方仍沿用原有转换逻辑。
- `MonsterSpawnObserver` 默认关闭，仅 bridge 最终执行单个已选动作时启用；维护 SaveState 的历史怪物条目、按 X 插入的新生怪物和目标映射，不向搜索副本附加历史日志。
- 大史莱姆分裂不再重复消耗 AI RNG：两个子体各 roll 一次，仅大尖刺史莱姆的死亡父体额外 roll 一次。MCTS 公用测试夹具同步补齐合法怪物意图和决策边界。
- 回归：仓库根目录执行 `conda run --no-capture-output -n spire python -m unittest discover -s battle_engine/tests -t .`。

### Linux 执行预算生命周期（2026-11-15）

- `ExecutionBudget` 自持 deadline 值，线程局部指针指向 guard 内部存储；修复 `Snapshot.cpp` 传入临时时间点后留下悬垂引用、在 GCC Release 构建下误报 `execution_deadline` 的问题。
- 禁止复制 guard，保留嵌套预算恢复及真实超时检查。回归覆盖临时参数、嵌套恢复、异常展开与过期 deadline；生产搜索预算及状态语义保持原样。

## 如何更新

### Headless 显式 RNG policy（2026-11-16）

- 增加默认关闭的 `headlessRngKnown/headlessRng`，精确状态恢复、回合搜索状态键及 MCTS hash 纳入该流；旧 SaveState 路径保持原样。
- Headless 回合末按固定上游源码先移走所有 retain/selfRetain 卡，再用 `headlessRng.randomLong()` 驱动 Java Collections.shuffle 的结束回合触发顺序；空手牌和 Runic Pyramid 也保留该次 RNG 调用。保留牌成本／成长在恢复时执行。
- 修复按 UUID 找回消耗牌时循环误用旧索引，保证乱序的多张 Ethereal 正确消耗。验证与实局进展见根目录 Headless 验收文档。
- 2026-11-17：Cultist 的 Headless 攻击回合按真实源码更新 move history；保留旧策略分支。毒回合伤害补齐目标／伤害参数，使用 HP_LOSS 绕过格挡、递减毒层并执行真实死亡触发，替换此前空 Action 回调；空回调现显式报错而非抛出含糊的 bad_function_call。
- 实局对照补齐 Mugger 死亡音效对 aiRng 的调用、Guardian 入防御时的下一轮阈值、Hexaghost Activate 的 SetMove／分割伤害／球计数与升级 Burn。相关固定上游策略由 `headlessRngKnown` 显式开启。
- 修正吸血药水的 20%／圣树皮 40%、玩具扑翼飞机的饮药回血、死亡目标的格挡队列，以及无实体的 Intangible justApplied 标记。Headless 在弃牌动画结算边界恢复临时费用；Codex 候选按实际角色生成。
- Headless Pointy 重复攻击按 SetMove 更新历史而不消耗 AI RNG；百夫长 DEFEND 按真实随机盟友选择结算，即使仅一位盟友也消耗选择 RNG，无盟友时格挡自身。治疗师按存活全场缺血总量判断治疗，并限制连续两次治疗。旧模式分支保持原样，增加单盟友／独自百夫长回归；最终四角色终局轨迹均通过独立强制动作回放。

### Headless 多种子验收增量（2026-11-17）

- 地精按固定上游行为区分 `SetMove` 与 `RollMove`：Mad／Sneaky／Shield／Wizard 的重复动作更新历史，保留各分支真实的 AI RNG 调用；Fat Gremlin 执行真实 `RollMove`。Wizard 充能／施法／逃跑分支保留实际计数进展，`battle_engine/src/Snapshot.cpp` 从私有 `currentCharge` 精确恢复 `miscInfo`，纳入后继 native key 校验。
- Shelled Parasite 连续 Fell 触发 20–99 重掷后，Headless 分支按新的 roll 选择 Double Strike／Suck，修正旧低 roll 残留导致的分支偏移；旧模式保留原分支。
- `battle_engine/src/TurnSearch.cpp` 将 `HEX` 加入 Headless flag-only 状态键规范化：保留状态存在位比较，省去 importer 写入、模拟器不使用的冗余 sentinel 数值项。
- BookOfStabbing 的私有 `stabCount` 精确恢复到 `miscInfo`，覆盖多刺与单刺意图；私有动态计数通过 native key 比较，并校验活体多刺的 `move_hits` 一致性。修正导入默认一击造成的伤害与 Painful Stabs 生成 Wound 数量差异。
- Taskmaster 的 Headless 鞭击使用真实 `RollMove`，同时更新重复行动历史并保留原有一次 AI RNG 调用；旧模式继续使用原来的 no-op roll。
- Champ 精确恢复独立 `numTurns`、`forgeTimes` 与 `thresholdReached`，不再从全局回合猜测；按源码在 Taunt 选招时归零计数，Gloat 力量校正为 2/3/4。首次发言标志由真实行动进展校验，跳过怪物回合时保持不变。
- Headless 从 `cards_played_this_combat` 恢复 `lastCardType` 并在新回合保留，修正 FollowUp、SashWhip、CrushJoints、Sanctity 的跨回合连牌；旧模式仍使用当回合历史及原重置逻辑。
- CentennialPuzzle 从显式导出的私有静态 `usedThisCombat` 恢复已有 native 一次性 availability 位，不根据受伤次数猜测。源导出缺失或类型错误时保持守卫，旧模式仍拒绝缺少此私有状态的导入。
- Headless 首次获得玩家 Ritual 时恢复源 `skipFirst` 标记，堆叠保留原标记；玩家首次回合末仍立即增加力量。为 Ritual 显式使用内部标记位，消除其高枚举值导致的超宽移位；不改变其他状态位或旧模式药水分支。
- 增加独立的 Headless Gambler／LiquidMemories 选择任务，保留 legacy 任务语义。选择阶段比较带能力、卡牌属性与身份的完整 guard，而非仅比较公共投影；源 continuation 只接受明确的派生刷新／等待白名单。Gambler 确认执行手动弃牌回调后抽等量；Liquid 的 GRID 引用仍留在弃牌堆、选满自动回手，负费用与满手规则依源处理，回手不触发抽牌或造牌效果。
- Combust 从源私有 `hpLoss` 恢复每次自损量，不从伤害层数猜测；Angry 仅在实际正普通伤害后触发力量，完全格挡、零伤害和已死亡目标按源处理，旧模式保持原样。
- Headless 在确认进入下一玩家决策边界时刷新消耗堆的临时费用，对齐源消耗动画结束的 `resetAttributes`；不提前到回合末，中途 Nilry／Codex 选牌、同回合消耗和保留牌语义保持原样。一般性的同回合动画时间仍不推测，遇到差异继续停局。
- 小尖刺史莱姆在 Headless 分支执行真实 `RollMove`，同步重复行动历史且保留一次 AI RNG 调用，legacy 的 no-op roll 分支保持原样。
- Linux GCC 原生完整回归 **240 项通过**（含上述增量）；后续按用户更新的 24 局双进程计划执行，见 `docs/HEADLESS_BATCH24.md`。原基础四局与旧多种子批次的历史记录保留。

### 24 局批次差异修复（2026-11-17）

- Headless Headbutt 增加独立 GRID 任务，保存弃牌 UUID、在途卡与真实 UseCard 续接并参与完整状态键；legacy 任务保留。卡牌输出按源目标类型处理 Rip and Tear 的无指定目标命令，内部随机选敌调用量保持。
- Headless The Bomb 从实例 ID、倒计时与私有 damage 恢复原生炸弹桶；同倒计时多实例及超出精确表示的状态保持明确守卫。WrathNextTurn 按标记存在位比较，覆盖源 sentinel 与重复施放。
- Headless 自动打顶牌按真实 FIFO 入卡牌队列，修正 Distilled Chaos 连续预排动作触发 Deep Breath 时的牌序；legacy 队列保持原样。
- Lagavulin 苏醒按源 SetMove 保存前一行动，Choked 在怪物回合开始清除数值与存在位。以上均保留 legacy 分支。
- Linux native **264 项全部通过**；包含菜单／流程修复的 framework **496 项全部通过**。原 24 条轨迹／有效前缀在修复版独立回放通过，完整证据见 `docs/HEADLESS_BATCH24_REPAIRS.md`。

```powershell
git -c http.sslBackend=openssl clone --depth 1 https://github.com/Attemory/sts_lightspeed.git <tmp>
# 搬运 `<tmp>/src`、`<tmp>/include`、`<tmp>/apps`、`<tmp>/CMakeLists.txt`、`<tmp>/LICENSE.md`、`<tmp>/README.md`
# 更新本文件里的 commit
```
