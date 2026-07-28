package com.megacrit.cardcrawl.localization;

public class BlightStrings {
    public String NAME;
    public String[] DESCRIPTION;

    public static BlightStrings getBlightOrbString() {
        BlightStrings retVal = new BlightStrings();
        retVal.NAME = "[MISSING_NAME]";
        retVal.DESCRIPTION = LocalizedStrings.createMockStringArray(5);
        return retVal;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\localization\
 * BlightStrings.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

