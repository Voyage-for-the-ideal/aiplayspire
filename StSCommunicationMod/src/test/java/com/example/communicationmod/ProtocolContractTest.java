package com.example.communicationmod;

import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.DeathScreen;
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
    public void permanentCardNumbersDoNotRequireCombatInitialization() {
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        GameStateConverter.addPermanentCardValues(values, 6, 0, -1);
        assertTrue(values.get("base_damage").equals(6));
        assertTrue(values.get("base_block").equals(0));
        assertFalse(values.containsKey("base_magic_number"));
    }

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
    public void cardRewardSkipReflectsTheActualButtonState() throws Exception {
        assertNotNull(com.megacrit.cardcrawl.screens.CardRewardScreen.class.getDeclaredField("skipButton"));
        assertNotNull(com.megacrit.cardcrawl.ui.buttons.SkipCardButton.class.getDeclaredField("isHidden"));
        assertTrue(ChoiceScreenUtils.isCardRewardSkipAvailable(false, false, false));
        assertFalse(ChoiceScreenUtils.isCardRewardSkipAvailable(true, false, false));
        assertFalse(ChoiceScreenUtils.isCardRewardSkipAvailable(false, true, false));
        assertFalse(ChoiceScreenUtils.isCardRewardSkipAvailable(false, false, true));
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

    @Test
    public void bossChestGuardsPickupAndTransition() {
        assertTrue(ChoiceScreenUtils.bossChestMayOpen(false, false, false));
        assertFalse(ChoiceScreenUtils.bossChestMayOpen(true, false, false));
        assertFalse(ChoiceScreenUtils.bossChestMayOpen(false, false, true));
        assertFalse(ChoiceScreenUtils.bossChestMayOpen(false, true, false));
        assertFalse(ChoiceScreenUtils.bossChestMayProceed(false, false, false, false, true));
        assertFalse(ChoiceScreenUtils.bossChestMayProceed(true, true, false, false, true));
        assertFalse(ChoiceScreenUtils.bossChestMayProceed(true, false, true, false, true));
        assertFalse(ChoiceScreenUtils.bossChestMayProceed(true, false, false, true, true));
        assertFalse(ChoiceScreenUtils.bossChestMayProceed(true, false, false, false, false));
        assertTrue(ChoiceScreenUtils.bossChestMayProceed(true, false, false, false, true));
    }

    @Test
    public void astrolabePlainGridIsRecognizedAsTransform() throws Exception {
        assertNotNull(com.megacrit.cardcrawl.relics.Astrolabe.class.getDeclaredField("cardsSelected"));
        assertTrue(ChoiceScreenUtils.gridIsTransform(false, true));
        assertTrue(ChoiceScreenUtils.gridIsTransform(true, false));
        assertFalse(ChoiceScreenUtils.gridIsTransform(false, false));
    }

    @Test
    public void bossRelicLandingIsDeferredOnlyForNewlyAddedRelics() {
        // Picked via bossObtainLogic(): obtain() appended it, so the arrival
        // (and its onEquip grid) has to wait for the boss screen to close.
        assertTrue(ChoiceScreenUtils.needsDeferredBossRelicLanding(false, true));
        // Holy Water / Black Blood / Ring of the Serpent / Frozen Core are
        // instant-obtained by BossRelicSelectScreen itself; that path owns
        // onEquip and must not be deferred a second time.
        assertFalse(ChoiceScreenUtils.needsDeferredBossRelicLanding(false, false));
        // Already owned before the pick (duplicate / manual state): vanilla
        // behaviour is kept.
        assertFalse(ChoiceScreenUtils.needsDeferredBossRelicLanding(true, true));
    }

    @Test
    public void gameOverReturnButtonLivesOnTheSharedSuperclass() throws Exception {
        // RunSetupUtils.clickReturnButton reflects on GameOverScreen.class
        // because DeathScreen and VictoryScreen only inherit the protected
        // field from that superclass; a subclass-scoped lookup would throw
        // NoSuchFieldException and the click would silently do nothing
        // (the 1.4.1 regression on the death settlement chain).
        assertNotNull(com.megacrit.cardcrawl.screens.GameOverScreen.class
                .getDeclaredField("returnButton"));
        try {
            DeathScreen.class.getDeclaredField("returnButton");
        } catch (NoSuchFieldException expected) {
            return;
        }
        throw new AssertionError("returnButton moved into DeathScreen; update RunSetupUtils"
                + " to reflect on the declaring class instead");
    }
}
