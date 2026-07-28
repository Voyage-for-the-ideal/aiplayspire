package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateHopAction;
import com.megacrit.cardcrawl.actions.animations.SetAnimationAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.ShakeScreenAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.BiteEffect;

public class JawWorm extends AbstractMonster {
    public static final String ID = "JawWorm";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("JawWorm");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final int HP_MIN = 40;

    private static final int HP_MAX = 44;

    private static final int A_2_HP_MIN = 42;

    private static final int A_2_HP_MAX = 46;

    private static final float HB_X = 0.0F;

    private static final float HB_Y = -25.0F;

    private static final float HB_W = 260.0F;

    private static final float HB_H = 170.0F;
    private static final int CHOMP_DMG = 11;
    private static final int A_2_CHOMP_DMG = 12;
    private static final int THRASH_DMG = 7;
    private static final int THRASH_BLOCK = 5;
    private static final int BELLOW_STR = 3;

    public JawWorm(float x, float y) {
        this(x, y, false);
    }
    private static final int A_2_BELLOW_STR = 4;
    private static final int A_17_BELLOW_STR = 5;
    private static final int BELLOW_BLOCK = 6;
    private static final int A_17_BELLOW_BLOCK = 9;
    private int bellowBlock;
    private int chompDmg;
    private int thrashDmg;
    private int thrashBlock;
    private int bellowStr;
    private static final byte CHOMP = 1;
    private static final byte BELLOW = 2;
    private static final byte THRASH = 3;
    private boolean firstMove = true;
    private boolean hardMode;
    public JawWorm(float x, float y, boolean hard) {
        super(NAME, "JawWorm", 44, 0.0F, -25.0F, 260.0F, 170.0F, null, x, y);

        this.hardMode = hard;
        if (this.hardMode) {
            this.firstMove = false;
        }

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(42, 46);
        } else {
            setHp(40, 44);
        }

        if (AbstractDungeon.ascensionLevel >= 17) {
            this.bellowStr = 5;
            this.bellowBlock = 9;
            this.chompDmg = 12;
            this.thrashDmg = 7;
            this.thrashBlock = 5;
        } else if (AbstractDungeon.ascensionLevel >= 2) {
            this.bellowStr = 4;
            this.bellowBlock = 6;
            this.chompDmg = 12;
            this.thrashDmg = 7;
            this.thrashBlock = 5;
        } else {
            this.bellowStr = 3;
            this.bellowBlock = 6;
            this.chompDmg = 11;
            this.thrashDmg = 7;
            this.thrashBlock = 5;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.chompDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.thrashDmg));

        loadAnimation("images/monsters/theBottom/jawWorm/skeleton.atlas",
                "images/monsters/theBottom/jawWorm/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
    }

    public void usePreBattleAction() {
        if (this.hardMode) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                    (AbstractCreature) this, (AbstractPower) new StrengthPower((AbstractCreature) this, this.bellowStr),
                    this.bellowStr));

            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                    (AbstractCreature) this, this.bellowBlock));
        }
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new SetAnimationAction((AbstractCreature) this, "chomp"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new BiteEffect(AbstractDungeon.player.hb.cX, AbstractDungeon.player.hb.cY),
                        0.3F));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.NONE));
                break;
            case 2:
                this.state.setAnimation(0, "tailslam", false);
                this.state.addAnimation(0, "idle", true, 0.0F);
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new SFXAction("MONSTER_JAW_WORM_BELLOW"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ShakeScreenAction(0.2F,
                        ScreenShake.ShakeDur.SHORT, ScreenShake.ShakeIntensity.MED));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) this, (AbstractCreature) this,
                        (AbstractPower) new StrengthPower((AbstractCreature) this, this.bellowStr), this.bellowStr));

                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                (AbstractCreature) this, this.bellowBlock));
                break;
            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateHopAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.BLUNT_LIGHT));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                (AbstractCreature) this, this.thrashBlock));
                break;
        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        if (this.firstMove) {
            this.firstMove = false;
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);

            return;
        }

        if (num < 25) {
            if (lastMove((byte) 1)) {
                if (AbstractDungeon.aiRng.randomBoolean(0.5625F)) {
                    setMove(MOVES[0], (byte) 2, Intent.DEFEND_BUFF);
                } else {
                    setMove((byte) 3, Intent.ATTACK_DEFEND, ((DamageInfo) this.damage.get(1)).base);
                }
            } else {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
            }

        } else if (num < 55) {
            if (lastTwoMoves((byte) 3)) {
                if (AbstractDungeon.aiRng.randomBoolean(0.357F)) {
                    setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
                } else {
                    setMove(MOVES[0], (byte) 2, Intent.DEFEND_BUFF);
                }
            } else {
                setMove((byte) 3, Intent.ATTACK_DEFEND, ((DamageInfo) this.damage.get(1)).base);

            }

        } else if (lastMove((byte) 2)) {
            if (AbstractDungeon.aiRng.randomBoolean(0.416F)) {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
            } else {
                setMove((byte) 3, Intent.ATTACK_DEFEND, ((DamageInfo) this.damage.get(1)).base);
            }
        } else {
            setMove(MOVES[0], (byte) 2, Intent.DEFEND_BUFF);
        }
    }

    public void die() {
        super.die();
        CardCrawlGame.sound.play("JAW_WORM_DEATH");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\exordium\
 * JawWorm.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

