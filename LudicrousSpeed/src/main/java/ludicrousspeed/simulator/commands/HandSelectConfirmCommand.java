package ludicrousspeed.simulator.commands;

import basemod.ReflectionHacks;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;

public class HandSelectConfirmCommand implements Command {
    private final String diffStateString;
    public static final HandSelectConfirmCommand INSTANCE = new HandSelectConfirmCommand();

    private HandSelectConfirmCommand() {
        this.diffStateString = null;
    }

    public HandSelectConfirmCommand(String diffString) {
        this.diffStateString = diffString;
    }

    @Override
    public void execute() {
        if (!StateDiffChecker.check(diffStateString, this.toString())) {
            return;
        }

        HandCardSelectScreen screen = AbstractDungeon.handCardSelectScreen;

        // Redundant safety net: prep() and HandSelectScreenState already
        // point the screen's hand at player.hand
        ReflectionHacks
                .setPrivate(AbstractDungeon.handCardSelectScreen, HandCardSelectScreen.class, "hand", AbstractDungeon.player.hand);

        screen.button.hb.clicked = true;
        screen.update();
    }

    @Override
    public String encode() {
        JsonObject cardCommandJson = new JsonObject();

        cardCommandJson.addProperty("type", "HAND_SELECT_CONFIRM");

        return cardCommandJson.toString();
    }
}
