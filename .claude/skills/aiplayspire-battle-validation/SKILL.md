---
name: aiplayspire-battle-validation
description: 验证 AIPlaySpire 战斗搜索、状态快照和动作回放改动。用于选择 C++ 原生回归、JNI 契约或实机验收，并区分各自的通过口径。
---

# 战斗验证

按改动选择最小验证范围；命令从仓库根目录在 conda `spire` 环境执行。

| 改动或目标 | 验证方式 |
|---|---|
| 原生搜索、模拟或状态导入 | Windows 先执行 `.\battle_engine\build.ps1`，再运行 `python -m unittest discover -s battle_engine/tests -t . -v`。构建和 worker 位置见 [战斗后端文档](../../../docs/BATTLE_ENGINE.md)。 |
| JNI 与 Mod 接入 | 先构建 DLL，再执行 BattleAiMod 的 Maven 测试；用打包后的 JAR 按 [实机步骤](../../../AUTOFIGHT.md)运行 oracle。 |
| Linux 采集与动作回放 | 运行 Framework 回归，再在隔离运行目录以实际 Linux worker 跑完整对局，检查终局、故障和样本隔离。 |

分别报告原生回归、JNI 契约、真实游戏回放和 Linux 整局采集；一项通过不代表另一项。进一步比较需要冻结部署配置及配对样本，见[验收](../../../docs/BATTLE_ENGINE.md)。
