package com.megacrit.cardcrawl.screens.custom;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.RunModStrings;

import java.util.HashSet;

public class CustomMod {
    public String ID;
    public String name;
    public String description;
    public String color;
    private String label;
    public boolean isDailyMod;
    public boolean selected = false;
    public Hitbox hb;
    private static float offset_x = 0.0F;
    private static float text_max_width;
    private static final float OFFSET_Y = 130.0F * Settings.scale;
    private static float line_spacing;
    public float height;
    private HashSet<CustomMod> mutuallyExclusive;

    public CustomMod(String setID, String color, boolean isDailyMod) {
        if (offset_x == 0.0F) {
            offset_x = CustomModeScreen.screenX + 120.0F * Settings.scale;
            line_spacing = Settings.BIG_TEXT_MODE ? (40.0F * Settings.scale) : (32.0F * Settings.scale);
            text_max_width = Settings.isMobile ? (1170.0F * Settings.scale) : (1050.0F * Settings.scale);
        }

        this.color = color;
        this.ID = setID;
        RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString(setID);
        this.name = modStrings.NAME;
        this.description = modStrings.DESCRIPTION;
        this.hb = new Hitbox(text_max_width, 70.0F * Settings.scale);
        this.isDailyMod = isDailyMod;
        this.label = FontHelper.colorString("[" + this.name + "]", color) + " " + this.description;

        this.height = -FontHelper.getSmartHeight(FontHelper.charDescFont, this.label, text_max_width, line_spacing)
                + 70.0F * Settings.scale;
    }

    public void update(float y) {
        this.hb.update();
        this.hb.move(offset_x + (text_max_width - 80.0F * Settings.scale) / 2.0F, y + OFFSET_Y);

        if (this.hb.justHovered) {
            playHoverSound();
        }

        if (this.hb.hovered && InputHelper.justClickedLeft) {
            this.hb.clickStarted = true;
        }

        if (this.hb.clicked) {
            this.hb.clicked = false;
            this.selected = !this.selected;
            playClickSound();
            if (this.selected) {
                disableMutuallyExclusiveMods();
            }
        }
    }

    public void render(SpriteBatch sb) {
        float scale = Settings.isMobile ? (Settings.scale * 1.2F) : Settings.scale;

        if (this.hb.hovered) {
            sb.draw(ImageMaster.CHECKBOX, offset_x - 32.0F, this.hb.cY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                    scale * 1.2F, scale * 1.2F, 0.0F, 0, 0, 64, 64, false, false);

            sb.setColor(Color.GOLD);
            sb.setBlendFunction(770, 1);
            sb.draw(ImageMaster.CHECKBOX, offset_x - 32.0F, this.hb.cY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                    scale * 1.2F, scale * 1.2F, 0.0F, 0, 0, 64, 64, false, false);

            sb.setBlendFunction(770, 771);
            sb.setColor(Color.WHITE);
        } else {
            sb.draw(ImageMaster.CHECKBOX, offset_x - 32.0F, this.hb.cY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, scale,
                    scale, 0.0F, 0, 0, 64, 64, false, false);
        }

        if (this.selected) {
            sb.draw(ImageMaster.TICK, offset_x - 32.0F, this.hb.cY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, scale, scale,
                    0.0F, 0, 0, 64, 64, false, false);
        }

        if (this.hb.hovered) {
            FontHelper.renderSmartText(sb, FontHelper.charDescFont, this.label, offset_x + 46.0F * Settings.scale,
                    this.hb.cY + 12.0F * Settings.scale, text_max_width, line_spacing, Settings.LIGHT_YELLOW_COLOR);

        } else {

            FontHelper.renderSmartText(sb, FontHelper.charDescFont, this.label, offset_x + 40.0F * Settings.scale,
                    this.hb.cY + 12.0F * Settings.scale, text_max_width, line_spacing, Settings.CREAM_COLOR);
        }

        this.hb.render(sb);
    }

    private void playClickSound() {
        CardCrawlGame.sound.playA("UI_CLICK_1", -0.1F);
    }

    private void playHoverSound() {
        CardCrawlGame.sound.playV("UI_HOVER", 0.75F);
    }

    public void setMutualExclusionPair(CustomMod otherMod) {
        setMutualExclusion(otherMod);
        otherMod.setMutualExclusion(this);
    }

    private void setMutualExclusion(CustomMod otherMod) {
        if (this.mutuallyExclusive == null) {
            this.mutuallyExclusive = new HashSet<>();
        }
        this.mutuallyExclusive.add(otherMod);
    }

    private void disableMutuallyExclusiveMods() {
        if (this.mutuallyExclusive != null)
            for (CustomMod mods : this.mutuallyExclusive)
                mods.selected = false;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\custom\
 * CustomMod.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

