package com.megacrit.cardcrawl.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.vfx.MapDot;

import java.util.ArrayList;

public class MapEdge
        implements Comparable<MapEdge> {
    public final int dstX;
    public final int dstY;
    public final int srcX;
    public final int srcY;
    public static final float ICON_SRC_RADIUS = 29.0F * Settings.scale;
    private static final float ICON_DST_RADIUS = 20.0F * Settings.scale;
    private static final float SPACING = Settings.isMobile ? (20.0F * Settings.xScale) : (17.0F * Settings.xScale);
    private ArrayList<MapDot> dots = new ArrayList<>();
    private static final Color DISABLED_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.25F);
    public Color color = DISABLED_COLOR.cpy();
    public boolean taken = false;
    private static final float SPACE_X = Settings.isMobile ? (140.8F * Settings.xScale) : (128.0F * Settings.xScale);

    public MapEdge(int srcX, int srcY, int dstX, int dstY) {
        this.srcX = srcX;
        this.srcY = srcY;
        this.dstX = dstX;
        this.dstY = dstY;
    }

    public MapEdge(int srcX, int srcY, float srcOffsetX, float srcOffsetY, int dstX, int dstY, float dstOffsetX,
            float dstOffsetY, boolean isBoss) {
        this.srcX = srcX;
        this.srcY = srcY;
        this.dstX = dstX;
        this.dstY = dstY;

        float tmpSX = getX(srcX) + srcOffsetX;
        float tmpDX = getX(dstX) + dstOffsetX;
        float tmpSY = srcY * Settings.MAP_DST_Y + srcOffsetY;
        float tmpDY = dstY * Settings.MAP_DST_Y + dstOffsetY;

        Vector2 vec2 = (new Vector2(tmpDX, tmpDY)).sub(new Vector2(tmpSX, tmpSY));
        float length = vec2.len();
        float START = SPACING * MathUtils.random() / 2.0F;

        float tmpRadius = ICON_DST_RADIUS;
        if (isBoss) {
            tmpRadius = 164.0F * Settings.scale;
        }
        float i;
        for (i = START + tmpRadius; i < length - ICON_SRC_RADIUS; i += SPACING) {
            vec2.clamp(length - i, length - i);
            if (i != START + tmpRadius && i <= length - ICON_SRC_RADIUS - SPACING) {
                this.dots.add(new MapDot(tmpSX + vec2.x, tmpSY + vec2.y, (new Vector2(tmpSX - tmpDX, tmpSY - tmpDY))

                        .nor().angle() + 90.0F, true));
            } else {

                this.dots.add(new MapDot(tmpSX + vec2.x, tmpSY + vec2.y, (new Vector2(tmpSX - tmpDX, tmpSY - tmpDY))

                        .nor().angle() + 90.0F, false));
            }
        }
    }

    private float getX(int x) {
        return x * SPACE_X + MapRoomNode.OFFSET_X;
    }

    public String toString() {
        return "(" + this.dstX + "," + this.dstY + ")";
    }

    public int compareTo(MapEdge e) {
        if (this.dstX > e.dstX)
            return 1;
        if (this.dstX < e.dstX) {
            return -1;
        }
        if (this.dstY > e.dstY)
            return 1;
        if (this.dstY < e.dstY)
            return -1;
        if (this.dstY == e.dstY) {
            return 0;
        }
        assert false;
        return 0;
    }

    public void markAsTaken() {
        this.taken = true;
        this.color = MapRoomNode.AVAILABLE_COLOR;
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        for (MapDot d : this.dots)
            d.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\map\MapEdge.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

