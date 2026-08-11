package savestate.selectscreen;

import basemod.ReflectionHacks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import savestate.CardQueueItemState;
import savestate.SaveState;
import savestate.StateJsonHelper;
import savestate.actions.ActionState;
import savestate.actions.CurrentActionState;

import java.util.ArrayList;

public class GridCardSelectScreenState {
    private final ArrayList<SaveState.CardStateContainer> selectedCards;

    private final CurrentActionState currentActionState;
    private final ArrayList<ActionState> actionQueue;

    // TODO this will probably need to be turned into a State object
    private final boolean isDiscard;
    private final ArrayList<SaveState.CardStateContainer> groupCards;
    private final ArrayList<CardQueueItemState> cardQueueState;

    private final boolean isConfirmButtonDisabled;
    private final int cardSelectAmount;
    private final int numCards;
    private final boolean anyNumber;
    private final boolean forClarity;

    private final boolean forUpgrade;
    private final boolean forTransform;
    private final boolean canCancel;
    private final boolean forPurge;

    public GridCardSelectScreenState() {
        ArrayList<AbstractCard> allCards = new ArrayList<>();

        AbstractPlayer player = AbstractDungeon.player;

        allCards.addAll(player.masterDeck.group);
        allCards.addAll(player.drawPile.group);
        allCards.addAll(player.hand.group);
        allCards.addAll(player.discardPile.group);
        allCards.addAll(player.exhaustPile.group);
        allCards.addAll(player.limbo.group);

        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        this.selectedCards = new ArrayList<>();
        screen.selectedCards
                .forEach(card -> this.selectedCards.add(SaveState.CardStateContainer
                        .forCard(card, allCards)));

        this.isDiscard = screen.targetGroup.type == CardGroup.CardGroupType.DISCARD_PILE;
        this.groupCards = new ArrayList<>();
        screen.targetGroup.group
                .forEach(card -> groupCards
                        .add(SaveState.CardStateContainer.forCard(card, allCards)));

        this.isConfirmButtonDisabled = screen.confirmButton.isDisabled;

        this.cardSelectAmount = ReflectionHacks
                .getPrivate(screen, GridCardSelectScreen.class, "cardSelectAmount");
        this.numCards = ReflectionHacks
                .getPrivate(screen, GridCardSelectScreen.class, "numCards");
        this.forUpgrade = screen.forUpgrade;

        this.forTransform = screen.forTransform;
        this.anyNumber = screen.anyNumber;
        this.forClarity = screen.forClarity;
        this.forPurge = screen.forPurge;

        this.canCancel = ReflectionHacks
                .getPrivate(screen, GridCardSelectScreen.class, "canCancel");

        if (AbstractDungeon.actionManager.currentAction != null) {
            currentActionState = CurrentActionState.getCurrentActionState();
            actionQueue = ActionState.getActionQueueState();
            cardQueueState = new ArrayList<>();
            AbstractDungeon.actionManager.cardQueue.forEach(cardQueueItem -> cardQueueState
                    .add(new CardQueueItemState(cardQueueItem)));
        } else {
            currentActionState = null;
            actionQueue = null;
            cardQueueState = null;
        }
    }

    public GridCardSelectScreenState(JsonObject json) {
        this.selectedCards = cardStateContainerListFromJson(json.get("selected_cards"));
        this.currentActionState = StateJsonHelper
                .currentActionStateFromJson(json.get("current_action_state"));
        this.actionQueue = actionQueueFromJson(json.get("action_queue"));
        this.isDiscard = json.get("is_discard").getAsBoolean();
        this.groupCards = cardStateContainerListFromJson(json.get("group_cards"));
        this.cardQueueState = cardQueueFromJson(json.get("card_queue_state"));
        this.isConfirmButtonDisabled = json.get("is_confirm_button_disabled").getAsBoolean();
        this.cardSelectAmount = json.get("card_select_amount").getAsInt();
        this.numCards = json.get("num_cards").getAsInt();
        this.anyNumber = json.get("any_number").getAsBoolean();
        this.forClarity = json.get("for_clarity").getAsBoolean();
        this.forUpgrade = json.get("for_upgrade").getAsBoolean();
        this.forTransform = json.get("for_transform").getAsBoolean();
        this.canCancel = json.get("can_cancel").getAsBoolean();
        this.forPurge = json.get("for_purge").getAsBoolean();
    }

    public void loadGridSelectScreen() {
        ArrayList<AbstractCard> allCards = new ArrayList<>();

        AbstractPlayer player = AbstractDungeon.player;

        allCards.addAll(player.masterDeck.group);
        allCards.addAll(player.drawPile.group);
        allCards.addAll(player.hand.group);
        allCards.addAll(player.discardPile.group);
        allCards.addAll(player.exhaustPile.group);
        allCards.addAll(player.limbo.group);

        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        screen.selectedCards = new ArrayList<>();
        selectedCards.forEach(cardStateContainer -> {
            AbstractCard card = cardStateContainer.loadCard(allCards);
            screen.selectedCards
                    .add(card);
            card.isGlowing = true;
        });

        if (currentActionState != null) {
            AbstractDungeon.actionManager.currentAction = currentActionState.loadCurrentAction();
            AbstractDungeon.actionManager.phase = GameActionManager.Phase.EXECUTING_ACTIONS;

            actionQueue.forEach(action -> AbstractDungeon.actionManager.actions.add(action
                    .loadAction()));
            AbstractDungeon.actionManager.cardQueue.clear();
            cardQueueState.forEach(cardQueueItemState -> AbstractDungeon.actionManager.cardQueue
                    .add(cardQueueItemState.loadItem()));
        }

        if (isDiscard) {
            screen.targetGroup = AbstractDungeon.player.discardPile;
        } else {
            screen.targetGroup = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
            this.groupCards
                    .forEach(cardStateContainer -> screen.targetGroup
                            .addToTop(cardStateContainer.loadCard(allCards)));
        }

        screen.confirmButton.isDisabled = this.isConfirmButtonDisabled;
        screen.forUpgrade = forUpgrade;
        screen.forPurge = forPurge;
        screen.forTransform = forTransform;
        screen.anyNumber = anyNumber;
        screen.forClarity = forClarity;

        ReflectionHacks
                .setPrivate(screen, GridCardSelectScreen.class, "canCancel", canCancel);

        ReflectionHacks
                .setPrivate(screen, GridCardSelectScreen.class, "cardSelectAmount", cardSelectAmount);

        ReflectionHacks.setPrivate(screen, GridCardSelectScreen.class, "numCards", numCards);
    }

    public String encode() {
        return jsonEncode().toString();
    }

    public JsonObject jsonEncode() {
        JsonObject json = new JsonObject();

        json.add("selected_cards", cardStateContainerListToJson(selectedCards));
        json.add("current_action_state", currentActionState == null ? null : StateJsonHelper
                .currentActionStateToJson(currentActionState));
        json.add("action_queue", actionQueueToJson(actionQueue));
        json.addProperty("is_discard", isDiscard);
        json.add("group_cards", cardStateContainerListToJson(groupCards));
        json.add("card_queue_state", cardQueueToJson(cardQueueState));
        json.addProperty("is_confirm_button_disabled", isConfirmButtonDisabled);
        json.addProperty("card_select_amount", cardSelectAmount);
        json.addProperty("num_cards", numCards);
        json.addProperty("any_number", anyNumber);
        json.addProperty("for_clarity", forClarity);
        json.addProperty("for_upgrade", forUpgrade);
        json.addProperty("for_transform", forTransform);
        json.addProperty("can_cancel", canCancel);
        json.addProperty("for_purge", forPurge);

        return json;
    }

    public JsonObject diffEncode() {
        JsonObject json = new JsonObject();

        json.add("selected_cards", cardStateContainerListToJson(selectedCards));
        json.add("current_action_state", currentActionState == null ? null : StateJsonHelper
                .currentActionStateToJson(currentActionState));
        json.add("action_queue", actionQueueToJson(actionQueue));
        json.addProperty("is_discard", isDiscard);
        json.add("group_cards", cardStateContainerListToJson(groupCards));
        json.add("card_queue_state", cardQueueToJson(cardQueueState));
        // Exact-card selections hide the confirm button, whose disabled state is stale UI state.
        if (anyNumber || forClarity || forUpgrade || forTransform || forPurge) {
            json.addProperty("is_confirm_button_disabled", isConfirmButtonDisabled);
        }
        json.addProperty("card_select_amount", cardSelectAmount);
        json.addProperty("num_cards", numCards);
        json.addProperty("any_number", anyNumber);
        json.addProperty("for_clarity", forClarity);
        json.addProperty("for_upgrade", forUpgrade);
        json.addProperty("for_transform", forTransform);
        json.addProperty("can_cancel", canCancel);
        json.addProperty("for_purge", forPurge);

        return json;
    }

    private static JsonArray cardStateContainerListToJson(ArrayList<SaveState.CardStateContainer> cards) {
        JsonArray json = new JsonArray();
        for (SaveState.CardStateContainer card : cards) {
            json.add(card.jsonEncode());
        }
        return json;
    }

    private static ArrayList<SaveState.CardStateContainer> cardStateContainerListFromJson(JsonElement json) {
        ArrayList<SaveState.CardStateContainer> cards = new ArrayList<>();
        for (JsonElement cardJson : json.getAsJsonArray()) {
            cards.add(SaveState.CardStateContainer.fromJson(cardJson.getAsJsonObject()));
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
