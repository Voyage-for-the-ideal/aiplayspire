package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.PoisonLoseHpAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class PoisonPower
        extends AbstractPower {
    public static final String POWER_ID = "Poison";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Poison");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    private AbstractCreature source;

    public PoisonPower(AbstractCreature owner, AbstractCreature source, int poisonAmt) {
        this.name = NAME;
        this.ID = "Poison";
        this.owner = owner;
        this.source = source;
        this.amount = poisonAmt;

        if (this.amount >= 9999) {
            this.amount = 9999;
        }

        updateDescription();
        loadRegion("poison");
        this.type = PowerType.DEBUFF;

        this.isTurnBased = true;
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_POISON", 0.05F);
    }

    public void updateDescription() {
        if (this.owner == null || this.owner.isPlayer) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[2] + this.amount + DESCRIPTIONS[1];
        }
    }

    public void stackPower(int stackAmount) {
        super.stackPower(stackAmount);

        if (this.amount > 98 && AbstractDungeon.player.chosenClass == AbstractPlayer.PlayerClass.THE_SILENT) {
            UnlockTracker.unlockAchievement("CATALYST");
        }
    }

    public void atStartOfTurn() {
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT &&
                !AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            flashWithoutSound();
            addToBot((AbstractGameAction) new PoisonLoseHpAction(this.owner, this.source, this.amount,
                    AbstractGameAction.AttackEffect.POISON));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\PoisonPower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

