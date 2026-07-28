package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.UpgradeShineParticleEffect;

public class BattleStartEffect
        extends AbstractGameEffect {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("BattleStartEffect");
    public static final String[] TEXT = uiStrings.TEXT;

    private static final float EFFECT_DUR = 4.0F;
    private static final float HEIGHT_DIV_2 = Settings.HEIGHT / 2.0F;
    private static final float WIDTH_DIV_2 = Settings.WIDTH / 2.0F;

    private boolean surpriseAttack;
    private boolean soundPlayed = false;
    private boolean bossFight = false;
    private Color bgColor;
    private static final float TARGET_HEIGHT = 150.0F * Settings.scale;
    private static final float BG_RECT_EXPAND_SPEED = 3.0F;
    private float currentHeight = 0.0F;

    private String battleStartMessage;

    private static final String BATTLE_START_MSG = TEXT[0];
    public static final String PLAYER_TURN_MSG = TEXT[1];
    public static final String ENEMY_TURN_MSG = TEXT[2];
    public static final String TURN_TXT = TEXT[3];
    private String turnMsg;
    private static final float TEXT_FADE_SPEED = 5.0F;
    private static final float MAIN_MSG_OFFSET_Y = 20.0F * Settings.scale;
    private static final float TURN_MSG_OFFSET_Y = -30.0F * Settings.scale;
    private Color turnMessageColor = new Color(0.7F, 0.7F, 0.7F, 0.0F);
    private float timer1 = 1.0F;
    private float timer2 = 1.0F;
    private static final float MSG_VANISH_X = -Settings.WIDTH * 0.25F;
    private float firstMessageX = Settings.WIDTH / 2.0F;
    private float secondMessageX = Settings.WIDTH * 1.5F;

    private boolean showHb = false;

    private static TextureAtlas.AtlasRegion img = null;
    private static final float SWORD_ANIM_TIME = 0.5F;
    private float swordTimer = 0.5F;
    private static final float SWORD_START_X = -50.0F * Settings.scale;
    private static final float SWORD_DEST_X = Settings.WIDTH / 2.0F + 0.0F * Settings.scale;
    private float swordX;
    private float swordY;
    private Color swordColor = new Color(0.9F, 0.9F, 0.85F, 0.0F);
    private float swordAngle;
    private boolean swordSound1 = false;

    public BattleStartEffect(boolean surpriseAttack) {
        this.duration = 4.0F;
        this.startingDuration = 4.0F;
        this.surpriseAttack = surpriseAttack;

        this.bgColor = new Color(AbstractDungeon.fadeColor.r / 2.0F, AbstractDungeon.fadeColor.g / 2.0F,
                AbstractDungeon.fadeColor.b / 2.0F, 0.0F);

        if (img == null) {
            img = ImageMaster.vfxAtlas.findRegion("combat/battleStartSword");
        }

        this.scale = Settings.scale;
        this.swordY = Settings.HEIGHT / 2.0F - img.packedHeight / 2.0F + 20.0F * Settings.scale;

        if (surpriseAttack) {
            this.turnMsg = ENEMY_TURN_MSG;
        } else {
            this.turnMsg = PLAYER_TURN_MSG;
        }

        this.color = Settings.GOLD_COLOR.cpy();
        this.color.a = 0.0F;
        if (Settings.usesOrdinal) {
            this.battleStartMessage = Integer.toString(GameActionManager.turn)
                    + getOrdinalNaming(GameActionManager.turn) + TURN_TXT;
        } else if (Settings.language == Settings.GameLanguage.VIE) {
            this.battleStartMessage = TURN_TXT + " " + Integer.toString(GameActionManager.turn);
        } else {
            this.battleStartMessage = Integer.toString(GameActionManager.turn) + TURN_TXT;
        }

        if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
            this.bossFight = true;
            CardCrawlGame.sound.play("BATTLE_START_BOSS");
        } else if (MathUtils.randomBoolean()) {
            CardCrawlGame.sound.play("BATTLE_START_1");
        } else {
            CardCrawlGame.sound.play("BATTLE_START_2");
        }
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
        if (!this.showHb) {
            AbstractDungeon.player.showHealthBar();
            for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                m.showHealthBar();
            }
            this.showHb = true;
        }

        if (this.duration > 3.0F) {
            this.currentHeight = MathUtils.lerp(this.currentHeight, TARGET_HEIGHT, Gdx.graphics

                    .getDeltaTime() * 3.0F);
        } else if (this.duration < 0.5F) {
            this.currentHeight = MathUtils.lerp(this.currentHeight, 0.0F, Gdx.graphics.getDeltaTime() * 3.0F);
        }

        if (this.duration < 3.0F && this.timer1 != 0.0F) {
            this.timer1 -= Gdx.graphics.getDeltaTime();
            if (this.timer1 < 0.0F) {
                this.timer1 = 0.0F;
            }
            this.firstMessageX = Interpolation.pow2In.apply(this.firstMessageX, MSG_VANISH_X, 1.0F - this.timer1);
        } else if (this.duration < 3.0F && this.timer2 != 0.0F) {
            if (!this.soundPlayed) {
                CardCrawlGame.sound.play("TURN_EFFECT");
                AbstractDungeon.getMonsters().showIntent();
                this.soundPlayed = true;
            }
            this.timer2 -= Gdx.graphics.getDeltaTime();
            if (this.timer2 < 0.0F) {
                this.timer2 = 0.0F;
            }
            this.secondMessageX = Interpolation.pow2In.apply(this.secondMessageX, WIDTH_DIV_2, 1.0F - this.timer2);
        }

        if (this.duration > 1.0F) {
            this.color.a = MathUtils.lerp(this.color.a, 1.0F, Gdx.graphics.getDeltaTime() * 5.0F);
        } else {
            this.color.a = MathUtils.lerp(this.color.a, 0.0F, Gdx.graphics.getDeltaTime() * 5.0F);
        }
        this.color.a *= 0.75F;
        this.turnMessageColor.a = this.color.a;

        if (Settings.FAST_MODE) {
            this.duration -= Gdx.graphics.getDeltaTime();
        }
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }

        updateSwords();
    }

    private void updateSwords() {
        this.swordTimer -= Gdx.graphics.getDeltaTime();
        if (this.swordTimer < 0.0F) {
            this.swordTimer = 0.0F;
        }

        this.swordColor.a = Interpolation.fade.apply(1.0F, 0.01F, this.swordTimer / 0.5F);

        if (this.bossFight) {
            if (this.swordTimer < 0.1F && !this.swordSound1) {
                this.swordSound1 = true;

                CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.SHORT, false);
                for (int i = 0; i < 30; i++) {
                    if (MathUtils.randomBoolean()) {
                        AbstractDungeon.effectsQueue.add(new UpgradeShineParticleEffect(Settings.WIDTH / 2.0F +

                                MathUtils.random(-150.0F, 150.0F) * Settings.scale,
                                Settings.HEIGHT / 2.0F +
                                        MathUtils.random(-10.0F, 50.0F) * Settings.scale));
                    } else {
                        AbstractDungeon.topLevelEffectsQueue.add(new UpgradeShineParticleEffect(Settings.WIDTH / 2.0F +

                                MathUtils.random(-150.0F, 150.0F) * Settings.scale,
                                Settings.HEIGHT / 2.0F +
                                        MathUtils.random(-10.0F, 50.0F) * Settings.scale));
                    }
                }
            }

            this.swordX = Interpolation.pow3Out.apply(SWORD_DEST_X, SWORD_START_X, this.swordTimer / 0.5F);
            this.swordAngle = Interpolation.pow3Out.apply(-50.0F, 500.0F, this.swordTimer / 0.5F);
        } else {
            this.swordX = SWORD_DEST_X;
            this.swordAngle = -50.0F;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, HEIGHT_DIV_2 - this.currentHeight / 2.0F, Settings.WIDTH,
                this.currentHeight);

        renderSwords(sb);

        FontHelper.renderFontCentered(sb, FontHelper.bannerNameFont, BATTLE_START_MSG, this.firstMessageX,
                HEIGHT_DIV_2 + MAIN_MSG_OFFSET_Y, this.color, 1.0F);

        FontHelper.renderFontCentered(sb, FontHelper.bannerNameFont, this.turnMsg, this.secondMessageX,
                HEIGHT_DIV_2 + MAIN_MSG_OFFSET_Y, this.color, 1.0F);

        if (!this.surpriseAttack) {
            FontHelper.renderFontCentered(sb, FontHelper.turnNumFont, this.battleStartMessage, this.secondMessageX,
                    HEIGHT_DIV_2 + TURN_MSG_OFFSET_Y, this.turnMessageColor);
        }
    }

    public void dispose() {
    }

    private void renderSwords(SpriteBatch sb) {
        sb.setColor(this.swordColor);
        sb.draw((TextureRegion) img,
                Settings.WIDTH - this.swordX - img.packedWidth / 2.0F + this.firstMessageX - Settings.WIDTH / 2.0F,
                this.swordY, img.packedWidth / 2.0F, img.packedHeight / 2.0F, img.packedWidth, img.packedHeight,
                -this.scale, -this.scale, -this.swordAngle + 180.0F);

        sb.draw((TextureRegion) img, this.swordX - img.packedWidth / 2.0F + this.firstMessageX - Settings.WIDTH / 2.0F,
                this.swordY, img.packedWidth / 2.0F, img.packedHeight / 2.0F, img.packedWidth, img.packedHeight,
                this.scale, this.scale, this.swordAngle);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * BattleStartEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
