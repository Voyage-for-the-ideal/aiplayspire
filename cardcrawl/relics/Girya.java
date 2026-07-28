package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import com.megacrit.cardcrawl.ui.campfire.LiftOption;

import java.util.ArrayList;

public class Girya extends AbstractRelic {
    public static final String ID = "Girya";

    public Girya() {
        super("Girya", "kettlebell.png", RelicTier.RARE, LandingSound.HEAVY);
        this.counter = 0;
    }
    public static final int STR_LIMIT = 3;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atBattleStart() {
        if (this.counter != 0) {
            flash();
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new StrengthPower((AbstractCreature) AbstractDungeon.player, this.counter),
                    this.counter));

            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
    }

    public boolean canSpawn() {
        if (AbstractDungeon.floorNum >= 48 && !Settings.isEndless) {
            return false;
        }
        int campfireRelicCount = 0;

        for (AbstractRelic r : AbstractDungeon.player.relics) {
            if (r instanceof PeacePipe || r instanceof Shovel || r instanceof Girya) {
                campfireRelicCount++;
            }
        }

        return (campfireRelicCount < 2);
    }

    public void addCampfireOption(ArrayList<AbstractCampfireOption> options) {
        options.add(new LiftOption((this.counter < 3)));
    }

    public AbstractRelic makeCopy() {
        return new Girya();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Girya.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

