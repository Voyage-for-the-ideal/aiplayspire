package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class TipHelper {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("TipHelper");
    public static final String[] TEXT = uiStrings.TEXT;

    private static final Logger logger = LogManager.getLogger(TipHelper.class.getName());
    private static boolean renderedTipThisFrame = false;
    private static boolean isCard = false;
    private static ArrayList<String> KEYWORDS = new ArrayList<>();
    private static float drawX;
    private static float drawY;
    private static ArrayList<PowerTip> POWER_TIPS = new ArrayList<>();
    private static String HEADER = null;
    private static String BODY = null;
    private static AbstractCard card;
    private static final Color BASE_COLOR = new Color(1.0F, 0.9725F, 0.8745F, 1.0F);

    private static final float CARD_TIP_PAD = 12.0F * Settings.scale;
    private static final float SHADOW_DIST_Y = 14.0F * Settings.scale;
    private static final float SHADOW_DIST_X = 9.0F * Settings.scale;
    private static final float BOX_EDGE_H = 32.0F * Settings.scale;
    private static final float BOX_BODY_H = 64.0F * Settings.scale;
    private static final float BOX_W = 320.0F * Settings.scale;

    private static GlyphLayout gl = new GlyphLayout();
    private static float textHeight;
    private static final float TEXT_OFFSET_X = 22.0F * Settings.scale;
    private static final float HEADER_OFFSET_Y = 12.0F * Settings.scale;
    private static final float ORB_OFFSET_Y = -8.0F * Settings.scale;
    private static final float BODY_OFFSET_Y = -20.0F * Settings.scale;
    private static final float BODY_TEXT_WIDTH = 280.0F * Settings.scale;
    private static final float TIP_DESC_LINE_SPACING = 26.0F * Settings.scale;

    private static final float POWER_ICON_OFFSET_X = 40.0F * Settings.scale;

    public static void render(SpriteBatch sb) {
        if (!Settings.hidePopupDetails &&
                renderedTipThisFrame) {

            if (AbstractDungeon.player != null && (AbstractDungeon.player.inSingleTargetMode
                    || (AbstractDungeon.player.isDraggingCard && !Settings.isTouchScreen))) {

                HEADER = null;
                BODY = null;
                card = null;
                renderedTipThisFrame = false;

                return;
            }
            if (Settings.isTouchScreen && AbstractDungeon.player != null && AbstractDungeon.player.isHoveringDropZone) {

                HEADER = null;
                BODY = null;
                card = null;
                renderedTipThisFrame = false;

                return;
            }
            if (isCard && card != null) {
                if (card.current_x > Settings.WIDTH * 0.75F) {
                    renderKeywords(card.current_x - AbstractCard.IMG_WIDTH / 2.0F - CARD_TIP_PAD - BOX_W,
                            card.current_y + AbstractCard.IMG_HEIGHT / 2.0F - BOX_EDGE_H, sb, KEYWORDS);

                } else {

                    renderKeywords(card.current_x + AbstractCard.IMG_WIDTH / 2.0F + CARD_TIP_PAD,
                            card.current_y + AbstractCard.IMG_HEIGHT / 2.0F - BOX_EDGE_H, sb, KEYWORDS);
                }

                card = null;
                isCard = false;

            } else if (HEADER != null) {
                textHeight = -FontHelper.getSmartHeight(FontHelper.tipBodyFont, BODY, BODY_TEXT_WIDTH,
                        TIP_DESC_LINE_SPACING) - 7.0F * Settings.scale;

                renderTipBox(drawX, drawY, sb, HEADER, BODY);
                HEADER = null;
            } else {

                renderPowerTips(drawX, drawY, sb, POWER_TIPS);
            }

            renderedTipThisFrame = false;
        }
    }

    public static void renderGenericTip(float x, float y, String header, String body) {
        if (!Settings.hidePopupDetails) {
            if (!renderedTipThisFrame) {
                renderedTipThisFrame = true;
                HEADER = header;
                BODY = body;
                drawX = x;
                drawY = y;
            } else if (HEADER == null && !KEYWORDS.isEmpty()) {
                logger.info("! " + (String) KEYWORDS.get(0));
            }
        }
    }

    public static void queuePowerTips(float x, float y, ArrayList<PowerTip> powerTips) {
        if (!renderedTipThisFrame) {
            renderedTipThisFrame = true;
            drawX = x;
            drawY = y;
            POWER_TIPS = powerTips;
        } else if (HEADER == null && !KEYWORDS.isEmpty()) {
            logger.info("! " + (String) KEYWORDS.get(0));
        }
    }

    public static void renderTipForCard(AbstractCard c, SpriteBatch sb, ArrayList<String> keywords) {
        if (!renderedTipThisFrame) {
            isCard = true;
            card = c;
            convertToReadable(keywords);
            KEYWORDS = keywords;
            renderedTipThisFrame = true;
        }
    }

    private static void convertToReadable(ArrayList<String> keywords) {
        ArrayList<String> add = new ArrayList<>();
        keywords.addAll(add);
    }

    private static void renderPowerTips(float x, float y, SpriteBatch sb, ArrayList<PowerTip> powerTips) {
        float originalY = y;
        boolean offsetLeft = false;
        if (x > Settings.WIDTH / 2.0F) {
            offsetLeft = true;
        }
        float offset = 0.0F;
        for (PowerTip tip : powerTips) {
            textHeight = getPowerTipHeight(tip);
            float offsetChange = textHeight + BOX_EDGE_H * 3.15F;

            if (offset + offsetChange >= Settings.HEIGHT * 0.7F) {
                y = originalY;
                offset = 0.0F;
                if (offsetLeft) {
                    x -= 324.0F * Settings.scale;
                } else {
                    x += 324.0F * Settings.scale;
                }
            }

            renderTipBox(x, y, sb, tip.header, tip.body);

            gl.setText(FontHelper.tipHeaderFont, tip.header, Color.WHITE, 0.0F, -1, false);
            if (tip.img != null) {
                sb.setColor(Color.WHITE);
                sb.draw(tip.img, x + TEXT_OFFSET_X + gl.width + 5.0F * Settings.scale, y - 10.0F * Settings.scale,
                        32.0F * Settings.scale, 32.0F * Settings.scale);

            } else if (tip.imgRegion != null) {
                sb.setColor(Color.WHITE);
                sb.draw((TextureRegion) tip.imgRegion,
                        x + gl.width + POWER_ICON_OFFSET_X - tip.imgRegion.packedWidth / 2.0F,
                        y + 5.0F * Settings.scale - tip.imgRegion.packedHeight / 2.0F, tip.imgRegion.packedWidth / 2.0F,
                        tip.imgRegion.packedHeight / 2.0F, tip.imgRegion.packedWidth, tip.imgRegion.packedHeight,
                        Settings.scale * 0.75F, Settings.scale * 0.75F, 0.0F);
            }

            y -= offsetChange;
            offset += offsetChange;
        }
    }

    private static float getPowerTipHeight(PowerTip powerTip) {
        return -FontHelper.getSmartHeight(FontHelper.tipBodyFont, powerTip.body, BODY_TEXT_WIDTH, TIP_DESC_LINE_SPACING)
                - 7.0F * Settings.scale;
    }

    public static float calculateAdditionalOffset(ArrayList<PowerTip> powerTips, float hBcY) {
        if (powerTips.isEmpty()) {
            return 0.0F;
        }
        return (1.0F - hBcY / Settings.HEIGHT) * getTallestOffset(powerTips)
                - (getPowerTipHeight(powerTips.get(0)) + BOX_EDGE_H * 3.15F) / 2.0F;
    }

    public static float calculateToAvoidOffscreen(ArrayList<PowerTip> powerTips, float hBcY) {
        if (powerTips.isEmpty()) {
            return 0.0F;
        }
        return Math.max(0.0F, getTallestOffset(powerTips) - hBcY);
    }

    private static float getTallestOffset(ArrayList<PowerTip> powerTips) {
        float currentOffset = 0.0F;
        float maxOffset = 0.0F;
        for (PowerTip p : powerTips) {
            float offsetChange = getPowerTipHeight(p) + BOX_EDGE_H * 3.15F;
            if (currentOffset + offsetChange >= Settings.HEIGHT * 0.7F) {
                currentOffset = 0.0F;
            }
            currentOffset += offsetChange;
            if (currentOffset > maxOffset) {
                maxOffset = currentOffset;
            }
        }
        return maxOffset;
    }

    private static void renderKeywords(float x, float y, SpriteBatch sb, ArrayList<String> keywords) {
        if (keywords.size() >= 4) {
            y += (keywords.size() - 1) * 62.0F * Settings.scale;
        }

        for (String s : keywords) {
            if (!GameDictionary.keywords.containsKey(s)) {
                logger.info("MISSING: " + s + " in Dictionary!");
                continue;
            }
            textHeight = -FontHelper.getSmartHeight(FontHelper.tipBodyFont, (String) GameDictionary.keywords

                    .get(s), BODY_TEXT_WIDTH, TIP_DESC_LINE_SPACING) - 7.0F * Settings.scale;

            renderBox(sb, s, x, y);
            y -= textHeight + BOX_EDGE_H * 3.15F;
        }
    }

    private static void renderTipBox(float x, float y, SpriteBatch sb, String title, String description) {
        float h = textHeight;

        sb.setColor(Settings.TOP_PANEL_SHADOW_COLOR);
        sb.draw(ImageMaster.KEYWORD_TOP, x + SHADOW_DIST_X, y - SHADOW_DIST_Y, BOX_W, BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BODY, x + SHADOW_DIST_X, y - h - BOX_EDGE_H - SHADOW_DIST_Y, BOX_W, h + BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BOT, x + SHADOW_DIST_X, y - h - BOX_BODY_H - SHADOW_DIST_Y, BOX_W, BOX_EDGE_H);

        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.KEYWORD_TOP, x, y, BOX_W, BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BODY, x, y - h - BOX_EDGE_H, BOX_W, h + BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BOT, x, y - h - BOX_BODY_H, BOX_W, BOX_EDGE_H);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipHeaderFont, title, x + TEXT_OFFSET_X, y + HEADER_OFFSET_Y,
                Settings.GOLD_COLOR);

        FontHelper.renderSmartText(sb, FontHelper.tipBodyFont, description, x + TEXT_OFFSET_X, y + BODY_OFFSET_Y,
                BODY_TEXT_WIDTH, TIP_DESC_LINE_SPACING, BASE_COLOR);
    }

    public static void renderTipEnergy(SpriteBatch sb, TextureAtlas.AtlasRegion region, float x, float y) {
        sb.setColor(Color.WHITE);
        sb.draw(region
                .getTexture(), x + region.offsetX * Settings.scale, y + region.offsetY * Settings.scale, 0.0F, 0.0F,
                region.packedWidth, region.packedHeight, Settings.scale, Settings.scale, 0.0F, region

                        .getRegionX(),
                region
                        .getRegionY(),
                region
                        .getRegionWidth(),
                region
                        .getRegionHeight(),
                false, false);
    }

    private static void renderBox(SpriteBatch sb, String word, float x, float y) {
        float h = textHeight;

        sb.setColor(Settings.TOP_PANEL_SHADOW_COLOR);
        sb.draw(ImageMaster.KEYWORD_TOP, x + SHADOW_DIST_X, y - SHADOW_DIST_Y, BOX_W, BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BODY, x + SHADOW_DIST_X, y - h - BOX_EDGE_H - SHADOW_DIST_Y, BOX_W, h + BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BOT, x + SHADOW_DIST_X, y - h - BOX_BODY_H - SHADOW_DIST_Y, BOX_W, BOX_EDGE_H);

        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.KEYWORD_TOP, x, y, BOX_W, BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BODY, x, y - h - BOX_EDGE_H, BOX_W, h + BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BOT, x, y - h - BOX_BODY_H, BOX_W, BOX_EDGE_H);

        TextureAtlas.AtlasRegion currentOrb = (AbstractDungeon.player != null) ? AbstractDungeon.player.getOrb()
                : AbstractCard.orb_red;

        if (word.equals("[R]") || word.equals("[G]") || word.equals("[B]") || word.equals("[W]")
                || word.equals("[E]")) {

            if (word.equals("[R]")) {
                renderTipEnergy(sb, AbstractCard.orb_red, x + TEXT_OFFSET_X, y + ORB_OFFSET_Y);
            } else if (word.equals("[G]")) {
                renderTipEnergy(sb, AbstractCard.orb_green, x + TEXT_OFFSET_X, y + ORB_OFFSET_Y);
            } else if (word.equals("[B]")) {
                renderTipEnergy(sb, AbstractCard.orb_blue, x + TEXT_OFFSET_X, y + ORB_OFFSET_Y);
            } else if (word.equals("[W]")) {
                renderTipEnergy(sb, AbstractCard.orb_purple, x + TEXT_OFFSET_X, y + ORB_OFFSET_Y);
            } else if (word.equals("[E]")) {
                renderTipEnergy(sb, currentOrb, x + TEXT_OFFSET_X, y + ORB_OFFSET_Y);
            }

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipHeaderFont,

                    capitalize(TEXT[0]), x + TEXT_OFFSET_X * 2.5F, y + HEADER_OFFSET_Y, Settings.GOLD_COLOR);

        } else {

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipHeaderFont,

                    capitalize(word), x + TEXT_OFFSET_X, y + HEADER_OFFSET_Y, Settings.GOLD_COLOR);
        }

        FontHelper.renderSmartText(sb, FontHelper.tipBodyFont, GameDictionary.keywords

                .get(word), x + TEXT_OFFSET_X, y + BODY_OFFSET_Y, BODY_TEXT_WIDTH, TIP_DESC_LINE_SPACING, BASE_COLOR);
    }

    public static String capitalize(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char tmp = input.charAt(i);
            if (i == 0) {
                tmp = Character.toUpperCase(tmp);
            } else {
                tmp = Character.toLowerCase(tmp);
            }
            sb.append(tmp);
        }
        return sb.toString();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\TipHelper.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

