package com.megacrit.cardcrawl.characters;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.green.Neutralize;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.EnergyManager;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.events.beyond.SpireHeart;
import com.megacrit.cardcrawl.events.city.Vampires;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.localization.CharacterStrings;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.screens.CharSelectInfo;
import com.megacrit.cardcrawl.screens.stats.CharStat;
import com.megacrit.cardcrawl.screens.stats.StatsScreen;
import com.megacrit.cardcrawl.ui.panels.energyorb.EnergyOrbGreen;
import com.megacrit.cardcrawl.ui.panels.energyorb.EnergyOrbInterface;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class TheSilent
        extends AbstractPlayer {
    private static final Logger logger = LogManager.getLogger(TheSilent.class.getName());
    private static final CharacterStrings characterStrings = CardCrawlGame.languagePack.getCharacterString("Silent");
    public static final String[] NAMES = characterStrings.NAMES;
    public static final String[] TEXT = characterStrings.TEXT;
    private EnergyOrbInterface energyOrb = (EnergyOrbInterface) new EnergyOrbGreen();
    private Prefs prefs;
    private CharStat charStat;

    TheSilent(String playerName) {
        super(playerName, PlayerClass.THE_SILENT);
        this.charStat = new CharStat(this);

        this.dialogX = this.drawX + 0.0F * Settings.scale;
        this.dialogY = this.drawY + 170.0F * Settings.scale;

        initializeClass((String) null, "images/characters/theSilent/shoulder2.png",
                "images/characters/theSilent/shoulder.png", "images/characters/theSilent/corpse.png",

                getLoadout(), -20.0F, -24.0F, 240.0F, 240.0F, new EnergyManager(3));

        loadAnimation("images/characters/theSilent/idle/skeleton.atlas",
                "images/characters/theSilent/idle/skeleton.json", 1.0F);

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        this.stateData.setMix("Hit", "Idle", 0.1F);
        e.setTimeScale(0.9F);

        if (ModHelper.enabledMods.size() > 0
                && (ModHelper.isModEnabled("Diverse") || ModHelper.isModEnabled("Chimera") ||
                        ModHelper.isModEnabled("Blue Cards"))) {
            this.masterMaxOrbs = 1;
        }
    }

    public String getPortraitImageName() {
        return "silentPortrait.jpg";
    }

    public ArrayList<String> getStartingDeck() {
        ArrayList<String> retVal = new ArrayList<>();
        retVal.add("Strike_G");
        retVal.add("Strike_G");
        retVal.add("Strike_G");
        retVal.add("Strike_G");
        retVal.add("Strike_G");
        retVal.add("Defend_G");
        retVal.add("Defend_G");
        retVal.add("Defend_G");
        retVal.add("Defend_G");
        retVal.add("Defend_G");
        retVal.add("Survivor");
        retVal.add("Neutralize");
        return retVal;
    }

    public AbstractCard getStartCardForEvent() {
        return (AbstractCard) new Neutralize();
    }

    public ArrayList<String> getStartingRelics() {
        ArrayList<String> retVal = new ArrayList<>();
        retVal.add("Ring of the Snake");
        UnlockTracker.markRelicAsSeen("Ring of the Snake");
        return retVal;
    }

    public CharSelectInfo getLoadout() {
        return new CharSelectInfo(NAMES[0], TEXT[0], 70, 70, 0, 99, 5, this,

                getStartingRelics(),
                getStartingDeck(), false);
    }

    public String getTitle(PlayerClass plyrClass) {
        return AbstractPlayer.uiStrings.TEXT[2];
    }

    public AbstractCard.CardColor getCardColor() {
        return AbstractCard.CardColor.GREEN;
    }

    public Color getCardRenderColor() {
        return Color.CHARTREUSE;
    }

    public String getAchievementKey() {
        return "EMERALD";
    }

    public ArrayList<AbstractCard> getCardPool(ArrayList<AbstractCard> tmpPool) {
        CardLibrary.addGreenCards(tmpPool);
        if (ModHelper.isModEnabled("Red Cards")) {
            CardLibrary.addRedCards(tmpPool);
        }
        if (ModHelper.isModEnabled("Blue Cards")) {
            CardLibrary.addBlueCards(tmpPool);
        }
        if (ModHelper.isModEnabled("Purple Cards")) {
            CardLibrary.addPurpleCards(tmpPool);
        }
        return tmpPool;
    }

    public Color getCardTrailColor() {
        return Color.CHARTREUSE.cpy();
    }

    public String getLeaderboardCharacterName() {
        return "SILENT";
    }

    public Texture getEnergyImage() {
        return ImageMaster.GREEN_ORB_FLASH_VFX;
    }

    public int getAscensionMaxHPLoss() {
        return 4;
    }

    public BitmapFont getEnergyNumFont() {
        return FontHelper.energyNumFontGreen;
    }

    public void renderOrb(SpriteBatch sb, boolean enabled, float current_x, float current_y) {
        this.energyOrb.renderOrb(sb, enabled, current_x, current_y);
    }

    public void updateOrb(int orbCount) {
        this.energyOrb.updateOrb(orbCount);
    }

    public Prefs getPrefs() {
        if (this.prefs == null) {
            logger.error("prefs need to be initialized first!");
        }
        return this.prefs;
    }

    public void loadPrefs() {
        this.prefs = SaveHelper.getPrefs("STSDataTheSilent");
    }

    public CharStat getCharStat() {
        return this.charStat;
    }

    public int getUnlockedCardCount() {
        return UnlockTracker.unlockedGreenCardCount;
    }

    public int getSeenCardCount() {
        return CardLibrary.seenGreenCards;
    }

    public int getCardCount() {
        return CardLibrary.greenCards;
    }

    public boolean saveFileExists() {
        return SaveAndContinue.saveExistsAndNotCorrupted(this);
    }

    public String getWinStreakKey() {
        return "win_streak_silent";
    }

    public String getLeaderboardWinStreakKey() {
        return "SILENT_CONSECUTIVE_WINS";
    }

    public void renderStatScreen(SpriteBatch sb, float screenX, float renderY) {
        if (!UnlockTracker.isCharacterLocked("The Silent")) {
            if (CardCrawlGame.mainMenuScreen.statsScreen.silentHb == null) {
                CardCrawlGame.mainMenuScreen.statsScreen.silentHb = new Hitbox(150.0F * Settings.scale,
                        150.0F * Settings.scale);
            }

            StatsScreen.renderHeader(sb, StatsScreen.NAMES[3], screenX, renderY);
            this.charStat.render(sb, screenX, renderY);
        }
    }

    public void doCharSelectScreenSelectEffect() {
        CardCrawlGame.sound.playA("ATTACK_DAGGER_2", MathUtils.random(-0.2F, 0.2F));
        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.SHORT, false);
    }

    public String getCustomModeCharacterButtonSoundKey() {
        return "ATTACK_DAGGER_2";
    }

    public Texture getCustomModeCharacterButtonImage() {
        return ImageMaster.FILTER_SILENT;
    }

    public CharacterStrings getCharacterString() {
        return CardCrawlGame.languagePack.getCharacterString("Silent");
    }

    public String getLocalizedCharacterName() {
        return NAMES[0];
    }

    public void refreshCharStat() {
        this.charStat = new CharStat(this);
    }

    public AbstractPlayer newInstance() {
        return new TheSilent(this.name);
    }

    public TextureAtlas.AtlasRegion getOrb() {
        return AbstractCard.orb_green;
    }

    public void damage(DamageInfo info) {
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output - this.currentBlock > 0) {
            AnimationState.TrackEntry e = this.state.setAnimation(0, "Hit", false);
            this.state.addAnimation(0, "Idle", true, 0.0F);
            e.setTimeScale(0.9F);
        }

        super.damage(info);
    }

    public String getSpireHeartText() {
        return SpireHeart.DESCRIPTIONS[9];
    }

    public Color getSlashAttackColor() {
        return Color.GREEN;
    }

    public AbstractGameAction.AttackEffect[] getSpireHeartSlashEffect() {
        return new AbstractGameAction.AttackEffect[] {AbstractGameAction.AttackEffect.SLASH_HEAVY,
                AbstractGameAction.AttackEffect.POISON, AbstractGameAction.AttackEffect.SLASH_DIAGONAL,
                AbstractGameAction.AttackEffect.SLASH_HEAVY, AbstractGameAction.AttackEffect.POISON,
                AbstractGameAction.AttackEffect.SLASH_DIAGONAL };
    }

    public String getVampireText() {
        return Vampires.DESCRIPTIONS[1];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\characters\
 * TheSilent.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

