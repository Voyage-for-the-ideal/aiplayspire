package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.SetMoveAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.WeakPower;

public class BanditLeader extends AbstractMonster {
    public static final String ID = "BanditLeader";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("BanditLeader");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    public static final int HP_MIN = 35;

    public static final int HP_MAX = 39;

    public static final int A_2_HP_MIN = 37;
    public static final int A_2_HP_MAX = 41;
    private static final int SLASH_DAMAGE = 15;
    private static final int AGONIZE_DAMAGE = 10;
    private static final int A_2_SLASH_DAMAGE = 17;
    private static final int A_2_AGONIZE_DAMAGE = 12;

    public BanditLeader(float x, float y) {
        super(NAME, "BanditLeader", 39, -10.0F, -7.0F, 180.0F, 285.0F, null, x, y);
        this.dialogX = 0.0F * Settings.scale;
        this.dialogY = 50.0F * Settings.scale;

        if (AbstractDungeon.ascensionLevel >= 17) {
            this.weakAmount = 3;
        } else {
            this.weakAmount = 2;
        }

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(37, 41);
        } else {
            setHp(35, 39);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.slashDmg = 17;
            this.agonizeDmg = 12;
        } else {
            this.slashDmg = 15;
            this.agonizeDmg = 10;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.slashDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.agonizeDmg));

        loadAnimation("images/monsters/theCity/romeo/skeleton.atlas", "images/monsters/theCity/romeo/skeleton.json",
                1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.2F);
        this.state.setTimeScale(0.8F);
    }
    private static final int WEAK_AMT = 2;
    private static final int A_17_WEAK = 3;
    private int slashDmg;
    private int agonizeDmg;
    private int weakAmount;
    private static final byte CROSS_SLASH = 1;
    private static final byte MOCK = 2;
    private static final byte AGONIZING_SLASH = 3;

    public void deathReact() {
        if (!isDeadOrEscaped()) {
            AbstractDungeon.actionManager
                    .addToBottom((AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[2]));
        }
    }

    public void takeTurn() {
        boolean bearLives;
        switch (this.nextMove) {
            case 2:
                bearLives = true;
                for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                    if (m instanceof BanditBear && m.isDying) {
                        bearLives = false;
                        break;
                    }
                }
                if (bearLives) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[0]));
                } else {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[1]));
                }
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SetMoveAction(this, (byte) 3,
                        Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage
                                .get(1)).base));
                break;
            case 3:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "STAB"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, this.weakAmount, true),
                        this.weakAmount));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new SetMoveAction(this, (byte) 1, Intent.ATTACK, ((DamageInfo) this.damage
                                .get(0)).base));
                break;
            case 1:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "STAB"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));

                if (AbstractDungeon.ascensionLevel >= 17 && !lastTwoMoves((byte) 1)) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SetMoveAction(this, (byte) 1,
                            Intent.ATTACK, ((DamageInfo) this.damage
                                    .get(0)).base));
                    break;
                }
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SetMoveAction(this, (byte) 3,
                        Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage
                                .get(1)).base));
                break;
        }
    }

    public void changeState(String key) {
        switch (key) {
            case "STAB":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
        }
    }

    public void damage(DamageInfo info) {
        super.damage(info);
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hit", false);
            this.state.setTimeScale(0.8F);
            this.state.addAnimation(0, "Idle", true, 0.0F);
        }
    }

    protected void getMove(int num) {
        setMove((byte) 2, Intent.UNKNOWN);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * BanditLeader.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

