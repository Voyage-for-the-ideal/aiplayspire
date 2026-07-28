package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.stances.AbstractStance;

public class ChangeStanceAction
        extends AbstractGameAction {
    private String id;
    private AbstractStance newStance = null;

    public ChangeStanceAction(String stanceId) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.id = stanceId;
    }

    public ChangeStanceAction(AbstractStance newStance) {
        this(newStance.ID);
        this.newStance = newStance;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (AbstractDungeon.player.hasPower("CannotChangeStancePower")) {
                this.isDone = true;

                return;
            }
            AbstractStance oldStance = AbstractDungeon.player.stance;

            if (!oldStance.ID.equals(this.id)) {
                if (this.newStance == null) {
                    this.newStance = AbstractStance.getStanceFromName(this.id);
                }

                for (AbstractPower p : AbstractDungeon.player.powers) {
                    p.onChangeStance(oldStance, this.newStance);
                }

                for (AbstractRelic r : AbstractDungeon.player.relics) {
                    r.onChangeStance(oldStance, this.newStance);
                }

                oldStance.onExitStance();
                AbstractDungeon.player.stance = this.newStance;
                this.newStance.onEnterStance();

                if (AbstractDungeon.actionManager.uniqueStancesThisCombat.containsKey(this.newStance.ID)) {
                    int currentCount = ((Integer) AbstractDungeon.actionManager.uniqueStancesThisCombat
                            .get(this.newStance.ID)).intValue();
                    AbstractDungeon.actionManager.uniqueStancesThisCombat.put(this.newStance.ID,
                            Integer.valueOf(currentCount + 1));
                } else {
                    AbstractDungeon.actionManager.uniqueStancesThisCombat.put(this.newStance.ID, Integer.valueOf(1));
                }

                AbstractDungeon.player.switchedStance();

                for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
                    c.triggerExhaustedCardsOnStanceChange(this.newStance);
                }

                AbstractDungeon.player.onStanceChange(this.id);
            }

            AbstractDungeon.onModifyPower();

            if (Settings.FAST_MODE) {
                this.isDone = true;

                return;
            }
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * ChangeStanceAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



