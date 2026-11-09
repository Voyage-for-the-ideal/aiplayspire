"""Batch real-engine snapshot replay; use acceptance arguments including --oracle-port."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from battle_engine.tools.acceptance import main

if __name__ == "__main__":
    raise SystemExit(main())
