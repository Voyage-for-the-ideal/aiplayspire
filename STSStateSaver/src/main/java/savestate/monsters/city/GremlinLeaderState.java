package savestate.monsters.city;

import basemod.ReflectionHacks;
import com.google.gson.JsonObject;
import savestate.fastobjects.AnimationStateFast;
import savestate.monsters.Monster;
import savestate.monsters.MonsterState;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.city.GremlinLeader;

import java.util.List;

import static savestate.SaveStateMod.shouldGoFast;

public class GremlinLeaderState extends MonsterState {
    public GremlinLeaderState(AbstractMonster monster) {
        super(monster);

        monsterTypeNumber = Monster.GREMLIN_LEADER.ordinal();
    }

    public GremlinLeaderState(String jsonString) {
        super(jsonString);

        monsterTypeNumber = Monster.GREMLIN_LEADER.ordinal();
    }

    public GremlinLeaderState(JsonObject monsterJson) {
        super(monsterJson);

        monsterTypeNumber = Monster.GREMLIN_LEADER.ordinal();
    }

    @Override
    public AbstractMonster loadMonster() {
        GremlinLeader result = new GremlinLeader();
        populateSharedFields(result);
        return result;
    }

    /** Restores the leader's private summon slots after the room monster list is rebuilt. */
    public static void restoreGremlinSlots(GremlinLeader leader, List<AbstractMonster> monsters) {
        AbstractMonster[] slots = new AbstractMonster[GremlinLeader.POSX.length];
        for (AbstractMonster monster : monsters) {
            if (monster == leader) {
                continue;
            }

            int slot = matchingSlot(monster.drawX);
            if (slot >= 0 && (slots[slot] == null ||
                    (slots[slot].isDying && !monster.isDying))) {
                slots[slot] = monster;
            }
        }
        ReflectionHacks.setPrivate(leader, GremlinLeader.class, "gremlins", slots);
    }

    private static int matchingSlot(float drawX) {
        for (int i = 0; i < GremlinLeader.POSX.length; i++) {
            float slotDrawX = Settings.WIDTH * 0.75F + GremlinLeader.POSX[i] * Settings.xScale;
            if (Math.abs(drawX - slotDrawX) < 1.0F) {
                return i;
            }
        }
        return -1;
    }

    @SpirePatch(
            clz = GremlinLeader.class,
            paramtypez = {},
            method = SpirePatch.CONSTRUCTOR
    )
    public static class NoAnimationsPatch {
        @SpireInsertPatch(loc = 62)
        public static SpireReturn GremlinLeader(GremlinLeader _instance) {
            if (shouldGoFast) {
                if (AbstractDungeon.ascensionLevel >= 8) {
                    MonsterState.setHp(_instance, 145, 155);
                } else {
                    MonsterState.setHp(_instance, 140, 148);
                }

                int strAmt;
                int blockAmt;

                if (AbstractDungeon.ascensionLevel >= 18) {
                    strAmt = 5;
                    blockAmt = 10;
                } else if (AbstractDungeon.ascensionLevel >= 3) {
                    strAmt = 4;
                    blockAmt = 6;
                } else {
                    strAmt = 3;
                    blockAmt = 6;
                }

                ReflectionHacks.setPrivate(_instance, GremlinLeader.class, "strAmt", strAmt);
                ReflectionHacks.setPrivate(_instance, GremlinLeader.class, "blockAmt", blockAmt);


                _instance.damage.add(new DamageInfo(_instance, 6));

                _instance.state = new AnimationStateFast();
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
