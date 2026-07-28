package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateFastAttackAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
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
import com.megacrit.cardcrawl.powers.ConstrictedPower;

public class SpireGrowth extends AbstractMonster {
    public static final String ID = "Serpent";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Serpent");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final int START_HP = 170;
    private static final int A_2_START_HP = 190;
    private int tackleDmg = 16, smashDmg = 22, constrictDmg = 10;
    private int A_2_tackleDmg = 18;
    private int A_2_smashDmg = 25;
    private int tackleDmgActual;
    private int smashDmgActual;

    public SpireGrowth() {
        super(NAME, "Serpent", 170, -10.0F, -35.0F, 480.0F, 430.0F, null, 0.0F, 10.0F);
        loadAnimation("images/monsters/theForest/spireGrowth/skeleton.atlas",
                "images/monsters/theForest/spireGrowth/skeleton.json", 1.0F);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(190);
        } else {
            setHp(170);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.tackleDmgActual = this.A_2_tackleDmg;
            this.smashDmgActual = this.A_2_smashDmg;
        } else {
            this.tackleDmgActual = this.tackleDmg;
            this.smashDmgActual = this.smashDmg;
        }

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        e.setTimeScale(1.3F);
        this.stateData.setMix("Hurt", "Idle", 0.2F);
        this.stateData.setMix("Idle", "Hurt", 0.2F);

        this.damage.add(new DamageInfo((AbstractCreature) this, this.tackleDmgActual));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.smashDmgActual));
    }
    private static final byte QUICK_TACKLE = 1;
    private static final byte CONSTRICT = 2;
    private static final byte SMASH = 3;

    public void takeTurn() {
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateFastAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                break;
            case 2:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                if (AbstractDungeon.ascensionLevel >= 17) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                            (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                            (AbstractPower) new ConstrictedPower((AbstractCreature) AbstractDungeon.player,
                                    (AbstractCreature) this, this.constrictDmg + 2)));

                    break;
                }

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new ConstrictedPower((AbstractCreature) AbstractDungeon.player,
                                (AbstractCreature) this, this.constrictDmg)));
                break;

            case 3:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.4F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                break;
        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        if (AbstractDungeon.ascensionLevel >= 17 &&
                !AbstractDungeon.player.hasPower("Constricted") && !lastMove((byte) 2)) {
            setMove((byte) 2, Intent.STRONG_DEBUFF);

            return;
        }

        if (num < 50 && !lastTwoMoves((byte) 1)) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);

            return;
        }
        if (!AbstractDungeon.player.hasPower("Constricted") && !lastMove((byte) 2)) {
            setMove((byte) 2, Intent.STRONG_DEBUFF);

            return;
        }
        if (!lastTwoMoves((byte) 3)) {
            setMove((byte) 3, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
            return;
        }
        setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
    }

    public void damage(DamageInfo info) {
        super.damage(info);
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hurt", false);
            this.state.setTimeScale(1.3F);
            this.state.addAnimation(0, "Idle", true, 0.0F);
        }
    }

    public void changeState(String key) {
        switch (key) {
            case "ATTACK":
                this.state.setAnimation(0, "Attack", false);
                this.state.setTimeScale(1.3F);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\beyond\
 * SpireGrowth.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

