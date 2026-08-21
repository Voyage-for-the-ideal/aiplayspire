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
    # （512/256)^0.5 ≈ 1.41 → 2.8e-4），配合 CosineAnnealingLR(T_max=EPOCHS)
    LEARNING_RATE = 2.8e-4
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
    MAX_SEQ_LEN = 64
    D_MODEL = 128            # 隐藏层/嵌入层维度
    N_HEADS = 4              # 注意力头数
    N_LAYERS = 3             # 注意力层数
    # Boss identity is a separate categorical [BOSS] context token, not a numeric
    # global feature.  Keep this input contract at nine dimensions.
    NUM_GLOBAL_FEATURES = 9
    DROPOUT = 0.1            # Dropout 比例
    GLOBAL_CONDITIONING = "token"  # "token" or "late_concat"
    NORM_POSITION = "pre"           # "pre" or "post"
    RANDOM_SEED = 42
    VALUE_WEIGHTS = {
        "reach17": 0.10,
        "reach34": 0.20,
        "reach50": 0.25,
        "win": 0.45,
    }
    VALUE_DEBUG = os.environ.get("STS_VALUE_DEBUG", "").lower() in {"1", "true", "yes"}

    # ==========================
    # 输出与保存
    # ==========================
    CHECKPOINT_DIR = os.path.join(BASE_DIR, "checkpoints")
    CHECKPOINT_NAME = "sts_value_model_final.pth"
    TRAINING_REPORT_NAME = "training_report.png"
