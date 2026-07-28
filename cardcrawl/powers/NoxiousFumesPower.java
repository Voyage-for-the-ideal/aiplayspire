package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class NoxiousFumesPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Noxious Fumes");
    public static final String POWER_ID = "Noxious Fumes";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public NoxiousFumesPower(AbstractCreature owner, int poisonAmount) {
        this.name = NAME;
        this.ID = "Noxious Fumes";
        this.owner = owner;
        this.amount = poisonAmount;
        updateDescription();
        loadRegion("fumes");
    }

    public void atStartOfTurnPostDraw() {
        if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            flash();
            for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                if (!m.isDead && !m.isDying) {
                    addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, this.owner,
                            new PoisonPower((AbstractCreature) m, this.owner, this.amount), this.amount));
                }
            }
        }
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * NoxiousFumesPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

