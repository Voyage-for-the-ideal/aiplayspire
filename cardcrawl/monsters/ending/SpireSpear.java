package com.megacrit.cardcrawl.monsters.ending;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Burn;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.ArtifactPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class SpireSpear extends AbstractMonster {
    public static final String ID = "SpireSpear";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("SpireSpear");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private int moveCount = 0;

    private static final byte BURN_STRIKE = 1;
    private static final byte PIERCER = 2;

    public SpireSpear() {
        super(NAME, "SpireSpear", 160, 0.0F, -15.0F, 380.0F, 290.0F, null, 70.0F, 10.0F);
        this.type = EnemyType.ELITE;

        loadAnimation("images/monsters/theEnding/spear/skeleton.atlas", "images/monsters/theEnding/spear/skeleton.json",
                1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.1F);
        e.setTimeScale(0.7F);

        if (AbstractDungeon.ascensionLevel >= 8) {
            setHp(180);
        } else {
            setHp(160);
        }

        if (AbstractDungeon.ascensionLevel >= 3) {
            this.skewerCount = 4;
            this.damage.add(new DamageInfo((AbstractCreature) this, 6));
            this.damage.add(new DamageInfo((AbstractCreature) this, 10));
        } else {
            this.skewerCount = 3;
            this.damage.add(new DamageInfo((AbstractCreature) this, 5));
            this.damage.add(new DamageInfo((AbstractCreature) this, 10));
        }
    }
    private static final byte SKEWER = 3;
    private static final int BURN_STRIKE_COUNT = 2;
    private int skewerCount;

    public void usePreBattleAction() {
        if (AbstractDungeon.ascensionLevel >= 18) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                    (AbstractCreature) this, (AbstractPower) new ArtifactPower((AbstractCreature) this, 2)));
        } else {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                    (AbstractCreature) this, (AbstractPower) new ArtifactPower((AbstractCreature) this, 1)));
        }
    }

    public void takeTurn() {
        int i;
        switch (this.nextMove) {
            case 1:
                for (i = 0; i < 2; i++) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.15F));
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(0), AbstractGameAction.AttackEffect.FIRE));
                }

                if (AbstractDungeon.ascensionLevel >= 18) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new MakeTempCardInDrawPileAction(
                            (AbstractCard) new Burn(), 2, false, true));
                    break;
                }
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new MakeTempCardInDiscardAction((AbstractCard) new Burn(), 2));
                break;

            case 2:
                for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) this,
                                    (AbstractPower) new StrengthPower((AbstractCreature) m, 2), 2));
                }
                break;

            case 3:
                for (i = 0; i < this.skewerCount; i++) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.05F));
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(1), AbstractGameAction.AttackEffect.SLASH_DIAGONAL, true));
                }
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        switch (this.moveCount % 3) {
            case 0:
                if (!lastMove((byte) 1)) {
                    setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base, 2, true);
                    break;
                }
                setMove((byte) 2, Intent.BUFF);
                break;

            case 1:
                setMove((byte) 3, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base, this.skewerCount, true);
                break;
            default:
                if (AbstractDungeon.aiRng.randomBoolean()) {
                    setMove((byte) 2, Intent.BUFF);
                    break;
                }
                setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base, 2, true);
                break;
        }

        this.moveCount++;
    }

    public void changeState(String key) {
        AnimationState.TrackEntry e = null;
        switch (key) {
            case "SLOW_ATTACK":
                this.state.setAnimation(0, "Attack_1", false);
                e = this.state.addAnimation(0, "Idle", true, 0.0F);
                e.setTimeScale(0.5F);
                break;
            case "ATTACK":
                this.state.setAnimation(0, "Attack_2", false);
                e = this.state.addAnimation(0, "Idle", true, 0.0F);
                e.setTimeScale(0.7F);
                break;
        }
    }

    public void damage(DamageInfo info) {
        super.damage(info);
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hit", false);
            AnimationState.TrackEntry e = this.state.addAnimation(0, "Idle", true, 0.0F);
            e.setTimeScale(0.7F);
        }
    }

    public void die() {
        super.die();
        for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
            if (!m.isDead && !m.isDying) {
                if (AbstractDungeon.player.hasPower("Surrounded")) {
                    AbstractDungeon.player.flipHorizontal = (m.drawX < AbstractDungeon.player.drawX);
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RemoveSpecificPowerAction(
                            (AbstractCreature) AbstractDungeon.player, (AbstractCreature) AbstractDungeon.player,
                            "Surrounded"));
                }

                if (m.hasPower("BackAttack"))
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new RemoveSpecificPowerAction((AbstractCreature) m,
                                    (AbstractCreature) m, "BackAttack"));
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\ending\
 * SpireSpear.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

