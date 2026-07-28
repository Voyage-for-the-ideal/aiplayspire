package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateShakeAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.unique.CanLoseAction;
import com.megacrit.cardcrawl.actions.unique.CannotLoseAction;
import com.megacrit.cardcrawl.actions.utility.HideHealthBarAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.TextAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Slimed;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.SlimeAnimListener;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.PoisonPower;
import com.megacrit.cardcrawl.powers.SplitPower;
import com.megacrit.cardcrawl.powers.WeakPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class AcidSlime_L
        extends AbstractMonster {
    public static final String ID = "AcidSlime_L";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("AcidSlime_L");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;

    private static final String WOUND_NAME = MOVES[0];
    private static final String SPLIT_NAME = MOVES[1];
    private static final String WEAK_NAME = MOVES[2];

    public static final int HP_MIN = 65;

    public static final int HP_MAX = 69;
    public static final int A_2_HP_MIN = 68;
    public static final int A_2_HP_MAX = 72;
    public static final int W_TACKLE_DMG = 11;
    public static final int N_TACKLE_DMG = 16;
    public static final int A_2_W_TACKLE_DMG = 12;
    public static final int A_2_N_TACKLE_DMG = 18;

    public AcidSlime_L(float x, float y) {
        this(x, y, 0, 69);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(68, 72);
        } else {
            setHp(65, 69);
        }
    }
    public static final int WEAK_TURNS = 2;
    public static final int WOUND_COUNT = 2;
    private static final byte SLIME_TACKLE = 1;
    private static final byte NORMAL_TACKLE = 2;
    private static final byte SPLIT = 3;
    private static final byte WEAK_LICK = 4;
    private float saveX;
    private float saveY;
    private boolean splitTriggered;
    public AcidSlime_L(float x, float y, int poisonAmount, int newHealth) {
        super(NAME, "AcidSlime_L", newHealth, 0.0F, 0.0F, 300.0F, 180.0F, null, x, y, true);

        this.saveX = x;
        this.saveY = y;
        this.splitTriggered = false;

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.damage.add(new DamageInfo((AbstractCreature) this, 12));
            this.damage.add(new DamageInfo((AbstractCreature) this, 18));
        } else {
            this.damage.add(new DamageInfo((AbstractCreature) this, 11));
            this.damage.add(new DamageInfo((AbstractCreature) this, 16));
        }

        this.powers.add(new SplitPower((AbstractCreature) this));

        if (poisonAmount >= 1) {
            this.powers.add(new PoisonPower((AbstractCreature) this, (AbstractCreature) this, poisonAmount));
        }

        loadAnimation("images/monsters/theBottom/slimeL/skeleton.atlas",
                "images/monsters/theBottom/slimeL/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.state.addListener((AnimationState.AnimationStateListener) new SlimeAnimListener());
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 4:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, 2, true), 2));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
                break;
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("MONSTER_SLIME_ATTACK"));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new MakeTempCardInDiscardAction((AbstractCard) new Slimed(), 2));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
                break;
            case 2:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
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

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new SpawnMonsterAction(new AcidSlime_M(this.saveX - 134.0F, this.saveY +

                                MathUtils.random(-4.0F, 4.0F), 0, this.currentHealth), false));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new SpawnMonsterAction(new AcidSlime_M(this.saveX + 134.0F, this.saveY +

                                MathUtils.random(-4.0F, 4.0F), 0, this.currentHealth), false));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new CanLoseAction());
                setMove(SPLIT_NAME, (byte) 3, Intent.UNKNOWN);
                break;
        }
    }

    public void damage(DamageInfo info) {
        super.damage(info);

        if (!this.isDying && this.currentHealth <= this.maxHealth / 2.0F && this.nextMove != 3
                && !this.splitTriggered) {
            setMove(SPLIT_NAME, (byte) 3, Intent.UNKNOWN);
            createIntent();
            AbstractDungeon.actionManager
                    .addToBottom((AbstractGameAction) new TextAboveCreatureAction((AbstractCreature) this,
                            TextAboveCreatureAction.TextType.INTERRUPTED));
            AbstractDungeon.actionManager
                    .addToBottom((AbstractGameAction) new SetMoveAction(this, SPLIT_NAME, (byte) 3, Intent.UNKNOWN));
            this.splitTriggered = true;
        }
    }

    protected void getMove(int num) {
        if (AbstractDungeon.ascensionLevel >= 17) {

            if (num < 40) {
                if (lastTwoMoves((byte) 1)) {
                    if (AbstractDungeon.aiRng.randomBoolean(0.6F)) {
                        setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
                    } else {
                        setMove(WEAK_NAME, (byte) 4, Intent.DEBUFF);
                    }
                } else {
                    setMove(WOUND_NAME, (byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
                }

            } else if (num < 70) {
                if (lastTwoMoves((byte) 2)) {
                    if (AbstractDungeon.aiRng.randomBoolean(0.6F)) {
                        setMove(WOUND_NAME, (byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
                    } else {
                        setMove(WEAK_NAME, (byte) 4, Intent.DEBUFF);
                    }
                } else {
                    setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);

                }

            } else if (lastMove((byte) 4)) {
                if (AbstractDungeon.aiRng.randomBoolean(0.4F)) {
                    setMove(WOUND_NAME, (byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
                } else {
                    setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
                }
            } else {
                setMove(WEAK_NAME, (byte) 4, Intent.DEBUFF);

            }

        } else if (num < 30) {
            if (lastTwoMoves((byte) 1)) {
                if (AbstractDungeon.aiRng.randomBoolean()) {
                    setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
                } else {
                    setMove(WEAK_NAME, (byte) 4, Intent.DEBUFF);
                }
            } else {
                setMove(WOUND_NAME, (byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
            }

        } else if (num < 70) {
            if (lastMove((byte) 2)) {
                if (AbstractDungeon.aiRng.randomBoolean(0.4F)) {
                    setMove(WOUND_NAME, (byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
                } else {
                    setMove(WEAK_NAME, (byte) 4, Intent.DEBUFF);
                }
            } else {
                setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);

            }

        } else if (lastTwoMoves((byte) 4)) {
            if (AbstractDungeon.aiRng.randomBoolean(0.4F)) {
                setMove(WOUND_NAME, (byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
            } else {
                setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
            }
        } else {
            setMove(WEAK_NAME, (byte) 4, Intent.DEBUFF);
        }
    }

    public void die() {
        super.die();

        for (AbstractGameAction a : AbstractDungeon.actionManager.actions) {
            if (a instanceof SpawnMonsterAction) {
                return;
            }
        }

        if (AbstractDungeon.getMonsters().areMonstersBasicallyDead() &&
                AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
            onBossVictoryLogic();
            UnlockTracker.hardUnlockOverride("SLIME");
            UnlockTracker.unlockAchievement("SLIME_BOSS");
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\exordium\
 * AcidSlime_L.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

