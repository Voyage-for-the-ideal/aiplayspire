# Headless 未开局状态与只读 HTTP 验收

> 2026-11-16：新增对局字段的[离线映射契约与回归](D:/code/aiplayspire/docs/HEADLESS_STATE_CONTRACT.md)。本页的在线服务仍仅支持未开局只读状态，未接入对局映射或动作。

2026-11-15，服务器上的原生 `state` 空节点异常已修复，兼容现有客户端的只读 HTTP 接口已部署并通过 10 分钟空载验收。当前只读服务保持运行，地址为 `http://127.0.0.1:5000/state`，无开机自启配置。角色、地图、房间和 run RNG 均未初始化，未开局、未启动采集，两个采集闸门持续缺失。

## 原生修复

基于上游固定提交 `b354a9ee8e118e906d9b9f55ba043100cc1ac6c7`，仅修改两份上游源码：

- `GameKernel.phase()`：无玩家、无节点时返回 `BOOT`，且在处理 FTUE 之前返回，保持读操作无推进；房间过渡时先检查节点再调用 `getCurrRoom()`。
- `HeadlessObservation.build()`：允许过渡期空 phase，读取房间前检查 `currMapNode`。

补丁由 `D:/code/aiplayspire/deploy/headless/patch_upstream.py` 重放。它先校验固定归档和全部 5,041 个原始文件，接受原版或精确匹配的已补丁版本；其他源码差异会中止，避免覆盖现场改动。支持幂等重跑，生成逐文件原版／补丁后 SHA-256 和 unified diff。

原生 CLI 实测仅发送 `state`、`quit`，成功返回 `phase=BOOT`，不再出现空节点异常。Java 回归做了 200 轮原生观察与兼容响应检查，并确认读取前后全局 RNG 状态不变。未改动四个现有 mod 或 RNG 策略。

## HTTP 契约

`D:/code/aiplayspire/deploy/headless/ReadonlyStateServer.java` 使用现有 JDK HTTP server 和 Gson，无新增依赖。启动前同时检查原生观察和 JSON 序列化；返回示例：

```json
{
  "screen_type": "MAIN_MENU",
  "choice_list": [],
  "can_proceed": false,
  "can_cancel": false,
  "headless": {
    "schema": "headless-readonly-state-v1",
    "engine_phase": "BOOT",
    "read_only": true,
    "run_started": false,
    "collection_enabled": false,
    "player_initialized": false,
    "map_initialized": false,
    "room_initialized": false,
    "seed_initialized": false,
    "rng_streams_initialized": false,
    "floor": 0,
    "act": 0,
    "graphics_type": "Mock",
    "gl_present": false
  }
}
```

前四个字段对应 CommunicationMod 的未开局格式。`MAIN_MENU` 是客户端生命周期映射，原生引擎仍为 BOOT，并非运行了图形菜单。只读服务不列出 `play` 等选项，`headless` 扩展用于验收诊断。根级不填造 player、floor、act、角色或进阶，现有 `GameState` 解析后这些字段保持 None；额外诊断字段由现有模型忽略。

服务器隔离复制了当前仓库的 `__init__.py`、`models.py`、`game_client.py` 三份源文件作契约检查，没有复制 `.env`、Agent 入口或采集配置。实际 `GameClient().get_state()` 与直接 `GameState` 解析均通过，文件指纹记入报告。

| 请求 | 返回 |
|---|---|
| `GET /state` | 200，UTF-8 JSON，`Cache-Control: no-store`、`X-Headless-Read-Only: true` |
| `POST/PUT/DELETE/HEAD /state` | 405，`Allow: GET` |
| `GET /state?probe=1` | 400 |
| `GET/POST /action` | 404，无动作处理器、队列或命令转发 |
| `/card_info`、未知路径、编码变体、`/state/extra` | 404 |

负向方法测试只发送空请求体，未发送游戏动作 payload。所有状态读取串行进入单个引擎；线程池和排队长度有限。每次 GET `/state` 检查玩家／地图／种子／14 个 run RNG 未初始化及采集闸门缺失；条件不满足时返回 503，不伪造正常菜单响应。内核子类也阻断 `newRun`，进程不启动 CLI 或游戏 update loop。

使用可公开构造的 ArrayList 表示空选择列表，避开旧 Gson 对 JDK 21 内部空列表类的反射问题；未升级依赖或添加 `--add-opens`。

## 空载实测

主报告：`/home/ubuntu/headless-spire-poc/reports/http-97bead8147be43f291b714b85d1e0ea6/summary.json`。

| 项目 | 结果 |
|---|---|
| 稳定性采样 | 600.004 秒，301 次 HTTP 状态校验，另有 1 次真实 GameClient 读取 |
| 兼容状态 | 始终 MAIN_MENU／原生 BOOT，响应字节一致 |
| 进程与重启 | PID 74516 全程一致，自动重启 0 次 |
| Java 峰值 RSS / 采样峰值 PSS | 80.23 / 77.25 MiB |
| 首次 / 最后采样 RSS | 79.57 / 80.23 MiB |
| 轮询期 CPU | 单核口径平均 0.168% |
| HTTP 延迟 | P95 6.08 ms，最大 49.67 ms，含预热和四路并发读取 |
| 换页 / OOM | 0 MiB / 0 次 |
| 只读负向请求 | 10 项全部符合预期 |
| 图形后端 | Mock；无 GL/GLX/EGL/X11/LWJGL/OpenAL 原生库映射 |
| 保存目录 | 19 个初始化偏好／显示设置文件，验收前后 SHA-256 完全一致；无对局存档或采集轨迹 |
| RNG 回归 | 4,065 行基础对照仍完全一致；上游既有 copy/global 差异保留 |

20:22 间隔复核：同一 PID 仍运行，自动重启仍为 0，当前 RSS 79.87 MiB、历史高水位 84.08 MiB、换页 0；GET 继续返回 BOOT。该次复核单独保存在 `readonly-final-state.json`。

Python 测试在服务器 spire 环境 **13/13 通过**，包含原生状态守卫、当前模型兼容、补丁幂等／指纹／漂移保护，以及模拟目录内的真实启动闸门测试。Windows 执行 12 项通过，1 项 Linux shell 守卫留给服务器执行且已通过。Java 零开局回归与 shell 语法、systemd unit 校验均通过。

本次兼容范围是未开局生命周期。对局状态、动作、模组功能迁移、战斗快照和整局 RNG 轨迹仍保持独立待适配状态。

## 服务配置与复验

- 根目录：`/home/ubuntu/headless-spire-poc`。只读服务：`headless-spire-state.service`；定义无 Install 段，不参与任何开机 target。
- 唯一监听：`127.0.0.1:5000`。网关和 Agent 均未启动，旧服务仍为 not-found。
- JDK 21 使用独立前缀；系统 Java 8 和共享 spire 环境保持原样。
- MemoryHigh=256M、MemoryMax=768M、MemorySwapMax=0、CPUQuota=100%、TasksMax=64。
- 只读系统与 home，唯一可写树为 `/home/ubuntu/headless-spire-poc/saves`，每次启动新建独立 profile；私有设备和临时目录，网络仅允许 loopback，无新 Xvfb。
- 两个真实 `ALLOW_COLLECTION` 闸门缺失；启动脚本检查普通文件和符号链接，每次 GET `/state` 再次检查。SoulNPC 与保留的 Napcat 服务维持运行，另一路仍停止、禁用。

服务器上查看状态与停止只读服务：

```bash
curl --noproxy '*' -fsS http://127.0.0.1:5000/state
systemctl status headless-spire-state.service --no-pager
# 按需停止的仅是此只读组件
sudo systemctl stop headless-spire-state.service
```

修改后重建时先停止此服务，再执行隔离构建和安装：

```bash
P=/home/ubuntu/headless-spire-poc
sudo systemctl stop headless-spire-state.service
sudo systemd-run --unit=headless-spire-state-build --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$P" \
  -p MemoryMax=1280M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=64 \
  /bin/bash "$P/tools/build-poc.sh" "$P"
bash "$P/tools/install-state.sh"
sudo systemctl start headless-spire-state.service
```

单元测试和完整只读 HTTP 复验：

```bash
P=/home/ubuntu/headless-spire-poc
PYTHONPATH="$P/contracts" /home/ubuntu/miniforge3/envs/spire/bin/python \
  -m unittest discover -s "$P/tools" -p 'test_*.py' -v
/home/ubuntu/miniforge3/envs/spire/bin/python "$P/tools/http_acceptance.py" \
  --root "$P" --contract-root "$P/contracts" --duration 600 --interval 5
```

本地报告在 `D:/code/aiplayspire/debug/headless-poc/20261115/reports`：对应 http 子目录包含 summary、采样、原生 CLI 和 RNG 对照；同级包含 `pre-run-state.patch`、`patched-source-integrity.json`、`http-tooling-manifest.json`、`boot-state-test.log`、Python 测试记录、当前进程日志和最终现场状态。
