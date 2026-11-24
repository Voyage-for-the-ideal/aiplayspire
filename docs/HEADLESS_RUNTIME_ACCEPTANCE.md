# Headless 四角色实局采集验收

2026-11-17，**四角色基础采集闭环验收完成（4/4）**。服务器串行完成每角色一局 A10 实局，均到达真实败北结算；1458 个动作全部通过独立离线回放。批次 `1794889745710277593`。

本次验收范围为每角色一个固定种子、固定 profile 的完整终局轨迹。多种子、长时间连续运行、全卡牌／事件及胜利线路覆盖仍需后续扩展。

后续四角色各 10 局的固定多种子计划、恢复方式和验证口径见[多种子验收记录](D:/code/aiplayspire/docs/HEADLESS_MULTISEED_ACCEPTANCE.md)，与本页基础 4 局分别计数。

## 运行范围

- 隔离根目录：`/home/ubuntu/headless-spire-poc/runtime-acceptance-v3`。
- IRONCLAD、THE_SILENT、DEFECT、WATCHER 串行运行；A10，输入种子依次为 5050001–5050004。
- 战斗使用现有 C++ FAST 搜索，5000 次展开、250 ms；非战斗使用现有地图路由与 CPU format-12 价值网络。
- 临时 systemd unit `headless-collection-acceptance.service`：单核配额、1536 MiB 内存上限、零 swap、900 秒批次上限、独立网络空间。Java 上限 512 MiB，Serial GC，无 DISPLAY。
- 原 `headless-spire-state.service` 保持 BOOT 只读；原 HTTP 动作口和两个旧全局闸门维持关闭。SoulNPC 与 3681174958 对应 Napcat 独立运行。

## 启动与停止

源码入口：[采集主循环](D:/code/aiplayspire/sts_ai_framework/headless/agent.py)、[实局 JSONL 驱动](D:/code/aiplayspire/deploy/headless/HeadlessSession.java)、[有界启动脚本](D:/code/aiplayspire/deploy/headless/run-collection-acceptance.sh)、[独立离线回放](D:/code/aiplayspire/sts_ai_framework/headless/replay.py)。

部署后的预检与启动：

```bash
bash /home/ubuntu/headless-spire-poc/runtime-acceptance-v3/tools/run-collection-acceptance.sh /home/ubuntu/headless-spire-poc/runtime-acceptance-v3 --preflight
bash /home/ubuntu/headless-spire-poc/runtime-acceptance-v3/tools/run-collection-acceptance.sh /home/ubuntu/headless-spire-poc/runtime-acceptance-v3 --run
sudo systemctl stop headless-collection-acceptance.service
```

启动要求隔离根目录中的 `ALLOW_RUN_SMOKE` 与 `ALLOW_COLLECTION` 两道具名许可。预检只检查输入；`--run` 执行一个有上限的四角色批次，未安装常驻采集服务。

**验收后已收回两道隔离许可**，存入 `reports/permit-archive/ALLOW_{RUN_SMOKE,COLLECTION}.1794889745710277593`。当前预检按设计以 78 退出；采集 unit、Java 对局进程及 C++ worker 均已结束。下一次明确启动批次时，先恢复这两份隔离许可，再执行预检与 `--run`；原全局闸门继续保持关闭。

游戏偏好从隔离 saves 冻结为 `profile-baseline`，配套逐文件 SHA-256 manifest。每个 Java 进程复制独立 profile 到 `sessions`；重试使用相同的 boss-seen、提示及解锁状态，写盘保留在各自 session。引擎与模型保留原版本。

## 数据与校验

输出在隔离目录的 `episodes` 与 `reports`。轨迹为 `headless-episode-v1` gzip JSONL：header、初始观察、动作／后继观察对、footer。每条记录包含连续索引及 SHA-256 链；header 记录角色、种子组、进阶、引擎 commit、RNG policy、模型、profile 与运行文件指纹。

- 仅 `APPLIED` 的动作／后继观察对进入有效前缀；字节预算触发时保留最后一个完整观察前缀。
- 提交绑定原状态 token，后继 driver receipt 绑定方法、参数、请求及序号；重放 ID 保持幂等，执行异常封闭当前 session。
- 普通战斗核对真实公共投影、完整存活怪物状态、卡牌堆、私有字段和八条已建模 RNG 的内部状态。另六条游戏随机流保持不变量，MathUtils 状态独立记录。
- 召唤战的原始目标顺序与死亡条目由 MonsterRoster 公共 ledger 核对；存活怪物私有状态按真实源目标槽位比较，隔离 C++ 回收死父体槽位的存储差异。
- 完成需要实际胜利／死亡界面事实和 TerminalReceipt。中断、预算耗尽、桥语义缺口及预测差异统一为删失，保存 `.aborted.jsonl.gz` 和诊断。
- 数据独立标识 `headless-native-v1`，`legacy_training_admission=false`。现有 checkpoint 与旧训练数据保持原样；本流程只采集与验收。

独立回放重新枚举菜单合法动作，并让 C++ 从每个真实前帧预测已记录动作，再与真实后帧核对：

```bash
R=/home/ubuntu/headless-spire-poc/runtime-acceptance-batch-r10-headless-package.yzx7fmqp
cd "$R/repo"
/home/ubuntu/miniforge3/envs/spire/bin/python -m sts_ai_framework.headless.replay \
  --native "$R/native-bin/battle-engine-worker" "$EPISODE"
```

`EPISODE` 指向要复验的轨迹。当前采集和批次入口分别为
`python -m sts_ai_framework.headless.agent` 和 `python -m sts_ai_framework.headless.batch160`。
启动脚本按传入的 repo 选择对应模块；历史冻结目录保留旧布局、原 worker 和原哈希。
`--allow-censored` 检查故障轨迹的有效前缀，整局验收使用默认完整轨迹检查。回放只运行原生模拟器，无 Java 游戏进程、Agent 或网络动作。

## 最终整批结果

| 角色 | 种子 | 真实终局 | 动作总数 | 独立战斗／菜单复核 | Episode ID |
|---|---:|---|---:|---:|---|
| 铁甲 IRONCLAD | 5050001 | 第 28 层、第二幕败北 | 351 | 205 / 146 | `77998dfa8e9543c2a2c0c66d2a5b38d4` |
| 静默 THE_SILENT | 5050002 | 第 33 层、第二幕败北 | 625 | 370 / 255 | `cda218b46aa84d4ba27ef58d4ed5689b` |
| 故障机器人 DEFECT | 5050003 | 第 22 层、第二幕败北 | 292 | 182 / 110 | `9ebf5d2e4a8448349b40a0874235151d` |
| 观者 WATCHER | 5050004 | 第 16 层、第一幕败北 | 190 | 108 / 82 | `5105a9ca53cb4640ac225e6bdf09992a` |

四条轨迹 `status=complete`、`censored=false`，终局回执来自实际死亡界面。独立复核共 865 个战斗动作、593 个菜单动作，重新计算合法动作、生效证据及战斗状态／RNG，不依赖轨迹中记录的 `APPLIED` 字样。

已修复的实局差异包括 Neow 最大生命、配对小游戏、事件后续阶段、战斗生成卡牌、营火按钮延迟、开箱 bookkeeping、毒伤害、药水／玩具扑翼飞机回血、弃牌 Soul 的临时费用复位、多个 Boss 和盗贼状态恢复。最后补齐百夫长 DEFEND 的随机盟友选择、治疗师的全场缺血总量与连续治疗限制；观者、静默、机器人也在同一最终构建下重新采集并复核。

### 内存、CPU 与隔离

- 整批 unit 运行 **156.977 秒**，CPU **156.928 秒**，单核配额；swap 峰值 **0**，成功退出。
- cgroup 内存峰值 **341536768 字节（325.7 MiB）**。
- `/proc` 采样的 Python＋Java＋C++ 进程合计峰值 RSS **494.1 MiB**、PSS **473.4 MiB**。进程映射统计与 cgroup 对共享页的记账归属不同，分别保留原值。
- 最终回放／回归 unit 成功退出；运行约 52.5 秒，内存峰值 214.0 MiB。
- 原只读服务 PID 74516、重启数 0，仍为 BOOT／MAIN_MENU；`GET /action`、`POST /action` 返回 404，`POST /state` 返回 405。
- 原运行文件 26 项及已安装 systemd unit 指纹保持一致；上游归档 5041 项与固定补丁、profile 39 项、当前原生源码 110 项及轨迹记录的运行指纹均核对通过。
- SoulNPC 仅原单实例，3681174958 的 Napcat 活跃；Napcat2 继续 inactive／disabled。旧部署目录保持删除状态。

### 回归与证据

本地 Windows `spire` 与服务器 Linux `spire` 均通过框架 **391** 项回归；Linux 最终原生构建通过 **204** 项回归。本次原生实际构建／运行平台为 Linux GCC。

```bash
python -m unittest discover -s sts_ai_framework/tests -t . -q
BATTLE_ENGINE_WORKER=/home/ubuntu/headless-spire-poc/runtime-acceptance-v3/native-bin/battle-engine-worker python -m unittest discover -s battle_engine/tests -t . -q
```

服务器证据位于隔离根目录的 `reports/final-acceptance.json`、`reports/final-server-audit.json`，四条完整轨迹位于 `episodes`。本地已下载副本，保存在忽略目录：

- [最终四角色验收清单](D:/code/aiplayspire/debug/headless-runtime-acceptance/accepted-1794889745710277593/reports/final-acceptance.json)
- [服务器隔离与资源审计](D:/code/aiplayspire/debug/headless-runtime-acceptance/accepted-1794889745710277593/reports/final-server-audit.json)
- [独立回放日志](D:/code/aiplayspire/debug/headless-runtime-acceptance/accepted-1794889745710277593/reports/final-audit.log)

清单包含每条轨迹 SHA-256、最终哈希链、终局回执、动作计数、资源采样与独立回放结果；本地副本再次执行 SHA-256 和完整轨迹契约校验。原生执行文件 SHA-256：`09df29e762e889f826fb7034729b794992531d73f7aa90ea738516d6f1b06689`。
