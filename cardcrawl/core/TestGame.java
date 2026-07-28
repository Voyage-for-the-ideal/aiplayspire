package com.megacrit.cardcrawl.core;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class TestGame implements ApplicationListener {
    private OrthographicCamera camera = new OrthographicCamera();

    public static FitViewport viewport;
    private SpriteBatch sb;
    private Texture texture;

    public void create() {
        viewport = new FitViewport(1600.0F, 900.0F, (Camera) this.camera);
        viewport.apply();
        this.sb = new SpriteBatch();
        this.texture = new Texture("images/whiteScreen.png");
    }

    public void render() {
        this.sb.begin();
        this.sb.setColor(Color.RED);
        this.sb.draw(this.texture, 0.0F, 0.0F, 1600.0F, 900.0F);
        this.sb.end();
    }

    public void resize(int width, int height) {
        viewport.update(width, height);
        this.camera.update();
    }

    public void pause() {
    }

    public void resume() {
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\core\TestGame.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

