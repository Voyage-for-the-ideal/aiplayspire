# 24 局批次差异修复

后续实局更新：v2 定点验证通过头槌两处、Rip and Tear 和 Dolly's Mirror；剩余星盘在 2026-11-18 通过原种子实局确认。见[星盘返回修复及证据](D:/code/aiplayspire/docs/HEADLESS_ORRERY_RETURN_FIX.md)。下文保留最初离线修复阶段的证据口径。

2026-11-17 的双进程批次已尝试 24 局，**8 局真实终局、16 局因差异删失**。原报告、诊断和轨迹保持原样。修复版部署在 `/home/ubuntu/headless-spire-poc/runtime-acceptance-batch24-fixes-v1`，本轮验证使用离线契约、真实故障步骤与已记录轨迹，新增实局数为 0。

## 修复与证据

| 差异 | 涉及 case | 修复 | 验证 |
|---|---|---|---|
| Bonfire 阶段 | WATCHER-02/03/04 | INTRO→CHOOSE、选牌／确认／按稀有度奖励、COMPLETE→MAP；未知来源选牌保持守卫 | 3 个原故障步骤 APPLIED；新增奖励／确认／Mark of the Bloom 回归 |
| Knowing Skull | IRONCLAD-04 | 明确 INTRO_1→ASK 与付费离开后的 COMPLETE 阶段 | 原故障步骤 APPLIED |
| Duplicator | WATCHER-05 | 按源事件、阶段和完整目标牌组恢复通用 GRID 的 duplicate 用途 | 原故障步骤 APPLIED；错误来源／数量／目标负例 |
| Neow 随机诅咒 | DEFECT-06 | 核对新 UUID、CURSE 类型／稀有度、数量、金币及原有库存不变 | 原故障步骤 APPLIED |
| Headbutt | IRONCLAD-03/06 | 独立 GRID 续接，绑定弃牌 UUID、在途卡、UseCard 队列、完整 guard 和 RNG，选后仅清理一次 | 两份真实前帧恢复合法规划；合成跨界面与完成步骤回归 |
| Rip and Tear | DEFECT-03 | 按源 ALL_ENEMY 输出无指定目标命令，内部仍执行真实随机选敌 | 真实前帧恢复合法规划；两次随机选敌及合法动作回归 |
| The Bomb | THE_SILENT-05 | 严格解析实例 ID、倒计时与私有 damage，恢复原生倒计时桶 | 原故障步骤 APPLIED |
| Simmering Fury | WATCHER-06 | WrathNextTurn 按标记处理，保留存在位，覆盖重复施放 | 原故障步骤 APPLIED；存在位负例 |
| Distilled Chaos | DEFECT-05 | 自动出牌队列按源 FIFO，修正 Deep Breath 后的牌序 | 原故障步骤 APPLIED，精确 RNG 保持一致 |
| Lagavulin 苏醒 | DEFECT-04 | 按真实 SetMove 记录 STUN 前一行动，不额外抽取 RNG | 原故障步骤 APPLIED |
| Choked 清除 | THE_SILENT-06 | 怪物回合开始移除存在位并清零残留值 | 原故障步骤 APPLIED |
| Dolly's Mirror | THE_SILENT-04 | 原遗物优先消费其选择，避免 ShopScreen 抢作删牌；等待真实 obtain／Soul 完成 | 直接执行原 Grid、Dolly.update、obtain／Soul 的 Java 离线 fixture 通过；原错误删牌后帧仍为 UNVERIFIED |
| Orrery 商店奖励 | IRONCLAD-05 | 使用原取消屏幕栈返回 SHOP；修正 singleton 快捷路径重开已拒奖励 | Java 源方法 fixture、独立返回路由与 14 条 gameplay RNG 不变回归 |

证据合计：**11 个真实故障步骤 APPLIED、3 个缺少真实后帧的故障恢复合法规划、2 个流程故障通过源码方法 fixture**。后两组仍待同种子实局复验；原批次的 16 个删失结果继续保留。

## 回归与部署核对

- Linux native **264 项全部通过**；framework **496 项全部通过**。
- 原 24 条完整轨迹或有效前缀，用最终修复版独立重新预测／校验，全部通过。
- Java Combat、Interaction、ShopFlow 离线 fixture 通过；未调用 `new_run`，原 RNG 引用和状态保持。
- 本地与服务器 **25 个修复输入文件**逐项 SHA-256 一致；修复运行目录冻结 **2477 项输入**。
- 原批次 **4932 项输入**、原报告 SHA、固定上游 **5041 文件**、39 文件 profile 和 CPU checkpoint 均保持。
- 原只读服务 PID 74516、SoulNPC PID 57389、3681174958 的 Napcat PID 21131，重启数均为 0；Napcat2 仍为 disabled／inactive。
- 原只读接口保持 MAIN_MENU／BOOT；动作端点关闭。原批次与修复目录的实局／采集许可均关闭，未启动训练，无遗留游戏 worker。

## 结果文件

- [修复证据与 24 条独立回放](D:/code/aiplayspire/debug/headless-batch24-fixes/verification/repair-audit.json)
- [最终保全核对](D:/code/aiplayspire/debug/headless-batch24-fixes/verification/final-preservation.json)
- [Linux native 回归](D:/code/aiplayspire/debug/headless-batch24-fixes/verification/native-regression.log)
- [Linux framework 回归](D:/code/aiplayspire/debug/headless-batch24-fixes/verification/framework-regression.log)
- [真实原方法商店 fixture](D:/code/aiplayspire/debug/headless-batch24-fixes/verification/ShopFlowFixtureTest.json)

服务器证据位于修复目录的 `reports/repair-audit.json`、`reports/final-preservation.json`。本轮保持原瞬态执行服务停止，未自动重跑批次。

## 当前契约边界

Headbutt 仅接收已建模的源选牌与 UseCard 续接，额外未知动作队列会停局。The Bomb 目前精确支持每个倒计时一个实例及既有伤害表示范围；同倒计时多实例继续报出诊断，避免把多次命中近似合并。后续实局遇到新差异仍按单局中止、留档的规则处理。
