package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class RedSkull
        extends AbstractRelic {
    public static final String ID = "Red Skull";

    public RedSkull() {
        super("Red Skull", "red_skull.png", RelicTier.COMMON, LandingSound.FLAT);
    }
    private static final int STR_AMT = 3;
    private boolean isActive = false;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\003' + this.DESCRIPTIONS[1];
    }

    public void atBattleStart() {
        this.isActive = false;
        addToBot(new AbstractGameAction() {
            public void update() {
                if (!RedSkull.this.isActive && AbstractDungeon.player.isBloodied) {
                    RedSkull.this.flash();
                    RedSkull.this.pulse = true;
                    AbstractDungeon.player
                            .addPower((AbstractPower) new StrengthPower((AbstractCreature) AbstractDungeon.player, 3));
                    addToTop((AbstractGameAction) new RelicAboveCreatureAction(
                            (AbstractCreature) AbstractDungeon.player, RedSkull.this));
                    RedSkull.this.isActive = true;
                    AbstractDungeon.onModifyPower();
                }
                this.isDone = true;
            }
        });
    }

    public void onBloodied() {
        flash();
        this.pulse = true;
        if (!this.isActive && (AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            AbstractPlayer p = AbstractDungeon.player;
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                    (AbstractPower) new StrengthPower((AbstractCreature) p, 3), 3));
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            this.isActive = true;
            AbstractDungeon.player.hand.applyPowers();
        }
    }

    public void onNotBloodied() {
        if (this.isActive && (AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            AbstractPlayer p = AbstractDungeon.player;
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                    (AbstractPower) new StrengthPower((AbstractCreature) p, -3), -3));
        }
        stopPulse();
        this.isActive = false;
        AbstractDungeon.player.hand.applyPowers();
    }

    public void onVictory() {
        this.pulse = false;
        this.isActive = false;
    }

    public AbstractRelic makeCopy() {
        return new RedSkull();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\RedSkull.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

