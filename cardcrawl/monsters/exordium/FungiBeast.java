package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.SporeCloudPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class FungiBeast extends AbstractMonster {
    public static final String ID = "FungiBeast";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("FungiBeast");
    public static final String DOUBLE_ENCOUNTER = "TwoFungiBeasts";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    private static final int HP_MIN = 22;
    private static final int HP_MAX = 28;
    private static final int A_2_HP_MIN = 24;
    private static final int A_2_HP_MAX = 28;
    private static final float HB_X = 0.0F;
    private static final float HB_Y = -16.0F;
    private static final float HB_W = 260.0F;
    private static final float HB_H = 170.0F;
    private int biteDamage;
    private int strAmt;
    private static final int BITE_DMG = 6;
    private static final int GROW_STR = 3;
    private static final int A_2_GROW_STR = 4;
    private static final byte BITE = 1;
    private static final byte GROW = 2;
    private static final int VULN_AMT = 2;

    public FungiBeast(float x, float y) {
        super(NAME, "FungiBeast", 28, 0.0F, -16.0F, 260.0F, 170.0F, null, x, y);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(24, 28);
        } else {
            setHp(22, 28);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.strAmt = 4;
            this.biteDamage = 6;
        } else {
            this.strAmt = 3;
            this.biteDamage = 6;
        }

        loadAnimation("images/monsters/theBottom/fungi/skeleton.atlas", "images/monsters/theBottom/fungi/skeleton.json",
                1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        e.setTimeScale(MathUtils.random(0.7F, 1.0F));

        this.damage.add(new DamageInfo((AbstractCreature) this, this.biteDamage));
    }

    public void usePreBattleAction() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new SporeCloudPower((AbstractCreature) this, 2)));
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_LIGHT));
                break;
            case 2:
                if (AbstractDungeon.ascensionLevel >= 17) {
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                                    (AbstractPower) new StrengthPower((AbstractCreature) this, this.strAmt + 1),
                                    this.strAmt + 1));
                    break;
                }
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) this, this.strAmt), this.strAmt));
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        if (num < 60) {
            if (lastTwoMoves((byte) 1)) {
                setMove(MOVES[0], (byte) 2, Intent.BUFF);
            } else {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);

            }

        } else if (lastMove((byte) 2)) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
        } else {
            setMove(MOVES[0], (byte) 2, Intent.BUFF);
        }
    }

    public void changeState(String key) {
        switch (key) {
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
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\exordium\
 * FungiBeast.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

