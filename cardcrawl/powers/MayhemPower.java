package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.PlayTopCardAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class MayhemPower extends AbstractPower {
    public static final String POWER_ID = "Mayhem";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Mayhem");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public MayhemPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Mayhem";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("mayhem");
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0];
        } else {
            this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        }
    }

    public void atStartOfTurn() {
        flash();
        for (int i = 0; i < this.amount; i++) {

            addToBot(new AbstractGameAction() {
                public void update() {
                    addToBot((AbstractGameAction) new PlayTopCardAction(

                            (AbstractCreature) (AbstractDungeon.getCurrRoom()).monsters.getRandomMonster(null, true,
                                    AbstractDungeon.cardRandomRng),
                            false));

                    this.isDone = true;
                }
            });
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\MayhemPower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

