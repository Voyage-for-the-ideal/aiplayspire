import os
import sys
from typing import Optional

from openai import OpenAI

from .agent_base import Agent
from .knowledge_base import KnowledgeBase
from .llm_agent_parts import ActionMixin, ChoiceMixin, DecisionMixin, InfoPromptMixin

# 添加 masterspire/selectcard 到 PYTHONPATH 以便导入模型
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "aiplayspire", "selectcard"))
try:
    from src.inference import STSInferenceEngine
except ImportError:
    STSInferenceEngine = None


class LLMAgent(ActionMixin, DecisionMixin, InfoPromptMixin, ChoiceMixin, Agent):
    def __init__(
        self,
        model_name: str = "deepseek-chat",
        knowledge_base: Optional[KnowledgeBase] = None,
        game_client=None,
        debug_prompt_file: Optional[str] = None,
    ):
        self.model_name = model_name
        self.knowledge_base = knowledge_base or KnowledgeBase()
        self.game_client = game_client
        self.debug_prompt_file = debug_prompt_file
        self.history = []
        self.last_screen_type = None

        # Initialize DeepSeek LLM client via OpenAI SDK
        api_key = os.getenv("DEEPSEEK_API_KEY", "")
        base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
        self.llm_client = OpenAI(api_key=api_key, base_url=base_url)

        # 加载本地选卡决策模型
        self.value_engine = None
        if STSInferenceEngine is not None:
            model_base_dir = os.path.join(
                os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
                "aiplayspire",
                "selectcard",
            )
            model_path = os.path.join(model_base_dir, "checkpoints", "best_sts_value_model_final.pth")
            vocab_path = os.path.join(model_base_dir, "checkpoints", "vocab.json")
            if os.path.exists(model_path) and os.path.exists(vocab_path):
                self.value_engine = STSInferenceEngine(model_path=model_path, vocab_path=vocab_path)
                print(f"\033[32m已加载本地卡牌决策模型: {model_path}\033[0m")
            else:
                print(f"\033[33m本地卡牌决策模型/词表 不存在，请先训练模型并确保在 checkpoints 目录下\033[0m")
        else:
            print(f"\033[31m未找到 selectcard.src.inference，无法加载决策模型\033[0m")
