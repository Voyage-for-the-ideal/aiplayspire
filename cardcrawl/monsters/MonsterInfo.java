package com.megacrit.cardcrawl.monsters;

import com.megacrit.cardcrawl.core.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;

public class MonsterInfo
        implements Comparable<MonsterInfo> {
    private static final Logger logger = LogManager.getLogger(MonsterInfo.class.getName());
    public String name;
    public float weight;

    public MonsterInfo(String name, float weight) {
        this.name = name;
        this.weight = weight;
    }

    public static void normalizeWeights(ArrayList<MonsterInfo> list) {
        Collections.sort(list);
        float total = 0.0F;
        for (MonsterInfo i : list) {
            total += i.weight;
        }

        for (MonsterInfo i : list) {
            i.weight /= total;
            if (Settings.isInfo) {
                logger.info(i.name + ": " + i.weight + "%");
            }
        }
    }

    public static String roll(ArrayList<MonsterInfo> list, float roll) {
        float currentWeight = 0.0F;
        for (MonsterInfo i : list) {
            currentWeight += i.weight;
            if (roll < currentWeight) {
                return i.name;
            }
        }
        return "ERROR";
    }

    public int compareTo(MonsterInfo other) {
        return Float.compare(this.weight, other.weight);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\
 * MonsterInfo.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

