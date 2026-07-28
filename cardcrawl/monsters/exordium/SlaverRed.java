package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
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
import com.megacrit.cardcrawl.powers.EntanglePower;
import com.megacrit.cardcrawl.powers.VulnerablePower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.EntangleEffect;

public class SlaverRed extends AbstractMonster {
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("SlaverRed");
    public static final String ID = "SlaverRed";
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    private static final int HP_MIN = 46;
    private static final int HP_MAX = 50;
    private static final int A_2_HP_MIN = 48;
    private static final int A_2_HP_MAX = 52;
    private static final int STAB_DMG = 13;
    private static final int A_2_STAB_DMG = 14;
    private static final int SCRAPE_DMG = 8;
    private static final int A_2_SCRAPE_DMG = 9;
    private int stabDmg;
    private int scrapeDmg;
    private int VULN_AMT = 1;
    private static final byte STAB = 1;
    private static final byte ENTANGLE = 2;
    private static final byte SCRAPE = 3;
    private static final String SCRAPE_NAME = MOVES[0];
    private static final String ENTANGLE_NAME = MOVES[1];
    private boolean usedEntangle = false, firstTurn = true;

    public SlaverRed(float x, float y) {
        super(NAME, "SlaverRed", 50, 0.0F, 0.0F, 170.0F, 230.0F, null, x, y);

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(48, 52);
        } else {
            setHp(46, 50);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.stabDmg = 14;
            this.scrapeDmg = 9;
        } else {
            this.stabDmg = 13;
            this.scrapeDmg = 8;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.stabDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.scrapeDmg));

        loadAnimation("images/monsters/theBottom/redSlaver/skeleton.atlas",
                "images/monsters/theBottom/redSlaver/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.firstTurn = true;
    }

    public void takeTurn() {
        switch (this.nextMove) {
            case 2:
                playSfx();
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "Use Net"));
                if (this.hb != null && AbstractDungeon.player.hb != null &&
                        !Settings.FAST_MODE) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                            (AbstractGameEffect) new EntangleEffect(this.hb.cX - 70.0F * Settings.scale,
                                    this.hb.cY + 10.0F * Settings.scale, AbstractDungeon.player.hb.cX,
                                    AbstractDungeon.player.hb.cY),
                            0.5F));
                }

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new EntanglePower((AbstractCreature) AbstractDungeon.player)));

                this.usedEntangle = true;
                break;
            case 1:
                playSfx();
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.SLASH_HORIZONTAL));
                break;
            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new AnimateSlowAttackAction((AbstractCreature) this));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
                if (AbstractDungeon.ascensionLevel >= 17) {
                    AbstractDungeon.actionManager
                            .addToBottom(
                                    (AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                                            (AbstractCreature) this,
                                            (AbstractPower) new VulnerablePower(
                                                    (AbstractCreature) AbstractDungeon.player, this.VULN_AMT + 1, true),
                                            this.VULN_AMT + 1));

                    break;
                }

                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                        (AbstractCreature) AbstractDungeon.player, (AbstractCreature) this,
                        (AbstractPower) new VulnerablePower((AbstractCreature) AbstractDungeon.player, this.VULN_AMT,
                                true),
                        this.VULN_AMT));
                break;
        }

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    private void playSfx() {
        int roll = MathUtils.random(1);
        if (roll == 0) {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_SLAVERRED_1A"));
        } else {
            AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("VO_SLAVERRED_1B"));
        }
    }

    private void playDeathSfx() {
        int roll = MathUtils.random(1);
        if (roll == 0) {
            CardCrawlGame.sound.play("VO_SLAVERRED_2A");
        } else {
            CardCrawlGame.sound.play("VO_SLAVERRED_2B");
        }
    }

    public void changeState(String stateName) {
        float tmp = this.state.getCurrent(0).getTime();
        AnimationState.TrackEntry e = this.state.setAnimation(0, "idleNoNet", true);
        e.setTime(tmp);
    }

    protected void getMove(int num) {
        if (this.firstTurn) {
            this.firstTurn = false;
            setMove((byte) 1, Intent.ATTACK, this.stabDmg);

            return;
        }

        if (num >= 75 && !this.usedEntangle) {
            setMove(ENTANGLE_NAME, (byte) 2, Intent.STRONG_DEBUFF);

            return;
        }

        if (num >= 55 && this.usedEntangle && !lastTwoMoves((byte) 1)) {
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);

            return;
        }
        if (AbstractDungeon.ascensionLevel >= 17) {

            if (!lastMove((byte) 3)) {
                setMove(SCRAPE_NAME, (byte) 3, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
                return;
            }
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);

            return;
        }

        if (!lastTwoMoves((byte) 3)) {
            setMove(SCRAPE_NAME, (byte) 3, Intent.ATTACK_DEBUFF, ((DamageInfo) this.damage.get(1)).base);
            return;
        }
        setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base);
    }

    public void die() {
        super.die();
        playDeathSfx();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\exordium\
 * SlaverRed.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

