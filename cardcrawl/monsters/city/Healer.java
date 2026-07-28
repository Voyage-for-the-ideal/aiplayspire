package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.FrailPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class Healer extends AbstractMonster {
    public static final String ID = "Healer";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Healer");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final float IDLE_TIMESCALE = 0.8F;

    public static final String ENC_NAME = "HealerTank";

    private static final int HP_MIN = 48;
    private static final int HP_MAX = 56;
    private static final int A_2_HP_MIN = 50;
    private static final int A_2_HP_MAX = 58;
    private static final int MAGIC_DMG = 8;
    private static final int HEAL_AMT = 16;

    public Healer(float x, float y) {
        super(NAME, "Healer", 56, 0.0F, -20.0F, 230.0F, 250.0F, null, x, y);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(50, 58);
        } else {
            setHp(48, 56);
        }

        if (AbstractDungeon.ascensionLevel >= 17) {
            this.magicDmg = 9;
            this.strAmt = 4;
            this.healAmt = 20;
        } else if (AbstractDungeon.ascensionLevel >= 2) {
            this.magicDmg = 9;
            this.strAmt = 3;
            this.healAmt = 16;
        } else {
            this.magicDmg = 8;
            this.strAmt = 2;
            this.healAmt = 16;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.magicDmg));

        loadAnimation("images/monsters/theCity/healer/skeleton.atlas", "images/monsters/theCity/healer/skeleton.json",
                1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        this.stateData.setMix("Hit", "Idle", 0.2F);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.state.setTimeScale(0.8F);
    }
    private static final int STR_AMOUNT = 2;
    private static final int A_2_MAGIC_DMG = 9;
    private static final int A_2_STR_AMOUNT = 3;
    private int magicDmg;
    private int strAmt;
    private int healAmt;
    private static final byte ATTACK = 1;
    private static final byte HEAL = 2;
    private static final byte BUFF = 3;

    public void takeTurn() {
        switch (this.nextMove) {
            case 1:
                playSfx();
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new FrailPower((AbstractCreature) AbstractDungeon.player, 2, true), 2));
                break;

            case 2:
                playSfx();
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ChangeStateAction(this, "STAFF_RAISE"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.25F));
                for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                    if (!m.isDying && !m.isEscaping) {
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new HealAction((AbstractCreature) m,
                                        (AbstractCreature) this, this.healAmt));
                    }
                }
                break;
            case 3:
                playSfx();
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ChangeStateAction(this, "STAFF_RAISE"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.25F));
                for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                    if (!m.isDying && !m.isEscaping) {
                        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                                (AbstractCreature) m, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) m, this.strAmt), this.strAmt));
                    }
                }
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    private void playSfx() {
        if (MathUtils.randomBoolean()) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_HEALER_1A"));
        } else {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_HEALER_1B"));
        }
    }

    private void playDeathSfx() {
        int roll = MathUtils.random(2);
        if (roll == 0) {
            CardCrawlGame.sound.play("VO_HEALER_2A");
        } else if (roll == 1) {
            CardCrawlGame.sound.play("VO_HEALER_2B");
        } else {
            CardCrawlGame.sound.play("VO_HEALER_2C");
        }
    }

    public void changeState(String key) {
        switch (key) {
            case "STAFF_RAISE":
                this.state.setAnimation(0, "Attack", false);
                this.state.setTimeScale(0.8F);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
        }
    }

    protected void getMove(int num) {
        int needToHeal = 0;

        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            if (!m.isDying && !m.isEscaping) {
                needToHeal += m.maxHealth - m.currentHealth;
            }
        }

        if (AbstractDungeon.ascensionLevel >= 17) {
            if (needToHeal > 20 && !lastTwoMoves((byte) 2)) {
                setMove((byte) 2, Intent.BUFF);

                return;
            }
        } else if (needToHeal > 15 && !lastTwoMoves((byte) 2)) {
            setMove((byte) 2, Intent.BUFF);

            return;
        }

        if (AbstractDungeon.ascensionLevel >= 17) {

            if (num >= 40 && !lastMove((byte) 1)) {
                setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);

                return;
            }
        } else if (num >= 40 && !lastTwoMoves((byte) 1)) {
            setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);

            return;
        }

        if (!lastTwoMoves((byte) 3)) {
            setMove((byte) 3, Intent.BUFF);
            return;
        }
        setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
    }

    public void damage(DamageInfo info) {
        super.damage(info);
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hit", false);
            this.state.setTimeScale(0.8F);
            this.state.addAnimation(0, "Idle", true, 0.0F);
        }
    }

    public void die() {
        playDeathSfx();
        this.state.setTimeScale(0.1F);
        useShakeAnimation(5.0F);
        super.die();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * Healer.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

