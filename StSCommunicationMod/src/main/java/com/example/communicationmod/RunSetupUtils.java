package com.example.communicationmod;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.screens.GameOverScreen;
import com.megacrit.cardcrawl.screens.charSelect.CharacterOption;
import com.megacrit.cardcrawl.screens.charSelect.CharacterSelectScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.ui.buttons.ReturnToMenuButton;

import java.util.ArrayList;

/**
 * Out-of-run lifecycle helpers: main menu -> character select -> run start,
 * plus the death/victory "return to menu" click. Every method must run on the
 * game thread (via ActionController.processQueue) or read-only from the state
 * builder; they never touch AbstractDungeon rooms.
 */
public class RunSetupUtils {

    /** True when the game sits in the controllable menu/char-select flow. */
    public static boolean isMenuAvailable() {
        return !CardCrawlGame.isInARun()
                && CardCrawlGame.mode == CardCrawlGame.GameMode.CHAR_SELECT
                && CardCrawlGame.mainMenuScreen != null;
    }

    public static MainMenuScreen menu() {
        return CardCrawlGame.mainMenuScreen;
    }

    public static CharacterSelectScreen charSelect() {
        return menu().charSelectScreen;
    }

    /** Character class names in display order; locked characters are not advertised. */
    public static ArrayList<String> getCharSelectChoices() {
        ArrayList<String> choices = new ArrayList<>();
        if (!isMenuAvailable() || menu().screen != MainMenuScreen.CurScreen.CHAR_SELECT) {
            return choices;
        }
        for (CharacterOption option : charSelect().options) {
            if (!option.locked) {
                choices.add(option.c.chosenClass.name());
            }
        }
        return choices;
    }

    public static boolean isCharSelected() {
        return selectedOption() != null;
    }

    public static String getSelectedCharacterName() {
        CharacterOption option = selectedOption();
        return option == null ? null : option.c.chosenClass.name();
    }

    /** Unlocked ascension cap of the selected character, null when none selected. */
    public static Integer getSelectedMaxAscension() {
        CharacterOption option = selectedOption();
        if (option == null) {
            return null;
        }
        Object max = ReflectionHacks.getPrivate(option, CharacterOption.class, "maxAscensionLevel");
        return max instanceof Integer ? (Integer) max : null;
    }

    /** choose on CHAR_SELECT: pick the advertised (unlocked) option at choiceIndex. */
    public static void selectCharacter(int choiceIndex) {
        ArrayList<CharacterOption> unlocked = unlockedOptions();
        if (choiceIndex < 0 || choiceIndex >= unlocked.size()) {
            System.err.println("Character choice index out of bounds: " + choiceIndex);
            return;
        }
        CharacterOption option = unlocked.get(choiceIndex);
        System.out.println("Selecting character: " + option.c.chosenClass.name());
        option.hb.clicked = true;
    }

    /**
     * set_ascension: enable ascension mode and set the level directly, clamped
     * to the character's unlocked cap. Writes the same fields the vanilla
     * ascension arrows write, so the confirm path reads identical state.
     */
    public static void setAscension(int level) {
        if (!isMenuAvailable() || menu().screen != MainMenuScreen.CurScreen.CHAR_SELECT) {
            System.err.println("Cannot set ascension: character select screen is not up.");
            return;
        }
        CharacterOption selected = selectedOption();
        if (selected == null) {
            System.err.println("Cannot set ascension: no character selected yet.");
            return;
        }
        if (!UnlockTracker.isAscensionUnlocked(selected.c)) {
            System.err.println("Ascension mode is not unlocked for " + selected.c.chosenClass.name() + ".");
            return;
        }
        int target = level;
        Integer max = getSelectedMaxAscension();
        if (max != null && target > max) {
            System.out.println("Clamping ascension " + level + " to unlocked max " + max);
            target = max;
        }
        if (target < 1) {
            // Ascension 0 = plain run: turn the ascension toggle off instead.
            charSelect().isAscensionMode = false;
            System.out.println("Ascension mode disabled (level " + level + " requested).");
            return;
        }
        CharacterSelectScreen screen = charSelect();
        screen.isAscensionMode = true;
        screen.ascensionLevel = target;
        if (target - 1 >= 0 && target - 1 < CharacterSelectScreen.A_TEXT.length) {
            screen.ascLevelInfoString = CharacterSelectScreen.A_TEXT[target - 1];
        }
        selected.saveChosenAscensionLevel(target);
        System.out.println("Ascension set to " + target + " for " + selected.c.chosenClass.name());
    }

    /** proceed on CHAR_SELECT: press Begin (only with a character picked, and not already starting). */
    public static void confirmCharacterSelect() {
        if (!isMenuAvailable() || menu().screen != MainMenuScreen.CurScreen.CHAR_SELECT) {
            return;
        }
        if (menu().isFadingOut) {
            return; // run is already starting
        }
        if (!isCharSelected()) {
            System.err.println("Cannot begin run: no character selected.");
            return;
        }
        charSelect().confirmButton.hb.clicked = true;
    }

    /**
     * choose "play" on MAIN_MENU: a leftover save blocks starting a new run in
     * vanilla (menu only offers resume/abandon), so delete it the same way the
     * abandon-confirm popup does, then open the character select directly
     * (skipping the play-panel submenu).
     */
    public static void openCharSelectFromMenu() {
        if (!isMenuAvailable() || menu().screen != MainMenuScreen.CurScreen.MAIN_MENU) {
            return;
        }
        if (CardCrawlGame.characterManager.anySaveFileExists()) {
            AbstractPlayer withSave = CardCrawlGame.characterManager.loadChosenCharacter();
            if (withSave != null) {
                System.out.println("Abandoning leftover save of " + withSave.chosenClass.name()
                        + " before starting a new run.");
                SaveAndContinue.deleteSave(withSave);
                menu().abandonedRun = true; // rebuilds the menu buttons next frame
            }
        }
        menu().charSelectScreen.open(false);
    }

    /**
     * choose on GAME_OVER: death/victory -> the return-to-menu button (vanilla
     * metric/stat handling stays intact); the unlock screens that appear during
     * the same return flow -> their confirm button.
     */
    public static void makeGameOverChoice() {
        if (AbstractDungeon.screen == null) {
            return;
        }
        switch (AbstractDungeon.screen) {
            case DEATH:
                clickReturnButton(AbstractDungeon.deathScreen);
                break;
            case VICTORY:
                clickReturnButton(AbstractDungeon.victoryScreen);
                break;
            case UNLOCK:
                if (AbstractDungeon.unlockScreen != null) {
                    AbstractDungeon.unlockScreen.button.hb.clicked = true;
                }
                break;
            case NEOW_UNLOCK:
                if (AbstractDungeon.gUnlockScreen != null) {
                    AbstractDungeon.gUnlockScreen.button.hb.clicked = true;
                }
                break;
            default:
                break;
        }
    }

    /**
     * Clicks the return button of a death/victory screen. The field is declared
     * as protected on the shared GameOverScreen superclass (DeathScreen and
     * VictoryScreen both extend it), so the reflection target must be that
     * superclass: getDeclaredField does not look up inherited fields and would
     * otherwise throw NoSuchFieldException (which is why the click silently did
     * nothing before 1.4.2).
     */
    private static void clickReturnButton(Object screen) {
        if (screen == null) {
            return;
        }
        Object buttonObj = ReflectionHacks.getPrivate(screen, GameOverScreen.class, "returnButton");
        if (buttonObj instanceof ReturnToMenuButton) {
            ReturnToMenuButton button = (ReturnToMenuButton) buttonObj;
            if (button.show) {
                button.hb.clicked = true;
            } else {
                System.out.println("Return-to-menu button not visible yet; the agent will retry.");
            }
        }
    }

    private static CharacterOption selectedOption() {
        if (!isMenuAvailable() || menu().screen != MainMenuScreen.CurScreen.CHAR_SELECT) {
            return null;
        }
        for (CharacterOption option : charSelect().options) {
            if (option.selected) {
                return option;
            }
        }
        return null;
    }

    private static ArrayList<CharacterOption> unlockedOptions() {
        ArrayList<CharacterOption> unlocked = new ArrayList<>();
        if (!isMenuAvailable() || menu().screen != MainMenuScreen.CurScreen.CHAR_SELECT) {
            return unlocked;
        }
        for (CharacterOption option : charSelect().options) {
            if (!option.locked) {
                unlocked.add(option);
            }
        }
        return unlocked;
    }
}
