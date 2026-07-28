package com.megacrit.cardcrawl.monsters.ending;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.*;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.HeartAnimListener;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.*;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderFlashEffect;
import com.megacrit.cardcrawl.vfx.combat.BloodShotEffect;
import com.megacrit.cardcrawl.vfx.combat.HeartBuffEffect;
import com.megacrit.cardcrawl.vfx.combat.HeartMegaDebuffEffect;
import com.megacrit.cardcrawl.vfx.combat.ViceCrushEffect;

public class CorruptHeart extends AbstractMonster {
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("CorruptHeart");
    public static final String ID = "CorruptHeart";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    private HeartAnimListener animListener = new HeartAnimListener();
    private static final byte BLOOD_SHOTS = 1;
    private static final byte ECHO_ATTACK = 2;
    private static final byte DEBILITATE = 3;
    private static final byte GAIN_ONE_STRENGTH = 4;
    public static final int DEBUFF_AMT = -1;
    private int bloodHitCount;
    private boolean isFirstMove = true;
    private int moveCount = 0, buffCount = 0;

    public CorruptHeart() {
        super(NAME, "CorruptHeart", 750, 30.0F, -30.0F, 476.0F, 410.0F, null, -50.0F, 30.0F);

        loadAnimation("images/npcs/heart/skeleton.atlas", "images/npcs/heart/skeleton.json", 1.0F);
        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTimeScale(1.5F);
        this.state.addListener((AnimationState.AnimationStateListener) this.animListener);
        this.type = EnemyType.BOSS;

        if (AbstractDungeon.ascensionLevel >= 9) {
            setHp(800);
        } else {
            setHp(750);
        }

        if (AbstractDungeon.ascensionLevel >= 4) {
            this.damage.add(new DamageInfo((AbstractCreature) this, 45));
            this.damage.add(new DamageInfo((AbstractCreature) this, 2));
            this.bloodHitCount = 15;
        } else {
            this.damage.add(new DamageInfo((AbstractCreature) this, 40));
            this.damage.add(new DamageInfo((AbstractCreature) this, 2));
            this.bloodHitCount = 12;
        }
    }

    public void usePreBattleAction() {
        CardCrawlGame.music.unsilenceBGM();
        AbstractDungeon.scene.fadeOutAmbiance();
        AbstractDungeon.getCurrRoom().playBgmInstantly("BOSS_ENDING");
        int invincibleAmt = 300;
        if (AbstractDungeon.ascensionLevel >= 19) {
            invincibleAmt -= 100;
        }
        int beatAmount = 1;
        if (AbstractDungeon.ascensionLevel >= 19) {
            beatAmount++;
        }
        AbstractDungeon.actionManager
                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                        (AbstractPower) new InvinciblePower((AbstractCreature) this, invincibleAmt), invincibleAmt));

        AbstractDungeon.actionManager
                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                        (AbstractPower) new BeatOfDeathPower((AbstractCreature) this, beatAmount), beatAmount));
    }

    public void takeTurn() {
        int additionalAmount, i;
        switch (this.nextMove) {
            case 3:
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new VFXAction((AbstractGameEffect) new HeartMegaDebuffEffect()));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new VulnerablePower((AbstractCreature) AbstractDungeon.player, 2, true), 2));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, 2, true), 2));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new FrailPower((AbstractCreature) AbstractDungeon.player, 2, true), 2));

                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new MakeTempCardInDrawPileAction((AbstractCard) new Dazed(),
                                1, true, false, false, Settings.WIDTH * 0.2F, Settings.HEIGHT / 2.0F));

                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new MakeTempCardInDrawPileAction((AbstractCard) new Slimed(),
                                1, true, false, false, Settings.WIDTH * 0.35F, Settings.HEIGHT / 2.0F));

                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new MakeTempCardInDrawPileAction((AbstractCard) new Wound(),
                                1, true, false, false, Settings.WIDTH * 0.5F, Settings.HEIGHT / 2.0F));

                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new MakeTempCardInDrawPileAction((AbstractCard) new Burn(), 1,
                                true, false, false, Settings.WIDTH * 0.65F, Settings.HEIGHT / 2.0F));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new MakeTempCardInDrawPileAction((AbstractCard) new VoidCard(), 1, true,
                                false, false, Settings.WIDTH * 0.8F, Settings.HEIGHT / 2.0F));
                break;

            case 4:
                additionalAmount = 0;
                if (hasPower("Strength") && (getPower("Strength")).amount < 0) {
                    additionalAmount = -(getPower("Strength")).amount;
                }

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new BorderFlashEffect(new Color(0.8F, 0.5F, 1.0F, 1.0F))));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new HeartBuffEffect(this.hb.cX, this.hb.cY)));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new ApplyPowerAction((AbstractCreature) this, (AbstractCreature) this,
                                (AbstractPower) new StrengthPower((AbstractCreature) this, additionalAmount + 2),
                                additionalAmount + 2));

                switch (this.buffCount) {
                    case 0:
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                                        (AbstractCreature) this,
                                        (AbstractPower) new ArtifactPower((AbstractCreature) this, 2), 2));
                        break;

                    case 1:
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                                        (AbstractCreature) this,
                                        (AbstractPower) new BeatOfDeathPower((AbstractCreature) this, 1), 1));
                        break;

                    case 2:
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                                        (AbstractCreature) this,
                                        (AbstractPower) new PainfulStabsPower((AbstractCreature) this)));
                        break;

                    case 3:
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                                        (AbstractCreature) this,
                                        (AbstractPower) new StrengthPower((AbstractCreature) this, 10), 10));
                        break;

                    default:
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                                        (AbstractCreature) this,
                                        (AbstractPower) new StrengthPower((AbstractCreature) this, 50), 50));
                        break;
                }

                this.buffCount++;
                break;
            case 1:
                if (Settings.FAST_MODE) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                            (AbstractGameEffect) new BloodShotEffect(this.hb.cX, this.hb.cY,
                                    AbstractDungeon.player.hb.cX, AbstractDungeon.player.hb.cY, this.bloodHitCount),
                            0.25F));

                } else {

                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                            (AbstractGameEffect) new BloodShotEffect(this.hb.cX, this.hb.cY,
                                    AbstractDungeon.player.hb.cX, AbstractDungeon.player.hb.cY, this.bloodHitCount),
                            0.6F));
                }

                for (i = 0; i < this.bloodHitCount; i++) {
                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                    .get(1), AbstractGameAction.AttackEffect.BLUNT_HEAVY, true));
                }
                break;
            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new ViceCrushEffect(AbstractDungeon.player.hb.cX,
                                AbstractDungeon.player.hb.cY),
                        0.5F));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                break;
        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        if (this.isFirstMove) {
            setMove((byte) 3, Intent.STRONG_DEBUFF);
            this.isFirstMove = false;

            return;
        }
        switch (this.moveCount % 3) {
            case 0:
                if (AbstractDungeon.aiRng.randomBoolean()) {
                    setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base, this.bloodHitCount, true);
                    break;
                }
                setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
                break;

            case 1:
                if (!lastMove((byte) 2)) {
                    setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
                    break;
                }
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base, this.bloodHitCount, true);
                break;

            default:
                setMove((byte) 4, Intent.BUFF);
                break;
        }

        this.moveCount++;
    }

    public void die() {
        if (!(AbstractDungeon.getCurrRoom()).cannotLose) {
            super.die();
            this.state.removeListener((AnimationState.AnimationStateListener) this.animListener);
            onBossVictoryLogic();
            onFinalBossVictoryLogic();
            CardCrawlGame.stopClock = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\ending\
 * CorruptHeart.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

