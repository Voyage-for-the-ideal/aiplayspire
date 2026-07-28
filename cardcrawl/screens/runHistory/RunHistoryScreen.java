package com.megacrit.cardcrawl.screens.runHistory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.GameCursor;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.metrics.Metrics;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Circlet;
import com.megacrit.cardcrawl.screens.SingleCardViewPopup;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import com.megacrit.cardcrawl.screens.options.DropdownMenu;
import com.megacrit.cardcrawl.screens.options.DropdownMenuListener;
import com.megacrit.cardcrawl.screens.stats.CharStat;
import com.megacrit.cardcrawl.screens.stats.RunData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/* loaded from: desktop-1.0.jar:com/megacrit/cardcrawl/screens/runHistory/RunHistoryScreen.class */
public class RunHistoryScreen implements DropdownMenuListener {
    private static final boolean SHOULD_SHOW_PATH = true;
    private DropdownMenu characterFilter;
    private DropdownMenu winLossFilter;
    private DropdownMenu runTypeFilter;
    private DropdownMenu runsDropdown;
    private static final float ARROW_SIDE_PADDING = 180.0f;
    private Hitbox currentHb;
    private static final Logger logger = LogManager.getLogger(RunHistoryScreen.class.getName());
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("RunHistoryScreen");
    public static final String[] TEXT = uiStrings.TEXT;
    private static final AbstractCard.CardRarity[] orderedRarity = {AbstractCard.CardRarity.SPECIAL,
            AbstractCard.CardRarity.RARE, AbstractCard.CardRarity.UNCOMMON, AbstractCard.CardRarity.COMMON,
            AbstractCard.CardRarity.BASIC, AbstractCard.CardRarity.CURSE };
    private static final AbstractRelic.RelicTier[] orderedRelicRarity = {AbstractRelic.RelicTier.BOSS,
            AbstractRelic.RelicTier.SPECIAL, AbstractRelic.RelicTier.RARE, AbstractRelic.RelicTier.SHOP,
            AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.RelicTier.COMMON, AbstractRelic.RelicTier.STARTER,
            AbstractRelic.RelicTier.DEPRECATED };
    private static final String IRONCLAD_NAME = TEXT[0];
    private static final String SILENT_NAME = TEXT[1];
    private static final String DEFECT_NAME = TEXT[2];
    private static final String WATCHER_NAME = TEXT[35];
    private static final String ALL_CHARACTERS_TEXT = TEXT[23];
    private static final String WINS_AND_LOSSES_TEXT = TEXT[24];
    private static final String WINS_TEXT = TEXT[25];
    private static final String LOSSES_TEXT = TEXT[26];
    private static final String RUN_TYPE_ALL = TEXT[28];
    private static final String RUN_TYPE_NORMAL = TEXT[29];
    private static final String RUN_TYPE_ASCENSION = TEXT[30];
    private static final String RUN_TYPE_DAILY = TEXT[31];
    private static final String RARITY_LABEL_STARTER = TEXT[11];
    private static final String RARITY_LABEL_COMMON = TEXT[12];
    private static final String RARITY_LABEL_UNCOMMON = TEXT[13];
    private static final String RARITY_LABEL_RARE = TEXT[14];
    private static final String RARITY_LABEL_SPECIAL = TEXT[15];
    private static final String RARITY_LABEL_CURSE = TEXT[16];
    private static final String RARITY_LABEL_BOSS = TEXT[17];
    private static final String RARITY_LABEL_SHOP = TEXT[18];
    private static final String RARITY_LABEL_UNKNOWN = TEXT[19];
    private static final String COUNT_WITH_LABEL = TEXT[20];
    private static final String LABEL_WITH_COUNT_IN_PARENS = TEXT[21];
    private static final String SEED_LABEL = TEXT[32];
    private static final String CUSTOM_SEED_LABEL = TEXT[33];
    private static Gson gson = new Gson();
    private static final float SHOW_X = 300.0f * Settings.xScale;
    private static final float HIDE_X = (-800.0f) * Settings.xScale;
    private static final float RELIC_SPACE = 64.0f * Settings.scale;
    public MenuCancelButton button = new MenuCancelButton();
    private ArrayList<RunData> unfilteredRuns = new ArrayList<>();
    private ArrayList<RunData> filteredRuns = new ArrayList<>();
    private int runIndex = 0;
    private RunData viewedRun = null;
    public boolean screenUp = false;
    public AbstractPlayer.PlayerClass currentChar = null;
    private float screenX = HIDE_X;
    private float targetX = HIDE_X;
    private boolean grabbedScreen = false;
    private float grabStartY = 0.0f;
    private float scrollTargetY = 0.0f;
    private float scrollY = 0.0f;
    private float scrollLowerBound = 0.0f;
    private float scrollUpperBound = 0.0f;
    private ArrayList<AbstractRelic> relics = new ArrayList<>();
    private ArrayList<TinyCard> cards = new ArrayList<>();
    private String cardCountByRarityString = "";
    private String relicCountByRarityString = "";
    private int circletCount = 0;
    private Color controllerUiColor = new Color(0.7f, 0.9f, 1.0f, 0.25f);
    AbstractRelic hoveredRelic = null;
    AbstractRelic clickStartedRelic = null;
    private RunHistoryPath runPath = new RunHistoryPath();
    private ModIcons modIcons = new ModIcons();
    private CopyableTextElement seedElement = new CopyableTextElement(FontHelper.cardDescFont_N);
    private CopyableTextElement secondSeedElement = new CopyableTextElement(FontHelper.cardDescFont_N);
    private Hitbox prevHb = new Hitbox(110.0f * Settings.scale, 110.0f * Settings.scale);
    private Hitbox nextHb = new Hitbox(110.0f * Settings.scale, 110.0f * Settings.scale);

    /* JADX INFO: Access modifiers changed from: private */
    /*
     * loaded from: desktop-1.0.jar:com/megacrit/cardcrawl/screens/runHistory/
     * RunHistoryScreen$InputSection.class
     */
    public enum InputSection {
        DROPDOWN, ROOM, RELIC, CARD
    }

    public RunHistoryScreen() {
        this.prevHb.move(ARROW_SIDE_PADDING * Settings.scale, Settings.HEIGHT / 2.0f);
        this.nextHb.move(Settings.WIDTH - (ARROW_SIDE_PADDING * Settings.xScale), Settings.HEIGHT / 2.0f);
    }

    public void refreshData() {
        FileHandle[] list;
        FileHandle[] subfolders = Gdx.files.local("runs" + File.separator).list();
        this.unfilteredRuns.clear();
        for (FileHandle subFolder : subfolders) {
            switch (CardCrawlGame.saveSlot) {
                case 0:
                    if (subFolder.name().contains("0_")) {
                        continue;
                    } else if (!subFolder.name().contains("1_")) {
                        if (subFolder.name().contains("2_")) {
                        }
                    }
                    break;
                default:
                    if (!subFolder.name().contains(CardCrawlGame.saveSlot + "_")) {
                        continue;
                    }
                    break;
            }
            for (FileHandle file : subFolder.list()) {
                try {
                    RunData data = (RunData) gson.fromJson(file.readString(), RunData.class);
                    if (data != null && data.timestamp == null) {
                        data.timestamp = file.nameWithoutExtension();
                        boolean assumeDaysSinceUnix = data.timestamp.length() == "17586".length();
                        if (assumeDaysSinceUnix) {
                            try {
                                long days = Long.parseLong(data.timestamp);
                                data.timestamp = Long.toString(days * 86400);
                            } catch (NumberFormatException e) {
                                logger.info(
                                        "Run file " + file.path() + " name is could not be parsed into a Timestamp.");
                                data = null;
                            }
                        }
                    }
                    if (data != null) {
                        try {
                            AbstractPlayer.PlayerClass.valueOf(data.character_chosen);
                            this.unfilteredRuns.add(data);
                        } catch (IllegalArgumentException | NullPointerException e2) {
                            logger.info("Run file " + file.path() + " does not use a real character: "
                                    + data.character_chosen);
                        }
                    }
                } catch (JsonSyntaxException e3) {
                    logger.info("Failed to load RunData from JSON file: " + file.path());
                }
            }
        }
        if (this.unfilteredRuns.size() > 0) {
            this.unfilteredRuns.sort(RunData.orderByTimestampDesc);
            this.viewedRun = this.unfilteredRuns.get(0);
        }
        String[] charFilterOptions = {ALL_CHARACTERS_TEXT, IRONCLAD_NAME, SILENT_NAME, DEFECT_NAME, WATCHER_NAME };
        this.characterFilter = new DropdownMenu(this, charFilterOptions, FontHelper.cardDescFont_N,
                Settings.CREAM_COLOR);
        String[] winLossFilterOptions = {WINS_AND_LOSSES_TEXT, WINS_TEXT, LOSSES_TEXT };
        this.winLossFilter = new DropdownMenu(this, winLossFilterOptions, FontHelper.cardDescFont_N,
                Settings.CREAM_COLOR);
        String[] runTypeFilterOptions = {RUN_TYPE_ALL, RUN_TYPE_NORMAL, RUN_TYPE_ASCENSION, RUN_TYPE_DAILY };
        this.runTypeFilter = new DropdownMenu(this, runTypeFilterOptions, FontHelper.cardDescFont_N,
                Settings.CREAM_COLOR);
        resetRunsDropdown();
    }

    private void resetRunsDropdown() {
        SimpleDateFormat dateFormat;
        String dateTimeStr;
        this.filteredRuns.clear();
        boolean only_wins = this.winLossFilter.getSelectedIndex() == 1;
        boolean only_losses = this.winLossFilter.getSelectedIndex() == 2;
        boolean only_ironclad = this.characterFilter.getSelectedIndex() == 1;
        boolean only_silent = this.characterFilter.getSelectedIndex() == 2;
        boolean only_defect = this.characterFilter.getSelectedIndex() == 3;
        boolean only_watcher = this.characterFilter.getSelectedIndex() == 4;
        boolean only_normal = this.runTypeFilter.getSelectedIndex() == 1;
        boolean only_ascension = this.runTypeFilter.getSelectedIndex() == 2;
        boolean only_daily = this.runTypeFilter.getSelectedIndex() == 3;
        Iterator<RunData> it = this.unfilteredRuns.iterator();
        while (it.hasNext()) {
            RunData data = it.next();
            boolean includeMe = true;
            if (only_wins) {
                includeMe = 1 != 0 && data.victory;
            } else if (only_losses) {
                includeMe = 1 != 0 && !data.victory;
            }
            String runCharacter = data.character_chosen;
            if (only_ironclad) {
                includeMe = includeMe && runCharacter.equals(AbstractPlayer.PlayerClass.IRONCLAD.name());
            } else if (only_silent) {
                includeMe = includeMe && runCharacter.equals(AbstractPlayer.PlayerClass.THE_SILENT.name());
            } else if (only_defect) {
                includeMe = includeMe && runCharacter.equals(AbstractPlayer.PlayerClass.DEFECT.name());
            } else if (only_watcher) {
                includeMe = includeMe && runCharacter.equals(AbstractPlayer.PlayerClass.WATCHER.name());
            }
            if (only_normal) {
                includeMe = includeMe && !data.is_ascension_mode && !data.is_daily;
            } else if (only_ascension) {
                includeMe = includeMe && data.is_ascension_mode;
            } else if (only_daily) {
                includeMe = includeMe && data.is_daily;
            }
            if (includeMe) {
                this.filteredRuns.add(data);
            }
        }
        ArrayList<String> options = new ArrayList<>();
        if (Settings.language == Settings.GameLanguage.JPN) {
            dateFormat = new SimpleDateFormat(TEXT[34], Locale.JAPAN);
        } else {
            dateFormat = new SimpleDateFormat(TEXT[34]);
        }
        Iterator<RunData> it2 = this.filteredRuns.iterator();
        while (it2.hasNext()) {
            RunData run = it2.next();
            try {
                if (run.local_time != null) {
                    dateTimeStr = dateFormat.format(Metrics.timestampFormatter.parse(run.local_time));
                } else {
                    dateTimeStr = dateFormat.format(Long.valueOf(Long.valueOf(run.timestamp).longValue() * 1000));
                }
                options.add(dateTimeStr + " - " + run.score);
            } catch (Exception e) {
                logger.info(e.getMessage());
            }
        }
        this.runsDropdown = new DropdownMenu(this, options, FontHelper.panelNameFont, Settings.CREAM_COLOR);
        this.runIndex = 0;
        if (this.filteredRuns.size() > 0) {
            reloadWithRunData(this.filteredRuns.get(this.runIndex));
            return;
        }
        this.viewedRun = null;
        reloadWithRunData(null);
    }

    public String baseCardSuffixForCharacter(String character) {
        switch (AbstractPlayer.PlayerClass.valueOf(character)) {
            case IRONCLAD:
                return "_R";
            case THE_SILENT:
                return "_G";
            case DEFECT:
                return "_B";
            case WATCHER:
                return "_W";
            default:
                return "";
        }
    }

    public void reloadWithRunData(RunData runData) {
        if (runData == null) {
            logger.info("Attempted to load Run History with 0 runs.");
            return;
        }
        this.scrollUpperBound = 0.0f;
        this.viewedRun = runData;
        reloadRelics(runData);
        reloadCards(runData);
        this.runPath.setRunData(runData);
        this.modIcons.setRunData(runData);
        try {
            if (this.viewedRun.special_seed == null || this.viewedRun.special_seed.longValue() == 0
                    || this.viewedRun.is_daily) {
                String seedFormat = this.viewedRun.chose_seed ? CUSTOM_SEED_LABEL : SEED_LABEL;
                String seedText = SeedHelper.getString(Long.parseLong(runData.seed_played));
                this.seedElement.setText(String.format(seedFormat, seedText), seedText);
                this.secondSeedElement.setText("", "");
            } else {
                String seedText2 = SeedHelper.getString(runData.special_seed.longValue());
                this.seedElement.setText(String.format(CUSTOM_SEED_LABEL, seedText2), seedText2);
                String secondSeedText = SeedHelper.getString(Long.parseLong(runData.seed_played));
                this.secondSeedElement.setText(String.format(SEED_LABEL, secondSeedText), secondSeedText);
            }
        } catch (NumberFormatException e) {
            this.seedElement.setText("", "");
            this.secondSeedElement.setText("", "");
        }
        this.scrollTargetY = 0.0f;
        resetScrolling();
        if (this.runsDropdown != null) {
            this.runsDropdown.setSelectedIndex(this.filteredRuns.indexOf(runData));
        }
    }

    private void reloadRelics(RunData runData) {
        AbstractRelic.RelicTier[] relicTierArr;
        this.relics.clear();
        this.circletCount = runData.circlet_count;
        boolean circletCountSet = this.circletCount > 0;
        Hashtable<AbstractRelic.RelicTier, Integer> relicRarityCounts = new Hashtable<>();
        AbstractRelic circlet = null;
        for (String relicName : runData.relics) {
            try {
                AbstractRelic relic = RelicLibrary.getRelic(relicName).makeCopy();
                relic.isSeen = true;
                if (!(relic instanceof Circlet)) {
                    this.relics.add(relic);
                } else if (relicName.equals(Circlet.ID)) {
                    if (!circletCountSet) {
                        this.circletCount++;
                    }
                    if (circlet == null) {
                        circlet = relic;
                        this.relics.add(relic);
                    }
                } else {
                    logger.info("Could not find relic for: " + relicName);
                }
                int newCount = relicRarityCounts.containsKey(relic.tier)
                        ? relicRarityCounts.get(relic.tier).intValue() + 1
                        : 1;
                relicRarityCounts.put(relic.tier, Integer.valueOf(newCount));
            } catch (NullPointerException e) {
                logger.info("NPE while loading: " + relicName);
            }
        }
        if (circlet != null && this.circletCount > 1) {
            circlet.setCounter(this.circletCount);
        }
        StringBuilder bldr = new StringBuilder();
        for (AbstractRelic.RelicTier rarity : orderedRelicRarity) {
            if (relicRarityCounts.containsKey(rarity)) {
                if (bldr.length() > 0) {
                    bldr.append(", ");
                }
                bldr.append(String.format(COUNT_WITH_LABEL, relicRarityCounts.get(rarity), rarityLabel(rarity)));
            }
        }
        this.relicCountByRarityString = bldr.toString();
    }

    private void reloadCards(RunData runData) {
        AbstractCard.CardRarity[] cardRarityArr;
        AbstractCard card;
        Hashtable<String, AbstractCard> rawNameToCards = new Hashtable<>();
        Hashtable<AbstractCard, Integer> cardCounts = new Hashtable<>();
        Hashtable<AbstractCard.CardRarity, Integer> cardRarityCounts = new Hashtable<>();
        CardGroup sortedMasterDeck = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
        for (String cardID : runData.master_deck) {
            if (rawNameToCards.containsKey(cardID)) {
                card = rawNameToCards.get(cardID);
            } else {
                card = cardForName(runData, cardID);
            }
            if (card != null) {
                int value = cardCounts.containsKey(card) ? cardCounts.get(card).intValue() + 1 : 1;
                cardCounts.put(card, Integer.valueOf(value));
                rawNameToCards.put(cardID, card);
                int rarityCount = cardRarityCounts.containsKey(card.rarity)
                        ? cardRarityCounts.get(card.rarity).intValue() + 1
                        : 1;
                cardRarityCounts.put(card.rarity, Integer.valueOf(rarityCount));
            }
        }
        sortedMasterDeck.clear();
        for (AbstractCard card2 : rawNameToCards.values()) {
            sortedMasterDeck.addToTop(card2);
        }
        sortedMasterDeck.sortAlphabetically(true);
        sortedMasterDeck.sortByRarityPlusStatusCardType(false);
        CardGroup sortedMasterDeck2 = sortedMasterDeck.getGroupedByColor();
        this.cards.clear();
        Iterator<AbstractCard> it = sortedMasterDeck2.group.iterator();
        while (it.hasNext()) {
            AbstractCard card3 = it.next();
            this.cards.add(new TinyCard(card3, cardCounts.get(card3).intValue()));
        }
        StringBuilder bldr = new StringBuilder();
        for (AbstractCard.CardRarity rarity : orderedRarity) {
            if (cardRarityCounts.containsKey(rarity)) {
                if (bldr.length() > 0) {
                    bldr.append(", ");
                }
                bldr.append(String.format(COUNT_WITH_LABEL, cardRarityCounts.get(rarity), rarityLabel(rarity)));
            }
        }
        this.cardCountByRarityString = bldr.toString();
    }

    private String rarityLabel(AbstractCard.CardRarity rarity) {
        switch (rarity) {
            case BASIC:
                return RARITY_LABEL_STARTER;
            case SPECIAL:
                return RARITY_LABEL_SPECIAL;
            case COMMON:
                return RARITY_LABEL_COMMON;
            case UNCOMMON:
                return RARITY_LABEL_UNCOMMON;
            case RARE:
                return RARITY_LABEL_RARE;
            case CURSE:
                return RARITY_LABEL_CURSE;
            default:
                return RARITY_LABEL_UNKNOWN;
        }
    }

    private String rarityLabel(AbstractRelic.RelicTier rarity) {
        switch (rarity) {
            case STARTER:
                return RARITY_LABEL_STARTER;
            case COMMON:
                return RARITY_LABEL_COMMON;
            case UNCOMMON:
                return RARITY_LABEL_UNCOMMON;
            case RARE:
                return RARITY_LABEL_RARE;
            case SPECIAL:
                return RARITY_LABEL_SPECIAL;
            case BOSS:
                return RARITY_LABEL_BOSS;
            case SHOP:
                return RARITY_LABEL_SHOP;
            case DEPRECATED:
            default:
                return RARITY_LABEL_UNKNOWN;
        }
    }

    private void layoutTinyCards(ArrayList<TinyCard> cards, float x, float y) {
        float originX = x + screenPosX(60.0f);
        float originY = y - screenPosY(64.0f);
        float rowHeight = screenPosY(48.0f);
        float columnWidth = screenPosX(340.0f);
        int row = 0;
        int column = 0;
        TinyCard.desiredColumns = cards.size() <= 36 ? 3 : 4;
        int cardsPerColumn = cards.size() / TinyCard.desiredColumns;
        int remainderCards = cards.size() - (cardsPerColumn * TinyCard.desiredColumns);
        int[] columnSizes = new int[TinyCard.desiredColumns];
        Arrays.fill(columnSizes, cardsPerColumn);
        for (int i = 0; i < remainderCards; i++) {
            int i2 = i % TinyCard.desiredColumns;
            columnSizes[i2] = columnSizes[i2] + 1;
        }
        Iterator<TinyCard> it = cards.iterator();
        while (it.hasNext()) {
            TinyCard card = it.next();
            if (row >= columnSizes[column]) {
                row = 0;
                column++;
            }
            float cardY = originY - (row * rowHeight);
            card.hb.move(originX + (column * columnWidth) + (card.hb.width / 2.0f), cardY);
            if (card.col == -1) {
                card.col = column;
                card.row = row;
            }
            row++;
            this.scrollUpperBound = Math.max(this.scrollUpperBound, (this.scrollY - cardY) + screenPosY(50.0f));
        }
    }

    private AbstractCard cardForName(RunData runData, String cardID) {
        String libraryLookupName = cardID;
        if (cardID.endsWith("+")) {
            libraryLookupName = cardID.substring(0, cardID.length() - 1);
        }
        if (libraryLookupName.equals("Defend") || libraryLookupName.equals("Strike")) {
            libraryLookupName = libraryLookupName + baseCardSuffixForCharacter(runData.character_chosen);
        }

        AbstractCard card = CardLibrary.getCard(libraryLookupName);
        int upgrades = 0;
        if (card != null) {
            if (cardID.endsWith("+")) {
                upgrades = 1;
            }
        } else if (libraryLookupName.contains("+")) {

            String[] split = libraryLookupName.split("\\+", -1);
            libraryLookupName = split[0];
            upgrades = Integer.parseInt(split[1]);
            card = CardLibrary.getCard(libraryLookupName);
        }
        if (card != null) {
            card = card.makeCopy();
            for (int i = 0; i < upgrades; i++) {
                card.upgrade();
            }
            return card;
        }
        logger.info("Could not find card named: " + cardID);
        return null;
    }

    public void update() {
        updateControllerInput();
        if (Settings.isControllerMode && !CardCrawlGame.isPopupOpen && this.currentHb != null) {
            if (Gdx.input.getY() > Settings.HEIGHT * 0.8f) {
                this.scrollTargetY += Settings.SCROLL_SPEED / 2.0f;
                if (this.scrollTargetY > this.scrollUpperBound) {
                    this.scrollTargetY = this.scrollUpperBound;
                }
            } else if (Gdx.input.getY() < Settings.HEIGHT * 0.2f && this.scrollY > 100.0f) {
                this.scrollTargetY -= Settings.SCROLL_SPEED / 2.0f;
                if (this.scrollTargetY < this.scrollLowerBound) {
                    this.scrollTargetY = this.scrollLowerBound;
                }
            }
        }
        if (this.runsDropdown.isOpen) {
            this.runsDropdown.update();
        } else if (this.winLossFilter.isOpen) {
            this.winLossFilter.update();
        } else if (this.characterFilter.isOpen) {
            this.characterFilter.update();
        } else if (this.runTypeFilter.isOpen) {
            this.runTypeFilter.update();
        } else {
            this.runsDropdown.update();
            this.winLossFilter.update();
            this.characterFilter.update();
            this.runTypeFilter.update();
            this.button.update();
            updateScrolling();
            updateArrows();
            this.modIcons.update();
            this.runPath.update();
            if (!this.seedElement.getText().isEmpty()) {
                this.seedElement.update();
            }
            if (!this.secondSeedElement.getText().isEmpty()) {
                this.secondSeedElement.update();
            }
            if (this.button.hb.clicked || InputHelper.pressedEscape) {
                InputHelper.pressedEscape = false;
                hide();
            }
            this.screenX = MathHelper.uiLerpSnap(this.screenX, this.targetX);
            if (this.filteredRuns.size() != 0) {
                boolean isAPopupOpen = CardCrawlGame.cardPopup.isOpen || CardCrawlGame.relicPopup.isOpen;
                if (!isAPopupOpen) {
                    if (InputActionSet.left.isJustPressed()) {
                        this.runIndex = Math.max(0, this.runIndex - 1);
                        reloadWithRunData(this.filteredRuns.get(this.runIndex));
                    }
                    if (InputActionSet.right.isJustPressed()) {
                        this.runIndex = Math.min(this.runIndex + 1, this.filteredRuns.size() - 1);
                        reloadWithRunData(this.filteredRuns.get(this.runIndex));
                    }
                }
                handleRelicInteraction();
                Iterator<TinyCard> it = this.cards.iterator();
                while (it.hasNext()) {
                    TinyCard card = it.next();
                    boolean didClick = card.updateDidClick();
                    if (didClick) {
                        CardGroup cardGroup = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
                        Iterator<TinyCard> it2 = this.cards.iterator();
                        while (it2.hasNext()) {
                            TinyCard addMe = it2.next();
                            cardGroup.addToTop(addMe.card);
                        }
                        CardCrawlGame.cardPopup.open(card.card, cardGroup);
                    }
                }
                if (Settings.isControllerMode && this.currentHb != null) {
                    CInputHelper.setCursor(this.currentHb);
                }
            }
        }
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode || this.runsDropdown.isOpen || this.winLossFilter.isOpen
                || this.characterFilter.isOpen || this.runTypeFilter.isOpen) {
            return;
        }

        InputSection section = null;
        boolean anyHovered = false;
        int index = 0;

        ArrayList<Hitbox> hbs = new ArrayList<>();
        if (!this.runsDropdown.rows.isEmpty()) {
            hbs.add(this.runsDropdown.getHitbox());
        }
        hbs.add(this.winLossFilter.getHitbox());
        hbs.add(this.characterFilter.getHitbox());
        hbs.add(this.runTypeFilter.getHitbox());

        for (Hitbox hb : hbs) {
            if (hb.hovered) {
                section = InputSection.DROPDOWN;
                anyHovered = true;
                break;
            }
            index++;
        }

        if (!anyHovered) {
            index = 0;
            for (RunPathElement e : this.runPath.pathElements) {
                if (e.hb.hovered) {
                    section = InputSection.ROOM;
                    anyHovered = true;
                    break;
                }
                index++;
            }
        }

        if (!anyHovered) {
            index = 0;
            for (AbstractRelic r : this.relics) {
                if (r.hb.hovered) {
                    section = InputSection.RELIC;
                    anyHovered = true;
                    break;
                }
                index++;
            }
        }

        if (!anyHovered) {
            index = 0;
            for (TinyCard card : this.cards) {
                if (card.hb.hovered) {
                    section = InputSection.CARD;
                    anyHovered = true;
                    break;
                }
                index++;
            }
        }

        if (!anyHovered) {
            CInputHelper.setCursor(hbs.get(0));
            this.currentHb = hbs.get(0);
            this.scrollTargetY = 0.0F;
        } else {
            int c;
            int r;
            switch (section) {
                case DROPDOWN:
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        index--;
                        if (index != -1) {
                            CInputHelper.setCursor(hbs.get(index));
                            this.currentHb = hbs.get(index);
                        }
                        break;
                    }
                    if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                        index++;
                        if (hbs.size() == 4) {
                            if (index > hbs.size() - 1 || index == 1) {

                                if (!this.runPath.pathElements.isEmpty()) {
                                    CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(0)).hb);
                                    this.currentHb = ((RunPathElement) this.runPath.pathElements.get(0)).hb;

                                    break;
                                }
                                if (!this.relics.isEmpty()) {
                                    CInputHelper.setCursor(((AbstractRelic) this.relics.get(0)).hb);
                                    this.currentHb = ((AbstractRelic) this.relics.get(0)).hb;
                                    break;
                                }
                                CInputHelper.setCursor(((TinyCard) this.cards.get(0)).hb);
                                this.currentHb = ((TinyCard) this.cards.get(0)).hb;

                                break;
                            }
                            CInputHelper.setCursor(hbs.get(index));
                            this.currentHb = hbs.get(index);
                            break;
                        }
                        if (index > hbs.size() - 1) {
                            index = 0;
                        }
                        CInputHelper.setCursor(hbs.get(index));
                        this.currentHb = hbs.get(index);
                        break;
                    }
                    if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        if (index == 0) {
                            CInputHelper.setCursor(hbs.get(1));
                            this.currentHb = hbs.get(1);
                        }
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        if (index == 1) {
                            CInputHelper.setCursor(hbs.get(0));
                            this.currentHb = hbs.get(0);
                            this.scrollTargetY = 0.0F;
                            break;
                        }
                        if (index > 1) {

                            if (!this.runPath.pathElements.isEmpty()) {
                                CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(0)).hb);
                                this.currentHb = ((RunPathElement) this.runPath.pathElements.get(0)).hb;

                                break;
                            }
                            if (!this.relics.isEmpty()) {
                                CInputHelper.setCursor(((AbstractRelic) this.relics.get(0)).hb);
                                this.currentHb = ((AbstractRelic) this.relics.get(0)).hb;
                                break;
                            }
                            if (!this.cards.isEmpty()) {
                                CInputHelper.setCursor(((TinyCard) this.cards.get(0)).hb);
                                this.currentHb = ((TinyCard) this.cards.get(0)).hb;
                            }
                        }
                    }
                    break;

                case ROOM:
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        int j = ((RunPathElement) this.runPath.pathElements.get(index)).col;
                        int k = ((RunPathElement) this.runPath.pathElements.get(index)).row - 1;

                        if (k < 0) {

                            CInputHelper.setCursor(hbs.get(0));
                            this.currentHb = hbs.get(0);
                            this.scrollTargetY = 0.0F;
                            break;
                        }
                        boolean foundNode = false;

                        int i;
                        for (i = 0; i < this.runPath.pathElements.size(); i++) {
                            if (((RunPathElement) this.runPath.pathElements.get(i)).row == k
                                    && ((RunPathElement) this.runPath.pathElements.get(i)).col == j) {
                                CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(i)).hb);
                                this.currentHb = ((RunPathElement) this.runPath.pathElements.get(i)).hb;
                                foundNode = true;

                                break;
                            }
                        }

                        if (!foundNode) {
                            for (i = this.runPath.pathElements.size() - 1; i > 0; i--) {
                                if (((RunPathElement) this.runPath.pathElements.get(i)).row == k) {
                                    CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(i)).hb);
                                    this.currentHb = ((RunPathElement) this.runPath.pathElements.get(i)).hb;
                                    foundNode = true;

                                    break;
                                }
                            }
                        }

                        if (!foundNode) {
                            CInputHelper.setCursor(hbs.get(0));
                            this.currentHb = hbs.get(0);
                            this.scrollTargetY = 0.0F;
                        }
                        break;
                    }
                    if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                        int j = ((RunPathElement) this.runPath.pathElements.get(index)).col;
                        int k = ((RunPathElement) this.runPath.pathElements.get(index)).row + 1;

                        if (k > ((RunPathElement) this.runPath.pathElements
                                .get(this.runPath.pathElements.size() - 1)).row) {
                            if (!this.relics.isEmpty()) {
                                CInputHelper.setCursor(((AbstractRelic) this.relics.get(0)).hb);
                                this.currentHb = ((AbstractRelic) this.relics.get(0)).hb;
                                break;
                            }
                            CInputHelper.setCursor(((TinyCard) this.cards.get(0)).hb);
                            this.currentHb = ((TinyCard) this.cards.get(0)).hb;
                            break;
                        }
                        boolean foundNode = false;

                        int i;
                        for (i = this.runPath.pathElements.size() - 1; i > 0; i--) {
                            if (((RunPathElement) this.runPath.pathElements.get(i)).row == k
                                    && ((RunPathElement) this.runPath.pathElements.get(i)).col == j) {
                                CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(i)).hb);
                                this.currentHb = ((RunPathElement) this.runPath.pathElements.get(i)).hb;
                                foundNode = true;

                                break;
                            }
                        }

                        if (!foundNode) {
                            for (i = this.runPath.pathElements.size() - 1; i > 0; i--) {
                                if (((RunPathElement) this.runPath.pathElements.get(i)).row == k) {
                                    CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(i)).hb);
                                    this.currentHb = ((RunPathElement) this.runPath.pathElements.get(i)).hb;
                                    foundNode = true;

                                    break;
                                }
                            }
                        }

                        if (!foundNode) {
                            if (!this.relics.isEmpty()) {
                                CInputHelper.setCursor(((AbstractRelic) this.relics.get(0)).hb);
                                this.currentHb = ((AbstractRelic) this.relics.get(0)).hb;
                                break;
                            }
                            CInputHelper.setCursor(((TinyCard) this.cards.get(0)).hb);
                            this.currentHb = ((TinyCard) this.cards.get(0)).hb;
                        }

                        break;
                    }
                    if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        index--;
                        if (index < 0) {
                            if (hbs.size() > 3) {
                                CInputHelper.setCursor(hbs.get(3));
                                this.currentHb = hbs.get(3);
                            }
                            break;
                        }
                        CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(index)).hb);
                        this.currentHb = ((RunPathElement) this.runPath.pathElements.get(index)).hb;
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        index++;
                        if (index > this.runPath.pathElements.size() - 1) {
                            if (!this.relics.isEmpty()) {
                                CInputHelper.setCursor(((AbstractRelic) this.relics.get(0)).hb);
                                this.currentHb = ((AbstractRelic) this.relics.get(0)).hb;
                                break;
                            }
                            CInputHelper.setCursor(((TinyCard) this.cards.get(0)).hb);
                            this.currentHb = ((TinyCard) this.cards.get(0)).hb;
                            break;
                        }
                        CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(index)).hb);
                        this.currentHb = ((RunPathElement) this.runPath.pathElements.get(index)).hb;
                    }
                    break;

                case RELIC:
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        index -= 15;
                        if (index < 0) {
                            if (!this.runPath.pathElements.isEmpty()) {
                                CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(0)).hb);
                                this.currentHb = ((RunPathElement) this.runPath.pathElements.get(0)).hb;
                                break;
                            }
                            CInputHelper.setCursor(hbs.get(0));
                            this.currentHb = hbs.get(0);
                            this.scrollTargetY = 0.0F;
                            break;
                        }
                        CInputHelper.setCursor(((AbstractRelic) this.relics.get(index)).hb);
                        this.currentHb = ((AbstractRelic) this.relics.get(index)).hb;
                        break;
                    }
                    if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                        index += 15;
                        if (index > this.relics.size() - 1) {
                            if (!this.cards.isEmpty()) {
                                CInputHelper.setCursor(((TinyCard) this.cards.get(0)).hb);
                                this.currentHb = ((TinyCard) this.cards.get(0)).hb;
                            }
                            break;
                        }
                        CInputHelper.setCursor(((AbstractRelic) this.relics.get(index)).hb);
                        this.currentHb = ((AbstractRelic) this.relics.get(index)).hb;
                        break;
                    }
                    if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        index--;
                        if (index < 0) {
                            if (!this.cards.isEmpty()) {
                                CInputHelper.setCursor(((TinyCard) this.cards.get(0)).hb);
                                this.currentHb = ((TinyCard) this.cards.get(0)).hb;
                            }
                            break;
                        }
                        if (!this.relics.isEmpty()) {
                            CInputHelper.setCursor(((AbstractRelic) this.relics.get(index)).hb);
                            this.currentHb = ((AbstractRelic) this.relics.get(index)).hb;
                        }
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        index++;
                        if (index > this.relics.size() - 1) {
                            if (!this.cards.isEmpty()) {
                                CInputHelper.setCursor(((TinyCard) this.cards.get(0)).hb);
                                this.currentHb = ((TinyCard) this.cards.get(0)).hb;
                            }
                            break;
                        }
                        if (!this.relics.isEmpty()) {
                            CInputHelper.setCursor(((AbstractRelic) this.relics.get(index)).hb);
                            this.currentHb = ((AbstractRelic) this.relics.get(index)).hb;
                        }
                        break;
                    }
                    if (CInputActionSet.select.isJustPressed()) {
                        CardCrawlGame.relicPopup.open(this.relics.get(index), this.relics);
                    }
                    break;
                case CARD:
                    c = ((TinyCard) this.cards.get(index)).col;
                    r = ((TinyCard) this.cards.get(index)).row;

                    if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        c--;

                        if (c < 0) {
                            for (int j = this.cards.size() - 1; j > 0; j--) {
                                if ((((TinyCard) this.cards.get(j)).col == TinyCard.desiredColumns - 1
                                        || ((TinyCard) this.cards.get(j)).col == 1)
                                        && ((TinyCard) this.cards
                                                .get(j)).row == r) {
                                    CInputHelper.setCursor(((TinyCard) this.cards.get(j)).hb);
                                    this.currentHb = ((TinyCard) this.cards.get(j)).hb;
                                    break;
                                }
                            }
                            break;
                        }
                        boolean foundNode = false;
                        for (int i = 0; i < this.cards.size(); i++) {
                            if (((TinyCard) this.cards.get(i)).col == c && ((TinyCard) this.cards.get(i)).row == r) {
                                CInputHelper.setCursor(((TinyCard) this.cards.get(i)).hb);
                                this.currentHb = ((TinyCard) this.cards.get(i)).hb;
                                foundNode = true;

                                break;
                            }
                        }
                        if (!foundNode) {
                            if (!this.relics.isEmpty()) {
                                CInputHelper.setCursor(((AbstractRelic) this.relics.get(0)).hb);
                                this.currentHb = ((AbstractRelic) this.relics.get(0)).hb;
                                break;
                            }
                            CInputHelper.setCursor(this.runsDropdown.getHitbox());
                            this.currentHb = this.runsDropdown.getHitbox();
                        }
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        c++;
                        if (c > TinyCard.desiredColumns - 1) {
                            c = 0;
                            break;
                        }
                        boolean foundNode = false;
                        int i;
                        for (i = 0; i < this.cards.size(); i++) {
                            if (((TinyCard) this.cards.get(i)).col == c && ((TinyCard) this.cards.get(i)).row == r) {
                                CInputHelper.setCursor(((TinyCard) this.cards.get(i)).hb);
                                this.currentHb = ((TinyCard) this.cards.get(i)).hb;
                                foundNode = true;

                                break;
                            }
                        }
                        if (!foundNode) {
                            c = 0;
                            for (i = 0; i < this.cards.size(); i++) {
                                if (((TinyCard) this.cards.get(i)).col == c
                                        && ((TinyCard) this.cards.get(i)).row == r) {
                                    CInputHelper.setCursor(((TinyCard) this.cards.get(i)).hb);
                                    this.currentHb = ((TinyCard) this.cards.get(i)).hb;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        index--;
                        if (index < 0) {
                            if (!this.relics.isEmpty()) {
                                CInputHelper.setCursor(((AbstractRelic) this.relics.get(this.relics.size() - 1)).hb);
                                this.currentHb = ((AbstractRelic) this.relics.get(this.relics.size() - 1)).hb;
                                break;
                            }
                            if (!this.runPath.pathElements.isEmpty()) {
                                CInputHelper.setCursor(((RunPathElement) this.runPath.pathElements.get(0)).hb);
                                this.currentHb = ((RunPathElement) this.runPath.pathElements.get(0)).hb;
                                break;
                            }
                            CInputHelper.setCursor(this.runsDropdown.getHitbox());
                            this.currentHb = this.runsDropdown.getHitbox();
                            break;
                        }
                        CInputHelper.setCursor(((TinyCard) this.cards.get(index)).hb);
                        this.currentHb = ((TinyCard) this.cards.get(index)).hb;
                        break;
                    }
                    if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                        index++;
                        if (index <= this.cards.size() - 1) {
                            CInputHelper.setCursor(((TinyCard) this.cards.get(index)).hb);
                            this.currentHb = ((TinyCard) this.cards.get(index)).hb;
                        }
                        break;
                    }
                    if (CInputActionSet.select.isJustPressed()) {
                        CardGroup cardGroup = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
                        for (TinyCard addMe : this.cards) {
                            cardGroup.addToTop(addMe.card);
                        }
                        CardCrawlGame.cardPopup.open(((TinyCard) this.cards.get(index)).card, cardGroup);
                    }
                    break;
            }
        }
    }

    public void open() {
        this.currentHb = null;
        CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.RUN_HISTORY;
        SingleCardViewPopup.enableUpgradeToggle = false;
        refreshData();
        this.targetX = SHOW_X;
        this.button.show(TEXT[3]);
        this.screenUp = true;
        this.scrollY = this.scrollLowerBound;
        this.scrollTargetY = this.scrollLowerBound;
    }

    public void hide() {
        this.targetX = HIDE_X;
        this.button.hide();
        this.screenUp = false;
        this.currentChar = null;
        CardCrawlGame.mainMenuScreen.panelScreen.refresh();
        SingleCardViewPopup.enableUpgradeToggle = true;
    }

    public void render(SpriteBatch sb) {
        renderRunHistoryScreen(sb);
        renderArrows(sb);
        renderFilters(sb);
        this.button.render(sb);
        renderControllerUi(sb, this.currentHb);
    }

    private void renderControllerUi(SpriteBatch sb, Hitbox hb) {
        if (Settings.isControllerMode && hb != null) {
            sb.setBlendFunction(770, 1);
            sb.setColor(this.controllerUiColor);
            sb.draw(ImageMaster.CONTROLLER_HB_HIGHLIGHT, hb.cX - (hb.width / 2.0f), hb.cY - (hb.height / 2.0f),
                    hb.width, hb.height);
            sb.setBlendFunction(770, 771);
        }
    }

    private String characterText(String chosenCharacter) {
        if (chosenCharacter.equals(AbstractPlayer.PlayerClass.IRONCLAD.name())) {
            return IRONCLAD_NAME;
        }
        if (chosenCharacter.equals(AbstractPlayer.PlayerClass.THE_SILENT.name())) {
            return SILENT_NAME;
        }
        if (chosenCharacter.equals(AbstractPlayer.PlayerClass.DEFECT.name())) {
            return DEFECT_NAME;
        }
        if (chosenCharacter.equals(AbstractPlayer.PlayerClass.WATCHER.name())) {
            return WATCHER_NAME;
        }
        return chosenCharacter;
    }

    private void renderArrows(SpriteBatch sb) {
        if (this.runIndex < this.filteredRuns.size() - 1) {
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.POPUP_ARROW, this.nextHb.cX - 128.0f, this.nextHb.cY - 128.0f, 128.0f, 128.0f, 256.0f,
                    256.0f, Settings.scale * 0.75f, Settings.scale * 0.75f, 0.0f, 0, 0, 256, 256, true, false);
            if (this.nextHb.hovered) {
                sb.setBlendFunction(770, 1);
                sb.setColor(Color.WHITE);
                sb.draw(ImageMaster.POPUP_ARROW, this.nextHb.cX - 128.0f, this.nextHb.cY - 128.0f, 128.0f, 128.0f,
                        256.0f, 256.0f, Settings.scale * 0.75f, Settings.scale * 0.75f, 0.0f, 0, 0, 256, 256, true,
                        false);
                sb.setBlendFunction(770, 771);
            }
            if (Settings.isControllerMode) {
                sb.draw(CInputActionSet.pageRightViewExhaust.getKeyImg(), this.nextHb.cX - 32.0f,
                        (this.nextHb.cY - 32.0f) - (100.0f * Settings.scale), 32.0f, 32.0f, 64.0f, 64.0f,
                        Settings.scale, Settings.scale, 0.0f, 0, 0, 64, 64, false, false);
            }
            this.nextHb.render(sb);
        }
        if (this.runIndex > 0) {
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.POPUP_ARROW, this.prevHb.cX - 128.0f, this.prevHb.cY - 128.0f, 128.0f, 128.0f, 256.0f,
                    256.0f, Settings.scale * 0.75f, Settings.scale * 0.75f, 0.0f, 0, 0, 256, 256, false, false);
            if (this.prevHb.hovered) {
                sb.setBlendFunction(770, 1);
                sb.setColor(Color.WHITE);
                sb.draw(ImageMaster.POPUP_ARROW, this.prevHb.cX - 128.0f, this.prevHb.cY - 128.0f, 128.0f, 128.0f,
                        256.0f, 256.0f, Settings.scale * 0.75f, Settings.scale * 0.75f, 0.0f, 0, 0, 256, 256, false,
                        false);
                sb.setBlendFunction(770, 771);
            }
            if (Settings.isControllerMode) {
                sb.draw(CInputActionSet.pageLeftViewDeck.getKeyImg(), this.prevHb.cX - 32.0f,
                        (this.prevHb.cY - 32.0f) - (100.0f * Settings.scale), 32.0f, 32.0f, 64.0f, 64.0f,
                        Settings.scale, Settings.scale, 0.0f, 0, 0, 64, 64, false, false);
            }
            this.prevHb.render(sb);
        }
    }

    private void renderRunHistoryScreen(SpriteBatch sb) {
        Color resultTextColor;
        String resultText;
        if (this.viewedRun == null) {
            FontHelper.renderSmartText(sb, FontHelper.panelNameFont, TEXT[4], Settings.WIDTH * 0.43f,
                    Settings.HEIGHT * 0.53f, Settings.GOLD_COLOR);
            return;
        }
        float header1x = this.screenX + screenPosX(100.0f);
        float header2x = this.screenX + screenPosX(120.0f);
        float yOffset = this.scrollY + screenPosY(1020.0f);
        String characterName = characterText(this.viewedRun.character_chosen);
        renderHeader1(sb, characterName, header1x, yOffset);
        float approxCharNameWidth = approximateHeader1Width(characterName);
        if (!this.seedElement.getText().isEmpty()) {
            this.seedElement.render(sb, this.screenX + (1200.0f * Settings.scale), yOffset);
            if (!this.secondSeedElement.getText().isEmpty()) {
                this.secondSeedElement.render(sb, 1200.0f * Settings.scale, yOffset - screenPosY(36.0f));
            }
        }
        float yOffset2 = yOffset - screenPosY(50.0f);
        String specialModeText = "";
        if (this.viewedRun.is_daily) {
            specialModeText = " (" + TEXT[27] + ")";
        } else if (this.viewedRun.is_ascension_mode) {
            specialModeText = " (" + TEXT[5] + this.viewedRun.ascension_level + ")";
        }
        if (this.viewedRun.victory) {
            resultText = TEXT[8] + specialModeText;
            resultTextColor = Settings.GREEN_TEXT_COLOR;
        } else {
            resultTextColor = Settings.RED_TEXT_COLOR;
            resultText = this.viewedRun.killed_by == null
                    ? String.format(TEXT[7], Integer.valueOf(this.viewedRun.floor_reached)) + specialModeText
                    : String.format(TEXT[6], Integer.valueOf(this.viewedRun.floor_reached),
                            MonsterHelper.getEncounterName(this.viewedRun.killed_by)) + specialModeText;
        }
        renderSubHeading(sb, resultText, header1x, yOffset2, resultTextColor);
        if (this.viewedRun.victory) {
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.TIMER_ICON,
                    ((header1x + approximateSubHeaderWidth(resultText)) - 32.0f) + (54.0f * Settings.scale),
                    (yOffset2 - 32.0f) - (10.0f * Settings.scale), 32.0f, 32.0f, 64.0f, 64.0f, Settings.scale,
                    Settings.scale, 0.0f, 0, 0, 64, 64, false, false);
            renderSubHeading(sb, CharStat.formatHMSM(this.viewedRun.playtime),
                    header1x + approximateSubHeaderWidth(resultText) + (80.0f * Settings.scale), yOffset2,
                    Settings.CREAM_COLOR);
        }
        float yOffset3 = yOffset2 - screenPosY(40.0f);
        String scoreText = String.format(TEXT[22], Integer.valueOf(this.viewedRun.score));
        renderSubHeading(sb, scoreText, header1x, yOffset3, Settings.GOLD_COLOR);
        float scoreLineXOffset = header1x + approximateSubHeaderWidth(scoreText);
        if (this.modIcons.hasMods()) {
            this.modIcons.renderDailyMods(sb, scoreLineXOffset, yOffset3);
            float approximateWidth = scoreLineXOffset + this.modIcons.approximateWidth();
        }
        float yOffset4 = yOffset3 - screenPosY(18.0f);
        this.runPath.render(sb, header2x + (52.0f * Settings.scale), yOffset4);
        float relicBottom = renderRelics(sb, header2x,
                (yOffset4 - this.runPath.approximateHeight()) - screenPosY(35.0f));
        renderDeck(sb, header2x, relicBottom - screenPosY(70.0f));
        this.runsDropdown.render(sb, header1x + approxCharNameWidth + screenPosX(30.0f),
                this.scrollY + screenPosY(1020.0f + 6.0f));
    }

    private void renderHeader1(SpriteBatch sb, String text, float x, float y) {
        FontHelper.renderSmartText(sb, FontHelper.charTitleFont, text, x, y, 9999.0f, 36.0f * Settings.scale,
                Settings.GOLD_COLOR);
    }

    private float approximateHeader1Width(String text) {
        return FontHelper.getSmartWidth(FontHelper.charTitleFont, text, 9999.0f, 36.0f * Settings.scale);
    }

    private float approximateSubHeaderWidth(String text) {
        return FontHelper.getSmartWidth(FontHelper.buttonLabelFont, text, 9999.0f, 36.0f * Settings.scale);
    }

    private void renderSubHeading(SpriteBatch sb, String text, float x, float y, Color color) {
        FontHelper.renderSmartText(sb, FontHelper.buttonLabelFont, text, x, y, 9999.0f, 36.0f * Settings.scale, color);
    }

    private void renderSubHeadingWithMessage(SpriteBatch sb, String main, String description, float x, float y) {
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.buttonLabelFont, main, x, y, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, description,
                x + FontHelper.getSmartWidth(FontHelper.buttonLabelFont, main, 99999.0f, 0.0f),
                y - (4.0f * Settings.scale), Settings.CREAM_COLOR);
    }

    private void renderDeck(SpriteBatch sb, float x, float y) {
        layoutTinyCards(this.cards, this.screenX + screenPosX(90.0f), y);
        int cardCount = 0;
        Iterator<TinyCard> it = this.cards.iterator();
        while (it.hasNext()) {
            TinyCard card = it.next();
            card.render(sb);
            cardCount += card.count;
        }
        String mainText = String.format(LABEL_WITH_COUNT_IN_PARENS, TEXT[9], Integer.valueOf(cardCount));
        renderSubHeadingWithMessage(sb, mainText, this.cardCountByRarityString, x, y);
    }

    private float renderRelics(SpriteBatch sb, float x, float y) {
        String mainText = String.format(LABEL_WITH_COUNT_IN_PARENS, TEXT[10], Integer.valueOf(this.relics.size()));
        renderSubHeadingWithMessage(sb, mainText, this.relicCountByRarityString, x, y);
        int col = 0;
        int row = 0;
        float relicStartX = x + screenPosX(30.0f) + (RELIC_SPACE / 2.0f);
        float relicStartY = (y - RELIC_SPACE) - screenPosY(10.0f);
        Iterator<AbstractRelic> it = this.relics.iterator();
        while (it.hasNext()) {
            AbstractRelic r = it.next();
            if (col == 15) {
                col = 0;
                row++;
            }
            r.currentX = relicStartX + (RELIC_SPACE * col);
            r.currentY = relicStartY - (RELIC_SPACE * row);
            r.hb.move(r.currentX, r.currentY);
            r.render(sb, false, Settings.TWO_THIRDS_TRANSPARENT_BLACK_COLOR);
            col++;
        }
        return relicStartY - (RELIC_SPACE * row);
    }

    private void handleRelicInteraction() {
        Iterator<AbstractRelic> it = this.relics.iterator();
        while (it.hasNext()) {
            AbstractRelic r = it.next();
            boolean wasScreenUp = AbstractDungeon.isScreenUp;
            AbstractDungeon.isScreenUp = true;
            r.update();
            AbstractDungeon.isScreenUp = wasScreenUp;
            if (r.hb.hovered) {
                this.hoveredRelic = r;
            }
        }
        if (this.hoveredRelic != null) {
            CardCrawlGame.cursor.changeType(GameCursor.CursorType.INSPECT);
            if (InputHelper.justClickedLeft) {
                this.clickStartedRelic = this.hoveredRelic;
            }
            if (InputHelper.justReleasedClickLeft && this.hoveredRelic == this.clickStartedRelic) {
                CardCrawlGame.relicPopup.open(this.hoveredRelic, this.relics);
                this.clickStartedRelic = null;
            }
        } else {
            this.clickStartedRelic = null;
        }
        this.hoveredRelic = null;
    }

    private float screenPos(float val) {
        return val * Settings.scale;
    }

    private float screenPosX(float val) {
        return val * Settings.xScale;
    }

    private float screenPosY(float val) {
        return val * Settings.yScale;
    }

    private void updateArrows() {
        if (this.runIndex < this.filteredRuns.size() - 1) {
            this.nextHb.update();
            if (this.nextHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            } else if (this.nextHb.hovered && InputHelper.justClickedLeft) {
                this.nextHb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            } else if (this.nextHb.clicked
                    || (CInputActionSet.pageRightViewExhaust.isJustPressed() && !CardCrawlGame.isPopupOpen)) {
                this.nextHb.clicked = false;
                this.runIndex = Math.min(this.runIndex + 1, this.filteredRuns.size() - 1);
                reloadWithRunData(this.filteredRuns.get(this.runIndex));
            }
        }
        if (this.runIndex > 0) {
            this.prevHb.update();
            if (this.prevHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            } else if (this.prevHb.hovered && InputHelper.justClickedLeft) {
                this.prevHb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            } else if (this.prevHb.clicked
                    || (CInputActionSet.pageLeftViewDeck.isJustPressed() && !CardCrawlGame.isPopupOpen)) {
                this.prevHb.clicked = false;
                this.runIndex = Math.max(0, this.runIndex - 1);
                reloadWithRunData(this.filteredRuns.get(this.runIndex));
            }
        }
    }

    private void renderFilters(SpriteBatch sb) {
        float filterX = this.screenX + screenPosX(-270.0f);
        float winLossY = this.scrollY + screenPosY(1000.0f);
        float charY = winLossY - screenPosY(54.0f);
        float runTypeY = charY - screenPosY(54.0f);
        this.runTypeFilter.render(sb, filterX, runTypeY);
        this.characterFilter.render(sb, filterX, charY);
        this.winLossFilter.render(sb, filterX, winLossY);
    }

    private void updateScrolling() {
        int y = InputHelper.mY;
        if (this.scrollUpperBound > 0.0f) {
            if (!this.grabbedScreen) {
                if (InputHelper.scrolledDown) {
                    this.scrollTargetY += Settings.SCROLL_SPEED;
                } else if (InputHelper.scrolledUp) {
                    this.scrollTargetY -= Settings.SCROLL_SPEED;
                }
                if (InputHelper.justClickedLeft) {
                    this.grabbedScreen = true;
                    this.grabStartY = y - this.scrollTargetY;
                }
            } else if (InputHelper.isMouseDown) {
                this.scrollTargetY = y - this.grabStartY;
            } else {
                this.grabbedScreen = false;
            }
        }
        this.scrollY = MathHelper.scrollSnapLerpSpeed(this.scrollY, this.scrollTargetY);
        resetScrolling();
    }

    private void resetScrolling() {
        if (this.scrollTargetY < this.scrollLowerBound) {
            this.scrollTargetY = MathHelper.scrollSnapLerpSpeed(this.scrollTargetY, this.scrollLowerBound);
        } else if (this.scrollTargetY > this.scrollUpperBound) {
            this.scrollTargetY = MathHelper.scrollSnapLerpSpeed(this.scrollTargetY, this.scrollUpperBound);
        }
    }

    @Override // com.megacrit.cardcrawl.screens.options.DropdownMenuListener
    public void changedSelectionTo(DropdownMenu dropdownMenu, int index, String optionText) {
        if (dropdownMenu == this.runsDropdown) {
            runDropdownChangedTo(index);
        } else if (isFilterDropdown(dropdownMenu)) {
            resetRunsDropdown();
        }
    }

    private boolean isFilterDropdown(DropdownMenu dropdownMenu) {
        return dropdownMenu == this.winLossFilter || dropdownMenu == this.characterFilter
                || dropdownMenu == this.runTypeFilter;
    }

    private void runDropdownChangedTo(int index) {
        if (this.runIndex != index) {
            this.runIndex = index;
            reloadWithRunData(this.filteredRuns.get(this.runIndex));
        }
    }
}

