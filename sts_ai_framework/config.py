import os
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

# Slay the Spire Mod API
STS_API_BASE_URL = os.getenv("STS_API_BASE_URL", "http://localhost:5000")
STS_API_STATE_URL = f"{STS_API_BASE_URL}/state"
STS_API_ACTION_URL = f"{STS_API_BASE_URL}/action"

# DeepSeek API
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# Model name (CLI --model flag default)
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")

# Logging
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
DEBUG_PROMPT_FILE = os.getenv("DEBUG_PROMPT_FILE", "debug/latest_prompt.txt")

