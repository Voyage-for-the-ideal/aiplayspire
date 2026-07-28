package com.megacrit.cardcrawl.helpers.steamInput;

import com.codedisaster.steamworks.SteamController;
import com.megacrit.cardcrawl.helpers.controller.CInputAction;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;

public class SteamInputActionSet {
    public static SteamInputAction select;
    public static SteamInputAction cancel;
    public static SteamInputAction topPanel;
    public static SteamInputAction proceed;
    public static SteamInputAction peek;
    public static SteamInputAction pageLeftViewDeck;
    public static SteamInputAction pageRightViewExhaust;
    public static SteamInputAction map;
    public static SteamInputAction settings;
    public static SteamInputAction drawPile;
    public static SteamInputAction discardPile;
    public static SteamInputAction up;

    public static void load(SteamController setController) {
        controller = setController;

        controller.activateActionSet(SteamInputHelper.controllerHandles[0], controller

                .getActionSetHandle("AllControls"));

        select = SetAction("select", CInputActionSet.select);
        cancel = SetAction("cancel", CInputActionSet.cancel);
        topPanel = SetAction("top_panel", CInputActionSet.topPanel);
        proceed = SetAction("proceed", CInputActionSet.proceed);

        peek = SetAction("peek", CInputActionSet.peek);
        pageLeftViewDeck = SetAction("page_left", CInputActionSet.pageLeftViewDeck);
        pageRightViewExhaust = SetAction("page_right", CInputActionSet.pageRightViewExhaust);
        map = SetAction("map", CInputActionSet.map);
        settings = SetAction("settings", CInputActionSet.settings);

        up = SetAction("up", CInputActionSet.up);
        down = SetAction("down", CInputActionSet.down);
        left = SetAction("left", CInputActionSet.left);
        right = SetAction("right", CInputActionSet.right);

        inspectUp = SetAction("scroll_up", CInputActionSet.inspectUp);
        inspectDown = SetAction("scroll_down", CInputActionSet.inspectDown);
        inspectLeft = SetAction("scroll_left", CInputActionSet.inspectLeft);
        inspectRight = SetAction("scroll_right", CInputActionSet.inspectRight);

        altUp = SetAction("alt_up", CInputActionSet.altUp);
        altDown = SetAction("alt_down", CInputActionSet.altDown);
        altLeft = SetAction("alt_left", CInputActionSet.altLeft);
        altRight = SetAction("alt_right", CInputActionSet.altRight);

        drawPile = SetAction("draw_pile", CInputActionSet.drawPile);
        discardPile = SetAction("discard_pile", CInputActionSet.discardPile);

        SteamInputHelper.actions.clear();
        SteamInputHelper.actions.add(select);
        SteamInputHelper.actions.add(cancel);
        SteamInputHelper.actions.add(topPanel);
        SteamInputHelper.actions.add(proceed);

        SteamInputHelper.actions.add(peek);
        SteamInputHelper.actions.add(pageLeftViewDeck);
        SteamInputHelper.actions.add(pageRightViewExhaust);
        SteamInputHelper.actions.add(map);
        SteamInputHelper.actions.add(settings);

        SteamInputHelper.actions.add(up);
        SteamInputHelper.actions.add(down);
        SteamInputHelper.actions.add(left);
        SteamInputHelper.actions.add(right);

        SteamInputHelper.actions.add(inspectUp);
        SteamInputHelper.actions.add(inspectDown);
        SteamInputHelper.actions.add(inspectLeft);
        SteamInputHelper.actions.add(inspectRight);

        SteamInputHelper.actions.add(altUp);
        SteamInputHelper.actions.add(altDown);
        SteamInputHelper.actions.add(altLeft);
        SteamInputHelper.actions.add(altRight);

        SteamInputHelper.actions.add(drawPile);
        SteamInputHelper.actions.add(discardPile);
    }
    public static SteamInputAction down;
    public static SteamInputAction left;
    public static SteamInputAction right;
    public static SteamInputAction inspectUp;
    public static SteamInputAction inspectDown;
    public static SteamInputAction inspectLeft;
    public static SteamInputAction inspectRight;
    public static SteamInputAction altUp;
    public static SteamInputAction altDown;
    public static SteamInputAction altLeft;
    public static SteamInputAction altRight;
    public static SteamController controller;
    private static SteamInputAction SetAction(String name, CInputAction ref) {
        return new SteamInputAction(controller.getDigitalActionHandle(name), ref);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\steamInput
 * \SteamInputActionSet.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

