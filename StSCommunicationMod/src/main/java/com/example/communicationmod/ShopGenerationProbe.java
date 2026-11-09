package com.example.communicationmod;

import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.Exordium;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.ShopRoom;
import com.megacrit.cardcrawl.shop.Merchant;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import basemod.ReflectionHacks;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Opt-in real-engine generation oracle. Run ONLY in a copied game/profile.
 * Writes create-new JSONL, never loads/writes a user's save, exits on completion.
 * Private pre-generation RNG/pools are fixture evidence, not policy inputs.
 */
public final class ShopGenerationProbe {
    private ShopGenerationProbe() {}
    private static List<Long> words(Random r) {
        return Arrays.asList(r.random.getState(0), r.random.getState(1));
    }
    private static List<Map<String, Object>> cards(CardGroup group) {
        List<AbstractCard> sorted = new ArrayList<>(group.group);
        Collections.sort(sorted);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AbstractCard c : sorted) {
            Map<String, Object> row = new LinkedHashMap<>();
            AbstractCard preview = c.makeCopy();
            for (AbstractRelic relic : AbstractDungeon.player.relics) relic.onPreviewObtainCard(preview);
            row.put("id", c.cardID); row.put("type", c.type.name()); row.put("rarity", c.rarity.name());
            row.put("color", c.color.name()); row.put("base_price", AbstractCard.getPrice(c.rarity));
            row.put("preview_upgrades", preview.timesUpgraded); rows.add(row);
        }
        return rows;
    }
    private static Map<String, Object> before() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("format", "shop-generation-v1"); s.put("hooks", "vanilla");
        s.put("character", AbstractDungeon.player.chosenClass.name());
        s.put("ascension", AbstractDungeon.ascensionLevel);
        s.put("card_blizz", AbstractDungeon.cardBlizzRandomizer);
        s.put("rare_chance", 9); s.put("uncommon_chance", 37);
        s.put("potion_common", PotionHelper.POTION_COMMON_CHANCE);
        s.put("potion_uncommon", PotionHelper.POTION_UNCOMMON_CHANCE);
        s.put("purge_base", ShopScreen.purgeCost);
        Map<String, Object> rng = new LinkedHashMap<>();
        rng.put("card", words(AbstractDungeon.cardRng)); rng.put("merchant", words(AbstractDungeon.merchantRng));
        rng.put("potion", words(AbstractDungeon.potionRng)); s.put("rng", rng);
        if (MathUtils.random instanceof RandomXS128) {
            RandomXS128 random = (RandomXS128) MathUtils.random;
            rng.put("math", Arrays.asList(random.getState(0), random.getState(1)));
        }
        s.put("colorless_rare_chance", AbstractDungeon.colorlessRareChance);
        int slots = 0;
        for (AbstractPotion p : AbstractDungeon.player.potions) if (p instanceof PotionSlot) slots++;
        s.put("empty_potion_slots", slots);
        s.put("purchase_sound_rng", !(CardCrawlGame.MUTE_IF_BG && Settings.isBackgrounded));
        Map<String, Object> pools = new LinkedHashMap<>();
        pools.put("COMMON", cards(AbstractDungeon.commonCardPool));
        pools.put("UNCOMMON", cards(AbstractDungeon.uncommonCardPool));
        pools.put("RARE", cards(AbstractDungeon.rareCardPool));
        pools.put("COLORLESS", cards(AbstractDungeon.colorlessCardPool)); s.put("cards", pools);
        Map<String, Object> relicPools = new LinkedHashMap<>();
        relicPools.put("COMMON", new ArrayList<>(AbstractDungeon.commonRelicPool));
        relicPools.put("UNCOMMON", new ArrayList<>(AbstractDungeon.uncommonRelicPool));
        relicPools.put("RARE", new ArrayList<>(AbstractDungeon.rareRelicPool));
        relicPools.put("SHOP", new ArrayList<>(AbstractDungeon.shopRelicPool)); s.put("relic_pools", relicPools);
        Map<String, Object> relicCatalog = new LinkedHashMap<>();
        List<String> relicIds = new ArrayList<>();
        for (Object pool : relicPools.values()) relicIds.addAll((List<String>) pool);
        relicIds.add("Circlet");
        for (String id : relicIds) {
            AbstractRelic r = RelicLibrary.getRelic(id);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("base_price", r.getPrice()); info.put("can_spawn", r.canSpawn()); relicCatalog.put(id, info);
        }
        s.put("relic_catalog", relicCatalog);
        List<Map<String, Object>> potions = new ArrayList<>();
        for (String id : PotionHelper.potions) {
            AbstractPotion p = PotionHelper.getPotion(id);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", id); info.put("rarity", p.rarity.name()); info.put("base_price", p.getPrice()); potions.add(info);
        }
        s.put("potions", potions);
        List<String> owned = new ArrayList<>();
        for (AbstractRelic r : AbstractDungeon.player.relics) owned.add(r.relicId);
        s.put("owned_relics", owned);
        // Introspection constructs previews/potions and may consume visual RNG.
        // Capture streams at the actual generation/transaction boundary, after
        // all catalog work, so the fixture is causally aligned.
        rng.put("card", words(AbstractDungeon.cardRng)); rng.put("merchant", words(AbstractDungeon.merchantRng));
        rng.put("potion", words(AbstractDungeon.potionRng));
        if (MathUtils.random instanceof RandomXS128) {
            RandomXS128 random = (RandomXS128) MathUtils.random;
            rng.put("math", Arrays.asList(random.getState(0), random.getState(1)));
        }
        return s;
    }

    private static Map<String, Object> stock() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = ShopInventory.items();
        for (Map<String, Object> item : items) {
            item.remove("affordable"); item.remove("legal"); item.remove("choice_index");
        }
        result.put("items", items); result.put("purge_cost", ShopScreen.actualPurgeCost);
        result.put("purge_available", AbstractDungeon.shopScreen.purgeAvailable);
        return result;
    }

    private static void write(java.io.BufferedWriter writer, Map<String, Object> fixture) throws java.io.IOException {
        writer.write(new Gson().toJson(fixture)); writer.newLine(); writer.flush();
    }

    private static void transaction(java.io.BufferedWriter writer, String caseId, String action) throws Exception {
        ShopScreen shop = AbstractDungeon.shopScreen;
        Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("case_id", caseId); fixture.put("before", before());
        fixture.put("stock_before", stock()); fixture.put("action", action);
        String[] parts = action.split(":");
        int index = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        if ("colored".equals(parts[0]) || "colorless".equals(parts[0])) {
            AbstractCard target = ("colored".equals(parts[0]) ? shop.coloredCards : shop.colorlessCards).get(index);
            ReflectionHacks.privateMethod(ShopScreen.class, "purchaseCard", AbstractCard.class).invoke(shop, target);
        } else if ("relic".equals(parts[0])) {
            ArrayList<StoreRelic> relics = ReflectionHacks.getPrivate(shop, ShopScreen.class, "relics");
            relics.get(index).purchaseRelic();
        } else if ("potion".equals(parts[0])) {
            ArrayList<StorePotion> potions = ReflectionHacks.getPrivate(shop, ShopScreen.class, "potions");
            potions.get(index).purchasePotion();
        } else {
            AbstractDungeon.gridSelectScreen.selectedCards.clear();
            AbstractDungeon.gridSelectScreen.selectedCards.add(AbstractDungeon.player.masterDeck.group.get(0));
            shop.updatePurge();
        }
        fixture.put("expected", stock()); write(writer, fixture);
    }

    public static void runIfRequested() {
        String destination = System.getProperty("spire.shopProbeOutput");
        if (destination == null) return;
        if (!Boolean.getBoolean("spire.isolatedResearch"))
            throw new IllegalStateException("Shop probe requires isolatedResearch=true and a copied runtime/profile");
        Path path = Paths.get(destination).toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            // CREATE_NEW prevents accidental truncation of a previous experiment.
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                Settings.isShowBuild = true; // Exordium uses EmptyRoom, avoiding Neow autosave.
                Settings.isDailyRun = false; Settings.isEndless = false;
                int count = Integer.getInteger("spire.shopProbeCount", 200);
                AbstractPlayer.PlayerClass[] roles = {AbstractPlayer.PlayerClass.IRONCLAD, AbstractPlayer.PlayerClass.THE_SILENT,
                        AbstractPlayer.PlayerClass.DEFECT, AbstractPlayer.PlayerClass.WATCHER};
                int[] levels = {0, 9, 10, 15, 16, 20};
                String[][] variants = {{}, {"The Courier"}, {"Membership Card"}, {"The Courier", "Membership Card"},
                        {"Smiling Mask"}, {"Molten Egg 2", "Toxic Egg 2", "Frozen Egg 2"}};
                for (int i = 0; i < count; i++) {
                    Settings.seed = 900000L + i;
                    AbstractDungeon.generateSeeds();
                    AbstractDungeon.floorNum = 0; AbstractDungeon.actNum = 1;
                    AbstractDungeon.ascensionLevel = levels[(i / 4) % levels.length];
                    AbstractPlayer player = CardCrawlGame.characterManager.getCharacter(roles[i % 4]).newInstance();
                    CardCrawlGame.dungeon = new Exordium(player, new ArrayList<String>());
                    player.gold = 5000;
                    player.relics.clear();
                    String[] variant = variants[((i / 4) / levels.length) % variants.length];
                    for (String id : variant) player.relics.add(RelicLibrary.getRelic(id).makeCopy());
                    // Vary generated streams independently of the seed and prior encounters.
                    for (int j = 0; j < i % 17; j++) AbstractDungeon.cardRng.random(99);
                    for (int j = 0; j < i % 7; j++) AbstractDungeon.potionRng.random(99);
                    AbstractDungeon.currMapNode = new MapRoomNode(0, 5);
                    AbstractDungeon.currMapNode.room = new ShopRoom();
                    AbstractDungeon.floorNum = 6; AbstractDungeon.cardBlizzRandomizer = (i % 6) - 5;
                    ShopScreen.resetPurgeCost();
                    Map<String, Object> fixture = new LinkedHashMap<>();
                    fixture.put("case_id", "engine-" + i); fixture.put("before", before());
                    Merchant merchant = new Merchant(); // real vanilla generation, including init/prices
                    fixture.put("expected", stock()); write(writer, fixture);
                    if ((i / 4) % 5 == 4) {
                        // Controlled membership-purchase fixture: test the real
                        // engine's immediate discount independently of RNG luck.
                        ArrayList<StoreRelic> relics = ReflectionHacks.getPrivate(AbstractDungeon.shopScreen, ShopScreen.class, "relics");
                        relics.get(2).relic = RelicLibrary.getRelic("Membership Card").makeCopy();
                        relics.get(2).price = 150;
                        transaction(writer, "engine-" + i + "-membership", "relic:2");
                    }
                    transaction(writer, "engine-" + i + "-buy1", (i / 4) % 2 == 0 ? "colored:0" : "colorless:0");
                    transaction(writer, "engine-" + i + "-buy2", (i / 4) % 2 == 0 ? "potion:0" : "relic:0");
                    transaction(writer, "engine-" + i + "-purge", "purge");
                    merchant.dispose();
                }
            }
            System.out.println("SHOP_PROBE_COMPLETE " + path);
        } catch (Exception error) {
            error.printStackTrace();
            System.out.println("SHOP_PROBE_FAILED " + error);
        } finally {
            Gdx.app.exit();
        }
    }
}
