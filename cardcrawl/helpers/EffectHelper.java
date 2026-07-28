package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.GainPennyEffect;

public class EffectHelper {
    public static void gainGold(AbstractCreature target, float srcX, float srcY, int amount) {
        for (int i = 0; i < amount; i++)
            AbstractDungeon.effectList.add(new GainPennyEffect(target, srcX, srcY, target.hb.cX, target.hb.cY, true));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\
 * EffectHelper.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

