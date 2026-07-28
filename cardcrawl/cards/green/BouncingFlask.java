package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.unique.BouncingFlaskAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.PotionBounceEffect;

public class BouncingFlask extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Bouncing Flask");
    public static final String ID = "Bouncing Flask";

    public BouncingFlask() {
        super("Bouncing Flask", cardStrings.NAME, "green/skill/bouncing_flask", 2, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.GREEN, CardRarity.UNCOMMON, CardTarget.ALL_ENEMY);

        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        AbstractMonster randomMonster = AbstractDungeon.getMonsters().getRandomMonster(null, true,
                AbstractDungeon.cardRandomRng);

        if (randomMonster != null) {
            addToBot((AbstractGameAction) new VFXAction(
                    (AbstractGameEffect) new PotionBounceEffect(p.hb.cX, p.hb.cY, randomMonster.hb.cX, this.hb.cY),
                    0.4F));
        }
        addToBot((AbstractGameAction) new BouncingFlaskAction((AbstractCreature) randomMonster, 3, this.magicNumber));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new BouncingFlask();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * BouncingFlask.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


