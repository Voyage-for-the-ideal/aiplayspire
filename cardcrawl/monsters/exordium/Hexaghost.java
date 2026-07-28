package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.unique.BurnIncreaseAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Burn;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderFlashEffect;
import com.megacrit.cardcrawl.vfx.combat.FireballEffect;
import com.megacrit.cardcrawl.vfx.combat.GhostIgniteEffect;
import com.megacrit.cardcrawl.vfx.combat.InflameEffect;
import com.megacrit.cardcrawl.vfx.combat.ScreenOnFireEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class Hexaghost extends AbstractMonster {
    private static final Logger logger = LogManager.getLogger(Hexaghost.class.getName());
    public static final String ID = "Hexaghost";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Hexaghost");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    public static final String IMAGE = "images/monsters/theBottom/boss/ghost/core.png";
    private static final int HP = 250;
    private static final int A_2_HP = 264;
    private static final int SEAR_DMG = 6;
    private static final int INFERNO_DMG = 2;
    private static final int FIRE_TACKLE_DMG = 5;
    private static final int BURN_COUNT = 1;
    private static final int STR_AMT = 2;
    private ArrayList<HexaghostOrb> orbs = new ArrayList<>();
    private static final int A_4_INFERNO_DMG = 3;
    private static final int A_4_FIRE_TACKLE_DMG = 6;
    private static final int A_19_BURN_COUNT = 2;
    private static final int A_19_STR_AMT = 3;
    private int searDmg;
    private int strAmount;
    private int searBurnCount;
    private int strengthenBlockAmt = 12;
    private int fireTackleDmg;
    private int fireTackleCount = 2;
    private int infernoDmg;
    private int infernoHits = 6;
    private static final byte DIVIDER = 1;
    private static final byte TACKLE = 2;
    private static final byte INFLAME = 3;
    private static final String STRENGTHEN_NAME = MOVES[0];
    private static final byte SEAR = 4;
    private static final byte ACTIVATE = 5;
    private static final byte INFERNO = 6;
    private static final String SEAR_NAME = MOVES[1];
    private static final String BURN_NAME = MOVES[2];
    private static final String ACTIVATE_STATE = "Activate";
    private static final String ACTIVATE_ORB = "Activate Orb";
    private static final String DEACTIVATE_ALL_ORBS = "Deactivate";
    private boolean activated = false;
    private boolean burnUpgraded = false;
    private int orbActiveCount = 0;
    private HexaghostBody body;

    public Hexaghost() {
        super(NAME, "Hexaghost", 250, 20.0F, 0.0F, 450.0F, 450.0F, "images/monsters/theBottom/boss/ghost/core.png");
        this.type = EnemyType.BOSS;
        this.body = new HexaghostBody(this);
        this.disposables.add(this.body);
        createOrbs();

        if (AbstractDungeon.ascensionLevel >= 9) {
            setHp(264);
        } else {
            setHp(250);
        }

        if (AbstractDungeon.ascensionLevel >= 19) {
            this.strAmount = 3;
            this.searBurnCount = 2;
            this.fireTackleDmg = 6;
            this.infernoDmg = 3;
        } else if (AbstractDungeon.ascensionLevel >= 4) {
            this.strAmount = 2;
            this.searBurnCount = 1;
            this.fireTackleDmg = 6;
            this.infernoDmg = 3;
        } else {
            this.strAmount = 2;
            this.searBurnCount = 1;
            this.fireTackleDmg = 5;
            this.infernoDmg = 2;
        }

        this.searDmg = 6;
        this.damage.add(new DamageInfo((AbstractCreature) this, this.fireTackleDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.searDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, -1));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.infernoDmg));
    }

    public void usePreBattleAction() {
        UnlockTracker.markBossAsSeen("GHOST");
        CardCrawlGame.music.precacheTempBgm("BOSS_BOTTOM");
    }

    private void createOrbs() {
        this.orbs.add(new HexaghostOrb(-90.0F, 380.0F, this.orbs.size()));
        this.orbs.add(new HexaghostOrb(90.0F, 380.0F, this.orbs.size()));
        this.orbs.add(new HexaghostOrb(160.0F, 250.0F, this.orbs.size()));
        this.orbs.add(new HexaghostOrb(90.0F, 120.0F, this.orbs.size()));
        this.orbs.add(new HexaghostOrb(-90.0F, 120.0F, this.orbs.size()));
        this.orbs.add(new HexaghostOrb(-160.0F, 250.0F, this.orbs.size()));
    }
    public void takeTurn() {
        int d, i;
        Burn c;
        int j;
        switch (this.nextMove) {

            case 5:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "Activate"));
                d = AbstractDungeon.player.currentHealth / 12 + 1;

                ((DamageInfo) this.damage.get(2)).base = d;

                applyPowers();
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(2)).base, 6, true);
                return;

            case 1:
                for (i = 0; i < 6; i++) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new VFXAction((AbstractCreature) this,
                                    (AbstractGameEffect) new GhostIgniteEffect(AbstractDungeon.player.hb.cX +

                                            MathUtils.random(-120.0F, 120.0F) * Settings.scale,
                                            AbstractDungeon.player.hb.cY +
                                                    MathUtils.random(-120.0F, 120.0F) * Settings.scale),
                                    0.05F));

                    if (MathUtils.randomBoolean()) {
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new SFXAction("GHOST_ORB_IGNITE_1", 0.3F));
                    } else {
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new SFXAction("GHOST_ORB_IGNITE_2", 0.3F));
                    }
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(2), AbstractGameAction.AttackEffect.BLUNT_HEAVY, true));
                }
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ChangeStateAction(this, "Deactivate"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
                return;

            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new BorderFlashEffect(Color.CHARTREUSE)));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.FIRE));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.FIRE));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ChangeStateAction(this, "Activate Orb"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
                return;

            case 4:
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new VFXAction((AbstractGameEffect) new FireballEffect(this.hb.cX,
                                this.hb.cY, AbstractDungeon.player.hb.cX, AbstractDungeon.player.hb.cY), 0.5F));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.FIRE));
                c = new Burn();
                if (this.burnUpgraded) {
                    c.upgrade();
                }
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new MakeTempCardInDiscardAction((AbstractCard) c, this.searBurnCount));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ChangeStateAction(this, "Activate Orb"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
                return;

            case 3:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction((AbstractCreature) this,
                        (AbstractGameEffect) new InflameEffect((AbstractCreature) this), 0.5F));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                (AbstractCreature) this, this.strengthenBlockAmt));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) this, (AbstractCreature) this,
                        (AbstractPower) new StrengthPower((AbstractCreature) this, this.strAmount), this.strAmount));

                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ChangeStateAction(this, "Activate Orb"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
                return;

            case 6:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction((AbstractCreature) this,
                        (AbstractGameEffect) new ScreenOnFireEffect(), 1.0F));
                for (j = 0; j < this.infernoHits; j++) {
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(3), AbstractGameAction.AttackEffect.FIRE));
                }
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new BurnIncreaseAction());
                if (!this.burnUpgraded) {
                    this.burnUpgraded = true;
                }
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ChangeStateAction(this, "Deactivate"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
                return;
        }
        logger.info("ERROR: Default Take Turn was called on " + this.name);
    }

    protected void getMove(int num) {
        if (!this.activated) {
            this.activated = true;
            setMove((byte) 5, Intent.UNKNOWN);
        } else {

            switch (this.orbActiveCount) {
                case 0:
                    setMove(SEAR_NAME, (byte) 4, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                    break;
                case 1:
                    setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, this.fireTackleCount,
                            true);
                    break;
                case 2:
                    setMove(SEAR_NAME, (byte) 4, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                    break;
                case 3:
                    setMove(STRENGTHEN_NAME, (byte) 3, Intent.DEFEND_BUFF);
                    break;
                case 4:
                    setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, this.fireTackleCount,
                            true);
                    break;
                case 5:
                    setMove(SEAR_NAME, (byte) 4, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                    break;
                case 6:
                    setMove(BURN_NAME, (byte) 6, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(3)).base,
                            this.infernoHits, true);
                    break;
            }
        }
    }

    public void changeState(String stateName) {
        switch (stateName) {

            case "Activate":
                if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
                    CardCrawlGame.music.unsilenceBGM();
                    AbstractDungeon.scene.fadeOutAmbiance();
                    CardCrawlGame.music.playPrecachedTempBgm();
                }

                for (HexaghostOrb orb : this.orbs) {
                    orb.activate(this.drawX + this.animX, this.drawY + this.animY);
                }
                this.orbActiveCount = 6;
                this.body.targetRotationSpeed = 120.0F;
                break;

            case "Activate Orb":
                for (HexaghostOrb orb : this.orbs) {
                    if (!orb.activated) {
                        orb.activate(this.drawX + this.animX, this.drawY + this.animY);
                        break;
                    }
                }
                this.orbActiveCount++;
                if (this.orbActiveCount == 6) {
                    setMove(BURN_NAME, (byte) 6, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(3)).base,
                            this.infernoHits, true);
                }
                break;

            case "Deactivate":
                for (HexaghostOrb orb : this.orbs) {
                    orb.deactivate();
                }
                CardCrawlGame.sound.play("CARD_EXHAUST", 0.2F);
                CardCrawlGame.sound.play("CARD_EXHAUST", 0.2F);
                this.orbActiveCount = 0;
                break;
        }
    }

    public void die() {
        useFastShakeAnimation(5.0F);
        CardCrawlGame.screenShake.rumble(4.0F);
        super.die();

        for (HexaghostOrb orb : this.orbs) {
            orb.hide();
        }

        onBossVictoryLogic();
        UnlockTracker.hardUnlockOverride("GHOST");
        UnlockTracker.unlockAchievement("GHOST_GUARDIAN");
    }

    public void update() {
        super.update();
        this.body.update();

        for (HexaghostOrb orb : this.orbs) {
            orb.update(this.drawX + this.animX, this.drawY + this.animY);
        }
    }

    public void render(SpriteBatch sb) {
        this.body.render(sb);
        super.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\exordium\
 * Hexaghost.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

