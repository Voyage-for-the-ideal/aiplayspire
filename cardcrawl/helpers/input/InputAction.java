package com.megacrit.cardcrawl.helpers.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.UIStrings;

import java.util.HashMap;
import java.util.Map;

public class InputAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("InputKeyNames");
    public static final Map<String, String> TEXT_CONVERSIONS = uiStrings.TEXT_DICT;

    private int keycode;
    private static final HashMap<Integer, Integer> equivalentKeys = new HashMap<>();

    static {
        equivalentKeys.put(Integer.valueOf(7), Integer.valueOf(144));
        equivalentKeys.put(Integer.valueOf(8), Integer.valueOf(145));
        equivalentKeys.put(Integer.valueOf(9), Integer.valueOf(146));
        equivalentKeys.put(Integer.valueOf(10), Integer.valueOf(147));
        equivalentKeys.put(Integer.valueOf(11), Integer.valueOf(148));
        equivalentKeys.put(Integer.valueOf(12), Integer.valueOf(149));
        equivalentKeys.put(Integer.valueOf(13), Integer.valueOf(150));
        equivalentKeys.put(Integer.valueOf(14), Integer.valueOf(151));
        equivalentKeys.put(Integer.valueOf(15), Integer.valueOf(152));
        equivalentKeys.put(Integer.valueOf(16), Integer.valueOf(153));

        equivalentKeys.put(Integer.valueOf(144), Integer.valueOf(7));
        equivalentKeys.put(Integer.valueOf(145), Integer.valueOf(8));
        equivalentKeys.put(Integer.valueOf(146), Integer.valueOf(9));
        equivalentKeys.put(Integer.valueOf(147), Integer.valueOf(10));
        equivalentKeys.put(Integer.valueOf(148), Integer.valueOf(11));
        equivalentKeys.put(Integer.valueOf(149), Integer.valueOf(12));
        equivalentKeys.put(Integer.valueOf(150), Integer.valueOf(13));
        equivalentKeys.put(Integer.valueOf(151), Integer.valueOf(14));
        equivalentKeys.put(Integer.valueOf(152), Integer.valueOf(15));
        equivalentKeys.put(Integer.valueOf(153), Integer.valueOf(16));
    }

    public InputAction(int keycode) {
        this.keycode = keycode;
    }

    public int getKey() {
        return this.keycode;
    }

    public String getKeyString() {
        String keycodeStr = Input.Keys.toString(this.keycode);
        return TEXT_CONVERSIONS.getOrDefault(keycodeStr, keycodeStr);
    }

    public boolean isJustPressed() {
        boolean alternatePressed = (equivalentKeys.containsKey(Integer.valueOf(this.keycode))
                && Gdx.input.isKeyJustPressed(((Integer) equivalentKeys
                        .get(Integer.valueOf(this.keycode))).intValue()));
        return (alternatePressed || Gdx.input.isKeyJustPressed(this.keycode));
    }

    public boolean isPressed() {
        boolean alternatePressed = (equivalentKeys.containsKey(Integer.valueOf(this.keycode))
                && Gdx.input.isKeyPressed(((Integer) equivalentKeys
                        .get(Integer.valueOf(this.keycode))).intValue()));
        return (alternatePressed || Gdx.input.isKeyPressed(this.keycode));
    }

    public void remap(int newKeycode) {
        this.keycode = newKeycode;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\input\
 * InputAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

