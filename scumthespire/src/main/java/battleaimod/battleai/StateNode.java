package battleaimod.battleai;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import ludicrousspeed.simulator.commands.CardCommand;
import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.EndCommand;
import savestate.SaveState;

import java.util.List;

import static battleaimod.ValueFunctions.getStateScore;

public class StateNode {
    private final BattleAiController controller;

    public SaveState saveState;
    private boolean initialized = false;
    private boolean isDone = false;

    // The list of lastCommands obtained by iterating up the parents will be the backwards sequence
    // of commands to get to this state.
    public final StateNode parent;
    public final Command lastCommand;

    // List of commands available from the current state, ordered by guessed result from best to
    // worst.
    public List<Command> commands;
    private int commandIndex = -1;

    // The number of actions taken in this turn, used to limit the total number of actions to
    // prevent infinite loops.
    int turnDepth;

    public StateNode(StateNode parent, Command lastCommand, BattleAiController controller) {
        this.parent = parent;
        this.lastCommand = lastCommand;
        this.controller = controller;
    }

    /**
     * Performs the next step and returns true iff the parent should load state
     */
    public Command step() {
        if (saveState == null) {
            saveState = controller.captureState();
        }

        if (parent == null || parent.saveState.turn < saveState.turn) {
            turnDepth = 0;
        } else {
            turnDepth = parent.turnDepth + 1;
        }

        if (commands == null) {
            commands = battleaimod.strategy.CommandOrdering.enumerate(saveState);
            if (commands.isEmpty() && AbstractDungeon.isScreenUp) logEmptyCommandList();
        }

        if (turnDepth > 50) {
            boolean hasEnd = false;
            for (Command command : commands) {
                if (command instanceof EndCommand) {
                    hasEnd = true;
                    break;
                }
            }

            if (hasEnd) {
                commands.clear();
                commands.add(new EndCommand());
            }
        }

        if (!initialized) {
            initialized = true;

            if (AbstractDungeon.player.isDead || AbstractDungeon.player.isDying || saveState
                    .getPlayerHealth() < 1) {
                if (controller.deathNode == null ||
                        (controller.deathNode != null && controller.deathNode.saveState.turn < saveState.turn)) {
                    controller.deathNode = this;
                }

                isDone = true;
                return null;
            }

            if (isBattleOver()) {
                boolean isBestWin = controller.bestEnd == null ||
                        getStateScore(this) > getStateScore(controller.bestEnd);
                if (isBestWin) {
                    controller.bestEnd = this;
                }

                isDone = true;
                return null;
            } else {
                commandIndex = 0;
            }
        }

        if (commands.size() == 0) {
            isDone = true;
            return null;
        }


        Command toExecute = commands.get(commandIndex);
        commandIndex++;
        isDone = commandIndex >= commands.size();

        return toExecute;
    }

    private boolean isBattleOver() {
        return AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead();
    }

    private void logEmptyCommandList() {
        String screenName = String.valueOf(AbstractDungeon.screen);
        String actionName = AbstractDungeon.actionManager.currentAction == null
                ? "none"
                : AbstractDungeon.actionManager.currentAction.getClass().getName();
        int gridSize = -1;
        int selectableCount = -1;
        int selectedCount = -1;
        String anyNumber = "n/a";
        String forClarity = "n/a";

        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID
                && AbstractDungeon.gridSelectScreen != null) {
            GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
            gridSize = screen.targetGroup == null ? 0 : screen.targetGroup.size();
            selectableCount = 0;
            if (screen.targetGroup != null) {
                for (AbstractCard card : screen.targetGroup.group) {
                    if (!card.isGlowing) {
                        selectableCount++;
                    }
                }
            }
            selectedCount = screen.selectedCards == null ? 0 : screen.selectedCards.size();
            anyNumber = Boolean.toString(screen.anyNumber);
            forClarity = Boolean.toString(screen.forClarity);
        }

        System.err.println("BattleAiMod: EMPTY_COMMAND_LIST"
                + " screen=" + screenName
                + " action=" + actionName
                + " grid_size=" + gridSize
                + " selectable_count=" + selectableCount
                + " selected_count=" + selectedCount
                + " any_number=" + anyNumber
                + " for_clarity=" + forClarity
                + " generated_commands=0");
    }

    public boolean isDone() {
        return isDone;
    }

    public static int getPlayerDamage(StateNode node) {
        return node.controller.startingHealth - node.saveState.getPlayerHealth();
    }
}
