package com.megacrit.cardcrawl.credits;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.GameCursor;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.CreditStrings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.DoorUnlockScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;

import java.util.ArrayList;

public class CreditsScreen {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("CreditsScreen");

    private Color screenColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    private float fadeInTimer = 2.0F;
    private float targetY;
    private static final float scrollSpeed = 50.0F * Settings.scale;
    private float currentY;
    private static final int W = 720;
    private ArrayList<CreditLine> lines = new ArrayList<>();
    private static final float LINE_SPACING = 45.0F;
    private static final float SECTION_SPACING = 150.0F;
    private static final float SCROLL_START_Y = 400.0F * Settings.scale;
    private static final float THANK_YOU_TIME = 3.0F;
    private float thankYouTimer = 3.0F;
    private Color thankYouColor = Settings.CREAM_COLOR.cpy();
    private static float END_OF_CREDITS_Y = 15000.0F * Settings.scale;
    private String THANKS_MSG = (CardCrawlGame.languagePack.getCreditString("THANKS_MSG")).HEADER;
    private static Texture logoImg = null;
    private float skipTimer;
    private static final float SKIP_MENU_UP_DUR = 2.0F;
    private static final float SKIP_APPEAR_TIME = 0.5F;
    private boolean isSkippable;
    private boolean closingSkipMenu;
    private boolean showNeowAfter = false;
    private static final float SKIP_START_X = -300.0F * Settings.scale;
    private static final float SKIP_END_X = 50.0F * Settings.scale;
    private float skipX;
    private float tmpY = -400.0F;

    public CreditsScreen() {
        this.currentY = SCROLL_START_Y;
        this.targetY = this.currentY;
        creditLineHelper("DEV");
        creditLineHelper("OPS");
        creditLineHelper("SOUND");
        creditLineHelper("VOICE");
        creditLineHelper("PORTRAITS");
        creditLineHelper("ILLUSTRATION");
        creditLineHelper("ANIMATION");

        if (Settings.isConsoleBuild) {
            creditLineHelper("PORTING");
            creditLineHelper("PUBLISHING");
            creditLineHelper("KAKEHASHI");
            END_OF_CREDITS_Y += 1600.0F * Settings.scale;
        }

        creditLineHelper("LOC_ZHS");
        creditLineHelper("LOC_ZHT");
        creditLineHelper("LOC_DEU");
        creditLineHelper("LOC_DUT");
        creditLineHelper("LOC_EPO");
        creditLineHelper("LOC_FIN");
        creditLineHelper("LOC_FRA");
        creditLineHelper("LOC_GRE");
        creditLineHelper("LOC_IND");
        creditLineHelper("LOC_ITA");
        creditLineHelper("LOC_JPN");
        creditLineHelper("LOC_KOR");
        creditLineHelper("LOC_NOR");
        creditLineHelper("LOC_POL");
        creditLineHelper("LOC_PTB");
        creditLineHelper("LOC_RUS");
        creditLineHelper("LOC_SPA");
        creditLineHelper("LOC_SRB");
        creditLineHelper("LOC_THA");
        creditLineHelper("LOC_TUR");
        creditLineHelper("LOC_UKR");
        creditLineHelper("LOC_VIE");
        creditLineHelper("LOC_ADDITIONAL");
        creditLineHelper("TEST");
        creditLineHelper("SPECIAL");

        if (logoImg == null) {
            switch (Settings.language) {

            }

            logoImg = ImageMaster.loadImage("images/ui/credits_logo/eng.png");
        }
    }

    private void creditLineHelper(String id) {
        CreditStrings str = CardCrawlGame.languagePack.getCreditString(id);

        this.lines.add(new CreditLine(str.HEADER, this.tmpY -= 150.0F, true));
        for (int i = 0; i < str.NAMES.length; i++) {
            this.lines.add(new CreditLine(str.NAMES[i], this.tmpY -= 45.0F, false));
        }
    }

    public void open(boolean playCreditsBgm) {
        if (playCreditsBgm) {
            this.showNeowAfter = true;
            CardCrawlGame.playCreditsBgm = false;
            CardCrawlGame.music.playTempBgmInstantly("CREDITS", true);
        } else {
            this.showNeowAfter = false;
        }

        CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.CREDITS;
        this.skipTimer = 0.0F;
        this.isSkippable = false;
        this.closingSkipMenu = false;
        this.skipX = SKIP_START_X;
        GameCursor.hidden = true;
        this.thankYouColor.a = 0.0F;
        this.screenColor.a = 0.0F;
        this.thankYouTimer = 3.0F;
        CardCrawlGame.mainMenuScreen.darken();
        this.fadeInTimer = 2.0F;
        this.currentY = SCROLL_START_Y;
        this.targetY = this.currentY;
    }

    public void update() {
        if (InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            close();
        }

        if (InputHelper.isMouseDown_R) {
            this.targetY -= Gdx.graphics.getDeltaTime() * scrollSpeed * 4.0F;
        } else {
            this.targetY += Gdx.graphics.getDeltaTime() * scrollSpeed;

            if (this.currentY > END_OF_CREDITS_Y) {
                this.thankYouTimer -= Gdx.graphics.getDeltaTime();
                if (this.thankYouTimer < 0.0F) {
                    this.thankYouTimer = 0.0F;
                }
                this.thankYouColor.a = Interpolation.fade.apply(1.0F, 0.0F, this.thankYouTimer / 3.0F);
            }
        }

        if (this.thankYouColor.a == 0.0F) {
            if (Gdx.input.isKeyJustPressed(62)) {
                this.targetY = SCROLL_START_Y;
            }

            if (InputHelper.scrolledUp) {
                this.targetY -= 100.0F * Settings.scale;
            } else if (InputHelper.scrolledDown) {
                this.targetY += 100.0F * Settings.scale;
            }

            if (CInputActionSet.up.isJustPressed()) {
                this.targetY -= 300.0F * Settings.scale;
            } else if (CInputActionSet.down.isJustPressed()) {
                this.targetY += 300.0F * Settings.scale;
            } else if (CInputActionSet.inspectDown.isJustPressed()) {
                this.targetY -= 1000.0F * Settings.scale;
            } else if (CInputActionSet.inspectUp.isJustPressed()) {
                this.targetY += 1000.0F * Settings.scale;
            }
        }

        this.currentY = MathHelper.scrollSnapLerpSpeed(this.currentY, this.targetY);

        updateFade();
        skipLogic();
    }

    private void skipLogic() {
        if (InputHelper.justClickedLeft || CInputActionSet.select.isJustPressed()) {
            if (this.isSkippable) {
                close();
            } else if (!this.isSkippable && this.skipTimer == 0.0F) {
                this.skipTimer = 0.5F;
                this.skipX = SKIP_END_X;
            }
        }

        if (this.skipTimer != 0.0F) {
            this.skipTimer -= Gdx.graphics.getDeltaTime();

            if (!this.isSkippable && !this.closingSkipMenu) {
                this.skipX = Interpolation.swingIn.apply(SKIP_END_X, SKIP_START_X, this.skipTimer * 2.0F);
            } else if (this.closingSkipMenu) {
                this.skipX = Interpolation.fade.apply(SKIP_START_X, SKIP_END_X, this.skipTimer * 2.0F);
            } else {
                this.skipX = SKIP_END_X;
            }

            if (this.skipTimer < 0.0F) {
                if (!this.isSkippable && !this.closingSkipMenu) {
                    this.isSkippable = true;
                    this.skipTimer = 2.0F;
                } else if (!this.closingSkipMenu) {
                    this.closingSkipMenu = true;
                    this.isSkippable = false;
                    this.skipTimer = 0.5F;
                } else {
                    this.isSkippable = false;
                    this.closingSkipMenu = false;
                    this.skipTimer = 0.0F;
                }
            }
        }
    }

    public void close() {
        if (this.showNeowAfter) {
            CardCrawlGame.music.justFadeOutTempBGM();
            CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.NEOW_SCREEN;
            CardCrawlGame.mainMenuScreen.neowNarrateScreen.open();
        } else if (DoorUnlockScreen.show) {
            CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.DOOR_UNLOCK;
            CardCrawlGame.mainMenuScreen.doorUnlockScreen.open(false);
        } else if (CardCrawlGame.mainMenuScreen.screen == MainMenuScreen.CurScreen.CREDITS) {
            CardCrawlGame.mainMenuScreen.lighten();
            CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.MAIN_MENU;
            CardCrawlGame.music.fadeOutTempBGM();
            GameCursor.hidden = false;
        }
    }

    private void updateFade() {
        this.fadeInTimer -= Gdx.graphics.getDeltaTime();
        if (this.fadeInTimer < 0.0F) {
            this.fadeInTimer = 0.0F;
        }
        this.screenColor.a = Interpolation.fade.apply(1.0F, 0.0F, this.fadeInTimer / 2.0F);
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.screenColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);

        sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.screenColor.a));
        sb.draw(logoImg, Settings.WIDTH / 2.0F - 360.0F, this.currentY - 360.0F, 360.0F, 360.0F, 720.0F, 720.0F,
                Settings.scale, Settings.scale, 0.0F, 0, 0, 720, 720, false, false);

        for (CreditLine c : this.lines) {
            c.render(sb, this.currentY);
        }

        FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, this.THANKS_MSG, Settings.WIDTH / 2.0F,
                Settings.HEIGHT / 2.0F, this.thankYouColor);

        if (Settings.isTouchScreen) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, uiStrings.TEXT[2], this.skipX,
                    50.0F * Settings.scale, Settings.BLUE_TEXT_COLOR);

        } else if (!Settings.isControllerMode) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, uiStrings.TEXT[0], this.skipX,
                    50.0F * Settings.scale, Settings.BLUE_TEXT_COLOR);

        } else {

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, uiStrings.TEXT[1],
                    this.skipX + 46.0F * Settings.scale, 50.0F * Settings.scale, Settings.BLUE_TEXT_COLOR);

            sb.setColor(Color.WHITE);
            sb.draw(CInputActionSet.select
                    .getKeyImg(), this.skipX - 32.0F + 10.0F * Settings.scale, -32.0F + 44.0F * Settings.scale, 32.0F,
                    32.0F, 64.0F, 64.0F, Settings.scale * 0.9F, Settings.scale * 0.9F, 0.0F, 0, 0, 64, 64, false,
                    false);
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\credits\
 * CreditsScreen.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

