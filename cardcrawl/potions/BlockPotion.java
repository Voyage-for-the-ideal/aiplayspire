package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class BlockPotion extends AbstractPotion {
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Block Potion");

    public static final String POTION_ID = "Block Potion";

    public BlockPotion() {
        super(potionStrings.NAME, "Block Potion", PotionRarity.COMMON, PotionSize.S, PotionColor.BLUE);
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        this.tips.add(new PowerTip(

                TipHelper.capitalize(GameDictionary.BLOCK.NAMES[0]), (String) GameDictionary.keywords
                        .get(GameDictionary.BLOCK.NAMES[0])));
    }

    public void use(AbstractCreature target) {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player, this.potency));
    }

    public int getPotency(int ascensionLevel) {
        return 12;
    }

    public AbstractPotion makeCopy() {
        return new BlockPotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * BlockPotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

