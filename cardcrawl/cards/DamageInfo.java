package com.megacrit.cardcrawl.cards;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DamageInfo {
    public AbstractCreature owner;
    public String name;
    public DamageType type;
    public int base;
    public int output;
    public boolean isModified = false;

    public DamageInfo(AbstractCreature damageSource, int base, DamageType type) {
        this.owner = damageSource;
        this.type = type;
        this.base = base;
        this.output = base;
    }

    public DamageInfo(AbstractCreature owner, int base) {
        this(owner, base, DamageType.NORMAL);
    }

    public void applyPowers(AbstractCreature owner, AbstractCreature target) {
        this.output = this.base;
        this.isModified = false;
        float tmp = this.output;

        if (!owner.isPlayer) {

            if (Settings.isEndless && AbstractDungeon.player.hasBlight("DeadlyEnemies")) {
                float mod = AbstractDungeon.player.getBlight("DeadlyEnemies").effectFloat();
                tmp *= mod;

                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            for (AbstractPower p : owner.powers) {
                tmp = p.atDamageGive(tmp, this.type);

                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            for (AbstractPower p : target.powers) {
                tmp = p.atDamageReceive(tmp, this.type);
                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            tmp = AbstractDungeon.player.stance.atDamageReceive(tmp, this.type);
            if (this.base != (int) tmp) {
                this.isModified = true;
            }

            for (AbstractPower p : owner.powers) {
                tmp = p.atDamageFinalGive(tmp, this.type);
                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            for (AbstractPower p : target.powers) {
                tmp = p.atDamageFinalReceive(tmp, this.type);
                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            this.output = MathUtils.floor(tmp);
            if (this.output < 0) {
                this.output = 0;
            }
        } else {

            for (AbstractPower p : owner.powers) {
                tmp = p.atDamageGive(tmp, this.type);
                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            tmp = AbstractDungeon.player.stance.atDamageGive(tmp, this.type);
            if (this.base != (int) tmp) {
                this.isModified = true;
            }

            for (AbstractPower p : target.powers) {
                tmp = p.atDamageReceive(tmp, this.type);
                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            for (AbstractPower p : owner.powers) {
                tmp = p.atDamageFinalGive(tmp, this.type);
                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            for (AbstractPower p : target.powers) {
                tmp = p.atDamageFinalReceive(tmp, this.type);
                if (this.base != (int) tmp) {
                    this.isModified = true;
                }
            }

            this.output = MathUtils.floor(tmp);
            if (this.output < 0) {
                this.output = 0;
            }
        }
    }

    public void applyEnemyPowersOnly(AbstractCreature target) {
        this.output = this.base;
        this.isModified = false;
        float tmp = this.output;

        for (AbstractPower p : target.powers) {
            tmp = p.atDamageReceive(this.output, this.type);
            if (this.base != this.output) {
                this.isModified = true;
            }
        }

        for (AbstractPower p : target.powers) {
            tmp = p.atDamageFinalReceive(this.output, this.type);
            if (this.base != this.output) {
                this.isModified = true;
            }
        }

        if (tmp < 0.0F) {
            tmp = 0.0F;
        }
        this.output = MathUtils.floor(tmp);
    }

    public static int[] createDamageMatrix(int baseDamage) {
        return createDamageMatrix(baseDamage, false);
    }

    public static int[] createDamageMatrix(int baseDamage, boolean isPureDamage) {
        int[] retVal = new int[(AbstractDungeon.getMonsters()).monsters.size()];
        for (int i = 0; i < retVal.length; i++) {
            DamageInfo info = new DamageInfo((AbstractCreature) AbstractDungeon.player, baseDamage);
            if (!isPureDamage) {
                info.applyPowers((AbstractCreature) AbstractDungeon.player,
                        (AbstractDungeon.getMonsters()).monsters.get(i));
            }
            retVal[i] = info.output;
        }
        return retVal;
    }

    public static int[] createDamageMatrix(int baseDamage, boolean isPureDamage, boolean isOrbDamage) {
        int[] retVal = new int[(AbstractDungeon.getMonsters()).monsters.size()];
        for (int i = 0; i < retVal.length; i++) {
            DamageInfo info = new DamageInfo((AbstractCreature) AbstractDungeon.player, baseDamage);
            if (isOrbDamage && ((AbstractMonster) (AbstractDungeon.getMonsters()).monsters.get(i)).hasPower("Lockon")) {
                info.output = (int) (info.base * 1.5F);
            }
            retVal[i] = info.output;
        }
        return retVal;
    }

    public enum DamageType {
        NORMAL, THORNS, HP_LOSS;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\DamageInfo.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



