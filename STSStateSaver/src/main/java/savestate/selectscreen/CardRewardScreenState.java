package savestate.selectscreen;

import basemod.ReflectionHacks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import savestate.CardQueueItemState;
import savestate.CardState;
import savestate.StateJsonHelper;
import savestate.actions.ActionState;
import savestate.actions.CurrentActionState;

import java.util.ArrayList;

public class CardRewardScreenState {
    private final CurrentActionState currentActionState;
    private final ArrayList<ActionState> actionQueue;
    private final ArrayList<CardQueueItemState> cardQueueState;

    private final RewardItem rItem;
    private final boolean discovery;
    private final boolean chooseOne;
    private final boolean skippable;
    private final boolean draft;
    private final CardState discoveryCard;
    private final CardState touchCard;

    ArrayList<CardState> rewardGroup;

    public CardRewardScreenState() {
        rewardGroup = new ArrayList<>();

        CardRewardScreen screen = AbstractDungeon.cardRewardScreen;

        screen.rewardGroup.forEach(card -> rewardGroup.add(new CardState(card)));

        rItem = screen.rItem;
        discovery = ReflectionHacks.getPrivate(screen, CardRewardScreen.class, "discovery");
        chooseOne = ReflectionHacks.getPrivate(screen, CardRewardScreen.class, "chooseOne");
        skippable = ReflectionHacks.getPrivate(screen, CardRewardScreen.class, "skippable");
        draft = ReflectionHacks.getPrivate(screen, CardRewardScreen.class, "draft");

        discoveryCard = screen.discoveryCard == null ? null : new CardState(screen.discoveryCard);

        AbstractCard screenTouchCard = ReflectionHacks
                .getPrivate(screen, CardRewardScreen.class, "touchCard");
        touchCard = screenTouchCard == null ? null : new CardState(screenTouchCard);

        // store the action state
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

    public CardRewardScreenState(JsonObject json) {
        this.currentActionState = StateJsonHelper
                .currentActionStateFromJson(json.get("current_action_state"));
        this.actionQueue = actionQueueFromJson(json.get("action_queue"));
        this.cardQueueState = cardQueueFromJson(json.get("card_queue_state"));

        this.rItem = null;
        this.discovery = json.get("discovery").getAsBoolean();
        this.chooseOne = json.get("choose_one").getAsBoolean();
        this.skippable = json.get("skippable").getAsBoolean();
        this.draft = json.get("draft").getAsBoolean();
        this.discoveryCard = cardStateFromJson(json.get("discovery_card"));
        this.touchCard = cardStateFromJson(json.get("touch_card"));
        this.rewardGroup = cardStateListFromJson(json.get("reward_group"));
    }

    public void loadCardRewardScreen() {
        CardRewardScreen screen = AbstractDungeon.cardRewardScreen;

        screen.rItem = rItem;
        ReflectionHacks.setPrivate(screen, CardRewardScreen.class, "discovery", discovery);
        ReflectionHacks.setPrivate(screen, CardRewardScreen.class, "chooseOne", chooseOne);
        ReflectionHacks.setPrivate(screen, CardRewardScreen.class, "skippable", skippable);
        ReflectionHacks.setPrivate(screen, CardRewardScreen.class, "draft", draft);

        screen.discoveryCard = discoveryCard == null ? null : discoveryCard.loadCard();

        AbstractCard screenTouchCard = touchCard == null ? null : touchCard.loadCard();
        ReflectionHacks
                .setPrivate(screen, CardRewardScreen.class, "touchCard", screenTouchCard);

        if (currentActionState != null) {
            AbstractDungeon.actionManager.currentAction = currentActionState.loadCurrentAction();
            AbstractDungeon.actionManager.phase = GameActionManager.Phase.EXECUTING_ACTIONS;

            actionQueue.forEach(action -> AbstractDungeon.actionManager.actions.add(action
                    .loadAction()));

            AbstractDungeon.actionManager.cardQueue.clear();
            cardQueueState.forEach(cardQueueItemState -> AbstractDungeon.actionManager.cardQueue
                    .add(cardQueueItemState.loadItem()));
        }

        screen.rewardGroup.clear();
        rewardGroup.forEach(cardState -> screen.rewardGroup.add(cardState.loadCard()));
    }

    public String encode() {
        return jsonEncode().toString();
    }

    public JsonObject jsonEncode() {
        JsonObject json = new JsonObject();

        json.add("current_action_state", currentActionState == null ? null : StateJsonHelper
                .currentActionStateToJson(currentActionState));
        json.add("action_queue", actionQueueToJson(actionQueue));
        json.add("card_queue_state", cardQueueToJson(cardQueueState));
        json.add("r_item", null);
        json.addProperty("discovery", discovery);
        json.addProperty("choose_one", chooseOne);
        json.addProperty("skippable", skippable);
        json.addProperty("draft", draft);
        json.add("discovery_card", discoveryCard == null ? null : discoveryCard.jsonEncode());
        json.add("touch_card", touchCard == null ? null : touchCard.jsonEncode());
        json.add("reward_group", cardStateListToJson(rewardGroup));

        return json;
    }

    private static CardState cardStateFromJson(JsonElement json) {
        return json == null || json.isJsonNull() ? null : CardState.forJson(json.getAsJsonObject());
    }

    private static JsonArray cardStateListToJson(ArrayList<CardState> cards) {
        JsonArray json = new JsonArray();
        for (CardState card : cards) {
            json.add(card.jsonEncode());
        }
        return json;
    }

    private static ArrayList<CardState> cardStateListFromJson(JsonElement json) {
        ArrayList<CardState> cards = new ArrayList<>();
        for (JsonElement cardJson : json.getAsJsonArray()) {
            cards.add(CardState.forJson(cardJson.getAsJsonObject()));
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
