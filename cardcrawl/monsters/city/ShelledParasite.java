package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateHopAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.unique.VampireDamageAction;
import com.megacrit.cardcrawl.actions.utility.TextAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.FrailPower;
import com.megacrit.cardcrawl.powers.PlatedArmorPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.BiteEffect;

public class ShelledParasite extends AbstractMonster {
    public static final String ID = "Shelled Parasite";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack
            .getMonsterStrings("Shelled Parasite");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final int HP_MIN = 68;
    private static final int HP_MAX = 72;
    private static final int A_2_HP_MIN = 70;
    private static final int A_2_HP_MAX = 75;
    private static final float HB_X_F = 20.0F;
    private static final float HB_Y_F = -6.0F;
    private static final float HB_W = 350.0F;
    private static final float HB_H = 260.0F;
    private static final int PLATED_ARMOR_AMT = 14;
    private static final int FELL_DMG = 18;
    private static final int DOUBLE_STRIKE_DMG = 6;
    private static final int SUCK_DMG = 10;
    private static final int A_2_FELL_DMG = 21;

    public ShelledParasite(float x, float y) {
        super(NAME, "Shelled Parasite", 72, 20.0F, -6.0F, 350.0F, 260.0F, null, x, y);

        loadAnimation("images/monsters/theCity/shellMonster/skeleton.atlas",
                "images/monsters/theCity/shellMonster/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.2F);
        e.setTimeScale(0.8F);

        this.dialogX = -50.0F * Settings.scale;

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(70, 75);
        } else {
            setHp(68, 72);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.doubleStrikeDmg = 7;
            this.fellDmg = 21;
            this.suckDmg = 12;
        } else {
            this.doubleStrikeDmg = 6;
            this.fellDmg = 18;
            this.suckDmg = 10;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.doubleStrikeDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.fellDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.suckDmg));
    }
    private static final int A_2_DOUBLE_STRIKE_DMG = 7;
    private static final int A_2_SUCK_DMG = 12;
    private int fellDmg;
    private int doubleStrikeDmg;
    private int suckDmg;
    private static final int DOUBLE_STRIKE_COUNT = 2;
    private static final int FELL_FRAIL_AMT = 2;
    private static final byte FELL = 1;
    private static final byte DOUBLE_STRIKE = 2;
    private static final byte LIFE_SUCK = 3;
    private static final byte STUNNED = 4;
    private boolean firstMove = true;
    public static final String ARMOR_BREAK = "ARMOR_BREAK";
    public ShelledParasite() {
        this(-20.0F, 10.0F);
    }

    public void usePreBattleAction() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new PlatedArmorPower((AbstractCreature) this, 14)));

        AbstractDungeon.actionManager.addToBottom(
                (AbstractGameAction) new GainBlockAction((AbstractCreature) this, (AbstractCreature) this, 14));
    }

    public void takeTurn() {
        int i;
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.3F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.BLUNT_HEAVY));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new FrailPower((AbstractCreature) AbstractDungeon.player, 2, true), 2));
                break;

            case 2:
                for (i = 0; i < 2; i++) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new AnimateHopAction((AbstractCreature) this));
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.2F));
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(0), AbstractGameAction.AttackEffect.BLUNT_LIGHT));
                }
                break;
            case 3:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.4F));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new VFXAction((AbstractGameEffect) new BiteEffect(
                                AbstractDungeon.player.hb.cX +

                                        MathUtils.random(-25.0F, 25.0F) * Settings.scale,
                                AbstractDungeon.player.hb.cY +
                                        MathUtils.random(-25.0F, 25.0F) * Settings.scale,
                                Color.GOLD
                                        .cpy()),
                                0.0F));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VampireDamageAction(
                        (AbstractCreature) AbstractDungeon.player, this.damage
                                .get(2),
                        AbstractGameAction.AttackEffect.NONE));
                break;
            case 4:
                setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new TextAboveCreatureAction((AbstractCreature) this,
                                TextAboveCreatureAction.TextType.STUNNED));
                break;
        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    public void changeState(String stateName) {
        switch (stateName) {
            case "ATTACK":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
            case "ARMOR_BREAK":
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateHopAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.3F));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateHopAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.3F));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateHopAction((AbstractCreature) this));
                setMove((byte) 4, Intent.STUN);
                createIntent();
                break;
        }
    }

    public void damage(DamageInfo info) {
        super.damage(info);
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hit", false);
            this.state.addAnimation(0, "Idle", true, 0.0F);
        }
    }

    protected void getMove(int num) {
        if (this.firstMove) {
            this.firstMove = false;
            if (AbstractDungeon.ascensionLevel >= 17) {
                setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                return;
            }
            if (AbstractDungeon.aiRng.randomBoolean()) {
                setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 2, true);
            } else {
                setMove((byte) 3, Intent.ATTACK_BUFF, ((DamageInfo) this.damage.get(2)).base);
            }

            return;
        }

        if (num < 20) {
            if (!lastMove((byte) 1)) {
                setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
            } else {
                getMove(AbstractDungeon.aiRng.random(20, 99));
            }

        } else if (num < 60) {
            if (!lastTwoMoves((byte) 2)) {
                setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 2, true);
            } else {
                setMove((byte) 3, Intent.ATTACK_BUFF, ((DamageInfo) this.damage.get(2)).base);
            }

        } else if (!lastTwoMoves((byte) 3)) {
            setMove((byte) 3, Intent.ATTACK_BUFF, ((DamageInfo) this.damage.get(2)).base);
        } else {
            setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 2, true);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * ShelledParasite.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

