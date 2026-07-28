package com.megacrit.cardcrawl.neow;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.ui.buttons.UnlockConfirmButton;
import com.megacrit.cardcrawl.unlock.AbstractUnlock;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.ConeEffect;
import com.megacrit.cardcrawl.vfx.RoomShineEffect;
import com.megacrit.cardcrawl.vfx.RoomShineEffect2;

import java.util.ArrayList;
import java.util.Iterator;

public class NeowUnlockScreen {
    public ArrayList<AbstractUnlock> unlockBundle;
    private ArrayList<ConeEffect> cones = new ArrayList<>();

    private static final int CONE_AMT = 30;
    private float shinyTimer = 0.0F;
    private static final float SHINY_INTERVAL = 0.2F;
    public UnlockConfirmButton button = new UnlockConfirmButton();

    public long id;

    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("UnlockScreen");
    public static final String[] TEXT = uiStrings.TEXT;

    public void open(ArrayList<AbstractUnlock> unlock, boolean isVictory) {
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.NEOW_UNLOCK;
        this.unlockBundle = unlock;
        this.button.show();
        this.id = CardCrawlGame.sound.play("UNLOCK_SCREEN");

        this.cones.clear();
        int i;
        for (i = 0; i < 30; i++) {
            this.cones.add(new ConeEffect());
        }

        switch (((AbstractUnlock) this.unlockBundle.get(0)).type) {
            case CARD:
                for (i = 0; i < this.unlockBundle.size(); i++) {
                    UnlockTracker.unlockCard(((AbstractUnlock) this.unlockBundle.get(i)).card.cardID);
                    AbstractDungeon.dynamicBanner.appearInstantly(TEXT[0]);
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.targetDrawScale = 1.0F;
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.drawScale = 0.01F;
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.current_x = Settings.WIDTH * 0.25F * (i + 1);
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.current_y = Settings.HEIGHT / 2.0F;
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.target_x = Settings.WIDTH * 0.25F * (i + 1);
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.target_y = Settings.HEIGHT / 2.0F
                            - 30.0F * Settings.scale;
                }
                break;
            case RELIC:
                for (i = 0; i < this.unlockBundle.size(); i++) {
                    UnlockTracker.hardUnlockOverride(((AbstractUnlock) this.unlockBundle.get(i)).relic.relicId);
                    UnlockTracker.markRelicAsSeen(((AbstractUnlock) this.unlockBundle.get(i)).relic.relicId);
                    ((AbstractUnlock) this.unlockBundle.get(i)).relic.loadLargeImg();
                    AbstractDungeon.dynamicBanner.appearInstantly(TEXT[1]);
                    ((AbstractUnlock) this.unlockBundle.get(i)).relic.currentX = Settings.WIDTH * 0.25F * (i + 1);
                    ((AbstractUnlock) this.unlockBundle.get(i)).relic.currentY = Settings.HEIGHT / 2.0F;
                    ((AbstractUnlock) this.unlockBundle.get(i)).relic.hb.move(
                            ((AbstractUnlock) this.unlockBundle.get(i)).relic.currentX,
                            ((AbstractUnlock) this.unlockBundle.get(i)).relic.currentY);
                }
                break;
            case CHARACTER:
                ((AbstractUnlock) this.unlockBundle.get(0)).onUnlockScreenOpen();
                AbstractDungeon.dynamicBanner.appearInstantly(TEXT[2]);
                break;
        }
    }

    public void reOpen() {
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.NEOW_UNLOCK;
        this.button.show();
        this.id = CardCrawlGame.sound.play("UNLOCK_SCREEN");

        this.cones.clear();
        int i;
        for (i = 0; i < 30; i++) {
            this.cones.add(new ConeEffect());
        }

        switch (((AbstractUnlock) this.unlockBundle.get(0)).type) {
            case CARD:
                for (i = 0; i < this.unlockBundle.size(); i++) {
                    UnlockTracker.unlockCard(((AbstractUnlock) this.unlockBundle.get(i)).card.cardID);
                    AbstractDungeon.dynamicBanner.appearInstantly(TEXT[0]);
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.targetDrawScale = 1.0F;
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.drawScale = 0.01F;
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.current_x = Settings.WIDTH * 0.25F * (i + 1);
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.current_y = Settings.HEIGHT / 2.0F;
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.target_x = Settings.WIDTH * 0.25F * (i + 1);
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.target_y = Settings.HEIGHT / 2.0F
                            - 30.0F * Settings.scale;
                }
                break;
            case RELIC:
                for (i = 0; i < this.unlockBundle.size(); i++) {
                    AbstractDungeon.dynamicBanner.appearInstantly(TEXT[1]);
                    ((AbstractUnlock) this.unlockBundle.get(i)).relic.currentX = Settings.WIDTH * 0.25F * (i + 1);
                    ((AbstractUnlock) this.unlockBundle.get(i)).relic.currentY = Settings.HEIGHT / 2.0F;
                    ((AbstractUnlock) this.unlockBundle.get(i)).relic.hb.move(
                            ((AbstractUnlock) this.unlockBundle.get(i)).relic.currentX,
                            ((AbstractUnlock) this.unlockBundle.get(i)).relic.currentY);
                }
                break;
            case CHARACTER:
                ((AbstractUnlock) this.unlockBundle.get(0)).onUnlockScreenOpen();
                AbstractDungeon.dynamicBanner.appearInstantly(TEXT[2]);
                break;
        }
    }

    public void update() {
        int i;
        this.shinyTimer -= Gdx.graphics.getDeltaTime();
        if (this.shinyTimer < 0.0F) {
            this.shinyTimer = 0.2F;
            AbstractDungeon.topLevelEffects.add(new RoomShineEffect());
            AbstractDungeon.topLevelEffects.add(new RoomShineEffect());
            AbstractDungeon.topLevelEffects.add(new RoomShineEffect2());
        }

        switch (((AbstractUnlock) this.unlockBundle.get(0)).type) {
            case CARD:
                updateConeEffect();
                for (i = 0; i < this.unlockBundle.size(); i++) {
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.update();
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.updateHoverLogic();
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.targetDrawScale = 1.0F;
                }
                break;
            case RELIC:
                updateConeEffect();
                for (i = 0; i < this.unlockBundle.size(); i++) {
                    ((AbstractUnlock) this.unlockBundle.get(i)).relic.update();
                }
                break;
            case CHARACTER:
                updateConeEffect();
                ((AbstractUnlock) this.unlockBundle.get(0)).player.update();
                break;
        }

        this.button.update();
    }

    private void updateConeEffect() {
        for (Iterator<ConeEffect> e = this.cones.iterator(); e.hasNext();) {
            ConeEffect d = e.next();
            d.update();
            if (d.isDone) {
                e.remove();
            }
        }

        if (this.cones.size() < 30)
            this.cones.add(new ConeEffect());
    }

    public void render(SpriteBatch sb) {
        int i;
        sb.setColor(new Color(0.05F, 0.15F, 0.18F, 1.0F));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);

        sb.setBlendFunction(770, 1);
        for (ConeEffect e : this.cones) {
            e.render(sb);
        }
        sb.setBlendFunction(770, 771);

        switch (((AbstractUnlock) this.unlockBundle.get(0)).type) {
            case CARD:
                for (i = 0; i < this.unlockBundle.size(); i++) {
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.renderHoverShadow(sb);
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.render(sb);
                    ((AbstractUnlock) this.unlockBundle.get(i)).card.renderCardTip(sb);
                }

                sb.setColor(new Color(0.0F, 0.0F, 0.0F, 0.5F));
                sb.draw(ImageMaster.UNLOCK_TEXT_BG, Settings.WIDTH / 2.0F - 500.0F,
                        Settings.HEIGHT / 2.0F - 330.0F * Settings.scale - 130.0F, 500.0F, 130.0F, 1000.0F, 260.0F,
                        Settings.scale * 1.2F, Settings.scale * 0.8F, 0.0F, 0, 0, 1000, 260, false, false);

                FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, TEXT[3], Settings.WIDTH / 2.0F,
                        Settings.HEIGHT / 2.0F - 330.0F * Settings.scale, Settings.CREAM_COLOR);
                break;

            case CHARACTER:
                ((AbstractUnlock) this.unlockBundle.get(0)).render(sb);
                ((AbstractUnlock) this.unlockBundle.get(0)).player.renderPlayerImage(sb);
                break;
            case RELIC:
                for (i = 0; i < this.unlockBundle.size(); i++) {
                    if (RelicLibrary.redList.contains(((AbstractUnlock) this.unlockBundle.get(i)).relic)) {
                        ((AbstractUnlock) this.unlockBundle.get(i)).relic.render(sb, false, Settings.RED_RELIC_COLOR);
                    } else if (RelicLibrary.greenList.contains(((AbstractUnlock) this.unlockBundle.get(i)).relic)) {
                        ((AbstractUnlock) this.unlockBundle.get(i)).relic.render(sb, false, Settings.GREEN_RELIC_COLOR);
                    } else if (RelicLibrary.blueList.contains(((AbstractUnlock) this.unlockBundle.get(i)).relic)) {
                        ((AbstractUnlock) this.unlockBundle.get(i)).relic.render(sb, false, Settings.BLUE_RELIC_COLOR);
                    } else if (RelicLibrary.whiteList.contains(((AbstractUnlock) this.unlockBundle.get(i)).relic)) {
                        ((AbstractUnlock) this.unlockBundle.get(i)).relic.render(sb, false,
                                Settings.PURPLE_RELIC_COLOR);
                    } else {
                        ((AbstractUnlock) this.unlockBundle.get(i)).relic.render(sb, false,
                                Settings.TWO_THIRDS_TRANSPARENT_BLACK_COLOR);
                    }

                    sb.setColor(new Color(0.0F, 0.0F, 0.0F, 0.5F));
                    sb.draw(ImageMaster.UNLOCK_TEXT_BG, Settings.WIDTH / 2.0F - 500.0F,
                            Settings.HEIGHT / 2.0F - 330.0F * Settings.scale - 130.0F, 500.0F, 130.0F, 1000.0F, 260.0F,
                            Settings.scale * 1.2F, Settings.scale * 0.8F, 0.0F, 0, 0, 1000, 260, false, false);

                    FontHelper.renderFontCentered(sb, FontHelper.losePowerFont,

                            ((AbstractUnlock) this.unlockBundle.get(i)).relic.name, Settings.WIDTH * 0.25F * (i + 1),
                            Settings.HEIGHT / 2.0F - 150.0F * Settings.scale, Settings.GOLD_COLOR, 1.2F);
                }

                FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, TEXT[3], Settings.WIDTH / 2.0F,
                        Settings.HEIGHT / 2.0F - 330.0F * Settings.scale, Settings.CREAM_COLOR);
                break;
        }

        this.button.render(sb);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\neow\
 * NeowUnlockScreen.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

