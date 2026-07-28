package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.DexterityPower;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class DexterityPotion extends AbstractPotion {
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Dexterity Potion");

    public static final String POTION_ID = "Dexterity Potion";

    public DexterityPotion() {
        super(potionStrings.NAME, "Dexterity Potion", PotionRarity.COMMON, PotionSize.S, PotionColor.GREEN);
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        this.tips.add(new PowerTip(

                TipHelper.capitalize(GameDictionary.DEXTERITY.NAMES[0]), (String) GameDictionary.keywords
                        .get(GameDictionary.DEXTERITY.NAMES[0])));
    }

    public void use(AbstractCreature target) {
        AbstractPlayer abstractPlayer = AbstractDungeon.player;
        AbstractDungeon.effectList.add(new FlashAtkImgEffect(((AbstractCreature) abstractPlayer).hb.cX,
                ((AbstractCreature) abstractPlayer).hb.cY, AbstractGameAction.AttackEffect.SHIELD));
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) abstractPlayer,
                (AbstractCreature) AbstractDungeon.player,
                (AbstractPower) new DexterityPower((AbstractCreature) abstractPlayer, this.potency), this.potency));
    }

    public int getPotency(int ascensionLevel) {
        return 2;
    }

    public AbstractPotion makeCopy() {
        return new DexterityPotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * DexterityPotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

