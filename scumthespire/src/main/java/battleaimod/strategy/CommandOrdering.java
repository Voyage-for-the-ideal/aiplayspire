package battleaimod.strategy;

import battleaimod.BattleAiMod;
import battleaimod.evaluation.CombatFeatures;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.simulator.commands.*;
import savestate.SaveState;
import savestate.monsters.MonsterState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Decorate/sort/undecorate once per node. No simulation or JSON inside comparators. */
public final class CommandOrdering {
    private CommandOrdering() {}
    public enum Selection { NONE, DISCARD, EXHAUST, SCRY, SEEK, RETAIN }

    public static Selection selection(String action) {
        switch (action) {
            case "DiscardAction": case "ConcentrateAction": case "CalculatedGambleAction":
            case "DaggerThrowAction": case "PreparedAction": return Selection.DISCARD;
            case "ExhaustAction": case "TrueGritAction": case "RecycleAction":
            case "PurityAction": case "ExhaustSpecificCardAction": return Selection.EXHAUST;
            case "ScryAction": return Selection.SCRY;
            case "SeekAction": case "HologramAction": case "HeadbuttAction":
            case "MeditateAction": return Selection.SEEK;
            case "RetainCardsAction": return Selection.RETAIN;
            default: return Selection.NONE;
        }
    }

    public static List<Command> enumerate(SaveState state) {
        StrategyContext context = new StrategyContext(state,
                CombatFeatures.extract(state, null, state.getPlayerHealth()));
        String action = AbstractDungeon.actionManager.currentAction == null ? ""
                : AbstractDungeon.actionManager.currentAction.getClass().getSimpleName();
        Selection selection = selection(action);
        List<AbstractCard> liveHand = AbstractDungeon.player.hand.group;
        List<StrategyContext.Card> hand = snapshot(liveHand);
        List<StrategyContext.Card> grid = AbstractDungeon.isScreenUp
                && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID
                ? snapshot(AbstractDungeon.gridSelectScreen.targetGroup.group) : new ArrayList<>();

        // Override only a recognized screen's comparator, preserving external hooks elsewhere.
        HashMap<Class, Comparator<AbstractCard>> heuristics = new HashMap<>(BattleAiMod.actionHeuristics);
        if (selection != Selection.NONE && AbstractDungeon.actionManager.currentAction != null) {
            Map<AbstractCard, Integer> priorities = new IdentityHashMap<>();
            for (int i = 0; i < liveHand.size(); i++) priorities.put(liveHand.get(i), selectionPriority(context, hand.get(i), selection));
            heuristics.put(AbstractDungeon.actionManager.currentAction.getClass(),
                    (a, b) -> Integer.compare(priorities.getOrDefault(b, 0), priorities.getOrDefault(a, 0)));
        }
        List<Command> commands = CommandList.getAvailableCommands(null, heuristics);
        Map<Integer, Integer> extraRanks = new HashMap<>();
        if (!BattleAiMod.cardPlayHeuristics.isEmpty()) {
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < liveHand.size(); i++) indices.add(i);
            indices.sort((a, b) -> {
                for (Comparator<AbstractCard> extension : BattleAiMod.cardPlayHeuristics) {
                    int result = extension.compare(liveHand.get(a), liveHand.get(b));
                    if (result != 0) return result;
                }
                return Integer.compare(a, b);
            });
            for (int i = 0; i < indices.size(); i++) extraRanks.put(indices.get(i), i);
        }
        return order(context, commands, hand, grid, selection, extraRanks);
    }

    public static List<Command> order(StrategyContext context, List<Command> commands,
                                      List<StrategyContext.Card> hand, List<StrategyContext.Card> grid,
                                      Selection selection) {
        return order(context, commands, hand, grid, selection, new HashMap<>());
    }

    private static List<Command> order(StrategyContext context, List<Command> commands,
                                      List<StrategyContext.Card> hand, List<StrategyContext.Card> grid,
                                      Selection selection, Map<Integer, Integer> extraRanks) {
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            int priority = 0, fallback = 0;
            if (command instanceof CardCommand) {
                CardCommand play = (CardCommand) command;
                StrategyContext.Card card = hand.get(play.cardIndex);
                MonsterState target = play.monsterIndex < 0 ? null : context.state.curMapNodeState.monsterData.get(play.monsterIndex);
                priority = playPriority(context, card, target);
                fallback = CharacterStrategies.forCard(context, card).defaultRank(card.id);
            } else if (selection != Selection.NONE && command instanceof HandSelectCommand) {
                priority = selectionPriority(context, hand.get(((HandSelectCommand) command).getCardIndex()), selection);
            } else if (selection != Selection.NONE && command instanceof GridSelectCommand) {
                priority = selectionPriority(context, grid.get(((GridSelectCommand) command).getCardIndex()), selection);
            } else if (selection == Selection.SCRY && command instanceof GridSelectConfirmCommand) {
                // Bad cards rank above confirm; useful cards below it. Zero-select remains reachable.
                priority = 0;
            } else if (command instanceof PotionCommand || command instanceof EndCommand) {
                // Normal cards first, then potions/end, then explicitly wasteful plays.
                priority = -50;
            }
            int extra = command instanceof CardCommand ? extraRanks.getOrDefault(((CardCommand) command).cardIndex, 0) : 0;
            ranked.add(new Ranked(command, priority, fallback, extra, i));
        }
        // Custom card heuristics remain a final tie-breaker. Only project-owned
        // tables have moved into the registry, so they cannot mask another class.
        ranked.sort((a, b) -> {
            int result = Integer.compare(b.priority, a.priority);
            if (result == 0) result = Integer.compare(a.fallback, b.fallback);
            if (result == 0) result = Integer.compare(a.extra, b.extra);
            return result == 0 ? Integer.compare(a.original, b.original) : result;
        });
        List<Command> result = new ArrayList<>();
        for (Ranked item : ranked) result.add(item.command);
        if (Boolean.getBoolean("battleai.debugEvaluation")) {
            System.err.println("strategy=" + context.state.playerState.chosenClass + " selection=" + selection);
            for (Ranked item : ranked) System.err.println("  priority=" + item.priority + " " + item.command);
        }
        return result;
    }

    private static List<StrategyContext.Card> snapshot(List<AbstractCard> source) {
        List<StrategyContext.Card> result = new ArrayList<>(source.size());
        for (AbstractCard card : source) result.add(StrategyContext.Card.from(card));
        return result;
    }

    public static int playPriority(StrategyContext c, StrategyContext.Card card, MonsterState target) {
        int priority = CharacterStrategies.forCard(c, card).priority(c, card, target);
        if (card.type == AbstractCard.CardType.POWER && priority == 0) priority = 20;
        if (card.cost == 0 && card.damage > 0) priority += 5;
        return priority;
    }

    /** Different objectives for discarding, exhausting, filtering, retrieving and retaining. */
    public static int selectionPriority(StrategyContext c, StrategyContext.Card card, Selection selection) {
        int value = cardValue(c, card);
        switch (selection) {
            case DISCARD:
                if ("Tactician".equals(card.id) || "Reflex".equals(card.id)) return 200;
                return -value;
            case EXHAUST:
                if ("Sentinel".equals(card.id)) return 200;
                // Preserve pieces that enable an engine or the only stance exit.
                return -value + (card.ethereal ? -10 : 0);
            case SCRY:
                // Cards are being discarded, not retrieved. Confirm beats any useful card.
                return card.unplayable() ? 200 : card.cost > c.energy && card.cost >= 0 ? 30 : -Math.max(1, value);
            case SEEK:
                return value + (card.cost >= 0 && card.cost <= c.energy ? 40 : -60);
            case RETAIN:
                return card.ethereal || card.retained ? -200 : value;
            default: return 0;
        }
    }

    private static int cardValue(StrategyContext c, StrategyContext.Card card) {
        if (card.unplayable()) return -100;
        if ("Reflex".equals(card.id) || "Tactician".equals(card.id)) return 5;
        int value = 10 + Math.max(0, card.damage) + Math.min(Math.max(0, card.block), c.features.currentIncomingDamage);
        value += Math.max(0, playPriority(c, card, null));
        if (CharacterStrategies.isStanceExit(card.id) && "Wrath".equals(c.stance)) value += 100;
        if (card.id.equals("Catalyst") && c.has("Deadly Poison")) value += 60;
        if (card.retained) value += 5;
        return value;
    }

    private static final class Ranked {
        final Command command;
        final int priority, fallback, extra, original;
        Ranked(Command command, int priority, int fallback, int extra, int original) {
            this.command = command; this.priority = priority; this.fallback = fallback; this.extra = extra; this.original = original;
        }
    }
}
