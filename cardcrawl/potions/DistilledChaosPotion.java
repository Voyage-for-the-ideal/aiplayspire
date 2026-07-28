package com.megacrit.cardcrawl.potions;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.PlayTopCardAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class DistilledChaosPotion extends AbstractPotion {
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("DistilledChaos");
    public static final String POTION_ID = "DistilledChaos";

    public DistilledChaosPotion() {
        super(potionStrings.NAME, "DistilledChaos", PotionRarity.UNCOMMON, PotionSize.T, PotionEffect.RAINBOW,
                Color.WHITE, null, null);

        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        for (int i = 0; i < this.potency; i++) {
            addToBot((AbstractGameAction) new PlayTopCardAction(

                    (AbstractCreature) (AbstractDungeon.getCurrRoom()).monsters.getRandomMonster(null, true,
                            AbstractDungeon.cardRandomRng),
                    false));
        }
    }

    public int getPotency(int ascensionLevel) {
        return 3;
    }

    public AbstractPotion makeCopy() {
        return new DistilledChaosPotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * DistilledChaosPotion.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

