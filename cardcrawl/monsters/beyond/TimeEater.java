package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.ShoutAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.unique.RemoveDebuffsAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Slimed;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.*;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.ShockWaveEffect;

public class TimeEater extends AbstractMonster {
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("TimeEater");
    public static final String ID = "TimeEater";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    public static final int HP = 456;
    public static final int A_2_HP = 480;
    private static final byte REVERBERATE = 2;
    private static final byte RIPPLE = 3;
    private static final byte HEAD_SLAM = 4;
    private static final byte HASTE = 5;
    private static final int REVERB_DMG = 7;
    private static final int REVERB_AMT = 3;
    private static final int A_2_REVERB_DMG = 8;
    private static final int RIPPLE_BLOCK = 20;
    private static final int HEAD_SLAM_DMG = 26;
    private static final int A_2_HEAD_SLAM_DMG = 32;
    private int reverbDmg;
    private int headSlamDmg;
    private static final int HEAD_SLAM_STICKY = 1;
    private static final int RIPPLE_DEBUFF_TURNS = 1;
    private boolean usedHaste = false, firstTurn = true;

    public TimeEater() {
        super(NAME, "TimeEater", 456, -10.0F, -30.0F, 476.0F, 410.0F, null, -50.0F, 30.0F);

        if (AbstractDungeon.ascensionLevel >= 9) {
            setHp(480);
        } else {
            setHp(456);
        }

        loadAnimation("images/monsters/theForest/timeEater/skeleton.atlas",
                "images/monsters/theForest/timeEater/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.1F);
        e.setTimeScale(0.8F);

        this.type = EnemyType.BOSS;
        this.dialogX = -200.0F * Settings.scale;
        this.dialogY = 10.0F * Settings.scale;

        if (AbstractDungeon.ascensionLevel >= 4) {
            this.reverbDmg = 8;
            this.headSlamDmg = 32;
        } else {
            this.reverbDmg = 7;
            this.headSlamDmg = 26;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.reverbDmg, DamageInfo.DamageType.NORMAL));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.headSlamDmg, DamageInfo.DamageType.NORMAL));
    }

    public void usePreBattleAction() {
        CardCrawlGame.music.unsilenceBGM();
        AbstractDungeon.scene.fadeOutAmbiance();
        AbstractDungeon.getCurrRoom().playBgmInstantly("BOSS_BEYOND");
        UnlockTracker.markBossAsSeen("WIZARD");
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new TimeWarpPower((AbstractCreature) this)));
    }

    public void takeTurn() {
        int i;
        if (this.firstTurn) {
            if (AbstractDungeon.player.chosenClass == AbstractPlayer.PlayerClass.WATCHER) {
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[2], 0.5F, 2.0F));
            } else {
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[0], 0.5F, 2.0F));
            }
            this.firstTurn = false;
        }
        switch (this.nextMove) {
            case 2:
                for (i = 0; i < 3; i++) {
                    AbstractDungeon.actionManager
                            .addToBottom(
                                    (AbstractGameAction) new VFXAction((AbstractCreature) this,
                                            (AbstractGameEffect) new ShockWaveEffect(this.hb.cX, this.hb.cY,
                                                    Settings.BLUE_TEXT_COLOR, ShockWaveEffect.ShockWaveType.CHAOTIC),
                                            0.75F));

                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(0), AbstractGameAction.AttackEffect.FIRE));
                }
                break;
            case 3:
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new GainBlockAction((AbstractCreature) this, (AbstractCreature) this, 20));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new VulnerablePower((AbstractCreature) AbstractDungeon.player, 1, true), 1));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, 1, true), 1));

                if (AbstractDungeon.ascensionLevel >= 19) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                            (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                            (AbstractPower) new FrailPower((AbstractCreature) AbstractDungeon.player, 1, true), 1));
                }
                break;

            case 4:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.4F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.POISON));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new DrawReductionPower((AbstractCreature) AbstractDungeon.player, 1)));

                if (AbstractDungeon.ascensionLevel >= 19) {
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new MakeTempCardInDiscardAction((AbstractCard) new Slimed(), 2));
                }
                break;
            case 5:
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ShoutAction((AbstractCreature) this, DIALOG[1], 0.5F, 2.0F));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new RemoveDebuffsAction((AbstractCreature) this));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new RemoveSpecificPowerAction((AbstractCreature) this,
                                (AbstractCreature) this, "Shackled"));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new HealAction((AbstractCreature) this,
                        (AbstractCreature) this, this.maxHealth / 2 - this.currentHealth));
                if (AbstractDungeon.ascensionLevel >= 19) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                    (AbstractCreature) this, this.headSlamDmg));
                }
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
        if (this.currentHealth < this.maxHealth / 2 && !this.usedHaste) {
            this.usedHaste = true;
            setMove((byte) 5, Intent.BUFF);

            return;
        }
        if (num < 45) {
            if (!lastTwoMoves((byte) 2)) {
                setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 3, true);
                return;
            }
            getMove(AbstractDungeon.aiRng.random(50, 99));

            return;
        }

        if (num < 80) {
            if (!lastMove((byte) 4)) {
                setMove((byte) 4, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                return;
            }
            if (AbstractDungeon.aiRng.randomBoolean(0.66F)) {
                setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 3, true);
                return;
            }
            setMove((byte) 3, Intent.DEFEND_DEBUFF);

            return;
        }

        if (!lastMove((byte) 3)) {
            setMove((byte) 3, Intent.DEFEND_DEBUFF);
            return;
        }
        getMove(AbstractDungeon.aiRng.random(74));
    }

    public void die() {
        if (!(AbstractDungeon.getCurrRoom()).cannotLose) {
            useFastShakeAnimation(5.0F);
            CardCrawlGame.screenShake.rumble(4.0F);
            super.die();
            onBossVictoryLogic();
            UnlockTracker.hardUnlockOverride("WIZARD");
            UnlockTracker.unlockAchievement("TIME_EATER");
            onFinalBossVictoryLogic();
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\beyond\
 * TimeEater.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

