package ludicrousspeed.simulator.patches;

import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.actions.animations.AnimateFastAttackAction;
import com.megacrit.cardcrawl.actions.animations.AnimateHopAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.SetAnimationAction;
import com.megacrit.cardcrawl.actions.common.EscapeAction;
import com.megacrit.cardcrawl.actions.common.MonsterStartTurnAction;
import com.megacrit.cardcrawl.actions.common.SpawnMonsterAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.EnemyMoveInfo;
import com.megacrit.cardcrawl.powers.AbstractPower;
import ludicrousspeed.LudicrousSpeedMod;
import savestate.fastobjects.AnimationStateFast;

public class MonsterPatch {
    @SpirePatch(
            clz = AbstractMonster.class,
            paramtypez = {boolean.class},
            method = "die"
    )
    public static class MonsterDeathWithRelicsPatch {
        public static SpireReturn Prefix(AbstractMonster monster, boolean triggerRelics) {
            if (LudicrousSpeedMod.plaidMode) {
                if (!monster.isDying) {
                    monster.isDying = true;
                    if (monster.currentHealth <= 0 && triggerRelics) {
                        monster.powers.forEach(AbstractPower::onDeath);
                    }

                    if (triggerRelics) {
                        AbstractDungeon.player.relics
                                .forEach(relic -> relic.onMonsterDeath(monster));
                    }

                    if (AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
                        AbstractDungeon.overlayMenu.endTurnButton.disable();

                        AbstractDungeon.player.limbo.clear();
                    }

                    if (monster.currentHealth < 0) {
                        monster.currentHealth = 0;
                    }
                }

                return SpireReturn.Return(null);
            }

            return SpireReturn.Continue();
        }

        public static void Postfix(AbstractMonster _instance, boolean triggerRelics) {
            if (LudicrousSpeedMod.plaidMode) {
                _instance.deathTimer = .0F;
                _instance.isDying = true;
                _instance.isDead = true;
                _instance.dispose();
            }
        }
    }

    @SpirePatch(
            clz = AbstractMonster.class,
            paramtypez = {},
            method = "onBossVictoryLogic"
    )
    public static class BossFastDeathPatch {
        public static void Postfix(AbstractMonster _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                _instance.deathTimer = .0000001F;
            }
        }
    }

    @SpirePatch(
            clz = SetAnimationAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class SetAnimationPatch {
        public static void Prefix(SetAnimationAction _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                _instance.isDone = true;
            }
        }
    }

    @SpirePatch(
            clz = AbstractCreature.class,
            paramtypez = {},
            method = "updateAnimations"
    )
    public static class NoUpdateCreatureAnimationsPlayerPatch {
        public static SpireReturn Prefix(AbstractCreature _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = EscapeAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class FastEscapePatch {
        public static SpireReturn Prefix(EscapeAction _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                AbstractMonster m = (AbstractMonster) _instance.source;
                m.escape();
                _instance.isDone = true;
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = AnimateHopAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class HopAnimationPatch {
        public static void Prefix(AnimateHopAction _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                _instance.isDone = true;
            }
        }
    }

    @SpirePatch(
            clz = AnimateSlowAttackAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class SlowAttackAnimationPatch {
        public static void Prefix(AnimateSlowAttackAction _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                _instance.isDone = true;
            }
        }
    }

    @SpirePatch(
            clz = SpawnMonsterAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class SpawnMonsterAnimationPatch {
        public static void Prefix(SpawnMonsterAction _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                // Preserve the action's vanilla smart-positioning so the simulator and
                // the live replay keep summoned monsters in the same list order.
                _instance.isDone = true;
            }
        }
    }

    @SpirePatch(
            clz = AnimateFastAttackAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class FastAttackAnimationPatch {
        public static void Prefix(AnimateFastAttackAction _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                _instance.isDone = true;
            }
        }
    }

    @SpirePatch(
            clz = AbstractMonster.class,
            paramtypez = {},
            method = "createIntent"
    )
    public static class NoShowInetntPatch {
        public static SpireReturn Prefix(AbstractMonster _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                EnemyMoveInfo move = ReflectionHacks
                        .getPrivate(_instance, AbstractMonster.class, "move");

                _instance.intent = move.intent;
                _instance.nextMove = move.nextMove;
                _instance.setIntentBaseDmg(move.baseDamage);
                // Match vanilla createIntent: compute intentDmg and the multi-damage
                // markers so decision code reading them gets correct values
                if (move.baseDamage > -1) {
                    ReflectionHacks.privateMethod(AbstractMonster.class, "calculateDamage", int.class)
                                   .invoke(_instance, move.baseDamage);
                    if (move.isMultiDamage) {
                        ReflectionHacks.setPrivate(_instance, AbstractMonster.class, "intentMultiAmt",
                                move.multiplier);
                        ReflectionHacks.setPrivate(_instance, AbstractMonster.class, "isMultiDmg", true);
                    } else {
                        ReflectionHacks.setPrivate(_instance, AbstractMonster.class, "intentMultiAmt", -1);
                        ReflectionHacks.setPrivate(_instance, AbstractMonster.class, "isMultiDmg", false);
                    }
                }
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = AbstractMonster.class,
            paramtypez = {},
            method = "updateEscapeAnimation"
    )
    public static class NoAnimationsPatch {
        public static SpireReturn Prefix(AbstractMonster _instance) {
            if (LudicrousSpeedMod.plaidMode) {
                if (_instance.escapeTimer != 0) {
                    _instance.escaped = true;
                    // Converge immediately so AbstractMonster.update stops calling
                    // updateEscapeAnimation every frame (escapeTimer == 0 is the
                    // "no escape animation running" state in vanilla)
                    _instance.escapeTimer = 0;
                    _instance.state = new AnimationStateFast();
                    if (AbstractDungeon.getMonsters().areMonstersDead() && !AbstractDungeon
                            .getCurrRoom().isBattleOver && !AbstractDungeon
                            .getCurrRoom().cannotLose) {
                        AbstractDungeon.getCurrRoom().endBattle();
                    }
                }

                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = MonsterStartTurnAction.class,
            method = "update"
    )
    public static class BetterMonsterStartTurnPatch {
        @SpirePrefixPatch
        public static SpireReturn betterUpdate(MonsterStartTurnAction action) {
            if (LudicrousSpeedMod.plaidMode) {
                if (!action.isDone) {
                    AbstractDungeon.getCurrRoom().monsters.applyPreTurnLogic();
                }
                action.isDone = true;
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = AbstractPower.class, method = "flashWithoutSound")
    public static class NoFlashingPatch {
        @SpirePrefixPatch
        public static SpireReturn noFlash(AbstractPower power) {
            if (LudicrousSpeedMod.plaidMode) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
