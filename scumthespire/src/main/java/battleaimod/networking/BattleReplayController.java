package battleaimod.networking;

import battleaimod.search.SearchStateKey;
import ludicrousspeed.Controller;
import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.Command;
import savestate.SaveState;

import java.util.List;

/** Runs a returned command path from its original state on the game update thread. */
public class BattleReplayController implements Controller {
    private final SaveState startState;
    private final List<Command> commands;
    private int commandIndex;
    private boolean loaded;
    private boolean done;
    private String finalStateKey;
    private String error;

    public BattleReplayController(SaveState startState, List<Command> commands) {
        this.startState = startState;
        this.commands = commands;
    }

    @Override
    public void step() {
        if (done) {
            return;
        }
        try {
            if (!loaded) {
                LudicrousSpeedMod.mustRestart = false;
                startState.loadState();
                loaded = true;
                return;
            }
            if (commandIndex < commands.size()) {
                Command command = commands.get(commandIndex++);
                if (command != null) {
                    command.execute();
                }
                return;
            }
            finalStateKey = SearchStateKey.fromSaveState(new SaveState()).toString();
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

    public String error() {
        return error;
    }

    public int commandsExecuted() {
        return commandIndex;
    }
}
