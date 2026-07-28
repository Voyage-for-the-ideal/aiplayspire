package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.unique.GainBlockRandomMonsterAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Centurion extends AbstractMonster {
    public static final String ID = "Centurion";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Centurion");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    private static final float IDLE_TIMESCALE = 0.8F;
    private static final int HP_MIN = 76;
    private static final int HP_MAX = 80;
    private static final int A_2_HP_MIN = 78;
    private static final int A_2_HP_MAX = 83;
    private static final int SLASH_DMG = 12;
    private static final int FURY_DMG = 6;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final int FURY_HITS = 3;
    private static final int A_2_SLASH_DMG = 14;
    private static final int A_2_FURY_DMG = 7;
    private int slashDmg;
    private int furyDmg;
    private int furyHits;
    private int blockAmount;
    private int BLOCK_AMOUNT = 15;
    private int A_17_BLOCK_AMOUNT = 20;
    private static final byte SLASH = 1;
    private static final byte PROTECT = 2;
    private static final byte FURY = 3;

    public Centurion(float x, float y) {
        super(NAME, "Centurion", 80, -14.0F, -20.0F, 250.0F, 330.0F, null, x, y);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(78, 83);
        } else {
            setHp(76, 80);
        }

        if (AbstractDungeon.ascensionLevel >= 17) {
            this.blockAmount = this.A_17_BLOCK_AMOUNT;
        } else {
            this.blockAmount = this.BLOCK_AMOUNT;
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.slashDmg = 14;
            this.furyDmg = 7;
            this.furyHits = 3;
        } else {
            this.slashDmg = 12;
            this.furyDmg = 6;
            this.furyHits = 3;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.slashDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.furyDmg));

        loadAnimation("images/monsters/theCity/tank/skeleton.atlas", "images/monsters/theCity/tank/skeleton.json",
                1.0F);
        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.2F);
        this.state.setTimeScale(0.8F);
    }

    public void takeTurn() {
        int i;
        switch (this.nextMove) {
            case 1:
                playSfx();
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "MACE_HIT"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.3F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_LIGHT));
                break;
            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.25F));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new GainBlockRandomMonsterAction((AbstractCreature) this,
                                this.blockAmount));
                break;
            case 3:
                for (i = 0; i < this.furyHits; i++) {
                    playSfx();
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new ChangeStateAction(this, "MACE_HIT"));
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.3F));
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(1), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                }
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    private void playSfx() {
        int roll = MathUtils.random(1);
        if (roll == 0) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_TANK_1A"));
        } else if (roll == 1) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_TANK_1B"));
        } else {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_TANK_1C"));
        }
    }

    public void changeState(String key) {
        switch (key) {

            case "MACE_HIT":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
        }
    }

    protected void getMove(int num) {
        if (num >= 65 && !lastTwoMoves((byte) 2) && !lastTwoMoves((byte) 3)) {
            int i = 0;

            for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                if (!m.isDying && !m.isEscaping) {
                    i++;
                }
            }

            if (i > 1) {
                setMove((byte) 2, Intent.DEFEND);
                return;
            }
            setMove((byte) 3, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base, this.furyHits, true);

            return;
        }

        if (!lastTwoMoves((byte) 1)) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
            return;
        }
        int aliveCount = 0;

        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            if (!m.isDying && !m.isEscaping) {
                aliveCount++;
            }
        }

        if (aliveCount > 1) {
            setMove((byte) 2, Intent.DEFEND);
            return;
        }
        setMove((byte) 3, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base, this.furyHits, true);
    }

    public void damage(DamageInfo info) {
        super.damage(info);
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hit", false);
            this.state.setTimeScale(0.8F);
            this.state.addAnimation(0, "Idle", true, 0.0F);
        }
    }

    public void die() {
        this.state.setTimeScale(0.1F);
        useShakeAnimation(5.0F);
        super.die();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * Centurion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

