package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class FairyPotion extends AbstractPotion {
    public static final String POTION_ID = "FairyPotion";
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("FairyPotion");

    public FairyPotion() {
        super(potionStrings.NAME, "FairyPotion", PotionRarity.RARE, PotionSize.FAIRY, PotionColor.FAIRY);
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        float percent = this.potency / 100.0F;
        int healAmt = (int) (AbstractDungeon.player.maxHealth * percent);

        if (healAmt < 1) {
            healAmt = 1;
        }

        AbstractDungeon.player.heal(healAmt, true);
        AbstractDungeon.topPanel.destroyPotion(this.slot);
    }

    public boolean canUse() {
        return false;
    }

    public int getPotency(int ascensionLevel) {
        return 30;
    }

    public AbstractPotion makeCopy() {
        return new FairyPotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * FairyPotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

