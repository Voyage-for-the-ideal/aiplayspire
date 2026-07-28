package com.megacrit.cardcrawl.monsters;

public class EnemyMoveInfo {
    public byte nextMove;
    public AbstractMonster.Intent intent;
    public int baseDamage;
    public int multiplier;
    public boolean isMultiDamage;

    public EnemyMoveInfo(byte nextMove, AbstractMonster.Intent intent, int intentBaseDmg, int multiplier,
            boolean isMultiDamage) {
        this.nextMove = nextMove;
        this.intent = intent;
        this.baseDamage = intentBaseDmg;
        this.multiplier = multiplier;
        this.isMultiDamage = isMultiDamage;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\
 * EnemyMoveInfo.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

