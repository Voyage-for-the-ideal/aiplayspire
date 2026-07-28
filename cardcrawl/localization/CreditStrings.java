package com.megacrit.cardcrawl.localization;

public class CreditStrings {
    public String HEADER;
    public String[] NAMES;

    public static CreditStrings getMockCreditString() {
        CreditStrings retVal = new CreditStrings();
        retVal.HEADER = "[MISSING_HEADER]";
        retVal.NAMES = LocalizedStrings.createMockStringArray(8);
        return null;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\localization\
 * CreditStrings.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

