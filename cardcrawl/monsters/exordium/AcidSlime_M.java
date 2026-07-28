package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
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
import com.megacrit.cardcrawl.powers.WeakPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class AcidSlime_M extends AbstractMonster {
    public static final String ID = "AcidSlime_M";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("AcidSlime_M");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final String WOUND_NAME = MOVES[0];
    private static final String WEAK_NAME = MOVES[1];

    public static final int HP_MIN = 28;

    public static final int HP_MAX = 32;
    public static final int A_2_HP_MIN = 29;
    public static final int A_2_HP_MAX = 34;
    public static final int W_TACKLE_DMG = 7;
    public static final int WOUND_COUNT = 1;

    public AcidSlime_M(float x, float y) {
        this(x, y, 0, 32);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(29, 34);
        } else {
            setHp(28, 32);
        }
    }
    public static final int N_TACKLE_DMG = 10;
    public static final int A_2_W_TACKLE_DMG = 8;
    public static final int A_2_N_TACKLE_DMG = 12;
    public static final int WEAK_TURNS = 1;
    private static final byte WOUND_TACKLE = 1;
    private static final byte NORMAL_TACKLE = 2;
    private static final byte WEAK_LICK = 4;
    public AcidSlime_M(float x, float y, int poisonAmount, int newHealth) {
        super(NAME, "AcidSlime_M", newHealth, 0.0F, 0.0F, 170.0F, 130.0F, null, x, y, true);

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.damage.add(new DamageInfo((AbstractCreature) this, 8));
            this.damage.add(new DamageInfo((AbstractCreature) this, 12));
        } else {
            this.damage.add(new DamageInfo((AbstractCreature) this, 7));
            this.damage.add(new DamageInfo((AbstractCreature) this, 10));
        }

        if (poisonAmount >= 1) {
            this.powers.add(new PoisonPower((AbstractCreature) this, (AbstractCreature) this, poisonAmount));
        }

        loadAnimation("images/monsters/theBottom/slimeM/skeleton.atlas",
                "images/monsters/theBottom/slimeM/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
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
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, 1, true), 1));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
                break;
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new MakeTempCardInDiscardAction((AbstractCard) new Slimed(), 1));
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
        }
    }

    protected void getMove(int num) {
        if (AbstractDungeon.ascensionLevel >= 17) {

            if (num < 40) {
                if (lastTwoMoves((byte) 1)) {
                    if (AbstractDungeon.aiRng.randomBoolean()) {
                        setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
                    } else {
                        setMove(WEAK_NAME, (byte) 4, Intent.DEBUFF);
                    }
                } else {
                    setMove(WOUND_NAME, (byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
                }

            } else if (num < 80) {
                if (lastTwoMoves((byte) 2)) {
                    if (AbstractDungeon.aiRng.randomBoolean(0.5F)) {
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
 * AcidSlime_M.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

