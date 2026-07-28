package com.megacrit.cardcrawl.desktop;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;

public class MyTexturePacker {
    public static void main(String[] arg) {
        packTextures();
    }

    private static void packTextures() {
        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.maxWidth = 2048;
        settings.maxHeight = 2048;

        System.out.println("Done!");
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\desktop\
 * MyTexturePacker.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

