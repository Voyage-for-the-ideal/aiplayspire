package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class NoSkillsPower extends AbstractPower {
    public static final String POWER_ID = "NoSkills";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("NoSkills");

    public NoSkillsPower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "NoSkills";
        this.owner = owner;
        this.amount = 1;
        updateDescription();
        loadRegion("entangle");
        this.isTurnBased = true;
        this.type = PowerType.DEBUFF;
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_ENTANGLED", 0.05F);
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }

    public boolean canPlayCard(AbstractCard card) {
        return (card.type != AbstractCard.CardType.SKILL);
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (isPlayer)
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "NoSkills"));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * NoSkillsPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

