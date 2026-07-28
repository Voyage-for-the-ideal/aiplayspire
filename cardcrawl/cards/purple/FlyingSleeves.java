package com.megacrit.cardcrawl.cards.purple;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.AnimatedSlashEffect;

public class FlyingSleeves extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("FlyingSleeves");
    public static final String ID = "FlyingSleeves";

    public FlyingSleeves() {
        super("FlyingSleeves", cardStrings.NAME, "purple/attack/flying_sleeves", 1, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.PURPLE, CardRarity.COMMON, CardTarget.ENEMY);

        this.baseDamage = 4;
        this.selfRetain = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (m != null) {
            addToBot((AbstractGameAction) new SFXAction("ATTACK_WHIFF_2", 0.3F));
            addToBot((AbstractGameAction) new SFXAction("ATTACK_FAST", 0.2F));
            addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new AnimatedSlashEffect(m.hb.cX,
                    m.hb.cY - 30.0F * Settings.scale, 500.0F, 200.0F, 290.0F, 3.0F, Color.VIOLET, Color.PINK)));
        }

        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.NONE));
        if (m != null) {
            addToBot((AbstractGameAction) new SFXAction("ATTACK_WHIFF_1", 0.2F));
            addToBot((AbstractGameAction) new SFXAction("ATTACK_FAST", 0.2F));
            addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new AnimatedSlashEffect(m.hb.cX,
                    m.hb.cY - 30.0F * Settings.scale, 500.0F, -200.0F, 250.0F, 3.0F, Color.VIOLET, Color.PINK)));
        }

        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.NONE));
    }

    public AbstractCard makeCopy() {
        return new FlyingSleeves();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(2);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\
 * FlyingSleeves.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

