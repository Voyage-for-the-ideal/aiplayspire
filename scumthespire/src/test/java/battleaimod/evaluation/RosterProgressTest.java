package battleaimod.evaluation;

import org.junit.Test;
import savestate.SaveState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Roster-independent enemy burden progress invariants: no index alignment, no
 * monster id matching, no fixed-roster assumption.  Summons, splits, reorders
 * and kills must all be handled by the aggregate burden alone.
 */
public class RosterProgressTest {

    private static int deltaOf(SaveState root, SaveState current) {
        return CombatFeatures.extract(current, root, 70).enemyBurdenDelta;
    }

    private static List<TestStateBuilder.Monster> monsters(TestStateBuilder.Monster... m) {
        return new ArrayList<>(Arrays.asList(m));
    }

    // A - monster reorder does not affect progress
    @Test
    public void monsterReorderDoesNotAffectProgress() {
        TestStateBuilder.Monster a = TestStateBuilder.monster(50);
        TestStateBuilder.Monster b = TestStateBuilder.monster(30);
        SaveState root = TestStateBuilder.state(70, 70, monsters(a, b));

        // Same roster, different order, same damage dealt
        SaveState reordered = TestStateBuilder.state(70, 70,
                monsters(TestStateBuilder.monster(20), TestStateBuilder.monster(40)));
        SaveState originalOrder = TestStateBuilder.state(70, 70,
                monsters(TestStateBuilder.monster(40), TestStateBuilder.monster(20)));

        assertEquals(20, deltaOf(root, reordered));
        assertEquals(deltaOf(root, reordered), deltaOf(root, originalOrder));
    }

    // B - a summoned monster must not create fake progress
    @Test
    public void summonDoesNotCreateFakeProgress() {
        TestStateBuilder.Monster a = TestStateBuilder.monster(100);
        SaveState root = TestStateBuilder.state(70, 70, monsters(a));

        // A took 30 damage but a 40 HP summon appeared: burden 110 > root 100
        SaveState withSummon = TestStateBuilder.state(70, 70,
                monsters(TestStateBuilder.monster(70), TestStateBuilder.monster(40)));
        assertEquals(-10, deltaOf(root, withSummon));

        // And damage to the original monster still counts
        SaveState withoutSummon = TestStateBuilder.state(70, 70,
                monsters(TestStateBuilder.monster(70)));
        assertEquals(30, deltaOf(root, withoutSummon));
    }

    // C - a killed monster correctly reduces burden
    @Test
    public void killedMonsterReducesBurden() {
        TestStateBuilder.Monster a = TestStateBuilder.monster(20);
        TestStateBuilder.Monster b = TestStateBuilder.monster(30);
        SaveState root = TestStateBuilder.state(70, 70, monsters(a, b));

        SaveState aDead = TestStateBuilder.state(70, 70,
                monsters(TestStateBuilder.monster(0), TestStateBuilder.monster(30)));
        assertEquals(20, deltaOf(root, aDead));
    }

    // D - overkill still earns nothing extra
    @Test
    public void overkillStillEarnsNothing() {
        TestStateBuilder.Monster a = TestStateBuilder.monster(5);
        SaveState root = TestStateBuilder.state(70, 70, monsters(a));

        SaveState exactlyKilled = TestStateBuilder.state(70, 70, monsters(TestStateBuilder.monster(0)));
        SaveState massivelyOverkilled = TestStateBuilder.state(70, 70,
                monsters(TestStateBuilder.monster(-95)));

        assertEquals(5, deltaOf(root, exactlyKilled));
        assertEquals(deltaOf(root, exactlyKilled), deltaOf(root, massivelyOverkilled));
    }

    // Split semantics: crossing the split line costs tempo, deep splits cost less
    @Test
    public void splitRaisesBurdenAndDeepSplitCostsLess() {
        TestStateBuilder.Monster boss = TestStateBuilder.monster(150);
        SaveState root = TestStateBuilder.state(70, 70, monsters(boss));

        // Just before the split: boss at 30 -> 120 progress
        SaveState preSplit = TestStateBuilder.state(70, 70, monsters(TestStateBuilder.monster(30)));
        assertEquals(120, deltaOf(root, preSplit));

        // Shallow split: boss 74 -> children 74 + 74 = 148 burden -> progress 2
        SaveState shallowSplit = TestStateBuilder.state(70, 70,
                monsters(TestStateBuilder.monster(0),
                        TestStateBuilder.monster(74), TestStateBuilder.monster(74)));
        assertEquals(2, deltaOf(root, shallowSplit));

        // Deep split: boss 30 -> children 30 + 30 = 60 burden -> progress 90
        SaveState deepSplit = TestStateBuilder.state(70, 70,
                monsters(TestStateBuilder.monster(0),
                        TestStateBuilder.monster(30), TestStateBuilder.monster(30)));
        assertEquals(90, deltaOf(root, deepSplit));
    }
}
