package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.DiscoveryAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class SkillPotion extends AbstractPotion {
    public static final String POTION_ID = "SkillPotion";
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("SkillPotion");

    public SkillPotion() {
        super(potionStrings.NAME, "SkillPotion", PotionRarity.COMMON, PotionSize.CARD, PotionColor.GREEN);
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        if (AbstractDungeon.player == null || !AbstractDungeon.player.hasRelic("SacredBark")) {
            this.description = potionStrings.DESCRIPTIONS[0];
        } else {
            this.description = potionStrings.DESCRIPTIONS[1];
        }
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        addToBot((AbstractGameAction) new DiscoveryAction(AbstractCard.CardType.SKILL, this.potency));
    }

    public int getPotency(int ascensionLevel) {
        return 1;
    }

    public AbstractPotion makeCopy() {
        return new SkillPotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * SkillPotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

