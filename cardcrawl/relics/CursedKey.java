package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class CursedKey
        extends AbstractRelic {
    public CursedKey() {
        super("Cursed Key", "cursedKey.png", RelicTier.BOSS, LandingSound.CLINK);
    }
    public static final String ID = "Cursed Key";

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    public void justEnteredRoom(AbstractRoom room) {
        if (room instanceof com.megacrit.cardcrawl.rooms.TreasureRoom) {
            flash();
            this.pulse = true;
        } else {
            this.pulse = false;
        }
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[1] + this.DESCRIPTIONS[0];
    }

    public void onChestOpen(boolean bossChest) {
        if (!bossChest) {
            AbstractDungeon.topLevelEffects.add(new ShowCardAndObtainEffect(

                    AbstractDungeon.returnRandomCurse(), (Settings.WIDTH / 2), (Settings.HEIGHT / 2)));

            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
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

    public AbstractRelic makeCopy() {
        return new CursedKey();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\CursedKey.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

