import os

class Config:
    """
    模型训练与架构的全局参数配置
    """
    # ==========================
    # 数据路径与构建配置
    # ==========================
    BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    DATA_DIR = os.path.join(BASE_DIR, "processed_data_v2")
    TRAIN_SPLIT = 0.8
    VAL_SPLIT = 0.1
    TEST_SPLIT = 0.1
    SPLIT_SEED = "aiplayspire-v2"
    
    # ==========================
    # 训练超参数
    # ==========================
    BATCH_SIZE = 64
    EPOCHS = 10
    LEARNING_RATE = 1e-4

    # ==========================
    # 模型架构超参数
    # ==========================
    MAX_UPGRADE = 15         # 最大升级等级
    MAX_COUNT = 10           # 最大拥有数量限制
    MAX_SEQ_LEN = 64
    D_MODEL = 128            # 隐藏层/嵌入层维度
    N_HEADS = 4              # 注意力头数
    N_LAYERS = 3             # 注意力层数
    NUM_GLOBAL_FEATURES = 4  # floor, hp, gold, ascension
    DROPOUT = 0.1            # Dropout 比例
    GLOBAL_CONDITIONING = "token"  # "token" or "late_concat"
    NORM_POSITION = "pre"           # "pre" or "post"
    RANDOM_SEED = 42

    # ==========================
    # 输出与保存
    # ==========================
    CHECKPOINT_DIR = os.path.join(BASE_DIR, "checkpoints")
    CHECKPOINT_NAME = "sts_value_model_final.pth"
