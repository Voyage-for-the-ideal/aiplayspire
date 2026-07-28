package com.megacrit.cardcrawl.screens;

public class DisplayOption implements Comparable<DisplayOption> {
    public int width;
    public String aspectRatio = " TAB TAB";
    public int height;

    public DisplayOption(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public DisplayOption(int width, int height, boolean showAspectRatio) {

        this.width = width;
        this.height = height;
        if (showAspectRatio) {
            appendAspectRatio();
        }
    }

    private void appendAspectRatio() {
        float ratio = this.width / this.height;

        if (ratio > 1.25F && ratio < 1.26F) {
            this.aspectRatio = " (5:4)";
        } else if (ratio > 1.32F && ratio < 1.34F) {
            this.aspectRatio = " (4:3)";
        } else if (ratio > 1.76F && ratio < 1.78F) {
            this.aspectRatio = " (16:9)";
        } else if (ratio > 1.59F && ratio < 1.61F) {
            this.aspectRatio = " (16:10)";
        } else if (ratio > 2.32F && ratio < 2.34F) {
            this.aspectRatio = " (21:9)";
        } else {
            this.aspectRatio = " (" + String.format("#.##", new Object[] {Float.valueOf(ratio) }) + ")";
        }
    }

    public int compareTo(DisplayOption other) {
        if (this.width == other.width) {
            if (this.height == other.height)
                return 0;
            if (this.height < other.height) {
                return -1;
            }
            return 1;
        }
        if (this.width < other.width) {
            return -1;
        }
        return 1;
    }

    public boolean equals(Object other) {
        return (((DisplayOption) other).width == this.width && ((DisplayOption) other).height == this.height);
    }

    public String toString() {
        return "(" + this.width + "," + this.height + ")";
    }

    public String uiString() {
        return this.width + " x " + this.height + this.aspectRatio;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * DisplayOption.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

