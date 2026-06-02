package savestate.actions;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ShuffleAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class ShuffleActionState  implements ActionState {
    private final boolean triggerRelics;

    public ShuffleActionState(AbstractGameAction action) {
        this.triggerRelics = ReflectionHacks.getPrivate(action, ShuffleAction.class, "triggerRelics");
    }

    @Override
    public AbstractGameAction loadAction() {
        return new ShuffleAction(AbstractDungeon.player.drawPile, triggerRelics);
    }
}
