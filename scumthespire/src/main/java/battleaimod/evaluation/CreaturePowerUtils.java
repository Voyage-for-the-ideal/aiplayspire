package battleaimod.evaluation;

import com.megacrit.cardcrawl.powers.ArtifactPower;
import com.megacrit.cardcrawl.powers.DexterityPower;
import com.megacrit.cardcrawl.powers.FocusPower;
import com.megacrit.cardcrawl.powers.FrailPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.powers.VulnerablePower;
import com.megacrit.cardcrawl.powers.WeakPower;
import savestate.CreatureState;
import savestate.monsters.MonsterState;

/**
 * Cheap read-only access to creature power stacks on {@link CreatureState}
 * (which includes both PlayerState and MonsterState).
 */
public final class CreaturePowerUtils {

    private CreaturePowerUtils() {
    }

    /** Power stack amount for the given power id, or 0 if the creature does not have it. */
    public static int powerAmount(CreatureState creature, String powerId) {
        if (creature == null || creature.powers == null) {
            return 0;
        }
        for (savestate.powers.PowerState power : creature.powers) {
            if (power.powerId.equals(powerId)) {
                return power.amount;
            }
        }
        return 0;
    }

    public static int strengthOf(MonsterState monster) {
        return powerAmount(monster, StrengthPower.POWER_ID);
    }

    public static int playerStrength(CreatureState player) {
        return powerAmount(player, StrengthPower.POWER_ID);
    }

    public static int playerDexterity(CreatureState player) {
        return powerAmount(player, DexterityPower.POWER_ID);
    }

    public static int playerFocus(CreatureState player) {
        return powerAmount(player, FocusPower.POWER_ID);
    }

    public static int playerVulnerable(CreatureState player) {
        return powerAmount(player, VulnerablePower.POWER_ID);
    }

    public static int playerWeak(CreatureState player) {
        return powerAmount(player, WeakPower.POWER_ID);
    }

    public static int playerFrail(CreatureState player) {
        return powerAmount(player, FrailPower.POWER_ID);
    }

    public static int playerArtifact(CreatureState player) {
        return powerAmount(player, ArtifactPower.POWER_ID);
    }
}
