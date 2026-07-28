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
import com.megacrit.cardcrawl.powers.ArtifactPower;
import com.megacrit.cardcrawl.powers.PlatedArmorPower;
import com.megacrit.cardcrawl.powers.RegenerateMonsterPower;

public class AncientAugmentation extends AbstractBlight {
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("MetallicRebirth");
    public static final String ID = "MetallicRebirth";
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;

    public AncientAugmentation() {
        super("MetallicRebirth", NAME, DESC[0] + '\001' + DESC[1] + '\n' + DESC[2] + '\n' + DESC[3], "ancient.png",
                false);
        this.counter = 1;
    }

    public void stack() {
        this.counter++;
        updateDescription();
        flash();
    }

    public void updateDescription() {
        this.description = DESC[0] + this.counter + DESC[1] + (this.counter * 10) + DESC[2] + (this.counter * 10)
                + DESC[3];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onCreateEnemy(AbstractMonster m) {
        AbstractDungeon.actionManager
                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) m,
                        (AbstractPower) new ArtifactPower((AbstractCreature) m, this.counter), this.counter));
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m,
                (AbstractCreature) m, (AbstractPower) new PlatedArmorPower((AbstractCreature) m, this.counter * 10),
                this.counter * 10));

        AbstractDungeon.actionManager
                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) m,
                        (AbstractPower) new RegenerateMonsterPower(m, this.counter * 10), this.counter * 10));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\
 * AncientAugmentation.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



