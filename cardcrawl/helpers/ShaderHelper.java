package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class ShaderHelper {
    private static ShaderProgram gsShader;
    private static ShaderProgram rsShader;
    private static ShaderProgram wsShader;
    private static ShaderProgram blurShader;
    private static ShaderProgram waterShader;
    private static ShaderProgram outlineShader;

    public static void initializeShaders() {
        ShaderProgram.pedantic = false;

        gsShader = new ShaderProgram(Gdx.files.internal("shaders/grayscale/vertexShader.vs").readString(),
                Gdx.files.internal("shaders/grayscale/fragShader.fs").readString());
    }

    public static void setShader(SpriteBatch sb, Shader shader) {
        switch (shader) {
            case BLUR:
                sb.end();
                sb.setShader(blurShader);
                sb.begin();
                return;
            case DEFAULT:
                sb.end();
                sb.setShader(null);
                sb.begin();
                return;
            case GRAYSCALE:
                sb.end();
                sb.setShader(gsShader);
                sb.begin();
                return;
            case OUTLINE:
                sb.end();
                sb.setShader(outlineShader);
                sb.begin();
                return;
            case RED_SILHOUETTE:
                sb.end();
                sb.setShader(rsShader);
                sb.begin();
                return;
            case WATER:
                sb.end();
                sb.setShader(waterShader);
                sb.begin();
                return;
            case WHITE_SILHOUETTE:
                sb.end();
                sb.setShader(wsShader);
                sb.begin();
                return;
        }
        sb.end();
        sb.setShader(null);
        sb.begin();
    }

    public static void setShader(PolygonSpriteBatch sb, Shader shader) {
        switch (shader) {
            case BLUR:
                sb.setShader(blurShader);
                return;
            case DEFAULT:
                sb.setShader(null);
                return;
            case GRAYSCALE:
                sb.setShader(gsShader);
                return;
        }

        sb.setShader(null);
    }

    public enum Shader {
        BLUR, DEFAULT, GRAYSCALE, RED_SILHOUETTE, WHITE_SILHOUETTE, OUTLINE, WATER;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\
 * ShaderHelper.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

