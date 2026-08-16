package savestate;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class SaveStateCardIndexTest {
    @Test
    public void indexesCardsByIdentityAndKeepsFirstOccurrence() {
        String first = new String("same-card");
        String second = new String("same-card");

        java.util.IdentityHashMap<String, Integer> indices = SaveState
                .identityIndexMap(Arrays.asList(first, second, first));

        assertEquals(Integer.valueOf(0), indices.get(first));
        assertEquals(Integer.valueOf(1), indices.get(second));
        assertFalse(indices.containsKey(new String("same-card")));
    }

    @Test
    public void addCardByIdentityKeepsSingleInstancePerObject() {
        String card = new String("same-card");

        ArrayList<String> cards = new ArrayList<>();
        SaveState.addCardByIdentityIfMissing(cards, card);
        SaveState.addCardByIdentityIfMissing(cards, card);
        SaveState.addCardByIdentityIfMissing(cards, null);

        assertEquals(1, cards.size());
        assertSame(card, cards.get(0));
    }

    @Test
    public void addCardsByIdentityKeepsDistinctEqualInstances() {
        String first = new String("same-card");
        String second = new String("same-card");

        ArrayList<String> cards = new ArrayList<>();
        SaveState.addCardsByIdentityIfMissing(cards, Arrays.asList(first, second, first));

        assertEquals(2, cards.size());
        assertSame(first, cards.get(0));
        assertSame(second, cards.get(1));
    }

    @Test
    public void addCardsByIdentityToleratesNullCollections() {
        ArrayList<String> cards = new ArrayList<>();
        cards.add("existing");

        SaveState.addCardsByIdentityIfMissing(cards, null);

        assertEquals(1, cards.size());
    }
}
