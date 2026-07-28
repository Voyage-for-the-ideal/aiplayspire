package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.LocalizedStrings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

public class FontHelper {
    private static final Logger logger = LogManager.getLogger(FontHelper.class.getName());
    private static FreeTypeFontGenerator.FreeTypeFontParameter param = new FreeTypeFontGenerator.FreeTypeFontParameter();
    private static FreeTypeFontGenerator.FreeTypeBitmapFontData data = new FreeTypeFontGenerator.FreeTypeBitmapFontData();
    private static HashMap<String, FreeTypeFontGenerator> generators = new HashMap<>();
    private static FileHandle fontFile = null;
    private static float fontScale = 1.0F;

    private static Vector2 rotatedTextTmp = new Vector2(0.0F, 0.0F);
    private static Matrix4 rotatedTextMatrix = new Matrix4();

    private static final String TINY_NUMBERS_FONT = "font/04b03.ttf";

    private static final String ENG_DEFAULT_FONT = "font/Kreon-Regular.ttf";

    private static final String ENG_BOLD_FONT = "font/Kreon-Bold.ttf";

    private static final String ENG_ITALIC_FONT = "font/ZillaSlab-RegularItalic.otf";

    private static final String ENG_DRAMATIC_FONT = "font/FeDPrm27C.otf";

    private static final String ZHS_DEFAULT_FONT = "font/zhs/NotoSansMonoCJKsc-Regular.otf";

    private static final String ZHS_BOLD_FONT = "font/zhs/SourceHanSerifSC-Bold.otf";

    private static final String ZHS_ITALIC_FONT = "font/zhs/SourceHanSerifSC-Medium.otf";

    private static final String ZHT_DEFAULT_FONT = "font/zht/NotoSansCJKtc-Regular.otf";

    private static final String ZHT_BOLD_FONT = "font/zht/NotoSansCJKtc-Bold.otf";

    private static final String ZHT_ITALIC_FONT = "font/zht/NotoSansCJKtc-Medium.otf";

    private static final String EPO_DEFAULT_FONT = "font/epo/Andada-Regular.otf";

    private static final String EPO_BOLD_FONT = "font/epo/Andada-Bold.otf";

    private static final String EPO_ITALIC_FONT = "font/epo/Andada-Italic.otf";

    private static final String GRE_DEFAULT_FONT = "font/gre/Roboto-Regular.ttf";

    private static final String GRE_BOLD_FONT = "font/gre/Roboto-Bold.ttf";

    private static final String GRE_ITALIC_FONT = "font/gre/Roboto-Italic.ttf";

    private static final String JPN_DEFAULT_FONT = "font/jpn/NotoSansCJKjp-Regular.otf";

    private static final String JPN_BOLD_FONT = "font/jpn/NotoSansCJKjp-Bold.otf";

    private static final String JPN_ITALIC_FONT = "font/jpn/NotoSansCJKjp-Medium.otf";

    private static final String KOR_DEFAULT_FONT = "font/kor/GyeonggiCheonnyeonBatangBold.ttf";
    private static final String KOR_BOLD_FONT = "font/kor/GyeonggiCheonnyeonBatangBold.ttf";
    private static final String KOR_ITALIC_FONT = "font/kor/GyeonggiCheonnyeonBatangBold.ttf";
    private static final String RUS_DEFAULT_FONT = "font/rus/FiraSansExtraCondensed-Regular.ttf";
    private static final String RUS_BOLD_FONT = "font/rus/FiraSansExtraCondensed-Bold.ttf";
    private static final String RUS_ITALIC_FONT = "font/rus/FiraSansExtraCondensed-Italic.ttf";
    private static final String SRB_DEFAULT_FONT = "font/srb/InfluBG.otf";
    private static final String SRB_BOLD_FONT = "font/srb/InfluBG-Bold.otf";
    private static final String SRB_ITALIC_FONT = "font/srb/InfluBG-Italic.otf";
    private static final String THA_DEFAULT_FONT = "font/tha/CSChatThaiUI.ttf";
    private static final String THA_BOLD_FONT = "font/tha/CSChatThaiUI.ttf";
    private static final String THA_ITALIC_FONT = "font/tha/CSChatThaiUI.ttf";
    private static final String VIE_DEFAULT_FONT = "font/vie/Grenze-Regular.ttf";
    private static final String VIE_BOLD_FONT = "font/vie/Grenze-SemiBold.ttf";
    private static final String VIE_DRAMATIC_FONT = "font/vie/Grenze-Black.ttf";
    private static final String VIE_ITALIC_FONT = "font/vie/Grenze-RegularItalic.ttf";
    public static BitmapFont charDescFont;
    public static BitmapFont charTitleFont;
    public static BitmapFont cardTitleFont;
    public static BitmapFont cardTypeFont;
    public static BitmapFont cardEnergyFont_L;
    public static BitmapFont cardDescFont_N;
    public static BitmapFont cardDescFont_L;
    public static BitmapFont SCP_cardDescFont;
    public static BitmapFont SCP_cardEnergyFont;
    public static BitmapFont SCP_cardTitleFont_small;
    public static BitmapFont SRV_quoteFont;
    public static BitmapFont losePowerFont;
    public static BitmapFont energyNumFontRed;
    public static BitmapFont energyNumFontGreen;
    public static BitmapFont energyNumFontBlue;
    public static BitmapFont energyNumFontPurple;
    public static BitmapFont turnNumFont;
    public static BitmapFont damageNumberFont;
    public static BitmapFont buttonLabelFont;
    public static BitmapFont dungeonTitleFont;
    public static BitmapFont bannerNameFont;
    private static final float CARD_ENERGY_IMG_WIDTH = 26.0F * Settings.scale;

    public static BitmapFont topPanelAmountFont;

    public static BitmapFont powerAmountFont;

    public static BitmapFont panelNameFont;

    public static BitmapFont healthInfoFont;

    public static BitmapFont blockInfoFont;

    public static BitmapFont topPanelInfoFont;

    public static BitmapFont tipHeaderFont;

    public static BitmapFont tipBodyFont;

    public static BitmapFont panelEndTurnFont;

    public static BitmapFont largeDialogOptionFont;

    public static BitmapFont smallDialogOptionFont;

    public static BitmapFont largeCardFont;

    public static BitmapFont menuBannerFont;

    public static BitmapFont leaderboardFont;

    public static GlyphLayout layout = new GlyphLayout();

    private static TextureAtlas.AtlasRegion orb;
    private static Color color;
    private static float curWidth;
    private static Matrix4 mx4 = new Matrix4();
    private static float curHeight;
    private static float spaceWidth;
    private static int currentLine;
    private static StringBuilder newMsg = new StringBuilder("");

    private static final int TIP_OFFSET_X = 50;

    private static final float TIP_PADDING = 8.0F;

   public static void initialize() {
     long startTime = System.currentTimeMillis();







     generators.clear();
     HashMap<Character, Integer> paramCreator = new HashMap<>();

     switch (Settings.language) {
       case ZHS:
         fontFile = Gdx.files.internal("font/zhs/NotoSansMonoCJKsc-Regular.otf");
         break;
       case ZHT:
         fontFile = Gdx.files.internal("font/zht/NotoSansCJKtc-Regular.otf");
         break;
       case EPO:
         fontFile = Gdx.files.internal("font/epo/Andada-Regular.otf");
         break;
       case GRE:
         fontFile = Gdx.files.internal("font/gre/Roboto-Regular.ttf");
         break;
       case JPN:
         fontFile = Gdx.files.internal("font/jpn/NotoSansCJKjp-Regular.otf");
         break;
       case KOR:
         fontFile = Gdx.files.internal("font/kor/GyeonggiCheonnyeonBatangBold.ttf");
         break;
       case POL:
       case RUS:
       case UKR:
         fontFile = Gdx.files.internal("font/rus/FiraSansExtraCondensed-Regular.ttf");
         break;
       case SRP:
       case SRB:
         fontFile = Gdx.files.internal("font/srb/InfluBG.otf");
         break;
       case THA:
         fontFile = Gdx.files.internal("font/tha/CSChatThaiUI.ttf");
         fontScale = 0.95F;
         break;
       case VIE:
         fontFile = Gdx.files.internal("font/vie/Grenze-Regular.ttf");
         break;
       default:
         fontFile = Gdx.files.internal("font/Kreon-Regular.ttf");
         break;
     }



     data.xChars = new char[] {};
     data.capChars = new char[] {};


     param.hinting = FreeTypeFontGenerator.Hinting.Slight;
     param.spaceX = 0;
     param.kerning = true;


     paramCreator.clear();
     param.borderWidth = 0.0F;
     param.gamma = 0.9F;
     param.borderGamma = 0.9F;
     param.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
     param.shadowOffsetX = (int)(4.0F * Settings.scale);
     charDescFont = Settings.isMobile ? prepFont(31.0F, false) : prepFont(30.0F, false);

     param.gamma = 1.8F;
     param.borderGamma = 1.8F;
     param.shadowOffsetX = (int)(6.0F * Settings.scale);
     charTitleFont = prepFont(44.0F, false);


     param.gamma = 0.9F;
     param.borderGamma = 0.9F;
     param.shadowOffsetX = Math.round(3.0F * Settings.scale);
     param.shadowOffsetY = Math.round(3.0F * Settings.scale);
     param.borderStraight = false;
     param.shadowColor = new Color(0.0F, 0.0F, 0.0F, 0.25F);
     param.borderColor = new Color(0.35F, 0.35F, 0.35F, 1.0F);
     param.borderWidth = 2.0F * Settings.scale;
     cardTitleFont = prepFont(27.0F, true);
     param.borderWidth = 2.25F * Settings.scale;


     param.borderWidth = 0.0F;
     param.shadowOffsetX = 1;
     param.shadowOffsetY = 1;
     param.spaceX = 0;
     cardDescFont_N = prepFont(24.0F, false);
     cardDescFont_L = prepFont(24.0F, true);


     param.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
     param.shadowOffsetX = Math.round(4.0F * Settings.scale);
     param.shadowOffsetY = Math.round(3.0F * Settings.scale);
     SCP_cardDescFont = prepFont(48.0F, true);
     param.shadowOffsetX = Math.round(6.0F * Settings.scale);
     param.shadowOffsetY = Math.round(6.0F * Settings.scale);
     param.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
     param.borderColor = new Color(0.35F, 0.35F, 0.35F, 1.0F);
     param.borderWidth = 4.0F * Settings.scale;
     SCP_cardTitleFont_small = prepFont(46.0F, true);


     param.borderWidth = 0.0F;
     param.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
     param.shadowOffsetX = Math.round(3.0F * Settings.scale);
     param.shadowOffsetY = Math.round(3.0F * Settings.scale);
     panelNameFont = prepFont(34.0F, true);


     param.shadowOffsetX = (int)(3.0F * Settings.scale);
     param.shadowOffsetY = (int)(3.0F * Settings.scale);
     param.borderColor = new Color(0.67F, 0.06F, 0.22F, 1.0F);
     param.gamma = 0.9F;
     param.borderGamma = 0.9F;
     param.borderColor = new Color(0.4F, 0.1F, 0.1F, 1.0F);
     param.borderWidth = 0.0F;
     tipBodyFont = prepFont(22.0F, false);


     param.borderColor = new Color(0.1F, 0.1F, 0.1F, 0.5F);
     param.borderWidth = 2.0F * Settings.scale;
     topPanelAmountFont = prepFont(24.0F, false);


     param.borderColor = Color.valueOf("42514dff");
     param.shadowOffsetX = (int)(4.0F * Settings.scale);
     param.shadowOffsetY = (int)(4.0F * Settings.scale);
     param.borderWidth = 3.0F * Settings.scale;
     panelEndTurnFont = prepFont(26.0F, false);


     param.spaceX = 0;
     param.borderWidth = 0.0F;
     param.shadowOffsetX = (int)(3.0F * Settings.scale);
     param.shadowOffsetY = (int)(3.0F * Settings.scale);
     largeDialogOptionFont = prepFont(30.0F, false);
     (largeDialogOptionFont.getData()).markupEnabled = false;
     smallDialogOptionFont = prepFont(26.0F, false);
     (smallDialogOptionFont.getData()).markupEnabled = false;


     param.shadowOffsetX = 0;
     param.shadowOffsetY = 0;
     turnNumFont = prepFont(32.0F, true);


     param.borderWidth = 4.0F * Settings.scale;
     param.borderColor = new Color(0.3F, 0.3F, 0.3F, 1.0F);
     param.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
     param.shadowOffsetX = Math.round(3.0F * Settings.scale);
     param.shadowOffsetY = Math.round(3.0F * Settings.scale);
     losePowerFont = prepFont(36.0F, true);


     param.borderWidth = 3.0F * Settings.scale;
     param.borderColor = Color.DARK_GRAY;
     damageNumberFont = prepFont(48.0F, true);
     damageNumberFont.getData().setLineHeight((damageNumberFont.getData()).lineHeight * 0.85F);
     param.borderWidth = 0.0F;


     param.borderWidth = 2.7F * Settings.scale;
     param.shadowOffsetX = (int)(3.0F * Settings.scale);
     param.shadowOffsetY = (int)(3.0F * Settings.scale);
     param.borderColor = new Color(0.45F, 0.1F, 0.12F, 1.0F);
     param.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
     healthInfoFont = Settings.isMobile ? prepFont(29.0F, false) : prepFont(22.0F, false);





     param.borderWidth = 4.0F * Settings.scale;
     param.spaceX = (int)(-2.5F * Settings.scale);
     param.borderColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
     buttonLabelFont = Settings.isMobile ? prepFont(37.0F, true) : prepFont(32.0F, true);

     param.spaceX = 0;


     fontScale = 1.0F;
     fontFile = Gdx.files.internal("font/Kreon-Bold.ttf");


     param.borderStraight = true;
     param.borderWidth = 4.0F * Settings.scale;
     param.borderColor = new Color(0.4F, 0.15F, 0.15F, 1.0F);
     energyNumFontRed = prepFont(36.0F, true);
     param.borderColor = new Color(0.15F, 0.4F, 0.15F, 1.0F);
     energyNumFontGreen = prepFont(36.0F, true);
     param.borderColor = new Color(0.1F, 0.2F, 0.4F, 1.0F);
     energyNumFontBlue = prepFont(36.0F, true);
     param.borderColor = new Color(1595767551);
     energyNumFontPurple = prepFont(36.0F, true);
     param.borderWidth = 4.0F * Settings.scale;
     param.borderColor = new Color(0.3F, 0.3F, 0.3F, 1.0F);
     cardEnergyFont_L = prepFont(38.0F, true);
     param.borderWidth = 8.0F * Settings.scale;
     SCP_cardEnergyFont = prepFont(76.0F, true);


     param.shadowOffsetX = (int)(2.0F * Settings.scale);
     param.shadowOffsetY = (int)(2.0F * Settings.scale);
     param.borderColor = new Color(0.0F, 0.33F, 0.2F, 0.8F);
     param.borderWidth = 2.7F * Settings.scale;
     param.spaceX = (int)(-0.9F * Settings.scale);
     blockInfoFont = Settings.isMobile ? prepFont(30.0F, false) : prepFont(24.0F, false);


     switch (Settings.language) {
       case ZHS:
         fontFile = Gdx.files.internal("font/zhs/SourceHanSerifSC-Bold.otf");
         break;
       case ZHT:
         fontFile = Gdx.files.internal("font/zht/NotoSansCJKtc-Bold.otf");
         break;
       case EPO:
         fontFile = Gdx.files.internal("font/epo/Andada-Bold.otf");
         break;
       case GRE:
         fontFile = Gdx.files.internal("font/gre/Roboto-Bold.ttf");
         break;
       case JPN:
         fontFile = Gdx.files.internal("font/jpn/NotoSansCJKjp-Bold.otf");
         break;
       case KOR:
         fontFile = Gdx.files.internal("font/kor/GyeonggiCheonnyeonBatangBold.ttf");
         break;
       case POL:
       case RUS:
       case UKR:
         fontFile = Gdx.files.internal("font/rus/FiraSansExtraCondensed-Bold.ttf");
         break;
       case SRP:
       case SRB:
         fontFile = Gdx.files.internal("font/srb/InfluBG-Bold.otf");
         break;
       case THA:
         fontScale = 0.95F;
         fontFile = Gdx.files.internal("font/tha/CSChatThaiUI.ttf");
         break;
       case VIE:
         fontFile = Gdx.files.internal("font/vie/Grenze-SemiBold.ttf");
         break;
       default:
         fontFile = Gdx.files.internal("font/Kreon-Bold.ttf");
         break;
     }


     param.gamma = 1.2F;
     param.borderWidth = 0.0F;
     param.shadowOffsetX = 0;
     param.shadowOffsetY = 0;
     if (Settings.WIDTH >= 1600) {
       param.spaceX = -1;
     }

     cardTypeFont = prepFont(17.0F, true);


     param.gamma = 1.2F;
     param.borderGamma = 1.2F;
     param.borderWidth = 0.0F;
     param.shadowColor = new Color(0.0F, 0.0F, 0.0F, 0.12F);
     param.shadowOffsetX = (int)(5.0F * Settings.scale);
     param.shadowOffsetY = (int)(4.0F * Settings.scale);
     menuBannerFont = prepFont(38.0F, true);
     param.characters = "?";
     param.shadowOffsetX = (int)(15.0F * Settings.scale);
     param.shadowOffsetY = (int)(12.0F * Settings.scale);
     largeCardFont = prepFont(120.0F, true);


     param.shadowOffsetX = 2;
     param.shadowOffsetY = 2;
     param.shadowColor = new Color(0.0F, 0.0F, 0.0F, 0.33F);
     param.gamma = 2.0F;
     param.borderGamma = 2.0F;
     param.borderStraight = true;
     param.borderColor = Color.DARK_GRAY;
     param.borderWidth = 2.0F * Settings.scale;

     param.shadowOffsetX = 1;
     param.shadowOffsetY = 1;
     tipHeaderFont = prepFont(23.0F, false);
     param.shadowOffsetX = 2;
     param.shadowOffsetY = 2;
     topPanelInfoFont = prepFont(26.0F, false);
     param.spaceX = 0;
     param.gamma = 0.9F;
     param.borderGamma = 0.9F;
     param.borderWidth = 0.0F;


     fontScale = 1.0F;
     fontFile = Gdx.files.internal("font/04b03.ttf");
     param.borderWidth = 2.0F * Settings.scale;
     powerAmountFont = Settings.isMobile ? prepFont(20.0F, false) : prepFont(16.0F, false);


     switch (Settings.language) {
       case ZHS:
         fontFile = Gdx.files.internal("font/zhs/SourceHanSerifSC-Bold.otf");
         break;
       case ZHT:
         fontFile = Gdx.files.internal("font/zht/NotoSansCJKtc-Bold.otf");
         break;
       case EPO:
         fontFile = Gdx.files.internal("font/epo/Andada-Bold.otf");
         break;
       case GRE:
         fontFile = Gdx.files.internal("font/gre/Roboto-Bold.ttf");
         break;
       case JPN:
         fontFile = Gdx.files.internal("font/jpn/NotoSansCJKjp-Bold.otf");
         break;
       case KOR:
         fontFile = Gdx.files.internal("font/kor/GyeonggiCheonnyeonBatangBold.ttf");
         break;
       case POL:
       case RUS:
       case UKR:
         fontFile = Gdx.files.internal("font/rus/FiraSansExtraCondensed-Bold.ttf");
         break;
       case SRP:
       case SRB:
         fontFile = Gdx.files.internal("font/srb/InfluBG-Bold.otf");
         break;
       case THA:
         fontScale = 0.95F;
         fontFile = Gdx.files.internal("font/tha/CSChatThaiUI.ttf");
         break;
       case VIE:
         fontFile = Gdx.files.internal("font/vie/Grenze-Black.ttf");
         break;
       default:
         fontFile = Gdx.files.internal("font/FeDPrm27C.otf");
         break;
     }

     param.gamma = 0.5F;
     param.borderGamma = 0.5F;
     param.shadowOffsetX = 0;
     param.shadowOffsetY = 0;
     param.borderWidth = 6.0F * Settings.scale;
     param.borderColor = new Color(0.0F, 0.0F, 0.0F, 0.5F);
     param.spaceX = (int)(-5.0F * Settings.scale);
     dungeonTitleFont = prepFont(115.0F, true);
     dungeonTitleFont.getData().setScale(1.25F);
     param.borderWidth = 4.0F * Settings.scale;
     param.borderColor = new Color(0.0F, 0.0F, 0.0F, 0.33F);
     param.spaceX = (int)(-2.0F * Settings.scale);
     bannerNameFont = prepFont(72.0F, true);


     fontScale = 1.0F;
     switch (Settings.language) {
       case ZHS:
         fontFile = Gdx.files.internal("font/zhs/SourceHanSerifSC-Medium.otf");
         break;
       case ZHT:
         fontFile = Gdx.files.internal("font/zht/NotoSansCJKtc-Medium.otf");
         break;
       case EPO:
         fontFile = Gdx.files.internal("font/epo/Andada-Italic.otf");
         break;
       case GRE:
         fontFile = Gdx.files.internal("font/gre/Roboto-Italic.ttf");
         break;
       case JPN:
         fontFile = Gdx.files.internal("font/jpn/NotoSansCJKjp-Medium.otf");
         break;
       case KOR:
         fontFile = Gdx.files.internal("font/kor/GyeonggiCheonnyeonBatangBold.ttf");
         break;
       case POL:
       case RUS:
       case UKR:
         fontFile = Gdx.files.internal("font/rus/FiraSansExtraCondensed-Italic.ttf");
         break;
       case SRP:
       case SRB:
         fontFile = Gdx.files.internal("font/srb/InfluBG-Italic.otf");
         break;
       case THA:
         fontScale = 0.95F;
         fontFile = Gdx.files.internal("font/tha/CSChatThaiUI.ttf");
         break;
       case VIE:
         fontFile = Gdx.files.internal("font/vie/Grenze-RegularItalic.ttf");
         break;
       default:
         fontFile = Gdx.files.internal("font/ZillaSlab-RegularItalic.otf");
         break;
     }


     param.shadowOffsetX = 0;
     param.shadowOffsetY = 0;
     param.borderWidth = 0.0F;
     param.shadowOffsetX = Math.round(2.0F * Settings.scale);
     param.shadowOffsetY = Math.round(2.0F * Settings.scale);
     param.spaceX = 0;
     SRV_quoteFont = prepFont(28.0F, false);


     fontScale = 1.0F;
     fontFile = Gdx.files.internal("font/zhs/NotoSansMonoCJKsc-Regular.otf");
     leaderboardFont = prepFont(30.0F, false);

     logger.info("Font load time: " + (System.currentTimeMillis() - startTime) + "ms");
   }

    public static void ClearSCPFontTextures() {
        System.out.println("Clearing SCP font textures...");
        SCP_cardDescFont.dispose();
        SCP_cardEnergyFont.dispose();
        SCP_cardTitleFont_small.dispose();

        fontScale = (Settings.language == Settings.GameLanguage.THA) ? 0.95F : 1.0F;
        fontFile = SCP_cardDescFont.getData().getFontFile();
        param.borderWidth = 0.0F;
        param.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
        param.shadowOffsetX = Math.round(4.0F * Settings.scale);
        param.shadowOffsetY = Math.round(3.0F * Settings.scale);
        SCP_cardDescFont = prepFont(48.0F, true);

        fontScale = 1.0F;
        param.shadowOffsetX = Math.round(6.0F * Settings.scale);
        param.shadowOffsetY = Math.round(6.0F * Settings.scale);
        param.borderColor = new Color(0.35F, 0.35F, 0.35F, 1.0F);
        param.borderWidth = 4.0F * Settings.scale;
        SCP_cardTitleFont_small = prepFont(46.0F, true);

        param.borderStraight = true;
        param.borderColor = new Color(0.3F, 0.3F, 0.3F, 1.0F);
        param.borderWidth = 8.0F * Settings.scale;
        SCP_cardEnergyFont = prepFont(76.0F, true);
    }

    public static void ClearSRVFontTextures() {
        System.out.println("Clearing SRV font textures...");
        SRV_quoteFont.dispose();
        SCP_cardDescFont.dispose();

        fontScale = (Settings.language == Settings.GameLanguage.THA) ? 0.95F : 1.0F;
        fontFile = SCP_cardDescFont.getData().getFontFile();
        param.borderWidth = 0.0F;
        param.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
        param.shadowOffsetX = Math.round(4.0F * Settings.scale);
        param.shadowOffsetY = Math.round(3.0F * Settings.scale);
        SCP_cardDescFont = prepFont(48.0F, true);

        fontScale = 1.0F;
        fontFile = SRV_quoteFont.getData().getFontFile();
        param.shadowColor = new Color(0.0F, 0.0F, 0.0F, 0.33F);
        param.shadowOffsetX = Math.round(2.0F * Settings.scale);
        param.shadowOffsetY = Math.round(2.0F * Settings.scale);
        param.spaceX = 0;
        SRV_quoteFont = prepFont(28.0F, false);
    }

    public static void ClearLeaderboardFontTextures() {
        System.out.println("Clearing leaderboard font textures...");
        leaderboardFont.dispose();
        fontScale = 1.0F;
        param.shadowOffsetX = 0;
        param.shadowOffsetY = 0;
        param.borderWidth = 0.0F;
        param.spaceX = 0;
        fontFile = leaderboardFont.getData().getFontFile();
        leaderboardFont = prepFont(30.0F, false);
    }

    public static BitmapFont prepFont(float size, boolean isLinearFiltering) {
        FreeTypeFontGenerator g;
        if (generators.containsKey(fontFile.path())) {

            g = generators.get(fontFile.path());
        } else {

            g = new FreeTypeFontGenerator(fontFile);
            generators.put(fontFile.path(), g);
        }

        if (Settings.BIG_TEXT_MODE) {
            size *= 1.2F;
        }

        return prepFont(g, size, isLinearFiltering);
    }

    private static BitmapFont prepFont(FreeTypeFontGenerator g, float size, boolean isLinearFiltering) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();

        p.characters = "";
        p.incremental = true;
        p.size = Math.round(size * fontScale * Settings.scale);
        p.gamma = param.gamma;
        p.spaceX = param.spaceX;
        p.spaceY = param.spaceY;

        p.borderColor = param.borderColor;
        p.borderStraight = param.borderStraight;
        p.borderWidth = param.borderWidth;
        p.borderGamma = param.borderGamma;

        p.shadowColor = param.shadowColor;
        p.shadowOffsetX = param.shadowOffsetX;
        p.shadowOffsetY = param.shadowOffsetY;

        if (isLinearFiltering) {
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
        } else {
            p.minFilter = Texture.TextureFilter.Nearest;
            p.magFilter = Texture.TextureFilter.MipMapLinearNearest;
        }
        g.scaleForPixelHeight(p.size);

        BitmapFont font = g.generateFont(p);
        font.setUseIntegerPositions(!isLinearFiltering);
        (font.getData()).markupEnabled = true;

        if (LocalizedStrings.break_chars != null) {
            (font.getData()).breakChars = LocalizedStrings.break_chars.toCharArray();
        }

        (font.getData()).fontFile = fontFile;
        return font;
    }

    public static void renderTipLeft(SpriteBatch sb, String msg) {
        layout.setText(cardDescFont_N, msg);
        sb.setColor(Color.BLACK);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, InputHelper.mX - layout.width - 16.0F - 12.5F,
                InputHelper.mY - layout.height, layout.width + 16.0F, layout.height + 16.0F);

        renderFont(sb, cardDescFont_N, msg, InputHelper.mX - layout.width - 8.0F - 12.0F, InputHelper.mY + 8.0F,
                Color.WHITE);
    }

    public static void renderFont(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c) {
        font.setColor(c);
        font.draw((Batch) sb, msg, x, y);
    }

    public static void renderRotatedText(SpriteBatch sb, BitmapFont font, String msg, float x, float y, float offsetX,
            float offsetY, float angle, boolean roundY, Color c) {
        if (roundY) {
            y = Math.round(y) + 0.25F;
        }

        if ((font.getData()).scaleX == 1.0F) {
            x = MathUtils.round(x);
            y = MathUtils.round(y);
            offsetX = MathUtils.round(offsetX);
            offsetY = MathUtils.round(offsetY);
        }

        mx4.setToRotation(0.0F, 0.0F, 1.0F, angle);
        rotatedTextTmp.x = offsetX;
        rotatedTextTmp.y = offsetY;
        rotatedTextTmp.rotate(angle);
        mx4.trn(x + rotatedTextTmp.x, y + rotatedTextTmp.y, 0.0F);
        sb.end();
        sb.setTransformMatrix(mx4);
        sb.begin();

        font.setColor(c);
        layout.setText(font, msg);
        font.draw((Batch) sb, msg, -layout.width / 2.0F, layout.height / 2.0F);

        sb.end();
        sb.setTransformMatrix(rotatedTextMatrix);
        sb.begin();
    }

    public static void renderWrappedText(SpriteBatch sb, BitmapFont font, String msg, float x, float y, float width) {
        renderWrappedText(sb, font, msg, x, y, width, Color.WHITE, 1.0F);
    }

    public static void renderWrappedText(SpriteBatch sb, BitmapFont font, String msg, float x, float y, float width,
            float scale) {
        renderWrappedText(sb, font, msg, x, y, width, Color.WHITE, scale);
    }

    public static void renderWrappedText(SpriteBatch sb, BitmapFont font, String msg, float x, float y, float width,
            Color c, float scale) {
        font.getData().setScale(scale);
        font.setColor(c);
        layout.setText(font, msg, Color.WHITE, width, 1, true);
        font.draw((Batch) sb, msg, x - width / 2.0F, y + layout.height / 2.0F * scale, width, 1, true);
        font.getData().setScale(1.0F);
    }

    public static void renderFontLeftDownAligned(SpriteBatch sb, BitmapFont font, String msg, float x, float y,
            Color c) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x, y + layout.height, c);
    }

    public static void renderFontRightToLeft(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c) {
        layout.setText(font, msg, c, 1.0F, 18, false);
        font.setColor(c);
        font.draw((Batch) sb, msg, x - layout.width, y);
    }

    public static void renderFontRightTopAligned(SpriteBatch sb, BitmapFont font, String msg, float x, float y,
            Color c) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x - layout.width, y, c);
    }

    public static void renderFontRightAligned(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x - layout.width, y + layout.height / 2.0F, c);
    }

    public static void renderFontRightTopAligned(SpriteBatch sb, BitmapFont font, String msg, float x, float y,
            float scale, Color c) {
        font.getData().setScale(1.0F);
        layout.setText(font, msg);
        float offsetX = layout.width / 2.0F;
        float offsetY = layout.height;
        font.getData().setScale(scale);
        layout.setText(font, msg);
        renderFont(sb, font, msg, x - layout.width / 2.0F - offsetX, y + layout.height / 2.0F + offsetY, c);
    }

    public static void renderSmartText(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color baseColor) {
        renderSmartText(sb, font, msg, x, y, Float.MAX_VALUE, font.getLineHeight(), baseColor);
    }

    public static void renderSmartText(SpriteBatch sb, BitmapFont font, String msg, float x, float y, float lineWidth,
            float lineSpacing, Color baseColor) {
        if (msg == null) {
            return;
        }

        if (Settings.lineBreakViaCharacter && (font.getData()).markupEnabled) {
            exampleNonWordWrappedText(sb, font, msg, x, y, baseColor, lineWidth, lineSpacing);

            return;
        }
        curWidth = 0.0F;
        curHeight = 0.0F;
        layout.setText(font, " ");
        spaceWidth = layout.width;

        for (String word : msg.split(" ")) {
            if (word.equals("NL")) {
                curWidth = 0.0F;
                curHeight -= lineSpacing;
            } else if (word.equals("TAB")) {
                curWidth += spaceWidth * 5.0F;

            } else {

                orb = identifyOrb(word);
                if (orb == null) {

                    color = identifyColor(word).cpy();
                    if (!color.equals(Color.WHITE)) {
                        word = word.substring(2, word.length());
                        color.a = baseColor.a;
                        font.setColor(color);
                    } else {
                        font.setColor(baseColor);
                    }

                    layout.setText(font, word);

                    if (curWidth + layout.width > lineWidth) {
                        curHeight -= lineSpacing;
                        font.draw((Batch) sb, word, x, y + curHeight);
                        curWidth = layout.width + spaceWidth;
                    } else {
                        font.draw((Batch) sb, word, x + curWidth, y + curHeight);
                        curWidth += layout.width + spaceWidth;
                    }
                } else {
                    sb.setColor(new Color(1.0F, 1.0F, 1.0F, baseColor.a));

                    if (curWidth + CARD_ENERGY_IMG_WIDTH > lineWidth) {
                        curHeight -= lineSpacing;
                        sb.draw((TextureRegion) orb, x - orb.packedWidth / 2.0F + 13.0F * Settings.scale,
                                y + curHeight - orb.packedHeight / 2.0F - 8.0F * Settings.scale, orb.packedWidth / 2.0F,
                                orb.packedHeight / 2.0F, orb.packedWidth, orb.packedHeight, Settings.scale,
                                Settings.scale, 0.0F);

                        curWidth = CARD_ENERGY_IMG_WIDTH + spaceWidth;
                    } else {
                        sb.draw((TextureRegion) orb, x + curWidth - orb.packedWidth / 2.0F + 13.0F * Settings.scale,
                                y + curHeight - orb.packedHeight / 2.0F - 8.0F * Settings.scale, orb.packedWidth / 2.0F,
                                orb.packedHeight / 2.0F, orb.packedWidth, orb.packedHeight, Settings.scale,
                                Settings.scale, 0.0F);

                        curWidth += CARD_ENERGY_IMG_WIDTH + spaceWidth;
                    }
                }
            }
        }
        layout.setText(font, msg);
    }

    public static void renderSmartText(SpriteBatch sb, BitmapFont font, String msg, float x, float y, float lineWidth,
            float lineSpacing, Color baseColor, float scale) {
        BitmapFont.BitmapFontData data = font.getData();
        float prevScale = data.scaleX;
        data.setScale(scale);
        renderSmartText(sb, font, msg, x, y, lineWidth, lineSpacing, baseColor);
        data.setScale(prevScale);
    }

    public static float getSmartHeight(BitmapFont font, String msg, float lineWidth, float lineSpacing) {
        if (msg == null) {
            return 0.0F;
        }

        if (Settings.lineBreakViaCharacter) {
            return -getHeightForCharLineBreak(font, msg, lineWidth, lineSpacing);
        }

        curWidth = 0.0F;
        curHeight = 0.0F;
        layout.setText(font, " ");
        spaceWidth = layout.width;

        for (String word : msg.split(" ")) {
            if (word.equals("NL")) {
                curWidth = 0.0F;
                curHeight -= lineSpacing;
            } else if (word.equals("TAB")) {
                curWidth += spaceWidth * 5.0F;

            } else {

                orb = identifyOrb(word);
                if (orb == null) {
                    if (!identifyColor(word).equals(Color.WHITE)) {
                        word = word.substring(2, word.length());
                    }

                    layout.setText(font, word);

                    if (curWidth + layout.width > lineWidth) {
                        curHeight -= lineSpacing;
                        curWidth = layout.width + spaceWidth;
                    } else {
                        curWidth += layout.width + spaceWidth;
                    }

                } else if (curWidth + CARD_ENERGY_IMG_WIDTH > lineWidth) {
                    curHeight -= lineSpacing;
                    curWidth = CARD_ENERGY_IMG_WIDTH + spaceWidth;
                } else {
                    curWidth += CARD_ENERGY_IMG_WIDTH + spaceWidth;
                }
            }
        }

        return curHeight;
    }

    private static float getHeightForCharLineBreak(BitmapFont font, String msg, float lineWidth, float lineSpacing) {
        newMsg.setLength(0);
        for (String word : msg.split(" ")) {
            if (word.equals("NL")) {
                newMsg.append("\n");
            } else if (word.length() > 0 && word.charAt(0) == '#') {
                newMsg.append(word.substring(2));
            } else {
                newMsg.append(word);
            }
        }

        msg = newMsg.toString();

        if (msg != null && msg.length() > 0) {
            layout.setText(font, msg, Color.WHITE, lineWidth, -1, true);
        }
        return layout.height - 16.0F * Settings.scale;
    }

    public static float getHeight(BitmapFont font) {
        layout.setText(font, "gl0!");
        return layout.height;
    }

    public static float getSmartWidth(BitmapFont font, String msg, float lineWidth, float lineSpacing) {
        curWidth = 0.0F;
        layout.setText(font, " ");
        spaceWidth = layout.width;

        for (String word : msg.split(" ")) {
            if (word.equals("NL")) {
                curWidth = 0.0F;
            } else if (word.equals("TAB")) {
                curWidth += spaceWidth * 5.0F;

            } else {

                orb = identifyOrb(word);
                if (orb == null) {
                    if (!identifyColor(word).equals(Color.WHITE)) {
                        word = word.substring(2, word.length());
                    }

                    layout.setText(font, word);

                    if (curWidth + layout.width > lineWidth) {
                        curWidth = layout.width + spaceWidth;
                    } else {
                        curWidth += layout.width + spaceWidth;
                    }

                } else if (curWidth + CARD_ENERGY_IMG_WIDTH > lineWidth) {
                    curWidth = CARD_ENERGY_IMG_WIDTH + spaceWidth;
                } else {
                    curWidth += CARD_ENERGY_IMG_WIDTH + spaceWidth;
                }
            }
        }

        return curWidth;
    }

    public static float getSmartWidth(BitmapFont font, String msg, float lineWidth, float lineSpacing, float scale) {
        BitmapFont.BitmapFontData data = font.getData();
        float prevScale = data.scaleX;
        data.setScale(scale);
        float retVal = getSmartWidth(font, msg, lineWidth, lineSpacing);
        data.setScale(prevScale);
        return retVal;
    }

    private static TextureAtlas.AtlasRegion identifyOrb(String word) {
        switch (word) {
            case "[E]":
                return (AbstractDungeon.player != null) ? AbstractDungeon.player.getOrb() : AbstractCard.orb_red;
            case "[R]":
                return AbstractCard.orb_red;
            case "[G]":
                return AbstractCard.orb_green;
            case "[B]":
                return AbstractCard.orb_blue;
            case "[W]":
                return AbstractCard.orb_purple;
            case "[C]":
                return AbstractCard.orb_card;
            case "[P]":
                return AbstractCard.orb_potion;
            case "[T]":
                return AbstractCard.orb_relic;
            case "[S]":
                return AbstractCard.orb_special;
        }
        return null;
    }

    private static Color identifyColor(String word) {
        if (word.length() > 0 && word.charAt(0) == '#') {
            switch (word.charAt(1)) {
                case 'r':
                    return Settings.RED_TEXT_COLOR;
                case 'g':
                    return Settings.GREEN_TEXT_COLOR;
                case 'b':
                    return Settings.BLUE_TEXT_COLOR;
                case 'y':
                    return Settings.GOLD_COLOR;
                case 'p':
                    return Settings.PURPLE_COLOR;
            }
            return Color.WHITE;
        }

        return Color.WHITE;
    }

    public static void renderDeckViewTip(SpriteBatch sb, String msg, float y, Color color) {
        layout.setText(cardDescFont_N, msg);
        sb.setColor(Settings.TWO_THIRDS_TRANSPARENT_BLACK_COLOR);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, Settings.WIDTH / 2.0F - layout.width / 2.0F - 12.0F * Settings.scale,
                y - 24.0F * Settings.scale, layout.width + 24.0F * Settings.scale, 48.0F * Settings.scale);

        renderFontCentered(sb, cardDescFont_N, msg, Settings.WIDTH / 2.0F, y, color);
    }

    public static void renderFontLeftTopAligned(SpriteBatch sb, BitmapFont font, String msg, float x, float y,
            Color c) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x, y, c);
    }

    public static void renderFontCentered(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x - layout.width / 2.0F, y + layout.height / 2.0F, c);
    }

    public static void renderFontLeft(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x, y + layout.height / 2.0F, c);
    }

    public static void exampleNonWordWrappedText(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c,
            float widthMax, float lineSpacing) {
        layout.setText(font, msg, Color.WHITE, 0.0F, -1, false);
        currentLine = 0;
        curWidth = 0.0F;

        for (String word : msg.split(" ")) {
            if (word.length() != 0) {

                if (word.equals("NL")) {
                    curWidth = 0.0F;
                    currentLine++;

                } else if (word.equals("TAB")) {
                    layout.setText(font, word);
                    curWidth += layout.width;

                } else if (word.charAt(0) == '[') {
                    orb = identifyOrb(word);

                    if (orb != null) {
                        sb.setColor(new Color(1.0F, 1.0F, 1.0F, c.a));
                        if (CARD_ENERGY_IMG_WIDTH <= widthMax * 2.0F) {
                            if (curWidth + CARD_ENERGY_IMG_WIDTH > widthMax) {
                                sb.draw((TextureRegion) orb, x - orb.packedWidth / 2.0F + 14.0F * Settings.scale,
                                        y - currentLine * lineSpacing - orb.packedHeight / 2.0F
                                                - 38.0F * Settings.scale,
                                        orb.packedWidth / 2.0F, orb.packedHeight / 2.0F, orb.packedWidth,
                                        orb.packedHeight, Settings.scale, Settings.scale, 0.0F);

                            } else {

                                sb.draw((TextureRegion) orb,
                                        x + curWidth - orb.packedWidth / 2.0F + 14.0F * Settings.scale,
                                        y - currentLine * lineSpacing - orb.packedHeight / 2.0F - 8.0F * Settings.scale,
                                        orb.packedWidth / 2.0F, orb.packedHeight / 2.0F, orb.packedWidth,
                                        orb.packedHeight, Settings.scale, Settings.scale, 0.0F);
                            }
                        }

                        curWidth += CARD_ENERGY_IMG_WIDTH;
                        if (curWidth > widthMax) {
                            curWidth = CARD_ENERGY_IMG_WIDTH;
                            currentLine++;
                        }

                    }

                } else if (word.charAt(0) == '#') {
                    layout.setText(font, word.substring(2));
                    switch (word.charAt(1)) {
                        case 'r':
                            word = "[#ff6563]" + word.substring(2) + "[]";
                            break;
                        case 'g':
                            word = "[#7fff00]" + word.substring(2) + "[]";
                            break;
                        case 'b':
                            word = "[#87ceeb]" + word.substring(2) + "[]";
                            break;
                        case 'y':
                            word = "[#efc851]" + word.substring(2) + "[]";
                            break;
                        case 'p':
                            word = "[#0e82ee]" + word.substring(2) + "[]";
                            break;
                    }

                    curWidth += layout.width;
                    if (curWidth > widthMax) {
                        curWidth = 0.0F;
                        currentLine++;
                        font.draw((Batch) sb, word, x + curWidth, y - lineSpacing * currentLine);
                        curWidth = layout.width;
                    } else {
                        font.draw((Batch) sb, word, x + curWidth - layout.width, y - lineSpacing * currentLine);
                    }

                } else {

                    font.setColor(c);
                    for (int i = 0; i < word.length(); i++) {
                        String j = Character.toString(word.charAt(i));
                        layout.setText(font, j);
                        curWidth += layout.width;
                        if (curWidth > widthMax && !j.equals(LocalizedStrings.PERIOD)) {
                            curWidth = layout.width;
                            currentLine++;
                        }
                        font.draw((Batch) sb, j, x + curWidth - layout.width, y - lineSpacing * currentLine);
                    }
                }
            }
        }
    }

    public static void renderFontCenteredTopAligned(SpriteBatch sb, BitmapFont font, String msg, float x, float y,
            Color c) {
        layout.setText(font, "lL");
        font.setColor(c);
        font.draw((Batch) sb, msg, x, y + layout.height / 2.0F, 0.0F, 1, false);
    }

    public static void renderFontCentered(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c,
            float scale) {
        font.getData().setScale(scale);
        layout.setText(font, msg);
        renderFont(sb, font, msg, x - layout.width / 2.0F, y + layout.height / 2.0F, c);
        font.getData().setScale(1.0F);
    }

    public static void renderFontCentered(SpriteBatch sb, BitmapFont font, String msg, float x, float y) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x - layout.width / 2.0F, y + layout.height / 2.0F, Color.WHITE);
    }

    public static void renderFontCenteredWidth(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x - layout.width / 2.0F, y, c);
    }

    public static void renderFontCenteredWidth(SpriteBatch sb, BitmapFont font, String msg, float x, float y) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x - layout.width / 2.0F, y, Color.WHITE);
    }

    public static void renderFontCenteredHeight(SpriteBatch sb, BitmapFont font, String msg, float x, float y,
            float lineWidth, Color c) {
        layout.setText(font, msg, c, lineWidth, 1, true);
        font.setColor(c);
        font.draw((Batch) sb, msg, x, y + layout.height / 2.0F, lineWidth, 1, true);
    }

    public static void renderFontCenteredHeight(SpriteBatch sb, BitmapFont font, String msg, float x, float y,
            float lineWidth, Color c, float scale) {
        font.getData().setScale(scale);
        layout.setText(font, msg, c, lineWidth, 1, true);
        font.setColor(c);
        font.draw((Batch) sb, msg, x, y + layout.height / 2.0F, lineWidth, 1, true);
        font.getData().setScale(1.0F);
    }

    public static void renderFontCenteredHeight(SpriteBatch sb, BitmapFont font, String msg, float x, float y,
            Color c) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x, y + layout.height / 2.0F, c);
    }

    public static void renderFontCenteredHeight(SpriteBatch sb, BitmapFont font, String msg, float x, float y) {
        layout.setText(font, msg);
        renderFont(sb, font, msg, x, y + layout.height / 2.0F, Color.WHITE);
    }

    public static String colorString(String input, String colorValue) {
        newMsg.setLength(0);
        for (String word : input.split(" ")) {
            newMsg.append("#").append(colorValue).append(word).append(' ');
        }
        return newMsg.toString().trim();
    }

    public static float getWidth(BitmapFont font, String text, float scale) {
        layout.setText(font, text);
        return layout.width * scale;
    }

    public static float getHeight(BitmapFont font, String text, float scale) {
        layout.setText(font, text);
        return layout.height * scale;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\FontHelper
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

