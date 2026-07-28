package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
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

public class BanditPointy extends AbstractMonster {
    public static final String ID = "BanditChild";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("BanditChild");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    public static final int HP = 30;

    public static final int A_2_HP_MIN = 34;

    private static final int ATTACK_DMG = 5;
    private static final int A_2_ATTACK_DMG = 6;
    private int attackDmg;
    private static final byte POINTY_SPECIAL = 1;

    public BanditPointy(float x, float y) {
        super(NAME, "BanditChild", 30, -5.0F, -4.0F, 190.0F, 180.0F, null, x, y);
        this.dialogX = 0.0F * Settings.scale;
        this.dialogY = 50.0F * Settings.scale;

        if (AbstractDungeon.ascensionLevel >= 7) {
            setHp(34);
        } else {
            setHp(30);
        }

        if (AbstractDungeon.ascensionLevel >= 2) {
            this.attackDmg = 6;
        } else {
            this.attackDmg = 5;
        }

        this.damage.add(new DamageInfo((AbstractCreature) this, this.attackDmg, DamageInfo.DamageType.NORMAL));

        loadAnimation("images/monsters/theCity/pointy/skeleton.atlas", "images/monsters/theCity/pointy/skeleton.json",
                1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * MathUtils.random());
        this.stateData.setMix("Hit", "Idle", 0.2F);
        this.state.setTimeScale(1.0F);
    }

    public void takeTurn() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ChangeStateAction(this, "SLASH"));
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new WaitAction(0.4F));
        AbstractDungeon.actionManager.addToBottom(
                (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                        .get(0), AbstractGameAction.AttackEffect.SLASH_VERTICAL));
        AbstractDungeon.actionManager.addToBottom(
                (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                        .get(0), AbstractGameAction.AttackEffect.SLASH_HORIZONTAL));
        AbstractDungeon.actionManager.addToBottom(
                (AbstractGameAction) new SetMoveAction(this, (byte) 1, Intent.ATTACK, ((DamageInfo) this.damage
                        .get(0)).base, 2, true));
    }

    public void deathReact() {
        if (!isDeadOrEscaped()) {
            AbstractDungeon.actionManager
                    .addToBottom((AbstractGameAction) new TalkAction((AbstractCreature) this, DIALOG[0]));
        }
    }

    public void changeState(String key) {
        switch (key) {
            case "SLASH":
                this.state.setAnimation(0, "Attack", false);
                this.state.addAnimation(0, "Idle", true, 0.0F);
                break;
        }
    }

    public void damage(DamageInfo info) {
        super.damage(info);
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
            this.state.setAnimation(0, "Hit", false);
            this.state.setTimeScale(1.0F);
            this.state.addAnimation(0, "Idle", true, 0.0F);
        }
    }

    protected void getMove(int num) {
        setMove((byte) 1, Intent.ATTACK, ((DamageInfo) this.damage.get(0)).base, 2, true);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * BanditPointy.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

