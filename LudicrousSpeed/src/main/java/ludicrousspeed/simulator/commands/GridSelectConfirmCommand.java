package ludicrousspeed.simulator.commands;

import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;

public class GridSelectConfirmCommand implements Command {
    public static final GridSelectConfirmCommand INSTANCE = new GridSelectConfirmCommand();

    private final String diffStateString;

    private GridSelectConfirmCommand() {
        this.diffStateString = null;
    }

    public GridSelectConfirmCommand(String diffStateString) {
        this.diffStateString = diffStateString;
    }

    @Override
    public void execute() {
        if (!StateDiffChecker.check(diffStateString, this.toString())) {
            return;
        }

        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        screen.confirmButton.hb.clicked = true;
        screen.update();

        if (AbstractDungeon.isScreenUp) {
            AbstractDungeon.closeCurrentScreen();
        }
    }

    @Override
    public String encode() {
        JsonObject cardCommandJson = new JsonObject();

        cardCommandJson.addProperty("type", "GRID_SELECT_CONFIRM");

        return cardCommandJson.toString();
    }

    @Override
    public String toString() {
        return "GridConfirm";
    }
}
