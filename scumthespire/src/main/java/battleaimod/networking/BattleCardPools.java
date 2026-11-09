package battleaimod.networking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.core.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Preserve the actual Java pool iteration order; enum/alphabetical order changes RNG outcomes. */
public final class BattleCardPools {
    private BattleCardPools() { }

    public static JsonObject current() {
        JsonArray any = new JsonArray(), attacks = new JsonArray(), skills = new JsonArray();
        JsonArray powers = new JsonArray(), colorless = new JsonArray();
        for (CardGroup group : new CardGroup[]{AbstractDungeon.srcCommonCardPool,
                AbstractDungeon.srcUncommonCardPool, AbstractDungeon.srcRareCardPool}) {
            for (AbstractCard card : group.group) {
                if (card.hasTag(AbstractCard.CardTags.HEALING)) { continue; }
                any.add(card.cardID);
                switch (card.type) {
                    case ATTACK: attacks.add(card.cardID); break;
                    case SKILL: skills.add(card.cardID); break;
                    case POWER: powers.add(card.cardID); break;
                    default: break;
                }
            }
        }
        for (AbstractCard card : AbstractDungeon.srcColorlessCardPool.group) {
            if (!card.hasTag(AbstractCard.CardTags.HEALING)) { colorless.add(card.cardID); }
        }
        JsonObject result = new JsonObject();
        result.addProperty("fast_hand_confirm", Settings.FAST_HAND_CONF);
        result.add("any", any); result.add("attacks", attacks); result.add("skills", skills);
        result.add("powers", powers); result.add("colorless", colorless);
        String[] rarities = {"common", "uncommon", "rare"};
        CardGroup[] groups = {AbstractDungeon.srcCommonCardPool, AbstractDungeon.srcUncommonCardPool, AbstractDungeon.srcRareCardPool};
        for (int i = 0; i < groups.length; ++i) {
            JsonArray cards = new JsonArray();
            for (AbstractCard card : groups[i].group) { cards.add(card.cardID); }
            result.add(rarities[i], cards);
        }
        AbstractCard.CardRarity[] libraryRarities = {AbstractCard.CardRarity.COMMON,
                AbstractCard.CardRarity.UNCOMMON, AbstractCard.CardRarity.RARE};
        for (int i = 0; i < libraryRarities.length; ++i) {
            List<String> ids = new ArrayList<>();
            for (java.util.Map.Entry<String, AbstractCard> entry : CardLibrary.cards.entrySet()) {
                AbstractCard card = entry.getValue();
                if (card.type == AbstractCard.CardType.ATTACK && card.rarity == libraryRarities[i]
                        && !card.hasTag(AbstractCard.CardTags.HEALING)
                        && (!UnlockTracker.isCardLocked(entry.getKey()) || Settings.treatEverythingAsUnlocked())) {
                    ids.add(card.cardID);
                }
            }
            Collections.sort(ids); // AbstractCard.compareTo, after getAnyColorCard's shuffle.
            JsonArray cards = new JsonArray();
            for (String id : ids) cards.add(id);
            result.add("library_attacks_" + rarities[i], cards);
        }
        return result;
    }
}
