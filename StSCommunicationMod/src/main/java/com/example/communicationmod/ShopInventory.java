package com.example.communicationmod;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.shop.OnSaleTag;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import java.util.*;

/** Visible facts only. Never exposes seed, RNG, or future stock to /state. */
public final class ShopInventory {
    private ShopInventory() {}

    public static List<Map<String, Object>> items() {
        ShopScreen shop = AbstractDungeon.shopScreen;
        List<Object> legacy = ChoiceScreenUtils.getShopItems();
        List<Map<String, Object>> rows = new ArrayList<>();
        addCards(rows, shop.coloredCards, "colored", shop, legacy);
        addCards(rows, shop.colorlessCards, "colorless", shop, legacy);
        ArrayList<StoreRelic> relics = ReflectionHacks.getPrivate(shop, ShopScreen.class, "relics");
        ArrayList<StorePotion> potions = ReflectionHacks.getPrivate(shop, ShopScreen.class, "potions");
        for (int i = 0; i < relics.size(); i++) {
            StoreRelic r = relics.get(i);
            rows.add(row("relic:" + i, "relic", r.relic.relicId, r.price, 0, false,
                    r.isPurchased, !r.isPurchased, legacy.indexOf(r)));
        }
        boolean emptySlot = false;
        for (AbstractPotion p : AbstractDungeon.player.potions) if (p instanceof PotionSlot) emptySlot = true;
        boolean potionAllowed = !AbstractDungeon.player.hasRelic("Sozu") && emptySlot;
        for (int i = 0; i < potions.size(); i++) {
            StorePotion p = potions.get(i);
            rows.add(row("potion:" + i, "potion", p.potion.ID, p.price, 0, false,
                    p.isPurchased, !p.isPurchased && potionAllowed, legacy.indexOf(p)));
        }
        return rows;
    }

    private static void addCards(List<Map<String, Object>> rows, List<AbstractCard> cards,
                                 String prefix, ShopScreen shop, List<Object> legacy) {
        for (int i = 0; i < cards.size(); i++) {
            AbstractCard c = cards.get(i);
            OnSaleTag sale = ReflectionHacks.getPrivate(shop, ShopScreen.class, "saleTag");
            rows.add(row(prefix + ":" + i, "card", c.cardID, c.price, c.timesUpgraded,
                    sale != null && sale.card == c, false, true, legacy.indexOf(c)));
        }
    }

    static Map<String, Object> row(String slot, String kind, String id, int price, int upgrades,
                                   boolean sale, boolean sold, boolean enabled, int index) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("slot_id", slot); row.put("kind", kind); row.put("id", id);
        row.put("price", price); row.put("upgrades", upgrades); row.put("on_sale", sale);
        row.put("sold_out", sold); row.put("affordable", price <= AbstractDungeon.player.gold);
        row.put("legal", enabled && price <= AbstractDungeon.player.gold && index >= 0);
        row.put("choice_index", index >= 0 ? index : null);
        return row;
    }
}
