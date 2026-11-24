# Linux 无图形引擎隔离 PoC

> 第一阶段历史记录。此页的原生 state 缺口现已修复，当前只读 HTTP 服务保持运行；最新部署与验收见 [Headless 只读状态接口](D:/code/aiplayspire/docs/HEADLESS_HTTP_STATE.md)。以下“源码未打补丁／进程退出”等描述对应第一阶段结束时的现场。

2026-11-15 已在现有 4c4g Ubuntu 服务器完成构建、引擎初始化、BOOT 状态读取、5 分钟空载和纯 RNG 对照。资源表现值得继续适配；当前还不是现有采集链的直接替代品，主要工作在状态契约、战斗快照和 RNG 语义上。

本轮未创建角色或地图，未执行对局动作，未启动网关、Agent 或采集服务。所有 PoC 进程已退出，采集闸门保持缺失。

## 部署与清理

- PoC 根目录：`/home/ubuntu/headless-spire-poc`，独立源码、JDK、保存目录和报告。
- 上游：[SlayTheSpireHeadlessEngine 固定提交](https://github.com/GaoPeng415/SlayTheSpireHeadlessEngine/tree/b354a9ee8e118e906d9b9f55ba043100cc1ac6c7)，提交日期 2026-08-18。下载归档 SHA-256 为 `954fe6309a7cad5cd9ea27efbebeb2093fa37f21affd4f040cb13a117d1fe02b`。
- 归档内 5,041 个文件逐一校验一致，上游源码未打补丁。PoC 包装层在 `/home/ubuntu/headless-spire-poc/tools`，编译产物在独立的 `/home/ubuntu/headless-spire-poc/probe-classes`。
- JDK 21.0.10 位于 `/home/ubuntu/headless-spire-poc/toolchains/jdk21`。系统 `/usr/bin/java` 仍指向 Java 8；共享 `/home/ubuntu/miniforge3/envs/spire` 保留。
- 已删除服务器旧代码、运行目录 `/home/ubuntu/aiplayspire`、`/home/ubuntu/aiplayspire-runtime`、`/home/ubuntu/sts`，并移除对应 systemd、日志限额与采集配置。旧报告和非敏感配置归档在 `/home/ubuntu/aiplayspire-acceptance-archive/20261115`。
- 原始游戏参考 JAR 仅用于独立 JVM 中的 RNG 对照，保存在 `/home/ubuntu/headless-spire-poc/reference/desktop-1.0.jar`，SHA-256 为 `cfad868ac8d65a88e71a0bf096fb09f78811e553effe0787c5309a655e081673`。
- SoulNPC 和 3681174958 对应的 Napcat 服务保持运行；另一路 `napcat2.service` 保持停止、禁用。其 Xvfb 与系统共享依赖保留。

## 实测结果

主报告：`/home/ubuntu/headless-spire-poc/reports/boot-fd4480d0655e40059bfe8dadb0dbc2ce/summary.json`。

| 项目 | 结果 |
|---|---|
| 上游源码构建 | 成功，CPU 配额单核，25.81 秒，编译峰值 RSS 279.55 MiB |
| 初始化到首个只读状态 | 1.39 秒 |
| BOOT 状态读取 | 260 次，状态一致，持续 300 秒 |
| Java 峰值 RSS / 采样峰值 PSS | 73.65 / 70.74 MiB |
| 首次 / 最后 RSS | 73.63 / 72.87 MiB |
| 5 分钟轮询期间 CPU | 累计 0.38 CPU 秒，平均占单核 0.127% |
| Java 换页 / cgroup OOM | 0 MiB / 0 次 |
| 图形后端 | Mock，GL 为空，DISPLAY 与 WAYLAND_DISPLAY 均未设置 |
| 原生图形音频库映射 | 未出现 GL、GLX、EGL、X11、LWJGL、OpenAL |
| 对局数据 | 角色、地图、房间、种子均未初始化，floor=0、act=0 |
| 保存文件 | 仅 19 个初始化偏好／显示设置文件，无 autosave、run 或采集轨迹 |

旧图形基线曾测得主菜单 RSS 峰值约 2,002 MiB、CPU 约单核 102%。本轮是无模组的 BOOT 阶段，结果说明初始化空载负担显著降低；整局峰值、搜索开销和吞吐量留给独立的对局验收。

测试置于临时 systemd unit：MemoryMax=768M、MemorySwapMax=0、CPUQuota=100%、TasksMax=64；私有网络、设备和临时目录，系统／home 只读，仅 PoC 目录可写。Java 使用 `-Xms32m -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -Djava.awt.headless=true`。验收后临时 unit 已回收，没有安装常驻启动项。

## 未开局状态接口的实际缺口

上游原生 CLI 只接收 `state`、`quit` 的烟测中，`state` 因 `currMapNode` 为空触发 NullPointerException；`GameKernel.phase()` 进入 `getCurrRoom()` 的路径尚未处理开局前状态。

因此，本轮读取的是明确标记为 `headless-boot-diagnostics-v1` 的只读包装层：直接核查角色、地图、房间、种子和图形后端，并保留原生观察接口的异常。报告 `native_state_passed=false`；260 次稳定读取指 BOOT 诊断，不代表原生业务状态接口通过验收。

包装层只接受整行 `state` / `quit`，`newRun` 在子类中被阻断。Python 发送端也做相同白名单校验。未发送 `new`、`step`、`unlockAll` 或其他动作命令。上游没有原生 HTTP `/state`、`/action`、`/card_info` 服务，本轮也未伪装启动这些端点。

## RNG 对照与差异

同一个已编译探针、同一个独立 JDK、分别加载原始 JAR 与 headless classpath，且不调用游戏 bootstrap 或 newRun：

- 8 个种子，各 128 轮 RandomXS128 与游戏 Random 混合调用，检查数值位模式、底层状态、counter、copy 和 counter 重建；另测 2,001 个 MathUtils 点。两侧 **4,065 行完全一致**，SHA-256 同为 `2b274e3ecdc16ef5c9dea80ac8500490c66ac51546425cc3000446325dec1994`。
- 独立 `copy-global` 探针确认差异：原版 `Random.copy()` 先创建无参 Random，推进全局 MathUtils 随机流；headless 直接复制状态，保留全局流位置。复制出的局部 RNG 后续值仍一致。
- 上游另外引入第 14 个游戏流 `headlessRng`，将部分原先使用全局流或无种子 shuffle 的路径改到该流；如随机变牌、诅咒、部分怪物选取和回合结束弃牌触发顺序。`newRun()` 还按 run seed 重置 MathUtils 全局流。这些是确定性策略变化，基础 RNG 一致并不保证同种子整局轨迹一致。参见[上游 RNG 审计](https://github.com/GaoPeng415/SlayTheSpireHeadlessEngine/blob/b354a9ee8e118e906d9b9f55ba043100cc1ac6c7/docs/RNG_AUDIT.md)。
- `HeadlessRngState` 在内存中保存 14 个游戏流的两个 state word 与 counter，外加 MathUtils 的两个 state word。现有 C++ 快照桥使用六个战斗 RNG counter，格式与作用域不同。
- 静态检查还发现 `loadSeeds()` 将 `headlessRng` 重置为 `new Random(Settings.seed)`，原生保存结构中未发现对应状态字段。恢复连续性的验收需要补全此流的持久化，仅原版 autosave counter 尚不足以覆盖它。

本轮 RNG 证据限定为纯算法对照和静态调用点审计。地图、事件、战斗、存取档后的轨迹差异尚未执行对局验证；后续数据应记录引擎提交与 RNG policy 并独立分组。

## 现有接口适配成本

以下为源码契约审计的工程量判断，不是已实现功能。

| 接入层 | 成本 | 具体工作 |
|---|---|---|
| BOOT 与只读 HTTP `/state` | 低 | 补开局前生命周期、空节点处理和固定 JSON 响应；串行访问引擎，保留关闭动作闸门 |
| Agent 状态与非战斗决策 | 中到高 | 将 phase 映射为 screen_type、player.hp/maxHp 映射为 current_hp/max_hp；提供 choice_list、合法动作与生效确认；完整保留事件多步语义 |
| format-12 价值输入 | 中到高 | 补钥匙值与 known mask、当幕已走历史、事件概率、进阶和角色；对齐真实唯一地图边和可见 boss。当前 masterDeck 非 detailed 输出仅 id/name，缺少升级次数，需显式补齐，不从展示名称猜测 |
| 卡牌与动作身份 | 中 | 当前观察输出缺少现有 Card 模型必需的 uuid/index；手牌 play 为 1-based、怪物目标为 0-based，需统一索引并校验 expected_before/after；补 `/card_info` 及基础永久属性 |
| 四个现有 mod | 高 | 上游 build 不经过 ModTheSpire/BaseMod，没有加载仓库四个 mod；Communication、SaveState、LudicrousSpeed、BattleAi 的定制语义需要移植或重建桥接，JAR 不属于即插即用路径 |
| 现有 C++ 搜索网关 | 高 | 原入口读取 SaveState JSON 文件与六个 RNG counter；需新增 headless 全量快照导出、协议转换、单动作保护和回放差异检查。HeadlessObservation 不是可恢复战斗快照 |
| 采集与数据契约 | 中 | newRun 固定设置 seedSet=true，而现有 live 导出排除 chose_seed=true 的局；要显式区分实验 run mode，避免静默修改标志或混入原采集数据 |

建议顺序：先补原生 BOOT 状态和只读 HTTP 契约，再以合成状态做字段／快照契约测试；后续对局和 RNG 回放需单独开通。保留当前模型格式、checkpoint 与采集闸门。

## 工具与复验

本地新增工具位于 `D:/code/aiplayspire/deploy/headless`；Python 仅使用标准库，4 项白名单／资源参数／状态守卫测试在 Windows spire 和服务器 spire 均通过，两份 shell 脚本 `bash -n` 通过。未修改四个 mod、训练模型或原有用户改动。

服务器上已有固定源码与 JDK，重新构建：

```bash
P=/home/ubuntu/headless-spire-poc
sudo systemd-run --unit=headless-spire-poc-build --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$P" \
  -p MemoryMax=1280M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=64 \
  /bin/bash "$P/tools/build-poc.sh" "$P"
```

只读复验与单元测试：

```bash
bash /home/ubuntu/headless-spire-poc/tools/run-poc.sh /home/ubuntu/headless-spire-poc
/home/ubuntu/miniforge3/envs/spire/bin/python -m unittest discover \
  -s /home/ubuntu/headless-spire-poc/tools -p 'test_*.py' -v
```

报告已同步到 `D:/code/aiplayspire/debug/headless-poc/20261115/reports`：`summary.json` 在对应 boot 子目录；同级包含 `native-cli.log`、`source-integrity.json`、`cleanup-result.json`、`final-state.json` 和构建资源记录。最终核查：旧目录缺失，5000/5125 无监听，游戏／PoC／Agent 进程均为空，旧服务为 not-found；共享业务服务状态保持原样。
