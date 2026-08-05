package savestate.actions;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import savestate.CardState;
import savestate.fastobjects.actions.UpdateOnlyUseCardAction;

public class UseCardActionState implements ActionState {
    private final CardState card;
    private final boolean exhaustCard;
    private final Integer targetIndex;

    public UseCardActionState(UseCardAction action) {
        AbstractCard card = ReflectionHacks.getPrivate(action, UseCardAction.class, "targetCard");

        this.card = CardState.forCard(card);
        this.exhaustCard = action.exhaustCard;
        this.targetIndex = ActionState.indexForCreature(action.target);
    }

    public UseCardActionState(UpdateOnlyUseCardAction action) {
        AbstractCard card = ReflectionHacks
                .getPrivate(action, UpdateOnlyUseCardAction.class, "targetCard");

        this.card = CardState.forCard(card);
        this.exhaustCard = action.exhaustCard;
        this.targetIndex = ActionState.indexForCreature(action.target);
    }

    @Override
    public UpdateOnlyUseCardAction loadAction() {
        AbstractCard resultCard = card.loadCardReference();
        AbstractCreature target = ActionState
                .creatureForIndex(targetIndex == null ? ActionState.NULL_INDEX : targetIndex);

        UpdateOnlyUseCardAction result = new UpdateOnlyUseCardAction(resultCard, target);

        result.exhaustCard = exhaustCard;

        return result;
    }
}
