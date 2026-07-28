package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.ShoutAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.FrailPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.powers.WeakPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.BiteEffect;

public class Maw extends AbstractMonster {
    public static final String ID = "Maw";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Maw");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    private static final int HP = 300;
    private static final float HB_X = 0.0F;
    private static final float HB_Y = -40.0F;
    private static final float HB_W = 430.0F;
    private static final float HB_H = 360.0F;
    private static final int SLAM_DMG = 25;
    private static final int NOM_DMG = 5;
    private static final int A_2_SLAM_DMG = 30;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private int slamDmg;

    private int nomDmg;
    private static final byte ROAR = 2;
    private static final byte SLAM = 3;
    private static final byte DROOL = 4;
    private static final byte NOMNOMNOM = 5;
    private boolean roared = false;
    private int turnCount = 1;
    private int strUp;
    private int terrifyDur;

    public Maw(float x, float y) {
        super(NAME, "Maw", 300, 0.0F, -40.0F, 430.0F, 360.0F, null, x, y);

        loadAnimation("images/monsters/theForest/maw/skeleton.atlas", "images/monsters/theForest/maw/skeleton.json",
                1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());

        this.dialogX = -160.0F * Settings.scale;
        this.dialogY = 40.0F * Settings.scale;

        this.strUp = 3;
        this.terrifyDur = 3;

        if (AbstractDungeon.ascensionLevel >= 17) {
            this.strUp += 2;
            this.terrifyDur += 2;
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.slamDmg = 30;
            this.nomDmg = 5;
        } else {
            this.slamDmg = 25;
            this.nomDmg = 5;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.slamDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.nomDmg));
    }

    public void takeTurn() {
        int i;
        switch (this.nextMove) {
            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("MAW_DEATH", 0.1F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ShoutAction((AbstractCreature) this, DIALOG[0], 1.0F, 2.0F));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, this.terrifyDur, true),
                        this.terrifyDur));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new FrailPower((AbstractCreature) AbstractDungeon.player, this.terrifyDur,
                                true),
                        this.terrifyDur));

                this.roared = true;
                break;
            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                break;
            case 4:
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) this, this.strUp), this.strUp));
                break;

            case 5:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));

                for (i = 0; i < this.turnCount / 2; i++) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new VFXAction((AbstractGameEffect) new BiteEffect(
                                    AbstractDungeon.player.hb.cX +

                                            MathUtils.random(-50.0F, 50.0F) * Settings.scale,
                                    AbstractDungeon.player.hb.cY +
                                            MathUtils.random(-50.0F, 50.0F) * Settings.scale,
                                    Color.SKY
                                            .cpy())));

                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(1), AbstractGameAction.AttackEffect.NONE));
                }
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        this.turnCount++;
        if (!this.roared) {
            setMove((byte) 2, Intent.STRONG_DEBUFF);

            return;
        }
        if (num < 50 && !lastMove((byte) 5)) {
            if (this.turnCount / 2 <= 1) {
                setMove((byte) 5, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
            } else {
                setMove((byte) 5, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base, this.turnCount / 2, true);
            }

            return;
        }
        if (lastMove((byte) 3) || lastMove((byte) 5)) {
            setMove((byte) 4, Intent.BUFF);
            return;
        }
        setMove((byte) 3, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
    }

    public void die() {
        super.die();
        CardCrawlGame.sound.play("MAW_DEATH");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\beyond\
 * Maw.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

