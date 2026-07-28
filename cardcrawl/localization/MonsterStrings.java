package com.megacrit.cardcrawl.localization;

public class MonsterStrings {
    public String NAME;
    public String[] DIALOG;
    public String[] MOVES;

    public static MonsterStrings getMockMonsterString() {
        MonsterStrings retVal = new MonsterStrings();
        retVal.NAME = "[MISSING_NAME]";
        retVal.DIALOG = LocalizedStrings.createMockStringArray(5);
        retVal.MOVES = LocalizedStrings.createMockStringArray(5);
        return retVal;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\localization\
 * MonsterStrings.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

