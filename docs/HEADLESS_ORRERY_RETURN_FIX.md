# 星盘嵌套奖励返回修复

2026-11-18，原种子 **5050117** 的星盘返回商店问题已通过实局确认与独立回放。原 v2 失败报告及冻结运行文件保留，新版本部署在 `/home/ubuntu/headless-spire-poc/runtime-acceptance-orrery-fix-v1`。

## 根因与修改

源 `Orrery.onEquip()` 调用 `CombatRewardScreen.open()` 时未保存商店父页面；随后 `RewardItem.claimReward()` 打开卡牌选择，将 `previousScreen` 写为 `COMBAT_REWARD`。选择或跳过后仍返回奖励页，而商店返回条件要求 `previousScreen == SHOP`。

[HeadlessFlowKernel](D:/code/aiplayspire/deploy/headless/HeadlessFlowKernel.java) 现在从实际 SHOP 动作入口记录父上下文：

- 购买遗物打开奖励页后恢复商店父页面。
- 打开卡牌奖励时，保存实际玩家、地图节点、房间、商店、奖励屏幕、选牌屏幕、楼层／幕及对应 RewardItem 身份。
- 选牌、跳过或颂钵完成后，只在同一有效上下文中恢复返回目标；新局、异常或失效上下文清除绑定。
- 继续执行原版购买、选牌、获得卡牌、取消和屏幕栈操作。Python 对 SHOP 父页面、库存、地图、钥匙和 14 条 gameplay RNG 的校验保持严格。

## 验证

- Linux framework **510 项**、native **264 项**全部通过。
- Java 无开局 fixture 覆盖真实嵌套 claim／open／skip／pick／bowl／close 流程，包含重复打开、外部 kernel、房间变化和异常操作负例；原全局状态和 Math RNG 恢复。
- 原种子 5050117、IRONCLAD、A10：真实重放 **245 个原动作**，第 19 层执行返回商店动作 `proceed`，结果 **APPLIED**。总 **246 个动作**重新独立预测／回放通过。
- 返回前后金币均 **319**、牌组均 **23 张**，14 条 gameplay RNG 不变；实际落点为 `SHOP`。
- 目标验证后有意结束，轨迹保留删失标记，不添加胜负标签。此次只运行该定点实局，完整 24 局尚未重新启动。
- 本次 scoped 许可已归档关闭，无遗留游戏 worker。原只读服务、SoulNPC、3681174958 服务的 PID／重启数保持，旧 v2 的 7453 项输入保持；模型及 39 文件 profile 不变，训练未接入。

## 证据

- [实局确认与独立回放](D:/code/aiplayspire/debug/headless-orrery-fix/results/orrery-live-report.json)
- [最终保全核对](D:/code/aiplayspire/debug/headless-orrery-fix/results/final-preservation.json)
- [Java 嵌套奖励 fixture](D:/code/aiplayspire/debug/headless-orrery-fix/results/ShopFlowFixtureTest.json)
- [framework 回归](D:/code/aiplayspire/debug/headless-orrery-fix/results/framework-regression.log)、[native 回归](D:/code/aiplayspire/debug/headless-orrery-fix/results/native-regression.log)

服务器结果位于新目录 `reports/orrery-live-report.json`。复验入口为 [run-orrery-recheck.py](D:/code/aiplayspire/deploy/headless/run-orrery-recheck.py)，仅执行该原种子目标并在收尾时关闭许可。
