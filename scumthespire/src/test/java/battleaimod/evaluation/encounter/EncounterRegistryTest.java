package battleaimod.evaluation.encounter;

import battleaimod.evaluation.TestStateBuilder;
import org.junit.Test;
import savestate.SaveState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Registry resolution: shared stateless instances, default fallback. */
public class EncounterRegistryTest {

    @Test
    public void resolvesNobProfile() {
        TestStateBuilder.Monster nob = TestStateBuilder.attacking(80, 14);
        nob.id = "GremlinNob";
        SaveState state = TestStateBuilder.state(70, 70, nob);

        assertSame(GremlinNobProfile.INSTANCE, EncounterRegistry.resolve(state));
    }

    @Test
    public void resolvesLagavulinProfile() {
        SaveState state = TestStateBuilder.state(70, 70,
                TestStateBuilder.lagavulin(110, true));

        assertSame(LagavulinProfile.INSTANCE, EncounterRegistry.resolve(state));
    }

    @Test
    public void fallsBackToDefaultForUnknownEncounters() {
        SaveState state = TestStateBuilder.state(70, 70,
                TestStateBuilder.attacking(20, 6));
        assertSame(DefaultEncounterProfile.INSTANCE, EncounterRegistry.resolve(state));

        SaveState empty = TestStateBuilder.state(70, 70);
        assertSame(DefaultEncounterProfile.INSTANCE, EncounterRegistry.resolve(empty));
    }

    @Test
    public void defaultProfileScoresZero() {
        SaveState state = TestStateBuilder.state(70, 70,
                TestStateBuilder.attacking(20, 6));
        assertEquals(0, DefaultEncounterProfile.INSTANCE.evaluate(state, state, null));
    }

    @Test
    public void resolveReturnsSharedInstances() {
        SaveState state = TestStateBuilder.state(70, 70,
                TestStateBuilder.attacking(20, 6));
        assertTrue(EncounterRegistry.resolve(state) == EncounterRegistry.resolve(state));
    }
}
