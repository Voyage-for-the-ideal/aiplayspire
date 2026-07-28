package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class BloodPotion
        extends AbstractPotion {
    public static final String POTION_ID = "BloodPotion";
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("BloodPotion");

    public BloodPotion() {
        super(potionStrings.NAME, "BloodPotion", PotionRarity.COMMON, PotionSize.H, PotionColor.WHITE);
        this.labOutlineColor = Settings.RED_RELIC_COLOR;
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            addToBot((AbstractGameAction) new HealAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player,
                    (int) (AbstractDungeon.player.maxHealth * this.potency / 100.0F)));

        } else {

            AbstractDungeon.player.heal((int) (AbstractDungeon.player.maxHealth * this.potency / 100.0F));
        }
    }

    public boolean canUse() {
        if (AbstractDungeon.actionManager.turnHasEnded &&
                (AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            return false;
        }
        if ((AbstractDungeon.getCurrRoom()).event != null &&
                (AbstractDungeon.getCurrRoom()).event instanceof com.megacrit.cardcrawl.events.shrines.WeMeetAgain) {
            return false;
        }

        return true;
    }

    public int getPotency(int ascensionLevel) {
        return 20;
    }

    public AbstractPotion makeCopy() {
        return new BloodPotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * BloodPotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

