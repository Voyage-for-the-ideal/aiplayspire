package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;

public class Sprite
        extends AbstractDrawable {
    private Texture img;
    private String label;
    private Color color;
    private float x;
    private float y;
    private float scale;
    private float rotation;
    private boolean text;

    public Sprite(Texture img, float x, float y, int z, float scale, float rotation, Color color) {
        this.img = img;
        this.x = x;
        this.y = y;
        this.z = z;
        this.scale = scale;
        this.rotation = rotation;
        this.color = color;
        this.text = false;
    }

    public Sprite(Texture img, float x, float y, int z, float scale, Color color) {
        this(img, x, y, z, Settings.scale, 0.0F, color);
    }

    public Sprite(Texture img, float x, float y, int z) {
        this(img, x, y, z, Settings.scale, null);
    }

    public Sprite(Texture img, float x, float y, int z, Color color) {
        this(img, x, y, z, Settings.scale, color);
    }

    public Sprite(String label, float x, float y, int z, Color color) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.text = true;
    }

    public void render(SpriteBatch sb) {
        if (!this.text) {
            int w = this.img.getWidth();
            int h = this.img.getHeight();

            if (this.color != null) {
                sb.setColor(this.color);
            }

            if (isVisible()) {
                sb.draw(this.img, this.x - w / 2.0F, this.y - h / 2.0F, w / 2.0F, h / 2.0F, w, h, this.scale,
                        this.scale, this.rotation, 0, 0, w, h, false, false);

            }

        } else {

            FontHelper.renderFontCentered(sb, FontHelper.panelEndTurnFont, this.label, this.x, this.y, this.color);
        }
    }

    private boolean isVisible() {
        return true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\Sprite.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

