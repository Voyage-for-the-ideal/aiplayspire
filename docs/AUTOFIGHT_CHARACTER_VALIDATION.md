# 四角色战斗策略验证记录

验证日期：2026-10-23。

## 已交付

- 四角色策略注册表、按节点固定排序键、异色卡机制分派。
- 力量／消耗／保留格挡，毒／弃牌／刀舞，无实体，球槽／集中／暗球，姿态／真言／保留牌评分。
- 弃牌、消耗、占卜、检索、保留的上下文排序，保留完整合法命令集合。
- 威胁与生存共用逐次伤害估算；终局排除临时能力收益。
- 只读访问器和不依赖活游戏对象初始化的球快照读取；存档及命令格式保持兼容。
- SaveStateMod 1.6.0、LudicrousSpeed 1.2.0、BattleAiMod 1.5.0。

## 已执行验证

依赖顺序构建三个 mod，核对 `target/classes/ModTheSpire.json` 的版本及描述，jar 已通过 Maven 部署至仓库配置的 mod 输出目录。
没有新增依赖；没有修改源战斗存档或非战斗模型。

```powershell
mvn -f STSStateSaver/pom.xml package
mvn -f LudicrousSpeed/pom.xml package
mvn -f scumthespire/pom.xml "-Dbattleai.realStateManifest=D:/code/aiplayspire/debug/character-strategies/comparison/manifest.json" package
conda run --no-capture-output -n spire python -m py_compile scumthespire/tools/benchmark_characters.py
```

Java 测试共 100 项：99 项通过，1 项跳过，0 失败、0 错误。新增角色／选择回归 30 项全部通过。
可选真实快照测试已执行，四角色各四个快照（合计 16 个）成功解析并得到确定性的评分结果。
原有手动状态键基准因未指定 `stateFile` 跳过；可单独执行：

```powershell
mvn -f scumthespire/pom.xml "-DstateFile=D:/Program Files/Slay the Spire/startstates/119TLMC49V0AB/01/start.txt" "-Dtest=SearchStateKeyBenchmarkTest" test
```

回归覆盖愤怒／易伤／虚弱与无实体、多段格挡与缓冲、毒伤封顶与折扣、无球槽、负集中、暗球顺序、
消耗引擎燃料、保留格挡、终局临时能力隔离、催化前置条件、跨色卡、姿态退出费用、弃牌触发、
占卜零选／多选确认、检索费用、保留虚无牌反例、合法命令集合与确定性排序。

## 未完成的实战证据

完整游戏引擎 A/B、FAST／DEEP 冒烟和逐段回放验证尚未完成。
本机 ModTheSpire 启动在加载依赖阶段失败，日志报告无法找到 `SaveStateMod`／`stslib`／`LudicrousSpeed`，
尽管独立元数据读取可识别相应 jar。此失败发生在搜索开始之前，不计入战斗胜负。
静态快照评分成功不能证明真实胜率、HP、药水消耗或耗时改善。

固定清单涵盖三类敌人；静默现有精英快照的旧版整数 `drawn_cards` 与当前恢复格式不兼容，筛选时跳过，
没有转换或修改这些快照。需要补录静默精英场景。

对照启动器已隔离游戏、mod、JRE 和 ModTheSpire 配置，使用端口 15125。首次排查时启动器曾改写全局
启用列表，随后按历史可运行列表恢复全部七个加载项；后续启动均使用隔离配置。
现场日志保存在 `debug/character-strategies/comparison/<phase>/runtime/server.log`，不提交运行产物。

启动环境恢复后，从仓库根执行以下命令续做；baseline jar 已在修改代码之前留存：

```powershell
conda run --no-capture-output -n spire python scumthespire/tools/benchmark_characters.py --game-dir "D:/Program Files/Slay the Spire" --baseline-dir debug/character-strategies/baseline --output-dir debug/character-strategies/comparison --phase baseline
conda run --no-capture-output -n spire python scumthespire/tools/benchmark_characters.py --game-dir "D:/Program Files/Slay the Spire" --baseline-dir debug/character-strategies/baseline --output-dir debug/character-strategies/comparison --phase candidate
```

工具复用固定清单并校验源文件哈希，记录每场胜负、HP、剩余药水、耗时、扩展数和回放结果，
两阶段完成后输出 `comparison.json`。任何启动失败、无效配对或回放偏差都不能作为验证通过。
