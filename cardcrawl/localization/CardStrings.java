package com.megacrit.cardcrawl.localization;

public class CardStrings {
    public String NAME;
    public String DESCRIPTION;
    public String UPGRADE_DESCRIPTION;
    public String[] EXTENDED_DESCRIPTION;

    public static CardStrings getMockCardString() {
        CardStrings retVal = new CardStrings();
        retVal.NAME = "[MISSING_TITLE]";
        retVal.DESCRIPTION = "[MISSING_DESCRIPTION]";
        retVal.UPGRADE_DESCRIPTION = "[MISSING_DESCRIPTION+]";
        retVal.EXTENDED_DESCRIPTION = new String[] {"[MISSING_0]", "[MISSING_1]", "[MISSING_2]" };
        return retVal;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\localization\
 * CardStrings.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

