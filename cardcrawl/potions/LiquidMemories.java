package com.megacrit.cardcrawl.potions;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.BetterDiscardPileToHandAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class LiquidMemories extends AbstractPotion {
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("LiquidMemories");
    public static final String POTION_ID = "LiquidMemories";

    public LiquidMemories() {
        super(potionStrings.NAME, "LiquidMemories", PotionRarity.UNCOMMON, PotionSize.EYE, PotionEffect.NONE,
                new Color(225754111), new Color(389060095), null);

        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        if (this.potency == 1) {
            this.description = potionStrings.DESCRIPTIONS[0];
        } else {
            this.description = potionStrings.DESCRIPTIONS[1] + this.potency + potionStrings.DESCRIPTIONS[2];
        }
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        addToBot((AbstractGameAction) new BetterDiscardPileToHandAction(this.potency, 0));
    }

    public int getPotency(int ascensionLevel) {
        return 1;
    }

    public AbstractPotion makeCopy() {
        return new LiquidMemories();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * LiquidMemories.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

