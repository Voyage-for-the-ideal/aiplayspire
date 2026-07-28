package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.WeakPower;

public class WeakenPotion extends AbstractPotion {
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Weak Potion");

    public static final String POTION_ID = "Weak Potion";

    public WeakenPotion() {
        super(potionStrings.NAME, "Weak Potion", PotionRarity.COMMON, PotionSize.H, PotionColor.WEAK);
        this.isThrown = true;
        this.targetRequired = true;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        this.tips.add(new PowerTip(

                TipHelper.capitalize(GameDictionary.WEAK.NAMES[0]), (String) GameDictionary.keywords
                        .get(GameDictionary.WEAK.NAMES[0])));
    }

    public void use(AbstractCreature target) {
        addToBot((AbstractGameAction) new ApplyPowerAction(target, (AbstractCreature) AbstractDungeon.player,
                (AbstractPower) new WeakPower(target, this.potency, false), this.potency));
    }

    public int getPotency(int ascensionLevel) {
        return 3;
    }

    public AbstractPotion makeCopy() {
        return new WeakenPotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * WeakenPotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

