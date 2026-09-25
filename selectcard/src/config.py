import os

class Config:
    """
    模型训练与架构的全局参数配置
    """
    # ==========================
    # 数据路径与构建配置
    # ==========================
    BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    # STS_DATA_DIR 让训练可指向 enrich 后的新数据集目录，而不覆盖旧数据。
    DATA_DIR = os.environ.get(
        "STS_DATA_DIR", os.path.join(BASE_DIR, "processed_data_v2")
    )
    TRAIN_SPLIT = 0.8
    VAL_SPLIT = 0.1
    TEST_SPLIT = 0.1
    SPLIT_SEED = "aiplayspire-v2"
    
    # ==========================
    # 训练超参数
    # ==========================
    BATCH_SIZE = 512
    EPOCHS = 20
    # lr 2e-4 在 batch 256 下收敛良好；batch 翻倍后按 sqrt 缩放补偿
    # （512/256)^0.5 ≈ 1.41 → 2.8e-4），配合余弦衰减调度。
    LEARNING_RATE = 2.8e-4
    # None means decay across the full configured run.  A shorter decay is
    # useful for fixed-budget comparison jobs and is clamped at its minimum
    # rather than cycling back up.
    COSINE_DECAY_EPOCHS = None
    LR_MIN_RATIO = 0.0
    LOG_INTERVAL_SECONDS = 60
    EARLY_STOPPING_PATIENCE = 5
    # Let the 20-epoch cosine schedule leave its high-learning-rate phase before
    # treating validation fluctuations as evidence that training has stalled.
    EARLY_STOPPING_START_EPOCH = 10
    # 峰值显存仅 ~0.65 GiB（RTX 5090 32GB），瓶颈在 CPU dataloader：
    # 提交 SLURM 作业时请用 --cpus-per-task=32，worker 数会按实际 CPU 自动裁剪
    MAX_AUTO_DATALOADER_WORKERS = 32
    DATALOADER_PREFETCH_FACTOR = 4

    # ==========================
    # 模型架构超参数
    # ==========================
    MAX_UPGRADE = 15         # 最大升级等级
    MAX_COUNT = 10           # 最大拥有数量限制
    # MAX_SEQ_LEN is the full sequence budget; with map context enabled the
    # fixed context block is [CLS]+[GLOBAL]+[CHARACTER]+[BOSS]+[KEYS]+105 map+8
    # history tokens, so the item budget shrinks accordingly (73 at
    # 192/105/8). The value stays 73 because CHARACTER replaces the removed PLAN slot.
    MAX_SEQ_LEN = 192
    MAX_ITEM_TOKENS = 192 - 4 - 1 - 105 - 1 - 8  # 73
    D_MODEL = 192            # 隐藏层/嵌入层维度
    N_HEADS = 4              # 注意力头数
    N_LAYERS = 5             # 注意力层数
    # Boss identity is a separate categorical [BOSS] context token, not a numeric
    # global feature.  Keep this input contract at nine dimensions.
    NUM_GLOBAL_FEATURES = 9
    DROPOUT = 0.1            # Dropout 比例
    GLOBAL_CONDITIONING = "token"  # "token" or "late_concat"
    NORM_POSITION = "pre"           # "pre" or "post"
    RANDOM_SEED = 42
    HEART_WIN_BONUS_FLOORS = 3.0
    HEART_HEAD_LOSS_WEIGHT = 1.0
    # ``macro_relative_nll`` gives every stopping bucket equal influence and
    # scales it by the entropy of its training-set prior.  ``micro_nll`` keeps
    # the legacy risk-cell average for reproducible ablations.
    VALUE_LOSS_MODE = "macro_relative_nll"
    BASELINE_ENTROPY_FLOOR = 0.05
    VALUE_DEBUG = os.environ.get("STS_VALUE_DEBUG", "").lower() in {"1", "true", "yes"}

    # ==========================
    # 地图条件化上下文（[MAP]/[HISTORY]/[KEYS] + key 辅助头）
    # ==========================
    # Master switch for the map-conditioned extension. Checkpoints store their
    # own flag; format-8 and earlier checkpoints require retraining.
    USE_MAP_CONTEXT = True
    # First N epochs mask map/history tokens entirely for both train and
    # validation (curriculum warm-up so the v3 run starts from its v2 point).
    CONTEXT_CURRICULUM_EPOCHS = 3
    # Auxiliary key-progress head supervision weight (ruby/emerald/sapphire).
    KEY_HEAD_LOSS_WEIGHT = 0.2

    # ==========================
    # 输出与保存
    # ==========================
    CHECKPOINT_DIR = os.path.join(BASE_DIR, "checkpoints")
    CHECKPOINT_NAME = "sts_value_model_final.pth"
    TRAINING_REPORT_NAME = "training_report.png"
