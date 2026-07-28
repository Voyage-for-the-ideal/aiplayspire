package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.ChooseOneAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.optionCards.ChooseCalm;
import com.megacrit.cardcrawl.cards.optionCards.ChooseWrath;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;

import java.util.ArrayList;

public class StancePotion extends AbstractPotion {
    public static final String POTION_ID = "StancePotion";
    public static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("StancePotion");

    public StancePotion() {
        super(potionStrings.NAME, "StancePotion", PotionRarity.UNCOMMON, PotionSize.SPHERE, PotionColor.WEAK);
        this.labOutlineColor = Settings.PURPLE_RELIC_COLOR;
        this.description = potionStrings.DESCRIPTIONS[0];
        this.isThrown = false;
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        this.tips.add(new PowerTip(

                TipHelper.capitalize(GameDictionary.CALM.NAMES[0]), (String) GameDictionary.keywords
                        .get(GameDictionary.CALM.NAMES[0])));
        this.tips.add(new PowerTip(

                TipHelper.capitalize(GameDictionary.WRATH.NAMES[0]), (String) GameDictionary.keywords
                        .get(GameDictionary.WRATH.NAMES[0])));
    }

    public void use(AbstractCreature target) {
        InputHelper.moveCursorToNeutralPosition();
        ArrayList<AbstractCard> stanceChoices = new ArrayList<>();
        stanceChoices.add(new ChooseWrath());
        stanceChoices.add(new ChooseCalm());
        addToBot((AbstractGameAction) new ChooseOneAction(stanceChoices));
    }

    public int getPotency(int ascensionLevel) {
        return 0;
    }

    public AbstractPotion makeCopy() {
        return new StancePotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * StancePotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

