package ludicrousspeed.simulator.patches;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;
import ludicrousspeed.LudicrousSpeedMod;

public class FastExhaustPatches {
    private static final float START_DURATION = .1F;

    @SpirePatch(clz = ExhaustCardEffect.class, method = SpirePatch.CONSTRUCTOR)
    public static class FastDurationPatch {
        @SpirePostfixPatch
        public static void fastDuration(ExhaustCardEffect effect, AbstractCard c) {
            if (LudicrousSpeedMod.plaidMode) {
                effect.duration = START_DURATION;
            }
        }
    }

    @SpirePatch(clz = ExhaustCardEffect.class, method = "update")
    public static class FastExhaustUpdatePatch {
        @SpirePrefixPatch
        public static SpireReturn doFast(ExhaustCardEffect effect) {
            if (LudicrousSpeedMod.plaidMode) {
                AbstractCard c = ReflectionHacks.getPrivate(effect, ExhaustCardEffect.class, "c");

                // No particles, no sound: just run the timer down and reset
                // the card (the previous code spawned 140 effects per exhaust)
                effect.duration -= Gdx.graphics.getDeltaTime();

                if (effect.duration < 0.0F) {
                    effect.isDone = true;
                    c.resetAttributes();
                }

                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
