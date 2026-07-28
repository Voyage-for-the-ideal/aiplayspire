package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.utility.HideHealthBarAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.*;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.CollectorCurseEffect;
import com.megacrit.cardcrawl.vfx.GlowyFireEyesEffect;
import com.megacrit.cardcrawl.vfx.StaffFireEffect;
import com.megacrit.cardcrawl.vfx.combat.InflameEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class TheCollector extends AbstractMonster {
    private static final Logger logger = LogManager.getLogger(TheCollector.class.getName());
    public static final String ID = "TheCollector";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("TheCollector");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    public static final int HP = 282;

    public static final int A_2_HP = 300;

    private static final int FIREBALL_DMG = 18;
    private static final int STR_AMT = 3;
    private static final int BLOCK_AMT = 15;
    private static final int A_2_FIREBALL_DMG = 21;
    private int turnsTaken = 0;
    private static final int A_2_STR_AMT = 4;
    private static final int A_2_BLOCK_AMT = 18;
    private int rakeDmg;
    private int strAmt;
    private int blockAmt;
    private int megaDebuffAmt;
    private static final int MEGA_DEBUFF_AMT = 3;
    private float spawnX = -100.0F;
    private float fireTimer = 0.0F;

    private static final float FIRE_TIME = 0.07F;
    private boolean ultUsed = false;
    private boolean initialSpawn = true;
    private HashMap<Integer, AbstractMonster> enemySlots = new HashMap<>();
    private static final byte SPAWN = 1;
    private static final byte FIREBALL = 2;
    private static final byte BUFF = 3;
    private static final byte MEGA_DEBUFF = 4;
    private static final byte REVIVE = 5;

    public TheCollector() {
        super(NAME, "TheCollector", 282, 15.0F, -40.0F, 300.0F, 390.0F, null, 60.0F, 135.0F);

        this.dialogX = -90.0F * Settings.scale;
        this.dialogY = 10.0F * Settings.scale;
        this.type = EnemyType.BOSS;

        if (AbstractDungeon.ascensionLevel >= 9) {
            setHp(300);
            this.blockAmt = 18;
        } else {
            setHp(282);
            this.blockAmt = 15;
        }

        if (AbstractDungeon.ascensionLevel >= 19) {
            this.rakeDmg = 21;
            this.strAmt = 5;
            this.megaDebuffAmt = 5;
        } else if (AbstractDungeon.ascensionLevel >= 4) {
            this.rakeDmg = 21;
            this.strAmt = 4;
            this.megaDebuffAmt = 3;
        } else {
            this.rakeDmg = 18;
            this.strAmt = 3;
            this.megaDebuffAmt = 3;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.rakeDmg));

        loadAnimation("images/monsters/theCity/collector/skeleton.atlas",
                "images/monsters/theCity/collector/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
    }

    public void usePreBattleAction() {
        CardCrawlGame.music.unsilenceBGM();
        AbstractDungeon.scene.fadeOutAmbiance();
        AbstractDungeon.getCurrRoom().playBgmInstantly("BOSS_CITY");
        UnlockTracker.markBossAsSeen("COLLECTOR");
    }

    public void takeTurn() {
        int i;
        switch (this.nextMove) {
            case 1:
                for (i = 1; i < 3; i++) {
                    AbstractMonster m = new TorchHead(this.spawnX + -185.0F * i, MathUtils.random(-5.0F, 25.0F));
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new SFXAction("MONSTER_COLLECTOR_SUMMON"));
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SpawnMonsterAction(m, true));
                    this.enemySlots.put(Integer.valueOf(i), m);
                }

                this.initialSpawn = false;
                break;
            case 2:
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.FIRE));
                break;
            case 3:
                if (AbstractDungeon.ascensionLevel >= 19) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                    (AbstractCreature) this, this.blockAmt + 5));
                } else {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                    (AbstractCreature) this, this.blockAmt));
                }
                for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                    if (!m.isDead && !m.isDying && !m.isEscaping) {
                        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                                (AbstractCreature) m, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) m, this.strAmt), this.strAmt));
                    }
                }
                break;

            case 4:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[0]));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new SFXAction("MONSTER_COLLECTOR_DEBUFF"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new CollectorCurseEffect(AbstractDungeon.player.hb.cX,
                                AbstractDungeon.player.hb.cY),
                        2.0F));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, this.megaDebuffAmt,
                                true),
                        this.megaDebuffAmt));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new VulnerablePower((AbstractCreature) AbstractDungeon.player,
                                this.megaDebuffAmt, true),
                        this.megaDebuffAmt));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new FrailPower((AbstractCreature) AbstractDungeon.player, this.megaDebuffAmt,
                                true),
                        this.megaDebuffAmt));

                this.ultUsed = true;
                break;

            case 5:
                for (Map.Entry<Integer, AbstractMonster> m : this.enemySlots.entrySet()) {
                    if (((AbstractMonster) m.getValue()).isDying) {
                        AbstractMonster newMonster = new TorchHead(
                                this.spawnX + -185.0F * ((Integer) m.getKey()).intValue(),
                                MathUtils.random(-5.0F, 25.0F));
                        int key = ((Integer) m.getKey()).intValue();
                        this.enemySlots.put(Integer.valueOf(key), newMonster);
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new SpawnMonsterAction(newMonster, true));
                    }
                }
                break;
            default:
                logger.info("ERROR: Default Take Turn was called on " + this.name);
                break;
        }
        this.turnsTaken++;
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        if (this.initialSpawn) {
            setMove((byte) 1, Intent.UNKNOWN);

            return;
        }
        if (this.turnsTaken >= 3 && !this.ultUsed) {
            setMove((byte) 4, Intent.STRONG_DEBUFF);

            return;
        }

        if (num <= 25 && isMinionDead() && !lastMove((byte) 5)) {
            setMove((byte) 5, Intent.UNKNOWN);

            return;
        }
        if (num <= 70 && !lastTwoMoves((byte) 2)) {
            setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);

            return;
        }
        if (!lastMove((byte) 3)) {
            setMove((byte) 3, Intent.DEFEND_BUFF);
        } else {
            setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
        }
    }

    private boolean isMinionDead() {
        for (Map.Entry<Integer, AbstractMonster> m : this.enemySlots.entrySet()) {
            if (((AbstractMonster) m.getValue()).isDying) {
                return true;
            }
        }

        return false;
    }

    public void update() {
        super.update();
        if (!this.isDying) {
            this.fireTimer -= Gdx.graphics.getDeltaTime();
            if (this.fireTimer < 0.0F) {
                this.fireTimer = 0.07F;
                AbstractDungeon.effectList.add(new GlowyFireEyesEffect(this.skeleton

                        .getX() + this.skeleton.findBone("lefteyefireslot").getX(),
                        this.skeleton
                                .getY() + this.skeleton.findBone("lefteyefireslot").getY() + 140.0F * Settings.scale));

                AbstractDungeon.effectList.add(new GlowyFireEyesEffect(this.skeleton

                        .getX() + this.skeleton.findBone("righteyefireslot").getX(),
                        this.skeleton
                                .getY() + this.skeleton.findBone("righteyefireslot").getY() + 140.0F * Settings.scale));

                AbstractDungeon.effectList.add(new StaffFireEffect(this.skeleton

                        .getX() + this.skeleton.findBone("fireslot").getX() - 120.0F * Settings.scale, this.skeleton
                                .getY() + this.skeleton.findBone("fireslot").getY() + 390.0F * Settings.scale));
            }
        }
    }

    public void die() {
        useFastShakeAnimation(5.0F);
        CardCrawlGame.screenShake.rumble(4.0F);
        this.deathTimer += 1.5F;
        super.die();
        onBossVictoryLogic();

        for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
            if (!m.isDead && !m.isDying) {
                AbstractDungeon.actionManager
                        .addToTop((AbstractGameAction) new HideHealthBarAction((AbstractCreature) m));
                AbstractDungeon.actionManager.addToTop((AbstractGameAction) new SuicideAction(m));
                AbstractDungeon.actionManager.addToTop((AbstractGameAction) new VFXAction((AbstractCreature) m,
                        (AbstractGameEffect) new InflameEffect((AbstractCreature) m), 0.2F));
            }
        }
        UnlockTracker.hardUnlockOverride("COLLECTOR");
        UnlockTracker.unlockAchievement("COLLECTOR");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * TheCollector.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

