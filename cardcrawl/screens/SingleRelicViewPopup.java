package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

import java.util.ArrayList;

public class SingleRelicViewPopup {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("SingleViewRelicPopup");
    public static final String[] TEXT = uiStrings.TEXT;

    public boolean isOpen = false;

    private ArrayList<AbstractRelic> group;
    private AbstractRelic relic;
    private AbstractRelic prevRelic;
    private float fadeTimer = 0.0F;
    private AbstractRelic nextRelic;
    private static final int W = 128;
    private Texture relicFrameImg;
    private Texture largeImg;
    private Color fadeColor = Color.BLACK.cpy();
    private Hitbox popupHb;
    private Hitbox prevHb;
    private Hitbox nextHb;
    private String rarityLabel = "";
    private static final String LARGE_IMG_DIR = "images/largeRelics/";
    private static final float DESC_LINE_SPACING = 30.0F * Settings.scale;
    private static final float DESC_LINE_WIDTH = 418.0F * Settings.scale;

    private final float RELIC_OFFSET_Y;

    public SingleRelicViewPopup() {
        this.RELIC_OFFSET_Y = 76.0F * Settings.scale;
    }

    public void open(AbstractRelic relic, ArrayList<AbstractRelic> group) {
        CardCrawlGame.isPopupOpen = true;
        relic.playLandingSFX();

        this.prevRelic = null;
        this.nextRelic = null;
        this.prevHb = null;
        this.nextHb = null;

        for (int i = 0; i < group.size(); i++) {
            if (group.get(i) == relic) {
                if (i != 0) {
                    this.prevRelic = group.get(i - 1);
                }
                if (i != group.size() - 1) {
                    this.nextRelic = group.get(i + 1);
                }

                break;
            }
        }
        this.prevHb = new Hitbox(160.0F * Settings.scale, 160.0F * Settings.scale);
        this.nextHb = new Hitbox(160.0F * Settings.scale, 160.0F * Settings.scale);
        this.prevHb.move(Settings.WIDTH / 2.0F - 400.0F * Settings.scale, Settings.HEIGHT / 2.0F);
        this.nextHb.move(Settings.WIDTH / 2.0F + 400.0F * Settings.scale, Settings.HEIGHT / 2.0F);

        this.popupHb = new Hitbox(550.0F * Settings.scale, 680.0F * Settings.scale);
        this.popupHb.move(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F);
        this.isOpen = true;
        this.group = group;
        this.relic = relic;
        this.fadeTimer = 0.25F;
        this.fadeColor.a = 0.0F;
        generateRarityLabel();
        generateFrameImg();
        this.relic.isSeen = UnlockTracker.isRelicSeen(relic.relicId);
        initializeLargeImg();
    }

    public void open(AbstractRelic relic) {
        CardCrawlGame.isPopupOpen = true;
        relic.playLandingSFX();

        this.prevRelic = null;
        this.nextRelic = null;
        this.prevHb = null;
        this.nextHb = null;

        this.popupHb = new Hitbox(550.0F * Settings.scale, 680.0F * Settings.scale);
        this.popupHb.move(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F);
        this.isOpen = true;
        this.group = null;
        this.relic = relic;
        this.fadeTimer = 0.25F;
        this.fadeColor.a = 0.0F;
        generateRarityLabel();
        generateFrameImg();
        this.relic.isSeen = UnlockTracker.isRelicSeen(relic.relicId);
        initializeLargeImg();
    }

    private void initializeLargeImg() {
        this.largeImg = ImageMaster.loadImage("images/largeRelics/" + this.relic.imgUrl);
    }

    public void close() {
        CardCrawlGame.isPopupOpen = false;
        this.isOpen = false;
        InputHelper.justReleasedClickLeft = false;

        if (this.largeImg != null) {
            this.largeImg.dispose();
            this.largeImg = null;
        }

        if (this.relicFrameImg != null) {
            this.relicFrameImg.dispose();
            this.relicFrameImg = null;
        }
    }

    public void update() {
        this.popupHb.update();
        updateArrows();
        updateInput();
        updateFade();
        updateSoundEffect();
    }

    private void updateArrows() {
        if (this.prevRelic != null) {
            this.prevHb.update();
            if (this.prevHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            }
            if (this.prevHb.clicked || (this.prevRelic != null && CInputActionSet.pageLeftViewDeck.isJustPressed())) {
                openPrev();
            }
        }

        if (this.nextRelic != null) {
            this.nextHb.update();
            if (this.nextHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            }

            if (this.nextHb.clicked
                    || (this.nextRelic != null && CInputActionSet.pageRightViewExhaust.isJustPressed())) {
                openNext();
            }
        }
    }

    private void updateInput() {
        if (InputHelper.justClickedLeft) {
            if (this.prevRelic != null && this.prevHb.hovered) {
                this.prevHb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
                return;
            }
            if (this.nextRelic != null && this.nextHb.hovered) {
                this.nextHb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");

                return;
            }
        }
        if (InputHelper.justReleasedClickLeft) {
            if (!this.popupHb.hovered) {
                close();
                FontHelper.ClearSRVFontTextures();
            }
        } else if (InputHelper.pressedEscape || CInputActionSet.cancel.isJustPressed()) {
            CInputActionSet.cancel.unpress();
            InputHelper.pressedEscape = false;
            if (AbstractDungeon.screen == null || AbstractDungeon.screen == AbstractDungeon.CurrentScreen.NONE) {
                AbstractDungeon.isScreenUp = false;
            }
            close();
            FontHelper.ClearSRVFontTextures();
        }

        if (this.prevRelic != null && InputActionSet.left.isJustPressed()) {
            openPrev();
        } else if (this.nextRelic != null && InputActionSet.right.isJustPressed()) {
            openNext();
        }
    }

    private void openPrev() {
        close();
        open(this.prevRelic, this.group);
        this.fadeTimer = 0.0F;
        this.fadeColor.a = 0.9F;
    }

    private void openNext() {
        close();
        open(this.nextRelic, this.group);
        this.fadeTimer = 0.0F;
        this.fadeColor.a = 0.9F;
    }

    private void updateFade() {
        this.fadeTimer -= Gdx.graphics.getDeltaTime();
        if (this.fadeTimer < 0.0F) {
            this.fadeTimer = 0.0F;
        }
        this.fadeColor.a = Interpolation.pow2In.apply(0.9F, 0.0F, this.fadeTimer * 4.0F);
    }

    private void updateSoundEffect() {
        String key = null;

        if (this.relic instanceof com.megacrit.cardcrawl.relics.Tingsha) {
            key = "TINGSHA";
        } else if (this.relic instanceof com.megacrit.cardcrawl.relics.Damaru) {
            key = "DAMARU";
        } else if (this.relic instanceof com.megacrit.cardcrawl.relics.SingingBowl) {
            key = "SINGING_BOWL";
        } else if (this.relic instanceof com.megacrit.cardcrawl.relics.CallingBell) {
            key = "BELL";
        } else if (this.relic instanceof com.megacrit.cardcrawl.relics.ChemicalX) {
            key = "POTION_3";
        } else if (this.relic instanceof com.megacrit.cardcrawl.relics.Cauldron) {
            key = "POTION_1";
        } else if (this.relic instanceof com.megacrit.cardcrawl.relics.MembershipCard) {
            key = "SHOP_PURCHASE";
        } else if (this.relic instanceof com.megacrit.cardcrawl.relics.CharonsAshes) {
            key = "CARD_BURN";
        }

        if (InputActionSet.selectCard_1.isJustPressed()) {
            CardCrawlGame.sound.playA(key, -0.2F);
        } else if (InputActionSet.selectCard_2.isJustPressed()) {
            CardCrawlGame.sound.playA(key, -0.15F);
        } else if (InputActionSet.selectCard_3.isJustPressed()) {
            CardCrawlGame.sound.playA(key, -0.1F);
        } else if (InputActionSet.selectCard_4.isJustPressed()) {
            CardCrawlGame.sound.playA(key, -0.05F);
        } else if (InputActionSet.selectCard_5.isJustPressed()) {
            CardCrawlGame.sound.playA(key, 0.0F);
        } else if (InputActionSet.selectCard_6.isJustPressed()) {
            CardCrawlGame.sound.playA(key, 0.05F);
        } else if (InputActionSet.selectCard_7.isJustPressed()) {
            CardCrawlGame.sound.playA(key, 0.1F);
        } else if (InputActionSet.selectCard_8.isJustPressed()) {
            CardCrawlGame.sound.playA(key, 0.15F);
        } else if (InputActionSet.selectCard_9.isJustPressed()) {
            CardCrawlGame.sound.playA(key, 0.2F);
        } else if (InputActionSet.selectCard_10.isJustPressed()) {
            CardCrawlGame.sound.playA(key, 0.25F);
        }
    }

    private void generateRarityLabel() {
        switch (this.relic.tier) {
            case BOSS:
                this.rarityLabel = TEXT[0];
                break;
            case COMMON:
                this.rarityLabel = TEXT[1];
                break;
            case DEPRECATED:
                this.rarityLabel = TEXT[2];
                break;
            case RARE:
                this.rarityLabel = TEXT[3];
                break;
            case SHOP:
                this.rarityLabel = TEXT[4];
                break;
            case SPECIAL:
                this.rarityLabel = TEXT[5];
                break;
            case STARTER:
                this.rarityLabel = TEXT[6];
                break;
            case UNCOMMON:
                this.rarityLabel = TEXT[7];
                break;
        }
    }

    private void generateFrameImg() {
        if (!this.relic.isSeen) {
            this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameCommon.png");

            return;
        }
        switch (this.relic.tier) {
            case BOSS:
                this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameBoss.png");
                break;
            case COMMON:
                this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameCommon.png");
                break;
            case DEPRECATED:
                this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameCommon.png");
                break;
            case RARE:
                this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameRare.png");
                break;
            case SHOP:
                this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameRare.png");
                break;
            case SPECIAL:
                this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameRare.png");
                break;
            case STARTER:
                this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameCommon.png");
                break;
            case UNCOMMON:
                this.relicFrameImg = ImageMaster.loadImage("images/ui/relicFrameUncommon.png");
                break;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.fadeColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
        renderPopupBg(sb);
        renderFrame(sb);
        renderArrows(sb);
        renderRelicImage(sb);
        renderName(sb);
        renderRarity(sb);
        renderDescription(sb);
        renderQuote(sb);
        renderTips(sb);
        this.popupHb.render(sb);
        if (this.prevHb != null) {
            this.prevHb.render(sb);
        }
        if (this.nextHb != null) {
            this.nextHb.render(sb);
        }
    }

    private void renderPopupBg(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.RELIC_POPUP, Settings.WIDTH / 2.0F - 960.0F, Settings.HEIGHT / 2.0F - 540.0F, 960.0F,
                540.0F, 1920.0F, 1080.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 1920, 1080, false, false);
    }

    private void renderFrame(SpriteBatch sb) {
        sb.draw(this.relicFrameImg, Settings.WIDTH / 2.0F - 960.0F, Settings.HEIGHT / 2.0F - 540.0F, 960.0F, 540.0F,
                1920.0F, 1080.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 1920, 1080, false, false);
    }

    private void renderArrows(SpriteBatch sb) {
        if (this.prevRelic != null) {
            sb.draw(ImageMaster.POPUP_ARROW, this.prevHb.cX - 128.0F, this.prevHb.cY - 128.0F, 128.0F, 128.0F, 256.0F,
                    256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 256, 256, false, false);

            if (Settings.isControllerMode) {
                sb.draw(CInputActionSet.pageLeftViewDeck
                        .getKeyImg(), this.prevHb.cX - 32.0F + 0.0F * Settings.scale,
                        this.prevHb.cY - 32.0F + 100.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
            }

            if (this.prevHb.hovered) {
                sb.setBlendFunction(770, 1);
                sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.5F));
                sb.draw(ImageMaster.POPUP_ARROW, this.prevHb.cX - 128.0F, this.prevHb.cY - 128.0F, 128.0F, 128.0F,
                        256.0F, 256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 256, 256, false, false);

                sb.setColor(Color.WHITE);
                sb.setBlendFunction(770, 771);
            }
        }

        if (this.nextRelic != null) {
            sb.draw(ImageMaster.POPUP_ARROW, this.nextHb.cX - 128.0F, this.nextHb.cY - 128.0F, 128.0F, 128.0F, 256.0F,
                    256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 256, 256, true, false);

            if (Settings.isControllerMode) {
                sb.draw(CInputActionSet.pageRightViewExhaust
                        .getKeyImg(), this.nextHb.cX - 32.0F + 0.0F * Settings.scale,
                        this.nextHb.cY - 32.0F + 100.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
            }

            if (this.nextHb.hovered) {
                sb.setBlendFunction(770, 1);
                sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.5F));
                sb.draw(ImageMaster.POPUP_ARROW, this.nextHb.cX - 128.0F, this.nextHb.cY - 128.0F, 128.0F, 128.0F,
                        256.0F, 256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 256, 256, true, false);

                sb.setColor(Color.WHITE);
                sb.setBlendFunction(770, 771);
            }
        }
    }

    private void renderRelicImage(SpriteBatch sb) {
        if (UnlockTracker.isRelicLocked(this.relic.relicId)) {
            sb.setColor(new Color(0.0F, 0.0F, 0.0F, 0.5F));
            sb.draw(ImageMaster.RELIC_LOCK_OUTLINE, Settings.WIDTH / 2.0F - 64.0F,
                    Settings.HEIGHT / 2.0F - 64.0F + this.RELIC_OFFSET_Y, 64.0F, 64.0F, 128.0F, 128.0F,
                    Settings.scale * 2.0F, Settings.scale * 2.0F, 0.0F, 0, 0, 128, 128, false, false);

            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.RELIC_LOCK, Settings.WIDTH / 2.0F - 64.0F,
                    Settings.HEIGHT / 2.0F - 64.0F + this.RELIC_OFFSET_Y, 64.0F, 64.0F, 128.0F, 128.0F,
                    Settings.scale * 2.0F, Settings.scale * 2.0F, 0.0F, 0, 0, 128, 128, false, false);

            return;
        }

        if (!this.relic.isSeen) {
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.75F));
        } else {
            sb.setColor(new Color(0.0F, 0.0F, 0.0F, 0.5F));
        }

        sb.draw(this.relic.outlineImg, Settings.WIDTH / 2.0F - 64.0F,
                Settings.HEIGHT / 2.0F - 64.0F + this.RELIC_OFFSET_Y, 64.0F, 64.0F, 128.0F, 128.0F,
                Settings.scale * 2.0F, Settings.scale * 2.0F, 0.0F, 0, 0, 128, 128, false, false);

        if (!this.relic.isSeen) {
            sb.setColor(Color.BLACK);
        } else {
            sb.setColor(Color.WHITE);
        }

        if (this.largeImg == null) {
            sb.draw(this.relic.img, Settings.WIDTH / 2.0F - 64.0F, Settings.HEIGHT / 2.0F - 64.0F + this.RELIC_OFFSET_Y,
                    64.0F, 64.0F, 128.0F, 128.0F, Settings.scale * 2.0F, Settings.scale * 2.0F, 0.0F, 0, 0, 128, 128,
                    false, false);

        } else {

            sb.draw(this.largeImg, Settings.WIDTH / 2.0F - 128.0F,
                    Settings.HEIGHT / 2.0F - 128.0F + this.RELIC_OFFSET_Y, 128.0F, 128.0F, 256.0F, 256.0F,
                    Settings.scale, Settings.scale, 0.0F, 0, 0, 256, 256, false, false);
        }
    }

    private void renderName(SpriteBatch sb) {
        if (UnlockTracker.isRelicLocked(this.relic.relicId)) {
            FontHelper.renderWrappedText(sb, FontHelper.SCP_cardDescFont, TEXT[8], Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F + 265.0F * Settings.scale, 9999.0F, Settings.CREAM_COLOR, 0.9F);

        } else if (this.relic.isSeen) {
            FontHelper.renderWrappedText(sb, FontHelper.SCP_cardDescFont, this.relic.name, Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F + 280.0F * Settings.scale, 9999.0F, Settings.CREAM_COLOR, 0.9F);

        } else {

            FontHelper.renderWrappedText(sb, FontHelper.SCP_cardDescFont, TEXT[9], Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F + 265.0F * Settings.scale, 9999.0F, Settings.CREAM_COLOR, 0.9F);
        }
    }

    private void renderRarity(SpriteBatch sb) {
        Color tmpColor;
        switch (this.relic.tier) {
            case BOSS:
                tmpColor = Settings.RED_TEXT_COLOR;
                break;
            case RARE:
                tmpColor = Settings.GOLD_COLOR;
                break;
            case UNCOMMON:
                tmpColor = Settings.BLUE_TEXT_COLOR;
                break;
            case COMMON:
                tmpColor = Settings.CREAM_COLOR;
                break;
            case STARTER:
                tmpColor = Settings.CREAM_COLOR;
                break;
            case SPECIAL:
                tmpColor = Settings.GOLD_COLOR;
                break;
            case SHOP:
                tmpColor = Settings.GOLD_COLOR;
                break;
            default:
                tmpColor = Settings.CREAM_COLOR;
                break;
        }
        if (this.relic.isSeen) {
            if (Settings.language == Settings.GameLanguage.VIE) {
                FontHelper.renderWrappedText(sb, FontHelper.cardDescFont_N, TEXT[10] + this.rarityLabel,
                        Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F + 240.0F * Settings.scale, 9999.0F, tmpColor,
                        1.0F);

            } else {

                FontHelper.renderWrappedText(sb, FontHelper.cardDescFont_N, this.rarityLabel + TEXT[10],
                        Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F + 240.0F * Settings.scale, 9999.0F, tmpColor,
                        1.0F);
            }
        }
    }

    private void renderDescription(SpriteBatch sb) {
        if (UnlockTracker.isRelicLocked(this.relic.relicId)) {
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, TEXT[11], Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F - 140.0F * Settings.scale, Settings.CREAM_COLOR, 1.0F);

        } else if (this.relic.isSeen) {
            FontHelper.renderSmartText(sb, FontHelper.cardDescFont_N, this.relic.description,
                    Settings.WIDTH / 2.0F - 200.0F * Settings.scale, Settings.HEIGHT / 2.0F - 140.0F * Settings.scale -

                            FontHelper.getSmartHeight(FontHelper.cardDescFont_N, this.relic.description,
                                    DESC_LINE_WIDTH, DESC_LINE_SPACING) / 2.0F,
                    DESC_LINE_WIDTH, DESC_LINE_SPACING, Settings.CREAM_COLOR);

        } else {

            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, TEXT[12], Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F - 140.0F * Settings.scale, Settings.CREAM_COLOR, 1.0F);
        }
    }

    private void renderQuote(SpriteBatch sb) {
        if (this.relic.isSeen) {
            if (this.relic.flavorText != null) {
                FontHelper.renderWrappedText(sb, FontHelper.SRV_quoteFont, this.relic.flavorText, Settings.WIDTH / 2.0F,
                        Settings.HEIGHT / 2.0F - 310.0F * Settings.scale, DESC_LINE_WIDTH, Settings.CREAM_COLOR, 1.0F);

            } else {

                FontHelper.renderWrappedText(sb, FontHelper.SRV_quoteFont, "\"Missing quote...\"",
                        Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F - 300.0F * Settings.scale, DESC_LINE_WIDTH,
                        Settings.CREAM_COLOR, 1.0F);
            }
        }
    }

    private void renderTips(SpriteBatch sb) {
        if (this.relic.isSeen) {
            ArrayList<PowerTip> t = new ArrayList<>();
            if (this.relic.tips.size() > 1) {
                for (int i = 1; i < this.relic.tips.size(); i++) {
                    t.add(this.relic.tips.get(i));
                }
            }

            if (!t.isEmpty())
                TipHelper.queuePowerTips(Settings.WIDTH / 2.0F + 340.0F * Settings.scale, 420.0F * Settings.scale, t);
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * SingleRelicViewPopup.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

