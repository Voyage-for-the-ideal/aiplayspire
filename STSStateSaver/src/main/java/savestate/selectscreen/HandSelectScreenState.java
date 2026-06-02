package savestate.selectscreen;

import basemod.ReflectionHacks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import savestate.CardQueueItemState;
import savestate.CardState;
import savestate.PlayerState;
import savestate.StateJsonHelper;
import savestate.actions.ActionState;
import savestate.actions.CurrentActionState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class HandSelectScreenState {
    private final int numCardsToSelect;

    private final CardState[] selectedCards;
    private final ArrayList<ActionState> actionQueue;
    private final ArrayList<CardQueueItemState> cardQueueState;

    private final boolean wereCardsRetrieved;
    private final boolean canPickZero;
    private final boolean upTo;
    private final boolean anyNumber;
    private final boolean forTransform;
    private final boolean forUpgrade;
    private final int numSelected;
    private final CurrentActionState currentActionState;
    private final boolean isDisabled;

    public HandSelectScreenState() {
        selectedCards = PlayerState
                .toCardStateArray(AbstractDungeon.handCardSelectScreen.selectedCards.group);

        this.numCardsToSelect = AbstractDungeon.handCardSelectScreen.numCardsToSelect;
        this.wereCardsRetrieved = AbstractDungeon.handCardSelectScreen.wereCardsRetrieved;
        this.canPickZero = AbstractDungeon.handCardSelectScreen.canPickZero;
        this.upTo = AbstractDungeon.handCardSelectScreen.upTo;
        this.anyNumber = ReflectionHacks
                .getPrivate(AbstractDungeon.handCardSelectScreen, HandCardSelectScreen.class, "anyNumber");
        this.forTransform = ReflectionHacks
                .getPrivate(AbstractDungeon.handCardSelectScreen, HandCardSelectScreen.class, "forTransform");
        this.forUpgrade = ReflectionHacks
                .getPrivate(AbstractDungeon.handCardSelectScreen, HandCardSelectScreen.class, "forUpgrade");
        this.numSelected = AbstractDungeon.handCardSelectScreen.numSelected;

        AbstractGameAction currentAction = AbstractDungeon.actionManager.currentAction;

        isDisabled = AbstractDungeon.handCardSelectScreen.button.isDisabled;

        if (currentAction != null) {
            currentActionState = CurrentActionState.getCurrentActionState();
            actionQueue = ActionState.getActionQueueState();

            cardQueueState = new ArrayList<>();
            AbstractDungeon.actionManager.cardQueue.forEach(cardQueueItem -> cardQueueState
                    .add(new CardQueueItemState(cardQueueItem)));

            if (actionQueue.isEmpty()) {
                throw new IllegalStateException("The action queue shouldn't be empty in the middle of a selection screen");
            }
        } else {
            currentActionState = null;
            actionQueue = null;
            cardQueueState = null;
        }

    }

    public HandSelectScreenState(JsonObject json) {
        this.numCardsToSelect = json.get("num_cards_to_select").getAsInt();
        this.selectedCards = cardStateArrayFromJson(json.get("selected_cards").getAsJsonArray());
        this.wereCardsRetrieved = json.get("were_cards_retrieved").getAsBoolean();
        this.canPickZero = json.get("can_pick_zero").getAsBoolean();
        this.upTo = json.get("up_to").getAsBoolean();
        this.anyNumber = json.get("any_number").getAsBoolean();
        this.forTransform = json.get("for_transform").getAsBoolean();
        this.forUpgrade = json.get("for_upgrade").getAsBoolean();
        this.numSelected = json.get("num_selected").getAsInt();
        this.isDisabled = json.get("is_disabled").getAsBoolean();

        this.currentActionState = StateJsonHelper
                .currentActionStateFromJson(json.get("current_action_state"));
        this.actionQueue = actionQueueFromJson(json.get("action_queue"));
        this.cardQueueState = cardQueueFromJson(json.get("card_queue_state"));
    }

    public void loadHandSelectScreenState() {
        AbstractDungeon.handCardSelectScreen.button.isDisabled = isDisabled;

        AbstractDungeon.handCardSelectScreen.selectedCards.group = Arrays.stream(selectedCards)
                                                                         .map(CardState::loadCard)
                                                                         .collect(Collectors
                                                                                 .toCollection(ArrayList::new));

        AbstractDungeon.handCardSelectScreen.numSelected = numSelected;
        AbstractDungeon.handCardSelectScreen.numCardsToSelect = numCardsToSelect;
        AbstractDungeon.handCardSelectScreen.wereCardsRetrieved = wereCardsRetrieved;
        AbstractDungeon.handCardSelectScreen.canPickZero = canPickZero;
        AbstractDungeon.handCardSelectScreen.upTo = upTo;

        ReflectionHacks
                .setPrivate(AbstractDungeon.handCardSelectScreen, HandCardSelectScreen.class, "anyNumber", anyNumber);
        ReflectionHacks
                .setPrivate(AbstractDungeon.handCardSelectScreen, HandCardSelectScreen.class, "forTransform", forTransform);
        ReflectionHacks
                .setPrivate(AbstractDungeon.handCardSelectScreen, HandCardSelectScreen.class, "forUpgrade", forUpgrade);
        ReflectionHacks
                .setPrivate(AbstractDungeon.handCardSelectScreen, HandCardSelectScreen.class, "hand", AbstractDungeon.player.hand);
        AbstractDungeon.handCardSelectScreen.numSelected = numSelected;

        if (currentActionState != null) {
            AbstractDungeon.actionManager.actions.clear();
            actionQueue.forEach(action -> AbstractDungeon.actionManager.actions.add(action
                    .loadAction()));

            AbstractDungeon.actionManager.cardQueue.clear();
            cardQueueState.forEach(cardQueueItemState -> AbstractDungeon.actionManager.cardQueue
                    .add(cardQueueItemState.loadItem()));

            AbstractDungeon.actionManager.currentAction = currentActionState.loadCurrentAction();
            AbstractDungeon.actionManager.phase = GameActionManager.Phase.EXECUTING_ACTIONS;


            if (AbstractDungeon.actionManager.actions.isEmpty()) {
                throw new IllegalStateException("this too shouldn't happen");
            }

        }
    }

    public String encode() {
        return jsonEncode().toString();
    }

    public JsonObject jsonEncode() {
        JsonObject json = new JsonObject();

        json.addProperty("num_cards_to_select", numCardsToSelect);
        json.add("selected_cards", cardStateArrayToJson(selectedCards));
        json.addProperty("were_cards_retrieved", wereCardsRetrieved);
        json.addProperty("can_pick_zero", canPickZero);
        json.addProperty("up_to", upTo);
        json.addProperty("any_number", anyNumber);
        json.addProperty("for_transform", forTransform);
        json.addProperty("for_upgrade", forUpgrade);
        json.addProperty("num_selected", numSelected);
        json.addProperty("is_disabled", isDisabled);
        json.add("current_action_state", currentActionState == null ? null : StateJsonHelper
                .currentActionStateToJson(currentActionState));
        json.add("action_queue", actionQueueToJson(actionQueue));
        json.add("card_queue_state", cardQueueToJson(cardQueueState));

        return json;
    }

    private static JsonArray cardStateArrayToJson(CardState[] cards) {
        JsonArray json = new JsonArray();
        for (CardState card : cards) {
            json.add(card.jsonEncode());
        }
        return json;
    }

    private static CardState[] cardStateArrayFromJson(JsonArray json) {
        CardState[] cards = new CardState[json.size()];
        for (int i = 0; i < json.size(); i++) {
            cards[i] = CardState.forJson(json.get(i).getAsJsonObject());
        }
        return cards;
    }

    private static JsonArray actionQueueToJson(ArrayList<ActionState> actionQueue) {
        if (actionQueue == null) {
            return null;
        }

        JsonArray json = new JsonArray();
        for (ActionState actionState : actionQueue) {
            json.add(StateJsonHelper.actionStateToJson(actionState));
        }
        return json;
    }

    private static ArrayList<ActionState> actionQueueFromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        ArrayList<ActionState> actionQueue = new ArrayList<>();
        for (JsonElement actionJson : json.getAsJsonArray()) {
            actionQueue.add(StateJsonHelper.actionStateFromJson(actionJson));
        }
        return actionQueue;
    }

    private static JsonArray cardQueueToJson(ArrayList<CardQueueItemState> cardQueueState) {
        if (cardQueueState == null) {
            return null;
        }

        JsonArray json = new JsonArray();
        for (CardQueueItemState cardQueueItemState : cardQueueState) {
            json.add(cardQueueItemState.jsonEncode());
        }
        return json;
    }

    private static ArrayList<CardQueueItemState> cardQueueFromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        ArrayList<CardQueueItemState> cardQueueState = new ArrayList<>();
        for (JsonElement cardQueueItemJson : json.getAsJsonArray()) {
            cardQueueState.add(new CardQueueItemState(cardQueueItemJson.getAsJsonObject()));
        }
        return cardQueueState;
    }
}
