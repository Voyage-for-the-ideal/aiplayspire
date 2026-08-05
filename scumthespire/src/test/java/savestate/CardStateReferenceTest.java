package savestate;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class CardStateReferenceTest {
    @Test
    public void prefersCardInUseWhenUuidMatches() {
        UUID uuid = UUID.randomUUID();
        CardReference cardInUse = new CardReference(uuid);
        CardReference duplicate = new CardReference(uuid);

        CardReference resolved = CardState.findReferenceByUuid(uuid, CardReference::getUuid, cardInUse,
                Arrays.asList(duplicate));

        assertSame(cardInUse, resolved);
    }

    @Test
    public void findsMatchingCardInCombatPiles() {
        UUID uuid = UUID.randomUUID();
        CardReference matchingCard = new CardReference(uuid);

        CardReference resolved = CardState.findReferenceByUuid(uuid, CardReference::getUuid, null,
                Collections.<CardReference>emptyList(), Arrays.asList(matchingCard));

        assertSame(matchingCard, resolved);
    }

    @Test
    public void returnsNullWhenNoExistingCardMatches() {
        CardReference otherCard = new CardReference(UUID.randomUUID());

        CardReference resolved = CardState.findReferenceByUuid(UUID.randomUUID(),
                CardReference::getUuid, null,
                Arrays.asList(otherCard));

        assertNull(resolved);
    }

    private static final class CardReference {
        private final UUID uuid;

        private CardReference(UUID uuid) {
            this.uuid = uuid;
        }

        private UUID getUuid() {
            return uuid;
        }
    }
}
