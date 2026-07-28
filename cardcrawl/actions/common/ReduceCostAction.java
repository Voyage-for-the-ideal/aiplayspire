package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.GetAllInBattleInstances;

import java.util.UUID;

public class ReduceCostAction
        extends AbstractGameAction {
    UUID uuid;
    private AbstractCard card = null;

    public ReduceCostAction(AbstractCard card) {
        this.card = card;
    }

    public ReduceCostAction(UUID targetUUID, int amount) {
        this.uuid = targetUUID;
        this.amount = amount;
        this.duration = Settings.ACTION_DUR_XFAST;
    }

    public void update() {
        if (this.card == null) {
            for (AbstractCard c : GetAllInBattleInstances.get(this.uuid)) {
                c.modifyCostForCombat(-1);
            }
        } else {
            this.card.modifyCostForCombat(-1);
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ReduceCostAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



