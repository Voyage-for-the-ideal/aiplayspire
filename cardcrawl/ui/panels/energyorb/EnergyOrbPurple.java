package com.megacrit.cardcrawl.ui.panels.energyorb;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class EnergyOrbPurple
        implements EnergyOrbInterface {
    private static final int ORB_W = 128;
    public static float fontScale = 1.0F;
    private static final float ORB_IMG_SCALE = 1.15F * Settings.scale;
    private float angle4;

    public void updateOrb(int orbCount) {
        if (orbCount == 0) {

            this.angle4 += Gdx.graphics.getDeltaTime() * 5.0F;
            this.angle3 += Gdx.graphics.getDeltaTime() * -8.0F;
            this.angle2 += Gdx.graphics.getDeltaTime() * 8.0F;
        } else {

            this.angle4 += Gdx.graphics.getDeltaTime() * 20.0F;
            this.angle3 += Gdx.graphics.getDeltaTime() * -40.0F;
            this.angle2 += Gdx.graphics.getDeltaTime() * 40.0F;
        }
    }
    private float angle3;
    private float angle2;
    public void renderOrb(SpriteBatch sb, boolean enabled, float current_x, float current_y) {
        if (enabled) {
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.ENERGY_PURPLE_LAYER1, current_x - 64.0F, current_y - 64.0F, 64.0F, 64.0F, 128.0F,
                    128.0F, ORB_IMG_SCALE, ORB_IMG_SCALE, 0.0F, 0, 0, 128, 128, false, false);

            sb.draw(ImageMaster.ENERGY_PURPLE_LAYER2, current_x - 64.0F, current_y - 64.0F, 64.0F, 64.0F, 128.0F,
                    128.0F, ORB_IMG_SCALE, ORB_IMG_SCALE, this.angle2, 0, 0, 128, 128, false, false);

            sb.draw(ImageMaster.ENERGY_PURPLE_LAYER3, current_x - 64.0F, current_y - 64.0F, 64.0F, 64.0F, 128.0F,
                    128.0F, ORB_IMG_SCALE, ORB_IMG_SCALE, this.angle3, 0, 0, 128, 128, false, false);

            sb.draw(ImageMaster.ENERGY_PURPLE_LAYER4, current_x - 64.0F, current_y - 64.0F, 64.0F, 64.0F, 128.0F,
                    128.0F, ORB_IMG_SCALE, ORB_IMG_SCALE, this.angle4, 0, 0, 128, 128, false, false);

        } else {

            sb.setColor(Color.GRAY);
            sb.draw(ImageMaster.ENERGY_PURPLE_LAYER1, current_x - 64.0F, current_y - 64.0F, 64.0F, 64.0F, 128.0F,
                    128.0F, ORB_IMG_SCALE, ORB_IMG_SCALE, 0.0F, 0, 0, 128, 128, false, false);
        }

        sb.setColor(Color.WHITE);

        sb.draw(ImageMaster.ENERGY_PURPLE_BORDER, current_x - 64.0F, current_y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F,
                ORB_IMG_SCALE, ORB_IMG_SCALE, 0.0F, 0, 0, 128, 128, false, false);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\panels\
 * energyorb\EnergyOrbPurple.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */
