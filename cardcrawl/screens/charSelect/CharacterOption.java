package com.megacrit.cardcrawl.screens.charSelect;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.screens.CharSelectInfo;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class CharacterOption {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("CharacterOption");
    public static final String[] TEXT = uiStrings.TEXT;

    private Texture buttonImg;

    private Texture portraitImg;

    private String portraitUrl;
    private static final float HB_W = 150.0F * Settings.scale;
    public AbstractPlayer c;
    public boolean selected = false;
    public boolean locked = false;
    public Hitbox hb;
    private static final int BUTTON_W = 220;
    public static final String ASSETS_DIR = "images/ui/charSelect/";
    private static final Color BLACK_OUTLINE_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.5F);
    private Color glowColor = new Color(1.0F, 0.8F, 0.2F, 0.0F);

    private static final int ICON_W = 64;

    private static final float DEST_INFO_X = Settings.isMobile ? (160.0F * Settings.scale) : (200.0F * Settings.scale);
    private static final float START_INFO_X = -800.0F * Settings.xScale;
    private float infoX = START_INFO_X;
    private float infoY = Settings.HEIGHT / 2.0F;
    public String name = "";
    private static final float NAME_OFFSET_Y = 200.0F * Settings.scale;
    private String hp;
    private int gold;
    private String flavorText;
    private CharSelectInfo charInfo;
    private int unlocksRemaining;
    private int maxAscensionLevel = 1;

    public CharacterOption(String optionName, AbstractPlayer c, Texture buttonImg, Texture portraitImg) {
        this.name = optionName;
        this.hb = new Hitbox(HB_W, HB_W);
        this.buttonImg = buttonImg;
        this.portraitImg = portraitImg;
        this.c = c;
        this.charInfo = null;
        this.charInfo = c.getLoadout();
        this.hp = this.charInfo.hp;
        this.gold = this.charInfo.gold;
        this.flavorText = this.charInfo.flavorText;
        this.unlocksRemaining = 5 - UnlockTracker.getUnlockLevel(c.chosenClass);
    }

    public CharacterOption(String optionName, AbstractPlayer c, String buttonUrl, String portraitImg) {
        this.name = optionName;
        this.hb = new Hitbox(HB_W, HB_W);
        this.buttonImg = ImageMaster.loadImage("images/ui/charSelect/" + buttonUrl);
        this.portraitUrl = c.getPortraitImageName();
        this.c = c;
        this.charInfo = null;
        this.charInfo = c.getLoadout();
        this.hp = this.charInfo.hp;
        this.gold = this.charInfo.gold;
        this.flavorText = this.charInfo.flavorText;
        this.unlocksRemaining = 5 - UnlockTracker.getUnlockLevel(c.chosenClass);
    }

    public CharacterOption(AbstractPlayer c) {
        this.hb = new Hitbox(HB_W, HB_W);
        this.buttonImg = ImageMaster.CHAR_SELECT_LOCKED;
        this.locked = true;
        this.c = c;
    }

    public void saveChosenAscensionLevel(int level) {
        Prefs pref = this.c.getPrefs();
        pref.putInteger("LAST_ASCENSION_LEVEL", level);
        pref.flush();
    }

    public void incrementAscensionLevel(int level) {
        if (level > this.maxAscensionLevel) {
            return;
        }

        saveChosenAscensionLevel(level);
        CardCrawlGame.mainMenuScreen.charSelectScreen.ascensionLevel = level;
        CardCrawlGame.mainMenuScreen.charSelectScreen.ascLevelInfoString = CharacterSelectScreen.A_TEXT[level - 1];
    }

    public void decrementAscensionLevel(int level) {
        if (level == 0) {
            return;
        }

        saveChosenAscensionLevel(level);
        CardCrawlGame.mainMenuScreen.charSelectScreen.ascensionLevel = level;
        CardCrawlGame.mainMenuScreen.charSelectScreen.ascLevelInfoString = CharacterSelectScreen.A_TEXT[level - 1];
    }

    public void update() {
        updateHitbox();
        updateInfoPosition();
    }

    private void updateHitbox() {
        this.hb.update();
        if (this.hb.justHovered) {
            CardCrawlGame.sound.playA("UI_HOVER", -0.3F);
        }

        if (this.hb.hovered && this.locked) {
            if (this.c.chosenClass == AbstractPlayer.PlayerClass.THE_SILENT) {
                TipHelper.renderGenericTip(InputHelper.mX + 70.0F * Settings.xScale,
                        InputHelper.mY - 10.0F * Settings.scale, TEXT[0], TEXT[1]);

            } else if (this.c.chosenClass == AbstractPlayer.PlayerClass.DEFECT) {
                TipHelper.renderGenericTip(InputHelper.mX + 70.0F * Settings.xScale,
                        InputHelper.mY - 10.0F * Settings.scale, TEXT[0], TEXT[3]);

            } else if (this.c.chosenClass == AbstractPlayer.PlayerClass.WATCHER) {
                TipHelper.renderGenericTip(InputHelper.mX + 70.0F * Settings.xScale,
                        InputHelper.mY - 10.0F * Settings.scale, TEXT[0], TEXT[10]);
            }
        }

        if (InputHelper.justClickedLeft && !this.locked &&
                this.hb.hovered) {
            CardCrawlGame.sound.playA("UI_CLICK_1", -0.4F);
            this.hb.clickStarted = true;
        }

        if (this.hb.clicked) {
            this.hb.clicked = false;

            if (!this.selected) {
                CardCrawlGame.mainMenuScreen.charSelectScreen.deselectOtherOptions(this);
                this.selected = true;
                CardCrawlGame.mainMenuScreen.charSelectScreen.justSelected();
                CardCrawlGame.chosenCharacter = this.c.chosenClass;
                CardCrawlGame.mainMenuScreen.charSelectScreen.confirmButton.isDisabled = false;
                CardCrawlGame.mainMenuScreen.charSelectScreen.confirmButton.show();

                if (this.portraitUrl != null) {
                    CardCrawlGame.mainMenuScreen.charSelectScreen.bgCharImg = ImageMaster
                            .loadImage("images/ui/charSelect/" + this.portraitUrl);
                } else {

                    CardCrawlGame.mainMenuScreen.charSelectScreen.bgCharImg = this.portraitImg;
                }

                Prefs pref = this.c.getPrefs();
                if (!this.locked) {
                    this.c.doCharSelectScreenSelectEffect();
                }

                if (pref != null) {
                    CardCrawlGame.mainMenuScreen.charSelectScreen.ascensionLevel = pref
                            .getInteger("LAST_ASCENSION_LEVEL", 1);

                    if (20 < CardCrawlGame.mainMenuScreen.charSelectScreen.ascensionLevel) {
                        CardCrawlGame.mainMenuScreen.charSelectScreen.ascensionLevel = 20;
                    }
                    this.maxAscensionLevel = pref.getInteger("ASCENSION_LEVEL", 1);
                    if (20 < this.maxAscensionLevel) {
                        this.maxAscensionLevel = 20;
                    }
                    int ascensionLevel = CardCrawlGame.mainMenuScreen.charSelectScreen.ascensionLevel;
                    if (ascensionLevel > this.maxAscensionLevel) {
                        ascensionLevel = this.maxAscensionLevel;
                    }

                    if (ascensionLevel > 0) {
                        CardCrawlGame.mainMenuScreen.charSelectScreen.ascLevelInfoString = CharacterSelectScreen.A_TEXT[ascensionLevel
                                - 1];
                    } else {

                        CardCrawlGame.mainMenuScreen.charSelectScreen.ascLevelInfoString = "";
                    }
                }
            }
        }
    }

    private void updateInfoPosition() {
        if (this.selected) {
            this.infoX = MathHelper.uiLerpSnap(this.infoX, DEST_INFO_X);
        } else {
            this.infoX = MathHelper.uiLerpSnap(this.infoX, START_INFO_X);
        }
    }

    public void render(SpriteBatch sb) {
        renderOptionButton(sb);
        renderInfo(sb);
        this.hb.render(sb);
    }

    private void renderOptionButton(SpriteBatch sb) {
        if (this.selected) {
            this.glowColor.a = 0.25F
                    + (MathUtils.cosDeg((float) (System.currentTimeMillis() / 4L % 360L)) + 1.25F) / 3.5F;
            sb.setColor(this.glowColor);
        } else {
            sb.setColor(BLACK_OUTLINE_COLOR);
        }

        sb.draw(ImageMaster.CHAR_OPT_HIGHLIGHT, this.hb.cX - 110.0F, this.hb.cY - 110.0F, 110.0F, 110.0F, 220.0F,
                220.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 220, 220, false, false);

        if (this.selected || this.hb.hovered) {
            sb.setColor(Color.WHITE);
        } else {
            sb.setColor(Color.LIGHT_GRAY);
        }

        sb.draw(this.buttonImg, this.hb.cX - 110.0F, this.hb.cY - 110.0F, 110.0F, 110.0F, 220.0F, 220.0F,
                Settings.scale, Settings.scale, 0.0F, 0, 0, 220, 220, false, false);
    }

    private void renderInfo(SpriteBatch sb) {
        if (!this.name.equals("")) {
            if (!Settings.isMobile) {
                FontHelper.renderSmartText(sb, FontHelper.bannerNameFont, this.name,
                        this.infoX - 35.0F * Settings.scale, this.infoY + NAME_OFFSET_Y, 99999.0F,
                        38.0F * Settings.scale, Settings.GOLD_COLOR);

                sb.draw(ImageMaster.TP_HP, this.infoX - 10.0F * Settings.scale - 32.0F,
                        this.infoY + 95.0F * Settings.scale - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);

                FontHelper.renderSmartText(sb, FontHelper.tipHeaderFont, TEXT[4] + this.hp,
                        this.infoX + 18.0F * Settings.scale, this.infoY + 102.0F * Settings.scale, 10000.0F, 10000.0F,
                        Settings.RED_TEXT_COLOR);

                sb.draw(ImageMaster.TP_GOLD, this.infoX + 190.0F * Settings.scale - 32.0F,
                        this.infoY + 95.0F * Settings.scale - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);

                FontHelper.renderSmartText(sb, FontHelper.tipHeaderFont, TEXT[5] +

                        Integer.toString(this.gold), this.infoX + 220.0F * Settings.scale,
                        this.infoY + 102.0F * Settings.scale, 10000.0F, 10000.0F, Settings.GOLD_COLOR);

                FontHelper.renderSmartText(sb, FontHelper.tipHeaderFont, this.flavorText,
                        this.infoX - 26.0F * Settings.scale, this.infoY + 40.0F * Settings.scale, 10000.0F,
                        30.0F * Settings.scale, Settings.CREAM_COLOR);

                if (this.unlocksRemaining > 0) {
                    FontHelper.renderSmartText(sb, FontHelper.tipHeaderFont,

                            Integer.toString(this.unlocksRemaining) + TEXT[6], this.infoX - 26.0F * Settings.scale,
                            this.infoY - 112.0F * Settings.scale, 10000.0F, 10000.0F, Settings.CREAM_COLOR);

                    int unlockProgress = UnlockTracker.getCurrentProgress(this.c.chosenClass);
                    int unlockCost = UnlockTracker.getCurrentScoreCost(this.c.chosenClass);

                    FontHelper.renderSmartText(sb, FontHelper.tipHeaderFont,

                            Integer.toString(unlockProgress) + "/" + unlockCost + TEXT[9],
                            this.infoX - 26.0F * Settings.scale, this.infoY - 140.0F * Settings.scale, 10000.0F,
                            10000.0F, Settings.CREAM_COLOR);
                }

                renderRelics(sb);
            } else {

                FontHelper.renderSmartText(sb, FontHelper.bannerNameFont, this.name,
                        this.infoX - 35.0F * Settings.scale, this.infoY + 350.0F * Settings.scale, 99999.0F,
                        38.0F * Settings.scale, Settings.GOLD_COLOR, 1.1F);

                sb.draw(ImageMaster.TP_HP, this.infoX - 10.0F * Settings.scale - 32.0F,
                        this.infoY + 230.0F * Settings.scale - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);

                FontHelper.renderSmartText(sb, FontHelper.buttonLabelFont, TEXT[4] + this.hp,
                        this.infoX + 18.0F * Settings.scale, this.infoY + 243.0F * Settings.scale, 10000.0F, 10000.0F,
                        Settings.RED_TEXT_COLOR, 0.8F);

                sb.draw(ImageMaster.TP_GOLD, this.infoX + 260.0F * Settings.scale - 32.0F,
                        this.infoY + 230.0F * Settings.scale - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);

                FontHelper.renderSmartText(sb, FontHelper.buttonLabelFont, TEXT[5] +

                        Integer.toString(this.gold), this.infoX + 290.0F * Settings.scale,
                        this.infoY + 243.0F * Settings.scale, 10000.0F, 10000.0F, Settings.GOLD_COLOR, 0.8F);

                if (this.selected) {
                    FontHelper.renderSmartText(sb, FontHelper.buttonLabelFont, this.flavorText,
                            this.infoX - 26.0F * Settings.scale, this.infoY + 170.0F * Settings.scale, 10000.0F,
                            40.0F * Settings.scale, Settings.CREAM_COLOR, 0.9F);
                }

                if (this.unlocksRemaining > 0) {
                    FontHelper.renderSmartText(sb, FontHelper.buttonLabelFont,

                            Integer.toString(this.unlocksRemaining) + TEXT[6], this.infoX - 26.0F * Settings.scale,
                            this.infoY - 60.0F * Settings.scale, 10000.0F, 10000.0F, Settings.CREAM_COLOR, 0.8F);

                    int unlockProgress = UnlockTracker.getCurrentProgress(this.c.chosenClass);
                    int unlockCost = UnlockTracker.getCurrentScoreCost(this.c.chosenClass);

                    FontHelper.renderSmartText(sb, FontHelper.buttonLabelFont,

                            Integer.toString(unlockProgress) + "/" + unlockCost + TEXT[9],
                            this.infoX - 26.0F * Settings.scale, this.infoY - 100.0F * Settings.scale, 10000.0F,
                            10000.0F, Settings.CREAM_COLOR, 0.8F);
                }

                renderRelics(sb);
            }
        }
    }

    private void renderRelics(SpriteBatch sb) {
        if (this.charInfo.relics.size() == 1) {
            if (!Settings.isMobile) {
                sb.setColor(Settings.QUARTER_TRANSPARENT_BLACK_COLOR);
                sb.draw(
                        (RelicLibrary.getRelic((String) this.charInfo.relics.get(0))).outlineImg, this.infoX - 64.0F,
                        this.infoY - 60.0F * Settings.scale - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 128, 128, false, false);

                sb.setColor(Color.WHITE);
                sb.draw(
                        (RelicLibrary.getRelic((String) this.charInfo.relics.get(0))).img, this.infoX - 64.0F,
                        this.infoY - 60.0F * Settings.scale - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 128, 128, false, false);

                FontHelper.renderSmartText(sb, FontHelper.tipHeaderFont,

                        (RelicLibrary.getRelic((String) this.charInfo.relics.get(0))).name,
                        this.infoX + 44.0F * Settings.scale, this.infoY - 40.0F * Settings.scale, 10000.0F, 10000.0F,
                        Settings.GOLD_COLOR);

                String relicString = (RelicLibrary.getRelic((String) this.charInfo.relics.get(0))).description;
                if (this.charInfo.name.equals(TEXT[7])) {
                    relicString = TEXT[8];
                }

                FontHelper.renderSmartText(sb, FontHelper.tipBodyFont, relicString, this.infoX + 44.0F * Settings.scale,
                        this.infoY - 66.0F * Settings.scale, 10000.0F, 10000.0F, Settings.CREAM_COLOR);

            } else {

                sb.setColor(Settings.QUARTER_TRANSPARENT_BLACK_COLOR);
                sb.draw(
                        (RelicLibrary.getRelic((String) this.charInfo.relics.get(0))).outlineImg, this.infoX - 64.0F,
                        this.infoY + 30.0F * Settings.scale - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F,
                        Settings.scale * 1.4F, Settings.scale * 1.4F, 0.0F, 0, 0, 128, 128, false, false);

                sb.setColor(Color.WHITE);
                sb.draw(
                        (RelicLibrary.getRelic((String) this.charInfo.relics.get(0))).img, this.infoX - 64.0F,
                        this.infoY + 30.0F * Settings.scale - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F,
                        Settings.scale * 1.4F, Settings.scale * 1.4F, 0.0F, 0, 0, 128, 128, false, false);

                FontHelper.renderSmartText(sb, FontHelper.topPanelInfoFont,

                        (RelicLibrary.getRelic((String) this.charInfo.relics.get(0))).name,
                        this.infoX + 60.0F * Settings.scale, this.infoY + 60.0F * Settings.scale, 10000.0F, 10000.0F,
                        Settings.GOLD_COLOR);

                String relicString = (RelicLibrary.getRelic((String) this.charInfo.relics.get(0))).description;
                if (this.charInfo.name.equals(TEXT[7])) {
                    relicString = TEXT[8];
                }

                if (this.selected) {
                    FontHelper.renderSmartText(sb, FontHelper.topPanelInfoFont, relicString,
                            this.infoX + 60.0F * Settings.scale, this.infoY + 24.0F * Settings.scale, 10000.0F,
                            10000.0F, Settings.CREAM_COLOR);

                }

            }

        } else {

            for (int i = 0; i < this.charInfo.relics.size(); i++) {
                AbstractRelic r = RelicLibrary.getRelic(this.charInfo.relics.get(i));
                r.updateDescription(this.charInfo.player.chosenClass);

                Hitbox relicHitbox = new Hitbox(
                        80.0F * Settings.scale * (0.01F + 1.0F - 0.019F * this.charInfo.relics.size()),
                        80.0F * Settings.scale);

                relicHitbox.move(this.infoX + i * 72.0F * Settings.scale * (0.01F + 1.0F - 0.019F * this.charInfo.relics
                        .size()), this.infoY - 60.0F * Settings.scale);

                relicHitbox.render(sb);
                relicHitbox.update();
                if (relicHitbox.hovered) {
                    if (InputHelper.mX < 1400.0F * Settings.scale) {
                        TipHelper.queuePowerTips(InputHelper.mX + 60.0F * Settings.scale,
                                InputHelper.mY - 50.0F * Settings.scale, r.tips);

                    } else {

                        TipHelper.queuePowerTips(InputHelper.mX - 350.0F * Settings.scale,
                                InputHelper.mY - 50.0F * Settings.scale, r.tips);
                    }
                }

                sb.setColor(new Color(0.0F, 0.0F, 0.0F, 0.25F));
                sb.draw(r.outlineImg,
                        this.infoX - 64.0F + i * 72.0F * Settings.scale * (0.01F + 1.0F - 0.019F * this.charInfo.relics

                                .size()),
                        this.infoY - 60.0F * Settings.scale - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F,
                        Settings.scale * (0.01F + 1.0F - 0.019F * this.charInfo.relics

                                .size()),
                        Settings.scale * (0.01F + 1.0F - 0.019F * this.charInfo.relics
                                .size()),
                        0.0F, 0, 0, 128, 128, false, false);

                sb.setColor(Color.WHITE);
                sb.draw(r.img,
                        this.infoX - 64.0F + i * 72.0F * Settings.scale * (0.01F + 1.0F - 0.019F * this.charInfo.relics

                                .size()),
                        this.infoY - 60.0F * Settings.scale - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F,
                        Settings.scale * (0.01F + 1.0F - 0.019F * this.charInfo.relics

                                .size()),
                        Settings.scale * (0.01F + 1.0F - 0.019F * this.charInfo.relics
                                .size()),
                        0.0F, 0, 0, 128, 128, false, false);
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\charSelect
 * \CharacterOption.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

