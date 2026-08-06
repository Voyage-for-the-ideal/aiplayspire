package savestate;

import org.junit.Test;

import java.util.Arrays;
import java.util.IdentityHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SaveStateCardIndexTest {
    @Test
    public void indexesCardsByIdentityAndKeepsFirstOccurrence() {
        String first = new String("same-card");
        String second = new String("same-card");

        IdentityHashMap<String, Integer> indices = SaveState
                .identityIndexMap(Arrays.asList(first, second, first));

        assertEquals(Integer.valueOf(0), indices.get(first));
        assertEquals(Integer.valueOf(1), indices.get(second));
        assertFalse(indices.containsKey(new String("same-card")));
    }
}
