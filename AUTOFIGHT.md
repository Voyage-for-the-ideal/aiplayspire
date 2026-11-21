# 游戏内自动战斗（Windows x64）

**三个战斗 Mod，一个游戏进程。** 把发布 ZIP 中的 `SaveStateMod.jar`、`LudicrousSpeed.jar`、`BattleAiMod.jar` 放进游戏的 mods 目录，替换旧版本，避免同一个 Mod 出现两次。用 ModTheSpire 启动，勾选这三个 Mod 和基础依赖 BaseMod、StSLib。单独自动战斗不依赖 CommunicationMod；需要非战斗 Python agent 的玩家仍可另外安装它。

- 进战斗后用 **Start Steve** 启动，再点一次交回手动操作。Mod 选项支持自动开始和搜索档位。
- SaveStateMod 读取真实状态，BattleAiMod 在后台通过 JNI 调用 JAR 内的 C++ 模拟器，LudicrousSpeed 在游戏线程执行动作。玩家无需安装 Python、C++ 编译器、启动服务或配置端口。
- 原生库会按内容哈希解压到当前用户目录下的 `.aiplayspire/native` 缓存。此包适用于 **64 位 Windows JVM**；Linux 采集走 worker，不加载 Windows DLL。
- 遇到未支持状态、命令前提改变或结果失配，停止本场自动战斗；可查看游戏工作目录里的 `battle-native-diagnostics`。下次仍可主动开启。JNI 与游戏同进程，原生访问违规可能导致游戏退出，不具备子进程崩溃隔离。

## 从源码构建（仓库根目录）

```powershell
.\battle_engine\build.ps1
mvn -f scumthespire/pom.xml clean test
mvn -f scumthespire/pom.xml package "-Dmaven.antrun.skip=true"
.\battle_engine\package_bundle.ps1 -DependencyDirectory 'D:\Program Files\Slay the Spire\mods' -OutputZip "$PWD\battle_engine\out\autobattle-release.zip"
```

构建依赖现有 JDK（设置 JAVA_HOME）、Maven、x64 C++17 工具链，以及项目已有的游戏/Mod 编译依赖。打包脚本从指定目录取配套的 SaveStateMod 和 LudicrousSpeed，再加入新构建的 BattleAiMod，拒绝覆盖已有 ZIP。需要重编配套 Mod 时依次构建 STSStateSaver、LudicrousSpeed；`bash build_all.sh` 另会部署用于完整游戏 agent 的第四个 Mod。

## 验证真实游戏，不动玩家存档

```powershell
python -m battle_engine.tools.oracle --game-dir 'D:\Program Files\Slay the Spire' --snapshot '<真实战斗 start.txt>' --full-fight
```

这是隔离的真实游戏进程：使用发布 JAR、游戏自带 JRE、复制的配置，以及只用于验收的输入/快照测试补丁。默认驱动 Start Steve 使用的同一开关；加 `--auto-start` 验证自动启动，加 `--test-takeover` 验证搜索过程中停止、迟到响应被丢弃并再次启动。完整战斗保留日志、动作轨迹、胜负、净失血、用药和 DLL 哈希。测试补丁不进入玩家的安装包。

搜索档位、去重、批量采集及能力边界见[共享战斗核心](docs/BATTLE_ENGINE.md)。旧双实例说明仅供考古，见[历史设计记录](AUTOFIGHT_HISTORICAL.md)。
