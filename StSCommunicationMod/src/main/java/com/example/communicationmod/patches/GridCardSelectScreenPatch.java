package com.example.communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import basemod.ReflectionHacks;

@SpirePatch(
    clz = GridCardSelectScreen.class,
    method = "updateCardPositionsAndHoverLogic"
)
public class GridCardSelectScreenPatch {
    public static AbstractCard hoverCard = null;
    public static boolean doHover = false;

    public static void Postfix(GridCardSelectScreen __instance) {
        if (doHover && hoverCard != null) {
            ReflectionHacks.setPrivate(__instance, GridCardSelectScreen.class, "hoveredCard", hoverCard);
            hoverCard.hb.hovered = true;
            hoverCard.hb.clicked = true;
            doHover = false;
        }
    }
}
