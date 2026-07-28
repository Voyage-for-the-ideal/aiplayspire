package com.megacrit.cardcrawl.cutscenes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class NeowEye {
    private static Texture lid1;
    private static Texture lid2;
    private static Texture lid3;
    private static Texture lid4;
    private int currentFrame = 0;
    private static Texture lid5;
    private static Texture lid6;
    private static Texture eyeImg;
    private Texture eyeLid;
    private float eyeLidTimer;
    private float leftX;
    private float rightX;
    private float y;
    private float oY;
    private float scale;
    private float angle;

    public NeowEye(int eyePosition) {
        if (lid1 == null) {
            lid1 = ImageMaster.loadImage("images/scenes/neow/lid1.png");
            lid2 = ImageMaster.loadImage("images/scenes/neow/lid2.png");
            lid3 = ImageMaster.loadImage("images/scenes/neow/lid3.png");
            lid4 = ImageMaster.loadImage("images/scenes/neow/lid4.png");
            lid5 = ImageMaster.loadImage("images/scenes/neow/lid5.png");
            lid6 = ImageMaster.loadImage("images/scenes/neow/lid6.png");
            eyeImg = ImageMaster.loadImage("images/scenes/neow/eye.png");
        }

        switch (eyePosition) {
            case 0:
                this.leftX = 1410.0F * Settings.xScale - 128.0F;
                this.rightX = 510.0F * Settings.xScale - 128.0F;
                this.y = Settings.HEIGHT / 2.0F - 180.0F * Settings.scale - 128.0F;
                this.oY = 20.0F * Settings.scale;
                this.scale = Settings.scale;
                this.angle = -10.0F;
                this.eyeLidTimer = 3.0F;
                break;
            case 1:
                this.leftX = 1610.0F * Settings.xScale - 128.0F;
                this.rightX = 310.0F * Settings.xScale - 128.0F;
                this.y = Settings.HEIGHT / 2.0F + 80.0F * Settings.scale - 128.0F;
                this.oY = 15.0F * Settings.scale;
                this.scale = 0.75F * Settings.scale;
                this.angle = 10.0F;
                this.eyeLidTimer = 3.15F;
                break;
            case 2:
                this.leftX = 1710.0F * Settings.xScale - 128.0F;
                this.rightX = 210.0F * Settings.xScale - 128.0F;
                this.y = Settings.HEIGHT / 2.0F + 290.0F * Settings.scale - 128.0F;
                this.oY = 10.0F * Settings.scale;
                this.scale = 0.5F * Settings.scale;
                this.angle = 15.0F;
                this.eyeLidTimer = 3.3F;
                break;
        }

        this.eyeLid = lid1;
    }

    public void update() {
        this.eyeLidTimer -= Gdx.graphics.getDeltaTime();
        if (this.eyeLidTimer < 0.0F) {
            this.currentFrame++;
            if (this.currentFrame > 9) {
                this.currentFrame = 0;
            }

            switch (this.currentFrame) {
                case 0:
                    this.eyeLid = lid1;
                    this.eyeLidTimer += 5.0F;
                    break;
                case 1:
                    this.eyeLid = lid2;
                    this.eyeLidTimer += 0.04F;
                    break;
                case 2:
                    this.eyeLid = lid3;
                    this.eyeLidTimer += 0.04F;
                    break;
                case 3:
                    this.eyeLid = lid4;
                    this.eyeLidTimer += 0.04F;
                    break;
                case 4:
                    this.eyeLid = lid5;
                    this.eyeLidTimer += 0.04F;
                    break;
                case 5:
                    this.eyeLid = lid6;
                    this.eyeLidTimer = 0.25F;
                    break;
                case 6:
                    this.eyeLid = lid5;
                    this.eyeLidTimer += 0.06F;
                    break;
                case 7:
                    this.eyeLid = lid4;
                    this.eyeLidTimer += 0.06F;
                    break;
                case 8:
                    this.eyeLid = lid3;
                    this.eyeLidTimer += 0.06F;
                    break;
                case 9:
                    this.eyeLid = lid2;
                    this.eyeLidTimer += 0.06F;
                    break;
            }
        }
    }

    public void renderRight(SpriteBatch sb) {
        sb.draw(eyeImg, this.leftX, this.y +

                MathUtils.cosDeg((float) (System.currentTimeMillis() / 12L % 360L)) * this.oY, 128.0F, 128.0F, 256.0F,
                256.0F, this.scale, this.scale, this.angle, 0, 0, 256, 256, false, false);

        sb.draw(this.eyeLid, this.leftX, this.y +

                MathUtils.cosDeg((float) (System.currentTimeMillis() / 12L % 360L)) * this.oY, 128.0F, 128.0F, 256.0F,
                256.0F, this.scale, this.scale, this.angle, 0, 0, 256, 256, false, false);
    }

    public void renderLeft(SpriteBatch sb) {
        sb.draw(eyeImg, this.rightX, this.y +

                MathUtils.cosDeg((float) (System.currentTimeMillis() / 12L % 360L)) * this.oY, 128.0F, 128.0F, 256.0F,
                256.0F, this.scale, this.scale, -this.angle, 0, 0, 256, 256, true, false);

        sb.draw(this.eyeLid, this.rightX, this.y +

                MathUtils.cosDeg((float) (System.currentTimeMillis() / 12L % 360L)) * this.oY, 128.0F, 128.0F, 256.0F,
                256.0F, this.scale, this.scale, -this.angle, 0, 0, 256, 256, true, false);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cutscenes\NeowEye.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

