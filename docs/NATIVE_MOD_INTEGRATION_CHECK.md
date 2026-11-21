# 三 Mod 整合验收记录

仅记录本次 Windows 制品与本地实测，不作为 Linux 采数策略的晋级报告。

| 项目 | 证据 |
|---|---|
| 发布制品 | `AIPlaySpire-AutoBattle-2.0.0-windows-x64.zip`，SHA-256 `b1ba2d3319d608c4f5335f484e3f4ff8ae95735695288d6492c2b475602fc331` |
| ZIP 内容 | SaveStateMod 1.6.1、LudicrousSpeed 1.3.0、BattleAiMod 2.0.0。已展开核对三个元数据、无旧搜索服务类、无 EXE、JAR 内 DLL 与构建产物逐字节一致。 |
| Windows JNI DLL | SHA-256 `dfaa6391f2c265f2d4cbb6796e43655760f963ee2d8003613d15ff830f2b2ad9` |
| 自动化 | 原生/协议 Python 337 项（4 跳过）；Framework 574 项；BattleAiMod Maven 141 项（2 跳过）。以上均无失败。 |
| 手动启动/接管 | 真实游戏、游戏自带 64 位 JRE、隔离配置。快照 `119TLMC49V0AB/01`：启动后在搜索中停止，100 帧内未恢复旧控制器；重新开启，9 动作跨回合获胜，剩余 80 HP。证据目录 `battle_engine/out/jni-smoke-1795247597080084000`。 |
| 自动启动/短路径 | 同一快照、BALANCED、4 步上限：9 动作获胜，剩余 80 HP；2 次直接复用已核对的短路径动作。证据目录 `battle_engine/out/jni-smoke-1795247628020801300`。此为动作/状态验证，不是测速。 |
| 用药/选牌/异常 | 快照 `50RINSC5VRV77/04`：验证了 3 次出牌、POTION 和 CARD_REWARD_SELECT。下一步头槌将打开尚未支持的弃牌堆选择；新前置检查在出牌前停止、记录快照。这场按未完成统计。2.0.1 的复测结果见下。目录 `battle_engine/out/jni-smoke-1795247655703471000`。 |

本机未构建 Linux worker，也未在采数服务器切换策略或运行新一轮对局。固定生产 worker、预算、模型、分层 A10 留出集及 Linux 配对吞吐的下一步见[共享战斗核心](BATTLE_ENGINE.md)。测试不会写入玩家正在使用的 Mod 或进度。

## 2.0.1：桌面头槌续接

同一个失败快照 `50RINSC5VRV77/04`，用游戏自带的 64 位 JRE 运行新 JAR：20 个动作，打出头槌两次并通过两次弃牌堆选牌与卡牌实例校验，最终 80 HP 获胜。证据目录：`battle_engine/out/jni-smoke-1795248215144936800`。桌面头槌只接收已验证的标准清理队列，遇到未知额外动作仍停下；Linux 已有的续接分支不受影响。

原生回归 340 项（4 跳过）、Framework 574 项、Mod Maven 141 项（2 跳过），均无失败。2.0.1 已部署到游戏的 `mods/BattleAiMod.jar`，JAR SHA-256 为 `6056ed9a1c05e1b135febb7ecc69cacc0b484e92f9c746aa8e21b57f6a8f4c3b`。

## 2.0.2：牌堆选择盘点

| 同类动作 | 桌面处理 |
|---|---|
| 预见：当头一棒（Just Lucky）、第三只眼（Third Eye）、看破命运（Cut Through Fate）、洞察未来（Foresight） | 校验候选确实来自牌库顶。逐张按 UUID 核对已选项，支持零张和多张的确认；确认后核对牌堆及后续抽牌等效果。三张即时牌和回合开始能力均走过实机选择。 |
| 取牌：检索（Seek）、全息影像（Hologram）、秘密武器、秘密技法 | 识别桌面选择动作，按实际选项顺序执行。核对来源牌堆、过滤条件、已选卡和在途卡；这四张均走过实机选择。 |
| 战吼（Warcry） | 校验手牌选择状态；即使开启快速手选也走真实确认，再移动到牌库顶。通过实机出牌、选择、确认。 |
| 冥想（Meditate） | 校验弃牌堆选择身份；弃牌数量不超过需求时直接取回，不虚构选择窗口。 |
| 其他相似机制 | 掘墓、预备（Setup）、深谋远虑（Forethought）、通晓万物（Omniscience）的桌面组合，以及药水和遗物介入时的额外动作队列，仍需各自回归；这次不把未实测的组合视为已验证。 |

Linux 使用 A5 运行模板的新隔离发布根目录。同一份真实历史战斗快照在新 worker 上用了 `legacy / DEEP`、15,000 次展开和 `timeout_ms=0`；没有为这次验收启动整局采集。

2.0.2 已覆盖游戏目录的同名 JAR，未保留旧 JAR。战吼实机证据：`battle_engine/out/jni-smoke-1795249418002736700`；预见能力证据：`battle_engine/out/jni-smoke-1795249175355567300`。
