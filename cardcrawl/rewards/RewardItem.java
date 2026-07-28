package com.megacrit.cardcrawl.rewards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.TutorialStrings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.ui.FtueTip;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.ObtainKeyEffect;
import com.megacrit.cardcrawl.vfx.RewardGlowEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class RewardItem {
    private static final Logger logger = LogManager.getLogger(RewardItem.class.getName());
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("RewardItem");
    public static final String[] TEXT = uiStrings.TEXT;
    private static final TutorialStrings tutorialStrings = CardCrawlGame.languagePack.getTutorialString("Potion Tip");
    public static final String[] MSG = tutorialStrings.TEXT;
    public static final String[] LABEL = tutorialStrings.LABEL;

    public RewardType type;
    public Texture outlineImg = null;
    public Texture img = null;
    public int goldAmt = 0;
    public int bonusGold = 0;
    public String text;
    public RewardItem relicLink;
    public AbstractRelic relic;
    public AbstractPotion potion;
    public ArrayList<AbstractCard> cards;
    private ArrayList<AbstractGameEffect> effects = new ArrayList<>();
    private boolean isBoss;
    public Hitbox hb = new Hitbox(460.0F * Settings.xScale, 90.0F * Settings.yScale);
    public float y;
    public float flashTimer = 0.0F;

    public boolean isDone = false;
    public boolean ignoreReward = false;
    public boolean redText = false;
    public static final float REWARD_ITEM_X = Settings.WIDTH * 0.41F;
    private static final float FLASH_DUR = 0.5F;
    private static final int ITEM_W = 464;
    private static final int ITEM_H = 98;
    private static final float REWARD_TEXT_X = Settings.WIDTH * 0.434F;

    private Color reticleColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);

    public enum RewardType {
        CARD, GOLD, RELIC, POTION, STOLEN_GOLD, EMERALD_KEY, SAPPHIRE_KEY;
    }

    public RewardItem(RewardItem setRelicLink, RewardType type) {
        this.type = type;

        if (type == RewardType.SAPPHIRE_KEY) {
            this.text = TEXT[6];
            this.img = ImageMaster.loadImage("images/relics/sapphire_key.png");
            this.outlineImg = ImageMaster.loadImage("images/relics/outline/sapphire_key.png");
            this.relicLink = setRelicLink;
            setRelicLink.relicLink = this;
        } else if (type == RewardType.EMERALD_KEY) {
            this.text = TEXT[5];
            this.img = ImageMaster.loadImage("images/relics/emerald_key.png");
            this.outlineImg = ImageMaster.loadImage("images/relics/outline/emerald_key.png");
        }
    }

    public RewardItem(int gold) {
        this.type = RewardType.GOLD;
        this.goldAmt = gold;
        applyGoldBonus(false);
    }

    public RewardItem(int gold, boolean theft) {
        this.type = RewardType.STOLEN_GOLD;
        this.goldAmt = gold;
        applyGoldBonus(theft);
    }

    private void applyGoldBonus(boolean theft) {
        int tmp = this.goldAmt;
        this.bonusGold = 0;

        if (theft) {
            this.text = this.goldAmt + TEXT[0];
        } else {
            if (!(AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.TreasureRoom)) {
                if (AbstractDungeon.player.hasRelic("Golden Idol")) {
                    this.bonusGold += MathUtils.round(tmp * 0.25F);
                }

                if (ModHelper.isModEnabled("Midas")) {
                    this.bonusGold += MathUtils.round(tmp * 2.0F);
                }

                if (ModHelper.isModEnabled("MonsterHunter")) {
                    this.bonusGold += MathUtils.round(tmp * 1.5F);
                }
            }

            if (this.bonusGold == 0) {
                this.text = this.goldAmt + TEXT[1];
            } else {
                this.text = this.goldAmt + TEXT[1] + " (" + this.bonusGold + ")";
            }
        }
    }

    public RewardItem(AbstractRelic relic) {
        this.type = RewardType.RELIC;
        this.relic = relic;
        relic.hb = new Hitbox(80.0F * Settings.scale, 80.0F * Settings.scale);
        relic.hb.move(-1000.0F, -1000.0F);
        this.text = relic.name;
    }

    public RewardItem(AbstractPotion potion) {
        this.type = RewardType.POTION;
        this.potion = potion;
        this.text = potion.name;
    }

    public RewardItem() {
        this.type = RewardType.CARD;
        this.isBoss = AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
        this.cards = AbstractDungeon.getRewardCards();
        this.text = TEXT[2];
    }

    public RewardItem(AbstractCard.CardColor colorType) {
        this.type = RewardType.CARD;
        this.isBoss = AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
        if (colorType == AbstractCard.CardColor.COLORLESS) {
            this.cards = AbstractDungeon.getColorlessRewardCards();
        } else {
            this.cards = AbstractDungeon.getRewardCards();
        }
        this.text = TEXT[2];

        for (AbstractCard c : this.cards) {
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onPreviewObtainCard(c);
            }
        }
    }

    public void incrementGold(int gold) {
        if (this.type == RewardType.GOLD) {
            this.goldAmt += gold;
            applyGoldBonus(false);
        } else if (this.type == RewardType.STOLEN_GOLD) {
            this.goldAmt += gold;
            applyGoldBonus(true);
        } else {
            logger.info("ERROR: Using increment() wrong for RewardItem");
        }
    }

    public void move(float y) {
        this.y = y;
        this.hb.move(Settings.WIDTH / 2.0F, y);
        if (this.type == RewardType.POTION) {
            this.potion.move(REWARD_ITEM_X, y);
        } else if (this.type == RewardType.RELIC) {
            this.relic.currentX = REWARD_ITEM_X;
            this.relic.currentY = y;
            this.relic.targetX = REWARD_ITEM_X;
            this.relic.targetY = y;
        }
    }

    public void flash() {
        this.flashTimer = 0.5F;
    }

    public void update() {
        if (this.flashTimer > 0.0F) {
            this.flashTimer -= Gdx.graphics.getDeltaTime();
            if (this.flashTimer < 0.0F) {
                this.flashTimer = 0.0F;
            }
        }

        this.hb.update();
        updateReticle();

        if (this.effects.size() == 0) {
            this.effects.add(new RewardGlowEffect(this.hb.cX, this.hb.cY));
        }
        for (Iterator<AbstractGameEffect> i = this.effects.iterator(); i.hasNext();) {
            AbstractGameEffect e = i.next();
            e.update();
            if (e.isDone) {
                i.remove();
            }
        }

        if (this.hb.hovered) {
            switch (this.type) {
                case POTION:
                    if (!AbstractDungeon.topPanel.potionCombine) {
                        TipHelper.queuePowerTips(360.0F * Settings.scale, InputHelper.mY, this.potion.tips);
                    }
                    break;
            }

        }
        if (this.relicLink != null) {
            this.relicLink.redText = this.hb.hovered;
        }

        if (this.hb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        }

        if (this.hb.hovered && InputHelper.justClickedLeft && !this.isDone) {
            CardCrawlGame.sound.playA("UI_CLICK_1", 0.1F);
            this.hb.clickStarted = true;
        }

        if (this.hb.hovered && CInputActionSet.select.isJustPressed() && !this.isDone) {
            this.hb.clicked = true;
            CardCrawlGame.sound.playA("UI_CLICK_1", 0.1F);
        }

        if (this.hb.clicked) {
            this.hb.clicked = false;
            this.isDone = true;
        }
    }

    private void updateReticle() {
        if (!Settings.isControllerMode) {
            return;
        }

        if (this.hb.hovered) {
            this.reticleColor.a += Gdx.graphics.getDeltaTime() * 3.0F;
            if (this.reticleColor.a > 1.0F) {
                this.reticleColor.a = 1.0F;
            }
        } else {
            this.reticleColor.a = 0.0F;
        }
    }

    public boolean claimReward() {
        switch (this.type) {
            case GOLD:
                CardCrawlGame.sound.play("GOLD_GAIN");
                if (this.bonusGold == 0) {
                    AbstractDungeon.player.gainGold(this.goldAmt);
                } else {
                    AbstractDungeon.player.gainGold(this.goldAmt + this.bonusGold);
                }
                return true;
            case STOLEN_GOLD:
                CardCrawlGame.sound.play("GOLD_GAIN");
                if (this.bonusGold == 0) {
                    AbstractDungeon.player.gainGold(this.goldAmt);
                } else {
                    AbstractDungeon.player.gainGold(this.goldAmt + this.bonusGold);
                }
                return true;
            case POTION:
                if (AbstractDungeon.player.hasRelic("Sozu")) {
                    AbstractDungeon.player.getRelic("Sozu").flash();
                    return true;
                }
                if (AbstractDungeon.player.obtainPotion(this.potion)) {
                    if (!((Boolean) TipTracker.tips.get("POTION_TIP")).booleanValue()) {
                        AbstractDungeon.ftue = new FtueTip(LABEL[0], MSG[0], Settings.WIDTH / 2.0F,
                                Settings.HEIGHT / 2.0F, this.potion);

                        TipTracker.neverShowAgain("POTION_TIP");
                    }
                    CardCrawlGame.metricData.addPotionObtainData(this.potion);
                    return true;
                }

                return false;
            case RELIC:
                if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID) {
                    return false;
                }

                if (!this.ignoreReward) {
                    this.relic.instantObtain();
                    CardCrawlGame.metricData.addRelicObtainData(this.relic);
                }

                if (this.relicLink != null) {
                    this.relicLink.isDone = true;
                    this.relicLink.ignoreReward = true;
                }

                return true;
            case CARD:
                if (AbstractDungeon.player.hasRelic("Question Card")) {
                    AbstractDungeon.player.getRelic("Question Card").flash();
                }

                if (AbstractDungeon.player.hasRelic("Busted Crown")) {
                    AbstractDungeon.player.getRelic("Busted Crown").flash();
                }

                if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
                    AbstractDungeon.cardRewardScreen.open(this.cards, this, TEXT[4]);
                    AbstractDungeon.previousScreen = AbstractDungeon.CurrentScreen.COMBAT_REWARD;
                }
                return false;
            case SAPPHIRE_KEY:
                if (!this.ignoreReward) {
                    AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.BLUE));
                }

                this.relicLink.isDone = true;
                this.relicLink.ignoreReward = true;
                this.img.dispose();
                this.outlineImg.dispose();
                return true;
            case EMERALD_KEY:
                AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.GREEN));
                this.img.dispose();
                this.outlineImg.dispose();
                return true;
        }

        logger.info("ERROR: Claim Reward() failed");
        return false;
    }

    public void render(SpriteBatch sb) {
        Color color;
        if (this.hb.hovered) {
            sb.setColor(new Color(0.4F, 0.6F, 0.6F, 1.0F));
        } else {
            sb.setColor(new Color(0.5F, 0.6F, 0.6F, 0.8F));
        }

        if (this.hb.clickStarted) {
            sb.draw(ImageMaster.REWARD_SCREEN_ITEM, Settings.WIDTH / 2.0F - 232.0F, this.y - 49.0F, 232.0F, 49.0F,
                    464.0F, 98.0F, Settings.xScale * 0.98F, Settings.scale * 0.98F, 0.0F, 0, 0, 464, 98, false, false);

        } else {

            sb.draw(ImageMaster.REWARD_SCREEN_ITEM, Settings.WIDTH / 2.0F - 232.0F, this.y - 49.0F, 232.0F, 49.0F,
                    464.0F, 98.0F, Settings.xScale, Settings.scale, 0.0F, 0, 0, 464, 98, false, false);
        }

        if (this.flashTimer != 0.0F) {
            sb.setColor(0.6F, 1.0F, 1.0F, this.flashTimer * 1.5F);
            sb.setBlendFunction(770, 1);
            sb.draw(ImageMaster.REWARD_SCREEN_ITEM, Settings.WIDTH / 2.0F - 232.0F, this.y - 49.0F, 232.0F, 49.0F,
                    464.0F, 98.0F, Settings.xScale * 1.03F, Settings.scale * 1.15F, 0.0F, 0, 0, 464, 98, false, false);

            sb.setBlendFunction(770, 771);
        }

        sb.setColor(Color.WHITE);

        switch (this.type) {
            case CARD:
                if (this.isBoss) {
                    sb.draw(ImageMaster.REWARD_CARD_BOSS, REWARD_ITEM_X - 32.0F, this.y - 32.0F - 2.0F * Settings.scale,
                            32.0F, 32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 64, 64, false,
                            false);

                    break;
                }

                sb.draw(ImageMaster.REWARD_CARD_NORMAL, REWARD_ITEM_X - 32.0F, this.y - 32.0F - 2.0F * Settings.scale,
                        32.0F, 32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
                break;

            case GOLD:
                sb.draw(ImageMaster.UI_GOLD, REWARD_ITEM_X - 32.0F, this.y - 32.0F - 2.0F * Settings.scale, 32.0F,
                        32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
                break;

            case STOLEN_GOLD:
                sb.draw(ImageMaster.UI_GOLD, REWARD_ITEM_X - 32.0F, this.y - 32.0F - 2.0F * Settings.scale, 32.0F,
                        32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
                break;

            case RELIC:
                this.relic.renderWithoutAmount(sb, new Color(0.0F, 0.0F, 0.0F, 0.25F));

                if (this.hb.hovered) {
                    if (this.relicLink != null) {
                        ArrayList<PowerTip> tips = new ArrayList<>();
                        tips.add(new PowerTip(this.relic.name, this.relic.description));

                        if (this.relicLink.type == RewardType.SAPPHIRE_KEY) {
                            tips.add(new PowerTip(TEXT[7], TEXT[8] + FontHelper.colorString(TEXT[6] + TEXT[9], "y")));
                        }

                        TipHelper.queuePowerTips(360.0F * Settings.scale, InputHelper.mY + 50.0F * Settings.scale,
                                tips);
                        break;
                    }
                    this.relic.renderTip(sb);
                }
                break;

            case SAPPHIRE_KEY:
                renderKey(sb);

                if (this.hb.hovered && this.relicLink != null) {
                    TipHelper.renderGenericTip(360.0F * Settings.scale, InputHelper.mY + 50.0F * Settings.scale,
                            TEXT[7], TEXT[8] +

                                    FontHelper.colorString(this.relicLink.relic.name + TEXT[9], "y"));
                }
                break;
            case EMERALD_KEY:
                renderKey(sb);
                break;
            case POTION:
                this.potion.renderLightOutline(sb);
                this.potion.render(sb);
                this.potion.generateSparkles(this.potion.posX, this.potion.posY, true);
                break;
        }

        if (this.hb.hovered) {
            color = Settings.GOLD_COLOR;
        } else {
            color = Settings.CREAM_COLOR;
        }

        if (this.redText) {
            color = Settings.RED_TEXT_COLOR;
        }

        FontHelper.renderSmartText(sb, FontHelper.cardDescFont_N, this.text, REWARD_TEXT_X,
                this.y + 5.0F * Settings.scale, 1000.0F * Settings.scale, 0.0F, color);

        if (!this.hb.hovered) {
            for (AbstractGameEffect e : this.effects) {
                e.render(sb);
            }
        }

        if (Settings.isControllerMode) {
            renderReticle(sb, this.hb);
        }

        if (this.type == RewardType.SAPPHIRE_KEY) {
            renderRelicLink(sb);
        }
        this.hb.render(sb);
    }

    public void renderReticle(SpriteBatch sb, Hitbox hb) {
        float offset = Interpolation.fade.apply(18.0F * Settings.scale, 12.0F * Settings.scale, this.reticleColor.a);
        sb.setColor(this.reticleColor);
        renderReticleCorner(sb, -hb.width / 2.0F + offset, hb.height / 2.0F - offset, hb, false, false);
        renderReticleCorner(sb, hb.width / 2.0F - offset, hb.height / 2.0F - offset, hb, true, false);
        renderReticleCorner(sb, -hb.width / 2.0F + offset, -hb.height / 2.0F + offset, hb, false, true);
        renderReticleCorner(sb, hb.width / 2.0F - offset, -hb.height / 2.0F + offset, hb, true, true);
    }

    private void renderReticleCorner(SpriteBatch sb, float x, float y, Hitbox hb, boolean flipX, boolean flipY) {
        sb.draw(ImageMaster.RETICLE_CORNER, hb.cX + x - 18.0F, hb.cY + y - 18.0F, 18.0F, 18.0F, 36.0F, 36.0F,
                Settings.scale, Settings.scale, 0.0F, 0, 0, 36, 36, flipX, flipY);
    }

    private void renderRelicLink(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.RELIC_LINKED, this.hb.cX - 64.0F, this.y - 64.0F + 52.0F * Settings.scale, 64.0F, 64.0F,
                128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
    }

    private void renderKey(SpriteBatch sb) {
        if (this.img != null && this.outlineImg != null) {
            sb.setColor(AbstractRelic.PASSIVE_OUTLINE_COLOR);
            sb.draw(this.outlineImg, REWARD_ITEM_X - 64.0F, this.y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F,
                    Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);

            sb.setColor(Color.WHITE);
            sb.draw(this.img, REWARD_ITEM_X - 64.0F, this.y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
        }
    }

    public void recordCardSkipMetrics() {
        if (this.cards != null && !this.cards.isEmpty()) {
            HashMap<String, Object> choice = new HashMap<>();
            ArrayList<String> notpicked = new ArrayList<>();
            for (AbstractCard card : this.cards) {
                notpicked.add(card.getMetricID());
            }
            choice.put("picked", "SKIP");
            choice.put("not_picked", notpicked);
            choice.put("floor", Integer.valueOf(AbstractDungeon.floorNum));
            CardCrawlGame.metricData.card_choices.add(choice);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\rewards\RewardItem
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

