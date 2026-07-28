package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.ShoutAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.unique.SummonGremlinAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.MinionPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

import java.util.ArrayList;

public class GremlinLeader extends AbstractMonster {
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("GremlinLeader");
    public static final String ID = "GremlinLeader";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    private static final int HP_MIN = 140;
    private static final int HP_MAX = 148;
    private static final int A_2_HP_MIN = 145;
    private static final int A_2_HP_MAX = 155;
    public static final String ENC_NAME = "Gremlin Leader Combat";
    public AbstractMonster[] gremlins = new AbstractMonster[3];
    public static final float[] POSX = new float[] {-366.0F, -170.0F, -532.0F };
    public static final float[] POSY = new float[] {-4.0F, 6.0F, 0.0F };

    private static final byte RALLY = 2;

    private static final String RALLY_NAME = MOVES[0];

    private static final byte ENCOURAGE = 3;
    private static final int STR_AMT = 3;
    private static final int BLOCK_AMT = 6;
    private static final int A_2_STR_AMT = 4;
    private static final int A_18_STR_AMT = 5;
    private static final int A_18_BLOCK_AMT = 10;
    private int strAmt;
    private int blockAmt;
    private static final byte STAB = 4;
    private int STAB_DMG = 6, STAB_AMT = 3;

    public GremlinLeader() {
        super(NAME, "GremlinLeader", 148, 0.0F, -15.0F, 200.0F, 310.0F, null, 35.0F, 0.0F);
        this.type = EnemyType.ELITE;

        loadAnimation("images/monsters/theCity/gremlinleader/skeleton.atlas",
                "images/monsters/theCity/gremlinleader/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.1F);
        e.setTimeScale(0.8F);

        if (AbstractDungeon.ascensionLevel >= 8) {
            setHp(145, 155);
        } else {
            setHp(140, 148);
        }

        if (AbstractDungeon.ascensionLevel >= 18) {
            this.strAmt = 5;
            this.blockAmt = 10;
        } else if (AbstractDungeon.ascensionLevel >= 3) {
            this.strAmt = 4;
            this.blockAmt = 6;
        } else {
            this.strAmt = 3;
            this.blockAmt = 6;
        }

        this.dialogX = -80.0F * Settings.scale;
        this.dialogY = 50.0F * Settings.scale;
        this.damage.add(new DamageInfo((AbstractCreature) this, this.STAB_DMG));
    }

    public void usePreBattleAction() {
        this.gremlins[0] = (AbstractDungeon.getMonsters()).monsters.get(0);
        this.gremlins[1] = (AbstractDungeon.getMonsters()).monsters.get(1);
        this.gremlins[2] = null;

        for (AbstractMonster m : this.gremlins) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m,
                    (AbstractCreature) m, (AbstractPower) new MinionPower((AbstractCreature) this)));
        }
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "CALL"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SummonGremlinAction(this.gremlins));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SummonGremlinAction(this.gremlins));
                break;
            case 3:
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ShoutAction((AbstractCreature) this, getEncourageQuote()));
                for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                    if (m == this) {
                        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                                (AbstractCreature) m, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) m, this.strAmt), this.strAmt));
                        continue;
                    }
                    if (!m.isDying) {
                        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                                (AbstractCreature) m, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) m, this.strAmt), this.strAmt));

                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) m,
                                        (AbstractCreature) this, this.blockAmt));
                    }
                }
                break;

            case 4:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_HORIZONTAL, true));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_VERTICAL, true));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_HEAVY));
                break;
        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    private String getEncourageQuote() {
        ArrayList<String> list = new ArrayList<>();
        list.add(DIALOG[0]);
        list.add(DIALOG[1]);
        list.add(DIALOG[2]);

        return list.get(AbstractDungeon.aiRng.random(0, list.size() - 1));
    }

    protected void getMove(int num) {
        if (numAliveGremlins() == 0) {
            if (num < 75) {
                if (!lastMove((byte) 2)) {
                    setMove(RALLY_NAME, (byte) 2, Intent.UNKNOWN);
                } else {
                    setMove((byte) 4, Intent.ATTACK, this.STAB_DMG, this.STAB_AMT, true);
                }

            } else if (!lastMove((byte) 4)) {
                setMove((byte) 4, Intent.ATTACK, this.STAB_DMG, this.STAB_AMT, true);
            } else {
                setMove(RALLY_NAME, (byte) 2, Intent.UNKNOWN);

            }

        } else if (numAliveGremlins() < 2) {
            if (num < 50) {
                if (!lastMove((byte) 2)) {
                    setMove(RALLY_NAME, (byte) 2, Intent.UNKNOWN);
                } else {
                    getMove(AbstractDungeon.aiRng.random(50, 99));
                }
            } else if (num < 80) {
                if (!lastMove((byte) 3)) {
                    setMove((byte) 3, Intent.DEFEND_BUFF);
                } else {
                    setMove((byte) 4, Intent.ATTACK, this.STAB_DMG, this.STAB_AMT, true);
                }

            } else if (!lastMove((byte) 4)) {
                setMove((byte) 4, Intent.ATTACK, this.STAB_DMG, this.STAB_AMT, true);
            } else {
                getMove(AbstractDungeon.aiRng.random(0, 80));

            }

        } else if (numAliveGremlins() > 1) {
            if (num < 66) {
                if (!lastMove((byte) 3)) {
                    setMove((byte) 3, Intent.DEFEND_BUFF);
                } else {
                    setMove((byte) 4, Intent.ATTACK, this.STAB_DMG, this.STAB_AMT, true);
                }

            } else if (!lastMove((byte) 4)) {
                setMove((byte) 4, Intent.ATTACK, this.STAB_DMG, this.STAB_AMT, true);
            } else {
                setMove((byte) 3, Intent.DEFEND_BUFF);
            }
        }
    }

    private int numAliveGremlins() {
        int count = 0;
        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            if (m != null && m != this && !m.isDying) {
                count++;
            }
        }
        return count;
    }

    public void changeState(String stateName) {
        switch (stateName) {
            case "ATTACK":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
            case "CALL":
                this.state.setAnimation(0, "Call", false);
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

    public void die() {
        super.die();
        boolean first = true;
        for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
            if (!m.isDying) {
                if (first) {
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new ShoutAction((AbstractCreature) m, DIALOG[3], 0.5F, 1.2F));
                    first = false;
                    continue;
                }
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ShoutAction((AbstractCreature) m, DIALOG[4], 0.5F, 1.2F));
            }
        }

        for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
            if (!m.isDying)
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new EscapeAction(m));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * GremlinLeader.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

