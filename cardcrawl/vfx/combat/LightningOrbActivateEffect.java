package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

import java.util.ArrayList;

public class LightningOrbActivateEffect
        extends AbstractGameEffect {
    private static ArrayList<TextureAtlas.AtlasRegion> regions = null;
    private TextureAtlas.AtlasRegion img = null;
    private int index = 0;
    private float x;

    public LightningOrbActivateEffect(float x, float y) {
        this.renderBehind = false;
        this.color = Settings.LIGHT_YELLOW_COLOR.cpy();

        if (regions == null) {
            regions = new ArrayList<>();
            regions.add(ImageMaster.vfxAtlas.findRegion("combat/defect/l_orb1"));
            regions.add(ImageMaster.vfxAtlas.findRegion("combat/defect/l_orb2"));
            regions.add(ImageMaster.vfxAtlas.findRegion("combat/defect/l_orb3"));
            regions.add(ImageMaster.vfxAtlas.findRegion("combat/defect/l_orb4"));
            regions.add(ImageMaster.vfxAtlas.findRegion("combat/defect/l_orb5"));
            regions.add(ImageMaster.vfxAtlas.findRegion("combat/defect/l_orb6"));
        }

        this.img = regions.get(this.index);
        this.x = x - this.img.packedWidth / 2.0F;
        this.y = y - this.img.packedHeight / 2.0F;
        this.scale = 2.0F * Settings.scale;
        this.duration = 0.03F;
    }
    private float y;

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.index++;
            if (this.index > regions.size() - 1) {
                this.isDone = true;
                return;
            }
            this.img = regions.get(this.index);

            this.duration = 0.03F;
        }
        this.color.a -= Gdx.graphics.getDeltaTime() * 2.0F;
        if (this.color.a < 0.0F) {
            this.color.a = 0.0F;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * LightningOrbActivateEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */
