package com.megacrit.cardcrawl.localization;

public class ScoreBonusStrings {
    public String NAME;
    public String[] DESCRIPTIONS;

    public static ScoreBonusStrings getScoreBonusString() {
        ScoreBonusStrings retVal = new ScoreBonusStrings();
        retVal.NAME = "[MISSING_NAME]";
        retVal.DESCRIPTIONS = LocalizedStrings.createMockStringArray(1);
        return retVal;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\localization\
 * ScoreBonusStrings.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

