import os
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

# Slay the Spire Mod API
STS_API_BASE_URL = os.getenv("STS_API_BASE_URL", "http://localhost:5000")
STS_API_STATE_URL = f"{STS_API_BASE_URL}/state"
STS_API_ACTION_URL = f"{STS_API_BASE_URL}/action"

# DeepSeek API (OpenAI 兼容格式)
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash")

# Model name (CLI --model flag default)
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-v4-flash")

# Logging
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
DEBUG_PROMPT_FILE = os.getenv("DEBUG_PROMPT_FILE", "debug/latest_prompt.txt")
RUN_LOG_DIR = os.getenv("RUN_LOG_DIR", "debug")  # 运行日志目录(每次运行生成 run_YYYYMMDD_HHMMSS.log)

# Run lifecycle (auto next run). CHARACTER: IRONCLAD / SILENT / DEFECT / WATCHER.
AUTO_RESTART = os.getenv("AUTO_RESTART", "true").strip().lower() in ("1", "true", "yes", "on")
CHARACTER = os.getenv("CHARACTER", "IRONCLAD").strip().upper()
ASCENSION = int(os.getenv("ASCENSION", "15"))
RESTART_DELAY = float(os.getenv("RESTART_DELAY", "2.0"))

# Battle-stall watchdog (log-only): warn when the battle-owned state has not
# changed for this many seconds. 0 disables. A legit battle search can freeze
# the fight for up to ~90s (DEEP profile) plus replay time, so keep the
# threshold well above that.
BATTLE_STALL_WARN_SECONDS = float(os.getenv("BATTLE_STALL_WARN_SECONDS", "300"))

