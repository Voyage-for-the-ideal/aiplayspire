package com.megacrit.cardcrawl.potions;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.TopPanel;
import com.megacrit.cardcrawl.vfx.FlashPotionEffect;
import com.megacrit.cardcrawl.vfx.RarePotionParticleEffect;
import com.megacrit.cardcrawl.vfx.UncommonPotionParticleEffect;

import java.util.ArrayList;
import java.util.Iterator;

public abstract class AbstractPotion {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("AbstractPotion");
    public static final String[] TEXT = uiStrings.TEXT;
    public String ID;
    public String name;
    public String description;
    public int slot = -1;
    public ArrayList<PowerTip> tips = new ArrayList<>();

    private Texture containerImg;
    private Texture liquidImg;
    private Texture hybridImg;
    private Texture spotsImg;
    protected Color labOutlineColor = Settings.HALF_TRANSPARENT_BLACK_COLOR;
    private Texture outlineImg;
    public float posX;
    private ArrayList<FlashPotionEffect> effect = new ArrayList<>();
    public float posY;
    private static final int RAW_W = 64;
    public float scale = Settings.scale;
    public boolean isObtained = false;
    private float sparkleTimer = 0.0F;

    private static final int FLASH_COUNT = 1;

    private static final float FLASH_INTERVAL = 0.33F;
    private int flashCount = 0;
    private float flashTimer = 0.0F;

    public final PotionEffect p_effect;

    public final PotionColor color;
    public Color liquidColor;
    public Color hybridColor = null;
    public Color spotsColor = null;
    public PotionRarity rarity;
    public PotionSize size;
    protected int potency = 0;
    public Hitbox hb = new Hitbox(64.0F * Settings.scale, 64.0F * Settings.scale);
    private float angle = 0.0F;
    protected boolean canUse = false;
    public boolean discarded = false;
    public boolean isThrown = false;
    public boolean targetRequired = false;
    private static final Color PLACEHOLDER_COLOR = new Color(1.0F, 1.0F, 1.0F, 0.75F);

    public enum PotionSize {
        T, S, M, SPHERE, H, BOTTLE, HEART, SNECKO, FAIRY, GHOST, JAR, BOLT, CARD, MOON, SPIKY, EYE, ANVIL;
    }

    public enum PotionRarity {
        PLACEHOLDER, COMMON, UNCOMMON, RARE;
    }

    public enum PotionColor {
        POISON, BLUE, FIRE, GREEN, EXPLOSIVE, WEAK, FEAR, STRENGTH, WHITE, FAIRY, ANCIENT, ELIXIR, NONE, ENERGY, SWIFT, FRUIT, SNECKO, SMOKE, STEROID, SKILL, ATTACK, POWER;
    }

    public enum PotionEffect {
        NONE, RAINBOW, OSCILLATE;
    }

    public AbstractPotion(String name, String id, PotionRarity rarity, PotionSize size, PotionEffect effect,
            Color liquidColor, Color hybridColor, Color spotsColor) {
        this.ID = id;
        this.name = name;
        this.rarity = rarity;
        this.color = null;
        this.liquidColor = liquidColor.cpy();
        this.hybridColor = hybridColor;
        this.spotsColor = spotsColor;
        this.p_effect = effect;
        this.size = size;

        initializeImage();
        initializeData();
    }

    public AbstractPotion(String name, String id, PotionRarity rarity, PotionSize size, PotionColor color) {
        this.color = color;
        this.size = size;
        this.ID = id;
        this.name = name;
        this.rarity = rarity;
        this.p_effect = PotionEffect.NONE;

        initializeImage();
        initializeColor();
        initializeData();
    }

    private void initializeImage() {
        switch (this.size) {
            case T:
                this.containerImg = ImageMaster.POTION_T_CONTAINER;
                this.liquidImg = ImageMaster.POTION_T_LIQUID;
                this.hybridImg = ImageMaster.POTION_T_HYBRID;
                this.spotsImg = ImageMaster.POTION_T_SPOTS;
                this.outlineImg = ImageMaster.POTION_T_OUTLINE;
                return;
            case S:
                this.containerImg = ImageMaster.POTION_S_CONTAINER;
                this.liquidImg = ImageMaster.POTION_S_LIQUID;
                this.hybridImg = ImageMaster.POTION_S_HYBRID;
                this.spotsImg = ImageMaster.POTION_S_SPOTS;
                this.outlineImg = ImageMaster.POTION_S_OUTLINE;
                return;
            case M:
                this.containerImg = ImageMaster.POTION_M_CONTAINER;
                this.liquidImg = ImageMaster.POTION_M_LIQUID;
                this.hybridImg = ImageMaster.POTION_M_HYBRID;
                this.spotsImg = ImageMaster.POTION_M_SPOTS;
                this.outlineImg = ImageMaster.POTION_M_OUTLINE;
                return;
            case SPHERE:
                this.containerImg = ImageMaster.POTION_SPHERE_CONTAINER;
                this.liquidImg = ImageMaster.POTION_SPHERE_LIQUID;
                this.hybridImg = ImageMaster.POTION_SPHERE_HYBRID;
                this.spotsImg = ImageMaster.POTION_SPHERE_SPOTS;
                this.outlineImg = ImageMaster.POTION_SPHERE_OUTLINE;
                return;
            case H:
                this.containerImg = ImageMaster.POTION_H_CONTAINER;
                this.liquidImg = ImageMaster.POTION_H_LIQUID;
                this.hybridImg = ImageMaster.POTION_H_HYBRID;
                this.spotsImg = ImageMaster.POTION_H_SPOTS;
                this.outlineImg = ImageMaster.POTION_H_OUTLINE;
                return;
            case BOTTLE:
                this.containerImg = ImageMaster.POTION_BOTTLE_CONTAINER;
                this.liquidImg = ImageMaster.POTION_BOTTLE_LIQUID;
                this.hybridImg = ImageMaster.POTION_BOTTLE_HYBRID;
                this.spotsImg = ImageMaster.POTION_BOTTLE_SPOTS;
                this.outlineImg = ImageMaster.POTION_BOTTLE_OUTLINE;
                return;
            case HEART:
                this.containerImg = ImageMaster.POTION_HEART_CONTAINER;
                this.liquidImg = ImageMaster.POTION_HEART_LIQUID;
                this.hybridImg = ImageMaster.POTION_HEART_HYBRID;
                this.spotsImg = ImageMaster.POTION_HEART_SPOTS;
                this.outlineImg = ImageMaster.POTION_HEART_OUTLINE;
                return;
            case SNECKO:
                this.containerImg = ImageMaster.POTION_SNECKO_CONTAINER;
                this.liquidImg = ImageMaster.POTION_SNECKO_LIQUID;
                this.hybridImg = ImageMaster.POTION_SNECKO_HYBRID;
                this.spotsImg = ImageMaster.POTION_SNECKO_SPOTS;
                this.outlineImg = ImageMaster.POTION_SNECKO_OUTLINE;
                return;
            case FAIRY:
                this.containerImg = ImageMaster.POTION_FAIRY_CONTAINER;
                this.liquidImg = ImageMaster.POTION_FAIRY_LIQUID;
                this.hybridImg = ImageMaster.POTION_FAIRY_HYBRID;
                this.spotsImg = ImageMaster.POTION_FAIRY_SPOTS;
                this.outlineImg = ImageMaster.POTION_FAIRY_OUTLINE;
                return;
            case GHOST:
                this.containerImg = ImageMaster.POTION_GHOST_CONTAINER;
                this.liquidImg = ImageMaster.POTION_GHOST_LIQUID;
                this.hybridImg = ImageMaster.POTION_GHOST_HYBRID;
                this.spotsImg = ImageMaster.POTION_GHOST_SPOTS;
                this.outlineImg = ImageMaster.POTION_GHOST_OUTLINE;
                return;
            case JAR:
                this.containerImg = ImageMaster.POTION_JAR_CONTAINER;
                this.liquidImg = ImageMaster.POTION_JAR_LIQUID;
                this.hybridImg = ImageMaster.POTION_JAR_HYBRID;
                this.spotsImg = ImageMaster.POTION_JAR_SPOTS;
                this.outlineImg = ImageMaster.POTION_JAR_OUTLINE;
                return;
            case BOLT:
                this.containerImg = ImageMaster.POTION_BOLT_CONTAINER;
                this.liquidImg = ImageMaster.POTION_BOLT_LIQUID;
                this.hybridImg = ImageMaster.POTION_BOLT_HYBRID;
                this.spotsImg = ImageMaster.POTION_BOLT_SPOTS;
                this.outlineImg = ImageMaster.POTION_BOLT_OUTLINE;
                return;
            case CARD:
                this.containerImg = ImageMaster.POTION_CARD_CONTAINER;
                this.liquidImg = ImageMaster.POTION_CARD_LIQUID;
                this.hybridImg = ImageMaster.POTION_CARD_HYBRID;
                this.spotsImg = ImageMaster.POTION_CARD_SPOTS;
                this.outlineImg = ImageMaster.POTION_CARD_OUTLINE;
                return;
            case MOON:
                this.containerImg = ImageMaster.POTION_MOON_CONTAINER;
                this.liquidImg = ImageMaster.POTION_MOON_LIQUID;
                this.hybridImg = ImageMaster.POTION_MOON_HYBRID;
                this.outlineImg = ImageMaster.POTION_MOON_OUTLINE;
                return;
            case SPIKY:
                this.containerImg = ImageMaster.POTION_SPIKY_CONTAINER;
                this.liquidImg = ImageMaster.POTION_SPIKY_LIQUID;
                this.hybridImg = ImageMaster.POTION_SPIKY_HYBRID;
                this.outlineImg = ImageMaster.POTION_SPIKY_OUTLINE;
                return;
            case EYE:
                this.containerImg = ImageMaster.POTION_EYE_CONTAINER;
                this.liquidImg = ImageMaster.POTION_EYE_LIQUID;
                this.hybridImg = ImageMaster.POTION_EYE_HYBRID;
                this.outlineImg = ImageMaster.POTION_EYE_OUTLINE;
                return;
            case ANVIL:
                this.containerImg = ImageMaster.POTION_ANVIL_CONTAINER;
                this.liquidImg = ImageMaster.POTION_ANVIL_LIQUID;
                this.hybridImg = ImageMaster.POTION_ANVIL_HYBRID;
                this.outlineImg = ImageMaster.POTION_ANVIL_OUTLINE;
                return;
            default:
                this.containerImg = null;
                this.liquidImg = null;
                this.hybridImg = null;
                this.spotsImg = null;
                return;
        }

    }

    public void flash() {
        this.flashCount = 1;
    }

    private void initializeColor() {
        if (this.color != null) {
            switch (this.color) {
                case BLUE:
                    this.liquidColor = Color.SKY.cpy();
                    return;
                case WHITE:
                    this.liquidColor = Color.WHITE.cpy();
                    this.hybridColor = Color.LIGHT_GRAY.cpy();
                    return;
                case FAIRY:
                    this.liquidColor = Color.CLEAR.cpy();
                    this.spotsColor = Color.WHITE.cpy();
                    return;
                case ENERGY:
                    this.liquidColor = Color.GOLD.cpy();
                    return;
                case EXPLOSIVE:
                    this.liquidColor = Color.ORANGE.cpy();
                    return;
                case FIRE:
                    this.liquidColor = Color.RED.cpy();
                    this.hybridColor = Color.ORANGE.cpy();
                    return;
                case GREEN:
                    this.liquidColor = Color.CHARTREUSE.cpy();
                    return;
                case POISON:
                    this.liquidColor = Color.LIME.cpy();
                    this.spotsColor = Color.FOREST.cpy();
                    return;
                case STRENGTH:
                    this.liquidColor = Color.DARK_GRAY.cpy();
                    this.spotsColor = Color.CORAL.cpy();
                    return;
                case STEROID:
                    this.liquidColor = Color.DARK_GRAY.cpy();
                    this.hybridColor = Color.CORAL.cpy();
                    return;
                case SWIFT:
                    this.liquidColor = Color.valueOf("0d429dff");
                    this.spotsColor = Color.CYAN.cpy();
                    return;
                case WEAK:
                    this.liquidColor = Color.VIOLET.cpy();
                    this.hybridColor = Color.MAROON.cpy();
                    return;
                case FEAR:
                    this.liquidColor = Color.BLACK.cpy();
                    this.hybridColor = Color.SCARLET.cpy();
                    return;
                case ELIXIR:
                    this.liquidColor = Color.GOLD.cpy();
                    this.spotsColor = Color.DARK_GRAY.cpy();
                    return;
                case ANCIENT:
                    this.liquidColor = Color.GOLD.cpy();
                    this.hybridColor = Color.CYAN.cpy();
                    return;
                case FRUIT:
                    this.liquidColor = Color.ORANGE.cpy();
                    this.hybridColor = Color.LIME.cpy();
                    return;
                case SNECKO:
                    this.liquidColor = Settings.GREEN_TEXT_COLOR.cpy();
                    this.hybridColor = Settings.GOLD_COLOR.cpy();
                    return;
                case SMOKE:
                    this.liquidColor = Color.GRAY.cpy();
                    this.hybridColor = Color.DARK_GRAY.cpy();
                    return;
                case ATTACK:
                    this.liquidColor = Settings.RED_TEXT_COLOR.cpy();
                    this.hybridColor = Color.FIREBRICK.cpy();
                    return;
                case SKILL:
                    this.liquidColor = Color.FOREST.cpy();
                    this.hybridColor = Color.CHARTREUSE.cpy();
                    return;
                case POWER:
                    this.liquidColor = Color.NAVY.cpy();
                    this.hybridColor = Color.SKY.cpy();
                    return;
                default:
                    this.liquidColor = Color.RED.cpy();
                    this.spotsColor = Color.RED.cpy();
                    return;
            }
        }

    }

    public void move(float setX, float setY) {
        this.posX = setX;
        this.posY = setY;
    }

    public void adjustPosition(int slot) {
        this.posX = TopPanel.potionX + slot * Settings.POTION_W;
        this.posY = Settings.POTION_Y;
        this.hb.move(this.posX, this.posY);
    }

    public int getPrice() {
        switch (this.rarity) {
            case COMMON:
                return 50;
            case UNCOMMON:
                return 75;
            case RARE:
                return 100;
            default:
                return 999;
        }

    }

    public abstract void use(AbstractCreature paramAbstractCreature);

    public boolean canDiscard() {
        if ((AbstractDungeon.getCurrRoom()).event != null &&
                (AbstractDungeon.getCurrRoom()).event instanceof com.megacrit.cardcrawl.events.shrines.WeMeetAgain) {
            return false;
        }

        return true;
    }

    public void initializeData() {
    }

    public boolean canUse() {
        if ((AbstractDungeon.getCurrRoom()).event != null &&
                (AbstractDungeon.getCurrRoom()).event instanceof com.megacrit.cardcrawl.events.shrines.WeMeetAgain) {
            return false;
        }

        if ((AbstractDungeon.getCurrRoom()).monsters == null || (AbstractDungeon.getCurrRoom()).monsters
                .areMonstersBasicallyDead() || AbstractDungeon.actionManager.turnHasEnded ||
                (AbstractDungeon.getCurrRoom()).phase != AbstractRoom.RoomPhase.COMBAT) {
            return false;
        }
        return true;
    }

    public void update() {
        if (this.isObtained) {
            this.hb.update();
            updateFlash();
        }
    }

    private void updateFlash() {
        if (this.flashCount != 0) {
            this.flashTimer -= Gdx.graphics.getDeltaTime();
            if (this.flashTimer < 0.0F) {
                this.flashTimer = 0.33F;
                this.flashCount--;
                this.effect.add(new FlashPotionEffect(this));
            }
        }

        for (Iterator<FlashPotionEffect> i = this.effect.iterator(); i.hasNext();) {
            FlashPotionEffect e = i.next();
            e.update();
            if (e.isDone) {
                i.remove();
            }
        }
    }

    public void setAsObtained(int potionSlot) {
        this.slot = potionSlot;
        this.isObtained = true;
        adjustPosition(potionSlot);
    }

    public static void playPotionSound() {
        int tmp = MathUtils.random(2);
        if (tmp == 0) {
            CardCrawlGame.sound.play("POTION_1");
        } else if (tmp == 1) {
            CardCrawlGame.sound.play("POTION_2");
        } else {
            CardCrawlGame.sound.play("POTION_3");
        }
    }

    public void renderLightOutline(SpriteBatch sb) {
        if (!(this instanceof PotionSlot)) {
            sb.setColor(Settings.QUARTER_TRANSPARENT_BLACK_COLOR);
            sb.draw(this.outlineImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);
        }
    }

    public void renderOutline(SpriteBatch sb) {
        if (!(this instanceof PotionSlot)) {
            sb.setColor(Settings.HALF_TRANSPARENT_BLACK_COLOR);
            sb.draw(this.outlineImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);
        }
    }

    public void renderOutline(SpriteBatch sb, Color c) {
        if (!(this instanceof PotionSlot)) {
            sb.setColor(c);
            sb.draw(this.outlineImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);
        }
    }

    public void renderShiny(SpriteBatch sb) {
        if (!(this instanceof PotionSlot)) {
            sb.setBlendFunction(770, 1);
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.1F));
            sb.draw(this.containerImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);

            sb.setBlendFunction(770, 771);
        }
    }

    public void render(SpriteBatch sb) {
        updateFlash();
        updateEffect();

        if (this instanceof PotionSlot) {
            sb.setColor(PLACEHOLDER_COLOR);
            sb.draw(ImageMaster.POTION_PLACEHOLDER, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                    this.scale, this.scale, this.angle, 0, 0, 64, 64, false, false);

        } else {

            sb.setColor(this.liquidColor);
            sb.draw(this.liquidImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);

            if (this.hybridColor != null) {
                sb.setColor(this.hybridColor);
                sb.draw(this.hybridImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                        this.scale, this.angle, 0, 0, 64, 64, false, false);
            }

            if (this.spotsColor != null) {
                sb.setColor(this.spotsColor);
                sb.draw(this.spotsImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                        this.scale, this.angle, 0, 0, 64, 64, false, false);
            }

            sb.setColor(Color.WHITE);
            sb.draw(this.containerImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);
        }

        for (FlashPotionEffect e : this.effect) {
            e.render(sb, this.posX, this.posY);
        }

        if (this.hb != null) {
            this.hb.render(sb);
        }
    }

    private void updateEffect() {
        switch (this.p_effect) {

            case RAINBOW:
                this.liquidColor.r = (MathUtils.cosDeg((float) (System.currentTimeMillis() / 10L % 360L)) + 1.25F)
                        / 2.3F;
                this.liquidColor.g = (MathUtils.cosDeg((float) ((System.currentTimeMillis() + 1000L) / 10L % 360L))
                        + 1.25F) / 2.3F;
                this.liquidColor.b = (MathUtils.cosDeg((float) ((System.currentTimeMillis() + 2000L) / 10L % 360L))
                        + 1.25F) / 2.3F;
                this.liquidColor.a = 1.0F;
                break;
        }
    }

    public void shopRender(SpriteBatch sb) {
        generateSparkles(0.0F, 0.0F, false);
        updateFlash();
        updateEffect();

        if (this.hb.hovered) {
            TipHelper.queuePowerTips(InputHelper.mX + 50.0F * Settings.scale, InputHelper.mY + 50.0F * Settings.scale,
                    this.tips);

            this.scale = 1.5F * Settings.scale;
        } else {
            this.scale = MathHelper.scaleLerpSnap(this.scale, 1.2F * Settings.scale);
        }

        renderOutline(sb);

        sb.setColor(this.liquidColor);
        sb.draw(this.liquidImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                this.scale, this.angle, 0, 0, 64, 64, false, false);

        if (this.hybridColor != null) {
            sb.setColor(this.hybridColor);
            sb.draw(this.hybridImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);
        }

        if (this.spotsColor != null) {
            sb.setColor(this.spotsColor);
            sb.draw(this.spotsImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);
        }

        sb.setColor(Color.WHITE);
        sb.draw(this.containerImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                this.scale, this.angle, 0, 0, 64, 64, false, false);

        for (FlashPotionEffect e : this.effect) {
            e.render(sb, this.posX, this.posY);
        }

        if (this.hb != null) {
            this.hb.render(sb);
        }
    }

    public void labRender(SpriteBatch sb) {
        updateFlash();
        updateEffect();

        if (this.hb.hovered) {
            TipHelper.queuePowerTips(150.0F * Settings.scale, 800.0F * Settings.scale, this.tips);
            this.scale = 1.5F * Settings.scale;
        } else {
            this.scale = MathHelper.scaleLerpSnap(this.scale, 1.2F * Settings.scale);
        }

        renderOutline(sb, this.labOutlineColor);

        sb.setColor(this.liquidColor);
        sb.draw(this.liquidImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                this.scale, this.angle, 0, 0, 64, 64, false, false);

        if (this.hybridColor != null) {
            sb.setColor(this.hybridColor);
            sb.draw(this.hybridImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);
        }

        if (this.spotsColor != null) {
            sb.setColor(this.spotsColor);
            sb.draw(this.spotsImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, this.angle, 0, 0, 64, 64, false, false);
        }

        sb.setColor(Color.WHITE);
        sb.draw(this.containerImg, this.posX - 32.0F, this.posY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                this.scale, this.angle, 0, 0, 64, 64, false, false);

        for (FlashPotionEffect e : this.effect) {
            e.render(sb, this.posX, this.posY);
        }

        if (this.hb != null) {
            this.hb.render(sb);
        }
    }

    public void generateSparkles(float x, float y, boolean usePositions) {
        if (!Settings.DISABLE_EFFECTS) {
            if (usePositions) {
                switch (this.rarity) {
                    case COMMON:
                    case PLACEHOLDER:
                    default:
                        return;
                    case UNCOMMON:
                        this.sparkleTimer -= Gdx.graphics.getDeltaTime();
                        if (this.sparkleTimer < 0.0f) {
                            AbstractDungeon.topLevelEffects.add(new UncommonPotionParticleEffect(x, y));
                            this.sparkleTimer = MathUtils.random(0.25f, 0.3f);
                            return;
                        }
                        return;
                    case RARE:
                        this.sparkleTimer -= Gdx.graphics.getDeltaTime();
                        if (this.sparkleTimer < 0.0f) {
                            AbstractDungeon.topLevelEffects.add(new RarePotionParticleEffect(x, y));
                            this.sparkleTimer = MathUtils.random(0.35f, 0.5f);
                            return;
                        }
                        return;
                }
            } else {
                switch (this.rarity) {
                    case COMMON:
                    case PLACEHOLDER:
                    default:
                        return;
                    case UNCOMMON:
                        this.sparkleTimer -= Gdx.graphics.getDeltaTime();
                        if (this.sparkleTimer < 0.0f) {
                            AbstractDungeon.topLevelEffects.add(new UncommonPotionParticleEffect(this.hb));
                            this.sparkleTimer = MathUtils.random(0.25f, 0.3f);
                            return;
                        }
                        return;
                    case RARE:
                        this.sparkleTimer -= Gdx.graphics.getDeltaTime();
                        if (this.sparkleTimer < 0.0f) {
                            AbstractDungeon.topLevelEffects.add(new RarePotionParticleEffect(this.hb));
                            this.sparkleTimer = MathUtils.random(0.35f, 0.5f);
                            return;
                        }
                        return;
                }
            }
        }

    }

    public abstract int getPotency(int paramInt);

    public int getPotency() {
        int potency = getPotency(AbstractDungeon.ascensionLevel);
        if (AbstractDungeon.player != null && AbstractDungeon.player.hasRelic("SacredBark")) {
            potency *= 2;
        }
        return potency;
    }

    public boolean onPlayerDeath() {
        return false;
    }

    protected void addToBot(AbstractGameAction action) {
        AbstractDungeon.actionManager.addToBottom(action);
    }

    protected void addToTop(AbstractGameAction action) {
        addToTop(action);
    }

    public static String gameDataUploadHeader() {
        GameDataStringBuilder sb = new GameDataStringBuilder();
        sb.addFieldData("name");
        sb.addFieldData("rarity");
        sb.addFieldData("color");
        sb.addFieldData("text");
        return sb.toString();
    }

    public String getUploadData(String color) {
        GameDataStringBuilder sb = new GameDataStringBuilder();
        sb.addFieldData(this.name);
        sb.addFieldData(this.rarity.toString());
        sb.addFieldData(color);

        String originalValue = String.valueOf(getPotency(0));
        String comboDesc = this.description;
        if (getPotency(0) != getPotency(15)) {
            comboDesc = this.description.replace(originalValue,
                    String.format("%s(%s)", new Object[] {originalValue, Integer.valueOf(getPotency(15)) }));
        }

        sb.addFieldData(comboDesc);
        return sb.toString();
    }

    public abstract AbstractPotion makeCopy();
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * AbstractPotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

