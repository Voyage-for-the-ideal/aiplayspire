package ludicrousspeed.simulator.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import ludicrousspeed.LudicrousSpeedMod;

public class CardCommand implements Command {
    public final int cardIndex;
    public final int monsterIndex;
    public final String displayString;

    private String diffStateString = null;

    public CardCommand(int cardIndex, int monsterIndex, String displayString) {
        this.cardIndex = cardIndex;
        this.monsterIndex = monsterIndex;
        this.displayString = displayString;
    }

    public CardCommand(int cardIndex, String displayString) {
        this.cardIndex = cardIndex;
        this.monsterIndex = -1;
        this.displayString = displayString;
    }

    public CardCommand(String jsonString) {
        JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

        this.cardIndex = parsed.get("card_index").getAsInt();
        this.monsterIndex = parsed.get("monster_index").getAsInt();
        this.displayString = parsed.get("display_string").getAsString();
    }

    public CardCommand(String jsonString, String diffStateString) {
        JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

        this.cardIndex = parsed.get("card_index").getAsInt();
        this.monsterIndex = parsed.get("monster_index").getAsInt();
        this.displayString = parsed.get("display_string").getAsString();
        this.diffStateString = diffStateString;
    }

    @Override
    public void execute() {
        if (!StateDiffChecker.check(diffStateString, this.toString())) {
            return;
        }

        AbstractDungeon.player.hand.refreshHandLayout();
        AbstractCard card = AbstractDungeon.player.hand.group.get(cardIndex);
        AbstractMonster monster = null;

        if (monsterIndex != -1) {
            monster = AbstractDungeon.getMonsters().monsters.get(monsterIndex);

            // Match vanilla playCard: Surrounded only flips horizontal, it
            // does not re-apply powers to all monsters
            if (AbstractDungeon.player.hasPower("Surrounded")) {
                AbstractDungeon.player.flipHorizontal = monster.drawX < AbstractDungeon.player.drawX;
            }
        }

        AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, monster));

        if (!LudicrousSpeedMod.plaidMode) {
            AbstractDungeon.actionManager.addToBottom(new WaitAction(.2F));
        } else {
            AbstractDungeon.actionManager.phase = GameActionManager.Phase.EXECUTING_ACTIONS;
        }
    }

    @Override
    public String toString() {
        return displayString + monsterIndex;
    }

    @Override
    public String encode() {
        JsonObject cardCommandJson = new JsonObject();

        cardCommandJson.addProperty("type", "CARD");

        cardCommandJson.addProperty("card_index", cardIndex);
        cardCommandJson.addProperty("monster_index", monsterIndex);
        cardCommandJson.addProperty("display_string", displayString);

        return cardCommandJson.toString();
    }
}
