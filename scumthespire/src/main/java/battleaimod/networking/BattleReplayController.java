package battleaimod.networking;

import battleaimod.search.SearchStateKey;
import ludicrousspeed.Controller;
import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.Command;
import savestate.SaveState;

import java.util.List;

/** Runs a returned command path from its original state on the game update thread. */
public class BattleReplayController implements Controller {
    private final List<Command> commands;
    private int commandIndex;
    private boolean done;
    private String finalStateKey;
    private com.google.gson.JsonObject finalProjection;
    private com.google.gson.JsonObject finalSnapshot;
    private String error;

    public BattleReplayController(SaveState startState, List<Command> commands) {
        this.commands = commands;
        // Construction happens on the game update thread. Bootstrap here: a fresh
        // server starts in EmptyRoom, where LudicrousSpeed deliberately never steps
        // a controller. Deferring loadState to step() deadlocks the first REPLAY.
        try {
            LudicrousSpeedMod.mustRestart = false;
            startState.loadState();
        } catch (RuntimeException e) {
            error = e.toString();
            done = true;
        }
    }

    @Override
    public void step() {
        if (done) {
            return;
        }
        try {
            if (commandIndex < commands.size()) {
                Command command = commands.get(commandIndex++);
                if (command != null) {
                    command.execute();
                }
                return;
            }
            SaveState finalState = new SaveState();
            finalStateKey = SearchStateKey.fromSaveState(finalState).toString();
            finalSnapshot = finalState.jsonEncode();
            finalProjection = BattleStateProjection.fromSnapshot(finalSnapshot);
            if (LudicrousSpeedMod.mustRestart) {
                error = "mustRestart was requested during replay";
            }
            done = true;
        } catch (RuntimeException e) {
            error = e.toString();
            done = true;
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }

    public String finalStateKey() {
        return finalStateKey;
    }

    public com.google.gson.JsonObject finalProjection() { return finalProjection; }
    public com.google.gson.JsonObject finalSnapshot() { return finalSnapshot; }

    public String error() {
        return error;
    }

    public int commandsExecuted() {
        return commandIndex;
    }
}
