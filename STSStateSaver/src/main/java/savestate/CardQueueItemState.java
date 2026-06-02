package savestate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import savestate.actions.ActionState;

import java.util.Optional;

public class CardQueueItemState {
    public final CardState card;
    public final Optional<Integer> monsterIndex;
    public final int energyOnUse;
    public final boolean ignoreEnergyTotal;
    public final boolean autoplayCard;
    public final boolean randomTarget;
    public final boolean isEndTurnAutoPlay;

    public CardQueueItemState(CardQueueItem cardQueueItem) {
        this.card = cardQueueItem.card == null ? null : new CardState(cardQueueItem.card);
        this.monsterIndex = cardQueueItem.monster == null ? Optional.empty() : Optional
                .of(ActionState.indexForCreature(cardQueueItem.monster));
        this.energyOnUse = cardQueueItem.energyOnUse;
        this.ignoreEnergyTotal = cardQueueItem.ignoreEnergyTotal;
        this.autoplayCard = cardQueueItem.autoplayCard;
        this.randomTarget = cardQueueItem.randomTarget;
        this.isEndTurnAutoPlay = cardQueueItem.isEndTurnAutoPlay;
    }

    public CardQueueItemState(JsonObject json) {
        JsonElement cardElement = json.get("card");
        this.card = cardElement == null || cardElement.isJsonNull() ? null : CardState
                .forJson(cardElement.getAsJsonObject());

        JsonElement monsterIndexElement = json.get("monster_index");
        this.monsterIndex = monsterIndexElement == null || monsterIndexElement
                .isJsonNull() ? Optional.empty() : Optional.of(monsterIndexElement.getAsInt());

        this.energyOnUse = json.get("energy_on_use").getAsInt();
        this.ignoreEnergyTotal = json.get("ignore_energy_total").getAsBoolean();
        this.autoplayCard = json.get("autoplay_card").getAsBoolean();
        this.randomTarget = json.get("random_target").getAsBoolean();
        this.isEndTurnAutoPlay = json.has("is_end_turn_auto_play") && json
                .get("is_end_turn_auto_play").getAsBoolean();
    }

    public CardQueueItem loadItem() {
        CardQueueItem result = new CardQueueItem(card == null ? null : card
                .loadCard(), (AbstractMonster) (monsterIndex
                .isPresent() ? ActionState
                .creatureForIndex(monsterIndex
                        .get()) : null), energyOnUse, ignoreEnergyTotal, autoplayCard);
        result.randomTarget = randomTarget;
        return result;
    }

    public JsonObject jsonEncode() {
        JsonObject json = new JsonObject();

        json.add("card", card == null ? null : card.jsonEncode());
        json.addProperty("monster_index", monsterIndex.isPresent() ? monsterIndex.get() : null);
        json.addProperty("energy_on_use", energyOnUse);
        json.addProperty("ignore_energy_total", ignoreEnergyTotal);
        json.addProperty("autoplay_card", autoplayCard);
        json.addProperty("random_target", randomTarget);
        json.addProperty("is_end_turn_auto_play", isEndTurnAutoPlay);

        return json;
    }
}
