import os
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, random_split
import matplotlib.pyplot as plt
from sklearn.metrics import precision_recall_curve, auc

# 导入我们的 Model 和 Dataset
from model import STSValueNetwork
from dataset import STSDataset, SimpleTokenizer
from config import Config

def train_model():
    # 1. 基础配置
    data_dir = Config.DATA_DIR
    batch_size = Config.BATCH_SIZE
    epochs = Config.EPOCHS
    learning_rate = Config.LEARNING_RATE
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    # 用于绘图的数据记录
    train_losses_history = []
    val_losses_history = []
    lr_history = []

    # 2. 构建 DataLoader
    tokenizer = SimpleTokenizer()
    full_dataset = STSDataset(parquet_dir=data_dir, tokenizer=tokenizer)
    
    if len(full_dataset) == 0:
        print(f"未找到训练数据，当前读取目录: {data_dir}")
        print("请先运行 data_pipeline.py 生成 parquet 文件。")
        return

    # 切分训练集和验证集 (80% / 20%)
    train_size = int(Config.TRAIN_SPLIT * len(full_dataset))
    val_size = len(full_dataset) - train_size
    train_dataset, val_dataset = random_split(full_dataset, [train_size, val_size])

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

    # === 自动估计 pos_weight ===
    print("[*] 正在分析样本分布以计算 pos_weight...")
    all_labels = []
    # 限制分析样本量以节省空间，或者直接从训练集统计
    for _, _, _, _, batch_labels in train_loader:
        all_labels.append(batch_labels)
    all_labels = torch.cat(all_labels)
    num_pos = all_labels.sum().item()
    num_neg = len(all_labels) - num_pos
    # 防止除零
    pos_weight_val = num_neg / max(1.0, num_pos)
    pos_weight = torch.tensor([pos_weight_val]).to(device)
    print(f"[*] 样本分布: 正样本={num_pos}, 负样本={num_neg}, 自动计算 pos_weight={pos_weight_val:.4f}")

    # === 保存包含训练集词汇的词表 ===
    vocab_path = os.path.join(Config.CHECKPOINT_DIR, "vocab.json")
    tokenizer.save(vocab_path)
    print(f"[*] 初始化词表已保存至: {vocab_path} (当前 Vocab Size: {len(tokenizer)})")
    # ===============================

    # 3. 初始化模型、损失函数与优化器
    model = STSValueNetwork(
        vocab_size=len(tokenizer) + Config.VOCAB_BUFFER, # 预留词表空间
        max_upgrade=Config.MAX_UPGRADE, 
        max_count=Config.MAX_COUNT, 
        d_model=Config.D_MODEL,
        n_heads=Config.N_HEADS,
        n_layers=Config.N_LAYERS,
        num_global_features=Config.NUM_GLOBAL_FEATURES,
        dropout=Config.DROPOUT
    ).to(device)
    
    # 模型输出 logits，使用 BCEWithLogitsLoss 并加入 pos_weight
    criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
    optimizer = optim.AdamW(model.parameters(), lr=learning_rate, weight_decay=0.01)
    
    # 动态学习率调度器：余弦退火
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    # Early Stopping & Best Checkpoint 初始化
    best_val_loss = float('inf')
    patience = getattr(Config, 'EARLY_STOPPING_PATIENCE', 5) # 如果 Config 中没有定义，默认 5 个 Epoch 早停
    patience_counter = 0

    os.makedirs(Config.CHECKPOINT_DIR, exist_ok=True)
    checkpoint_path = os.path.join(Config.CHECKPOINT_DIR, Config.CHECKPOINT_NAME)
    best_checkpoint_path = os.path.join(Config.CHECKPOINT_DIR, "best_" + Config.CHECKPOINT_NAME)

    # 4. 开始训练循环
    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        
        for batch_idx, (seq, upg, cnt, glob_feat, labels) in enumerate(train_loader):
            seq, upg, cnt, glob_feat, labels = (
                seq.to(device), upg.to(device), cnt.to(device), 
                glob_feat.to(device), labels.to(device)
            )
            
            optimizer.zero_grad()
            
            # 前向传播
            preds = model(seq, upg, cnt, glob_feat)
            
            # 计算 Loss 并反向传播
            loss = criterion(preds, labels)
            loss.backward()
            
            # === 新增：梯度裁剪 (Gradient Clipping) ===
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            
            optimizer.step()
            
            total_loss += loss.item()
            
            # 每隔 10 次记录一次 loss
            if batch_idx % 10 == 0:
                train_losses_history.append(loss.item())

            if batch_idx % 100 == 0:
                print(f"Epoch [{epoch+1}/{epochs}] Batch {batch_idx} Loss: {loss.item():.4f}")
                
        avg_train_loss = total_loss / len(train_loader)
        
        # 5. 验证模型
        model.eval()
        val_loss = 0.0
        correct_preds = 0
        total_preds = 0
        all_val_probs = []
        all_val_labels = []
        
        with torch.no_grad():
            for seq, upg, cnt, glob_feat, labels in val_loader:
                seq, upg, cnt, glob_feat, labels = (
                    seq.to(device), upg.to(device), cnt.to(device), 
                    glob_feat.to(device), labels.to(device)
                )
                preds = model(seq, upg, cnt, glob_feat)
                loss = criterion(preds, labels)
                val_loss += loss.item()
                
                # 记录用于 PR-AUC 计算的概率和标签
                probs = torch.sigmoid(preds).cpu().numpy().reshape(-1)
                all_val_probs.extend(probs)
                all_val_labels.extend(labels.cpu().numpy().reshape(-1))

                # logits > 0 等价于 sigmoid(logits) > 0.5
                binary_preds = (preds > 0.0).float()
                correct_preds += (binary_preds == labels).sum().item()
                total_preds += labels.size(0)

        avg_val_loss = val_loss / len(val_loader)
        val_losses_history.append(avg_val_loss)
        
        # 计算 PR-AUC
        precision, recall, _ = precision_recall_curve(all_val_labels, all_val_probs)
        pr_auc = auc(recall, precision)

        # 更新学习率
        scheduler.step()
        curr_lr = optimizer.param_groups[0]['lr']
        lr_history.append(curr_lr)

        accuracy = correct_preds / total_preds
        print(
            f"--- Epoch {epoch+1} Summary: "
            f"Train Loss={avg_train_loss:.4f} | "
            f"Val Loss={avg_val_loss:.4f} | "
            f"PR-AUC={pr_auc:.4f} | "
            f"Val Acc={accuracy*100:.2f}% | "
            f"LR={curr_lr:.6f} ---"
        )

        # Early Stopping & 保存最佳模型逻辑
        if avg_val_loss < best_val_loss:
            best_val_loss = avg_val_loss
            patience_counter = 0
            torch.save(model.state_dict(), best_checkpoint_path)
            print(f"[*] 验证集 Loss 创出新低 ({best_val_loss:.4f})，已保存 Best Model 到: {best_checkpoint_path}")
        else:
            patience_counter += 1
            print(f"[!] 验证集 Loss 未下降，Early Stopping 计数: {patience_counter} / {patience}")
            if patience_counter >= patience:
                print(f"连续 {patience} 个 Epoch 验证集 Loss 未降低，触发 Early Stopping！提前结束训练。")
                break

    # 6. 保存最后一次 Epoch 的权重
    torch.save(model.state_dict(), checkpoint_path)
    print(f"模型训练循环结束，最后一个 Epoch 的权重已保存至: {checkpoint_path}")

    # === 训练结束后完整保存 Tokenizer 词表 ===
    vocab_path = os.path.join(Config.CHECKPOINT_DIR, "vocab.json")
    tokenizer.save(vocab_path)
    print(f"[*] 最终的词表已保存至: {vocab_path} (Vocab Size: {len(tokenizer)})")
    # ===============================

    # 7. 绘制训练结果图
    plt.figure(figsize=(15, 5))
    
    # Loss 曲线
    plt.subplot(1, 2, 1)
    plt.plot(train_losses_history, label='Train Loss (per 10 batches)', alpha=0.5)
    # 转换验证集索引以对应训练集比例
    val_x = [len(train_losses_history) // len(val_losses_history) * (i+1) for i in range(len(val_losses_history))]
    plt.plot(val_x, val_losses_history, marker='o', color='red', label='Val Loss (per epoch)')
    plt.title('Loss History')
    plt.xlabel('Iterations / Epochs')
    plt.ylabel('Loss')
    plt.legend()

    # Learning Rate 曲线
    plt.subplot(1, 2, 2)
    plt.plot(lr_history, marker='s', color='green', label='Learning Rate')
    plt.title('Learning Rate Schedule')
    plt.xlabel('Epochs')
    plt.ylabel('LR')
    plt.legend()

    plot_path = os.path.join(Config.CHECKPOINT_DIR, "training_report.png")
    plt.tight_layout()
    plt.savefig(plot_path)
    print(f"[*] 训练报告图已保存至: {plot_path}")

if __name__ == "__main__":
    train_model()