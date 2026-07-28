package com.megacrit.cardcrawl.characters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.*;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.purple.Eruption;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.EnergyManager;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.events.beyond.SpireHeart;
import com.megacrit.cardcrawl.events.city.Vampires;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.localization.CharacterStrings;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.screens.CharSelectInfo;
import com.megacrit.cardcrawl.screens.charSelect.CharacterOption;
import com.megacrit.cardcrawl.screens.charSelect.CharacterSelectScreen;
import com.megacrit.cardcrawl.screens.stats.CharStat;
import com.megacrit.cardcrawl.screens.stats.StatsScreen;
import com.megacrit.cardcrawl.ui.panels.energyorb.EnergyOrbInterface;
import com.megacrit.cardcrawl.ui.panels.energyorb.EnergyOrbPurple;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class Watcher
        extends AbstractPlayer {
    private static final Logger logger = LogManager.getLogger(Watcher.class.getName());
    private static final CharacterStrings characterStrings = CardCrawlGame.languagePack.getCharacterString("Watcher");
    public static final String[] NAMES = characterStrings.NAMES;
    public static final String[] TEXT = characterStrings.TEXT;
    private EnergyOrbInterface energyOrb = (EnergyOrbInterface) new EnergyOrbPurple();

    private Prefs prefs;
    private CharStat charStat;
    private Bone eyeBone;
    protected TextureAtlas eyeAtlas = null;
    protected Skeleton eyeSkeleton;
    public AnimationState eyeState;
    protected AnimationStateData eyeStateData;

    Watcher(String playerName) {
        super(playerName, PlayerClass.WATCHER);
        this.charStat = new CharStat(this);

        this.drawY += 7.0F * Settings.scale;
        this.dialogX = this.drawX + 0.0F * Settings.scale;
        this.dialogY = this.drawY + 170.0F * Settings.scale;

        initializeClass((String) null, "images/characters/watcher/shoulder2.png",
                "images/characters/watcher/shoulder.png", "images/characters/watcher/corpse.png",

                getLoadout(), 0.0F, -5.0F, 240.0F, 270.0F, new EnergyManager(3));

        loadAnimation("images/characters/watcher/idle/skeleton.atlas", "images/characters/watcher/idle/skeleton.json",
                1.0F);

        loadEyeAnimation();

        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        this.stateData.setMix("Hit", "Idle", 0.1F);
        e.setTimeScale(0.7F);
        this.eyeBone = this.skeleton.findBone("eye_anchor");

        if (ModHelper.enabledMods.size() > 0
                && (ModHelper.isModEnabled("Diverse") || ModHelper.isModEnabled("Chimera") ||
                        ModHelper.isModEnabled("Blue Cards"))) {
            this.masterMaxOrbs = 1;
        }
    }

    private void loadEyeAnimation() {
        this.eyeAtlas = new TextureAtlas(Gdx.files.internal("images/characters/watcher/eye_anim/skeleton.atlas"));
        SkeletonJson json = new SkeletonJson(this.eyeAtlas);
        json.setScale(Settings.scale / 1.0F);
        SkeletonData skeletonData = json.readSkeletonData(Gdx.files
                .internal("images/characters/watcher/eye_anim/skeleton.json"));
        this.eyeSkeleton = new Skeleton(skeletonData);
        this.eyeSkeleton.setColor(Color.WHITE);
        this.eyeStateData = new AnimationStateData(skeletonData);
        this.eyeState = new AnimationState(this.eyeStateData);
        this.eyeStateData.setDefaultMix(0.2F);
        this.eyeState.setAnimation(0, "None", true);
    }

    public String getPortraitImageName() {
        return "watcherPortrait.jpg";
    }

    public ArrayList<String> getStartingRelics() {
        ArrayList<String> retVal = new ArrayList<>();
        retVal.add("PureWater");
        return retVal;
    }

    public ArrayList<String> getStartingDeck() {
        ArrayList<String> retVal = new ArrayList<>();
        retVal.add("Strike_P");
        retVal.add("Strike_P");
        retVal.add("Strike_P");
        retVal.add("Strike_P");
        retVal.add("Defend_P");
        retVal.add("Defend_P");
        retVal.add("Defend_P");
        retVal.add("Defend_P");
        retVal.add("Eruption");
        retVal.add("Vigilance");
        return retVal;
    }

    public AbstractCard getStartCardForEvent() {
        return (AbstractCard) new Eruption();
    }

    public CharSelectInfo getLoadout() {
        return new CharSelectInfo(NAMES[0], TEXT[0], 72, 72, 0, 99, 5, this,

                getStartingRelics(),
                getStartingDeck(), false);
    }

    public String getTitle(PlayerClass plyrClass) {
        return AbstractPlayer.uiStrings.TEXT[4];
    }

    public AbstractCard.CardColor getCardColor() {
        return AbstractCard.CardColor.PURPLE;
    }

    public Color getCardRenderColor() {
        return Settings.PURPLE_COLOR;
    }

    public CharacterOption getCharacterSelectOption() {
        return new CharacterOption(CharacterSelectScreen.TEXT[14], this, ImageMaster.CHAR_SELECT_WATCHER,
                ImageMaster.CHAR_SELECT_BG_WATCHER);
    }

    public String getAchievementKey() {
        return "AMETHYST";
    }

    public ArrayList<AbstractCard> getCardPool(ArrayList<AbstractCard> tmpPool) {
        CardLibrary.addPurpleCards(tmpPool);
        if (ModHelper.isModEnabled("Red Cards")) {
            CardLibrary.addRedCards(tmpPool);
        }
        if (ModHelper.isModEnabled("Green Cards")) {
            CardLibrary.addGreenCards(tmpPool);
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
        return Color.PURPLE.cpy();
    }

    public String getLeaderboardCharacterName() {
        return "WATCHER";
    }

    public Texture getEnergyImage() {
        return ImageMaster.PURPLE_ORB_FLASH_VFX;
    }

    public int getAscensionMaxHPLoss() {
        return 4;
    }

    public BitmapFont getEnergyNumFont() {
        return FontHelper.energyNumFontPurple;
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
        this.prefs = SaveHelper.getPrefs("STSDataWatcher");
    }

    public CharStat getCharStat() {
        return this.charStat;
    }

    public int getUnlockedCardCount() {
        return UnlockTracker.unlockedPurpleCardCount;
    }

    public int getSeenCardCount() {
        return CardLibrary.seenPurpleCards;
    }

    public int getCardCount() {
        return CardLibrary.purpleCards;
    }

    public boolean saveFileExists() {
        return SaveAndContinue.saveExistsAndNotCorrupted(this);
    }

    public String getWinStreakKey() {
        return "win_streak_watcher";
    }

    public String getLeaderboardWinStreakKey() {
        return "WATCHER_CONSECUTIVE_WINS";
    }

    public void renderStatScreen(SpriteBatch sb, float screenX, float renderY) {
        if (!UnlockTracker.isCharacterLocked("Watcher")) {
            if (CardCrawlGame.mainMenuScreen.statsScreen.watcherHb == null) {
                CardCrawlGame.mainMenuScreen.statsScreen.watcherHb = new Hitbox(150.0F * Settings.scale,
                        150.0F * Settings.scale);
            }

            StatsScreen.renderHeader(sb, StatsScreen.NAMES[5], screenX, renderY);
            this.charStat.render(sb, screenX, renderY);
        }
    }

    public void doCharSelectScreenSelectEffect() {
        CardCrawlGame.sound.playA("SELECT_WATCHER", MathUtils.random(-0.15F, 0.15F));
        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.SHORT, false);
    }

    public String getCustomModeCharacterButtonSoundKey() {
        return "SELECT_WATCHER";
    }

    public Texture getCustomModeCharacterButtonImage() {
        return ImageMaster.FILTER_WATCHER;
    }

    public CharacterStrings getCharacterString() {
        return CardCrawlGame.languagePack.getCharacterString("Watcher");
    }

    public String getLocalizedCharacterName() {
        return NAMES[0];
    }

    public void refreshCharStat() {
        this.charStat = new CharStat(this);
    }

    public AbstractPlayer newInstance() {
        return new Watcher(this.name);
    }

    public TextureAtlas.AtlasRegion getOrb() {
        return AbstractCard.orb_purple;
    }

    public void damage(DamageInfo info) {
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output - this.currentBlock > 0
                && this.atlas != null) {

            AnimationState.TrackEntry e = this.state.setAnimation(0, "Hit", false);
            this.state.addAnimation(0, "Idle", true, 0.0F);
            e.setTime(0.9F);
        }

        super.damage(info);
    }

    public String getSpireHeartText() {
        return SpireHeart.DESCRIPTIONS[15];
    }

    public Color getSlashAttackColor() {
        return Color.MAGENTA;
    }

    public AbstractGameAction.AttackEffect[] getSpireHeartSlashEffect() {
        return new AbstractGameAction.AttackEffect[] {AbstractGameAction.AttackEffect.BLUNT_LIGHT,
                AbstractGameAction.AttackEffect.BLUNT_HEAVY, AbstractGameAction.AttackEffect.BLUNT_LIGHT,
                AbstractGameAction.AttackEffect.BLUNT_HEAVY, AbstractGameAction.AttackEffect.BLUNT_HEAVY,
                AbstractGameAction.AttackEffect.BLUNT_LIGHT };
    }

    public String getVampireText() {
        return Vampires.DESCRIPTIONS[1];
    }

    public void onStanceChange(String newStance) {
        if (newStance.equals("Calm")) {
            this.eyeState.setAnimation(0, "Calm", true);
        } else if (newStance.equals("Wrath")) {
            this.eyeState.setAnimation(0, "Wrath", true);
        } else if (newStance.equals("Divinity")) {
            this.eyeState.setAnimation(0, "Divinity", true);
        } else if (newStance.equals("Neutral")) {
            this.eyeState.setAnimation(0, "None", true);
        } else {
            this.eyeState.setAnimation(0, "None", true);
        }
    }

    public void renderPlayerImage(SpriteBatch sb) {
        this.state.update(Gdx.graphics.getDeltaTime());
        this.state.apply(this.skeleton);
        this.skeleton.updateWorldTransform();
        this.skeleton.setPosition(this.drawX + this.animX, this.drawY + this.animY);
        this.skeleton.setColor(this.tint.color);
        this.skeleton.setFlip(this.flipHorizontal, this.flipVertical);

        this.eyeState.update(Gdx.graphics.getDeltaTime());
        this.eyeState.apply(this.eyeSkeleton);
        this.eyeSkeleton.updateWorldTransform();
        this.eyeSkeleton.setPosition(this.skeleton.getX() + this.eyeBone.getWorldX(),
                this.skeleton.getY() + this.eyeBone.getWorldY());
        this.eyeSkeleton.setColor(this.tint.color);
        this.eyeSkeleton.setFlip(this.flipHorizontal, this.flipVertical);

        sb.end();
        CardCrawlGame.psb.begin();
        sr.draw(CardCrawlGame.psb, this.skeleton);
        sr.draw(CardCrawlGame.psb, this.eyeSkeleton);
        CardCrawlGame.psb.end();
        sb.begin();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\characters\Watcher
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

