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
import com.megacrit.cardcrawl.powers.LoseDexterityPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class SpeedPotion extends AbstractPotion {
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("SpeedPotion");

    public static final String POTION_ID = "SpeedPotion";

    public SpeedPotion() {
        super(potionStrings.NAME, "SpeedPotion", PotionRarity.COMMON, PotionSize.BOLT, PotionColor.SKILL);
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1] + this.potency
                + potionStrings.DESCRIPTIONS[2];

        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        this.tips.add(new PowerTip(

                TipHelper.capitalize(GameDictionary.DEXTERITY.NAMES[0]), (String) GameDictionary.keywords
                        .get(GameDictionary.DEXTERITY.NAMES[0])));
    }

    public void use(AbstractCreature target) {
        AbstractPlayer abstractPlayer = AbstractDungeon.player;
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) abstractPlayer,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new DexterityPower((AbstractCreature) abstractPlayer, this.potency), this.potency));

            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) abstractPlayer,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new LoseDexterityPower((AbstractCreature) abstractPlayer, this.potency),
                    this.potency));
        }
    }

    public int getPotency(int ascensionLevel) {
        return 5;
    }

    public AbstractPotion makeCopy() {
        return new SpeedPotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * SpeedPotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

