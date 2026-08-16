package battleaimod.evaluation.encounter;

import battleaimod.evaluation.EvaluationResult;
import battleaimod.evaluation.TacticalEvaluator;
import battleaimod.evaluation.TestStateBuilder;
import org.junit.Test;
import savestate.SaveState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Slime Boss split breakpoint with the real monster state classes.
 * <p>
 * Split mechanics (verified against the game bytecode): when the boss drops to
 * half HP it announces SPLIT, then takes a turn to suicide while spawning
 * SpikeSlime_L and AcidSlime_L, each with HP equal to the boss's current HP at
 * split time.  The dead boss stays in the monster list; the children are
 * appended at the end.
 * <p>
 * Under roster-safe burden progress, shallow splits keep almost all the burden
 * (74+74 of 150), deep splits remove most of it (30+30), so the generic
 * evaluator alone should prefer the deep split - no SlimeBossProfile needed.
 */
public class SlimeBossTest {

    private static TestStateBuilder.Monster boss(int hp) {
        TestStateBuilder.Monster m = TestStateBuilder.monster(hp);
        m.id = "SlimeBoss";
        m.intent = "STRONG_DEBUFF";
        return m;
    }

    private static TestStateBuilder.Monster child(String id, int hp) {
        TestStateBuilder.Monster m = TestStateBuilder.attacking(hp, 8);
        m.id = id;
        return m;
    }

    private static SaveState splitState(int bossSplitHp) {
        List<TestStateBuilder.Monster> monsters = new ArrayList<>();
        monsters.add(boss(0)); // dead boss stays in monsterData (verified)
        monsters.add(child("SpikeSlime_L", bossSplitHp));
        monsters.add(child("AcidSlime_L", bossSplitHp));
        return TestStateBuilder.state(70, 70, monsters);
    }

    @Test
    public void deepSplitBeatsShallowSplit() {
        SaveState root = TestStateBuilder.state(70, 70, boss(150));

        EvaluationResult unsplit = TacticalEvaluator.evaluate(root, root, 70);
        EvaluationResult shallow = TacticalEvaluator.evaluate(root, splitState(74), 70);
        EvaluationResult deep = TacticalEvaluator.evaluate(root, splitState(30), 70);

        assertTrue("deep split must beat shallow split: " + deep.totalScore + " vs "
                + shallow.totalScore, deep.totalScore > shallow.totalScore);
        assertTrue("shallow split keeps most of the burden: " + unsplit.totalScore + " vs "
                + shallow.totalScore, unsplit.totalScore > shallow.totalScore);
        assertEquals(2, shallow.damageProgressScore / TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT);
        assertEquals(90, deep.damageProgressScore / TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT);
        assertEquals("shallow and deep splits face identical threats",
                shallow.threatScore, deep.threatScore);
    }

    @Test
    public void slimeBossUsesDefaultEncounterProfile() {
        // The generic evaluator captures the split breakpoint, so no profile:
        // the dead boss still resolves through the registry but falls back.
        SaveState root = TestStateBuilder.state(70, 70, boss(150));
        SaveState split = splitState(30);

        assertSame(DefaultEncounterProfile.INSTANCE, EncounterRegistry.resolve(split));
        EvaluationResult result = TacticalEvaluator.evaluate(root, split, 70);
        assertEquals(0, result.encounterScore);
    }

    /** Records the breakdown for the report. */
    @Test
    public void recordSplitBreakdowns() {
        SaveState root = TestStateBuilder.state(70, 70, boss(150));
        EvaluationResult unsplit = TacticalEvaluator.evaluate(root, root, 70);
        EvaluationResult shallow = TacticalEvaluator.evaluate(root, splitState(74), 70);
        EvaluationResult deep = TacticalEvaluator.evaluate(root, splitState(30), 70);

        System.out.println("SLIME unsplit : " + unsplit);
        System.out.println("SLIME shallow : " + shallow);
        System.out.println("SLIME deep    : " + deep);
    }

    /** Registry lifecycle observation: the dead boss stays in monsterData. */
    @Test
    public void deadBossRemainsInMonsterDataAfterSplit() {
        SaveState split = splitState(74);
        assertEquals(3, split.curMapNodeState.monsterData.size());
        assertEquals("SlimeBoss", split.curMapNodeState.monsterData.get(0).id);
        assertEquals(0, split.curMapNodeState.monsterData.get(0).currentHealth);
        assertEquals("SpikeSlime_L", split.curMapNodeState.monsterData.get(1).id);
        assertEquals(74, split.curMapNodeState.monsterData.get(1).currentHealth);
        assertEquals("AcidSlime_L", split.curMapNodeState.monsterData.get(2).id);
        assertEquals(74, split.curMapNodeState.monsterData.get(2).currentHealth);
    }
}
