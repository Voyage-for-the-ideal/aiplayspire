package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.FastShakeAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.*;

public class Chosen extends AbstractMonster {
    public static final String ID = "Chosen";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Chosen");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final float IDLE_TIMESCALE = 0.8F;

    private static final int HP_MIN = 95;

    private static final int HP_MAX = 99;

    private static final int A_2_HP_MIN = 98;

    private static final int A_2_HP_MAX = 103;

    private static final float HB_X = 5.0F;

    private static final float HB_Y = -10.0F;

    private static final float HB_W = 200.0F;
    private static final float HB_H = 280.0F;
    private static final int ZAP_DMG = 18;
    private static final int A_2_ZAP_DMG = 21;
    private static final int DEBILITATE_DMG = 10;
    private static final int A_2_DEBILITATE_DMG = 12;
    private static final int POKE_DMG = 5;

    public Chosen() {
        this(0.0F, 0.0F);
    }
    private static final int A_2_POKE_DMG = 6;
    private int zapDmg;
    private int debilitateDmg;
    private int pokeDmg;
    private static final int DEBILITATE_VULN = 2;
    private static final int DRAIN_STR = 3;
    private static final int DRAIN_WEAK = 3;
    private static final byte ZAP = 1;
    private static final byte DRAIN = 2;
    private static final byte DEBILITATE = 3;
    private static final byte HEX = 4;
    private static final byte POKE = 5;
    private static final int HEX_AMT = 1;
    private boolean firstTurn = true, usedHex = false;
    public Chosen(float x, float y) {
        super(NAME, "Chosen", 99, 5.0F, -10.0F, 200.0F, 280.0F, null, x, -20.0F + y);
        this.dialogX = -30.0F * Settings.scale;
        this.dialogY = 50.0F * Settings.scale;

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(98, 103);
        } else {
            setHp(95, 99);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.zapDmg = 21;
            this.debilitateDmg = 12;
            this.pokeDmg = 6;
        } else {
            this.zapDmg = 18;
            this.debilitateDmg = 10;
            this.pokeDmg = 5;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.zapDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.debilitateDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.pokeDmg));

        loadAnimation("images/monsters/theCity/chosen/skeleton.atlas", "images/monsters/theCity/chosen/skeleton.json",
                1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.2F);
        this.stateData.setMix("Attack", "Idle", 0.2F);
        this.state.setTimeScale(0.8F);
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 5:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(2), AbstractGameAction.AttackEffect.SLASH_HORIZONTAL));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(2), AbstractGameAction.AttackEffect.SLASH_VERTICAL));
                break;
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new FastShakeAction((AbstractCreature) this, 0.3F, 0.5F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.FIRE));
                break;
            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, 3, true), 3));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) this, 3), 3));
                break;

            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.SLASH_HEAVY));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new VulnerablePower((AbstractCreature) AbstractDungeon.player, 2, true), 2));
                break;

            case 4:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[0]));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.2F));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new HexPower((AbstractCreature) AbstractDungeon.player, 1)));
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    public void changeState(String key) {
        switch (key) {
            case "ATTACK":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
        }
    }

    protected void getMove(int num) {
        if (AbstractDungeon.ascensionLevel >= 17) {

            if (!this.usedHex) {
                this.usedHex = true;
                setMove((byte) 4, Intent.STRONG_DEBUFF);

                return;
            }
            if (!lastMove((byte) 3) && !lastMove((byte) 2)) {
                if (num < 50) {
                    setMove((byte) 3, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                    return;
                }
                setMove((byte) 2, Intent.DEBUFF);

                return;
            }

            if (num < 40) {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
                return;
            }
            setMove((byte) 5, Intent.ATTACK, ((DamageInfo) this.damage.get(2)).base, 2, true);

            return;
        }

        if (this.firstTurn) {
            this.firstTurn = false;
            setMove((byte) 5, Intent.ATTACK, ((DamageInfo) this.damage.get(2)).base, 2, true);

            return;
        }

        if (!this.usedHex) {
            this.usedHex = true;
            setMove((byte) 4, Intent.STRONG_DEBUFF);

            return;
        }
        if (!lastMove((byte) 3) && !lastMove((byte) 2)) {
            if (num < 50) {
                setMove((byte) 3, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                return;
            }
            setMove((byte) 2, Intent.DEBUFF);

            return;
        }

        if (num < 40) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
            return;
        }
        setMove((byte) 5, Intent.ATTACK, ((DamageInfo) this.damage.get(2)).base, 2, true);
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
        super.die();
        CardCrawlGame.sound.play("CHOSEN_DEATH");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * Chosen.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

