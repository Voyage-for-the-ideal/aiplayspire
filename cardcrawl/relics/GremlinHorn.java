package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class GremlinHorn extends AbstractRelic {
    public GremlinHorn() {
        super("Gremlin Horn", "gremlin_horn.png", RelicTier.UNCOMMON, LandingSound.HEAVY);
        this.energyBased = true;
    }

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }
    public static final String ID = "Gremlin Horn";

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[0] + this.DESCRIPTIONS[1];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = setDescription(c);
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onMonsterDeath(AbstractMonster m) {
        if (m.currentHealth == 0 && !AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            flash();
            addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) m, this));
            addToBot((AbstractGameAction) new GainEnergyAction(1));
            addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) AbstractDungeon.player, 1));
        }
    }

    public AbstractRelic makeCopy() {
        return new GremlinHorn();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\GremlinHorn
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

