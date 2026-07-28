package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.BlightStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.IntangiblePlayerPower;

public class Hauntings extends AbstractBlight {
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("GraspOfShadows");
    public static final String ID = "GraspOfShadows";
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;

    public Hauntings() {
        super("GraspOfShadows", NAME, DESC[0] + '\001' + DESC[1], "hauntings.png", false);
        this.counter = 1;
    }

    public void stack() {
        this.counter++;
        updateDescription();
        flash();
    }

    public void updateDescription() {
        this.description = DESC[0] + this.counter + DESC[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onCreateEnemy(AbstractMonster m) {
        AbstractDungeon.actionManager
                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) m,
                        (AbstractPower) new IntangiblePlayerPower((AbstractCreature) m, this.counter), this.counter));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\Hauntings.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



