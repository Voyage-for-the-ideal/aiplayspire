package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.ChannelAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Frost;

public class WinterPower extends AbstractPower {
    public static final String POWER_ID = "Winter";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Winter");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public WinterPower(AbstractCreature owner, int orbAmt) {
        this.name = NAME;
        this.ID = "Winter";
        this.owner = owner;
        this.amount = orbAmt;
        updateDescription();
        loadRegion("winter");
    }

    public void atStartOfTurn() {
        if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            for (AbstractOrb o : AbstractDungeon.player.orbs) {
                if (o instanceof com.megacrit.cardcrawl.orbs.EmptyOrbSlot) {
                    flash();

                    break;
                }
            }
            for (int i = 0; i < this.amount; i++) {
                addToBot((AbstractGameAction) new ChannelAction((AbstractOrb) new Frost(), false));
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
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\WinterPower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

