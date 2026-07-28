package com.megacrit.cardcrawl.helpers.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.megacrit.cardcrawl.core.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScrollInputProcessor
        implements InputProcessor {
    private static final Logger logger = LogManager.getLogger(ScrollInputProcessor.class.getName());
    public static String lastPressed = "";
    public static String lastPressed2 = "";
    public static boolean lastPressedSwitch = true;

    public static void logLastPressed(String msg) {
        lastPressedSwitch = !lastPressedSwitch;
        if (lastPressedSwitch) {
            lastPressed = msg;
        } else {
            lastPressed2 = msg;
        }

        if (Settings.isInfo) {
            logger.info(msg);
        }
    }

    public boolean keyDown(int keycode) {
        return false;
    }

    public boolean keyUp(int keycode) {
        return false;
    }

    public boolean keyTyped(char character) {
        return false;
    }

    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!Gdx.input.isButtonPressed(1)) {
            InputHelper.touchDown = true;
        }
        return false;
    }

    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        InputHelper.touchUp = true;
        return false;
    }

    public boolean touchDragged(int screenX, int screenY, int pointer) {
        InputHelper.isMouseDown = true;
        return false;
    }

    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    public boolean scrolled(int amount) {
        if (amount == -1) {
            InputHelper.scrolledUp = true;
        } else if (amount == 1) {
            InputHelper.scrolledDown = true;
        }
        return false;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\input\
 * ScrollInputProcessor.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

