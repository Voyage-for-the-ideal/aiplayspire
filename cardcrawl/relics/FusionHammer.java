package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import com.megacrit.cardcrawl.ui.campfire.SmithOption;

public class FusionHammer extends AbstractRelic {
    public static final String ID = "Fusion Hammer";

    public FusionHammer() {
        super("Fusion Hammer", "burnHammer.png", RelicTier.BOSS, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[1] + this.DESCRIPTIONS[0];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = setDescription(c);
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onEquip() {
        AbstractDungeon.player.energy.energyMaster++;
    }

    public void onUnequip() {
        AbstractDungeon.player.energy.energyMaster--;
    }

    public boolean canUseCampfireOption(AbstractCampfireOption option) {
        if (option instanceof SmithOption && option.getClass().getName().equals(SmithOption.class.getName())) {
            ((SmithOption) option).updateUsability(false);
            return false;
        }
        return true;
    }

    public AbstractRelic makeCopy() {
        return new FusionHammer();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * FusionHammer.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

