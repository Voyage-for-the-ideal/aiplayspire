package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.MinionPower;
import com.megacrit.cardcrawl.powers.SlowPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class SpawnMonsterAction
        extends AbstractGameAction {
    private boolean used = false;
    private static final float DURATION = 0.1F;
    private AbstractMonster m;
    private boolean minion;
    private int targetSlot;
    private boolean useSmartPositioning;

    public SpawnMonsterAction(AbstractMonster m, boolean isMinion) {
        this(m, isMinion, -99);
        this.useSmartPositioning = true;
    }

    public SpawnMonsterAction(AbstractMonster m, boolean isMinion, int slot) {
        this.actionType = ActionType.SPECIAL;
        this.duration = 0.1F;
        this.m = m;
        this.minion = isMinion;
        this.targetSlot = slot;
        this.useSmartPositioning = false;
    }

    public void update() {
        if (!this.used) {

            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onSpawnMonster(this.m);
            }
            this.m.init();
            this.m.applyPowers();

            if (this.useSmartPositioning) {
                int position = 0;
                for (AbstractMonster mo : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                    if (this.m.drawX > mo.drawX) {
                        position++;
                    }
                }
                (AbstractDungeon.getCurrRoom()).monsters.addMonster(position, this.m);
            } else {
                (AbstractDungeon.getCurrRoom()).monsters.addMonster(this.targetSlot, this.m);
            }
            this.m.showHealthBar();
            if (ModHelper.isModEnabled("Lethality")) {
                addToBot(new ApplyPowerAction((AbstractCreature) this.m, (AbstractCreature) this.m,
                        (AbstractPower) new StrengthPower((AbstractCreature) this.m, 3), 3));
            }

            if (ModHelper.isModEnabled("Time Dilation")) {
                addToBot(new ApplyPowerAction((AbstractCreature) this.m, (AbstractCreature) this.m,
                        (AbstractPower) new SlowPower((AbstractCreature) this.m, 0)));
            }

            if (this.minion) {
                addToTop(new ApplyPowerAction((AbstractCreature) this.m, (AbstractCreature) this.m,
                        (AbstractPower) new MinionPower((AbstractCreature) this.m)));
            }
            this.used = true;
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * SpawnMonsterAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



