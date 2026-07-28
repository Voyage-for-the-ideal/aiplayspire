package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class DollysMirror extends AbstractRelic {
    public static final String ID = "DollysMirror";
    private boolean cardSelected = true;

    public DollysMirror() {
        super("DollysMirror", "mirror.png", RelicTier.SHOP, LandingSound.SOLID);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        this.cardSelected = false;
        if (AbstractDungeon.isScreenUp) {
            AbstractDungeon.dynamicBanner.hide();
            AbstractDungeon.overlayMenu.cancelButton.hide();
            AbstractDungeon.previousScreen = AbstractDungeon.screen;
        }
        (AbstractDungeon.getCurrRoom()).phase = AbstractRoom.RoomPhase.INCOMPLETE;

        AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.masterDeck, 1, this.DESCRIPTIONS[1], false, false,
                false, false);
    }

    public void update() {
        super.update();
        if (!this.cardSelected &&
                AbstractDungeon.gridSelectScreen.selectedCards.size() == 1) {
            this.cardSelected = true;

            AbstractCard c = ((AbstractCard) AbstractDungeon.gridSelectScreen.selectedCards.get(0))
                    .makeStatEquivalentCopy();
            c.inBottleFlame = false;
            c.inBottleLightning = false;
            c.inBottleTornado = false;
            AbstractDungeon.effectList
                    .add(new ShowCardAndObtainEffect(c, Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

            AbstractDungeon.gridSelectScreen.selectedCards.clear();

            (AbstractDungeon.getCurrRoom()).phase = AbstractRoom.RoomPhase.COMPLETE;
            AbstractDungeon.gridSelectScreen.selectedCards.clear();
        }
    }

    public AbstractRelic makeCopy() {
        return new DollysMirror();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * DollysMirror.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

