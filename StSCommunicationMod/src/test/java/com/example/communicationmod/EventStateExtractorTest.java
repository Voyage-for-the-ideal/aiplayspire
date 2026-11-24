package com.example.communicationmod;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import com.megacrit.cardcrawl.events.AbstractEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventStateExtractorTest {
    @Test
    public void registersEveryConcreteVanillaEvent() {
        Set<String> expected = new HashSet<>(Arrays.asList(
            "BigFish", "Cleric", "DeadAdventurer", "GoldenIdolEvent", "GoldenWing", "GoopPuddle",
            "LivingWall", "Mushrooms", "ScrapOoze", "ShiningLight", "Sssserpent",
            "Addict", "BackToBasics", "Beggar", "Colosseum", "CursedTome", "DrugDealer",
            "ForgottenAltar", "Ghosts", "KnowingSkull", "MaskedBandits", "Nest", "TheJoust",
            "TheLibrary", "TheMausoleum", "Vampires", "Falling", "MindBloom", "MoaiHead",
            "MysteriousSphere", "SecretPortal", "SensoryStone", "SpireHeart", "TombRedMask",
            "WindingHalls", "AccursedBlacksmith", "Bonfire", "Designer", "Duplicator", "FaceTrader",
            "FountainOfCurseRemoval", "GoldShrine", "GremlinMatchGame", "GremlinWheelGame", "Lab",
            "Nloth", "NoteForYourself", "PurificationShrine", "Transmogrifier", "UpgradeShrine",
            "WeMeetAgain", "WomanInBlue"
        ));
        assertEquals(52, EventStateExtractor.registeredEventClasses().size());
        assertEquals(expected, EventStateExtractor.registeredEventClasses());
        assertEquals(expected, EventStateExtractor.registeredEventIds().keySet());
    }

    @Test
    public void preservesRawIndexesAndCompressesOnlyActionIndexes() {
        ArrayList<LargeDialogOptionButton> buttons = new ArrayList<>();
        buttons.add(new LargeDialogOptionButton(0, "first"));
        buttons.add(new LargeDialogOptionButton(1, "disabled", true));
        buttons.add(new LargeDialogOptionButton(2, "third"));

        List<Map<String, Object>> choices = EventStateExtractor.baseChoices(buttons);
        assertEquals(0, choices.get(0).get("button_index"));
        assertEquals(0, choices.get(0).get("action_index"));
        assertEquals(1, choices.get(1).get("button_index"));
        assertNull(choices.get(1).get("action_index"));
        assertEquals(2, choices.get(2).get("button_index"));
        assertEquals(1, choices.get(2).get("action_index"));
    }

    @Test
    public void unknownModEventIsExplicitlyUnknown() {
        Map<String, Object> result = EventStateExtractor.extract(new ModdedEvent(), buttons("mystery"));
        assertEquals("UNKNOWN", result.get("semantics_status"));
        assertEquals("UNKNOWN", result.get("decision_kind"));
    }

    @Test
    public void reflectionFailureDoesNotReturnPartialKnownSemantics() {
        Map<String, Object> result = EventStateExtractor.extract(new Cleric(), buttons("heal", "purify", "leave"));
        assertEquals("UNKNOWN", result.get("semantics_status"));
        assertEquals("UNKNOWN", result.get("decision_kind"));
    }

    @Test
    public void livingWallHasLocaleIndependentGridEffects() {
        Map<String, Object> result = EventStateExtractor.extract(
            new LivingWall(), buttons("遗忘", "改变", "成长"));
        assertEquals("KNOWN", result.get("semantics_status"));
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        assertEquals("REMOVE_CARD", choices.get(0).get("kind"));
        assertEquals("GRID", choices.get(0).get("followup"));
        assertEquals("TRANSFORM_CARD", choices.get(1).get("kind"));
        assertTrue(((List<?>) choices.get(1).get("outcomes")).size() == 1);
        assertEquals("UPGRADE_CARD", choices.get(2).get("kind"));
    }

    @Test
    public void libraryDeclaresItsGeneratedCardGridFollowup() {
        Map<String, Object> result = EventStateExtractor.extract(
            new TheLibrary(), buttons("read", "sleep"));
        assertEquals("KNOWN", result.get("semantics_status"));
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        assertEquals("GENERATED_CARD_GRID", choices.get(0).get("followup"));
    }

    @Test
    public void libraryPredictsArchivedDefectOfferInGridOrderWithoutAdvancingRng() {
        long s0 = 0xd4918c24c8c09813L, s1 = 0x4ee34a2a21e55228L;
        RandomXS128 source = new RandomXS128(s0, s1);
        RandomXS128 global = (RandomXS128) MathUtils.random;
        long g0 = global.getState(0), g1 = global.getState(1);
        List<String> common = pool("Steam|Cold Snap|Leap|Beam Cell|Hologram|Conserve Battery|Sweeping Beam|Coolheaded|Gash|Stack|Barrage|Compile Driver|Redo|Streamline|Ball Lightning|Go for the Eyes");
        List<String> uncommon = pool("Doom and Gloom|Defragment|Capacitor|White Noise|Skim|Scrape|Lockon|Reprogram|Auto Shields|Reinforced Body|Double Energy|Darkness|Rip and Tear|FTL|Force Field|Tempest|Heatsinks|Static Discharge|BootSequence|Chill|Loop|Self Repair|Melter|Chaos|Blizzard|Aggregate|Fusion|Consume|Glacier|Hello World|Steam Power|Genetic Algorithm|Storm");
        List<String> rare = pool("Multi-Cast|Thunder Strike|Biased Cognition|Machine Learning|Electrodynamics|Buffer|Rainbow|Seek|All For One|Reboot|Amplify|Creative AI|Fission");
        List<String> expected = Arrays.asList("FTL", "Heatsinks", "Melter", "Doom and Gloom", "Capacitor",
            "Skim", "Compile Driver", "Ball Lightning", "Fusion", "Barrage", "Blizzard", "Self Repair",
            "Hologram", "Beam Cell", "Scrape", "Gash", "Force Field", "Steam", "Sweeping Beam", "Conserve Battery");
        for (int poll = 0; poll < 2; poll++) {
            assertEquals(expected, EventStateExtractor.predictLibraryCards(source, 4, 3, 37, common, uncommon, rare));
        }
        assertEquals(s0, source.getState(0));
        assertEquals(s1, source.getState(1));
        assertEquals(g0, global.getState(0));
        assertEquals(g1, global.getState(1));
    }

    private static List<String> pool(String reversedOrder) {
        List<String> cards = Arrays.asList(reversedOrder.split("\\|"));
        Collections.reverse(cards);
        return cards;
    }

    @Test
    public void knowingSkullPredictsRepeatedVanillaDrawsWithoutMutatingPoolOrRng() {
        com.megacrit.cardcrawl.random.Random live = new com.megacrit.cardcrawl.random.Random(527L);
        RandomXS128 global = (RandomXS128) MathUtils.random;
        long g0 = global.getState(0), g1 = global.getState(1);
        List<String> livePool = new ArrayList<>(Arrays.asList(
            "rare:Hand of Greed", "Trip", "Flash of Steel", "rare:Apotheosis", "Panacea"));
        for (int draw = 0; draw < 5; draw++) {
            List<String> before = new ArrayList<>(livePool);
            long s0 = live.random.getState(0), s1 = live.random.getState(1);
            String preview = EventStateExtractor.firstAfterColorlessShuffle(
                live.random, livePool, card -> !card.startsWith("rare:"));
            assertEquals(preview, EventStateExtractor.firstAfterColorlessShuffle(
                live.random, livePool, card -> !card.startsWith("rare:")));
            assertEquals(draw, live.counter);
            assertEquals(s0, live.random.getState(0));
            assertEquals(s1, live.random.getState(1));
            assertEquals(before, livePool);

            // The vanilla draw consumes the real seed and leaves the pool shuffled.
            Collections.shuffle(livePool, new java.util.Random(live.randomLong()));
            String actual = livePool.stream().filter(card -> !card.startsWith("rare:")).findFirst().get();
            assertEquals(actual, preview);
        }
        assertEquals(g0, global.getState(0));
        assertEquals(g1, global.getState(1));
    }

    @Test
    public void cursedTomeIntroExposesCommitDecisionMetadata() {
        Map<String, Object> result = EventStateExtractor.extract(
            new CursedTome(), buttons("read", "leave"));
        assertEquals("KNOWN", result.get("semantics_status"));
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        Map<String, Object> outcome = (Map<String, Object>) ((List<?>) choices.get(0).get("outcomes")).get(0);
        Map<String, Object> effect = (Map<String, Object>) ((List<?>) outcome.get("effects")).get(0);
        assertEquals("commit_reading", effect.get("type"));
        assertEquals(6, ((Number) effect.get("unavoidable_hp_loss")).intValue());
        assertEquals(15, ((Number) effect.get("final_dmg")).intValue());
        assertTrue(effect.containsKey("book_relics"));
        assertTrue(effect.get("book_relics") instanceof List);
    }

    @Test
    public void colosseumPostCombatMatchesVanillaButtonOrder() {
        Map<String, Object> result = EventStateExtractor.extract(new Colosseum(), buttons("leave", "fight"));
        assertEquals("KNOWN", result.get("semantics_status"));
        assertEquals("COMPLEX", result.get("decision_kind"));
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        // Vanilla Colosseum.buttonEffect: button 0 leaves, button 1 fights the Nobs.
        assertEquals("LEAVE", choices.get(0).get("kind"));
        assertEquals(0, choices.get(0).get("action_index"));
        Map<String, Object> leaveOutcome = (Map<String, Object>) ((List<?>) choices.get(0).get("outcomes")).get(0);
        assertTrue(((List<?>) leaveOutcome.get("effects")).isEmpty());
        assertEquals("COMBAT", choices.get(1).get("kind"));
        assertEquals(1, choices.get(1).get("action_index"));
        Map<String, Object> fightOutcome = (Map<String, Object>) ((List<?>) choices.get(1).get("outcomes")).get(0);
        Map<String, Object> effect = (Map<String, Object>) ((List<?>) fightOutcome.get("effects")).get(0);
        assertEquals("start_combat", effect.get("type"));
    }

    private static ArrayList<LargeDialogOptionButton> buttons(String... labels) {
        ArrayList<LargeDialogOptionButton> result = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) result.add(new LargeDialogOptionButton(i, labels[i]));
        return result;
    }

    private static class ModdedEvent extends AbstractEvent {
        @Override protected void buttonEffect(int buttonPressed) {}
    }

    private static class Cleric extends AbstractEvent {
        @SuppressWarnings("unused") private int screenNum = 0;
        @Override protected void buttonEffect(int buttonPressed) {}
    }

    private static class LivingWall extends AbstractEvent {
        @SuppressWarnings("unused") private String screen = "INTRO";
        @Override protected void buttonEffect(int buttonPressed) {}
    }


    private static class TheLibrary extends AbstractEvent {
        @SuppressWarnings("unused") private int screenNum = 0;
        @SuppressWarnings("unused") private int healAmt = 20;
        @Override protected void buttonEffect(int buttonPressed) {}
    }

    private static class CursedTome extends AbstractEvent {
        @SuppressWarnings("unused") private String screen = "INTRO";
        @SuppressWarnings("unused") private int finalDmg = 15;
        @Override protected void buttonEffect(int buttonPressed) {}
    }

    private static class Colosseum extends AbstractEvent {
        @SuppressWarnings("unused") private String screen = "POST_COMBAT";
        @Override protected void buttonEffect(int buttonPressed) {}
    }
}
