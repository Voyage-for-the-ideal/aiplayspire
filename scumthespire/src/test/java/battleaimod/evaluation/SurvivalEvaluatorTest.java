package battleaimod.evaluation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Unit tests for the non-linear survival curve. */
public class SurvivalEvaluatorTest {

    @Test
    public void deadIsWorseThanAnyLivingState() {
        assertEquals(TacticalEvaluator.DEATH_PENALTY, SurvivalEvaluator.survivalScore(0, 70, 0));
        assertTrue(SurvivalEvaluator.survivalScore(1, 70, 0) > TacticalEvaluator.DEATH_PENALTY);
        assertTrue(SurvivalEvaluator.survivalScore(70, 70, 0) > TacticalEvaluator.DEATH_PENALTY);
    }

    @Test
    public void lowHpLossesCostMoreThanHealthyLosses() {
        int healthyLoss = SurvivalEvaluator.survivalScore(30, 70, 0)
                - SurvivalEvaluator.survivalScore(26, 70, 0);
        int nearDeathLoss = SurvivalEvaluator.survivalScore(5, 70, 0)
                - SurvivalEvaluator.survivalScore(1, 70, 0);

        // 30->26 is entirely above the danger line; 5->1 is entirely below it
        assertTrue(nearDeathLoss > healthyLoss);
        assertTrue(nearDeathLoss >= 3 * healthyLoss);
    }

    @Test
    public void incomingDamagePushesHpIntoDangerZones() {
        // With 18 incoming damage, the danger line rises to 18: 30 HP is then in
        // the danger zone and each lost HP costs more than without the hit.
        int lossWithoutIncoming = SurvivalEvaluator.survivalScore(30, 70, 0)
                - SurvivalEvaluator.survivalScore(26, 70, 0);
        int lossWithIncoming = SurvivalEvaluator.survivalScore(30, 70, 18)
                - SurvivalEvaluator.survivalScore(26, 70, 18);
        assertTrue("incoming damage must raise the cost of lost HP",
                lossWithIncoming > lossWithoutIncoming);
    }

    @Test
    public void fullHpIsNotAnAbsoluteGoal() {
        // Safe-zone HP is cheap: losing 6 HP while healthy costs less than the
        // value of removing an 18-damage attacker (see TacticalEvaluator
        // weights: 18 * IMMEDIATE_THREAT_WEIGHT), so tempo plays can win.
        int sixHpLoss = SurvivalEvaluator.survivalScore(70, 70, 0)
                - SurvivalEvaluator.survivalScore(64, 70, 0);
        assertTrue(sixHpLoss < 18 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT);
    }

    @Test
    public void statusBands() {
        assertEquals(SurvivalEvaluator.Status.DEAD, SurvivalEvaluator.status(0, 70, 0));
        assertEquals(SurvivalEvaluator.Status.CRITICAL, SurvivalEvaluator.status(5, 70, 0));
        assertEquals(SurvivalEvaluator.Status.DANGER, SurvivalEvaluator.status(25, 70, 0));
        assertEquals(SurvivalEvaluator.Status.SAFE, SurvivalEvaluator.status(60, 70, 0));
    }
}
