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

## 如何更新

```powershell
git -c http.sslBackend=openssl clone --depth 1 https://github.com/Attemory/sts_lightspeed.git <tmp>
# 搬运 `<tmp>/src`、`<tmp>/include`、`<tmp>/apps`、`<tmp>/CMakeLists.txt`、`<tmp>/LICENSE.md`、`<tmp>/README.md`
# 更新本文件里的 commit
```
