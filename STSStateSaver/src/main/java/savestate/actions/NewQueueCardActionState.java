package savestate.actions;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.NewQueueCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import savestate.CardState;

public class NewQueueCardActionState implements ActionState {
    private final CardState cardState;
    private final Integer targetIndex;
    private final boolean randomTarget;
    private final boolean immediateCard;
    private final boolean autoplayCard;

    public NewQueueCardActionState(AbstractGameAction action) {
        NewQueueCardAction queueAction = (NewQueueCardAction) action;
        AbstractCard card = ReflectionHacks.getPrivate(queueAction, NewQueueCardAction.class, "card");
        this.cardState = card == null ? null : new CardState(card);
        this.targetIndex = ActionState.indexForCreature(queueAction.target);
        this.randomTarget = ReflectionHacks.getPrivate(queueAction, NewQueueCardAction.class, "randomTarget");
        this.immediateCard = ReflectionHacks.getPrivate(queueAction, NewQueueCardAction.class, "immediateCard");
        this.autoplayCard = ReflectionHacks.getPrivate(queueAction, NewQueueCardAction.class, "autoplayCard");
    }

    @Override
    public AbstractGameAction loadAction() {
        if (cardState == null) {
            return new NewQueueCardAction();
        }

        AbstractCard card = cardState.loadCard();
        if (randomTarget) {
            return new NewQueueCardAction(card, true, immediateCard, autoplayCard);
        }

        AbstractCreature target = targetIndex == null ? null : ActionState.creatureForIndex(targetIndex);
        return new NewQueueCardAction(card, target, immediateCard, autoplayCard);
    }
}
