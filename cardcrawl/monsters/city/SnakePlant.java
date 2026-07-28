package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.FrailPower;
import com.megacrit.cardcrawl.powers.MalleablePower;
import com.megacrit.cardcrawl.powers.WeakPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.BiteEffect;

public class SnakePlant extends AbstractMonster {
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("SnakePlant");
    public static final String ID = "SnakePlant";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    private static final int HP_MIN = 75;
    private static final int HP_MAX = 79;
    private static final int A_2_HP_MIN = 78;
    private static final int A_2_HP_MAX = 82;
    private static final byte CHOMPY_CHOMPS = 1;
    private static final byte SPORES = 2;
    private static final int CHOMPY_AMT = 3;
    private static final int CHOMPY_DMG = 7;
    private static final int A_2_CHOMPY_DMG = 8;
    private int rainBlowsDmg;

    public SnakePlant(float x, float y) {
        super(NAME, "SnakePlant", 79, 0.0F, -44.0F, 350.0F, 360.0F, null, x, y + 50.0F);
        loadAnimation("images/monsters/theCity/snakePlant/skeleton.atlas",
                "images/monsters/theCity/snakePlant/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.1F);
        e.setTimeScale(0.8F);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(78, 82);
        } else {
            setHp(75, 79);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.rainBlowsDmg = 8;
        } else {
            this.rainBlowsDmg = 7;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.rainBlowsDmg));
    }

    public void usePreBattleAction() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new MalleablePower((AbstractCreature) this)));
    }

    public void changeState(String stateName) {
        switch (stateName) {
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

    public void takeTurn() {
        int numBlows, i;
        AbstractPlayer abstractPlayer = AbstractDungeon.player;
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));
                numBlows = 3;

                for (i = 0; i < numBlows; i++) {
                    AbstractDungeon.actionManager
                            .addToBottom((AbstractGameAction) new VFXAction((AbstractGameEffect) new BiteEffect(
                                    AbstractDungeon.player.hb.cX +

                                            MathUtils.random(-50.0F, 50.0F) * Settings.scale,
                                    AbstractDungeon.player.hb.cY +
                                            MathUtils.random(-50.0F, 50.0F) * Settings.scale,
                                    Color.CHARTREUSE
                                            .cpy()),
                                    0.2F));

                    AbstractDungeon.actionManager.addToBottom(
                            (AbstractGameAction) new DamageAction((AbstractCreature) abstractPlayer, this.damage
                                    .get(0), AbstractGameAction.AttackEffect.NONE, true));
                }
                break;
            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new FrailPower((AbstractCreature) AbstractDungeon.player, 2, true), 2));

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new WeakPower((AbstractCreature) AbstractDungeon.player, 2, true), 2));
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    protected void getMove(int num) {
        if (AbstractDungeon.ascensionLevel >= 17) {
            if (num < 65) {
                if (lastTwoMoves((byte) 1)) {
                    setMove(MOVES[0], (byte) 2, Intent.STRONG_DEBUFF);
                } else {
                    setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 3, true);
                }

            } else if (lastMove((byte) 2) || lastMoveBefore((byte) 2)) {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 3, true);
            } else {
                setMove(MOVES[0], (byte) 2, Intent.STRONG_DEBUFF);
            }

        } else if (num < 65) {
            if (lastTwoMoves((byte) 1)) {
                setMove(MOVES[0], (byte) 2, Intent.STRONG_DEBUFF);
            } else {
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 3, true);
            }

        } else if (lastMove((byte) 2)) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 3, true);
        } else {
            setMove(MOVES[0], (byte) 2, Intent.STRONG_DEBUFF);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * SnakePlant.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

