package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateFastAttackAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.SetAnimationAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.TextAboveCreatureAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.FlightPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class Byrd extends AbstractMonster {
    public static final String ID = "Byrd";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Byrd");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    public static final String THREE_BYRDS = "3_Byrds";

    public static final String FOUR_BYRDS = "4_Byrds";

    public static final String IMAGE = "images/monsters/theCity/byrdFlying.png";
    private static final int HP_MIN = 25;
    private static final int HP_MAX = 31;
    private static final int A_2_HP_MIN = 26;
    private static final int A_2_HP_MAX = 33;
    private static final float HB_X_F = 0.0F;
    private static final float HB_X_G = 10.0F;
    private static final float HB_Y_F = 50.0F;
    private static final float HB_Y_G = -50.0F;
    private static final float HB_W = 240.0F;
    private static final float HB_H = 180.0F;
    private static final int PECK_DMG = 1;
    private static final int PECK_COUNT = 5;
    private static final int SWOOP_DMG = 12;
    private static final int A_2_PECK_COUNT = 6;

    public Byrd(float x, float y) {
        super(NAME, "Byrd", 31, 0.0F, 50.0F, 240.0F, 180.0F, null, x, y);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(26, 33);
        } else {
            setHp(25, 31);
        }

        if (AbstractDungeon.ascensionLevel >= 17) {
            this.flightAmt = 4;
        } else {
            this.flightAmt = 3;
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.peckDmg = 1;
            this.peckCount = 6;
            this.swoopDmg = 14;
        } else {
            this.peckDmg = 1;
            this.peckCount = 5;
            this.swoopDmg = 12;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.peckDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.swoopDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, 3));

        loadAnimation("images/monsters/theCity/byrd/flying.atlas", "images/monsters/theCity/byrd/flying.json", 1.0F);
        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle_flap", true);
        e.setTime(e.getEndTime() * MathUtils.random());
    }
    private static final int A_2_SWOOP_DMG = 14;
    private int peckDmg;
    private int peckCount;
    private int swoopDmg;
    private int flightAmt;
    private static final int HEADBUTT_DMG = 3;
    private static final int CAW_STR = 1;
    private static final byte PECK = 1;
    private static final byte GO_AIRBORNE = 2;
    private static final byte SWOOP = 3;
    private static final byte STUNNED = 4;
    private static final byte HEADBUTT = 5;
    private static final byte CAW = 6;
    private boolean firstMove = true;
    private boolean isFlying = true;
    public static final String FLY_STATE = "FLYING";
    public static final String GROUND_STATE = "GROUNDED";

    public void usePreBattleAction() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new FlightPower((AbstractCreature) this, this.flightAmt)));
    }

    public void takeTurn() {
        int i;
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateFastAttackAction((AbstractCreature) this));
                for (i = 0; i < this.peckCount; i++) {
                    playRandomBirdSFx();
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(0), AbstractGameAction.AttackEffect.BLUNT_LIGHT, true));
                }
                break;
            case 5:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(2), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                setMove((byte) 2, Intent.UNKNOWN);
                return;
            case 2:
                this.isFlying = true;
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "FLYING"));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                                (AbstractPower) new FlightPower((AbstractCreature) this, this.flightAmt)));
                break;

            case 6:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("BYRD_DEATH"));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[0], 1.2F, 1.2F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) this, 1), 1));
                break;

            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.SLASH_HEAVY));
                break;
            case 4:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new SetAnimationAction((AbstractCreature) this, "head_lift"));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new TextAboveCreatureAction((AbstractCreature) this,
                                TextAboveCreatureAction.TextType.STUNNED));
                break;
        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    private void playRandomBirdSFx() {
        AbstractDungeon.actionManager
                .addToBottom((AbstractGameAction) new SFXAction("MONSTER_BYRD_ATTACK_" + MathUtils.random(0, 5)));
    }

    public void changeState(String stateName) {
        AnimationState.TrackEntry e;
        switch (stateName) {
            case "FLYING":
                loadAnimation("images/monsters/theCity/byrd/flying.atlas", "images/monsters/theCity/byrd/flying.json",
                        1.0F);

                e = this.state.setAnimation(0, "idle_flap", true);
                e.setTime(e.getEndTime() * MathUtils.random());
                updateHitbox(0.0F, 50.0F, 240.0F, 180.0F);
                break;
            case "GROUNDED":
                setMove((byte) 4, Intent.STUN);
                createIntent();
                this.isFlying = false;
                loadAnimation("images/monsters/theCity/byrd/grounded.atlas",
                        "images/monsters/theCity/byrd/grounded.json", 1.0F);

                e = this.state.setAnimation(0, "idle", true);
                e.setTime(e.getEndTime() * MathUtils.random());
                updateHitbox(10.0F, -50.0F, 240.0F, 180.0F);
                break;
        }
    }

    protected void getMove(int num) {
        if (this.firstMove) {
            this.firstMove = false;
            if (AbstractDungeon.aiRng.randomBoolean(0.375F)) {
                setMove((byte) 6, Intent.BUFF);
            } else {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, this.peckCount, true);
            }

            return;
        }
        if (this.isFlying) {

            if (num < 50) {

                if (lastTwoMoves((byte) 1)) {
                    if (AbstractDungeon.aiRng.randomBoolean(0.4F)) {
                        setMove((byte) 3, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
                    } else {
                        setMove((byte) 6, Intent.BUFF);
                    }
                } else {
                    setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, this.peckCount, true);
                }

            } else if (num < 70) {

                if (lastMove((byte) 3)) {
                    if (AbstractDungeon.aiRng.randomBoolean(0.375F)) {
                        setMove((byte) 6, Intent.BUFF);
                    } else {
                        setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, this.peckCount, true);
                    }
                } else {
                    setMove((byte) 3, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);

                }

            } else if (lastMove((byte) 6)) {
                if (AbstractDungeon.aiRng.randomBoolean(0.2857F)) {
                    setMove((byte) 3, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
                } else {
                    setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, this.peckCount, true);
                }
            } else {
                setMove((byte) 6, Intent.BUFF);
            }

        } else {

            setMove((byte) 5, Intent.ATTACK, ((DamageInfo) this.damage.get(2)).base);
        }
    }

    public void die() {
        super.die();
        CardCrawlGame.sound.play("BYRD_DEATH");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\Byrd
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

