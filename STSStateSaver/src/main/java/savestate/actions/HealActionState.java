package savestate.actions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.HealAction;

public class HealActionState implements ActionState {
    private final int targetIndex;
    private final int sourceIndex;
    private final int amount;

    public HealActionState(AbstractGameAction action) {
        targetIndex = ActionState.indexOrMissing(action.target);
        sourceIndex = ActionState.indexOrMissing(action.source);
        this.amount = action.amount;
    }

    @Override
    public AbstractGameAction loadAction() {
        return new HealAction(ActionState.creatureForIndex(targetIndex), ActionState.creatureForIndex(sourceIndex), amount);
    }
}
