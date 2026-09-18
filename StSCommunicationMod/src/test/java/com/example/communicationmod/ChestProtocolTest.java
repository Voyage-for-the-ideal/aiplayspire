package com.example.communicationmod;

import com.megacrit.cardcrawl.rewards.chests.AbstractChest;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ChestProtocolTest {
    @Test
    public void regularChestExposesGoldAndRelicTier() {
        Map<String, Object> info = GameStateConverter.buildChestInfo(
                false, true, 50, AbstractChest.RelicReward.COMMON_RELIC);

        assertEquals(false, info.get("chest_boss"));
        assertEquals(true, info.get("chest_gold_reward"));
        assertEquals(50, info.get("chest_gold_amount"));
        assertEquals("common", info.get("chest_relic_tier"));
    }

    @Test
    public void relicOnlyChestReportsZeroGold() {
        Map<String, Object> info = GameStateConverter.buildChestInfo(
                false, false, 75, AbstractChest.RelicReward.UNCOMMON_RELIC);

        assertEquals(false, info.get("chest_boss"));
        assertEquals(false, info.get("chest_gold_reward"));
        assertEquals(0, info.get("chest_gold_amount"));
        assertEquals("uncommon", info.get("chest_relic_tier"));
    }

    @Test
    public void rareTierMapsToRare() {
        Map<String, Object> info = GameStateConverter.buildChestInfo(
                false, false, 0, AbstractChest.RelicReward.RARE_RELIC);

        assertEquals("rare", info.get("chest_relic_tier"));
    }

    @Test
    public void bossChestReportsEmptyContents() {
        Map<String, Object> info = GameStateConverter.buildChestInfo(
                true, false, 0, null);

        assertEquals(true, info.get("chest_boss"));
        assertEquals(false, info.get("chest_gold_reward"));
        assertEquals(0, info.get("chest_gold_amount"));
        assertEquals(null, info.get("chest_relic_tier"));
    }

    @Test
    public void unrewardedChestReportsNullTier() {
        assertEquals(null, GameStateConverter.relicTierName(null));
    }
}
