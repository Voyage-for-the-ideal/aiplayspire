import os
import sys
from typing import Optional

import colorama
from colorama import Fore, Style

from .agent_base import Agent
from .knowledge_base import KnowledgeBase
from .llm_agent_parts import ActionMixin, ChoiceMixin, DecisionMixin, InfoPromptMixin

# 添加 masterspire/selectcard 到 PYTHONPATH 以便导入模型
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "masterspire", "selectcard"))
try:
    from src.inference import STSInferenceEngine
except ImportError:
    STSInferenceEngine = None

colorama.init()


class LLMAgent(ActionMixin, DecisionMixin, InfoPromptMixin, ChoiceMixin, Agent):
    def __init__(
        self,
        model_name: str = "gpt-4o",
        knowledge_base: Optional[KnowledgeBase] = None,
        game_client=None,
        debug_prompt_file: Optional[str] = None,
    ):
        self.model_name = model_name
        self.knowledge_base = knowledge_base or KnowledgeBase()
        self.game_client = game_client
        self.debug_prompt_file = debug_prompt_file
        self.history = []  # 如果需要上下文记忆，可以保存历史记录
        self.last_screen_type = None

        # 加载本地选卡决策模型
        self.value_engine = None
        if STSInferenceEngine is not None:
            model_base_dir = os.path.join(
                os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
                "masterspire",
                "selectcard",
            )
            model_path = os.path.join(model_base_dir, "checkpoints", "best_sts_value_model_final.pth")
            vocab_path = os.path.join(model_base_dir, "checkpoints", "vocab.json")
            if os.path.exists(model_path) and os.path.exists(vocab_path):
                self.value_engine = STSInferenceEngine(model_path=model_path, vocab_path=vocab_path)
                print(Fore.GREEN + f"已加载本地卡牌决策模型: {model_path}" + Style.RESET_ALL)
            else:
                print(Fore.YELLOW + "本地卡牌决策模型/词表 不存在，请先训练模型并确保在 checkpoints 目录下" + Style.RESET_ALL)
        else:
            print(Fore.RED + "未找到 selectcard.src.inference，无法加载决策模型" + Style.RESET_ALL)
