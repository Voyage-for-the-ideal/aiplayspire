package com.example.communicationmod;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.ui.buttons.*;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import com.example.communicationmod.patches.CardRewardScreenPatch;
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
                    choices.add(relic.name);
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
                AbstractDungeon.bossRelicScreen.relics.get(choice_index).hb.clicked = true;
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
        }
    }

    // --- Helper Methods ---

    public static ArrayList<String> getEventScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        ArrayList<LargeDialogOptionButton> buttons = new ArrayList<>();
        boolean genericShown = (boolean) ReflectionHacks.getPrivateStatic(GenericEventDialog.class, "show");
        if (genericShown) {
             buttons = AbstractDungeon.getCurrRoom().event.imageEventText.optionList;
        } else {
             buttons = RoomEventDialog.optionList;
        }
        for(LargeDialogOptionButton b : buttons) {
            if (!b.isDisabled) choices.add(b.msg);
        }
        return choices;
    }

    public static void makeEventChoice(int choice) {
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
            choices.add("x=" + node.x + ",y=" + node.y + " " + node.getRoomSymbol(true));
        }
        return choices;
    }

    public static ArrayList<MapRoomNode> getMapScreenNodeChoices() {
        ArrayList<MapRoomNode> choices = new ArrayList<>();
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        if (currMapNode == null) return choices; // Should not happen if map is up
        
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
            // Purge
            ShopScreen screen = AbstractDungeon.shopScreen;
            Hitbox purgeHb = (Hitbox) ReflectionHacks.getPrivate(screen, ShopScreen.class, "purgeHb");
            purgeHb.clicked = true;
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
            return !AbstractDungeon.gridSelectScreen.confirmButton.isDisabled;
        } 
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT) {
            return !AbstractDungeon.handCardSelectScreen.button.isDisabled;
        }
        
        // Check Proceed Button
        boolean isHidden = (boolean) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class, "isHidden");
        return !isHidden;
    }

    public static boolean isCancelButtonAvailable() {
        // Check Cancel Button
        boolean isHidden = (boolean) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.cancelButton, CancelButton.class, "isHidden");
        if (!isHidden) return true;
        
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
            return AbstractDungeon.dungeonMapScreen.dismissable;
        }
        
        return false;
    }
    
    public static void pressConfirmButton() {
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
