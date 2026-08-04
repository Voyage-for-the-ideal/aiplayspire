package battleaimod.battleai;

import FightPredictor.FightPredictor;
import FightPredictor.ml.ModelUtils;
import FightPredictor.patches.com.megacrit.cardcrawl.combat.CombatPredictionPatches;
import FightPredictor.util.BaseGameConstants;
import basemod.BaseMod;
import battleaimod.ValueFunctions;
import battleaimod.search.SearchBudget;
import battleaimod.search.SearchMetrics;
import battleaimod.search.SearchProfile;
import battleaimod.search.SearchStateKey;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import ludicrousspeed.Controller;
import ludicrousspeed.simulator.commands.Command;
import savestate.CardState;
import savestate.SaveState;
import savestate.SaveStateMod;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static savestate.SaveStateMod.addRuntime;

public class BattleAiController implements Controller {
    private final int maxExpansions;
    private final long timeoutMillis;
    private SearchBudget searchBudget;
    private final Set<SearchStateKey> seenTurnStates = new HashSet<>();
    private final SearchMetrics searchMetrics = new SearchMetrics();
    private String stopReason = "RUNNING";
    private boolean battleComplete = false;
    private boolean shouldReplan = false;
    private final SearchProfile searchProfile;
    private long nextStreamingCommitExpansion;

    public int targetTurn;
    public int targetTurnJump;

    public PriorityQueue<TurnNode> turns = new PriorityQueue<>();

    // The best winning result unless the AI gave up in which case it will contain the chosen death
    // path
    public StateNode bestEnd;

    // If it doesn't work out just send back a path to kill the players so the game doesn't get
    // stuck.
    public StateNode deathNode = null;

    // The state the AI is currently processing from
    public TurnNode committedTurn = null;

    // The target turn that will be loaded if/when the max turn loads is hit
    public TurnNode bestTurn = null;
    public TurnNode backupTurn = null;

    public int startingHealth;
    public boolean isDone = false;
    public final SaveState startingState;
    private boolean initialized;

    // EXPERIMENTAL
    private TurnNode startNode = null;

    // The turn we're currently processing, if this is null then a turn will be polled from the
    // pqueue.
    public TurnNode curTurn;

    // The count of turns restricted by a turn limit.
    public int turnsLoaded = 0;

    private long startTime = 0;
    public int expectedDamage = 0;

    public BattleAiController(SaveState state, int maxTurnLoads) {
        this(state, SearchProfile.BALANCED, maxTurnLoads, SearchProfile.BALANCED.timeoutMillis());
    }

    public BattleAiController(SaveState state, SearchProfile searchProfile, int maxExpansions,
                              long timeoutMillis) {
        SaveStateMod.runTimes = new HashMap<>();
        targetTurnJump = 6;
        targetTurn = state.turn + targetTurnJump;

        bestEnd = null;
        startingState = state;
        initialized = false;

        System.err.println("loading state from constructor");
        startingState.loadState();

        if (BaseMod.hasModID("FightPredictor:")) {
            try {
                float prediction = FightPredictor.model.predict(ModelUtils.getBaseInputVector());
                expectedDamage = MathUtils.round(prediction * 100);
            } catch (Exception e) {
                // This can happen either because the model doesn't support something or the mod isn't
                // installed
            }

            runPredictions();
        }

        this.searchProfile = searchProfile;
        this.maxExpansions = Math.max(1, maxExpansions);
        this.timeoutMillis = Math.max(1L, timeoutMillis);
    }

    public void runPredictions() {
        float prediction = FightPredictor.model.predict(ModelUtils.getBaseInputVector());
        float intPrediction = Math.round(prediction * 100);
        CombatPredictionPatches.combatStartingHP = AbstractDungeon.player.currentHealth;
        CombatPredictionPatches.combatHPLossPrediction = MathUtils.round(prediction * 100);

        // Set up and do all card copying here
        // Only use thread safe things inside the other thread (maybe convert as much stuff to strings as possible)
        // That might mean overloading other methods from other classes to take strings, since they use them under
        // the hood anyways

        long start = System.currentTimeMillis();

        // Get the character's card pool
        ArrayList<AbstractCard> unupgradedCards = new ArrayList<>();
        switch (AbstractDungeon.player.chosenClass) {
            case IRONCLAD:
                CardLibrary.addRedCards(unupgradedCards);
                break;
            case THE_SILENT:
                CardLibrary.addGreenCards(unupgradedCards);
                break;
            case DEFECT:
                CardLibrary.addBlueCards(unupgradedCards);
                break;
            case WATCHER:
                CardLibrary.addPurpleCards(unupgradedCards);
                break;
            default:
                return;
        }

        // Make copies of cards to protect from concurency problems
        // Add the upgraded cards to the pool
        List<AbstractCard> cardPool = unupgradedCards.stream().map(AbstractCard::makeCopy)
                                                     .collect(Collectors.toList());
        List<AbstractCard> upgradedPool = cardPool.stream().map(AbstractCard::makeCopy)
                                                  .collect(Collectors.toList());
        upgradedPool.forEach(AbstractCard::upgrade);
        cardPool.addAll(upgradedPool);

        List<AbstractCard> playerCards = new ArrayList<>(AbstractDungeon.player.masterDeck.group)
                .stream().map(AbstractCard::makeCopy).collect(Collectors.toList());
        List<AbstractRelic> playerRelics = new ArrayList<>(AbstractDungeon.player.relics).stream()
                                                                                         .map(AbstractRelic::makeCopy)
                                                                                         .collect(Collectors
                                                                                                 .toList());
        int startingHealth = AbstractDungeon.player.currentHealth;
        int maxHealth = AbstractDungeon.player.maxHealth;

        // Get the enemies to predict against
        Set<String> elitesAndBosses = new HashSet<>();
        elitesAndBosses.addAll(BaseGameConstants.eliteIDs.get(AbstractDungeon.actNum));
        elitesAndBosses.add(AbstractDungeon.bossKey);
        if (AbstractDungeon.actNum < 4) {
            elitesAndBosses
                    .addAll(BaseGameConstants.elitesAndBossesByAct.get(AbstractDungeon.actNum + 1));
        }
        long end = System.currentTimeMillis();

        new Thread(() -> {
            FightPredictor
                    .getPercentiles(cardPool, playerCards, playerRelics, startingHealth, maxHealth, elitesAndBosses);

            System.err.println(" predictions " + FightPredictor.percentiles.entrySet());
        }).start();
    }

    public void step() {
        if (isDone) {
            return;
        }
        if (!initialized) {
            TurnNode.nodeIndex = 0;
            startTime = System.currentTimeMillis();
            initialized = true;
            searchBudget = new SearchBudget(maxExpansions, timeoutMillis);
            nextStreamingCommitExpansion = streamingCommitInterval();
            isDone = false;
            StateNode firstStateContainer = new StateNode(null, null, this);
            startingHealth = startingState.getPlayerHealth();
            firstStateContainer.saveState = startingState;
            turns = new PriorityQueue<>();
            startNode = new TurnNode(firstStateContainer, this, null);
            turns.add(startNode);
            registerTurnState(startingState);
            updateQueueMetrics();

            SaveStateMod.runTimes = new HashMap<>();
            CardState.resetFreeCards();
        }

        if (finishWhenBudgetReached()) {
            return;
        }

        if (curTurn == null || curTurn.isDone) {
            if (turns.isEmpty() || shouldCommitStreamingStage()) {
                if (bestEnd != null) {
                    System.err.println("Found end at turn threshold, going into rerun");
                    printRuntimeStats();

                    isDone = true;
                    battleComplete = true;
                    stopReason = "VICTORY";
                    return;
                } else if (bestTurn != null || backupTurn != null) {
                    if (bestTurn == null) {
                        System.err.println("Loading for backup " + backupTurn);
                        bestTurn = backupTurn;
                    }
                    System.err.println("Loading for turn load threshold, best turn: " + bestTurn);
                    turnsLoaded = 0;
                    turns.clear();

                    bestTurn = selectTurnToCommit(bestTurn);

                    System.err.println("Backstepping to turn: " + bestTurn);

                    TurnNode toAdd = makeResetCopy(bestTurn);
                    turns.add(toAdd);
                    updateQueueMetrics();
                    targetTurn = bestTurn.startingState.saveState.turn + targetTurnJump;
                    toAdd.startingState.saveState.loadState();
                    committedTurn = toAdd;
                    nextStreamingCommitExpansion += streamingCommitInterval();
                    bestTurn = null;
                    backupTurn = null;

                    return;
                } else if (turns.isEmpty()) {
                    System.err.println("No safe path found, using start state as fallback");
                    bestEnd = committedTurn == null ? startNode.startingState
                            : committedTurn.startingState;
                    isDone = true;
                    battleComplete = false;
                    shouldReplan = hasProgress(bestEnd);
                    stopReason = "SEARCH_EXHAUSTED";
                    printRuntimeStats();
                    return;
                }
            }
        }


        while (!turns.isEmpty() && (curTurn == null || curTurn.isDone)) {
            curTurn = turns.peek();

            int turnNumber = curTurn.startingState.saveState.turn;

            if (turnNumber >= targetTurn) {
                if (bestTurn == null || curTurn.isBetterThan(bestTurn)) {
                    bestTurn = curTurn;
                }

                addRuntime("turnsLoaded", 1);
                curTurn = null;
                ++turnsLoaded;
                turns.poll();
            } else {
                if (curTurn.isDone) {
                    turns.poll();
                }
            }
        }

        if (curTurn != null) {
            long startTurnStep = System.currentTimeMillis();

//            System.err.println("Stepping Turn " + curTurn.turnLabel);
            boolean reachedNewTurn = curTurn.step();
            if (reachedNewTurn) {
                curTurn = null;
            }

            addRuntime("Battle AI TurnNode Step", System.currentTimeMillis() - startTurnStep);
        }
    }

    private static TurnNode makeResetCopy(TurnNode node) {
        StateNode stateNode = new StateNode(node.startingState.parent, node.startingState.lastCommand, node.controller);
        stateNode.saveState = node.startingState.saveState;
        return new TurnNode(stateNode, node.controller, node.parent);
    }

    public static List<StateNode> stateNodesToGetToNode(StateNode endNode) {
        ArrayList<StateNode> result = new ArrayList<>();
        StateNode iterator = endNode;
        while (iterator != null) {
            result.add(0, iterator);
            iterator = iterator.parent;
        }

        return result;
    }

    public void printRuntimeStats() {
        System.err.println("-------------------------------------------------------------------");
        System.err.println("total time: " + (System.currentTimeMillis() - startTime));
        System.err.println(SaveStateMod.runTimes.entrySet()
                                                .stream()
                                                .map(entry -> entry.toString())
                                                .sorted()
                                                .collect(Collectors.joining("\n")));
        System.err.println("search metrics: " + searchMetrics
                .jsonEncode(elapsedMillis(), stopReason));
        System.err.println("-------------------------------------------------------------------");
    }

    public boolean isDone() {
        return isDone;
    }

    public TurnNode committedTurn() {
        return committedTurn;
    }

    public int turnsLoaded() {
        return turnsLoaded;
    }

    public int maxTurnLoads() {
        return maxExpansions();
    }

    public int maxExpansions() {
        return maxExpansions;
    }

    public void recordExpansion() {
        searchMetrics.expandedNodes++;
    }

    public boolean registerTurnState(SaveState state) {
        searchMetrics.generatedTurnStates++;
        searchMetrics.deepestTurn = Math.max(searchMetrics.deepestTurn, state.turn);
        long startedAt = System.nanoTime();
        SearchStateKey key = SearchStateKey.fromSaveState(state);
        searchMetrics.stateKeyNanos += System.nanoTime() - startedAt;
        if (!seenTurnStates.add(key)) {
            searchMetrics.duplicateTurnStates++;
            return false;
        }
        searchMetrics.uniqueTurnStates++;
        return true;
    }

    public void updateQueueMetrics() {
        searchMetrics.maxQueueSize = Math.max(searchMetrics.maxQueueSize, turns.size());
    }

    public SearchMetrics metrics() {
        return searchMetrics;
    }

    public long elapsedMillis() {
        return searchBudget == null ? 0L : searchBudget.elapsedMillis();
    }

    public String stopReason() {
        return stopReason;
    }

    public boolean battleComplete() {
        return battleComplete;
    }

    public boolean shouldReplan() {
        return shouldReplan;
    }

    public SearchProfile searchProfile() {
        return searchProfile;
    }

    private boolean finishWhenBudgetReached() {
        boolean expansionLimit = searchBudget.isExpansionLimitReached(searchMetrics.expandedNodes);
        boolean timedOut = searchBudget.isTimedOut();
        if (!expansionLimit && !timedOut) {
            return false;
        }

        if (bestEnd != null) {
            battleComplete = true;
            stopReason = "VICTORY";
        } else {
            TurnNode partialResult = bestTurn != null ? bestTurn
                    : (backupTurn != null ? backupTurn : committedTurn);
            if (partialResult == null) {
                bestEnd = startNode.startingState;
            } else {
                int committedThroughTurn = searchProfile.streamCommands() && committedTurn != null
                        ? committedTurn.startingState.saveState.turn : startingState.turn;
                bestEnd = firstStateAfterTurn(partialResult.startingState, committedThroughTurn);
            }
            shouldReplan = hasProgress(bestEnd);
            stopReason = timedOut ? "TIMEOUT" : "EXPANSION_LIMIT";
        }

        isDone = true;
        printRuntimeStats();
        return true;
    }

    private boolean shouldCommitStreamingStage() {
        return searchProfile.streamCommands() &&
                searchMetrics.expandedNodes >= nextStreamingCommitExpansion &&
                (bestTurn != null || backupTurn != null);
    }

    private long streamingCommitInterval() {
        return Math.max(1L, maxExpansions / 3L);
    }

    private TurnNode selectTurnToCommit(TurnNode candidate) {
        if (searchProfile.streamCommands()) {
            int committedThroughTurn = committedTurn == null ? startingState.turn
                    : committedTurn.startingState.saveState.turn;
            while (candidate.parent != null &&
                    candidate.parent.startingState.saveState.turn > committedThroughTurn) {
                candidate = candidate.parent;
            }
            return candidate;
        }

        int backStep = targetTurnJump / 2;
        TurnNode backStepTurn = candidate;
        for (int i = 0; i < backStep && backStepTurn != null; i++) {
            backStepTurn = backStepTurn.parent;
        }
        if (backStepTurn != null && (committedTurn == null ||
                backStepTurn.startingState.saveState.turn >
                        committedTurn.startingState.saveState.turn)) {
            return backStepTurn;
        }
        return candidate;
    }

    private static StateNode firstStateAfterTurn(StateNode endNode, int completedTurn) {
        StateNode result = endNode;
        for (StateNode stateNode : stateNodesToGetToNode(endNode)) {
            if (stateNode.saveState != null && stateNode.saveState.turn > completedTurn) {
                return stateNode;
            }
        }
        return result;
    }

    private static boolean hasProgress(StateNode stateNode) {
        return stateNode != null && stateNode.parent != null;
    }


}
