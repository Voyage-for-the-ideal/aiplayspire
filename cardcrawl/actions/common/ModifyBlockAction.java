package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.GetAllInBattleInstances;

import java.util.UUID;

public class ModifyBlockAction
        extends AbstractGameAction {
    UUID uuid;

    public ModifyBlockAction(UUID targetUUID, int amount) {
        setValues(this.target, this.source, amount);
        this.actionType = ActionType.CARD_MANIPULATION;
        this.uuid = targetUUID;
    }

    public void update() {
        for (AbstractCard c : GetAllInBattleInstances.get(this.uuid)) {
            c.baseBlock += this.amount;
            if (c.baseDamage < 0) {
                c.baseDamage = 0;
            }
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ModifyBlockAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



