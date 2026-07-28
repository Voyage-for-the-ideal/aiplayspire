package com.megacrit.cardcrawl.localization;

public class TutorialStrings {
    public String[] TEXT;
    public String[] LABEL;

    public static TutorialStrings getMockTutorialString() {
        TutorialStrings retVal = new TutorialStrings();
        retVal.TEXT = LocalizedStrings.createMockStringArray(25);
        retVal.LABEL = LocalizedStrings.createMockStringArray(8);
        return retVal;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\localization\
 * TutorialStrings.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

