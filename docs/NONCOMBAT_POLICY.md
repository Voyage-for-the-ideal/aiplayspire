# 非战斗 Set Transformer 策略

## A15+ 历史策略与旧版对照

本轮只训练四角色 A15–A20 普通胜局中的 `CARD_REWARD` 和 `BOSS_REWARD`。和 `pilot-121289` 一样是选择前 BC，不训练事件、不使用 format-12 权重初始化，也不启用 AWR。只可用 `sbatch` 的服务器直接提交：

```bash
cd /home/scc/pb23151808/Desktop/selectcard
# 固定整局哈希 1%，8 轮：先核对导出、覆盖与训练链路。
sbatch train_policy_a15plus.sbatch pilot
# 另行显式提交：全量合格 train/val，从头 10 轮。
sbatch train_policy_a15plus.sbatch confirm
```

这个文件在同一个作业里先导出再训练，固定 A15+，不嵌套提交。结果写入 `experiment_results/run_policy/<stage>-a15plus-<jobid>/`。默认在项目内找 `STS_DATA` 或 `STS Data`；路径不同可用 `--export=ALL,STS_RAW_DATA_DIR=/绝对路径`（放在文件名之前），Conda 根目录可用 `RUN_POLICY_CONDA_ROOT`。正式作业需要更长时间时，用服务器允许的 `sbatch --time` 覆盖默认 24 小时。需要分开申请 CPU 和 GPU 时，可用旧提交助手 `bash submit_run_policy.sh --stage <pilot或confirm> --min-ascension 15`。

也可在仓库根直接给 `python -m selectcard.policy.from_runs` 增加 `--min-ascension 15`，为新数据目录生成分片，再用原 `train --data-dir` 入口训练。进阶筛选发生在抽样／重放前，写入数据指纹及 checkpoint 的训练配置；换阈值使用新目录。原混合数据训练入口的筛选逻辑保持原样。A15+ 导出在 `coverage.json` 的 `ascension_coverage` 报告 train／val × A15–A19／A20 × 四角色 × 两项任务的决策数、局数和种子组数。Confirm 任一格为空时保留报告并停止，训练不会启动；不下调进阶或重抽。架构与超参沿用首版，以 A15+ 验证 NLL 选 `best.pt`。

要在同一个 Slurm 作业内追加旧模型对照，可通过 `--export=ALL,POLICY_BASELINE_CHECKPOINT=/旧模型/best.pt` 指定旧 checkpoint；也可同时提供 `POLICY_BASELINE_DATA_DIR` 核验旧训练种子。对照写入作业结果目录的 `comparison.json`。单独对照则从仓库根运行：

```powershell
conda run --no-capture-output -n spire python -m selectcard.policy.compare_bc --data-dir <A15+候选数据目录> --baseline <旧pilot/best.pt> --candidate <A15+模型/best.pt> --output <新对照报告.json>
```

可加 `--baseline-data-dir <旧模型原始数据归档目录>`，只需其中与旧 checkpoint 指纹匹配的 `manifest.json` 及校验通过的 `index.sqlite`。比对旧 train 与新 val 的种子组后，报告 `verified_disjoint`、`overlap` 或 `unverified`；后两者仅作描述性对照。两模型用各自冻结的词表对同一批合法候选评分，报告按任务和进阶段分组的 NLL、Top‑1、预测／标签跳过率及合法候选全部 token 的词表覆盖率。主指标 `a20_paired` 为候选减基线的平均 NLL 差，负数更好；区间按种子组整组重采样，少于两组时省略区间。比较只读 val，不削弱普通 `train --evaluate` 的数据指纹校验。

旧 pilot 的样本量和训练轮数也不同，离线差异不单独归因于进阶筛选。模型先做影子评分；如要替换现有实战策略，只显式启用上述两类决策并按下文整局配对标准验证 A20 碎心率。

## 混合历史与实机数据的下一版

新增 `mixed-observable-v1` 输入规范，历史样本与已有确认轨迹共同训练直接选择策略；历史未记录的药水、钥匙、商店和事件状态标为 unknown，实机已提供的数据保留。混合时将历史十进制种子映射为 CommunicationMod 的 UUID 分组后重新划分，避免同一种子跨来源进入不同 split。旧数据和 checkpoint 不修改。历史 `.run` 新增可选 `--include-map`，仅导出当前位置及下一节点均可唯一确定、无飞靴且候选完整的地图分叉；未证明的营火和事件菜单不生成标签。商店只从已有实机轨迹读取，不推测库存。

以下操作由用户显式执行；所有输出目录必须是新目录。`<已有轨迹目录>` 即使暂时没有轨迹也应存在，完整训练无需启动游戏：

```powershell
# 分别执行 pilot 或 confirm；历史数据先导出到新的目录。
conda run --no-capture-output -n spire python -m selectcard.policy.from_runs --input-dir <历史归档目录> --output-dir <新历史目录> --stage pilot --include-map
conda run --no-capture-output -n spire python -m selectcard.policy.mixed_data --historical-dir <新历史目录> --trace-dir <已有轨迹目录> --output-dir <新混合目录> --stage pilot
conda run --no-capture-output -n spire python -m selectcard.policy.train --data-dir <新混合目录> --output-dir <新模型目录> --stage pilot --device cuda

# 训练后显式启用；checkpoint 未训练的类型自动使用现有策略。
conda run --no-capture-output -n spire python -m sts_ai_framework --policy-checkpoint <新模型目录>/best.pt --policy-mode policy --policy-types CARD_REWARD BOSS_REWARD REST SHOP_SCREEN MAP EVENT CHEST COMBAT_REWARD GRID --policy-trace-dir <新轨迹目录> --character IRONCLAD --ascension 20
```

正式训练时将三个 `--stage pilot` 都换成 `--stage confirm`，并使用新的目录；数据覆盖报告位于混合目录的 `coverage.json`。验证结果、模型支持类型和数据指纹写入模型目录及 checkpoint。运行时遇到未训练类型、不完整候选或未见过的候选语义时沿用原有 Agent。轨迹记录 policy／baseline 及回退原因；baseline 包含规则、价值模型和 LLM，当前记录不细分三者。新增轨迹需要再次显式导出和训练，运行不会自动改模型。

该方案没有承诺所有非战斗界面都已被训练。营火和事件历史样本导出、多阶段回退覆盖及实机整局效果仍需进一步验证；已有实机轨迹若缺失，则这些类型继续使用原策略。

新策略位于 `selectcard/policy/`，格式为 `noncombat-policy-v1`。它直接对**决策前状态 + 实际候选**输出概率，与原 format-12 价值网络的数据、checkpoint、推理开关分离。现有行为默认不变。

## 服务器 `.run` 首版 BC（2026-10-25）

此入口只训练战斗／精英／Boss 的奖励选牌，以及 Boss 遗物选择（含跳过），不运行 AWR，不训练商店、营火或事件。使用四角色 A0–A20 的普通胜局，保留三幕与 Heart 标签。下面的旧实时轨迹／AWR 工具仍独立保留。

将更新后的整个 `selectcard/` 同步到服务器同名项目目录即可；随代码携带的 `policy/vanilla_catalog.json` 是 vanilla ID／卡牌类型快照，CPU 导出不需要服务器存在 `cardcrawl/` 或游戏 jar。使用现有 `spire` 环境，不新增依赖。服务器执行：

```bash
cd /home/scc/pb23151808/Desktop/selectcard

# 固定整局哈希的 1%，8 轮；抽样仅在导出时执行一次
bash submit_run_policy.sh --stage pilot --input-dir "STS Data"

# 正式训练需显式提交：全量合格 train/val，从头训练 10 轮
bash submit_run_policy.sh --stage confirm --input-dir "STS Data"
```

脚本提交 CPU 准备作业（16 核，默认 15 worker）和 `afterok` 依赖的单 GPU 作业。默认 conda 根目录为 `/home/scc/pb23151808/software/miniconda3`，可用 `--conda-root` 改写；可指定 `--workers 1..15`、`--output-root <不存在的新目录>`。GPU 不可用时失败，不回退长期 CPU 训练。模块搜索路径设置为 `selectcard/` 的父目录，兼容服务器只复制该项目的布局。

每次提交生成独立的 `experiment_results/run_policy/<stage>-<时间>-<进程号>/`：

| 产物 | 内容 |
|---|---|
| `logs/prep-*.log`, `logs/train-*.log` | Slurm 日志 |
| `data/manifest.json` | 原始文件 SHA256、代码、输入规范、单次抽样比例、分片／索引指纹 |
| `data/coverage.json`, `data/audit.jsonl` | 接纳／排除、停止原因、角色×进阶段×任务×split 覆盖、终局独立对账 |
| `data/shard-*.jsonl`, `data/index.sqlite` | 选择前样本与字节偏移索引 |
| `model/best.pt`, `model/policy.pt` | 每轮 val NLL 最佳模型、最终模型 |
| `model/last.pt`, `model/training.json` | 模型／优化器／调度器／RNG／轮次、恢复身份 |
| `model/metrics.json`, `model/best-validation.json` | 每轮／最佳验证指标，按任务、角色、进阶与进阶段分组 |
| `model/curves.csv`, `model/curves.svg` | 训练／验证 NLL 曲线与准确率 |
| `archive.json` | 训练完成后生成的 checkpoint／报告校验清单 |

指标包括准确率、NLL、预测／标签跳过率，以及随机合法选择、仅由 train 拟合的频率基线的准确率／NLL／跳过率。离线结果不代表 A20H 胜率提升；现有游戏策略保持默认。正式训练会检查四角色×两个任务在 train 和 val 都有样本，缺失时保留报告并停止，不自动缩减范围。Pilot train/val 为空也直接失败，不重抽。

### 严格重放与输入边界

`RunReconstructor(..., mode="policy")` 委托独立的因果前缀重放器；默认 `mode="value"` 及旧 `post_action` 接口保持。只从日志明确记录的初始牌组、遗物获取和卡牌变动传播状态，在加入所选牌／应用 Boss 遗物前输出。相同候选池的连续选择不去重；只去重排除传输时间等字段后内容相同的整局。终局牌组／遗物仅用于审计，不用于补推，也不因终局差异删除此前确定的前缀。

涅奥升级／移除目标缺失、潘多拉／星盘／磨刀石等未记录的随机牌组变化、持有 Lesson Learned 后的战斗、Cursed Key 后的宝箱及无法判明的事件重复日志等会停止后续导出。当天获得遗物的卡牌奖励因领取顺序不明被排除；商店按明确的购买顺序推进，保留确认的蛋类升级。Boss 选项本身可以在其效果不明时保留，该效果之后停止。未知实体／标签／候选退出训练。仅支持 vanilla、2020-01-14 起且构建标记为 2020／2021／2022 的日志；其他构建列为 unsupported，未声称跨 mod 的恢复能力。

输入规范冻结为 `run-context-v1`：牌组 ID／升级／数量、遗物 ID、角色／进阶／楼层／幕，以及 HP／max HP／金币 value＋known mask。金币领取时点不能确定，首版置 unknown；Boss 奖励、Bowl／Bloody Idol、同层药水等存在时点歧义的 HP 字段也置 unknown。首版不使用药水槽、遗物计数、卡牌 misc、瓶装绑定、钥匙或燃烧精英标记；现场推理应用同一投影。

地图仅生成当前幕。坐标 DP 只读取已发生的路径；不能唯一定位时专用 `context:position:unknown` token 配合空坐标，不编造可达性。幕地图／历史／可见 Boss 同样有专用 known/unknown token，既有 21 项数值特征顺序不变。Boss 奖励仍属于旧幕；虚拟 Boss、A20 双 Boss 和第四幕固定地图独立处理。首个 Boss 的身份来自当前幕首个 Boss 战斗标识，因为它从该幕开图起已经公开；不读取该战斗伤害／结果，不用种子猜测账户解锁。A20 第二 Boss 只在其已发生的战斗奖励中可见。

原始 `.run/.json/.json.gz`、顶层数组及 `event` 包装均可读取。数组逐局解析、worker 写临时磁盘流，最多预提交 `2×worker` 个文件。训练使用 SQLite 偏移索引和最多 4 个打开的分片句柄，逐批读取；内存保留词表、轻量分组索引和当前批次，不加载所有样本。种子仅重建／80:10:10 分组；词表和频率只扫描 train，缩放常数固定。test 不参与训练或验证。

### 独立重跑、恢复与验证

下面命令从项目父目录执行；这些入口均须显式运行：

```bash
cd /home/scc/pb23151808/Desktop
conda activate spire
python -m selectcard.policy.from_runs --stage pilot --input-dir "selectcard/STS Data" --output-dir <新数据目录> --workers 15
python -m selectcard.policy.train --stage pilot --data-dir <数据目录> --output-dir <新模型目录> --device cuda

# 只恢复同一次训练；保留原 stage、总 epochs、batch、seed、架构与数据
python -m selectcard.policy.train --stage pilot --data-dir <原数据目录> --output-dir <原模型目录> --device cuda --resume <原模型目录>/last.pt

# 显式验证；不运行 test
python -m selectcard.policy.train --stage pilot --data-dir <原数据目录> --output-dir <新验证目录> --device cuda --evaluate --checkpoint <模型目录>/best.pt --evaluation-split val
python -m unittest selectcard.policy.test_run_policy selectcard.policy.tests
```

恢复校验数据／代码／输入规范／配置指纹，恢复 optimizer、cosine scheduler 和 Torch RNG。Confirm 不接受 pilot checkpoint 初始化。既有目录只有源清单、代码、规范及分片／索引指纹一致时才复用；未完成或不同配置需新目录。`--epochs-this-run N` 可在轮次边界停下测试恢复，`--epochs` 仍保持原总预算。`--d-model/--layers/--heads` 仅用于显式结构实验／合成冒烟，生产默认保持 192／5／4。

本机验证包含合成归档→双 worker 导出→默认 192／5／4 模型一轮 CPU 冒烟→checkpoint 归档与重载；恢复与连续训练权重逐项相同。其模型只证明流程可运行，不能用于报告真实策略效果。真实服务器归档、Slurm 分配与 GPU 训练尚未运行。旧价值网络 183 项回归通过（14 项因原始归档缺失跳过），Framework 156 项通过。本机 38 局地图处理全部无越界；胜局严格前缀为 13 条（11 选牌、2 Boss），这不是全量覆盖率。

固定 batch 32 的读取／编码内存探针中，200→4,000 条的物理 JSONL 载荷从 1,546,800→30,936,000 字节增加，Python 堆峰值为 4,852,488→4,882,155 字节。此测量不包含 Torch 原生张量／GPU allocator，也不替代真实服务器峰值验收；原始报告保存在本机 `debug/run-policy-memory-20261025/report.json`。

## 已验证的重建边界

2026-10-24 的真实游戏对照结果及 2026-10-25 的历史恢复状态更正见 [重建报告](POLICY_RECONSTRUCTION_REPORT.md)。完整生成前快照能够复现所测试的商店；这不代表已经验证仅凭 `.run` 的恢复。当前历史商店函数没有搜索，直接返回 `ambiguous`，`searched_states` 为 0；历史恢复率尚未测量。缺少随机流计数不等于无法由种子和历史前缀推导，仍需实现求解器。未知样本不产生策略训练标签。

2026-10-25 新增独立 `.run` 前缀探针 `python -m selectcard.policy.shop_run_probe`，已进行实际局部求解、解锁状态歧义分析和购买记录独立校验；它不使用目标快照，不替代旧准入函数，也不输出训练标签。具体结果、限制和运行命令见 [专项调查](RUN_ONLY_SHOP_INVESTIGATION.md)。

现有 `selectcard/src/reconstructor.py` 的默认价值模式导出普通选牌／Boss 遗物 `post_action` 快照，部分状态依赖终局对账补推，不能直接当作新策略输入。新增 `mode="policy"` 使用上文的独立前缀导出。核对实现和本机实跑后的 [可重建范围表](RUN_RECOVERY_CAPABILITIES.md) 说明了可复用部分与待补采部分。

完整快照生成器目前只支持已验证游戏 jar SHA-256 `cfad868ac8d65a88e71a0bf096fb09f78811e553effe0787c5309a655e081673` 和 vanilla 生成机制。不同游戏版本、未验证 mod hook、缺少必要 RNG 状态会拒绝。初始生成与购买后变化分别验收。10,000 个候选状态／30 秒是历史恢复求解器的计划上限，尚未实现；当前没有用当次购买或后续记录反推随机计数。

从仓库根运行，使用现有 conda `spire`，无需新依赖：

```powershell
conda activate spire
# 显式跳过 pom 自带的复制部署，只构建研究 jar。
mvn -f StSCommunicationMod/pom.xml package '-Dmaven.antrun.skip=true'
conda run --no-capture-output -n spire python -m selectcard.policy.collect_shop_fixtures --game-dir 'D:/Program Files/Slay the Spire' --output-dir debug/policy-oracle-new --count 200
conda run --no-capture-output -n spire python -m selectcard.policy.audit --input-dir 'D:/Program Files/Slay the Spire/runs' --fixtures debug/policy-oracle-new/shops.jsonl --output debug/policy-oracle-new/report.json
```

采集器复制游戏 jar、JRE、BaseMod 和研究版 CommunicationMod 到独立运行目录，隔离配置与存档；不部署到原游戏。输出目录必须是新的。fixture 原始 JSONL 保留不改，审计使用同目录 manifest 绑定实际 jar 指纹。完整历史归档可换成 `--input-dir <归档路径>`，支持 `.run/.json/.json.gz`。

## 模型与数据约定

- 编码器：192 维、5 层、4 heads、Pre-LN、ReLU、dropout 0.1、CLS；复用原 `SetAttention` 组件，从头训练。候选共享 384→192→1 MLP，非法动作 masked softmax。
- 输入包含命名标量与 known mask、角色、可见 Boss、钥匙、当前幕地图、8 个历史房间、聚合牌组、永久属性、遗物计数、药水槽、可见库存、事件阶段和菜单。地图保留坐标与唯一出边；不输入 router 计划。
- 73-item cap、128 候选上限；超限回退，不静默截断。候选不加位置编码，训练打乱候选，推理保持置换等变。
- 事件效果类型和已知概率下的 HP／金币／最大 HP 数值作为候选特征；随机结果本身不进入输入。
- seed、RNG、未来 Boss、终局牌组、未来奖励、胜负和重建证据不进入编码器。`seed_group` 只用于整组划分：train/val/test = 80/10/10。
- 普通局必须明确提供 run-mode 标记；BC 只学习普通胜局，AWR 使用普通局的完整胜负。删失局保留未知，不作为失败。
- 确认轨迹包括 `run_start / decision / run_end`。每条 decision 保存前状态、完整候选、选择、确认后状态、来源；模型只读前状态与候选。同一动作等待生效时不重复打标签；整局和重复 decision 去重。
- checkpoint 冻结格式、架构、词表、标量顺序、缩放、token 数值字段顺序、训练预算及数据指纹。旧价值 checkpoint 不做隐式转换。

## 采集与启用

CommunicationMod 1.6.0 增加全部可见商店商品（包括买不起的）、价格、sold-out/affordable/legal、原 choice_index、删牌费用、已选网格卡、卡牌 misc、奖励金币／药水 ID、运行模式和种子分组。允许真实引擎判定合法的非战斗药水使用及 `discard_potion`。`/state` 不暴露研究快照的 RNG 和池。

以下命令需要使用者另行部署新版桥并启动游戏；本次实现未自动部署：

```powershell
conda run --no-capture-output -n spire python -m sts_ai_framework --policy-trace-dir debug/policy-traces --character IRONCLAD --ascension 20 --max-runs 50
conda run --no-capture-output -n spire python -m selectcard.policy.data --trace-dir debug/policy-traces --output-dir selectcard/processed_data/policy-v1
```

记录器包装 GameClient，捕获 Agent 内部本地多步流程提交的动作；在提交前重新读取状态，收到后续证据才记录决策。超时、ACK、WAIT、生命周期点击和战斗动作不成为策略标签。中途附加的轨迹可保留完整前状态样本，但不能用于整局配对评估。

有 checkpoint 后先影子评分，再显式启用指定类型：

```powershell
python -m sts_ai_framework --policy-checkpoint <policy.pt> --policy-trace-dir debug/policy-shadow
python -m sts_ai_framework --policy-checkpoint <policy.pt> --policy-mode policy --policy-types CARD_REWARD SHOP_SCREEN --policy-trace-dir debug/policy-enabled
```

支持的结构化任务为 CARD_REWARD、BOSS_REWARD、REST、SHOP_SCREEN、MAP、已知语义 EVENT、CHEST、COMBAT_REWARD、已知用途 GRID。多选逐步记录已选集合，区分选择／取消选择、确认、取消。未结构化涅奥／事件／小游戏、未知实体、未训练类型及推理失败保持原策略；轨迹包含 coverage_gap，不能声称这些任务已学会。

## BC 与 AWR 命令

```powershell
python -m selectcard.policy.train --data selectcard/processed_data/policy-v1/examples.jsonl --output-dir selectcard/experiment_results/policy-pilot --stage pilot
python -m selectcard.policy.train --data selectcard/processed_data/policy-v1/examples.jsonl --output-dir selectcard/experiment_results/policy-bc --stage confirm --device cuda
python -m selectcard.policy.train --data <含完整成功与失败的examples.jsonl> --checkpoint <BC-policy.pt> --mode awr --stage confirm --output-dir selectcard/experiment_results/policy-awr --device cuda
python -m selectcard.policy.train --data <examples.jsonl> --checkpoint <policy.pt> --evaluate --evaluation-split val --stage confirm --output-dir selectcard/experiment_results/policy-eval
```

Pilot 固定 run hash 的 1% train/val、8 轮，空分组直接报错，不借用验证样本。确认默认 10 轮、batch 512、AdamW lr 2.8e-4、weight decay .01、clip 1、seed 42。按角色 × 进阶段 × 任务分组均衡抽样，再均匀抽局和决策。词表、频率基线只来自 train；离线报告准确率、NLL、跳过率及角色／进阶／任务分组。

AWR 的独立 critic 用同一套输入规范、独立参数和优化器；`G=Heart胜利?1:0`，γ=1，Monte Carlo MSE。critic 拟合后冻结用于权重 `min(20, exp((G-V)/0.1))`，策略优化加权 BC，负优势仍为正权重。critic／actor 默认各 5 轮，actor lr 1e-4。按运行时间保留最近 2,000 个训练局。没有 Heart 正例不报告改进。本机 `spire` 是 CPU 版 PyTorch；上述 `--device cuda` 用于另行准备的 GPU 环境，CPU 合成闭环测试已通过。

`selectcard.policy.awr_loop` 提供显式长任务调度：默认最多 5 轮，每轮每角色 50 个 A20 完成局。`--collector-command` 指向 JSON 参数数组，必须包含 `{checkpoint}`、`{trace_dir}`、`{character}`、`{runs}` 占位符，可含 `{ascension}`。通过 `subprocess` 参数数组执行，不经过 shell；采集端必须是隔离游戏并提供实际 runtime manifest。调度器校验完成局数、角色、进阶、种子分组后才训练。该任务未自动运行。

例如 collector JSON 可以直接使用现有主循环，无需另写 Agent（先启动隔离游戏，配置 `-Dcommunicationmod.port=15000`，并确保自动重开开启）：

```json
["D:/miniconda3/envs/spire/python.exe", "-m", "sts_ai_framework", "--api-base-url", "http://localhost:15000", "--policy-checkpoint", "{checkpoint}", "--policy-mode", "policy", "--policy-types", "CARD_REWARD", "BOSS_REWARD", "REST", "SHOP_SCREEN", "MAP", "EVENT", "CHEST", "COMBAT_REWARD", "GRID", "--policy-trace-dir", "{trace_dir}", "--character", "{character}", "--ascension", "{ascension}", "--max-runs", "{runs}", "--policy-runtime-manifest", "debug/copied-runtime/runtime-manifest.json"]
```

```powershell
python -m selectcard.policy.awr_loop --data <examples.jsonl> --checkpoint <BC-policy.pt> --collector-command <collector.json> --output-dir selectcard/experiment_results/awr-loop-new --device cuda
```

## 实战证据与测试

实际运行配置通过 `--policy-runtime-manifest <JSON>` 写入轨迹，须包含实际游戏／mod／初始账户配置的 `runtime_fingerprint` 和实际 `combat_budget`。配对评估必须从 floor 0 开始，重置相同初始条件，固定四角色各 100 个独立验证种子；不把退出或丢失记录计为失败。

```powershell
python -m selectcard.policy.export_evaluation --trace-dir <baseline轨迹> --output debug/baseline-evaluation.json
python -m selectcard.policy.export_evaluation --trace-dir <candidate轨迹> --output debug/candidate-evaluation.json
python -m selectcard.policy.evaluate_runs --baseline debug/baseline-evaluation.json --candidate debug/candidate-evaluation.json --output debug/paired-policy-evaluation.json
conda run --no-capture-output -n spire python -m unittest selectcard.policy.tests
conda run --no-capture-output -n spire python -m unittest discover -s sts_ai_framework/tests -t .
Push-Location selectcard/src
conda run --no-capture-output -n spire python -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api
Pop-Location
```

旧价值网络回归已执行 183 项，其中 14 项 `test_map_context` 历史归档检查因 `selectcard/STS Data` 缺失而跳过；恢复归档后用上述原命令复跑。所有合成数据闭环已执行。

比较器要求配对种子、初始状态指纹、运行版本和战斗预算一致，按种子整局配对 cluster bootstrap；不足每角色 100 局或区间下界不大于 0 时保留基线。验证种子与训练轨迹分开；test 只接受显式 `--evaluate --evaluation-split test`。完整数据重建、GPU 训练、长时间采集、test 和部署都不由普通启动自动触发。

## 独立 Policy 游戏入口

先启动加载 CommunicationMod 和 BattleAiMod 的游戏客户端，以及现有无头战斗搜索服务；双实例配置见 [自动战斗说明](../AUTOFIGHT.md)。从仓库根目录运行：

```powershell
conda run --no-capture-output -n spire python -m sts_ai_framework.policy_player
# 指定其他兼容的 policy checkpoint、游戏地址或轮询间隔
conda run --no-capture-output -n spire python -m sts_ai_framework.policy_player --policy-checkpoint <policy.pt> --api-base-url http://localhost:5000 --interval 2
```

默认加载 `selectcard/experiment_results/run_policy/pilot-121289/model/best.pt`，启动时检查模型并显示支持类型。此文件属于本机训练产物，需要自行准备；加载失败时程序退出。默认 pilot 只训练了奖励选牌和 Boss 遗物，程序仅在这些类型且 checkpoint 声明支持时自动选择。地图、事件、商店、奖励领取等其他界面及开局、结束均由玩家手动操作；状态改变后自动继续。候选不完整、未知实体或推理失败时同样等待手动处理。战斗由已有 Battle AI 负责，此入口不启动战斗服务，也不使用价值网络、地图规划器或 LLM。提交动作后等待状态确认；超时提示人工检查，不重复提交。按 Ctrl+C 退出。

奖励选牌的跳过按钮需要 CommunicationMod 1.6.1 或更新版本；更新 mod 后须重启游戏。Boss 遗物界面按桥报告的实际可选动作评分，强制选遗物时不添加虚构的跳过按钮。
