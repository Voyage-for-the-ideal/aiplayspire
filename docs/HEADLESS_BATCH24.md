# Headless 24 局独立双进程验收

**实际结果：24 局均已尝试，8 局真实终局、16 局因差异删失；24 条轨迹／有效前缀独立回放通过。** 后续修复、逐项验证与隔离部署见[差异修复记录](D:/code/aiplayspire/docs/HEADLESS_BATCH24_REPAIRS.md)。原批次报告与冻结运行输入保留。

本批次按用户更新后的要求，取代此前“40 局遇差异停整批”的执行计划。此前 7 条终局及所有中止诊断原样保留；本批次**重新运行 24 个 case**，不把旧终局算入本批次。

## 提交记录

已于 2026-11-17 单次提交。启动确认时 supervisor 与两个 lane 均为 active：

- `headless-batch24-supervisor.service`：PID 790780，invocation `1f70915e9de542dfad48cdd38ea208f9`。
- `headless-batch24-lane0.service`：PID 790786。
- `headless-batch24-lane1.service`：PID 790787。

旧 40 局批次的 scoped 许可已归档为 `superseded-by-batch24`，不会与新批次并发启动。提交后的聊天侧不持续检查进度；以上仅为启动证据，不代表最终完成状态。

## 固定配置

- 根目录：`/home/ubuntu/headless-spire-poc/runtime-acceptance-batch24-v1`。
- A10，四角色各 6 个种子，范围 5050101–5050124；角色顺序 IRONCLAD、THE_SILENT、DEFECT、WATCHER。
- `lane0` 运行第 1、3、5 轮，`lane1` 运行第 2、4、6 轮；每条进程线 12 局、每角色各 3 局。各自独立 profile 副本、会话、轨迹、状态文件和进程锁。
- 每条进程线限 1 核、MemoryHigh 768 MiB、MemoryMax 1 GiB、swap 0；Java 最大堆 512 MiB，SerialGC，单核、无图形环境。各进程线常驻一份 CPU 模型，每局新建并关闭 Java／native worker。
- 固定 format-12 checkpoint 与冻结 profile；`frozen-runtime-manifest.json` 记录两条线全部部署输入的 SHA-256。运行期间不修补代码、不训练。

## 差异与中断

每个 case 仅尝试一次。合法动作、真实后帧、状态／RNG 与生效校验沿用现有严格契约；单局发现差异即中止，保存诊断和有效前缀，随后运行下一 case。不改写为游戏失败，也不为凑终局数重选种子。

SIGTERM、许可撤销等运维停止与普通差异分开处理：停止后续 case。已持久化预约的 case 在恢复时按删失保留，不自动重试；重复入口由进程锁及一次提交标记阻止。

## 独立执行与报告

一次提交入口为 [run-batch24.sh](D:/code/aiplayspire/deploy/headless/run-batch24.sh)。systemd supervisor 启动两个独立服务，通过操作系统等待退出，不由聊天模型持续轮询。两条线结束后自动关闭本批次四道 scoped 许可，核对原服务、只读接口、固定源与运行输入，再独立回放全部完整轨迹及中止有效前缀。

报告路径：

- `/home/ubuntu/headless-spire-poc/runtime-acceptance-batch24-v1/reports/batch24-report.md`
- 同目录 `batch24-report.json`：逐局结果、诊断 SHA、独立回放结果、资源采样。
- 同目录 `preservation-report.json`、`supervisor-result.json`：原服务不变与服务退出／关闸结果。
- 每条线的 `lane0/reports/agent.log`、`lane1/reports/agent.log`：逐局执行日志。

`finished_with_differences` 表示 24 局均已尝试，其中部分因差异删失；只有全部真实终局且回放／保全审计通过才是 `passed`。缺局、回放错误、OOM、进程线使用 swap 或保全审计失败不会标作通过。RSS/PSS 与 cgroup 峰值分开记录，修复及部署时间不计入连续稳定运行。

原 `:5000/state`、SoulNPC、3681174958 的服务及两道旧全局采集闸门保持不变。数据标记 `headless-native-v1`、`legacy_training_admission=false`，不接入训练。

## 提交前验证

- Linux framework 完整回归 **469 项通过**，其中新增 runner／report 目标 **14 项通过**。
- Linux native 完整回归 **240 项通过**；小尖刺史莱姆真实行动历史修复的故障步骤重新强制预测及指纹校验为 APPLIED。
- shell 语法、冻结输入一致性、39 文件 profile、原只读服务／HTTP、systemd 独立执行权限在提交前检查。

本文记录配置与提交前证据；24 局实际结果以服务器结束后生成的报告为准。
