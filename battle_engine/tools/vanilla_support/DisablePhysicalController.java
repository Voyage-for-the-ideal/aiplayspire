package aiplayspire.offline;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;

/** The isolated automation drives the game over HTTP/SaveState, not a joystick. */
@SpirePatch(cls = "com.megacrit.cardcrawl.helpers.controller.CInputHelper", method = "initializeIfAble")
public final class DisablePhysicalController {
    @SpirePrefixPatch
    public static SpireReturn<Void> skipControllerDiscovery() {
        return Boolean.getBoolean("aiplayspire.disablePhysicalController")
                ? SpireReturn.Return() : SpireReturn.Continue();
    }
}
