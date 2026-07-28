package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.DexterityPower;

public class BanditBear extends AbstractMonster {
    public static final String ID = "BanditBear";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("BanditBear");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    public static final int HP_MIN = 38;

    public static final int HP_MAX = 42;

    public static final int A_2_HP_MIN = 40;

    public static final int A_2_HP_MAX = 44;
    private static final int MAUL_DMG = 18;
    private static final int A_2_MAUL_DMG = 20;
    private static final int LUNGE_DMG = 9;
    private static final int A_2_LUNGE_DMG = 10;

    public BanditBear(float x, float y) {
        super(NAME, "BanditBear", 42, -5.0F, -4.0F, 180.0F, 280.0F, null, x, y);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(40, 44);
        } else {
            setHp(38, 42);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.maulDmg = 20;
            this.lungeDmg = 10;
        } else {
            this.maulDmg = 18;
            this.lungeDmg = 9;
        }

        if (AbstractDungeon.ascensionLevel >= 17) {
            this.con_reduction = -4;
        } else {
            this.con_reduction = -2;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.maulDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.lungeDmg));

        loadAnimation("images/monsters/theCity/bear/skeleton.atlas", "images/monsters/theCity/bear/skeleton.json",
                1.0F);
        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.2F);
        this.state.setTimeScale(1.0F);
    }
    private static final int LUNGE_DEFENSE = 9;
    private static final int CON_AMT = -2;
    private static final int A_17_CON_AMT = -4;
    private int maulDmg;
    private int lungeDmg;
    private int con_reduction;
    private static final byte MAUL = 1;
    private static final byte BEAR_HUG = 2;
    private static final byte LUNGE = 3;

    public void takeTurn() {
        switch (this.nextMove) {
            case 2:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new DexterityPower((AbstractCreature) AbstractDungeon.player,
                                this.con_reduction),
                        this.con_reduction));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SetMoveAction(this, (byte) 3,
                        Intent.ATTACK_DEFEND, ((DamageInfo) this.damage
                                .get(1)).base));
                break;
            case 1:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "MAUL"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.3F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SetMoveAction(this, (byte) 3,
                        Intent.ATTACK_DEFEND, ((DamageInfo) this.damage
                                .get(1)).base));
                break;
            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new GainBlockAction((AbstractCreature) this, (AbstractCreature) this, 9));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new SetMoveAction(this, (byte) 1, Intent.ATTACK, ((DamageInfo) this.damage
                                .get(0)).base));
                break;
        }
    }

    public void changeState(String key) {
        switch (key) {
            case "MAUL":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
        }
    }

    public void damage(DamageInfo info) {
        super.damage(info);
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hit", false);
            this.state.setTimeScale(1.0F);
            this.state.addAnimation(0, "Idle", true, 0.0F);
        }
    }

    public void die() {
        super.die();
        for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
            if (!m.isDead && !m.isDying) {
                m.deathReact();
            }
        }
    }

    protected void getMove(int num) {
        setMove((byte) 2, Intent.STRONG_DEBUFF);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * BanditBear.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

