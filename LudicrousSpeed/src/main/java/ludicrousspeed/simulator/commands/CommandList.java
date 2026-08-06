package ludicrousspeed.simulator.commands;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.actions.utility.ScryAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.curses.Clumsy;
import com.megacrit.cardcrawl.cards.status.Dazed;
import com.megacrit.cardcrawl.cards.status.VoidCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.ui.buttons.CardSelectConfirmButton;
import com.megacrit.cardcrawl.ui.buttons.GridSelectConfirmButton;
import savestate.PotionState;

import java.util.*;

public final class CommandList {
    public static List<Command> getAvailableCommands(Comparator<AbstractCard> cardComparator, HashMap<Class, Comparator<AbstractCard>> actionHeuristics) {
        List<Command> commands = new ArrayList<>();
        AbstractPlayer player = AbstractDungeon.player;
        List<AbstractCard> hand = player.hand.group;
        List<AbstractPotion> potions = player.potions;

        Set<String> seenCommands = new HashSet<>();

        if (shouldCheckForPlays()) {
            List<AbstractMonster> monsters = AbstractDungeon.currMapNode.room.monsters.monsters;

            for(int i = 0; i < hand.size(); i++) {
                AbstractCard card = hand.get(i);

                if (card.target == AbstractCard.CardTarget.ENEMY || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
                    for (int j = 0; j < monsters.size(); j++) {
                        AbstractMonster monster = monsters.get(j);
                        if (card.canUse(player, monster) && !monster.isDeadOrEscaped()) {
                            addCommandIfNew(commands, seenCommands, new CardCommand(i, j, card.cardID));
                        }
                    }
                }

                if (card.target == AbstractCard.CardTarget.ALL_ENEMY || card.target == AbstractCard.CardTarget.ALL) {
                    if (card.canUse(player, null)) {
                        addCommandIfNew(commands, seenCommands, new CardCommand(i, card.cardID));
                    }
                }

                if (card.target == AbstractCard.CardTarget.SELF || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY || card.target == AbstractCard.CardTarget.NONE) {
                    if (card.canUse(player, null)) {
                        addCommandIfNew(commands, seenCommands, new CardCommand(i, card.cardID));
                    }
                }
            }

            for (int i = 0; i < potions.size(); i++) {
                AbstractPotion potion = potions.get(i);
                if (!potion
                        .canUse() || !potion.isObtained || potion instanceof PotionSlot || PotionState.UNPLAYABLE_POTIONS
                        .contains(potion.ID)) {
                    continue;
                }

                // Note: no dedupe here. seenCommands is fresh per call, and
                // the old display-name dedupe made a second same-named potion
                // (e.g. two Strength Potions) permanently unplayable
                if (potion.targetRequired) {
                    for (int j = 0; j < monsters.size(); j++) {
                        AbstractMonster monster = monsters.get(j);
                        if (!monster.isDeadOrEscaped()) {
                            commands.add(new PotionCommand(i, j));
                        }
                    }
                } else {
                    commands.add(new PotionCommand(i));
                }
            }
        }

        if (isInHandSelect()) {
            if (AbstractDungeon.handCardSelectScreen.selectedCards.group
                    .size() < AbstractDungeon.handCardSelectScreen.numCardsToSelect) {

                ArrayList<Integer> orderedIndeces = new ArrayList<>();

                Comparator<AbstractCard> heuristic = actionHeuristics
                        .get(AbstractDungeon.actionManager.currentAction.getClass());
                if (heuristic != null) {
                    HashMap<Integer, AbstractCard> indexToCardMap = new HashMap<>();

                    for (int i = 0; i < AbstractDungeon.player.hand.group.size(); i++) {
                        indexToCardMap.put(i, AbstractDungeon.player.hand.group.get(i));
                    }

                    indexToCardMap.entrySet().stream().sorted((e1, e2) -> {
                        int compValue = heuristic.compare(e1.getValue(), e2.getValue());
                        if (compValue == 0) {
                            return e1.getKey() - e2.getKey();
                        }
                        return compValue;
                    }).forEach(entry -> orderedIndeces.add(entry.getKey()));

                } else {
                    for (int i = 0; i < AbstractDungeon.player.hand.group.size(); i++) {
                        orderedIndeces.add(i);
                    }
                }

                orderedIndeces.forEach(index -> commands.add(new HandSelectCommand(index)));
            }

            if (isHandSelectConfirmButtonEnabled()) {
                commands.add(HandSelectConfirmCommand.INSTANCE);
            }
        }

        if (isInGridSelect()) {
            for (int i = 0; i < AbstractDungeon.gridSelectScreen.targetGroup.size(); i++) {
                AbstractCard card = AbstractDungeon.gridSelectScreen.targetGroup.group.get(i);
                if (!card.isGlowing) {
                    // Weak hack to only scry basics curses and statuses
                    boolean canClick = true;

                    if (AbstractDungeon.actionManager.currentAction instanceof ScryAction) {
                        canClick = false;
                        if (card.type == AbstractCard.CardType.STATUS) {
                            if (!card.cardID.equals(Dazed.ID) && !card.cardID.equals(VoidCard.ID)) {
                                canClick = true;
                            }
                        }

                        if (card.type == AbstractCard.CardType.CURSE) {
                            if (!card.cardID.equals(Clumsy.ID)) {
                                canClick = true;
                            }
                        }

                        if (card.hasTag(AbstractCard.CardTags.STARTER_DEFEND) || card
                                .hasTag(AbstractCard.CardTags.STARTER_STRIKE)) {
                            canClick = true;
                        }
                    }


                    if (canClick) {
                        commands.add(new GridSelectCommand(i));
                    }
                }
            }

            if (isGridScreenConfirmAvailable()) {
                commands.add(GridSelectConfirmCommand.INSTANCE);
            }
        }

        if (isInCardRewardSelect()) {
            for (int i = 0; i < AbstractDungeon.cardRewardScreen.rewardGroup.size(); i++) {
                commands.add(new CardRewardSelectCommand(i));
            }
        }


        if (isEndCommandAvailable()) {
            commands.add(new EndCommand());
        }

        return commands;
    }

    private static boolean shouldCheckForPlays() {
        return isInDungeon() &&
                !(AbstractDungeon.player.isDead || AbstractDungeon.player.isDying) &&
                (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT &&
                        !AbstractDungeon.isScreenUp &&
                        (AbstractDungeon.actionManager.currentAction == null && AbstractDungeon.actionManager.actions
                                .isEmpty()));
    }

    private static boolean isInDungeon() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY && AbstractDungeon
                .isPlayerInDungeon() && AbstractDungeon.currMapNode != null;
    }

    private static boolean isEndCommandAvailable() {
        // Same guards as shouldCheckForPlays: alive player, in combat, no
        // screen up, and no actions in flight
        return shouldCheckForPlays();
    }

    private static boolean isInGridSelect() {
        return isInDungeon() &&
                AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT &&
                AbstractDungeon.isScreenUp &&
                AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID;
    }

    private static boolean isInCardRewardSelect() {
        return isInDungeon() &&
                AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT &&
                AbstractDungeon.isScreenUp &&
                AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD;
    }

    private static boolean isInHandSelect() {
        return isInDungeon() &&
                AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT &&
                AbstractDungeon.isScreenUp &&
                AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT;
    }

    private static boolean isHandSelectConfirmButtonEnabled() {
        CardSelectConfirmButton button = AbstractDungeon.handCardSelectScreen.button;
        boolean isHidden = ReflectionHacks
                .getPrivate(button, CardSelectConfirmButton.class, "isHidden");
        boolean isDisabled = button.isDisabled;
        return !(isHidden || isDisabled);
    }

    private static boolean isGridScreenConfirmAvailable() {
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        if (screen.confirmScreenUp || screen.isJustForConfirming) {
            return true;
        } else if ((!screen.confirmButton.isDisabled) && (!(boolean) ReflectionHacks
                .getPrivate(screen.confirmButton, GridSelectConfirmButton.class, "isHidden"))) {
            return screen.forUpgrade || screen.forTransform || screen.forPurge || screen.anyNumber;
        }
        return false;
    }

    private static void addCommandIfNew(List<Command> commands, Set<String> seenCommands, Command command) {
        if (seenCommands.add(command.encode())) {
            commands.add(command);
        }
    }

    private CommandList() {
    }
}
