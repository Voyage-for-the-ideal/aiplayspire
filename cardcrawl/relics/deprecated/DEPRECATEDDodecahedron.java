package com.megacrit.cardcrawl.relics.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class DEPRECATEDDodecahedron extends AbstractRelic {
    public static final String ID = "Dodecahedron";
    private static final int ENERGY_AMT = 1;

    public DEPRECATEDDodecahedron() {
        super("Dodecahedron", "dodecahedron.png", RelicTier.UNCOMMON, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[0] + this.DESCRIPTIONS[1];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = setDescription(c);
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void atBattleStart() {
        controlPulse();
    }

    public void onVictory() {
        stopPulse();
    }

    public void atTurnStart() {
        addToBot(new AbstractGameAction() {
            public void update() {
                if (DEPRECATEDDodecahedron.this.isActive()) {
                    DEPRECATEDDodecahedron.this.flash();
                    addToBot((AbstractGameAction) new RelicAboveCreatureAction(
                            (AbstractCreature) AbstractDungeon.player, DEPRECATEDDodecahedron.this));
                    addToBot((AbstractGameAction) new GainEnergyAction(1));
                }
                this.isDone = true;
            }
        });
    }

    public int onPlayerHeal(int healAmount) {
        controlPulse();
        return super.onPlayerHeal(healAmount);
    }

    public int onAttacked(DamageInfo info, int damageAmount) {
        if (damageAmount > 0) {
            stopPulse();
        }
        return super.onAttacked(info, damageAmount);
    }

    public AbstractRelic makeCopy() {
        return new DEPRECATEDDodecahedron();
    }

    private boolean isActive() {
        return (AbstractDungeon.player.currentHealth >= AbstractDungeon.player.maxHealth);
    }

    private void controlPulse() {
        if (isActive()) {
            beginLongPulse();
        } else {
            stopPulse();
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\deprecated\
 * DEPRECATEDDodecahedron.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

