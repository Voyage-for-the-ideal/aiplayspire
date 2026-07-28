package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.ExplosivePower;

public class Exploder extends AbstractMonster {
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Exploder");
    public static final String ID = "Exploder";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    public static final String ENCOUNTER_NAME = "Ancient Shapes";
    private static final int HP_MIN = 30;
    private static final int HP_MAX = 30;
    private static final int A_2_HP_MIN = 30;
    private static final int A_2_HP_MAX = 35;
    private int turnCount = 0;

    private static final float HB_X = -8.0F;

    private static final float HB_Y = -10.0F;
    private static final float HB_W = 150.0F;
    private static final float HB_H = 150.0F;
    private static final byte ATTACK = 1;
    private static final int ATTACK_DMG = 9;
    private static final int A_2_ATTACK_DMG = 11;
    private int attackDmg;
    private static final byte BLOCK = 2;
    private static final int EXPLODE_BASE = 3;

    public Exploder(float x, float y) {
        super(NAME, "Exploder", 30, -8.0F, -10.0F, 150.0F, 150.0F, null, x, y + 10.0F);

        loadAnimation("images/monsters/theForest/exploder/skeleton.atlas",
                "images/monsters/theForest/exploder/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(30, 35);
        } else {
            setHp(30, 30);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.attackDmg = 11;
        } else {
            this.attackDmg = 9;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.attackDmg));
    }

    public void usePreBattleAction() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new ExplosivePower((AbstractCreature) this, 3)));
    }

    public void takeTurn() {
        this.turnCount++;
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.FIRE));
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        if (this.turnCount < 2) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
        } else {
            setMove((byte) 2, Intent.UNKNOWN);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\beyond\
 * Exploder.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

