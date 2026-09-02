package com.example.communicationmod;

import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the private/public game API details used by the JSON protocol.
 * These checks deliberately avoid booting a game session.
 */
public class ProtocolContractTest {
    @Test
    public void shopPurgeUsesTheActualGameEntryPoint() throws Exception {
        assertNotNull(ShopScreen.class.getDeclaredMethod("purchasePurge"));
    }

    @Test
    public void combatRewardEnumExposesBothKeyRewardKinds() {
        assertNotNull(RewardItem.RewardType.valueOf("EMERALD_KEY"));
        assertNotNull(RewardItem.RewardType.valueOf("SAPPHIRE_KEY"));
    }

    @Test
    public void bossRewardCannotBeCancelled() {
        assertFalse(ChoiceScreenUtils.isBossRewardCancelable());
    }

    @Test
    public void gridConfirmRequiresTheGridConfirmationPhase() throws Exception {
        assertNotNull(GridCardSelectScreen.class.getField("confirmScreenUp"));
        assertFalse(ChoiceScreenUtils.isGridConfirmAvailable(false, false, false, false, false));
        assertFalse(ChoiceScreenUtils.isGridConfirmAvailable(true, false, false, false, true));
        assertTrue(ChoiceScreenUtils.isGridConfirmAvailable(true, false, false, false, false));
        assertTrue(ChoiceScreenUtils.isGridConfirmAvailable(false, true, false, false, false));
        assertTrue(ChoiceScreenUtils.isGridConfirmAvailable(false, false, true, false, false));
        assertTrue(ChoiceScreenUtils.isGridConfirmAvailable(false, false, false, true, false));
    }
}
