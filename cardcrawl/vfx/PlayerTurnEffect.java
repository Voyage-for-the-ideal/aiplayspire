package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.vfx.combat.BattleStartEffect;

public class PlayerTurnEffect
        extends AbstractGameEffect {
    private static final float DUR = 2.0F;
    private static final float HEIGHT_DIV_2 = Settings.HEIGHT / 2.0F;
    private static final float WIDTH_DIV_2 = Settings.WIDTH / 2.0F;

    private Color bgColor;

    private static final float TARGET_HEIGHT = 150.0F * Settings.scale;
    private static final float BG_RECT_EXPAND_SPEED = 3.0F;
    private float currentHeight = 0.0F;

    private String turnMessage;

    private static final float MAIN_MSG_OFFSET_Y = 20.0F * Settings.scale;
    private static final float TURN_MSG_OFFSET_Y = -30.0F * Settings.scale;
    private Color turnMessageColor = new Color(0.7F, 0.7F, 0.7F, 0.0F);

    public PlayerTurnEffect() {
        this.duration = 2.0F;
        this.startingDuration = 2.0F;

        this.bgColor = new Color(AbstractDungeon.fadeColor.r / 2.0F, AbstractDungeon.fadeColor.g / 2.0F,
                AbstractDungeon.fadeColor.b / 2.0F, 0.0F);

        this.color = Settings.GOLD_COLOR.cpy();
        this.color.a = 0.0F;
        if (Settings.usesOrdinal) {
            this.turnMessage = Integer.toString(GameActionManager.turn) + getOrdinalNaming(GameActionManager.turn)
                    + BattleStartEffect.TURN_TXT;
        } else if (Settings.language == Settings.GameLanguage.VIE) {
            this.turnMessage = BattleStartEffect.TURN_TXT + " " + Integer.toString(GameActionManager.turn);
        } else {
            this.turnMessage = Integer.toString(GameActionManager.turn) + BattleStartEffect.TURN_TXT;
        }
        AbstractDungeon.player.energy.recharge();
        for (AbstractRelic r : AbstractDungeon.player.relics) {
            r.onEnergyRecharge();
        }
        for (AbstractPower p : AbstractDungeon.player.powers) {
            p.onEnergyRecharge();
        }
        CardCrawlGame.sound.play("TURN_EFFECT");
        AbstractDungeon.getMonsters().showIntent();
        this.scale = 1.0F;
    }

    public static String getOrdinalNaming(int i) {
        (new String[10])[0] = "th";
        (new String[10])[1] = "st";
        (new String[10])[2] = "nd";
        (new String[10])[3] = "rd";
        (new String[10])[4] = "th";
        (new String[10])[5] = "th";
        (new String[10])[6] = "th";
        (new String[10])[7] = "th";
        (new String[10])[8] = "th";
        (new String[10])[9] = "th";
        return (i % 100 == 11 || i % 100 == 12 || i % 100 == 13) ? "th" : (new String[10])[i % 10];
    }

    public void update() {
        if (this.duration > 1.5F) {
            this.currentHeight = MathUtils.lerp(this.currentHeight, TARGET_HEIGHT, Gdx.graphics

                    .getDeltaTime() * 3.0F);
        } else if (this.duration < 0.5F) {
            this.currentHeight = MathUtils.lerp(this.currentHeight, 0.0F, Gdx.graphics.getDeltaTime() * 3.0F);
        }

        if (this.duration > 1.5F) {
            this.scale = Interpolation.exp10In.apply(1.0F, 3.0F, (this.duration - 1.5F) * 2.0F);
            this.color.a = Interpolation.exp10In.apply(1.0F, 0.0F, (this.duration - 1.5F) * 2.0F);
        } else if (this.duration < 0.5F) {
            this.scale = Interpolation.pow3In.apply(0.9F, 1.0F, this.duration * 2.0F);
            this.color.a = Interpolation.pow3In.apply(0.0F, 1.0F, this.duration * 2.0F);
        }
        this.color.a *= 0.75F;
        this.turnMessageColor.a = this.color.a;

        if (Settings.FAST_MODE) {
            this.duration -= Gdx.graphics.getDeltaTime();
        }
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
            for (AbstractPower p : AbstractDungeon.player.powers) {
                p.atEnergyGain();
            }
        }
    }

    public void render(SpriteBatch sb) {
        if (!this.isDone) {
            sb.setColor(this.bgColor);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, HEIGHT_DIV_2 - this.currentHeight / 2.0F, Settings.WIDTH,
                    this.currentHeight);

            sb.setBlendFunction(770, 1);
            FontHelper.renderFontCentered(sb, FontHelper.bannerNameFont, BattleStartEffect.PLAYER_TURN_MSG, WIDTH_DIV_2,
                    HEIGHT_DIV_2 + MAIN_MSG_OFFSET_Y, this.color, this.scale);

            FontHelper.renderFontCentered(sb, FontHelper.turnNumFont, this.turnMessage, WIDTH_DIV_2,
                    HEIGHT_DIV_2 + TURN_MSG_OFFSET_Y, this.turnMessageColor, this.scale);

            sb.setBlendFunction(770, 771);
        }
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * PlayerTurnEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
