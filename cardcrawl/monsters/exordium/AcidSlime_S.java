package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.SlimeAnimListener;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.PoisonPower;
import com.megacrit.cardcrawl.powers.WeakPower;

public class AcidSlime_S extends AbstractMonster {
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("AcidSlime_S");
    public static final String ID = "AcidSlime_S";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    public static final int HP_MIN = 8;
    public static final int HP_MAX = 12;
    public static final int A_2_HP_MIN = 9;
    public static final int A_2_HP_MAX = 13;
    public static final int TACKLE_DAMAGE = 3;
    public static final int WEAK_TURNS = 1;
    public static final int A_2_TACKLE_DAMAGE = 4;
    private static final byte TACKLE = 1;
    private static final byte DEBUFF = 2;

    public AcidSlime_S(float x, float y, int poisonAmount) {
        super(NAME, "AcidSlime_S", 12, 0.0F, -4.0F, 130.0F, 100.0F, null, x, y);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(9, 13);
        } else {
            setHp(8, 12);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.damage.add(new DamageInfo((AbstractCreature) this, 4));
        } else {
            this.damage.add(new DamageInfo((AbstractCreature) this, 3));
        }

        if (poisonAmount >= 1) {
            this.powers.add(new PoisonPower((AbstractCreature) this, (AbstractCreature) this, poisonAmount));
        }

        loadAnimation("images/monsters/theBottom/slimeS/skeleton.atlas",
                "images/monsters/theBottom/slimeS/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.state.addListener((AnimationState.AnimationStateListener) new SlimeAnimListener());
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                setMove((byte) 2, Intent.DEBUFF);
                break;
            case 2:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, 1, true), 1));

                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
                break;
        }
    }

    protected void getMove(int num) {
        if (AbstractDungeon.ascensionLevel >= 17) {
            if (lastTwoMoves((byte) 1)) {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
            } else {
                setMove((byte) 2, Intent.DEBUFF);
            }

        } else if (AbstractDungeon.aiRng.randomBoolean()) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
        } else {
            setMove((byte) 2, Intent.DEBUFF);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\exordium\
 * AcidSlime_S.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

