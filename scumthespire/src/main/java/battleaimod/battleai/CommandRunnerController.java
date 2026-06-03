package battleaimod.battleai;

import battleaimod.BattleAiMod;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.Controller;
import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.Command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class CommandRunnerController implements Controller {
    public boolean isDone = false;

    public List<Command> bestPath;
    private List<Command> queuedPath = null;

    public Iterator<Command> bestPathRunner;
    private int consumedCommandCount = 0;

    boolean isComplete;
    boolean wouldComplete = true;

    public HashMap<String, Long> runTimes;

    public CommandRunnerController(List<Command> commands, boolean isComplete) {
        runTimes = new HashMap<>();
        this.isComplete = isComplete;
        bestPath = commands;
        bestPathRunner = commands.iterator();
    }

    public void updateBestPath(List<Command> commands, boolean wouldComplete) {
        queuedPath = commands;
        this.wouldComplete = wouldComplete;
    }

    public void step() {
        if (isDone) {
            return;
        }

        if (queuedPath != null && !applyQueuedPath()) {
            return;
        }

        boolean foundCommand = false;
        while ((!isComplete || bestPathRunner.hasNext()) && !foundCommand) {
            if (!bestPathRunner.hasNext()) {
                if (queuedPath != null) {
                    if (!applyQueuedPath()) {
                        return;
                    }
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            Command command = bestPathRunner.next();
            consumedCommandCount++;
            if (command != null) {
                foundCommand = true;
                command.execute();
            } else {
                foundCommand = true;
            }
        }
        if (!BattleAiMod.isServer) {
            AbstractDungeon.player.hand.refreshHandLayout();
        }

        if (!bestPathRunner.hasNext()) {
            if (isComplete) {
                isDone = true;
            }
        }
    }

    public boolean isDone() {
        return isDone;
    }

    private boolean applyQueuedPath() {
        if (!isPathCompatible(queuedPath)) {
            queuedPath = null;
            isDone = true;
            LudicrousSpeedMod.mustRestart = true;
            return false;
        }

        Iterator<Command> newPath = queuedPath.iterator();
        for (int i = 0; i < consumedCommandCount; i++) {
            newPath.next();
        }

        bestPath = queuedPath;
        queuedPath = null;
        bestPathRunner = newPath;
        isComplete = wouldComplete;
        return true;
    }

    private boolean isPathCompatible(List<Command> commands) {
        if (commands == null) {
            System.err.println("PANIC PANIC PANIC updated command path was null");
            return false;
        }

        if (bestPath.size() < consumedCommandCount) {
            System.err.println("PANIC PANIC PANIC consumed more commands than current path contains");
            return false;
        }

        if (commands.size() < consumedCommandCount) {
            System.err.println(String.format(
                    "PANIC PANIC PANIC updated command path too short: consumed=%d newSize=%d",
                    consumedCommandCount,
                    commands.size()));
            return false;
        }

        for (int i = 0; i < consumedCommandCount; i++) {
            Command oldCommand = bestPath.get(i);
            Command newCommand = commands.get(i);
            if (!commandsEquivalent(oldCommand, newCommand)) {
                System.err.println(String.format(
                        "PANIC PANIC PANIC updated command path diverged at %d: old=%s new=%s",
                        i,
                        commandKey(oldCommand),
                        commandKey(newCommand)));
                return false;
            }
        }

        return true;
    }

    private boolean commandsEquivalent(Command oldCommand, Command newCommand) {
        if (oldCommand == null || newCommand == null) {
            return oldCommand == newCommand;
        }

        return commandKey(oldCommand).equals(commandKey(newCommand));
    }

    private String commandKey(Command command) {
        if (command == null) {
            return "null";
        }

        return command.encode();
    }
}
