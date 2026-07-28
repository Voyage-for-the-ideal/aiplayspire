package com.megacrit.cardcrawl.scenes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.vfx.scene.LogoFlameEffect;
import com.megacrit.cardcrawl.vfx.scene.TitleDustEffect;

import java.util.ArrayList;
import java.util.Iterator;

public class TitleBackground {
    protected static TextureAtlas atlas;
    protected final TextureAtlas.AtlasRegion mg3Bot;
    protected final TextureAtlas.AtlasRegion mg3Top;
    protected final TextureAtlas.AtlasRegion topGlow;
    protected final TextureAtlas.AtlasRegion topGlow2;
    protected final TextureAtlas.AtlasRegion botGlow;
    protected final TextureAtlas.AtlasRegion sky;
    private static Texture titleLogoImg = null;
    private static int W = 0;
    private static int H = 0;

    protected ArrayList<TitleCloud> topClouds = new ArrayList<>();
    protected ArrayList<TitleCloud> midClouds = new ArrayList<>();
    public float slider = 1.0F;
    private float timer = 1.0F;

    public boolean activated = false;

    private ArrayList<TitleDustEffect> dust = new ArrayList<>();
    private ArrayList<TitleDustEffect> dust2 = new ArrayList<>();
    private ArrayList<LogoFlameEffect> flame = new ArrayList<>();
    private float dustTimer = 2.0F;
    private float flameTimer = 0.2F;
    private static final float FLAME_INTERVAL = 0.05F;
    private float logoAlpha = 1.0F;

    private Color promptTextColor = Settings.CREAM_COLOR.cpy();

    public TitleBackground() {
        this.promptTextColor.a = 0.0F;
        if (atlas == null) {
            atlas = new TextureAtlas(Gdx.files.internal("title/title.atlas"));
        }
        this.sky = atlas.findRegion("jpg/sky");
        this.mg3Bot = atlas.findRegion("mg3Bot");
        this.mg3Top = atlas.findRegion("mg3Top");
        this.topGlow = atlas.findRegion("mg3TopGlow1");
        this.topGlow2 = atlas.findRegion("mg3TopGlow2");
        this.botGlow = atlas.findRegion("mg3BotGlow");
        int i;
        for (i = 1; i < 7; i++) {
            this.topClouds.add(new TitleCloud(atlas

                    .findRegion("topCloud" + Integer.toString(i)),
                    MathUtils.random(10.0F, 50.0F) * Settings.scale,
                    MathUtils.random(-1920.0F, 1920.0F) * Settings.scale));
        }

        for (i = 1; i < 13; i++) {
            this.midClouds.add(new TitleCloud(atlas

                    .findRegion("midCloud" + Integer.toString(i)),
                    MathUtils.random(-50.0F, -10.0F) * Settings.scale,
                    MathUtils.random(-1920.0F, 1920.0F) * Settings.scale));
        }

        if (titleLogoImg == null) {
            switch (Settings.language) {

            }

            titleLogoImg = ImageMaster.loadImage("images/ui/title_logo/eng.png");

            W = titleLogoImg.getWidth();
            H = titleLogoImg.getHeight();
        }
    }

    public void slideDownInstantly() {
        this.activated = true;
        this.timer = 0.0F;
        this.slider = 0.0F;
    }

    public void update() {
        if (CardCrawlGame.mainMenuScreen.darken) {
            this.logoAlpha = MathHelper.slowColorLerpSnap(this.logoAlpha, 0.25F);
        } else {
            this.logoAlpha = MathHelper.slowColorLerpSnap(this.logoAlpha, 1.0F);
        }

        if (InputHelper.justClickedLeft &&
                !this.activated) {
            this.activated = true;
            this.timer = 1.0F;
        }

        if (this.activated &&
                this.timer != 0.0F) {
            this.timer -= Gdx.graphics.getDeltaTime();
            if (this.timer < 0.0F) {
                this.timer = 0.0F;
            }
            if (this.timer < 1.0F) {
                this.slider = Interpolation.pow4In.apply(0.0F, 1.0F, this.timer);
            }
        }

        for (TitleCloud c : this.topClouds) {
            c.update();
        }
        for (TitleCloud c : this.midClouds) {
            c.update();
        }

        if (!Settings.DISABLE_EFFECTS) {
            updateDust();
        }

        if (!CardCrawlGame.mainMenuScreen.isFadingOut) {
            updateFlame();
        } else {
            this.flame.clear();
        }
    }

    private void updateFlame() {
        this.flameTimer -= Gdx.graphics.getDeltaTime();
        if (this.flameTimer < 0.0F) {
            this.flameTimer = 0.05F;
            this.flame.add(new LogoFlameEffect());
        }

        for (Iterator<LogoFlameEffect> e = this.flame.iterator(); e.hasNext();) {
            LogoFlameEffect effect = e.next();
            effect.update();
            if (effect.isDone) {
                e.remove();
            }
        }
    }

    private void updateDust() {
        this.dustTimer -= Gdx.graphics.getDeltaTime();
        if (this.dustTimer < 0.0F) {
            this.dustTimer = 0.05F;
            this.dust.add(new TitleDustEffect());
        }

        for (Iterator<TitleDustEffect> e = this.dust.iterator(); e.hasNext();) {
            TitleDustEffect effect = e.next();
            effect.update();
            if (effect.isDone) {
                e.remove();
            }
        }
    }

    public void render(SpriteBatch sb) {
        renderRegion(sb, this.sky, 0.0F, -100.0F * Settings.scale * this.slider);

        renderRegion(sb, this.mg3Bot, 0.0F,

                MathUtils.round(-45.0F * Settings.scale * this.slider + Settings.HEIGHT - 2219.0F * Settings.scale));

        renderRegion(sb, this.mg3Top, 0.0F,

                MathUtils.round(-45.0F * Settings.scale * this.slider + Settings.HEIGHT - 1080.0F * Settings.scale));

        sb.setBlendFunction(770, 1);
        sb.setColor(new Color(1.0F, 0.2F, 0.1F,
                0.1F + (MathUtils.cosDeg((float) (System.currentTimeMillis() / 16L % 360L)) + 1.25F) / 5.0F));
        renderRegion(sb, this.botGlow, 0.0F,

                MathUtils.round(-45.0F * Settings.scale * this.slider + Settings.HEIGHT - 2220.0F * Settings.scale));
        renderRegion(sb, this.topGlow, 0.0F,
                -45.0F * Settings.scale * this.slider + Settings.HEIGHT - 1080.0F * Settings.scale);
        renderRegion(sb, this.topGlow2, 0.0F,
                -45.0F * Settings.scale * this.slider + Settings.HEIGHT - 1080.0F * Settings.scale);
        sb.setColor(Color.WHITE);
        sb.setBlendFunction(770, 771);

        for (TitleDustEffect e : this.dust2) {
            e.render(sb, 0.0F, -50.0F * Settings.scale * this.slider + Settings.HEIGHT - 1300.0F * Settings.scale);
        }
        for (TitleDustEffect e : this.dust) {
            e.render(sb, 0.0F, -50.0F * Settings.scale * this.slider + Settings.HEIGHT - 1300.0F * Settings.scale);
        }
        sb.setColor(Color.WHITE);

        for (TitleCloud c : this.midClouds) {
            c.render(sb, this.slider);
        }
        for (TitleCloud c : this.topClouds) {
            c.render(sb, this.slider);
        }

        sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.logoAlpha));
        sb.draw(titleLogoImg, 930.0F * Settings.xScale - W / 2.0F,
                -70.0F * Settings.scale * this.slider + Settings.HEIGHT / 2.0F - H / 2.0F + 14.0F * Settings.scale,
                W / 2.0F, H / 2.0F, W, H, Settings.scale, Settings.scale, 0.0F, 0, 0, W, H, false, false);

        sb.setBlendFunction(770, 1);

        for (LogoFlameEffect e : this.flame) {
            switch (Settings.language) {

            }

            e.render(sb, Settings.WIDTH / 2.0F,
                    -70.0F * Settings.scale * this.slider + Settings.HEIGHT / 2.0F - 260.0F * Settings.scale);
        }

        sb.setBlendFunction(770, 771);
    }

    private void renderRegion(SpriteBatch sb, TextureAtlas.AtlasRegion region, float x, float y) {
        if (Settings.isLetterbox) {
            sb.draw(region
                    .getTexture(), region.offsetX * Settings.scale + x, region.offsetY * Settings.scale + y, 0.0F, 0.0F,
                    region.packedWidth, region.packedHeight, Settings.xScale, Settings.xScale, 0.0F, region

                            .getRegionX(),
                    region
                            .getRegionY(),
                    region
                            .getRegionWidth(),
                    region
                            .getRegionHeight(),
                    false, false);
        } else {

            sb.draw(region
                    .getTexture(), region.offsetX * Settings.scale + x, region.offsetY * Settings.scale + y, 0.0F, 0.0F,
                    region.packedWidth, region.packedHeight, Settings.scale, Settings.scale, 0.0F, region

                            .getRegionX(),
                    region
                            .getRegionY(),
                    region
                            .getRegionWidth(),
                    region
                            .getRegionHeight(),
                    false, false);
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\scenes\
 * TitleBackground.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

