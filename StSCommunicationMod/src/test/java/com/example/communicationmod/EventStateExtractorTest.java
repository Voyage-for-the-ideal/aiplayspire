package com.example.communicationmod;

import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import com.megacrit.cardcrawl.events.AbstractEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
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
}
