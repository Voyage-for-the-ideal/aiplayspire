package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateFastAttackAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.FastShakeAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.unique.AddCardToDeckAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.*;

public class WrithingMass extends AbstractMonster {
    public static final String ID = "WrithingMass";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("WrithingMass");
    public static final String NAME = monsterStrings.NAME;

    private static final int HP = 160;

    private static final int A_2_HP = 175;
    private boolean firstMove = true;
    private boolean usedMegaDebuff = false;
    private static final int HIT_COUNT = 3;

    public WrithingMass() {
        super(NAME, "WrithingMass", 160, 5.0F, -26.0F, 450.0F, 310.0F, null, 0.0F, 15.0F);

        loadAnimation("images/monsters/theForest/spaghetti/skeleton.atlas",
                "images/monsters/theForest/spaghetti/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.1F);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(175);
        } else {
            setHp(160);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.damage.add(new DamageInfo((AbstractCreature) this, 38));
            this.damage.add(new DamageInfo((AbstractCreature) this, 9));
            this.damage.add(new DamageInfo((AbstractCreature) this, 16));
            this.damage.add(new DamageInfo((AbstractCreature) this, 12));
            this.normalDebuffAmt = 2;
        } else {
            this.damage.add(new DamageInfo((AbstractCreature) this, 32));
            this.damage.add(new DamageInfo((AbstractCreature) this, 7));
            this.damage.add(new DamageInfo((AbstractCreature) this, 15));
            this.damage.add(new DamageInfo((AbstractCreature) this, 10));
            this.normalDebuffAmt = 2;
        }
    }
    private int normalDebuffAmt;
    private static final byte BIG_HIT = 0;
    private static final byte MULTI_HIT = 1;
    private static final byte ATTACK_BLOCK = 2;
    private static final byte ATTACK_DEBUFF = 3;
    private static final byte MEGA_DEBUFF = 4;

    public void usePreBattleAction() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new ReactivePower((AbstractCreature) this)));
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new MalleablePower((AbstractCreature) this)));
    }

    public void takeTurn() {
        int i;
        switch (this.nextMove) {
            case 0:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.4F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_HEAVY));
                break;
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                for (i = 0; i < 3; i++) {
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(1), AbstractGameAction.AttackEffect.BLUNT_LIGHT));
                }
                break;
            case 2:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateFastAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(2), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                (AbstractCreature) this, ((DamageInfo) this.damage.get(2)).base));
                break;
            case 3:
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(3), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, this.normalDebuffAmt,
                                true),
                        this.normalDebuffAmt));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new VulnerablePower((AbstractCreature) AbstractDungeon.player,
                                this.normalDebuffAmt, true),
                        this.normalDebuffAmt));

                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateFastAttackAction((AbstractCreature) this));
                break;
            case 4:
                this.usedMegaDebuff = true;
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new FastShakeAction((AbstractCreature) this, 0.5F, 0.2F));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new AddCardToDeckAction(
                        CardLibrary.getCard("Parasite").makeCopy()));
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    public void damage(DamageInfo info) {
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hit", false);
            this.state.addAnimation(0, "Idle", true, 0.0F);
        }

        super.damage(info);
    }

    public void changeState(String key) {
        switch (key) {
            case "ATTACK":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
        }
    }

    protected void getMove(int num) {
        if (this.firstMove) {
            this.firstMove = false;
            if (num < 33) {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base, 3, true);
            } else if (num < 66) {
                setMove((byte) 2, Intent.ATTACK_DEFEND, ((DamageInfo) this.damage.get(2)).base);
            } else {
                setMove((byte) 3, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(3)).base);
            }

            return;
        }
        if (num < 10) {
            if (!lastMove((byte) 0)) {
                setMove((byte) 0, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
            } else {
                getMove(AbstractDungeon.aiRng.random(10, 99));
            }
        } else if (num < 20) {
            if (!this.usedMegaDebuff && !lastMove((byte) 4)) {
                setMove((byte) 4, Intent.STRONG_DEBUFF);
            } else if (AbstractDungeon.aiRng.randomBoolean(0.1F)) {
                setMove((byte) 0, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
            } else {
                getMove(AbstractDungeon.aiRng.random(20, 99));
            }

        } else if (num < 40) {
            if (!lastMove((byte) 3)) {
                setMove((byte) 3, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(3)).base);
            } else if (AbstractDungeon.aiRng.randomBoolean(0.4F)) {
                getMove(AbstractDungeon.aiRng.random(19));
            } else {
                getMove(AbstractDungeon.aiRng.random(40, 99));
            }

        } else if (num < 70) {
            if (!lastMove((byte) 1)) {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base, 3, true);
            } else if (AbstractDungeon.aiRng.randomBoolean(0.3F)) {
                setMove((byte) 2, Intent.ATTACK_DEFEND, ((DamageInfo) this.damage.get(2)).base);
            } else {
                getMove(AbstractDungeon.aiRng.random(39));
            }

        } else if (!lastMove((byte) 2)) {
            setMove((byte) 2, Intent.ATTACK_DEFEND, ((DamageInfo) this.damage.get(2)).base);
        } else {
            getMove(AbstractDungeon.aiRng.random(69));
        }

        createIntent();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\beyond\
 * WrithingMass.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

