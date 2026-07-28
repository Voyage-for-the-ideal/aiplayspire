package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateFastAttackAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.utility.HideHealthBarAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.TextAboveCreatureAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.ArtifactPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.InflameEffect;
import com.megacrit.cardcrawl.vfx.combat.LaserBeamEffect;

public class BronzeAutomaton extends AbstractMonster {
    public static final String ID = "BronzeAutomaton";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack
            .getMonsterStrings("BronzeAutomaton");
    public static final String NAME = monsterStrings.NAME;
    private static final String[] MOVES = monsterStrings.MOVES;
    private static final int HP = 300;
    private static final int A_2_HP = 320;
    private static final byte FLAIL = 1;
    private static final byte HYPER_BEAM = 2;
    private static final byte STUNNED = 3;
    private static final byte SPAWN_ORBS = 4;
    private static final byte BOOST = 5;
    private static final String BEAM_NAME = MOVES[0];

    private static final int FLAIL_DMG = 7;

    private static final int BEAM_DMG = 45;

    private static final int A_2_FLAIL_DMG = 8;
    private static final int A_2_BEAM_DMG = 50;
    private int flailDmg;
    private int beamDmg;
    private int numTurns = 0;
    private static final int BLOCK_AMT = 9;
    private static final int STR_AMT = 3;
    private static final int A_2_BLOCK_AMT = 12;
    private static final int A_2_STR_AMT = 4;
    private int strAmt;
    private int blockAmt;
    private boolean firstTurn = true;

    public BronzeAutomaton() {
        super(NAME, "BronzeAutomaton", 300, 0.0F, -30.0F, 270.0F, 400.0F, null, -50.0F, 20.0F);
        loadAnimation("images/monsters/theCity/automaton/skeleton.atlas",
                "images/monsters/theCity/automaton/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());

        this.type = EnemyType.BOSS;
        this.dialogX = -100.0F * Settings.scale;
        this.dialogY = 10.0F * Settings.scale;

        if (AbstractDungeon.ascensionLevel >= 9) {
            setHp(320);
            this.blockAmt = 12;
        } else {
            setHp(300);
            this.blockAmt = 9;
        }

        if (AbstractDungeon.ascensionLevel >= 4) {
            this.flailDmg = 8;
            this.beamDmg = 50;
            this.strAmt = 4;
        } else {
            this.flailDmg = 7;
            this.beamDmg = 45;
            this.strAmt = 3;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.flailDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.beamDmg));
    }

    public void usePreBattleAction() {
        CardCrawlGame.music.unsilenceBGM();
        AbstractDungeon.scene.fadeOutAmbiance();
        AbstractDungeon.getCurrRoom().playBgmInstantly("BOSS_CITY");
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new ArtifactPower((AbstractCreature) this, 3)));
        UnlockTracker.markBossAsSeen("AUTOMATON");
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 4:
                if (MathUtils.randomBoolean()) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("AUTOMATON_ORB_SPAWN",
                            MathUtils.random(-0.1F, 0.1F)));
                } else {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new SFXAction("MONSTER_AUTOMATON_SUMMON",
                                    MathUtils.random(-0.1F, 0.1F)));
                }
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new SpawnMonsterAction(new BronzeOrb(-300.0F, 200.0F, 0), true));
                if (MathUtils.randomBoolean()) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("AUTOMATON_ORB_SPAWN",
                            MathUtils.random(-0.1F, 0.1F)));
                } else {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new SFXAction("MONSTER_AUTOMATON_SUMMON",
                                    MathUtils.random(-0.1F, 0.1F)));
                }
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new SpawnMonsterAction(new BronzeOrb(200.0F, 130.0F, 1), true));
                break;
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateFastAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
                break;
            case 5:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                (AbstractCreature) this, this.blockAmt));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) this, this.strAmt), this.strAmt));
                break;

            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new LaserBeamEffect(this.hb.cX, this.hb.cY + 60.0F * Settings.scale),
                        1.5F));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.NONE));
                break;
            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new TextAboveCreatureAction((AbstractCreature) this,
                                TextAboveCreatureAction.TextType.STUNNED));
                break;
        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        if (this.firstTurn) {
            setMove((byte) 4, Intent.UNKNOWN);
            this.firstTurn = false;

            return;
        }
        if (this.numTurns == 4) {
            setMove(BEAM_NAME, (byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
            this.numTurns = 0;

            return;
        }

        if (lastMove((byte) 2)) {
            if (AbstractDungeon.ascensionLevel >= 19) {
                setMove((byte) 5, Intent.DEFEND_BUFF);
                return;
            }
            setMove((byte) 3, Intent.STUN);

            return;
        }

        if (lastMove((byte) 3) || lastMove((byte) 5) || lastMove((byte) 4)) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 2, true);
        } else {
            setMove((byte) 5, Intent.DEFEND_BUFF);
        }

        this.numTurns++;
    }

    public void die() {
        useFastShakeAnimation(5.0F);
        CardCrawlGame.screenShake.rumble(4.0F);
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
        UnlockTracker.hardUnlockOverride("AUTOMATON");
        UnlockTracker.unlockAchievement("AUTOMATON");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * BronzeAutomaton.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

