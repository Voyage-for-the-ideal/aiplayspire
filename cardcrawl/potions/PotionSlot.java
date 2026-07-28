package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class PotionSlot extends AbstractPotion {
    public static final String POTION_ID = "Potion Slot";
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Potion Slot");

    public PotionSlot(int slot) {
        super(potionStrings.NAME, "Potion Slot", PotionRarity.PLACEHOLDER, PotionSize.T, PotionColor.NONE);
        this.isObtained = true;
        this.description = potionStrings.DESCRIPTIONS[0];
        this.name = potionStrings.DESCRIPTIONS[1];
        this.tips.add(new PowerTip(this.name, this.description));
        adjustPosition(slot);
    }

    public void use(AbstractCreature target) {
    }

    public int getPotency(int ascensionLevel) {
        return 0;
    }

    public AbstractPotion makeCopy() {
        return null;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\PotionSlot
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

