package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;

public class MathHelper {
    public static float cardLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 6.0F);
            if (Math.abs(startX - targetX) < Settings.CARD_SNAP_THRESHOLD) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float cardScaleLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 7.5F);
            if (Math.abs(startX - targetX) < 0.003F) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float uiLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 9.0F);
            if (Math.abs(startX - targetX) < Settings.UI_SNAP_THRESHOLD) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float orbLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 6.0F);
            if (Math.abs(startX - targetX) < Settings.UI_SNAP_THRESHOLD) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float mouseLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 20.0F);
            if (Math.abs(startX - targetX) < Settings.UI_SNAP_THRESHOLD) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float scaleLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 8.0F);
            if (Math.abs(startX - targetX) < 0.003F) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float fadeLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 12.0F);
            if (Math.abs(startX - targetX) < 0.01F) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float popLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 8.0F);
            if (Math.abs(startX - targetX) < 0.003F) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float angleLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 12.0F);
            if (Math.abs(startX - targetX) < 0.003F) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float slowColorLerpSnap(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 3.0F);
            if (Math.abs(startX - targetX) < 0.01F) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float scrollSnapLerpSpeed(float startX, float targetX) {
        if (startX != targetX) {
            startX = MathUtils.lerp(startX, targetX, Gdx.graphics.getDeltaTime() * 10.0F);
            if (Math.abs(startX - targetX) < Settings.UI_SNAP_THRESHOLD) {
                startX = targetX;
            }
        }
        return startX;
    }

    public static float valueFromPercentBetween(float min, float max, float percent) {
        float diff = max - min;
        return min + diff * percent;
    }

    public static float percentFromValueBetween(float min, float max, float value) {
        float diff = max - min;
        float offset = value - min;
        return offset / diff;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\MathHelper
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

