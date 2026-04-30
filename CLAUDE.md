# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **aiplayspire** — an AI bot for Slay the Spire. Three subsystems work together:

1. **StSCommunicationMod** (Java) — A mod that opens HTTP port 5000 inside the game, exposing `/state`, `/action`, `/card_info`.
2. **sts_ai_framework** (Python) — The main AI client that polls game state, decides actions via LLM + local value network, and submits them.
3. **selectcard** (Python) — A deep learning project that trains a Set Transformer survival-value network used by the AI framework for card/relic/event/shop decisions.

## Common Commands

### sts_ai_framework
```bash
# Install deps
pip install -r sts_ai_framework/requirements.txt

# Run the AI agent (must have game + CommunicationMod running)
python -m sts_ai_framework --model deepseek/deepseek-chat --interval 2.0
```

### StSCommunicationMod (Java)

**Local game paths** (configured in `pom.xml`, `build.ps1`, `Build.java`):
- Game root: `D:\Program Files\Slay the Spire` — contains `desktop-1.0.jar`, `ModTheSpire.jar`
- Mods: `D:\Program Files\Slay the Spire\mods` — contains `BaseMod.jar`, `StSLib.jar`

```bash
# Build with Maven
cd StSCommunicationMod && mvn package
# Build without Maven (PowerShell)
.\build.ps1
# Or compile and run Build.java directly
javac Build.java && java Build
```

### selectcard
```bash
# Process raw JSON run data into training samples
python src/data_pipeline.py
# Train
python src/train.py
# Run inference API
uvicorn src.api:app --reload
```

## Architecture

### `sts_ai_framework` — AI Agent

Entry point: `__main__.py`. Main loop: poll `/state` → `agent.choose_action(state)` → POST `/action` → detect effect.

`LLMAgent` uses Python MRO with four mixin classes:

| Mixin | Role |
|---|---|
| `ActionMixin` | `choose_action()` — main dispatch and fallback orchestration. Handles routing to local model vs LLM based on `screen_type`. |
| `ChoiceMixin` | Choice-list handling (`_build_unified_choices`, `_is_choice_state`, `_is_button_state`), campfire/combat-reward logic, safe fallback actions. |
| `DecisionMixin` | Local value-model decisions for events, shops, card rewards. Parses choice text into structured effects (HP changes, gold, relics, curses, card modifications). |
| `InfoPromptMixin` | Card info resolution (local KB + `/card_info` API), map summary with BFS, LLM prompt building, debug prompt file writing. |

Decision flow: local value network handles CARD_REWARD, SHOP_SCREEN, EVENT, REST screens. Combat (screen_type=NONE) falls through to LLM. COMBAT_REWARD and GRID screens use hardcoded heuristics.

`GameState` and `GameAction` are Pydantic models in `models.py`. `GameClient` wraps HTTP calls in `game_client.py`. `KnowledgeBase` in `knowledge_base.py` has hand-coded monster AI patterns and card descriptions.

### `StSCommunicationMod` — Game Mod

`CommunicationMod.java` registers with BaseMod as `PostInitializeSubscriber` + `PostUpdateSubscriber`. On init, starts `CommunicationServer` (a `com.sun.net.httpserver.HttpServer`). On each frame update, `StateController.updateState()` snapshots state, `ActionController.processQueue()` drains the action queue on the game thread.

`GameStateConverter.java` serializes full game state to JSON via Gson. Key detail: `/action` is async queue — HTTP returns `{"status":"queued"}` immediately; actual execution happens on next `receivePostUpdate()`.

### `selectcard` — Survival Value Network

- **Model**: `STSValueNetwork` in `model.py` — a Set Transformer (no positional encoding) with `token_emb + upgrade_emb + count_emb` per card/relic. `[CLS]` token pooled with global features (floor, HP, gold, ascension) for a single survival-probability output.
- **Reconstructor**: `RunReconstructor` in `reconstructor.py` — "time machine" that replays run history to reconstruct deck/relics/gold at each floor from end-of-run JSON data.
- **Data pipeline**: `data_pipeline.py` — streaming ProcessPoolExecutor → Parquet chunks. Filters A15+, pre-2020 data, victory:false without killed_by (abandoned runs).
- **Inference**: `STSInferenceEngine` in `inference.py` — `_apply_choice()` simulates state changes, `recommend_choice()` evaluates all candidates, `shop_greedy_search()` iteratively buys the best single item.
- **API**: FastAPI in `api.py` — endpoints `/recommend/choice` and `/recommend/shop`.

## Key Behavior Notes

- **Action effectiveness detection**: After submitting an action, the framework polls state and checks for visible changes (hand size, energy, HP, monster HP, screen type, choice list). See `_is_action_effective()` in `__main__.py`.
- **Safe fallback chain**: If LLM fails, `_build_safe_fallback_action()` tries choice-list mapping → button proceed/cancel → combat fallback (play any valid card) → wait.
- **Campfire (REST)**: Evaluates all options (rest, smith each card, toke each card, dig, lift) via value network, sets `intended_smith_card`/`intended_purge_card` for downstream GRID handling.
- **GRID auto-handling**: When the game shows a grid picker after smith/purge, the framework matches card names automatically.
- **Map navigation**: Uses BFS from each map choice node to report distances to nearest campfire/shop/elite in the prompt.
- **Omamori-aware**: Both `DecisionMixin` and `STSInferenceEngine` check for Omamori charges when evaluating curse events.
- **selectcard reference path**: `sts_ai_framework/llm_agent.py` imports from `../masterspire/selectcard` (hardcoded relative path outside this repo).
