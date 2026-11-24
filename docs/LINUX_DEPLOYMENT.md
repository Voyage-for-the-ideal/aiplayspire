> 历史记录：下文的图形实例、网关及 systemd 部署已退役。当前无头数据采集使用 [战斗内核与 Linux 验收](BATTLE_ENGINE.md)。

# Linux 单路采集部署（4c4g）

> 2026-11-15 状态：本文对应的服务器旧部署已退役并删除，以下命令仅作历史基线，当前入口见 `D:/code/aiplayspire/docs/HEADLESS_HTTP_STATE.md`。共享 spire 环境和旧验收报告保留，采集闸门关闭。

部署链：一个 Java 8 游戏＋一个 Xvfb＋一个 C++ 常驻 worker／标准库网关＋一个 CPU Agent。FAST 为 5,000 次扩展／250 ms，Java 搜索回退关闭，价值推理在 Agent 内运行。四角色完整实机交互仍待验收，首批固定铁甲。本文所有路径针对当前 Ubuntu 服务器。

## 入口与默认状态

- 源码：`/home/ubuntu/aiplayspire`；独立运行目录：`/home/ubuntu/aiplayspire-runtime`。旧 `/home/ubuntu/sts` 和其他项目配置保持原样。
- CPU 环境：`/home/ubuntu/miniforge3/envs/spire`，Python 3.11，CPU-only PyTorch 2.7.1。不复制其他项目凭据。
- `battle_engine/build.sh [jobs]`：默认单编译任务；`start.sh`：启动网关；`start-game.sh`：启动唯一游戏。
- `deploy/linux/preflight.sh --mode offline --model`：静态资产、源码／模组／模型指纹、CPU 模型加载、资源余量检查，不连接游戏。
- `prepare-runtime.py`：创建独立 profile、FAST 配置、1024×576／30 FPS 和运行清单。存在不同文件时停止，留给操作者核查。
- `install-systemd.sh`：安装并校验 unit、日志限额和轮转定义；不执行 enable/start。
- `validate-offline.sh`：离线回归、CPU 重复评分、原生合成快照与资源报告。不启动游戏或真实采集。

游戏脚本要求 `ALLOW_GAME` 文件；Agent 还要求 `ALLOW_COLLECTION` 文件。两者位于独立运行目录，安装器保持它们缺失。systemd 和直接脚本两层检查闸门；每类进程另有 flock 单实例锁。`--no-auto-restart` 不是 dry-run。

## 安装与打包

本机先按依赖顺序运行 `build_all.sh`，或分别 `mvn -f <mod>/pom.xml package`。四个模组使用当前源码声明版本，不额外改变版本。打包器从工作区读取源码，包含未提交改动、只读内容目录、四个显式 JAR 和 format-12 checkpoint，生成逐文件 SHA-256 清单；排除凭据、训练数据、历史日志、Windows worker 和构建目录。

```powershell
& D:/miniconda3/envs/spire/python.exe D:/code/aiplayspire/deploy/linux/package.py --output D:/code/aiplayspire/debug/linux-deploy/release.tar.gz
```

首次部署在空的 `/home/ubuntu/aiplayspire` 解包。依赖安装器固定 Miniforge 26.9.4-2 及官方 SHA-256，不修改 shell 初始化文件；创建名为 spire 的 CPU 环境：

```bash
cd /home/ubuntu/aiplayspire
# LWJGL 2 会实际执行 xrandr；仅安装 Xvfb 不足以枚举显示模式。
sudo env DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=l apt-get install --no-install-recommends -y x11-xserver-utils
bash deploy/linux/install-cpu.sh
# 若需使用服务器已有代理，显式设置 AIP_INSTALL_PROXY=http://127.0.0.1:7890。
bash battle_engine/build.sh 1
source deploy/linux/common.sh
"$AIP_PYTHON" deploy/linux/prepare-runtime.py --source-game /home/ubuntu/sts/lib --menu-profile AIPlaySpire
bash deploy/linux/install-systemd.sh
bash deploy/linux/preflight.sh --mode offline --model
```

安装外部依赖需任务明确许可。CPU requirements 中的 scikit-learn 用于现有离线回归／合成闭环，matplotlib 使用 Agg 后端生成回归报告；CUDA、FastAPI 服务、多 worker 和完整训练均未加入。

## 离线验收

```bash
sudo systemd-run --wait --collect --unit=aiplayspire-offline-check \
  --uid=ubuntu --gid=ubuntu --slice=aiplayspire.slice --property=MemoryAccounting=yes \
  /bin/bash /home/ubuntu/aiplayspire/deploy/linux/validate-offline.sh
```

报告位于运行目录 `reports/offline-<UUID>`：每套测试日志、GNU time 峰值 RSS、200 次真实 checkpoint 重复评分／PSS、40 次合成原生请求、cgroup 内存峰值／压力／OOM 计数。原生测试通过 `BATTLE_ENGINE_WORKER` 指向 ELF，避免误找 `.exe` 后跳过。

`test_live_learning`／`test_multiscale` 包含隔离临时目录里的合成 CPU 训练／重载契约，不训练生产模型，不改写源 checkpoint，也不接触真实游戏。历史归档缺失的跳过项单独报告。

短测要求：全部可运行测试通过，`oom`／`oom_kill` 为 0，重复评分热身后的 PSS 增长小于 100 MiB，无游戏和采集进程，两个闸门缺失，5000／5124／5125／5126 无残留监听。离线峰值不代表真实游戏整套峰值。

Linux 验收还覆盖两项可移植性修复：C++ 执行预算自持临时 deadline；楼层 MAE 在相减前转为 float64，避免 float32 相减／汇总的额外舍入。相同输入、均分终点的协议测试改用单行批次，消除不同平台批矩阵尾行舍入对精确 MAE 平局的影响。选模门槛本身、生产 checkpoint 和推理公式保持原样。

## 后续开通（需单独安排）

先只打开游戏闸门并验收主菜单／渲染／结构化状态，再检查角色和 A10 解锁进度。`prepare-runtime.py` 为 ModTheSpire 的 Workshop 子进程创建 `game/jre/bin/java` 链接；`--jre51` 不代替该查找路径。显式 `--menu-profile` 只初始化空的存档槽选择器，不改变已有 profile、解锁或对局，运行前要求游戏停止。未复制游戏存档的新 profile 不保证 A10 已解锁。准备 LLM 凭据时，使用独立的 `/etc/aiplayspire/credentials.env`，root 所有、0600；不保存到源码或清单。

主菜单验收只启动 `aiplayspire-gateway.service` 和 `aiplayspire-game.service`，不启动 target 或 collector，`ALLOW_COLLECTION` 始终缺失。状态必须同时为 `screen_type=MAIN_MENU`、`choice_list` 含 `play`；空的 choice_list 可能仍是首次存档槽弹窗，不算验收通过。可在打开游戏闸门并手动启动上述两个服务后执行只读短测：

```bash
source /home/ubuntu/aiplayspire/deploy/linux/common.sh
"$AIP_PYTHON" /home/ubuntu/aiplayspire/deploy/linux/idle-acceptance.py --duration 600 --interval 5
```

该工具只发送 GET `/state` 和网关 PING，逐次核对主菜单、单 JVM、服务 PID／重启计数、Agent 停止、采集闸门关闭、主机内存余量和 trace 未变化，保存 RSS／PSS、cgroup 内存／CPU／压力及接口延迟；不提交 `/action`。报告在 `reports/idle-<UUID>`。10 分钟空载通过不等同于整局或 24 小时验收。

只有游戏空载验收和凭据／进阶检查完成后，才打开采集闸门。`AIP_MAX_RUNS=1` 为默认首批上限，随后 5 局、24 小时逐级放开。每次 Agent 启动创建全新 trace 子目录，正常停止发送 SIGINT，使未完成对局以删失结束；不轮转采集 JSONL。关闭目标时停止 `aiplayspire.target`。若游戏或网关异常退出，BindsTo 停止 Agent；后台服务各自限次重启，Agent 随依赖停止后需重新核查并启动目标，不承诺自动续采。

统一 slice：MemoryHigh=2304M、MemoryMax=2560M、MemorySwapMax=256M、CPUQuota=250%。真实运行还需检查主机 MemAvailable≥512 MiB、无持续换页、同类状态局末内存不逐局上涨，且战斗、楼层、结算确实持续推进。现有战斗停滞监测为日志告警，尚无自动推进／重开 watchdog。

游戏和网关 unit 使用 cgroup IPAddressDeny／Allow 限制 loopback（安装后核查内核支持情况）；游戏侧不使用 Steam Workshop 网络，仅加载本地六个模组。日志独立 journal namespace：磁盘 100M、内存 30M、7 天；诊断文件轮转，trace 保持原样。
