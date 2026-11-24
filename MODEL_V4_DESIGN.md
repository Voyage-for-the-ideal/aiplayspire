# V4 多尺度价值网络（format 12）

本契约替代历史的全细桶 `bucket-hazard-v1`。本次包含代码、合成回归、审计和运行脚本；完整数据重建和 GPU 训练由操作者另行执行。旧 pilot 与 confirm15 保存了全零先验，不能作为新实验的有效对照。

## 目标与输入

`V(S)=(E[T|S]+3*P(Heart|S))/60`，其中 T 是真实终止楼层；Heart 胜利规范到 57。模型估计后续发育和行为策略产生的期望，不假定当前牌组冻结。历史玩家策略与 AI 策略差异仍然是建模限制。

原始行保存 terminal_known、terminal_floor、heart_victory。删失轨迹只监督已证实到达的端点，停止位置和 Heart 结果保持未知。run 级 split、post-action 快照、角色和可见 Boss 规则不变。终局差分回放算法不在本轮修改，需后续用原始 run 抽查历史快照。

全局 11 维顺序冻结为 act_1、act_2、act_3、act_4、act_progress、floor_normalized、hp_ratio、hp_absolute、max_hp_absolute、gold_log、ascension。四段按 `[0,17)`、`[17,34)`、`[34,51)`、`[51,57]` 划分，是与价值端点一致的进度段；训练/推理共同解析，不依赖未来结果。段内进度为 `(floor-start)/(end-start)`，绝对楼层为 floor/57。通过共享字段名查找全局特征，尤其是进阶，避免硬编码列号。资源归一化继续仅从完整 training split 的 99.5% 分位数拟合。

钥匙使用 value 与 known mask。绿钥匙确认获得后跨幕保持 value=1、known=1，缺失后续地图不得清零；未确认的持有状态保持 unknown。Sapphire 在历史记录进入 Act IV 前仍为 unknown。

固定序列为 `[CLS]+[GLOBAL]+[CHARACTER]+[BOSS]+[KEYS]+[MAP×105]+[HISTORY×8]+item tokens`。角色编码为 IRONCLAD=0、THE_SILENT=1、DEFECT=2、WATCHER=3；缺失和未知角色报错。BOSS 使用可见信息。

105 格地图使用浮点坐标 `[x/6,y/14]`、真实唯一边、三个方向的出边 mask、current_candidate 和 reachable；HISTORY 只保留当前幕最近八个已离开的房间。归档当前位置仅从已观测路径前缀推断。地图生成 RNG 和 bookkeeping 保持不变，输入不含 router 计划。默认 192 维、5 层、4 heads、ReLU、Pre-LN、early Global、CLS，MHA `need_weights=False`；可选骨干消融默认关闭。item 聚合/顺序及 73-item cap 保持。

## 标签、结构化输出与价值

| 当前段 | 有效细桶端点 | 有效未来粗桶端点 |
|---|---|---|
| 1 | 3/6/9/12/15/17 | 34/51/57 |
| 2 | 20/23/26/29/32/34 | 51/57 |
| 3 | 37/40/43/46/49/51 | 57 |
| 4 | 54/57 | 无 |

移除所有 endpoint<=floor 的桶，按剩余端点排序。每个桶的起点是当前 floor 或前一个有效端点。粗桶从原始终止结果重新构造，不能简单复用同名细桶的标签。到达端点标 survival；终止时仅第一个未到达桶标 stop；后续桶屏蔽。

模型输出 ValueOutputs：hazards[23]、means[23]、heart[1]。hazard 前 20 项为原细端点，后三项为 coarse 34/51/57；mean 与 hazard 一一对应。固定 47 个标量通过命名结构访问。full_fine 控制组启用全部未来细桶，粗桶完全屏蔽；它也训练条件停止位置。

条件停止均值为 `mu=start+max(0,end-1-start)*sigmoid(mean_logit)`，只对已确认停在本桶的样本监督桶内归一化 MSE；宽度零时不需要位置梯度。概率质量 `q_k=h_k*prod(previous(1-h))`，于是：

`E[T]=sum(q_k*mu_k)+57*prod(all_active(1-h))`

`P(Heart)=prod(all_active(1-h))*sigmoid(heart_logit)`。

分布合成在 multiscale.py 统一实现，训练/推理/机制探针共用；生存乘积在 log 空间计算。无效输出不得参与价值、损失或梯度。E[T] 必须在 [floor,57]，停止质量与最终生存和为 1。

默认损失为 `near_BCE + .25*far_BCE + .25*Heart_relative_NLL + .1*position_MSE + .2*key_BCE + .1*monotonic`。near/far 各按有效风险单元与固定进阶权重求均值；Heart 除以训练先验熵，下限 .05。空分组损失为零，覆盖数显式输出。原 macro_relative_nll 作为控制组保留，所有权重冻结在 checkpoint。

## 统计、评估与实验

Cache 4 保存全细桶、多尺度两种风险统计及共同评估统计；词表、caps、进阶权重和统计均来自同一完整 training split。检查 positive/valid/rate 自洽，并与 manifest 中 train_positive_samples_by_target、train_valid_samples_by_target 以及 training_outcome_counts 的两种分辨率和共同目标逐项对账。无观测项以 valid=0 标识，评估输出空指标；不能将其当成已验证的 0 分。

共同标签是 reach-17/34/51/57 与 Heart；已经到达的端点不计入未来评分。未完成但已观察到达的端点仍可监督。共同 NLL 固定为 `(四个有效里程碑的平均相对NLL+Heart相对NLL)/2`，Heart benchmark 权重始终 1，不受候选训练权重影响。输出 common_nll，relative_value_nll 是兼容报告别名。真实终止楼层 MAE 只用 terminal_known 行；统计基线的终止均值仅拟合训练结果。

报告角色、进阶、当前楼层、距离 1–3/4–8/9–16/17+、细/粗桶覆盖与 37/57 的 near/far 覆盖。不同分辨率的条件 hazard 不直接横比。检查 Heart PR-AUC、Brier/ECE 和机制排序，不能将预测指标直接解释成通关增益。

逐样本导出包含 run_id、sample_id、共同标签指纹、5 路加权 NLL 分子/分母、概率、真实楼层绝对误差、终止观测数和机制探针统计。按整局成组 bootstrap，种子和全部快照保持配对。共同 NLL 的差值 95% 区间上界<0，且 MAE/机制违反率无显著恶化时报告可晋级；证据不足保留参考组。单 seed 区间明确只表示 run 层不确定性。

Pilot 默认固定 1% run-hash、8 轮；确认默认完整 train/val、10 轮、seed 42。batch 512、AdamW lr 2.8e-4、weight decay .01、clip 1、前三轮屏蔽 map/history、余弦覆盖实际预算，不提前停止。可另跑 43/44 检验稳定性，不强制三个 seed 或 20 轮。比较要求相同声明预算均已完成、种子集合及共同评估契约一致。

五组由 train_multiscale10.sbatch 在普通作业内顺序运行：full_fine_legacy、full_fine_new、multiscale、multiscale+depth8、multiscale+width256。脚本按服务器模板显式加载 conda.sh 并激活 spire，先重建缺失的新数据并审计 train/val，再训练五组；已有完整新数据审计后复用。每组自动输出 val 评估导出；不读 test、不部署。每轮用共同 NLL 在完整上下文且通过真实楼层 MAE guard 的候选中选最优。final 文件保存选中权重和完整训练历史，不是强制最后一轮权重。

## 版本与运行

- checkpoint 12；preprocessing set-transformer-v11-multiscale；global-features-v7-floor；map-context-v6-persistent-keys；value target multiscale-hazard-v1；cache 4。
- Format 11 及以前和旧数据明确拒绝加载；不得在原权重上重新解释目标或特征。
- 模型冻结 resolution、架构、47 输出契约、全部权重、73-item cap、词表、caps、先验及可观测性。
- evaluate_state 仍返回 [0,1] 标量；hazards/survival 仅返回有效端点。evaluate_state_distribution 增加 active_bins 的 kind/start/end/hazard/stop_mean/stop_probability/survival。不要为远期细桶补造概率。
- 只读评估默认使用 `val`。布尔参数 `--test-only` 是 `--evaluate` 的别名；评估 test 集仍须明确传入 `--evaluation-split test`。评估写入新报告目录，源 checkpoint 原样保留。
- HTTP 推理从 `selectcard/` 使用 `uvicorn src.api:app`，端点为 `POST /recommend/choice` 与 `POST /recommend/shop`。

以下从 selectcard 执行，完整操作由用户在数据/GPU 机器独立运行：

```bash
conda activate spire
python src/rebuild_multiscale.py --input-dir "STS Data"
python src/inspect_multiscale.py --split train --output experiment_results/multiscale-train-audit.json
python src/inspect_multiscale.py --split val --output experiment_results/multiscale-val-audit.json
sbatch train_multiscale10.sbatch
python src/evaluation_v4.py --baseline <reference-val_predictions.jsonl> --candidate <candidate-val_predictions.jsonl> --output experiment_results/multiscale-comparison.json
```

重建先保留 `processed_data_v4/a10plus_multiscale_v1_base/`，通过可见 Boss 解析与审计后生成 `processed_data_v4/a10plus_multiscale_v1/`；所有输出必须为新路径。已有数据、模型与报告不覆盖。训练文件在 checkpoints/v4/a10plus_multiscale10，评估在 experiment_results/a10plus_multiscale10。

```bash
cd src
python -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api
```

原始归档缺失时部分 test_map_context 测试跳过，必须继续执行无需归档的 test_multiscale 合成回归及构建→短训练→保存→只读评估→动作评分闭环。完整地图回验、GPU 吞吐和实战收益只能在相应环境验证。
