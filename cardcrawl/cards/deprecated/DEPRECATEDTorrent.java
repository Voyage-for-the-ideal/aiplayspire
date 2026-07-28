package com.megacrit.cardcrawl.cards.deprecated;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.utility.ShakeScreenAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderLongFlashEffect;

public class DEPRECATEDTorrent extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Torrent");
    public static final String ID = "Torrent";

    public DEPRECATEDTorrent() {
        super("Torrent", cardStrings.NAME, null, 1, cardStrings.DESCRIPTION, CardType.ATTACK, CardColor.PURPLE,
                CardRarity.RARE, CardTarget.ALL_ENEMY);

        this.exhaust = true;
        this.baseDamage = 1;
        this.isMultiDamage = true;
        this.baseMagicNumber = 4;
        this.magicNumber = 4;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new BorderLongFlashEffect(Color.CYAN)));
        addToBot((AbstractGameAction) new ShakeScreenAction(0.0F, ScreenShake.ShakeDur.MED,
                ScreenShake.ShakeIntensity.HIGH));

        for (int i = 0; i < this.magicNumber; i++) {
            addToBot((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) p, this.multiDamage,
                    this.damageTypeForTurn, AbstractGameAction.AttackEffect.SLASH_HORIZONTAL, true));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(2);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDTorrent();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDTorrent.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



