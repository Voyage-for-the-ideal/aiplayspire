package com.megacrit.cardcrawl.actions.unique;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class RegenAction
        extends AbstractGameAction {
    public RegenAction(AbstractCreature target, int amount) {
        this.target = target;
        this.amount = amount;
        this.actionType = ActionType.DAMAGE;
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public void update() {
        if ((AbstractDungeon.getCurrRoom()).phase != AbstractRoom.RoomPhase.COMBAT) {
            this.isDone = true;

            return;
        }
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (this.target.currentHealth > 0) {
                this.target.tint.color = Color.CHARTREUSE.cpy();
                this.target.tint.changeColor(Color.WHITE.cpy());
                this.target.heal(this.amount, true);
            }

            if (this.target.isPlayer) {
                AbstractPower p = this.target.getPower("Regeneration");
                if (p != null) {
                    p.amount--;
                    if (p.amount == 0) {
                        this.target.powers.remove(p);
                    } else {
                        p.updateDescription();
                    }
                }
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * RegenAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



