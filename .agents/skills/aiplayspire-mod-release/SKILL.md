---
name: aiplayspire-mod-release
description: AIPlaySpire Java Mod 版本维护与发布。用于改动 Mod 代码后同步版本和描述、核验 Maven 生成的元数据，或按依赖顺序打包部署。
---

# Mod 版本与发布

1. 根据变更分类更新受影响的 Mod：破坏性变更升 major，新功能升 minor，修复或性能变更升 patch。已有尚未发布的版本改动先结合当前 diff 核对，避免为同一改动重复升版。描述应反映实际行为。
2. 修改游戏读取的版本来源：

   | Mod ID | 版本和描述来源（仓库相对路径） |
   |---|---|
   | `SaveStateMod` | [STSStateSaver/pom.xml](../../../STSStateSaver/pom.xml) |
   | `LudicrousSpeed` | [LudicrousSpeed/pom.xml](../../../LudicrousSpeed/pom.xml) |
   | `BattleAiMod` | [scumthespire/pom.xml](../../../scumthespire/pom.xml) |
   | `CommunicationMod` | [StSCommunicationMod 的源 ModTheSpire.json](../../../StSCommunicationMod/src/main/resources/ModTheSpire.json) |

   三个 POM 使用项目级 `<version>`、`<description>`，由 Maven 过滤注入 JSON；保留资源模板中的插值，并正确处理 XML 转义。
3. 仅验证时，从仓库根目录运行 `mvn -f <mod>/pom.xml test`。读取该模块生成的 `target/classes/ModTheSpire.json`，确认可解析且 `version`、`description` 与来源一致。若还要验证 JAR 打包但不部署，在测试之后运行 `mvn -f <mod>/pom.xml package "-Dmaven.antrun.skip=true"`；该开关也会跳过 SaveStateMod 的 Antrun 测试任务，不替代前一步。
4. 需要更新游戏 JAR 时，再用普通 `mvn package` 或根目录的 `bash build_all.sh`。两者均会复制产物到 `_ModTheSpire/mods/`。全量顺序以 [build_all.sh](../../../build_all.sh) 为准；三 Mod 安装与运行说明见 [AUTOFIGHT.md](../../../AUTOFIGHT.md)。
