# Battle AI Remaining Risks

## Scope

This audit covers the remaining unfinished and risky areas in the three combat automation mods:

- `STSStateSaver`
- `LudicrousSpeed`
- `scumthespire`

These mods should only serve automatic combat. Non-combat decisions such as combat rewards, map navigation, events, shops, campfires, and similar run-flow choices are expected to be handled by other modules.

Combat-time selection screens remain in scope because cards and actions can open them during a fight:

- `HAND_SELECT`
- `GRID`
- `CARD_REWARD`

## High-Priority Unfinished Work

### `SaveState` JSON loading is incomplete

The JSON constructors for `SaveState` do not fully restore combat selection screens and some action-history fields. In particular, screen states such as hand selection, grid selection, and card reward selection are set to `null` in JSON-loading paths, while runtime-created `SaveState` instances can capture them.

Risk: a state loaded from JSON may not be equivalent to a state captured in memory, especially when the battle is paused on a combat selection screen or mid-action.

### Server startup hardcodes Ironclad

The fast server startup path creates an `Exordium` dungeon with `AbstractPlayer.PlayerClass.IRONCLAD`. The existing comment says this needs to be the actual character class or bad things happen.

Risk: Silent, Defect, or Watcher states can be loaded into a server environment initialized with the wrong character assumptions, causing bad card pools, relic behavior, stance/orb behavior, or state restoration errors.

### `UseCardActionState` loses the action target

`UseCardActionState.loadAction()` recreates `UpdateOnlyUseCardAction` with a `null` target.

Risk: actions whose effects depend on the original target can replay inaccurately. This can make simulated battle states diverge from the real client state.

### Some action states are too shallow

Several action state implementations are explicitly marked as simplified, including `NewQueueCardActionState` and `ShuffleActionState`.

Risk: if the original action carries important parameters or trigger context, restoring only a generic action can change the simulated result.

## High-Risk Failure Points

### `SaveState.diff()` is the key client/server consistency check

Commands sent from the server can include expected diff state files. Before executing a command, the client compares its actual state with the expected diff via `SaveState.diff()`.

Risk: if any important state is not serialized or restored correctly, the client can print `PANIC PANIC PANIC`, set `LudicrousSpeedMod.mustRestart`, and stop trusting the server path.

### Path updates assume prefix compatibility

`CommandRunnerController.updateBestPath()` assumes a newly received command path is compatible with the path already being executed. It advances through the new path by consuming the same number of commands already consumed from the old path.

Risk: if the server sends a path that diverges before the already-executed prefix, or sends a shorter path, the client can skip to the wrong command, hit an iterator error, or execute a command that does not match the current state.

### Command deduplication can hide legal branches

`CommandList` deduplicates playable cards by `cardID` plus upgrade status.

Risk: two cards with the same id and upgrade status but different cost, modifiers, UUID-sensitive behavior, retain/exhaust state, or temporary status may not both be explored. The search can miss a legal or optimal branch.

### Turn depth limit can truncate valid long turns

`StateNode` forces the command list down to `EndCommand` after a turn depth greater than 50.

Risk: this prevents infinite loops, but it can also cut off valid long-combo or infinite-style turns before the best line is found.

### Missing cards can cause `NullPointerException`

`CardState.getFreshCard()` prints a message when `CardLibrary.getCard(key)` returns `null`, then immediately calls `card.makeCopy()`.

Risk: missing card ids, mod card mismatches, special temporary cards, or server/client mod differences can crash state restoration with a `NullPointerException`.

### Socket disconnect recovery is incomplete

The main AI server and status/control threads use raw sockets and several long-running loops. Some disconnect paths print stack traces, clear a singleton, or let the thread exit.

Risk: a client disconnect, port conflict, server crash, or stale socket can leave the UI in a misleading state or require a full restart instead of a clean reconnect.

## Medium-Priority Risks

### Heavy reliance on `ReflectionHacks`

The mods read and write many private fields from Slay the Spire, BaseMod, and UI/action classes.

Risk: private fields are not stable APIs. Game or dependency version changes can break state capture, command availability checks, or UI restoration.

### Missing powers, orbs, or card modifiers may be ignored

Some state classes support flags that return `null` when an unknown power, orb, or card modifier is encountered.

Risk: ignoring an unknown state avoids an immediate crash but can silently remove combat mechanics from the simulation. The AI may search from a state that is easier, harder, or simply different from the real fight.

### `FightPredictor` runs asynchronously and swallows some errors

`BattleAiController` optionally starts prediction work in a background thread. Some compatibility errors are caught without detailed reporting.

Risk: prediction failures may be invisible. If the background work touches game objects that are not thread-safe, failures may be intermittent and hard to reproduce.

### Large command responses fall back to files

When socket messages are too large, the server writes command data or state diffs to files and sends paths back to the client.

Risk: this makes correctness depend on both socket messages and filesystem paths. Incorrect working directories, failed writes, stale files, or overwritten `savestates/*.txt` files can break replay.

### Server readiness is represented too coarsely

The UI mostly treats the server as ready or not ready based on ping responses and boolean state.

Risk: port conflicts, startup failure, searching, disconnected, crashed, and restarting states can look similar. This makes operational failures harder to diagnose.

## Low-Priority Maintenance Issues

### Repeated parsing and performance TODOs

Many monster state classes contain comments such as `TODO don't parse twice`. These are mostly performance or cleanup issues rather than immediate correctness blockers.

Risk: unnecessary parsing adds overhead during deep search, but it is unlikely to be the first source of incorrect play.

### Logging noise is high

The code uses many direct `System.err.println(...)` and `printStackTrace()` calls. Normal debug messages, expected fallbacks, and real errors are mixed together.

Risk: important failures can be buried in routine output, especially when the client, server, and background threads print at the same time.

### `GridSelectConfrimCommand` is misspelled

The class name uses `Confrim` instead of `Confirm`.

Risk: this does not appear to break current behavior, but it makes searching, maintenance, and future refactors more error-prone.

## Verification Notes

This document is an audit only. It does not change Java code, dependencies, lockfiles, generated files, or runtime behavior.

Suggested follow-up checks:

- Confirm automatic battle still treats `HAND_SELECT`, `GRID`, and combat `CARD_REWARD` as in-scope.
- Confirm non-combat decisions remain outside these three mods.
- Compile in dependency order before implementing code changes: `STSStateSaver`, `LudicrousSpeed`, then `scumthespire`.
