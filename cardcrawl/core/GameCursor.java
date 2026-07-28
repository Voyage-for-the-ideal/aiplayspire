package com.megacrit.cardcrawl.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class GameCursor {
    private Texture img;
    private Texture mImg;
    private static final float OFFSET_X = 24.0F * Settings.scale;
    public static final int W = 64;
    public static boolean hidden = false;
    private float rotation;
    private static final float OFFSET_Y = -24.0F * Settings.scale;
    private static final float SHADOW_OFFSET_X = -10.0F * Settings.scale;
    private static final float SHADOW_OFFSET_Y = 8.0F * Settings.scale;
    private static final Color SHADOW_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.15F);
    private static final float TILT_ANGLE = 6.0F;
    private CursorType type = CursorType.NORMAL;
    public Color color = new Color(1.0F, 1.0F, 1.0F, 0.0F);

    public enum CursorType {
        NORMAL, INSPECT;
    }

    public GameCursor() {
        this.img = ImageMaster.loadImage("images/ui/cursors/gold2.png");
        this.mImg = ImageMaster.loadImage("images/ui/cursors/magGlass2.png");
    }

    public void update() {
        if (InputHelper.isMouseDown) {
            this.rotation = 6.0F;
        } else {
            this.rotation = 0.0F;
        }
    }

    public void changeType(CursorType type) {
        this.type = type;
    }

    public void render(SpriteBatch sb) {
        if (hidden || Settings.isControllerMode) {
            return;
        }

        if (!Settings.isTouchScreen || Settings.isDev) {
            switch (this.type) {
                case NORMAL:
                    sb.setColor(SHADOW_COLOR);
                    sb.draw(this.img, InputHelper.mX - 32.0F - SHADOW_OFFSET_X + OFFSET_X,
                            InputHelper.mY - 32.0F - SHADOW_OFFSET_Y + OFFSET_Y, 32.0F, 32.0F, 64.0F, 64.0F,
                            Settings.scale, Settings.scale, this.rotation, 0, 0, 64, 64, false, false);

                    sb.setColor(Color.WHITE);
                    sb.draw(this.img, InputHelper.mX - 32.0F + OFFSET_X, InputHelper.mY - 32.0F + OFFSET_Y, 32.0F,
                            32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, this.rotation, 0, 0, 64, 64, false,
                            false);
                    break;

                case INSPECT:
                    sb.setColor(SHADOW_COLOR);
                    sb.draw(this.mImg, InputHelper.mX - 32.0F - SHADOW_OFFSET_X + OFFSET_X,
                            InputHelper.mY - 32.0F - SHADOW_OFFSET_Y + OFFSET_Y, 32.0F, 32.0F, 64.0F, 64.0F,
                            Settings.scale, Settings.scale, this.rotation, 0, 0, 64, 64, false, false);

                    sb.setColor(Color.WHITE);
                    sb.draw(this.mImg, InputHelper.mX - 32.0F + OFFSET_X, InputHelper.mY - 32.0F + OFFSET_Y, 32.0F,
                            32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, this.rotation, 0, 0, 64, 64, false,
                            false);
                    break;
            }

            changeType(CursorType.NORMAL);
        } else {
            this.color.a = MathHelper.slowColorLerpSnap(this.color.a, 0.0F);
            sb.setColor(this.color);
            sb.draw(ImageMaster.WOBBLY_ORB_VFX, InputHelper.mX - 16.0F + OFFSET_X - 24.0F * Settings.scale,
                    InputHelper.mY - 16.0F + OFFSET_Y + 24.0F * Settings.scale, 16.0F, 16.0F, 32.0F, 32.0F,
                    Settings.scale, Settings.scale, this.rotation, 0, 0, 32, 32, false, false);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\core\GameCursor.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

