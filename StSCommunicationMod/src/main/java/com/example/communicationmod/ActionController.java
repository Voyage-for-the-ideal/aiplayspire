package com.example.communicationmod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.common.EndTurnAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ActionController {
    private static final Gson gson = new Gson();
    public static final ConcurrentLinkedQueue<String> actionQueue = new ConcurrentLinkedQueue<>();

    public static void processQueue() {
        while (!actionQueue.isEmpty()) {
            String jsonCommand = actionQueue.poll();
            executeCommand(jsonCommand);
        }
    }

    private static void executeCommand(String jsonCommand) {
        System.out.println("ActionController received command: " + jsonCommand);
        try {
            JsonObject command = gson.fromJson(jsonCommand, JsonObject.class);
            String type = command.get("type").getAsString();

            if (AbstractDungeon.player == null || AbstractDungeon.getCurrRoom() == null) {
                System.err.println("Cannot execute command: Game not ready.");
                return;
            }

            switch (type) {
                case "play":
                    if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
                        System.err.println("Cannot play cards outside of combat.");
                        break;
                    }
                    int cardIndex = command.get("card_index").getAsInt();
                    int targetIndex = command.has("target_index") ? command.get("target_index").getAsInt() : -1;
                    System.out.println("Executing Play Command: cardIndex=" + cardIndex + ", targetIndex=" + targetIndex);
                    playCard(cardIndex, targetIndex);
                    break;
                case "end_turn":
                    if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
                        System.err.println("Cannot end turn outside of combat.");
                        break;
                    }
                    System.out.println("Executing End Turn Command");
                    AbstractDungeon.overlayMenu.endTurnButton.disable(true);
                    break;
                case "choose":
                    if (command.has("choice_index")) {
                        int choiceIndex = command.get("choice_index").getAsInt();
                        System.out.println("Executing Choose Command: index=" + choiceIndex);
                        ChoiceScreenUtils.executeChoice(choiceIndex);
                    }
                    break;
                case "proceed":
                case "confirm":
                    ChoiceScreenUtils.pressConfirmButton();
                    break;
                case "skip":
                case "cancel":
                    ChoiceScreenUtils.pressCancelButton();
                    break;
                case "potion":
                    if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
                        System.err.println("Cannot use potion outside of combat.");
                        break;
                    }
                    if (!command.has("potion_index")) {
                        System.err.println("Potion command missing potion_index.");
                        break;
                    }
                    int potionIndex = command.get("potion_index").getAsInt();
                    int potionTargetIndex = command.has("target_index") ? command.get("target_index").getAsInt() : -1;
                    System.out.println("Executing Potion Command: potionIndex=" + potionIndex + ", targetIndex=" + potionTargetIndex);
                    usePotion(potionIndex, potionTargetIndex);
                    break;
                case "wait":
                    // Do nothing, just wait
                    break;
                default:
                    System.err.println("Unknown command type: " + type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void playCard(int cardIndex, int targetIndex) {
        if (cardIndex < 0 || cardIndex >= AbstractDungeon.player.hand.size()) {
            System.err.println("Invalid card index: " + cardIndex);
            return;
        }

        AbstractCard card = AbstractDungeon.player.hand.group.get(cardIndex);
        System.out.println("Playing card: " + card.name + " (uuid=" + card.uuid + ")");
        AbstractMonster target = null;

        // Find target correctly (matching the logic in GameStateConverter which filters out dead monsters)
        if (targetIndex >= 0 && AbstractDungeon.getMonsters() != null) {
            int aliveCount = 0;
            for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                if (!m.isDeadOrEscaped()) {
                    if (aliveCount == targetIndex) {
                        target = m;
                        break;
                    }
                    aliveCount++;
                }
            }
        }

        // Verify energy (skip if free)
        if (card.costForTurn > EnergyPanel.totalCount && !card.freeToPlayOnce && !card.ignoreEnergyOnUse && !card.isInAutoplay) {
             System.err.println("Not enough energy to play " + card.name);
             return;
        }

        if (!card.canUse(AbstractDungeon.player, target)) {
            System.err.println("Card cannot be used (target invalid or other condition): " + card.name);
            return;
        }

        if (target != null) {
            card.calculateCardDamage(target);
        }
        
        // Add to card queue using the standard constructor to ensure energy is consumed and on-play effects trigger correctly
        AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, target));
    }

    private static void usePotion(int potionIndex, int targetIndex) {
        if (potionIndex < 0 || potionIndex >= AbstractDungeon.player.potions.size()) {
            System.err.println("Invalid potion index: " + potionIndex);
            return;
        }

        AbstractPotion potion = AbstractDungeon.player.potions.get(potionIndex);
        if (potion == null || potion instanceof PotionSlot) {
            System.err.println("Potion slot is empty: " + potionIndex);
            return;
        }

        if (!potion.canUse()) {
            System.err.println("Potion cannot be used now: " + potion.name);
            return;
        }

        AbstractMonster target = null;
        if (potion.isThrown) {
            if (targetIndex < 0) {
                System.err.println("Potion requires target but target_index is missing: " + potion.name);
                return;
            }

            int aliveCount = 0;
            for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                if (!m.isDeadOrEscaped()) {
                    if (aliveCount == targetIndex) {
                        target = m;
                        break;
                    }
                    aliveCount++;
                }
            }

            if (target == null) {
                System.err.println("Invalid potion target index: " + targetIndex);
                return;
            }

            potion.use(target);
        } else {
            potion.use(AbstractDungeon.player);
        }

        AbstractDungeon.topPanel.destroyPotion(potionIndex);
    }
}
