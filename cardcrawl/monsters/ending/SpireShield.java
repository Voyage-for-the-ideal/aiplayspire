package com.megacrit.cardcrawl.monsters.ending;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.*;

public class SpireShield
        extends AbstractMonster {
    public static final String ID = "SpireShield";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("SpireShield");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private int moveCount = 0;

    private static final byte BASH = 1;
    private static final byte FORTIFY = 2;

    public SpireShield() {
        super(NAME, "SpireShield", 110, 0.0F, -20.0F, 380.0F, 290.0F, null, -1000.0F, 15.0F);
        this.type = EnemyType.ELITE;

        loadAnimation("images/monsters/theEnding/shield/skeleton.atlas",
                "images/monsters/theEnding/shield/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.1F);

        if (AbstractDungeon.ascensionLevel >= 8) {
            setHp(125);
        } else {
            setHp(110);
        }

        if (AbstractDungeon.ascensionLevel >= 3) {
            this.damage.add(new DamageInfo((AbstractCreature) this, 14));
            this.damage.add(new DamageInfo((AbstractCreature) this, 38));
        } else {
            this.damage.add(new DamageInfo((AbstractCreature) this, 12));
            this.damage.add(new DamageInfo((AbstractCreature) this, 34));
        }
    }
    private static final byte SMASH = 3;
    private static final int BASH_DEBUFF = -1;
    private static final int FORTIFY_BLOCK = 30;

    public void usePreBattleAction() {
        AbstractDungeon.actionManager
                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                        (AbstractCreature) this,
                        (AbstractPower) new SurroundedPower((AbstractCreature) AbstractDungeon.player)));

        if (AbstractDungeon.ascensionLevel >= 18) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                    (AbstractCreature) this, (AbstractPower) new ArtifactPower((AbstractCreature) this, 2)));
        } else {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                    (AbstractCreature) this, (AbstractPower) new ArtifactPower((AbstractCreature) this, 1)));
        }
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.35F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));

                if (!AbstractDungeon.player.orbs.isEmpty() && AbstractDungeon.aiRng.randomBoolean()) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                            (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                            (AbstractPower) new FocusPower((AbstractCreature) AbstractDungeon.player, -1), -1));

                    break;
                }

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new StrengthPower((AbstractCreature) AbstractDungeon.player, -1), -1));
                break;

            case 2:
                for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) m,
                                    (AbstractCreature) this, 30));
                }
                break;
            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ChangeStateAction(this, "OLD_ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.BLUNT_HEAVY));

                if (AbstractDungeon.ascensionLevel >= 18) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                    (AbstractCreature) this, 99));
                    break;
                }
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new GainBlockAction((AbstractCreature) this,
                                (AbstractCreature) this, ((DamageInfo) this.damage.get(1)).output));
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        switch (this.moveCount % 3) {
            case 0:
                if (AbstractDungeon.aiRng.randomBoolean()) {
                    setMove((byte) 2, Intent.DEFEND);
                    break;
                }
                setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
                break;

            case 1:
                if (!lastMove((byte) 1)) {
                    setMove((byte) 1, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(0)).base);
                    break;
                }
                setMove((byte) 2, Intent.DEFEND);
                break;

            default:
                setMove((byte) 3, Intent.ATTACK_DEFEND, ((DamageInfo) this.damage.get(1)).base);
                break;
        }
        this.moveCount++;
    }

    public void changeState(String key) {
        switch (key) {
            case "OLD_ATTACK":
                this.state.setAnimation(0, "old_attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
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
 * SpireShield.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

