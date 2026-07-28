package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateFastAttackAction;
import com.megacrit.cardcrawl.actions.animations.FastShakeAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.ConfusionPower;
import com.megacrit.cardcrawl.powers.VulnerablePower;
import com.megacrit.cardcrawl.powers.WeakPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.BiteEffect;
import com.megacrit.cardcrawl.vfx.combat.IntimidateEffect;

public class Snecko extends AbstractMonster {
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Snecko");
    public static final String ID = "Snecko";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    private static final byte GLARE = 1;
    private static final byte BITE = 2;
    private static final byte TAIL = 3;
    private static final int BITE_DAMAGE = 15;
    private static final int TAIL_DAMAGE = 8;
    private static final int A_2_BITE_DAMAGE = 18;
    private static final int A_2_TAIL_DAMAGE = 10;
    private int biteDmg;
    private int tailDmg;
    private static final int VULNERABLE_AMT = 2;
    private static final int HP_MIN = 114;
    private static final int HP_MAX = 120;
    private static final int A_2_HP_MIN = 120;
    private static final int A_2_HP_MAX = 125;
    private boolean firstTurn = true;

    public Snecko() {
        this(0.0F, 0.0F);
    }

    public Snecko(float x, float y) {
        super(NAME, "Snecko", 120, -30.0F, -20.0F, 310.0F, 305.0F, null, x, y);
        loadAnimation("images/monsters/theCity/reptile/skeleton.atlas", "images/monsters/theCity/reptile/skeleton.json",
                1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.1F);
        e.setTimeScale(0.8F);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(120, 125);
        } else {
            setHp(114, 120);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.biteDmg = 18;
            this.tailDmg = 10;
        } else {
            this.biteDmg = 15;
            this.tailDmg = 8;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.biteDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.tailDmg));
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("MONSTER_SNECKO_GLARE"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction((AbstractCreature) this,
                        (AbstractGameEffect) new IntimidateEffect(this.hb.cX, this.hb.cY), 0.5F));

                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new FastShakeAction((AbstractCreature) AbstractDungeon.player,
                                1.0F, 1.0F));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new ConfusionPower((AbstractCreature) AbstractDungeon.player)));
                break;

            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK_2"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.3F));
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new VFXAction((AbstractGameEffect) new BiteEffect(
                                AbstractDungeon.player.hb.cX +

                                        MathUtils.random(-50.0F, 50.0F) * Settings.scale,
                                AbstractDungeon.player.hb.cY +
                                        MathUtils.random(-50.0F, 50.0F) * Settings.scale,
                                Color.CHARTREUSE
                                        .cpy()),
                                0.3F));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.NONE));
                break;
            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateFastAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
                if (AbstractDungeon.ascensionLevel >= 17) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                            (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                            (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, 2, true), 2));
                }

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new VulnerablePower((AbstractCreature) AbstractDungeon.player, 2, true), 2));
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    public void changeState(String stateName) {
        switch (stateName) {
            case "ATTACK":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
            case "ATTACK_2":
                this.state.setAnimation(0, "Attack_2", false);
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

    protected void getMove(int num) {
        if (this.firstTurn) {
            this.firstTurn = false;
            setMove(MOVES[0], (byte) 1, Intent.STRONG_DEBUFF);

            return;
        }

        if (num < 40) {
            setMove(MOVES[1], (byte) 3, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);

            return;
        }

        if (lastTwoMoves((byte) 2)) {
            setMove(MOVES[1], (byte) 3, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
        } else {
            setMove(MOVES[2], (byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
        }
    }

    public void die() {
        super.die();
        CardCrawlGame.sound.play("SNECKO_DEATH");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * Snecko.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

