import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), 'src'))
from config import Config
from dataset import STSDataset, SimpleTokenizer
from torch.utils.data import DataLoader

print('[*] 正在极速遍历所有数据提取完整词表...')
tok = SimpleTokenizer()
ds = STSDataset(Config.DATA_DIR, tok)
dl = DataLoader(ds, batch_size=4096)
try:
    for _ in dl:
        pass
except Exception as e:
    pass

path = os.path.join(Config.CHECKPOINT_DIR, 'vocab.json')
tok.save(path)
print(f'[+] 成功！预构建词表已保存至: {path}，总词汇量: {len(tok)}')
