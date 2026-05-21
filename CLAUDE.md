# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **aiplayspire** — an AI bot for Slay the Spire. Six subsystems work together:

1. **StSCommunicationMod** (Java) — A mod that opens HTTP port 5000 inside the game, exposing `/state`, `/action`, `/card_info`.
2. **sts_ai_framework** (Python) — The main AI client that polls game state, decides actions via LLM + local value network, and submits them.
3. **selectcard** (Python) — A deep learning project that trains a Set Transformer survival-value network used by the AI framework for card/relic/event/shop decisions.
4. **STSStateSaver** (Java) — Game state serialization. Captures full battle state (player, monsters, powers, relics, orbs, actions, select screens) to JSON.
5. **LudicrousSpeed** (Java) — Fast simulation engine. Replaces normal game action loop with blocking simulation via `Controller` interface. Command system for card plays, potions, screen selects.
6. **scumthespire** (Java) — Battle AI (BattleAiMod). Server/client architecture — AI server runs headless, client sends battle state, server tree-searches optimal plays via `BattleAiController`.

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

# Launch the game with ModTheSpire
cd "D:\Program Files\Slay the Spire"
jre\bin\java.exe -jar ModTheSpire.jar
```

### STSStateSaver, LudicrousSpeed, scumthespire (Java — Battle AI mods)

These three mods form a pipeline: **STSStateSaver** serializes game state → **LudicrousSpeed** simulates actions fast → **scumthespire** (BattleAiMod) tree-searches optimal plays.

Dependencies are system-scoped via `../lib/` and `../_ModTheSpire/mods/`. Build in dependency order:

```bash
# First-time dependency setup (from game installation)
mkdir -p lib _ModTheSpire/mods
cp "D:/Program Files/Slay the Spire/desktop-1.0.jar" lib/
cp "D:/Program Files/Slay the Spire/ModTheSpire.jar" lib/
cp "D:/Program Files/Slay the Spire/mods/BaseMod.jar" _ModTheSpire/mods/
cp "D:/Program Files/Slay the Spire/mods/StSLib.jar" _ModTheSpire/mods/

# Build all four Java mods in order
cd STSStateSaver && mvn package     # → ../_ModTheSpire/mods/SaveStateMod.jar
cd LudicrousSpeed && mvn package    # → ../_ModTheSpire/mods/LudicrousSpeed.jar
cd StSCommunicationMod && mvn package  # → ../_ModTheSpire/mods/CommunicationMod.jar
cd scumthespire && mvn package      # → ../_ModTheSpire/mods/BattleAiMod.jar

# Or use the unified script
./build_all.sh
```

### Running the Battle AI

1. Start server: `java -DisServer=true -jar ModTheSpire.jar`
2. Start client: `java -jar ModTheSpire.jar` (normal launch with all 4 mods enabled)
3. Enter battle → press "Start Steve" button → AI plays the fight

### selectcard
```bash
# Process raw JSON run data into training samples
python src/data_pipeline.py
# Train
python src/train.py
# Run inference API
uvicorn src.api:app --reload
```

## `cardcrawl` — Decompiled Game Source

Decompiled from `desktop-1.0.jar` (~2000 Java files). Read-only reference for understanding game internals when building the AI.

**文件布局**: `.java` 文件直接放在主题子目录下（如 `cards/AbstractCard.java`），不是 `com/megacrit/cardcrawl/` 路径。目录名即对应包名段。

**导航技巧**: 追踪一个游戏机制时，按以下顺序查找：`actions/`（队列化的效果）→ 触发它的 `powers/`（持续修改器）或 `relics/`（遗物钩子）→ `AbstractDungeon`（全局条件检查）。大部分游戏逻辑都经由 `actions/` 和 `powers/` 流转。

| Directory | Contents |
|---|---|
| `cards/` | `AbstractCard` (130KB, the big one), `CardGroup`, `DamageInfo`, `Soul`. Subdirs: `red/` (Ironclad), `green/` (Silent), `blue/` (Defect), `purple/` (Watcher), `colorless/`, `curses/`, `status/`, `tempCards/` |
| `core/` | `CardCrawlGame` (59KB, main game class), `AbstractCreature` (47KB), `Settings`, `EnergyManager`, `OverlayMenu`, `GameCursor` |
| `monsters/` | `AbstractMonster` (50KB), `MonsterGroup`, `MonsterInfo`. Subdirs by act: `exordium/`, `city/`, `beyond/`, `ending/` |
| `relics/` | `AbstractRelic` (42KB) + ~170 individual relic classes (one `.java` per relic) |
| `powers/` | `AbstractPower` + ~120 power classes. `watcher/` subdir for stance-related powers |
| `rooms/` | `AbstractRoom` (38KB), `MonsterRoom`, `MonsterRoomBoss`, `MonsterRoomElite`, `EventRoom`, `RestRoom`, `ShopRoom`, `TreasureRoom`, `TreasureRoomBoss`, `VictoryRoom`, `TrueVictoryRoom`, `CampfireUI` |
| `events/` | `AbstractEvent` (23KB), `GenericEventDialog`, `RoomEventDialog`. Subdirs: `exordium/`, `city/`, `beyond/`, `shrines/` |
| `actions/` | `AbstractGameAction`, `GameActionManager`. Subdirs: `common/`, `unique/`, `defect/`, `watcher/`, `utility/`, `animations/` |
| `dungeons/` | `AbstractDungeon` (the global game state), `Exordium`, `TheCity`, `TheBeyond`, `TheEnding` |
| `characters/` | `AbstractPlayer`, `Ironclad`, `TheSilent`, `Defect`, `Watcher`, `CharacterManager` |
| `orbs/` | `AbstractOrb`, `Dark`, `Frost`, `Lightning`, `Plasma`, `EmptyOrbSlot` |
| `map/` | `DungeonMap`, `MapGenerator`, `MapRoomNode`, `MapEdge`, `RoomTypeAssigner` |
| `ui/` | `buttons/`, `campfire/`, `panels/` (EnergyPanel, etc.) |
| `potions/` | Potion classes (one per potion) |
| `blights/` | Blight classes (Endless mode negative relics) |
| `stances/` | Watcher stance classes |
| `rewards/` | Reward item classes |
| `shop/` | Shop screen logic |
| `neow/` | Neow (whale) starting bonus |
| `trials/` | Custom run modifiers |
| `daily/` | Daily climb modifiers |
| `helpers/` | Utilities (`FontHelper`, `ImageMaster`, `GameDictionary`, `CardHelper`, `Hitbox`, etc.) |
| `vfx/` | Visual effects (`AbstractGameEffect` + subclasses) |
| `screens/` | Full-screen UI (card library, stats, etc.) |
| `scenes/` | Scene management |

Key classes for AI development:
- **`AbstractDungeon`** — Global singleton holding `player`, `monsters`, `cardRandomRng`, `map`, `screen`, `actionManager`, etc. This is what `/state` serializes.
- **`AbstractCard`** — `type`, `cost`, `rarity`, `color`, `damage`, `block`, `magicNumber`, `upgraded`, `exhaust`, `ethereal`, `keywords`, etc. All ~300+ card classes extend this.
- **`AbstractPlayer`** — `hand`, `drawPile`, `discardPile`, `exhaustPile`, `energy`, `block`, `hp`, `powers`, `relics`, `masterDeck`, `orbs`
- **`AbstractMonster`** — `intent`, `intentDmg`, `intentMultiAmt`, `moveHistory`, `powers`, `currentBlock`, `currentHealth`
- **`AbstractRoom`** — `monsters`, `rewards`, `event`, `phase` (COMBAT/EVENT/COMPLETE), `rewards` list
- **`GameActionManager`** — Action queue with phases. `.actions` (current), `.preTurnActions`, `.cardQueue`

### Run History JSON Format (SpireLog)

Generated by `Metrics.java` → `gatherAllDataAndSave()` (line 186). Runtime accumulation via `MetricData.java` → `CardCrawlGame.metricData`. Output to `runs/<CHARACTER>/<timestamp>.run`.

**Card naming** — `AbstractCard.getMetricID()` (`AbstractCard.java` line 3002):
- Base: `"CardID"` (e.g. `"Strike_R"`)
- Upgraded once: `"CardID+"` (e.g. `"Strike_R+"`)
- Upgraded N times (Searing Blow etc.): `"CardID+N"` (e.g. `"SearingBlow+3"`)
- **No `+1` suffix** for the first upgrade — it's just `+`

**Egg relics** (Molten/Frozen/Toxic Egg): `onPreviewObtainCard()` fires during reward generation, calling `c.upgrade()` *before* the card enters `masterDeck`. Therefore `master_deck` and `card_choices.picked` already record the upgraded name — the reconstructor correctly does NOT need special egg handling.

**Key fields and their game-side sources:**

| JSON field | Source |
|---|---|
| `master_deck` | `AbstractDungeon.player.masterDeck.getCardIdsForMetrics()` |
| `relics` | `AbstractDungeon.player.getRelicNames()` |
| `current_hp_per_floor` | `CardCrawlGame.metricData.current_hp_per_floor` (populated at `AbstractDungeon.incrementFloorBasedMetrics()`) |
| `max_hp_per_floor` | `CardCrawlGame.metricData.max_hp_per_floor` |
| `gold_per_floor` | `CardCrawlGame.metricData.gold_per_floor` |
| `path_per_floor` | `CardCrawlGame.metricData.path_per_floor` (room type per floor, `?`=event, `$`=shop, boss=colored) |
| `path_taken` | `MapRoomNode` → `CardCrawlGame.metricData.path_taken` (map symbol per node clicked) |
| `card_choices` | `CardRewardScreen.recordMetrics()` / `RewardItem.recordCardSkipMetrics()` |
| `event_choices` | Event system → `CardCrawlGame.metricData.event_choices` |
| `campfire_choices` | `MetricData.addCampfireChoiceData(choiceKey, data)` |
| `relics_obtained` | `MetricData.addRelicObtainData(relic)` |
| `items_purchased` / `item_purchase_floors` | `MetricData.addShopPurchaseData(key)` |
| `items_purged` / `items_purged_floors` | `MetricData.addPurgedItem(key)` |
| `boss_relics` | `BossRelicSelectScreen` → `CardCrawlGame.metricData.boss_relics` |
| `neow_bonus` / `neow_cost` | `CardCrawlGame.metricData.neowBonus` / `neowCost` |

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
- **selectcard reference path**: `sts_ai_framework/llm_agent.py` imports from `../selectcard` (same-repo relative path).

### STSStateSaver — State System

- **SaveState** — root state object, serializes entire battle state via `jsonEncode()` / loads via `loadState()`. Contains PlayerState, monster states, screen states, action queue state.
- **StateFactories** — registry mapping class names to state constructors for deserialization.
- Monster/power/relic/action states organized by game zone (exordium, city, beyond, ending) and character class (ironclad, silent, defect, watcher).
- **fastobjects/** — optimized replacement objects that skip logging/rendering for simulation speed.

### LudicrousSpeed — Command Pattern

- **Command** (interface) — `execute()` + `encode()`. Implementations: CardCommand, PotionCommand, EndCommand, HandSelectCommand, GridSelectCommand, CardRewardSelectCommand, plus confirm variants.
- **CommandList.getAvailableCommands()** — enumerates all legal commands for current game state.
- **ActionSimulator.actionLoop()** — blocking loop that replaces `GameActionManager.update()`. Runs controller steps.
- **Controller** (interface) — `step()` called each frame while game waits for input; `isDone()` exits the simulation loop.
- Patches in `simulator/patches/` override game behavior for speed.

### scumthespire (BattleAiMod) — AI Tree Search

- **BattleAiController** (implements Controller) — manages tree search. Priority queue of TurnNodes, explores states breadth-first by value.
- **TurnNode** — represents a turn's worth of states. Comparable by value. Each TurnNode has a stack of StateNodes.
- **StateNode** — a single state snapshot after one command.
- **ValueFunctions** — scores game states to guide search.
- Character-specific card play heuristics: IronCladPlayOrder, DefectPlayOrder, SilentPlayOrder.

### Networking (scumthespire)

- **AiServer** (port 5125) — runs on headless game instance. Receives JSON run requests, runs BattleAiController, streams progress updates, returns command list.
- **AiClient** — runs on player's game instance. Serializes current state, sends to server, receives command list, executes via CommandRunnerController.
- Wire format: JSON with `type`, `commands`, `message` fields. `state` field uses diff encoding (`diffEncode()`) to pass state along command chain.
