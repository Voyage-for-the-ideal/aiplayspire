package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
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
import com.megacrit.cardcrawl.powers.PainfulStabsPower;

public class BookOfStabbing extends AbstractMonster {
    public static final String ID = "BookOfStabbing";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("BookOfStabbing");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final int HP_MIN = 160;

    private static final int HP_MAX = 164;

    private static final int A_2_HP_MIN = 168;
    private static final int A_2_HP_MAX = 172;
    private static final int STAB_DAMAGE = 6;
    private static final int BIG_STAB_DAMAGE = 21;
    private int stabCount = 1;
    private static final int A_2_STAB_DAMAGE = 7;
    private static final int A_2_BIG_STAB_DAMAGE = 24;
    private int stabDmg;
    private int bigStabDmg;
    private static final byte STAB = 1;
    private static final byte BIG_STAB = 2;

    public BookOfStabbing() {
        super(NAME, "BookOfStabbing", 164, 0.0F, -10.0F, 320.0F, 420.0F, null, 0.0F, 5.0F);
        loadAnimation("images/monsters/theCity/stabBook/skeleton.atlas",
                "images/monsters/theCity/stabBook/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.2F);
        e.setTimeScale(0.8F);

        this.type = EnemyType.ELITE;
        this.dialogX = -70.0F * Settings.scale;
        this.dialogY = 50.0F * Settings.scale;

        if (AbstractDungeon.ascensionLevel >= 8) {
            setHp(168, 172);
        } else {
            setHp(160, 164);
        }

        if (AbstractDungeon.ascensionLevel >= 3) {
            this.stabDmg = 7;
            this.bigStabDmg = 24;
        } else {
            this.stabDmg = 6;
            this.bigStabDmg = 21;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.stabDmg));
        this.damage.add(new DamageInfo((AbstractCreature) this, this.bigStabDmg));
    }

    public void usePreBattleAction() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this,
                (AbstractCreature) this, (AbstractPower) new PainfulStabsPower((AbstractCreature) this)));
    }

    public void takeTurn() {
        int i;
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));

                for (i = 0; i < this.stabCount; i++) {
                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new SFXAction("MONSTER_BOOK_STAB_" +
                            MathUtils.random(0, 3)));
                    AbstractDungeon.actionManager
                            .addToBottom(
                                    (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player,
                                            this.damage

                                                    .get(0),
                                            AbstractGameAction.AttackEffect.SLASH_VERTICAL, false, true));
                }
                break;

            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "ATTACK_2"));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.5F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(1), AbstractGameAction.AttackEffect.SLASH_VERTICAL));
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
        if (num < 15) {
            if (lastMove((byte) 2)) {
                this.stabCount++;
                setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, this.stabCount, true);
            } else {
                setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
                if (AbstractDungeon.ascensionLevel >= 18) {
                    this.stabCount++;
                }
            }

        } else if (lastTwoMoves((byte) 1)) {
            setMove((byte) 2, Intent.ATTACK, ((DamageInfo) this.damage.get(1)).base);
            if (AbstractDungeon.ascensionLevel >= 18) {
                this.stabCount++;
            }
        } else {
            this.stabCount++;
            setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, this.stabCount, true);
        }
    }

    public void die() {
        super.die();
        CardCrawlGame.sound.play("STAB_BOOK_DEATH");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * BookOfStabbing.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

