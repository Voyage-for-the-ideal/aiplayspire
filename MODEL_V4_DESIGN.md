# V4 价值网络：输入修复与可复现实验

> 更新：2026-10-13。代码、合成回归及实验工具已实现；完整数据重建、GPU 训练及候选部署尚未执行。

## 目标与边界

先修复信息丢失与训练／实战输入不一致，再检验容量和结构假设。
价值契约保持 `bucket-hazard-v1`；标量仍为
`V(S) = E[终止楼层] + HEART_WIN_BONUS_FLOORS × P(Heart)`。
保留 post-action 状态、run 级 split、角色／Boss／钥匙／历史上下文。
item 聚合、词表排序及地图模型的 **73 个 item 上限全部保持原样**。
不改 Java mod、地图生成 RNG、router 或决策引擎，不增加依赖。

## 已确认问题与修复

| 问题 | V4 行为 |
|---|---|
| 坐标跟随整数 flags 转换，105 个格子仅剩 4 种坐标 | 坐标始终为浮点数，顺序统一 `[x/6,y/14]`，Python／模型解码一致 |
| 训练 visited 标历史候选，实战只标当前位置 | bit 2 改为 `current_candidate`；训练标当前行候选集合，实战标准确当前节点；全零表示未知 |
| `parents` 重复／过时记录使入度偏大 | 两侧共用最终有效边去重、反向入度、出度和可达性计算，不修改地图生成器 |
| 边没有显式传给模型 | 新增 `map_outgoing_mask[105]`：左／直／右为 bit 0／1／2，连接下一行 |
| 推理的 item 上限依赖运行时 Config | 从 checkpoint 保存的 `item_token_budget` 加载 |
| MHA 计算并丢弃注意力权重 | 保留 MHA，使用 `need_weights=False`；前向及梯度等价性有回归覆盖 |

普通边只接受下一行相邻三列，重复边去重、缺失目标忽略；非法在图内跳边报错。
有效最后一行节点统一连虚拟 Boss，普通出边位域为 0、出度为 1。
历史 token 仍只来自当前幕已经离开的房间。候选位置来自已观测路径前缀，绝不使用未来后缀消歧。
`reachable` 本来就有显式特征；V4 增加的是节点之间的连接关系，不能再表述为“可达性只能靠坐标悟”。

## 契约与迁移

- preprocessing：`set-transformer-v10-current-edges`。
- map-context：`map-context-v5-current-edges`。
- checkpoint format：**11**，保存完整架构、item 上限、词表、归一化、训练先验、损失权重与实验设置。
- 旧数据／format 10 及更早模型明确拒绝加载；需要在新目录重建、重训。不能把修复后的坐标解释强行套在旧权重上。
- 宽度、层数、FFN、归一化、结构编码、latent 数量、坐标表示、池化和 register 都由 checkpoint 决定，不为每个实验重复提升格式号。
- 新数据默认目录 `selectcard/processed_data_v4/`；旧数据、checkpoint 原样保留。生成物不提交。

## 实验配置

修复基线：192 维、5 层、4 heads、ReLU、LayerNorm、pre-LN、CLS；key／monotonic 权重 0.2／0.1。
所有结构实验默认关闭。实验注册表在 `src/experiments.py`，可用 CLI 列出。

| 配置 | 相对基线的改动 |
|---|---|
| `baseline` | 修复后的基线 |
| `depth8` / `width256` | 单独加深／加宽 |
| `gelu` / `swiglu` | 只改变 Transformer FFN；SwiGLU 隐层约 8d/3，取最近 8 的倍数 |
| `rmsnorm` | attention block 和最终 norm；pre-LN 位置不变 |
| `edges` | 三个出边 bit 的线性投影 |
| `dag` / `node_mlp` | 逆拓扑后继聚合／同参数量无边控制 |
| `latent16` / `latent32` | 可学习查询的交叉注意力汇总；保留向量维度和 73 个 item |
| `dag_latent16` / `dag_latent32` | 仅在独立结果支持后测试组合 |
| `key0` / `key005` / `key02` | 钥匙损失权重 0／0.05／0.2 |
| `monotonic0` / `monotonic003` / `monotonic01` | 单调损失权重 0／0.03／0.1 |
| `plr` | 仅 xy：每坐标 8 个可学习频率、保留原始坐标；二值特征不周期化 |
| `cls_mean` | 拼接 CLS 和有效 item token 的 masked mean |
| `register2` / `register4` | 增加 2／4 个内部计算 token，不减少 item 数量 |

DAG 从第 14 行逆序计算到第 0 行。每节点汇总最终有效后继的均值，
采用 `norm(node + successor_mean + MLP(concat(node, successor_mean)))`；MLP 在所有行共享。
无边控制使用相同参数，但后继均值与边信号均置零。直接后继残差为远端信息提供传播路径。
latent 空地图使用安全零 key，随后把全部输出屏蔽，避免全屏蔽注意力 NaN。
辅助 key 头重构当前已输入的钥匙状态，不是预测未来能否拿到钥匙。

## 训练与晋级协议

初筛：train／val 各按 `SHA256("aiplayspire-v4-pilot:" + run_id)` 取前 1% 哈希范围，
同一局全部状态共同入选；seed 42、8 epochs、batch 512、不提前停止。
确认：完整训练／验证数据，20 epochs，seeds 42／43／44，基线与候选共同重跑。
两阶段都保留前三轮上下文 curriculum，AdamW lr=2.8e-4、weight decay=0.01、梯度裁剪 1，余弦覆盖实际轮数。
词表、归一化和先验从同一完整 training split 拟合并缓存，各候选共用；不使用 val／test 拟合。
显存不足记录 `cuda_oom`，不静默改变 batch。默认训练入口执行初筛而非全量训练。

训练只查看 train／val，不自动读取或评估 test。最终模型确认后显式选择 `--evaluation-split test`。
评估输出各角色／进阶等级的 MAE、Heart PR-AUC、Brier／ECE、固定定义的 relative NLL。
额外在固定 1% run-hash 探针集合上比较移除已观察 Egg／已知已收集钥匙的排序；unknown 钥匙不伪造标签。
保存逐样本加权 NLL 分子／分母、run_id 和探针统计。比较时按整局成组 bootstrap，所有快照及随机种子保持成对。
仅在三个确认种子、完整 val、完成 20 epochs、具备探针数据时允许晋级；
NLL 差值 95% 区间上界必须小于 0，探针违反率不得显著恶化（差值区间下界不得大于 0）。
否则保留基线。有效 bootstrap 不足 95% 也视为证据不足。比较工具流式读取配对导出，内存按局数而非全部快照增长。
吞吐、参数量、显存和推理成本同时报告；不把合成测试、CPU 耗时当作 GPU 或通关收益证据。

## 运行命令

以下均在 `selectcard/`，先激活 conda `spire`。数据重建与训练由操作者另行执行，本轮未运行。
输出目录必须独立且未存在，避免覆盖已有模型。

```bash
# 在新目录重建，不覆盖旧数据
python src/data_pipeline.py --input-dir "STS Data" --output-dir processed_data_v4

python src/train.py --list-experiments
python src/train.py --experiment baseline --stage pilot --data-dir processed_data_v4
python src/train.py --experiment gelu --stage pilot --data-dir processed_data_v4

# 确认阶段对基线和候选分别执行三个 seed
python src/train.py --experiment baseline --stage confirm --seed 42 --data-dir processed_data_v4
python src/train.py --experiment baseline --stage confirm --seed 43 --data-dir processed_data_v4
python src/train.py --experiment baseline --stage confirm --seed 44 --data-dir processed_data_v4

# 只读评估：使用最终 sts_value_model_final.pth，以保留完整训练轮数证据
python src/train.py --evaluate --checkpoint checkpoints/v4/baseline/confirm/42/sts_value_model_final.pth --data-dir processed_data_v4 --output-dir experiment_results/baseline-42

# --test-only 是 --evaluate 的别名，不接受位置参数；默认评估 val
# 最终选型完成后才显式 --evaluation-split test

# 初筛报告：每个独立类别只推荐一个改善候选，否则保留基线
python src/evaluation_v4.py --pilot-metrics experiment_results/baseline-pilot/val_metrics.json experiment_results/gelu-pilot/val_metrics.json --output experiment_results/pilot-selection.json

# baseline/candidate 各提供三个 seed 的 val_predictions.jsonl
python src/evaluation_v4.py --baseline baseline42.jsonl baseline43.jsonl baseline44.jsonl --candidate candidate42.jsonl candidate43.jsonl candidate44.jsonl --output experiment_results/comparison.json

# 测目标 GPU；本地 CPU 仅供独立报告，不能推断 GPU 加速
python src/benchmark_v4.py --experiment baseline --device cuda --compare-attention --output experiment_results/benchmark-cuda.json
python src/benchmark_v4.py --experiment baseline --device cpu --output experiment_results/benchmark-cpu.json

# 已独立晋级的配置可显式组合；冲突参数会报错
python src/train.py --experiment gelu,rmsnorm --stage pilot --data-dir processed_data_v4
```

训练输出 `experiment.json`、`status.json`、best/final checkpoint、`training_metrics.json` 和训练图。
只读评估输出独立 `val_metrics.json`／`val_predictions.jsonl`；不修改源 checkpoint。
benchmark 固定 batch 1／8／32，预热 10 次、测量 100 次，记录 median／p95、吞吐及 GPU 峰值显存。

```bash
cd src
python -m unittest test_map_context.py test_value_network_v2.py test_value_network_v3.py test_value_network_v4.py test_data_pipeline.py test_api.py
# 仓库根
python -m unittest discover -s sts_ai_framework/tests -t .
```

新回归使用合成图，不依赖归档 run。另有 8 条样本的临时 CPU 训练／只读评估冒烟，验证完整链路，不是研究训练。
旧归档测试在原始数据缺失时仍会跳过，需单独报告。

## 修正原方案的证据与文献结论

- 31M / 2.44M 约 **12.7**，不是 13,000。相邻状态相关，不能由该比例直接判断容量。
- `v3_train_62013.log` 是 map-context-v2、2,439,576 参数；引用的 0.3518→0.3524 是 epoch 8 内累计训练 loss。
- 2,440,344 参数来自 `train_v3_75460.log`。其 val relative NLL 在 epoch 8 为 3.9437，epoch 15 降至 3.8002。
  日志记录 A100 80GB；不能用它推断 Blackwell 32GB 的 kernel 或显存瓶颈。
- 扩容、深度优先、GELU/SwiGLU/RMSNorm、PLR、register 和 latent 的质量收益都是待检验假设，不是“免费收益”。
- MHA 可复用 SDPA；先 `need_weights=False`，是否使用融合 GPU kernel 仍需目标环境实测。
- bucket-hazard 更贴近有删失的离散时间生存模型，不等同于 C51 的类别分布／Bellman 训练。
- 一个线性输入投影不代表整个注意力＋非线性网络无法表达邻接；坐标嵌入也不能替代真实边关系。

参考：[PyTorch MHA](https://docs.pytorch.org/docs/2.7/generated/torch.nn.MultiheadAttention.html)、
[GLU variants](https://arxiv.org/abs/2002.05202)、[RMSNorm](https://arxiv.org/abs/1910.07467)、
[数值嵌入](https://arxiv.org/abs/2203.05556)、[Perceiver](https://arxiv.org/abs/2103.03206)、
[Graphormer](https://arxiv.org/abs/2106.05234)、[离散时间生存模型](https://arxiv.org/abs/1805.00917)。
这些论文支持实验动机，不证明本项目收益。PMA、多任务自动加权、消融分支退役暂不实现。
