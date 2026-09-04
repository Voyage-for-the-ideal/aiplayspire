package com.example.communicationmod;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.ui.buttons.*;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import com.example.communicationmod.patches.CardRewardScreenPatch;
import com.example.communicationmod.patches.DungeonMapPatch;
import com.example.communicationmod.patches.MapRoomNodeHoverPatch;
import com.example.communicationmod.patches.GridCardSelectScreenPatch;
import com.example.communicationmod.patches.MerchantPatch;
import com.example.communicationmod.patches.ShopScreenPatch;

import java.util.ArrayList;
import java.lang.reflect.Method;

public class ChoiceScreenUtils {

    public enum ChoiceType {
        EVENT,
        CHEST,
        SHOP_ROOM,
        REST,
        CARD_REWARD,
        COMBAT_REWARD,
        MAP,
        BOSS_REWARD,
        SHOP_SCREEN,
        GRID,
        HAND_SELECT,
        GAME_OVER,
        COMPLETE,
        MAIN_MENU,
        CHAR_SELECT,
        NONE
    }

    // Helper method to remove text formatting
    public static String removeTextFormatting(String text) {
        text = text.replaceAll("~|@(\\S+)~|@", "$1");
        return text.replaceAll("#.|NL", "");
    }

    // Helper method to format a card for display
    public static String formatCard(AbstractCard card, boolean showPrice) {
        String cost;
        if (card.cost == -1)
            cost = "X";
        else if (card.cost == -2)
            cost = "UNPLAYABLE";
        else if (card.freeToPlay())
            cost = "0";
        else
            cost = Integer.toString(card.costForTurn);

        String result = "[" + card.name + "] Cost: " + cost + " " + card.type.name();
        if (showPrice && card.price > 0) {
            result += " (" + card.price + " gold)";
        }
        return result;
    }

    public static ChoiceType getCurrentChoiceType() {
        // Out-of-run screens must be classified before any AbstractDungeon
        // dereference (the dungeon is already disposed at the menu).
        if (!CardCrawlGame.isInARun()) {
            if (RunSetupUtils.isMenuAvailable()) {
                switch (CardCrawlGame.mainMenuScreen.screen) {
                    case CHAR_SELECT:
                        return ChoiceType.CHAR_SELECT;
                    default:
                        return ChoiceType.MAIN_MENU;
                }
            }
            return ChoiceType.NONE;
        }

        if (!AbstractDungeon.isScreenUp) {
            if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.EVENT || (AbstractDungeon.getCurrRoom().event != null && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE)) {
                return ChoiceType.EVENT;
            } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss || AbstractDungeon.getCurrRoom() instanceof TreasureRoom) {
                return ChoiceType.CHEST;
            } else if (AbstractDungeon.getCurrRoom() instanceof ShopRoom) {
                return ChoiceType.SHOP_ROOM;
            } else if (AbstractDungeon.getCurrRoom() instanceof RestRoom) {
                return ChoiceType.REST;
            } else if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE && AbstractDungeon.actionManager.isEmpty() && !AbstractDungeon.isFadingOut) {
                if (AbstractDungeon.getCurrRoom().event == null || (!(AbstractDungeon.getCurrRoom().event instanceof AbstractImageEvent) && (!AbstractDungeon.getCurrRoom().event.hasFocus))) {
                    return ChoiceType.COMPLETE;
                }
            }
            return ChoiceType.NONE;
        }
        
        switch(AbstractDungeon.screen) {
            case CARD_REWARD: return ChoiceType.CARD_REWARD;
            case COMBAT_REWARD: return ChoiceType.COMBAT_REWARD;
            case MAP: return ChoiceType.MAP;
            case BOSS_REWARD: return ChoiceType.BOSS_REWARD;
            case SHOP: return ChoiceType.SHOP_SCREEN;
            case GRID: return ChoiceType.GRID;
            case HAND_SELECT: return ChoiceType.HAND_SELECT;
            case DEATH:
            case VICTORY:
            case UNLOCK:
            case NEOW_UNLOCK: return ChoiceType.GAME_OVER;
            default: return ChoiceType.NONE;
        }
    }

    public static ArrayList<String> getCurrentChoiceList() {
        ChoiceType choiceType = getCurrentChoiceType();
        ArrayList<String> choices = new ArrayList<>();
        switch (choiceType) {
            case EVENT: return getEventScreenChoices();
            case CHEST: 
                if (AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss) {
                    if (!((TreasureRoomBoss) AbstractDungeon.getCurrRoom()).chest.isOpen) choices.add("open");
                } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoom) {
                    if (!((TreasureRoom) AbstractDungeon.getCurrRoom()).chest.isOpen) choices.add("open");
                }
                break;
            case SHOP_ROOM: choices.add("shop"); break;
            case REST: return getRestRoomChoices();
            case CARD_REWARD: return getCardRewardScreenChoices();
            case COMBAT_REWARD: return getCombatRewardScreenChoices();
            case MAP: return getMapScreenChoices();
            case BOSS_REWARD:
                for(com.megacrit.cardcrawl.relics.AbstractRelic relic : AbstractDungeon.bossRelicScreen.relics) {
                    choices.add(relic.relicId);
                }
                break;
            case SHOP_SCREEN: return getShopScreenChoices();
            case GRID:
                if (isConfirmButtonAvailable()) {
                    choices.add("confirm");
                }
                for(AbstractCard card : AbstractDungeon.gridSelectScreen.targetGroup.group) {
                    choices.add(card.name.toLowerCase());
                }
                break;
            case HAND_SELECT:
                for(AbstractCard card : AbstractDungeon.player.hand.group) {
                    choices.add(card.name.toLowerCase());
                }
                break;
            case GAME_OVER:
                // Death/victory screens offer "return to menu"; the unlock
                // screens that appear during the same return flow offer confirm.
                if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.UNLOCK
                        || AbstractDungeon.screen == AbstractDungeon.CurrentScreen.NEOW_UNLOCK) {
                    choices.add("confirm");
                } else {
                    choices.add("return_to_menu");
                }
                break;
            case MAIN_MENU:
                if (CardCrawlGame.mainMenuScreen != null
                        && CardCrawlGame.mainMenuScreen.screen == MainMenuScreen.CurScreen.MAIN_MENU) {
                    choices.add("play");
                }
                break;
            case CHAR_SELECT:
                return RunSetupUtils.getCharSelectChoices();
        }
        return choices;
    }

    public static void executeChoice(int choice_index) {
        ChoiceType choiceType = getCurrentChoiceType();
        System.out.println("ChoiceScreenUtils: Executing choice index " + choice_index + " for screen type " + choiceType);
        switch (choiceType) {
            case EVENT: makeEventChoice(choice_index); break;
            case CHEST: 
                if (AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss) {
                    ((TreasureRoomBoss) AbstractDungeon.getCurrRoom()).chest.isOpen = true;
                    ((TreasureRoomBoss) AbstractDungeon.getCurrRoom()).chest.open(false);
                } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoom) {
                    ((TreasureRoom) AbstractDungeon.getCurrRoom()).chest.isOpen = true;
                    ((TreasureRoom) AbstractDungeon.getCurrRoom()).chest.open(false);
                }
                break;
            case SHOP_ROOM: 
                 if (AbstractDungeon.getCurrRoom() instanceof ShopRoom) {
                     MerchantPatch.visitMerchant = true;
                 }
                break;
            case REST: makeRestRoomChoice(choice_index); break;
            case CARD_REWARD:
                ArrayList<String> cChoices = getCurrentChoiceList();
                if (cChoices.get(choice_index).equals("bowl")) {
                    SingingBowlButton bowlButton = (SingingBowlButton) ReflectionHacks.getPrivate(AbstractDungeon.cardRewardScreen, com.megacrit.cardcrawl.screens.CardRewardScreen.class, "bowlButton");
                    bowlButton.onClick();
                } else {
                    AbstractCard selectedCard = AbstractDungeon.cardRewardScreen.rewardGroup.get(choice_index);
                    CardRewardScreenPatch.doHover = true;
                    CardRewardScreenPatch.hoverCard = selectedCard;
                    selectedCard.hb.clicked = true;
                }
                break;
            case COMBAT_REWARD:
                makeCombatRewardChoice(choice_index);
                break;
            case MAP: makeMapChoice(choice_index); break;
            case BOSS_REWARD:
                AbstractDungeon.bossRelicScreen.relics.get(choice_index).bossObtainLogic();
                break;
            case SHOP_SCREEN: makeShopScreenChoice(choice_index); break;
            case GRID:
                if (choice_index == 0 && isConfirmButtonAvailable()) {
                    pressConfirmButton();
                    return;
                }
                int adjustedIndex = isConfirmButtonAvailable() ? choice_index - 1 : choice_index;
                if (adjustedIndex >= 0 && adjustedIndex < AbstractDungeon.gridSelectScreen.targetGroup.group.size()) {
                    AbstractCard card = AbstractDungeon.gridSelectScreen.targetGroup.group.get(adjustedIndex);
                    GridCardSelectScreenPatch.hoverCard = card;
                    GridCardSelectScreenPatch.doHover = true;
                }
                break;
            case HAND_SELECT:
                AbstractDungeon.player.hand.group.get(choice_index).hb.clicked = true;
                // HandSelectScreen usually requires hovering and then selecting logic which is complex without patches.
                // Trying a simple approach:
                AbstractDungeon.handCardSelectScreen.hoveredCard = AbstractDungeon.player.hand.group.get(choice_index);
                try {
                    Method m = HandCardSelectScreen.class.getDeclaredMethod("selectHoveredCard");
                    m.setAccessible(true);
                    m.invoke(AbstractDungeon.handCardSelectScreen);
                } catch (Exception e) { e.printStackTrace(); }
                break;
            case GAME_OVER:
                RunSetupUtils.makeGameOverChoice();
                break;
            case MAIN_MENU:
                RunSetupUtils.openCharSelectFromMenu();
                break;
            case CHAR_SELECT:
                RunSetupUtils.selectCharacter(choice_index);
                break;
        }
    }

    // --- Helper Methods ---

    public static ArrayList<String> getEventScreenChoices() {
        ArrayList<LargeDialogOptionButton> buttons = getEventScreenButtons();
        return EventStateExtractor.enabledChoiceLabels(AbstractDungeon.getCurrRoom().event, buttons);
    }

    public static ArrayList<LargeDialogOptionButton> getEventScreenButtons() {
        ArrayList<LargeDialogOptionButton> buttons = new ArrayList<>();
        boolean genericShown = (boolean) ReflectionHacks.getPrivateStatic(GenericEventDialog.class, "show");
        if (genericShown) {
             buttons = AbstractDungeon.getCurrRoom().event.imageEventText.optionList;
        } else {
             buttons = RoomEventDialog.optionList;
        }
        return buttons;
    }

    public static void makeEventChoice(int choice) {
        if (makeMiniGameChoice(choice)) return;
        ArrayList<LargeDialogOptionButton> buttons = new ArrayList<>();
        boolean genericShown = (boolean) ReflectionHacks.getPrivateStatic(GenericEventDialog.class, "show");
        if (genericShown) {
             buttons = AbstractDungeon.getCurrRoom().event.imageEventText.optionList;
             System.out.println("Using GenericEventDialog options.");
        } else {
             buttons = RoomEventDialog.optionList;
             System.out.println("Using RoomEventDialog options.");
        }
        int activeIndex = 0;
        for(LargeDialogOptionButton b : buttons) {
            if (!b.isDisabled) {
                if (activeIndex == choice) {
                    b.pressed = true;
                    System.out.println("Pressed event button: " + b.msg);
                    return;
                }
                activeIndex++;
            }
        }
        System.err.println("Event choice index not found: " + choice);
    }

    private static boolean makeMiniGameChoice(int choice) {
        if (AbstractDungeon.getCurrRoom().event == null) return false;
        Object event = AbstractDungeon.getCurrRoom().event;
        String className = event.getClass().getSimpleName();
        if ("GremlinWheelGame".equals(className)) {
            Object startSpin = getPrivateField(event, "startSpin");
            Object buttonPressed = getPrivateField(event, "buttonPressed");
            if (choice == 0 && Boolean.TRUE.equals(startSpin) && Boolean.FALSE.equals(buttonPressed)) {
                setPrivateField(event, "buttonPressed", true);
                CardCrawlGame.sound.play("WHEEL");
                return true;
            }
            return "SPIN".equals(String.valueOf(getPrivateField(event, "screen")));
        }
        if (!"GremlinMatchGame".equals(className) ||
            !"PLAY".equals(String.valueOf(getPrivateField(event, "screen")))) return false;

        CardGroup cards = (CardGroup) getPrivateField(event, "cards");
        float waitTimer = ((Number) getPrivateField(event, "waitTimer")).floatValue();
        boolean gameDone = Boolean.TRUE.equals(getPrivateField(event, "gameDone"));
        if (cards == null || waitTimer != 0.0F || gameDone) return true;
        int activeIndex = 0;
        for (AbstractCard card : cards.group) {
            if (!card.isFlipped) continue;
            if (activeIndex++ == choice) {
                card.isFlipped = false;
                card.drawScale = 0.7F;
                card.targetDrawScale = 0.7F;
                boolean cardFlipped = Boolean.TRUE.equals(getPrivateField(event, "cardFlipped"));
                AbstractCard chosenCard = (AbstractCard) getPrivateField(event, "chosenCard");
                if (!cardFlipped || chosenCard == null) {
                    setPrivateField(event, "cardFlipped", true);
                    setPrivateField(event, "chosenCard", card);
                    return true;
                }

                setPrivateField(event, "cardFlipped", false);
                setPrivateField(event, "hoveredCard", card);
                if (chosenCard.cardID.equals(card.cardID)) {
                    setPrivateField(event, "waitTimer", 1.0F);
                    chosenCard.targetDrawScale = 0.7F;
                    chosenCard.target_x = Settings.WIDTH / 2.0F;
                    chosenCard.target_y = Settings.HEIGHT / 2.0F;
                    card.targetDrawScale = 0.7F;
                    card.target_x = Settings.WIDTH / 2.0F;
                    card.target_y = Settings.HEIGHT / 2.0F;
                } else {
                    setPrivateField(event, "waitTimer", 1.25F);
                    chosenCard.targetDrawScale = 1.0F;
                    card.targetDrawScale = 1.0F;
                }
                return true;
            }
        }
        System.err.println("Match game choice index not found: " + choice);
        return true;
    }

    private static Object getPrivateField(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static void setPrivateField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new IllegalStateException("Missing event field: " + name);
    }

    public static ArrayList<String> getRestRoomChoices() {
        ArrayList<String> choices = new ArrayList<>();
        RestRoom room = (RestRoom) AbstractDungeon.getCurrRoom();
        boolean somethingSelected = (boolean) ReflectionHacks.getPrivate(room.campfireUI, CampfireUI.class, "somethingSelected");
        if (somethingSelected) {
            return choices;
        }
        ArrayList<AbstractCampfireOption> buttons = (ArrayList<AbstractCampfireOption>) ReflectionHacks.getPrivate(room.campfireUI, CampfireUI.class, "buttons");
        for (AbstractCampfireOption b : buttons) {
            if (b.usable) choices.add(b.getClass().getSimpleName());
        }
        return choices;
    }

    public static void makeRestRoomChoice(int index) {
        RestRoom room = (RestRoom) AbstractDungeon.getCurrRoom();
        ArrayList<AbstractCampfireOption> buttons = (ArrayList<AbstractCampfireOption>) ReflectionHacks.getPrivate(room.campfireUI, CampfireUI.class, "buttons");
        int activeIndex = 0;
        for (AbstractCampfireOption b : buttons) {
            if (b.usable) {
                if (activeIndex == index) {
                    b.useOption();
                    ReflectionHacks.setPrivate(room.campfireUI, CampfireUI.class, "somethingSelected", true);
                    return;
                }
                activeIndex++;
            }
        }
    }

    public static ArrayList<String> getMapScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        ArrayList<MapRoomNode> nodes = getMapScreenNodeChoices();
        for (MapRoomNode node : nodes) {
            choices.add(describeMapNodeForHuman(node));
        }
        return choices;
    }

    public static ArrayList<MapRoomNode> getVisibleMapRowNodes(int rowIndex) {
        ArrayList<MapRoomNode> visibleNodes = new ArrayList<>();
        if (AbstractDungeon.map == null || rowIndex < 0 || rowIndex >= AbstractDungeon.map.size()) {
            return visibleNodes;
        }

        for (MapRoomNode node : AbstractDungeon.map.get(rowIndex)) {
            if (isVisibleMapNode(node)) {
                visibleNodes.add(node);
            }
        }
        return visibleNodes;
    }

    public static boolean isVisibleMapNode(MapRoomNode node) {
        if (node == null) {
            return false;
        }
        String symbol = node.getRoomSymbol(true);
        if (symbol == null || symbol.isEmpty() || symbol.equals("*")) {
            return false;
        }
        if (node.hasEdges()) {
            return true;
        }
        if (AbstractDungeon.currMapNode != null && node.x == AbstractDungeon.currMapNode.x && node.y == AbstractDungeon.currMapNode.y) {
            return true;
        }
        return hasIncomingMapEdge(node);
    }

    private static boolean hasIncomingMapEdge(MapRoomNode targetNode) {
        if (AbstractDungeon.map == null || targetNode == null) {
            return false;
        }

        for (ArrayList<MapRoomNode> row : AbstractDungeon.map) {
            for (MapRoomNode sourceNode : row) {
                if (sourceNode == null) {
                    continue;
                }
                if (sourceNode.isConnectedTo(targetNode) || sourceNode.wingedIsConnectedTo(targetNode)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int getMapNodeLaneIndexFromLeft(MapRoomNode node) {
        if (node == null) {
            return -1;
        }

        ArrayList<MapRoomNode> rowNodes = getVisibleMapRowNodes(node.y);
        for (int i = 0; i < rowNodes.size(); i++) {
            MapRoomNode rowNode = rowNodes.get(i);
            if (rowNode.x == node.x && rowNode.y == node.y) {
                return i + 1;
            }
        }
        return -1;
    }

    public static String getRoomTypeLabel(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return "未知";
        }

        switch (symbol) {
            case "M":
                return "怪物房";
            case "E":
                return "精英房";
            case "R":
                return "休息点";
            case "$":
                return "商店";
            case "?":
                return "事件房";
            case "T":
                return "宝箱房";
            case "B":
                return "Boss";
            default:
                return symbol;
        }
    }

    public static String describeMapNodeForHuman(MapRoomNode node) {
        if (node == null) {
            return "未知房间";
        }

        int laneIndex = getMapNodeLaneIndexFromLeft(node);
        String symbol = node.getRoomSymbol(true);
        String roomType = getRoomTypeLabel(symbol);
        if (laneIndex > 0) {
            return "第" + laneIndex + "个房间(" + roomType + ")";
        }
        return roomType;
    }

    public static ArrayList<MapRoomNode> getMapScreenNodeChoices() {
        ArrayList<MapRoomNode> choices = new ArrayList<>();
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        if (currMapNode == null) return choices; // Should not happen if map is up

        // Check if we are selecting the boss
        String actId = AbstractDungeon.id;
        if(currMapNode.y == 14 || ("TheEnding".equals(actId) && currMapNode.y == 2)) {
            MapRoomNode bossNode = new MapRoomNode(-1, 15);
            bossNode.room = new com.megacrit.cardcrawl.rooms.MonsterRoomBoss();
            choices.add(bossNode);
            return choices;
        }

        // Special handling for first room
        if (!AbstractDungeon.firstRoomChosen) {
             for(MapRoomNode node : AbstractDungeon.map.get(0)) {
                if (node.hasEdges()) choices.add(node);
             }
             return choices;
        }

        // Normal connections
        ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
        for (ArrayList<MapRoomNode> rows : map) {
            for (MapRoomNode node : rows) {
                if (node.hasEdges()) {
                    if (currMapNode.isConnectedTo(node) || currMapNode.wingedIsConnectedTo(node)) {
                        choices.add(node);
                    }
                }
            }
        }
        return choices;
    }

    public static void makeMapChoice(int index) {
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        if(currMapNode != null) {
            String actId = AbstractDungeon.id;
            if(currMapNode.y == 14 || ("TheEnding".equals(actId) && currMapNode.y == 2)) {
                if(index == 0) {
                    DungeonMapPatch.doBossHover = true;
                    return;
                } else {
                    throw new IndexOutOfBoundsException("Only a boss node can be chosen here.");
                }
            }
        }

        ArrayList<MapRoomNode> nodes = getMapScreenNodeChoices();
        if (index >= 0 && index < nodes.size()) {
            MapRoomNode node = nodes.get(index);
            System.out.println("Selecting map node: " + node.getRoomSymbol(true) + " at (" + node.x + "," + node.y + ")");
            MapRoomNodeHoverPatch.hoverNode = node;
            MapRoomNodeHoverPatch.doHover = true;
            AbstractDungeon.dungeonMapScreen.clicked = true;
        } else {
             System.err.println("Map choice index out of bounds: " + index);
        }
    }

    public static ArrayList<String> getShopScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        ArrayList<Object> items = getShopItems();
        for (Object item : items) {
            if (item instanceof String) choices.add((String)item);
            else if (item instanceof AbstractCard) choices.add(formatCard((AbstractCard)item, true));
            else if (item instanceof StoreRelic) {
                choices.add("relic: [" + ((StoreRelic)item).relic.name + "] " +
                        removeTextFormatting(((StoreRelic)item).relic.description) + "(" +
                        ((StoreRelic)item).price  + " gold)");
            } else if (item instanceof StorePotion) {
                choices.add("add potion: [" + ((StorePotion)item).potion.name + "] " +
                        removeTextFormatting(((StorePotion)item).potion.description) +
                        "(" + ((StorePotion)item).price  + " gold)");
            }
        }
        if (isCancelButtonAvailable()) {
            choices.add("leave");
        }
        return choices;
    }
    
    public static ArrayList<Object> getShopItems() {
        ArrayList<Object> items = new ArrayList<>();
        ShopScreen screen = AbstractDungeon.shopScreen;
        if (screen.purgeAvailable && AbstractDungeon.player.gold >= ShopScreen.actualPurgeCost) {
            items.add("purge (" + ShopScreen.actualPurgeCost + " gold)");
        }
        
        ArrayList<AbstractCard> colored = (ArrayList<AbstractCard>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "coloredCards");
        ArrayList<AbstractCard> colorless = (ArrayList<AbstractCard>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "colorlessCards");
        ArrayList<StoreRelic> relics = (ArrayList<StoreRelic>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "relics");
        ArrayList<StorePotion> potions = (ArrayList<StorePotion>) ReflectionHacks.getPrivate(screen, ShopScreen.class, "potions");
        
        for (AbstractCard c : colored) {
            if (c.price <= AbstractDungeon.player.gold) items.add(c);
        }
        for (AbstractCard c : colorless) {
            if (c.price <= AbstractDungeon.player.gold) items.add(c);
        }
        for (StoreRelic r : relics) {
            if (r.price <= AbstractDungeon.player.gold) items.add(r);
        }
        for (StorePotion p : potions) {
            if (p.price <= AbstractDungeon.player.gold) items.add(p);
        }
        
        return items;
    }

    public static void makeShopScreenChoice(int index) {
        ArrayList<Object> items = getShopItems();
        if (index >= items.size()) {
            if (index == items.size() && isCancelButtonAvailable()) {
                pressCancelButton();
                return;
            } else {
                System.err.println("Shop choice index out of bounds: " + index);
                return;
            }
        }
        Object item = items.get(index);
        if (item instanceof String) {
            // ShopScreen has no purge Hitbox field.  Its normal UI path enters
            // the GRID screen through this private method.
            ShopScreen screen = AbstractDungeon.shopScreen;
            try {
                Method purchasePurge = ShopScreen.class.getDeclaredMethod("purchasePurge");
                purchasePurge.setAccessible(true);
                purchasePurge.invoke(screen);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to open the shop purge selection", e);
            }
        } else if (item instanceof AbstractCard) {
            ShopScreenPatch.doHover = true;
            ShopScreenPatch.hoverCard = (AbstractCard)item;
            ((AbstractCard)item).hb.clicked = true;
        } else if (item instanceof StoreRelic) {
            ((StoreRelic)item).relic.hb.clicked = true;
        } else if (item instanceof StorePotion) {
            ((StorePotion)item).potion.hb.clicked = true;
        }
    }

    public static boolean isBowlAvailable() {
        SingingBowlButton bowlButton = (SingingBowlButton) ReflectionHacks.getPrivate(AbstractDungeon.cardRewardScreen, com.megacrit.cardcrawl.screens.CardRewardScreen.class, "bowlButton");
        return !((boolean) ReflectionHacks.getPrivate(bowlButton, SingingBowlButton.class, "isHidden"));
    }

    public static boolean isConfirmButtonAvailable() {
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID) {
            GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
            return isGridConfirmAvailable(
                screen.confirmScreenUp,
                screen.isJustForConfirming,
                screen.anyNumber,
                screen.forClarity,
                screen.confirmButton.isDisabled);
        } 
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT) {
            return !AbstractDungeon.handCardSelectScreen.button.isDisabled;
        }
        
        // Check Proceed Button
        boolean isHidden = (boolean) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class, "isHidden");
        return !isHidden;
    }

    static boolean isGridConfirmAvailable(boolean confirmScreenUp,
                                          boolean isJustForConfirming,
                                          boolean anyNumber,
                                          boolean forClarity,
                                          boolean confirmButtonDisabled) {
        boolean gridModeUsesConfirm = confirmScreenUp || isJustForConfirming || anyNumber || forClarity;
        return gridModeUsesConfirm && !confirmButtonDisabled;
    }

    public static boolean isCancelButtonAvailable() {
        // Boss relic selection must choose a relic; do not advertise its UI skip button.
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.BOSS_REWARD) {
            return isBossRewardCancelable();
        }
        // Check Cancel Button
        boolean isHidden = (boolean) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.cancelButton, CancelButton.class, "isHidden");
        if (!isHidden) return true;
        
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
            return AbstractDungeon.dungeonMapScreen.dismissable;
        }
        
        return false;
    }

    static boolean isBossRewardCancelable() {
        return false;
    }
    
    public static void pressConfirmButton() {
        if (!CardCrawlGame.isInARun()) {
            if (getCurrentChoiceType() == ChoiceType.CHAR_SELECT) {
                RunSetupUtils.confirmCharacterSelect();
            }
            return;
        }
        if (AbstractDungeon.getCurrRoom() == null) {
            return;
        }
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID) {
            AbstractDungeon.gridSelectScreen.confirmButton.hb.clicked = true;
        } else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT) {
            AbstractDungeon.handCardSelectScreen.button.hb.clicked = true;
        } else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
            AbstractDungeon.overlayMenu.proceedButton.show();
            Hitbox hb = (Hitbox) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class, "hb");
            hb.clicked = true;
        } else if (AbstractDungeon.getCurrRoom() instanceof TreasureRoom || AbstractDungeon.getCurrRoom() instanceof TreasureRoomBoss || AbstractDungeon.getCurrRoom() instanceof RestRoom || AbstractDungeon.getCurrRoom() instanceof ShopRoom) {
             AbstractDungeon.overlayMenu.proceedButton.show();
             Hitbox hb = (Hitbox) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class, "hb");
             hb.clicked = true;
        } else if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE) {
             AbstractDungeon.overlayMenu.proceedButton.show();
             Hitbox hb = (Hitbox) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class, "hb");
             hb.clicked = true;
        }
    }
    
    public static void pressCancelButton() {
        if (!CardCrawlGame.isInARun()) {
            return;
        }
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
            if (AbstractDungeon.dungeonMapScreen.dismissable) {
                AbstractDungeon.dungeonMapScreen.clicked = true; // Logic might be more complex to dismiss
                AbstractDungeon.closeCurrentScreen();
            }
        } else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.SHOP) {
            AbstractDungeon.overlayMenu.cancelButton.hb.clicked = true;
        } else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID) {
             AbstractDungeon.closeCurrentScreen();
        } else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD) {
             AbstractDungeon.closeCurrentScreen();
        } else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
             AbstractDungeon.overlayMenu.cancelButton.hb.clicked = true;
             AbstractDungeon.closeCurrentScreen();
        }
    }

    public static ArrayList<String> getCardRewardScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.cardRewardScreen.rewardGroup) {
            choices.add(formatCard(card, false));
        }
        if(isBowlAvailable()) {
            choices.add("bowl");
        }
        return choices;
    }

    public static void makeCombatRewardChoice(int choice) {
        RewardItem reward = AbstractDungeon.combatRewardScreen.rewards.get(choice);
        // Using isDone = true might just remove it without triggering the effect (like opening card screen)
        // Simulate a click instead
        reward.hb.hovered = true;
        reward.hb.clicked = true;
    }

    public static ArrayList<String> getCombatRewardScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        for(RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
            switch(reward.type) {
                case GOLD:
                case STOLEN_GOLD:
                    choices.add(reward.goldAmt + "(+" + reward.bonusGold + " bonus) " +
                            reward.type.name().toLowerCase());
                    break;
                case POTION:
                    choices.add("add " + reward.type.name().toLowerCase() +": [" +
                            reward.potion.name + "] " + removeTextFormatting(reward.potion.description));
                    break;
                case RELIC:
                    choices.add(reward.type.name().toLowerCase() +": [" +
                            reward.relic.name + "]  " + removeTextFormatting(reward.relic.description));
                    break;
                case CARD:
                    choices.add("add card to deck");
                    break;
                default:
                    choices.add(reward.type.name().toLowerCase());
            }
        }
        return choices;
    }
}
