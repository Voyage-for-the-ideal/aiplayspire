package ludicrousspeed.simulator.commands;

import basemod.ReflectionHacks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;

public class HandSelectCommand implements Command {
    private final int cardIndex;
    public final String diffStateString;

    public HandSelectCommand(int cardIndex) {
        this.cardIndex = cardIndex;
        this.diffStateString = null;
    }

    public HandSelectCommand(String jsonString) {
        JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

        this.diffStateString = null;
        this.cardIndex = parsed.get("card_index").getAsInt();
    }

    public HandSelectCommand(String jsonString, String displayString) {
        JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

        this.diffStateString = displayString;
        this.cardIndex = parsed.get("card_index").getAsInt();
    }

    @Override
    public void execute() {
        if (!StateDiffChecker.check(diffStateString, this.toString())) {
            return;
        }

        AbstractDungeon.handCardSelectScreen.hoveredCard = AbstractDungeon.player.hand.group
                .get(cardIndex);

        // selectHoveredCard is private in this STS version; if the signature
        // changes on a game update the reflection silently fails
        ReflectionHacks.privateMethod(HandCardSelectScreen.class, "selectHoveredCard")
                       .invoke(AbstractDungeon.handCardSelectScreen);
    }

    @Override
    public String encode() {
        JsonObject cardCommandJson = new JsonObject();

        cardCommandJson.addProperty("type", "HAND_SELECT");
        cardCommandJson.addProperty("card_index", cardIndex);


        return cardCommandJson.toString();
    }

    @Override
    public String toString() {
        return "HandSelectCommand" + cardIndex;
    }
}
