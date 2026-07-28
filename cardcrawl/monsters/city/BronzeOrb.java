package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.unique.ApplyStasisAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderFlashEffect;
import com.megacrit.cardcrawl.vfx.combat.SmallLaserEffect;

public class BronzeOrb extends AbstractMonster {
    public static final String ID = "BronzeOrb";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("BronzeOrb");
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private static final int HP_MIN = 52;

    private static final int HP_MAX = 58;

    private static final int A_2_HP_MIN = 54;
    private static final int A_2_HP_MAX = 60;
    private static final int BEAM_DMG = 8;

    public BronzeOrb(float x, float y, int count) {
        super(monsterStrings.NAME, "BronzeOrb", AbstractDungeon.monsterHpRng

                .random(52, 58), 0.0F, 0.0F, 160.0F, 160.0F, "images/monsters/theCity/automaton/orb.png", x, y);

        if (AbstractDungeon.ascensionLevel >= 9) {
            setHp(54, 60);
        } else {
            setHp(52, 58);
        }

        this.count = count;
        this.damage.add(new DamageInfo((AbstractCreature) this, 8));
    }
    private static final int BLOCK_AMT = 12;
    private static final byte BEAM = 1;
    private static final byte SUPPORT_BEAM = 2;
    private static final byte STASIS = 3;
    private boolean usedStasis = false;
    private int count;

    public void takeTurn() {
        switch (this.nextMove) {
            case 1:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new SFXAction("ATTACK_MAGIC_BEAM_SHORT", 0.5F));
                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new VFXAction((AbstractGameEffect) new BorderFlashEffect(Color.SKY)));
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new SmallLaserEffect(AbstractDungeon.player.hb.cX,
                                AbstractDungeon.player.hb.cY, this.hb.cX, this.hb.cY),
                        0.3F));

                AbstractDungeon.actionManager.addToBottom(
                        (AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, this.damage
                                .get(0), AbstractGameAction.AttackEffect.NONE));
                break;
            case 2:
                AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new GainBlockAction(
                        (AbstractCreature) AbstractDungeon.getMonsters().getMonster("BronzeAutomaton"),
                        (AbstractCreature) this, 12));
                break;
            case 3:
                AbstractDungeon.actionManager
                        .addToBottom((AbstractGameAction) new ApplyStasisAction((AbstractCreature) this));
                break;
        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new RollMoveAction(this));
    }

    public void update() {
        super.update();
        if (this.count % 2 == 0) {
            this.animY = MathUtils.cosDeg((float) (System.currentTimeMillis() / 6L % 360L)) * 6.0F * Settings.scale;
        } else {
            this.animY = -MathUtils.cosDeg((float) (System.currentTimeMillis() / 6L % 360L)) * 6.0F * Settings.scale;
        }
    }

    protected void getMove(int num) {
        if (!this.usedStasis && num >= 25) {
            setMove((byte) 3, Intent.STRONG_DEBUFF);
            this.usedStasis = true;

            return;
        }
        if (num >= 70 && !lastTwoMoves((byte) 2)) {
            setMove((byte) 2, Intent.DEFEND);
            return;
        }
        if (!lastTwoMoves((byte) 1)) {
            setMove((byte) 1, Intent.ATTACK, 8);

            return;
        }
        setMove((byte) 2, Intent.DEFEND);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\city\
 * BronzeOrb.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

