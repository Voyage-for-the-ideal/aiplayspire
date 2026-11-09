package battleaimod.strategy;

import savestate.monsters.MonsterState;

/** Small internal extension point. Priorities change exploration order, never legality. */
public interface CharacterStrategy {
    void evaluate(StrategyContext context, MechanismScore score);
    int priority(StrategyContext context, StrategyContext.Card card, MonsterState target);
    int defaultRank(String cardId);
}
