package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.TextAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.PoisonPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;
import com.megacrit.cardcrawl.vfx.combat.PowerBuffEffect;
import com.megacrit.cardcrawl.vfx.combat.PowerDebuffEffect;

import java.util.Collections;

@Deprecated
public class ApplyPoisonOnRandomMonsterAction
        extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("ApplyPowerAction");
    public static final String[] TEXT = uiStrings.TEXT;

    private float startingDuration;

    private AbstractPower powerToApply;

    public ApplyPoisonOnRandomMonsterAction(AbstractCreature source, int stackAmount, boolean isFast,
            AttackEffect effect) {
        if (Settings.FAST_MODE) {
            this.startingDuration = 0.1F;
        } else if (isFast) {
            this.startingDuration = Settings.ACTION_DUR_FASTER;
        } else {
            this.startingDuration = Settings.ACTION_DUR_FAST;
        }

        this.duration = this.startingDuration;
        this.target = null;
        this.source = source;
        this.amount = stackAmount;
        this.actionType = ActionType.POWER;
        this.attackEffect = effect;

        if (AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            this.duration = 0.0F;
            this.startingDuration = 0.0F;
            this.isDone = true;
        }
    }

    public void update() {
        if (this.duration == this.startingDuration) {
            this.target = (AbstractCreature) AbstractDungeon.getRandomMonster();

            if (this.target == null) {
                this.isDone = true;

                return;
            }
            this.powerToApply = (AbstractPower) new PoisonPower(this.target, this.source, this.amount);

            if (this.source != null) {
                for (AbstractPower pow : this.source.powers) {
                    pow.onApplyPower(this.powerToApply, this.target, this.source);
                }
            }

            if (this.target.hasPower("Artifact")) {
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
 * ApplyPoisonOnRandomMonsterAction.class Java compiler version: 8 (52.0)
 * JD-Core Version: 1.1.3
 */



