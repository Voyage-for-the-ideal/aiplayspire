package com.megacrit.cardcrawl.screens.options;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GiantToggleButton {
    private static final Logger logger = LogManager.getLogger(GiantToggleButton.class.getName());
    public boolean ticked = false;
    public ToggleType type;
    private String label;
    Hitbox hb = new Hitbox(320.0F * Settings.scale, 72.0F * Settings.scale);
    private float x;
    private float scale = Settings.scale;
    private float y;

    public enum ToggleType {
        CONTROLLER_ENABLED, TOUCHSCREEN_ENABLED;
    }

    public GiantToggleButton(ToggleType type, float x, float y, String label) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.label = label;
        this.hb.move(x + 110.0F * Settings.scale, y);
        initialize();
    }

    private void initialize() {
        switch (this.type) {
            case CONTROLLER_ENABLED:
                this.ticked = Settings.gamePref.getBoolean("Controller Enabled", true);
                return;
            case TOUCHSCREEN_ENABLED:
                this.ticked = Settings.gamePref.getBoolean("Touchscreen Enabled", false);
                return;
        }
        logger.info(this.type.name() + " not found (initialize())");
    }

    public void update() {
        this.hb.update();
        if (this.hb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        }
        if (this.hb.hovered && InputHelper.justClickedLeft) {
            this.hb.clickStarted = true;
            CardCrawlGame.sound.play("UI_CLICK_1");
        } else if (this.hb.clicked || (this.hb.hovered && CInputActionSet.select.isJustPressed())) {
            CInputActionSet.select.unpress();
            this.hb.clicked = false;
            this.ticked = !this.ticked;
            useEffect();
        }

        if (this.hb.hovered) {
            this.scale = Settings.scale * 1.125F;
        } else {
            this.scale = Settings.scale;
        }
    }

    private void useEffect() {
        switch (this.type) {
            case CONTROLLER_ENABLED:
                Settings.gamePref.putBoolean("Controller Enabled", this.ticked);
                Settings.gamePref.flush();
                Settings.CONTROLLER_ENABLED = this.ticked;
                return;
            case TOUCHSCREEN_ENABLED:
                Settings.gamePref.putBoolean("Touchscreen Enabled", this.ticked);
                Settings.gamePref.flush();
                Settings.TOUCHSCREEN_ENABLED = this.ticked;
                Settings.isTouchScreen = this.ticked;
                return;
        }
        logger.info(this.type.name() + " not found (useEffect())");
    }

    public void render(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.CHECKBOX, this.x - 32.0F, this.y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                this.scale, 0.0F, 0, 0, 64, 64, false, false);
        if (this.ticked) {
            sb.draw(ImageMaster.TICK, this.x - 32.0F, this.y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                    this.scale, 0.0F, 0, 0, 64, 64, false, false);
        }

        if (this.hb.hovered) {
            FontHelper.renderFontLeft(sb, FontHelper.panelEndTurnFont, this.label, this.x + 40.0F * Settings.scale,
                    this.y, Settings.GREEN_TEXT_COLOR);

        } else {

            FontHelper.renderFontLeft(sb, FontHelper.panelEndTurnFont, this.label, this.x + 40.0F * Settings.scale,
                    this.y, Settings.CREAM_COLOR);
        }

        this.hb.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\options\
 * GiantToggleButton.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

