import os
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

# Slay the Spire Mod API
STS_API_BASE_URL = os.getenv("STS_API_BASE_URL", "http://localhost:5000")
STS_API_STATE_URL = f"{STS_API_BASE_URL}/state"
STS_API_ACTION_URL = f"{STS_API_BASE_URL}/action"

# LLM API
# Default model
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o")
# You can use litellm to support multiple providers.
# Make sure to set API keys in .env (e.g., OPENAI_API_KEY, ANTHROPIC_API_KEY)

# Logging
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
DEBUG_PROMPT_FILE = os.getenv("DEBUG_PROMPT_FILE", "")
