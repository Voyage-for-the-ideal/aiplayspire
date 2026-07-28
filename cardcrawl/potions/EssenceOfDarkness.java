package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.EssenceOfDarknessAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class EssenceOfDarkness extends AbstractPotion {
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("EssenceOfDarkness");
    public static final String POTION_ID = "EssenceOfDarkness";

    public EssenceOfDarkness() {
        super(potionStrings.NAME, "EssenceOfDarkness", PotionRarity.RARE, PotionSize.MOON, PotionColor.SMOKE);
        this.labOutlineColor = Settings.BLUE_RELIC_COLOR;
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        this.tips.add(new PowerTip(

                TipHelper.capitalize(GameDictionary.CHANNEL.NAMES[0]), (String) GameDictionary.keywords
                        .get(GameDictionary.CHANNEL.NAMES[0])));
        this.tips.add(new PowerTip(

                TipHelper.capitalize(GameDictionary.DARK.NAMES[0]), (String) GameDictionary.keywords
                        .get(GameDictionary.DARK.NAMES[0])));
    }

    public void use(AbstractCreature target) {
        AbstractPlayer abstractPlayer = AbstractDungeon.player;
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            addToBot((AbstractGameAction) new EssenceOfDarknessAction(this.potency));
        }
    }

    public int getPotency(int ascensionLevel) {
        return 1;
    }

    public AbstractPotion makeCopy() {
        return new EssenceOfDarkness();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * EssenceOfDarkness.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

