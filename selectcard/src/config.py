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
        "STS_DATA_DIR", os.path.join(BASE_DIR, "processed_data_v4", "a10plus_multiscale_v1")
    )
    TRAIN_SPLIT = 0.8
    VAL_SPLIT = 0.1
    TEST_SPLIT = 0.1
    SPLIT_SEED = "aiplayspire-v2"
    
    # ==========================
    # 训练超参数
    # ==========================
    BATCH_SIZE = 512
    DEVICE = "auto"
    EPOCHS = 10
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
    # Confirmation uses its complete declared 10-epoch cosine budget.
    # Experiment configurations disable early stopping.
    EARLY_STOPPING_START_EPOCH = 10
    # Worker count is capped by available CPUs; profile memory/throughput on
    # the actual target device rather than extrapolating from older logs.
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
    # global feature.  Progress and resource features use the frozen 11-dimensional schema.
    NUM_GLOBAL_FEATURES = 11
    DROPOUT = 0.1            # Dropout 比例
    GLOBAL_CONDITIONING = "token"  # "token" or "late_concat"
    NORM_POSITION = "pre"           # "pre" or "post"
    FFN_TYPE = "relu"
    NORM_TYPE = "layernorm"
    MAP_ENCODER = "none"
    MAP_LATENTS = 0
    COORDINATE_EMBEDDING = "linear"
    POOLING = "cls"
    REGISTER_TOKENS = 0
    RUN_FRACTION = 1.0
    EXPERIMENT_METADATA = {}
    RANDOM_SEED = 42
    HEART_WIN_BONUS_FLOORS = 3.0
    HEART_HEAD_LOSS_WEIGHT = 0.25
    # Multiscale gives near/far risk cells separate weights. The original macro
    # relative objective remains available for the full-fine control.
    VALUE_LOSS_MODE = "multiscale"
    RESOLUTION_MODE = "multiscale"
    FAR_LOSS_WEIGHT = 0.25
    MEAN_LOSS_WEIGHT = 0.1
    BASELINE_ENTROPY_FLOOR = 0.05
    VALUE_DEBUG = os.environ.get("STS_VALUE_DEBUG", "").lower() in {"1", "true", "yes"}

    # ==========================
    # 地图条件化上下文（[MAP]/[HISTORY]/[KEYS] + key 辅助头）
    # ==========================
    # Master switch for the map-conditioned extension. Checkpoints store their
    # own architecture; format-11 and earlier checkpoints require retraining.
    USE_MAP_CONTEXT = True
    # Mask only the 105 [MAP] and 8 [HISTORY] slots. Keep [KEYS], items,
    # the 73-item budget and every parameter identical to the control.
    ABLATE_MAP_HISTORY = False
    # First N epochs mask map/history tokens entirely for both train and
    # validation (curriculum warm-up so the v3 run starts from its v2 point).
    CONTEXT_CURRICULUM_EPOCHS = 3
    # Auxiliary key-progress head supervision weight (ruby/emerald/sapphire).
    KEY_HEAD_LOSS_WEIGHT = 0.2
    # Zero-margin ordering for mechanics that are strictly non-negative when
    # added for free to an otherwise identical post-action state.
    MONOTONIC_LOSS_WEIGHT = 0.1

    # ==========================
    # 输出与保存
    # ==========================
    CHECKPOINT_DIR = os.path.join(BASE_DIR, "checkpoints")
    CHECKPOINT_NAME = "sts_value_model_final.pth"
    TRAINING_REPORT_NAME = "training_report.png"
