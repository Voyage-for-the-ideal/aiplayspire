package com.megacrit.cardcrawl.cards.curses;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.NecronomicurseEffect;

public class Necronomicurse extends AbstractCard {
    public static final String ID = "Necronomicurse";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Necronomicurse");

    public Necronomicurse() {
        super("Necronomicurse", cardStrings.NAME, "curse/necronomicurse", -2, cardStrings.DESCRIPTION, CardType.CURSE,
                CardColor.CURSE, CardRarity.SPECIAL, CardTarget.NONE);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    public void onRemoveFromMasterDeck() {
        if (AbstractDungeon.player.hasRelic("Necronomicon")) {
            AbstractDungeon.player.getRelic("Necronomicon").flash();
        }
        AbstractDungeon.effectsQueue
                .add(new NecronomicurseEffect(new Necronomicurse(), Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
    }

    public void triggerOnExhaust() {
        if (AbstractDungeon.player.hasRelic("Necronomicon")) {
            AbstractDungeon.player.getRelic("Necronomicon").flash();
        }
        addToBot((AbstractGameAction) new MakeTempCardInHandAction(makeCopy()));
    }

    public void upgrade() {
    }

    public AbstractCard makeCopy() {
        return new Necronomicurse();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\curses\
 * Necronomicurse.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



