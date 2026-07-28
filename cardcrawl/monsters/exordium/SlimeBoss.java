package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.*;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.unique.CanLoseAction;
import com.megacrit.cardcrawl.actions.unique.CannotLoseAction;
import com.megacrit.cardcrawl.actions.utility.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Slimed;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.SplitPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.WeightyImpactEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SlimeBoss
        extends AbstractMonster {
    private static final Logger logger = LogManager.getLogger(SlimeBoss.class.getName());
    public static final String ID = "SlimeBoss";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("SlimeBoss");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    public static final int HP = 140;

    public static final int A_2_HP = 150;

    public static final int TACKLE_DAMAGE = 9;

    public static final int SLAM_DAMAGE = 35;

    public static final int A_2_TACKLE_DAMAGE = 10;
    public static final int A_2_SLAM_DAMAGE = 38;
    private static final String SLAM_NAME = MOVES[0], PREP_NAME = MOVES[1], SPLIT_NAME = MOVES[2];
    private int tackleDmg;
    private int slamDmg;
    public static final int STICKY_TURNS = 3;
    private static final byte SLAM = 1;
    private static final byte PREP_SLAM = 2;
    private static final byte SPLIT = 3;
    private static final byte STICKY = 4;
    private static final String STICKY_NAME = MOVES[3];
    private boolean firstTurn = true;

    public SlimeBoss() {
        super(NAME, "SlimeBoss", 140, 0.0F, -30.0F, 400.0F, 350.0F, null, 0.0F, 28.0F);
        this.type = EnemyType.BOSS;

        this.dialogX = -150.0F * Settings.scale;
        this.dialogY = -70.0F * Settings.scale;

        if (AbstractDungeon.ascensionLevel >= 9) {
            setHp(150);
        } else {
            setHp(140);
        }

        if (AbstractDungeon.ascensionLevel >= 4) {
            this.tackleDmg = 10;
            this.slamDmg = 38;
        } else {
            this.tackleDmg = 9;
            this.slamDmg = 35;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.tackleDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.slamDmg));
        this.powers.add(new SplitPower((AbstractCreature) this));

        loadAnimation("images/monsters/theBottom/boss/slime/skeleton.atlas",
                "images/monsters/theBottom/boss/slime/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
    }

    public void usePreBattleAction() {
        if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
            CardCrawlGame.music.unsilenceBGM();
            AbstractDungeon.scene.fadeOutAmbiance();
            AbstractDungeon.getCurrRoom().playBgmInstantly("BOSS_BOTTOM");
        }

        UnlockTracker.markBossAsSeen("SLIME");
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 4:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("MONSTER_SLIME_ATTACK"));
                if (AbstractDungeon.ascensionLevel >= 19) {
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new MakeTempCardInDiscardAction((AbstractCard) new Slimed(), 5));
                } else {

                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new MakeTempCardInDiscardAction((AbstractCard) new Slimed(), 3));
                }

                setMove(PREP_NAME, (byte) 2, Intent.UNKNOWN);
                break;
            case 2:
                playSfx();
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ShoutAction((AbstractCreature) this, DIALOG[0], 1.0F, 2.0F));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ShakeScreenAction(0.3F,
                        ScreenShake.ShakeDur.LONG, ScreenShake.ShakeIntensity.LOW));

                setMove(SLAM_NAME, (byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
                break;
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateJumpAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new WeightyImpactEffect(AbstractDungeon.player.hb.cX,
                                AbstractDungeon.player.hb.cY, new Color(0.1F, 1.0F, 0.1F, 0.0F))));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.8F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.POISON));
                setMove(STICKY_NAME, (byte) 4, Intent.STRONG_DEBUFF);
                break;
            case 3:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new CannotLoseAction());
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateShakeAction((AbstractCreature) this, 1.0F, 0.1F));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new HideHealthBarAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SuicideAction(this, false));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(1.0F));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("SLIME_SPLIT"));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SpawnMonsterAction(
                        new SpikeSlime_L(-385.0F, 20.0F, 0, this.currentHealth), false));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SpawnMonsterAction(
                        new AcidSlime_L(120.0F, -8.0F, 0, this.currentHealth), false));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new CanLoseAction());
                setMove(SPLIT_NAME, (byte) 3, Intent.UNKNOWN);
                break;
        }
    }

    private void playSfx() {
        int roll = MathUtils.random(1);
        if (roll == 0) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_SLIMEBOSS_1A"));
        } else {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_SLIMEBOSS_1B"));
        }
    }

    public void damage(DamageInfo info) {
        super.damage(info);

        if (!this.isDying && this.currentHealth <= this.maxHealth / 2.0F && this.nextMove != 3) {
            logger.info("SPLIT");
            setMove(SPLIT_NAME, (byte) 3, Intent.UNKNOWN);
            createIntent();
            AbstractDungeon.actionManager
                    .addToBottom((AbstractGameAction) new TextAboveCreatureAction((AbstractCreature) this,
                            TextAboveCreatureAction.TextType.INTERRUPTED));
            AbstractDungeon.actionManager
                    .addToBottom((AbstractGameAction) new SetMoveAction(this, SPLIT_NAME, (byte) 3, Intent.UNKNOWN));
        }
    }

    protected void getMove(int num) {
        if (this.firstTurn) {
            this.firstTurn = false;
            setMove(STICKY_NAME, (byte) 4, Intent.STRONG_DEBUFF);
            return;
        }
    }

    public void die() {
        super.die();
        CardCrawlGame.sound.play("VO_SLIMEBOSS_2A");

        for (AbstractGameAction a : AbstractDungeon.actionManager.actions) {
            if (a instanceof SpawnMonsterAction) {
                return;
            }
        }

        if (this.currentHealth <= 0) {
            useFastShakeAnimation(5.0F);
            CardCrawlGame.screenShake.rumble(4.0F);
            onBossVictoryLogic();
            UnlockTracker.hardUnlockOverride("SLIME");
            UnlockTracker.unlockAchievement("SLIME_BOSS");
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\exordium\
 * SlimeBoss.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

