package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.TipTracker;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.options.ConfirmPopup;

public class DungeonTransitionScreen {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("DungeonTransitionScreen");
    public static final String[] TEXT = uiStrings.TEXT;

    public boolean isComplete = false;

    public boolean msgCreated = false;

    public boolean isFading = false;
    public float timer;
    private ConfirmPopup popup = null;

    public String name;
    public String levelNum;
    public String levelName;
    private String source;
    private boolean playSFX = false;
    private Color color = Settings.CREAM_COLOR.cpy();
    private Color lvlColor = Settings.BLUE_TEXT_COLOR.cpy();
    private float oscillateTimer;
    private float animTimer = 0.0F;
    private float continueFader;
    private Color continueColor = Settings.GOLD_COLOR.cpy();

    public DungeonTransitionScreen(String key) {
        if (!((Boolean) TipTracker.tips.get("NO_FTUE")).booleanValue()) {
            this.popup = new ConfirmPopup(TEXT[0], TEXT[1], ConfirmPopup.ConfirmType.SKIP_FTUE);
            this.popup.show();
        }

        this.source = "";
        this.name = "";

        this.timer = 2.0F;
        this.continueFader = 0.0F;
        this.oscillateTimer = 0.0F;
        this.continueColor.a = 0.0F;
        this.lvlColor.a = 0.0F;
        this.color.a = 0.0F;
        setAreaName(key);
        this.isComplete = true;
    }

    private void setAreaName(String key) {
        switch (key) {
            case "Exordium":
                this.levelNum = TEXT[2];
                this.levelName = TEXT[3];
                break;
            case "TheCity":
                this.levelNum = TEXT[4];
                this.levelName = TEXT[5];
                break;
            case "TheBeyond":
                this.levelNum = TEXT[6];
                this.levelName = TEXT[7];
                break;
            case "TheEnding":
                this.levelNum = TEXT[8];
                this.levelName = TEXT[9];
                break;
            default:
                this.levelNum = TEXT[8];
                this.levelName = TEXT[9];
                break;
        }

        AbstractDungeon.name = this.levelName;
        AbstractDungeon.levelNum = this.levelNum;
    }

    private void oscillateColor() {
        this.oscillateTimer += Gdx.graphics.getDeltaTime() * 5.0F;
        this.continueColor.a = 0.33F + (MathUtils.cos(this.oscillateTimer) + 1.0F) / 3.0F;

        if (!this.isFading) {
            if (this.continueFader != 1.0F) {
                this.continueFader += Gdx.graphics.getDeltaTime() / 2.0F;
                if (this.continueFader > 1.0F) {
                    this.continueFader = 1.0F;
                }
            }

        } else if (this.continueFader != 0.0F) {
            this.continueFader -= Gdx.graphics.getDeltaTime();
            if (this.continueFader < 0.0F) {
                this.continueFader = 0.0F;
            }
        }

        this.continueColor.a *= this.continueFader;
    }

    public void update() {
        if (this.popup != null &&
                this.popup.shown) {
            this.popup.update();

            return;
        }

        if (this.msgCreated) {
            oscillateColor();
        }

        if (Settings.isDebug || InputHelper.justClickedLeft) {
            InputHelper.justClickedLeft = false;
            this.isComplete = true;
        }

        if (this.isFading) {
            this.timer -= Gdx.graphics.getDeltaTime();
            if (this.timer < 0.0F) {
                this.isComplete = true;
            } else {
                this.color.a = this.timer;

                return;
            }
        }
        if (this.animTimer > 0.5F && !this.playSFX) {
            this.playSFX = true;
            CardCrawlGame.sound.play("DUNGEON_TRANSITION");
        }

        if (!this.msgCreated) {
            this.animTimer += Gdx.graphics.getDeltaTime();
            if (this.animTimer > 4.0F) {
                this.msgCreated = true;
                this.animTimer = 4.0F;
            }

            if (this.animTimer > 2.0F) {
                this.color.a = 1.0F;
            } else {
                this.color.a = this.animTimer / 2.0F;
            }
        }
    }

    public void render(SpriteBatch sb) {
        this.lvlColor.a = this.color.a;
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, this.levelNum,
                Settings.WIDTH / 2.0F - 44.0F * Settings.scale, Settings.HEIGHT * 0.54F, this.lvlColor);

        FontHelper.renderFontCentered(sb, FontHelper.dungeonTitleFont, this.levelName, Settings.WIDTH / 2.0F,
                Settings.HEIGHT / 2.0F, this.color);

        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, "\"" + this.source + "\"", Settings.WIDTH / 2.0F,
                Settings.HEIGHT * 0.44F, this.color);

        FontHelper.renderFontCenteredWidth(sb, FontHelper.tipBodyFont, TEXT[10], Settings.WIDTH / 2.0F,
                100.0F * Settings.scale, this.continueColor);

        if (this.popup != null && this.popup.shown)
            this.popup.render(sb);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * DungeonTransitionScreen.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

