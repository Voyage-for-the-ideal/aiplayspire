# 原种子 5 处确认 → 双进程 24 局

后续结果：v2 第一阶段 4/5 通过，星盘未通过，因此 24 局未启动。星盘在 2026-11-18 完成修复及单独原种子实局验证，见[修复记录](D:/code/aiplayspire/docs/HEADLESS_ORRERY_RETURN_FIX.md)。原 v2 报告保持。

隔离根目录：`/home/ubuntu/headless-spire-poc/runtime-acceptance-batch24-v2`。一次提交给 systemd，服务器自行完成两个阶段及最终报告，聊天侧只确认启动，不持续轮询。

已于 2026-11-17 单次提交，启动确认 supervisor／pilot 均为 active：supervisor PID **861705**，invocation `1e35c6c2111d4508a7fe8f5e18fb6ad4`；pilot PID **861710**。确认时两个完整批次 lane 尚未启动、其 scoped 许可尚未创建。该记录是启动证据，实际阶段结果以最终统一报告为准。

## 第一阶段：原种子定点实局

| 原 case | 种子 | 必须实际观察并校验的修复 |
|---|---:|---|
| IRONCLAD-03 | 5050109 | Headbutt 打出、进入 GRID、选牌续接完成 |
| IRONCLAD-06 | 5050121 | Headbutt 第二处 GRID 与清理边界 |
| DEFECT-03 | 5050111 | Rip and Tear 无指定目标，真实随机选敌 |
| THE_SILENT-04 | 5050114 | Dolly's Mirror 增加副本、保留原牌与金币、不误删牌 |
| IRONCLAD-05 | 5050117 | Orrery 最后一个被拒奖励返回商店，不反复重开 |

使用冻结 profile 和原种子真正开局，按原来已验证的动作前缀重放到故障位置。逐步校验合法动作、生效、状态和 RNG；原轨迹与新实局卡牌 UUID 使用持久一一映射，14 条 gameplay RNG 精确匹配，Math cosmetic RNG 单独保留。达到目标并验证后有意结束该局，轨迹继续标记删失，不伪造游戏胜负。

单局遇差异保存诊断并继续其余目标。5 处全部通过后，独立校验器重新读取实际轨迹、重放动作、复核原种子前缀与目标覆盖，生成 `reports/pilot-gate.json`。只有该门槛通过，才打开第二阶段的 scoped 许可；失败时结束任务并保留统一报告。

## 第二阶段：24 局完整复跑

沿用 A10、种子 5050101–5050124，四角色各 6 局。两个独立进程各 12 局、每角色各 3 局，各自复制相同冻结 profile。单局差异删失留档，继续下一局，不自动重选种子或重试。

每个游戏进程线限 **1 核、1 GiB、swap 0**；Java 堆最大 512 MiB、SerialGC、无图形环境。第一阶段为单路，结束后才启动第二阶段双路。逐局记录 CPU、RSS／PSS、cgroup 内存／swap 峰值、线程及 FD。

## 任务与结果

- supervisor：`headless-batch24-v2-supervisor.service`
- 第一阶段：`headless-batch24-v2-pilot.service`
- 第二阶段：`headless-batch24-v2-lane0.service`、`headless-batch24-v2-lane1.service`
- 统一报告：根目录 `reports/staged-report.md`、`reports/staged-report.json`
- 第二阶段明细：`reports/batch24-report.md`、`reports/batch24-report.json`
- 第一阶段逐局状态／诊断：`pilot/reports/confirmation-status.json`、`pilot/reports/failure-*.json`

两阶段结束后，服务器自动独立回放有效轨迹，关闭并归档 scoped 许可，核对原服务、只读接口、冻结输入和固定上游。原 `:5000/state`、SoulNPC、3681174958 服务及旧全局采集闸门保持不变，训练保持未接入。此前 v1 批次及修复目录原样保留。

## 提交前验证

- Linux framework **508 项**、native **264 项**全部通过。
- 5 个原种子证据与所有前缀的 14 条 RNG 格式／目标触发上下文核对通过。
- shell 语法、冻结输入、profile、只读服务与 systemd 独立执行权限预检通过。
- 4 个离线 supervisor 场景通过：双阶段正常放行、第一阶段失败、独立门槛失败、信号停止；均验证条件启动、关闸和报告收尾。

实现：[实局确认与独立门槛](D:/code/aiplayspire/sts_ai_framework/headless/live_confirmation.py)、[统一报告](D:/code/aiplayspire/sts_ai_framework/headless/staged_report.py)、[一次提交入口](D:/code/aiplayspire/deploy/headless/run-staged-batch24.sh)。
