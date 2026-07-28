package com.megacrit.cardcrawl.helpers;

public class EnemyData {
    public String name;
    public int level;
    public MonsterType type;

    public enum MonsterType {
        WEAK, STRONG, ELITE, BOSS, EVENT;
    }

    public EnemyData(String key, int level, MonsterType type) {
        this.name = key;
        this.level = level;
        this.type = type;
    }

    public static String gameDataUploadHeader() {
        GameDataStringBuilder builder = new GameDataStringBuilder();
        builder.addFieldData("name");
        builder.addFieldData("level");
        builder.addFieldData("type");
        return builder.toString();
    }

    public String gameDataUploadData() {
        GameDataStringBuilder builder = new GameDataStringBuilder();
        builder.addFieldData(this.name);
        builder.addFieldData(this.level);
        builder.addFieldData(this.type.name());
        return builder.toString();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\EnemyData.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

