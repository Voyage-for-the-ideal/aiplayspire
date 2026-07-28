package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.TextAboveCreatureAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;
import com.megacrit.cardcrawl.vfx.combat.PowerBuffEffect;
import com.megacrit.cardcrawl.vfx.combat.PowerDebuffEffect;

import java.util.Collections;

public class ApplyPowerAction
        extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("ApplyPowerAction");
    public static final String[] TEXT = uiStrings.TEXT;

    private AbstractPower powerToApply;

    private float startingDuration;

    public ApplyPowerAction(AbstractCreature target, AbstractCreature source, AbstractPower powerToApply,
            int stackAmount, boolean isFast, AttackEffect effect) {
        if (Settings.FAST_MODE) {
            this.startingDuration = 0.1F;
        } else if (isFast) {
            this.startingDuration = Settings.ACTION_DUR_FASTER;
        } else {
            this.startingDuration = Settings.ACTION_DUR_FAST;
        }

        setValues(target, source, stackAmount);

        this.duration = this.startingDuration;
        this.powerToApply = powerToApply;

        if (AbstractDungeon.player.hasRelic("Snake Skull") && source != null && source.isPlayer && target != source
                && powerToApply.ID
                        .equals("Poison")) {
            AbstractDungeon.player.getRelic("Snake Skull").flash();
            this.powerToApply.amount++;
            this.amount++;
        }

        if (powerToApply.ID.equals("Corruption")) {
            for (AbstractCard c : AbstractDungeon.player.hand.group) {
                if (c.type == AbstractCard.CardType.SKILL)
                    c.modifyCostForCombat(-9);
            }
            for (AbstractCard c : AbstractDungeon.player.drawPile.group) {
                if (c.type == AbstractCard.CardType.SKILL)
                    c.modifyCostForCombat(-9);
            }
            for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
                if (c.type == AbstractCard.CardType.SKILL)
                    c.modifyCostForCombat(-9);
            }
            for (AbstractCard c : AbstractDungeon.player.exhaustPile.group) {
                if (c.type == AbstractCard.CardType.SKILL) {
                    c.modifyCostForCombat(-9);
                }
            }
        }

        this.actionType = ActionType.POWER;
        this.attackEffect = effect;

        if (AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            this.duration = 0.0F;
            this.startingDuration = 0.0F;
            this.isDone = true;
        }
    }

    public ApplyPowerAction(AbstractCreature target, AbstractCreature source, AbstractPower powerToApply,
            int stackAmount, boolean isFast) {
        this(target, source, powerToApply, stackAmount, isFast, AttackEffect.NONE);
    }

    public ApplyPowerAction(AbstractCreature target, AbstractCreature source, AbstractPower powerToApply) {
        this(target, source, powerToApply, powerToApply.amount);
    }

    public ApplyPowerAction(AbstractCreature target, AbstractCreature source, AbstractPower powerToApply,
            int stackAmount) {
        this(target, source, powerToApply, stackAmount, false, AttackEffect.NONE);
    }

    public ApplyPowerAction(AbstractCreature target, AbstractCreature source, AbstractPower powerToApply,
            int stackAmount, AttackEffect effect) {
        this(target, source, powerToApply, stackAmount, false, effect);
    }

    public void update() {
        if (this.target == null || this.target.isDeadOrEscaped()) {
            this.isDone = true;

            return;
        }
        if (this.duration == this.startingDuration) {
            if (this.powerToApply instanceof com.megacrit.cardcrawl.powers.NoDrawPower
                    && this.target.hasPower(this.powerToApply.ID)) {
                this.isDone = true;

                return;
            }
            if (this.source != null) {
                for (AbstractPower pow : this.source.powers) {
                    pow.onApplyPower(this.powerToApply, this.target, this.source);
                }
            }

            if (AbstractDungeon.player.hasRelic("Champion Belt") && this.source != null && this.source.isPlayer
                    && this.target != this.source && this.powerToApply.ID
                            .equals("Vulnerable")
                    && !this.target.hasPower("Artifact")) {
                AbstractDungeon.player.getRelic("Champion Belt").onTrigger(this.target);
            }

            if (this.target instanceof com.megacrit.cardcrawl.monsters.AbstractMonster &&
                    this.target.isDeadOrEscaped()) {
                this.duration = 0.0F;
                this.isDone = true;

                return;
            }

            if (AbstractDungeon.player.hasRelic("Ginger") && this.target.isPlayer
                    && this.powerToApply.ID.equals("Weakened")) {

                AbstractDungeon.player.getRelic("Ginger").flash();
                addToTop((AbstractGameAction) new TextAboveCreatureAction(this.target, TEXT[1]));
                this.duration -= Gdx.graphics.getDeltaTime();

                return;
            }

            if (AbstractDungeon.player.hasRelic("Turnip") && this.target.isPlayer
                    && this.powerToApply.ID.equals("Frail")) {

                AbstractDungeon.player.getRelic("Turnip").flash();
                addToTop((AbstractGameAction) new TextAboveCreatureAction(this.target, TEXT[1]));
                this.duration -= Gdx.graphics.getDeltaTime();

                return;
            }

            if (this.target.hasPower("Artifact") &&
                    this.powerToApply.type == AbstractPower.PowerType.DEBUFF) {
                addToTop((AbstractGameAction) new TextAboveCreatureAction(this.target, TEXT[0]));
                this.duration -= Gdx.graphics.getDeltaTime();
                CardCrawlGame.sound.play("NULLIFY_SFX");
                this.target.getPower("Artifact").flashWithoutSound();
                this.target.getPower("Artifact").onSpecificTrigger();

                return;
            }

            AbstractDungeon.effectList
                    .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, this.attackEffect));

            boolean hasBuffAlready = false;
            for (AbstractPower p : this.target.powers) {
                if (p.ID.equals(this.powerToApply.ID) && !p.ID.equals("Night Terror")) {
                    p.stackPower(this.amount);
                    p.flash();

                    if ((p instanceof com.megacrit.cardcrawl.powers.StrengthPower
                            || p instanceof com.megacrit.cardcrawl.powers.DexterityPower) && this.amount <= 0) {
                        AbstractDungeon.effectList.add(new PowerDebuffEffect(this.target.hb.cX - this.target.animX,
                                this.target.hb.cY + this.target.hb.height / 2.0F, this.powerToApply.name + TEXT[3]));

                    } else if (this.amount > 0) {
                        if (p.type == AbstractPower.PowerType.BUFF
                                || p instanceof com.megacrit.cardcrawl.powers.StrengthPower
                                || p instanceof com.megacrit.cardcrawl.powers.DexterityPower) {
                            AbstractDungeon.effectList.add(new PowerBuffEffect(this.target.hb.cX - this.target.animX,
                                    this.target.hb.cY + this.target.hb.height / 2.0F, "+" +

                                            Integer.toString(this.amount) + " " + this.powerToApply.name));
                        } else {
                            AbstractDungeon.effectList.add(new PowerDebuffEffect(this.target.hb.cX - this.target.animX,
                                    this.target.hb.cY + this.target.hb.height / 2.0F, "+" +

                                            Integer.toString(this.amount) + " " + this.powerToApply.name));
                        }

                    } else if (p.type == AbstractPower.PowerType.BUFF) {
                        AbstractDungeon.effectList.add(new PowerBuffEffect(this.target.hb.cX - this.target.animX,
                                this.target.hb.cY + this.target.hb.height / 2.0F, this.powerToApply.name + TEXT[3]));

                    } else {

                        AbstractDungeon.effectList.add(new PowerDebuffEffect(this.target.hb.cX - this.target.animX,
                                this.target.hb.cY + this.target.hb.height / 2.0F, this.powerToApply.name + TEXT[3]));
                    }

                    p.updateDescription();
                    hasBuffAlready = true;
                    AbstractDungeon.onModifyPower();
                }
            }

            if (this.powerToApply.type == AbstractPower.PowerType.DEBUFF) {
                this.target.useFastShakeAnimation(0.5F);
            }

            if (!hasBuffAlready) {
                this.target.powers.add(this.powerToApply);
                Collections.sort(this.target.powers);
                this.powerToApply.onInitialApplication();
                this.powerToApply.flash();

                if (this.amount < 0 && (this.powerToApply.ID.equals("Strength")
                        || this.powerToApply.ID.equals("Dexterity") || this.powerToApply.ID
                                .equals("Focus"))) {
                    AbstractDungeon.effectList.add(new PowerDebuffEffect(this.target.hb.cX - this.target.animX,
                            this.target.hb.cY + this.target.hb.height / 2.0F, this.powerToApply.name + TEXT[3]));

                } else if (this.powerToApply.type == AbstractPower.PowerType.BUFF) {
                    AbstractDungeon.effectList.add(new PowerBuffEffect(this.target.hb.cX - this.target.animX,
                            this.target.hb.cY + this.target.hb.height / 2.0F, this.powerToApply.name));

                } else {

                    AbstractDungeon.effectList.add(new PowerDebuffEffect(this.target.hb.cX - this.target.animX,
                            this.target.hb.cY + this.target.hb.height / 2.0F, this.powerToApply.name));
                }

                AbstractDungeon.onModifyPower();

                if (this.target.isPlayer) {
                    int buffCount = 0;
                    for (AbstractPower p : this.target.powers) {
                        if (p.type == AbstractPower.PowerType.BUFF) {
                            buffCount++;
                        }
                    }
                    if (buffCount >= 10) {
                        UnlockTracker.unlockAchievement("POWERFUL");
                    }
                }
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ApplyPowerAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



