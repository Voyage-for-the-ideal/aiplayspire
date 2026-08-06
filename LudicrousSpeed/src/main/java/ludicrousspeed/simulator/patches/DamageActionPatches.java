package ludicrousspeed.simulator.patches;

import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import ludicrousspeed.LudicrousSpeedMod;

import java.util.List;

public class DamageActionPatches {
    @SpirePatch(
            clz = DamageAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class SpyOnDamageUpdatePatch {
        private static boolean npeWarned = false;

        public static SpireReturn Prefix(DamageAction _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                _instance.isDone = true;

                DamageInfo info = ReflectionHacks.getPrivate(_instance, DamageAction.class, "info");

                // Match vanilla DamageAction.update cancellation semantics
                // (shouldCancelAction && not THORNS): cancel when target is null,
                // dead/escaped, or source is dying
                if ((_instance.target == null || _instance.source != null && _instance.source.isDying
                        || _instance.target.isDeadOrEscaped())
                        && info.type != DamageInfo.DamageType.THORNS) {
                    return SpireReturn.Return(null);
                }

                if (info.type != DamageInfo.DamageType.THORNS && (info.owner.isDying || info.owner.halfDead)) {
                    return SpireReturn.Return(null);
                }

                int goldAmount = ReflectionHacks
                        .getPrivate(_instance, DamageAction.class, "goldAmount");

                if (goldAmount != 0) {
                    // stealGold deducts target.gold and spawns coin effects; only gold
                    // monsters hit this path (low frequency), so keep the reflection call
                    ReflectionHacks.privateMethod(DamageAction.class, "stealGold")
                                   .invoke(_instance);
                }

                try {
                    _instance.target.damage(info);
                } catch (NullPointerException e) {
                    // Stack traces are very expensive on the high-frequency simulator
                    // path; only report the first one
                    if (!npeWarned) {
                        e.printStackTrace();
                        npeWarned = true;
                    }
                }
                if (AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()) {
                    AbstractDungeon.actionManager.clearPostCombatActions();
                }

                return SpireReturn.Return(null);
            }

            return SpireReturn.Continue();
        }
    }


    @SpirePatch(
            clz = DamageAllEnemiesAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class SpyOnDamageAllEnemiesUpdatePatch {
        public static SpireReturn Prefix(DamageAllEnemiesAction _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                for (AbstractPower power : AbstractDungeon.player.powers) {
                    power.onDamageAllEnemies(_instance.damage);
                }

                List<AbstractMonster> monsters = AbstractDungeon.getCurrRoom().monsters.monsters;

                if (_instance.damage == null) {
                    int baseDamage = ReflectionHacks
                            .getPrivate(_instance, DamageAllEnemiesAction.class, "baseDamage");
                    _instance.damage = DamageInfo.createDamageMatrix(baseDamage);
                }

                // Match vanilla DamageAllEnemiesAction.update: skip dead/escaped monsters
                for (int i = 0; i < monsters.size(); i++) {
                    AbstractMonster monster = monsters.get(i);

                    if (!monster.isDeadOrEscaped()) {
                        monster.damage(new DamageInfo(_instance.source, _instance.damage[i], _instance.damageType));
                    }
                }


                if (AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()) {
                    AbstractDungeon.actionManager.clearPostCombatActions();
                }

                _instance.isDone = true;
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
