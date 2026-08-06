# 自动战斗搜索性能优化报告

## 环境与方法

- 日期：2026-08-06
- 系统：Windows 11 x64，Intel Family 6 Model 140，8 个逻辑处理器
- 构建：Maven 3.9.15，OpenJDK 8u482
- 游戏运行时：Java 8u144，服务端 `DEEP` profile，production plaid blocking loop
- 输入：仓库外的 early、long-history、GRID 三个完整状态；未提交状态、日志或生成产物
- 微基准：每个状态 200 次 warmup、1,000 次迭代、10 个测量样本
- 真实搜索：每个状态 5 次，每次固定 1,000 expansions

主要命令：

```powershell
cd STSStateSaver
mvn test
mvn package

cd ..\scumthespire
mvn test
mvn package
mvn '-Dtest=battleaimod.search.SearchStateKeyBenchmarkTest' '-DstateFile=<temp-state>' test
```

真实搜索通过仓库外临时 PowerShell runner 启动 ModTheSpire；基线、优化 SHA-256 和 Murmur3 128 使用相同请求参数与输入。

## 第一阶段：流式 SHA-256

真实搜索中位数：

| 状态 | 基线 nodes/s | 优化 SHA nodes/s | 基线 state key ms | 优化 SHA state key ms | 优化后占总耗时 |
| --- | ---: | ---: | ---: | ---: | ---: |
| early | 641.44 | 638.16 | 498 | 242 | 15.42% |
| long-history | 644.33 | 644.33 | 1 | 0 | 0.00% |
| GRID | 647.67 | 648.09 | 579 | 321 | 20.84% |

- 三个状态合并后的 nodes/s 中位数为基线的 100.13%，高于 95% 保留门槛。
- early 和 GRID 的状态键累计耗时分别下降 51.4% 和 44.6%。
- 微基准中，优化 SHA 完整键在三个状态的 10/10 样本均快于“旧 canonical 路径 + SHA 摘要”。
- long-history 搜索只生成 1 个回合状态，因此其真实 state-key 占比不用于判断热路径收益。

## 128 位门槛与结果

同输入微基准中位数（ns/op）：

| 状态 | 旧 canonical-only | 流式 SHA 完整键 | SHA 摘要 | SHA 摘要占完整键 | Murmur3 完整键 | Murmur3 摘要 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| early | 774,451 | 378,657 | 141,808 | 37.4% | 300,853 | 18,661 |
| long-history | 1,458,597 | 913,384 | 348,131 | 38.1% | 636,965 | 46,072 |
| GRID | 1,126,045 | 661,378 | 223,722 | 33.8% | 427,942 | 23,161 |

优化 SHA 的真实 `state_key_ms / elapsed_ms` 合并中位数为 15.42%，且三个微基准的 SHA 摘要占比均超过 20%。双重门槛成立，因此统一切换到已有 Guava `murmur3_128`。

切换后的真实搜索中位数：

| 状态 | SHA nodes/s | Murmur3 nodes/s | SHA state key ms | Murmur3 state key ms | Murmur3 key 占比 |
| --- | ---: | ---: | ---: | ---: | ---: |
| early | 638.16 | 637.76 | 242 | 175 | 11.16% |
| long-history | 644.33 | 649.35 | 0 | 0 | 0.00% |
| GRID | 648.09 | 643.09 | 321 | 257 | 16.53% |

Murmur3 将 hot-state 的状态键累计耗时再降低 19.9%–27.7%，nodes/s 变化在 -0.8% 到 +0.8% 内。响应现在报告 `state_key_algorithm: "murmur3_128"`，`final_state_key` 为 32 字符小写 hex。

## 一致性与后续

- 三种状态的 generated/unique/duplicate turn-state 数量在三版实现中完全一致。
- 三种状态各 5 次的命令序列在基线、优化 SHA 和 Murmur3 之间全部一致（15/15）。
- Murmur3 实测的单次 load 中位数为 46–63 us，单次 snapshot 中位数为 17–25 us；本轮不实施增量回滚或游戏对象复用。
- 游戏目录中的测试 JAR 已按 SHA-256 校验和恢复为基线版本。
