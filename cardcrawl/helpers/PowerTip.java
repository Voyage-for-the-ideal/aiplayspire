package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public class PowerTip {
    public Texture img;
    public TextureAtlas.AtlasRegion imgRegion;
    public String header;
    public String body;

    public PowerTip() {
    }

    public PowerTip(String header, String body) {
        this.header = header;
        this.body = body;
        this.img = null;
        this.imgRegion = null;
    }

    public PowerTip(String header, String body, Texture img) {
        this.header = header;
        this.body = body;
        this.img = img;
        this.imgRegion = null;
    }

    public PowerTip(String header, String body, TextureAtlas.AtlasRegion region48) {
        this.header = header;
        this.body = body;
        this.imgRegion = region48;
        this.img = null;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\PowerTip.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

