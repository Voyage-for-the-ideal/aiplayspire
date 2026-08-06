package ludicrousspeed.simulator.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import ludicrousspeed.LudicrousSpeedMod;

public class PotionCommand implements Command {
    private final int potionIndex;
    private final int monsterIndex;

    private String diffStateString = null;

    public PotionCommand(int potionIndex, int monsterIndex) {
        this.potionIndex = potionIndex;
        this.monsterIndex = monsterIndex;
    }

    public PotionCommand(int potionIndex) {
        this(potionIndex, -1);
    }

    public PotionCommand(String jsonString) {
        JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

        this.potionIndex = parsed.get("potion_index").getAsInt();
        this.monsterIndex = parsed.get("monster_index").getAsInt();
    }

    public PotionCommand(String jsonString, String diffStateString) {
        JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

        this.potionIndex = parsed.get("potion_index").getAsInt();
        this.monsterIndex = parsed.get("monster_index").getAsInt();

        this.diffStateString = diffStateString;
    }

    @Override
    public void execute() {
        if (!StateDiffChecker.check(diffStateString, this.toString())) {
            return;
        }

        AbstractPotion potion = AbstractDungeon.player.potions.get(potionIndex);
        // Match vanilla PotionPopUp: targetless potions get a null target
        AbstractCreature target = null;

        if (monsterIndex != -1) {
            target = AbstractDungeon.getMonsters().monsters.get(monsterIndex);
        }

        potion.use(target);
        AbstractDungeon.player.relics.forEach(relic -> relic.onUsePotion());

        AbstractDungeon.topPanel.destroyPotion(potionIndex);

        if (!LudicrousSpeedMod.plaidMode) {
            AbstractDungeon.actionManager.addToBottom(new WaitAction(.2F));
        } else {
            // Same fast-path style as CardCommand
            AbstractDungeon.actionManager.phase = GameActionManager.Phase.EXECUTING_ACTIONS;
        }
    }

    @Override
    public String toString() {
        return "Potion " + potionIndex + " " + monsterIndex;
    }

    @Override
    public String encode() {
        JsonObject cardCommandJson = new JsonObject();

        cardCommandJson.addProperty("type", "POTION");

        cardCommandJson.addProperty("potion_index", potionIndex);
        cardCommandJson.addProperty("monster_index", monsterIndex);

        return cardCommandJson.toString();
    }
}
