package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.blue.*;
import com.megacrit.cardcrawl.cards.colorless.*;
import com.megacrit.cardcrawl.cards.curses.*;
import com.megacrit.cardcrawl.cards.green.*;
import com.megacrit.cardcrawl.cards.optionCards.BecomeAlmighty;
import com.megacrit.cardcrawl.cards.optionCards.FameAndFortune;
import com.megacrit.cardcrawl.cards.optionCards.LiveForever;
import com.megacrit.cardcrawl.cards.purple.*;
import com.megacrit.cardcrawl.cards.red.*;
import com.megacrit.cardcrawl.cards.status.*;
import com.megacrit.cardcrawl.cards.tempCards.*;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.metrics.BotDataUploader;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CardLibrary {
    private static final Logger logger = LogManager.getLogger(CardLibrary.class.getName());
    public static int totalCardCount = 0;
    public static HashMap<String, AbstractCard> cards = new HashMap<>();
    private static HashMap<String, AbstractCard> curses = new HashMap<>();
    public static int redCards = 0, greenCards = 0, blueCards = 0, purpleCards = 0, colorlessCards = 0, curseCards = 0;
    public static int seenRedCards = 0, seenGreenCards = 0, seenBlueCards = 0, seenPurpleCards = 0;
    public static int seenColorlessCards = 0, seenCurseCards = 0;

    public enum LibraryType {
        RED, GREEN, BLUE, PURPLE, CURSE, COLORLESS;
    }

    public static void initialize() {
        long startTime = System.currentTimeMillis();

        addRedCards();
        addGreenCards();
        addBlueCards();
        addPurpleCards();
        addColorlessCards();
        addCurseCards();

        if (Settings.isDev)
            ;

        logger.info(
                "Card load time: " + (System.currentTimeMillis() - startTime) + "ms with " + cards.size() + " cards");

        if (Settings.isDev) {
            logger.info("[INFO] Red Cards: \t" + redCards);
            logger.info("[INFO] Green Cards: \t" + greenCards);
            logger.info("[INFO] Blue Cards: \t" + blueCards);
            logger.info("[INFO] Purple Cards: \t" + purpleCards);
            logger.info("[INFO] Colorless Cards: \t" + colorlessCards);
            logger.info("[INFO] Curse Cards: \t" + curseCards);
            logger.info("[INFO] Total Cards: \t"
                    + (redCards + greenCards + blueCards + purpleCards + colorlessCards + curseCards));
        }
    }

    public static void resetForReload() {
        cards = new HashMap<>();
        curses = new HashMap<>();
        totalCardCount = 0;
        redCards = 0;
        greenCards = 0;
        blueCards = 0;
        purpleCards = 0;
        colorlessCards = 0;
        curseCards = 0;
        seenRedCards = 0;
        seenGreenCards = 0;
        seenBlueCards = 0;
        seenPurpleCards = 0;
        seenColorlessCards = 0;
        seenCurseCards = 0;
    }

    private static void addRedCards() {
        add((AbstractCard) new Anger());
        add((AbstractCard) new Armaments());
        add((AbstractCard) new Barricade());
        add((AbstractCard) new Bash());
        add((AbstractCard) new BattleTrance());
        add((AbstractCard) new Berserk());
        add((AbstractCard) new BloodForBlood());
        add((AbstractCard) new Bloodletting());
        add((AbstractCard) new Bludgeon());
        add((AbstractCard) new BodySlam());
        add((AbstractCard) new Brutality());
        add((AbstractCard) new BurningPact());
        add((AbstractCard) new Carnage());
        add((AbstractCard) new Clash());
        add((AbstractCard) new Cleave());
        add((AbstractCard) new Clothesline());
        add((AbstractCard) new Combust());
        add((AbstractCard) new Corruption());
        add((AbstractCard) new DarkEmbrace());
        add((AbstractCard) new Defend_Red());
        add((AbstractCard) new DemonForm());
        add((AbstractCard) new Disarm());
        add((AbstractCard) new DoubleTap());
        add((AbstractCard) new Dropkick());
        add((AbstractCard) new DualWield());
        add((AbstractCard) new Entrench());
        add((AbstractCard) new Evolve());
        add((AbstractCard) new Exhume());
        add((AbstractCard) new Feed());
        add((AbstractCard) new FeelNoPain());
        add((AbstractCard) new FiendFire());
        add((AbstractCard) new FireBreathing());
        add((AbstractCard) new FlameBarrier());
        add((AbstractCard) new Flex());
        add((AbstractCard) new GhostlyArmor());
        add((AbstractCard) new Havoc());
        add((AbstractCard) new Headbutt());
        add((AbstractCard) new HeavyBlade());
        add((AbstractCard) new Hemokinesis());
        add((AbstractCard) new Immolate());
        add((AbstractCard) new Impervious());
        add((AbstractCard) new InfernalBlade());
        add((AbstractCard) new Inflame());
        add((AbstractCard) new Intimidate());
        add((AbstractCard) new IronWave());
        add((AbstractCard) new Juggernaut());
        add((AbstractCard) new LimitBreak());
        add((AbstractCard) new Metallicize());
        add((AbstractCard) new Offering());
        add((AbstractCard) new PerfectedStrike());
        add((AbstractCard) new PommelStrike());
        add((AbstractCard) new PowerThrough());
        add((AbstractCard) new Pummel());
        add((AbstractCard) new Rage());
        add((AbstractCard) new Rampage());
        add((AbstractCard) new Reaper());
        add((AbstractCard) new RecklessCharge());
        add((AbstractCard) new Rupture());
        add((AbstractCard) new SearingBlow());
        add((AbstractCard) new SecondWind());
        add((AbstractCard) new SeeingRed());
        add((AbstractCard) new Sentinel());
        add((AbstractCard) new SeverSoul());
        add((AbstractCard) new Shockwave());
        add((AbstractCard) new ShrugItOff());
        add((AbstractCard) new SpotWeakness());
        add((AbstractCard) new Strike_Red());
        add((AbstractCard) new SwordBoomerang());
        add((AbstractCard) new ThunderClap());
        add((AbstractCard) new TrueGrit());
        add((AbstractCard) new TwinStrike());
        add((AbstractCard) new Uppercut());
        add((AbstractCard) new Warcry());
        add((AbstractCard) new Whirlwind());
        add((AbstractCard) new WildStrike());
    }

    private static void addGreenCards() {
        add((AbstractCard) new Accuracy());
        add((AbstractCard) new Acrobatics());
        add((AbstractCard) new Adrenaline());
        add((AbstractCard) new AfterImage());
        add((AbstractCard) new Alchemize());
        add((AbstractCard) new AllOutAttack());
        add((AbstractCard) new AThousandCuts());
        add((AbstractCard) new Backflip());
        add((AbstractCard) new Backstab());
        add((AbstractCard) new Bane());
        add((AbstractCard) new BladeDance());
        add((AbstractCard) new Blur());
        add((AbstractCard) new BouncingFlask());
        add((AbstractCard) new BulletTime());
        add((AbstractCard) new Burst());
        add((AbstractCard) new CalculatedGamble());
        add((AbstractCard) new Caltrops());
        add((AbstractCard) new Catalyst());
        add((AbstractCard) new Choke());
        add((AbstractCard) new CloakAndDagger());
        add((AbstractCard) new Concentrate());
        add((AbstractCard) new CorpseExplosion());
        add((AbstractCard) new CripplingPoison());
        add((AbstractCard) new DaggerSpray());
        add((AbstractCard) new DaggerThrow());
        add((AbstractCard) new Dash());
        add((AbstractCard) new DeadlyPoison());
        add((AbstractCard) new Defend_Green());
        add((AbstractCard) new Deflect());
        add((AbstractCard) new DieDieDie());
        add((AbstractCard) new Distraction());
        add((AbstractCard) new DodgeAndRoll());
        add((AbstractCard) new Doppelganger());
        add((AbstractCard) new EndlessAgony());
        add((AbstractCard) new Envenom());
        add((AbstractCard) new EscapePlan());
        add((AbstractCard) new Eviscerate());
        add((AbstractCard) new Expertise());
        add((AbstractCard) new Finisher());
        add((AbstractCard) new Flechettes());
        add((AbstractCard) new FlyingKnee());
        add((AbstractCard) new Footwork());
        add((AbstractCard) new GlassKnife());
        add((AbstractCard) new GrandFinale());
        add((AbstractCard) new HeelHook());
        add((AbstractCard) new InfiniteBlades());
        add((AbstractCard) new LegSweep());
        add((AbstractCard) new Malaise());
        add((AbstractCard) new MasterfulStab());
        add((AbstractCard) new Neutralize());
        add((AbstractCard) new Nightmare());
        add((AbstractCard) new NoxiousFumes());
        add((AbstractCard) new Outmaneuver());
        add((AbstractCard) new PhantasmalKiller());
        add((AbstractCard) new PiercingWail());
        add((AbstractCard) new PoisonedStab());
        add((AbstractCard) new Predator());
        add((AbstractCard) new Prepared());
        add((AbstractCard) new QuickSlash());
        add((AbstractCard) new Reflex());
        add((AbstractCard) new RiddleWithHoles());
        add((AbstractCard) new Setup());
        add((AbstractCard) new Skewer());
        add((AbstractCard) new Slice());
        add((AbstractCard) new StormOfSteel());
        add((AbstractCard) new Strike_Green());
        add((AbstractCard) new SuckerPunch());
        add((AbstractCard) new Survivor());
        add((AbstractCard) new Tactician());
        add((AbstractCard) new Terror());
        add((AbstractCard) new ToolsOfTheTrade());
        add((AbstractCard) new SneakyStrike());
        add((AbstractCard) new Unload());
        add((AbstractCard) new WellLaidPlans());
        add((AbstractCard) new WraithForm());
    }

    private static void addBlueCards() {
        add((AbstractCard) new Aggregate());
        add((AbstractCard) new AllForOne());
        add((AbstractCard) new Amplify());
        add((AbstractCard) new AutoShields());
        add((AbstractCard) new BallLightning());
        add((AbstractCard) new Barrage());
        add((AbstractCard) new BeamCell());
        add((AbstractCard) new BiasedCognition());
        add((AbstractCard) new Blizzard());
        add((AbstractCard) new BootSequence());
        add((AbstractCard) new Buffer());
        add((AbstractCard) new Capacitor());
        add((AbstractCard) new Chaos());
        add((AbstractCard) new Chill());
        add((AbstractCard) new Claw());
        add((AbstractCard) new ColdSnap());
        add((AbstractCard) new CompileDriver());
        add((AbstractCard) new ConserveBattery());
        add((AbstractCard) new Consume());
        add((AbstractCard) new Coolheaded());
        add((AbstractCard) new CoreSurge());
        add((AbstractCard) new CreativeAI());
        add((AbstractCard) new Darkness());
        add((AbstractCard) new Defend_Blue());
        add((AbstractCard) new Defragment());
        add((AbstractCard) new DoomAndGloom());
        add((AbstractCard) new DoubleEnergy());
        add((AbstractCard) new Dualcast());
        add((AbstractCard) new EchoForm());
        add((AbstractCard) new Electrodynamics());
        add((AbstractCard) new Fission());
        add((AbstractCard) new ForceField());
        add((AbstractCard) new FTL());
        add((AbstractCard) new Fusion());
        add((AbstractCard) new GeneticAlgorithm());
        add((AbstractCard) new Glacier());
        add((AbstractCard) new GoForTheEyes());
        add((AbstractCard) new Heatsinks());
        add((AbstractCard) new HelloWorld());
        add((AbstractCard) new Hologram());
        add((AbstractCard) new Hyperbeam());
        add((AbstractCard) new Leap());
        add((AbstractCard) new LockOn());
        add((AbstractCard) new Loop());
        add((AbstractCard) new MachineLearning());
        add((AbstractCard) new Melter());
        add((AbstractCard) new MeteorStrike());
        add((AbstractCard) new MultiCast());
        add((AbstractCard) new Overclock());
        add((AbstractCard) new Rainbow());
        add((AbstractCard) new Reboot());
        add((AbstractCard) new Rebound());
        add((AbstractCard) new Recursion());
        add((AbstractCard) new Recycle());
        add((AbstractCard) new ReinforcedBody());
        add((AbstractCard) new Reprogram());
        add((AbstractCard) new RipAndTear());
        add((AbstractCard) new Scrape());
        add((AbstractCard) new Seek());
        add((AbstractCard) new SelfRepair());
        add((AbstractCard) new Skim());
        add((AbstractCard) new Stack());
        add((AbstractCard) new StaticDischarge());
        add((AbstractCard) new SteamBarrier());
        add((AbstractCard) new Storm());
        add((AbstractCard) new Streamline());
        add((AbstractCard) new Strike_Blue());
        add((AbstractCard) new Sunder());
        add((AbstractCard) new SweepingBeam());
        add((AbstractCard) new Tempest());
        add((AbstractCard) new ThunderStrike());
        add((AbstractCard) new Turbo());
        add((AbstractCard) new Equilibrium());
        add((AbstractCard) new WhiteNoise());
        add((AbstractCard) new Zap());
    }

    private static void addPurpleCards() {
        add((AbstractCard) new Alpha());
        add((AbstractCard) new BattleHymn());
        add((AbstractCard) new Blasphemy());
        add((AbstractCard) new BowlingBash());
        add((AbstractCard) new Brilliance());
        add((AbstractCard) new CarveReality());
        add((AbstractCard) new Collect());
        add((AbstractCard) new Conclude());
        add((AbstractCard) new ConjureBlade());
        add((AbstractCard) new Consecrate());
        add((AbstractCard) new Crescendo());
        add((AbstractCard) new CrushJoints());
        add((AbstractCard) new CutThroughFate());
        add((AbstractCard) new DeceiveReality());
        add((AbstractCard) new Defend_Watcher());
        add((AbstractCard) new DeusExMachina());
        add((AbstractCard) new DevaForm());
        add((AbstractCard) new Devotion());
        add((AbstractCard) new EmptyBody());
        add((AbstractCard) new EmptyFist());
        add((AbstractCard) new EmptyMind());
        add((AbstractCard) new Eruption());
        add((AbstractCard) new Establishment());
        add((AbstractCard) new Evaluate());
        add((AbstractCard) new Fasting());
        add((AbstractCard) new FearNoEvil());
        add((AbstractCard) new FlurryOfBlows());
        add((AbstractCard) new FlyingSleeves());
        add((AbstractCard) new FollowUp());
        add((AbstractCard) new ForeignInfluence());
        add((AbstractCard) new Foresight());
        add((AbstractCard) new Halt());
        add((AbstractCard) new Indignation());
        add((AbstractCard) new InnerPeace());
        add((AbstractCard) new Judgement());
        add((AbstractCard) new JustLucky());
        add((AbstractCard) new LessonLearned());
        add((AbstractCard) new LikeWater());
        add((AbstractCard) new MasterReality());
        add((AbstractCard) new Meditate());
        add((AbstractCard) new MentalFortress());
        add((AbstractCard) new Nirvana());
        add((AbstractCard) new Omniscience());
        add((AbstractCard) new Perseverance());
        add((AbstractCard) new Pray());
        add((AbstractCard) new PressurePoints());
        add((AbstractCard) new Prostrate());
        add((AbstractCard) new Protect());
        add((AbstractCard) new Ragnarok());
        add((AbstractCard) new ReachHeaven());
        add((AbstractCard) new Rushdown());
        add((AbstractCard) new Sanctity());
        add((AbstractCard) new SandsOfTime());
        add((AbstractCard) new SashWhip());
        add((AbstractCard) new Scrawl());
        add((AbstractCard) new SignatureMove());
        add((AbstractCard) new SimmeringFury());
        add((AbstractCard) new SpiritShield());
        add((AbstractCard) new Strike_Purple());
        add((AbstractCard) new Study());
        add((AbstractCard) new Swivel());
        add((AbstractCard) new TalkToTheHand());
        add((AbstractCard) new Tantrum());
        add((AbstractCard) new ThirdEye());
        add((AbstractCard) new Tranquility());
        add((AbstractCard) new Vault());
        add((AbstractCard) new Vigilance());
        add((AbstractCard) new Wallop());
        add((AbstractCard) new WaveOfTheHand());
        add((AbstractCard) new Weave());
        add((AbstractCard) new WheelKick());
        add((AbstractCard) new WindmillStrike());
        add((AbstractCard) new Wish());
        add((AbstractCard) new Worship());
        add((AbstractCard) new WreathOfFlame());
    }

    private static void printMissingPortraitInfo() {
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            AbstractCard card = c.getValue();
            if (card.jokePortrait == null) {
                System.out.println(card.name + ";" + card.color.name() + ";" + card.type.name());
            }
        }

        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            AbstractCard card = c.getValue();
            if (ImageMaster.loadImage("images/1024PortraitsBeta/" + card.assetUrl + ".png") == null) {
                System.out.println("[INFO] " + card.name + " missing LARGE beta portrait.");
            }
        }
    }

    private static void printBlueCards(AbstractCard.CardColor color) {
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            if (((AbstractCard) c.getValue()).color == color) {
                AbstractCard card = c.getValue();
                System.out.println(card.originalName + "; " + card.type
                        .toString() + "; " + card.rarity.toString() + "; " + card.cost + "; " + card.rawDescription);
            }
        }
    }

    private static void addColorlessCards() {
        add((AbstractCard) new Apotheosis());
        add((AbstractCard) new BandageUp());
        add((AbstractCard) new Blind());
        add((AbstractCard) new Chrysalis());
        add((AbstractCard) new DarkShackles());
        add((AbstractCard) new DeepBreath());
        add((AbstractCard) new Discovery());
        add((AbstractCard) new DramaticEntrance());
        add((AbstractCard) new Enlightenment());
        add((AbstractCard) new Finesse());
        add((AbstractCard) new FlashOfSteel());
        add((AbstractCard) new Forethought());
        add((AbstractCard) new GoodInstincts());
        add((AbstractCard) new HandOfGreed());
        add((AbstractCard) new Impatience());
        add((AbstractCard) new JackOfAllTrades());
        add((AbstractCard) new Madness());
        add((AbstractCard) new Magnetism());
        add((AbstractCard) new MasterOfStrategy());
        add((AbstractCard) new Mayhem());
        add((AbstractCard) new Metamorphosis());
        add((AbstractCard) new MindBlast());
        add((AbstractCard) new Panacea());
        add((AbstractCard) new Panache());
        add((AbstractCard) new PanicButton());
        add((AbstractCard) new Purity());
        add((AbstractCard) new SadisticNature());
        add((AbstractCard) new SecretTechnique());
        add((AbstractCard) new SecretWeapon());
        add((AbstractCard) new SwiftStrike());
        add((AbstractCard) new TheBomb());
        add((AbstractCard) new ThinkingAhead());
        add((AbstractCard) new Transmutation());
        add((AbstractCard) new Trip());
        add((AbstractCard) new Violence());

        add((AbstractCard) new Burn());
        add((AbstractCard) new Dazed());
        add((AbstractCard) new Slimed());
        add((AbstractCard) new VoidCard());
        add((AbstractCard) new Wound());

        add((AbstractCard) new Apparition());
        add((AbstractCard) new Beta());
        add((AbstractCard) new Bite());
        add((AbstractCard) new JAX());
        add((AbstractCard) new Insight());
        add((AbstractCard) new Miracle());
        add((AbstractCard) new Omega());
        add((AbstractCard) new RitualDagger());
        add((AbstractCard) new Safety());
        add((AbstractCard) new Shiv());
        add((AbstractCard) new Smite());
        add((AbstractCard) new ThroughViolence());
        add((AbstractCard) new BecomeAlmighty());
        add((AbstractCard) new FameAndFortune());
        add((AbstractCard) new LiveForever());
        add((AbstractCard) new Expunger());
    }

    private static void addCurseCards() {
        add((AbstractCard) new AscendersBane());
        add((AbstractCard) new CurseOfTheBell());
        add((AbstractCard) new Clumsy());
        add((AbstractCard) new Decay());
        add((AbstractCard) new Doubt());
        add((AbstractCard) new Injury());
        add((AbstractCard) new Necronomicurse());
        add((AbstractCard) new Normality());
        add((AbstractCard) new Pain());
        add((AbstractCard) new Parasite());
        add((AbstractCard) new Pride());
        add((AbstractCard) new Regret());
        add((AbstractCard) new Shame());
        add((AbstractCard) new Writhe());
    }

    private static void removeNonFinalizedCards() {
        ArrayList<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            if (((AbstractCard) c.getValue()).assetUrl == null) {
                toRemove.add(c.getKey());
            }
        }

        for (String s : toRemove) {
            logger.info("Removing Card " + s + " for trailer build.");
            cards.remove(s);
        }
        toRemove.clear();

        for (Map.Entry<String, AbstractCard> c : curses.entrySet()) {
            if (((AbstractCard) c.getValue()).assetUrl == null) {
                toRemove.add(c.getKey());
            }
        }

        for (String s : toRemove) {
            logger.info("Removing Curse " + s + " for trailer build.");
            curses.remove(s);
        }
    }

    public static void unlockAndSeeAllCards() {
        for (String s : UnlockTracker.lockedCards) {
            UnlockTracker.hardUnlockOverride(s);
        }

        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            if (((AbstractCard) c.getValue()).rarity != AbstractCard.CardRarity.BASIC &&
                    !UnlockTracker.isCardSeen(c.getKey())) {
                UnlockTracker.markCardAsSeen(c.getKey());
            }
        }

        for (Map.Entry<String, AbstractCard> c : curses.entrySet()) {
            if (!UnlockTracker.isCardSeen(c.getKey())) {
                UnlockTracker.markCardAsSeen(c.getKey());
            }
        }
    }

    public static void add(AbstractCard card) {
        switch (card.color) {
            case COLORLESS:
                redCards++;
                if (UnlockTracker.isCardSeen(card.cardID)) {
                    seenRedCards++;
                }
                break;
            case CURSE:
                greenCards++;
                if (UnlockTracker.isCardSeen(card.cardID)) {
                    seenGreenCards++;
                }
                break;
            case RED:
                purpleCards++;
                if (UnlockTracker.isCardSeen(card.cardID)) {
                    seenPurpleCards++;
                }
                break;
            case GREEN:
                blueCards++;
                if (UnlockTracker.isCardSeen(card.cardID)) {
                    seenBlueCards++;
                }
                break;
            case BLUE:
                colorlessCards++;
                if (UnlockTracker.isCardSeen(card.cardID)) {
                    seenColorlessCards++;
                }
                break;
            case PURPLE:
                curseCards++;
                if (UnlockTracker.isCardSeen(card.cardID)) {
                    seenCurseCards++;
                }
                curses.put(card.cardID, card);
                break;
        }

        if (!UnlockTracker.isCardSeen(card.cardID)) {
            card.isSeen = false;
        }
        cards.put(card.cardID, card);
        totalCardCount++;
    }

    public static AbstractCard getCopy(String key, int upgradeTime, int misc) {
        AbstractCard source = getCard(key);
        AbstractCard retVal = null;

        if (source == null) {
            retVal = getCard("Madness").makeCopy();
        } else {
            retVal = getCard(key).makeCopy();
        }

        for (int i = 0; i < upgradeTime; i++) {
            retVal.upgrade();
        }

        retVal.misc = misc;
        if (misc != 0) {
            if (retVal.cardID.equals("Genetic Algorithm")) {
                retVal.block = misc;
                retVal.baseBlock = misc;
                retVal.initializeDescription();
            }
            if (retVal.cardID.equals("RitualDagger")) {
                retVal.damage = misc;
                retVal.baseDamage = misc;
                retVal.initializeDescription();
            }
        }
        return retVal;
    }

    public static AbstractCard getCopy(String key) {
        return getCard(key).makeCopy();
    }

    public static AbstractCard getCard(AbstractPlayer.PlayerClass plyrClass, String key) {
        return cards.get(key);
    }

    public static AbstractCard getCard(String key) {
        return cards.get(key);
    }

    public static String getCardNameFromMetricID(String metricID) {
        String[] components = metricID.split("\\+");

        String baseId = components[0];
        AbstractCard card = cards.getOrDefault(baseId, null);
        if (card == null) {
            return metricID;
        }
        try {
            if (components.length > 1) {
                card = card.makeCopy();
                int upgrades = Integer.parseInt(components[1]);
                for (int i = 0; i < upgrades; i++) {
                    card.upgrade();
                }
            }
        } catch (IndexOutOfBoundsException | NumberFormatException indexOutOfBoundsException) {
        }

        return card.name;
    }

    public static boolean isACard(String metricID) {
        String[] components = metricID.split("\\+");
        String baseId = components[0];
        AbstractCard card = cards.getOrDefault(baseId, null);
        return (card != null);
    }

    public static AbstractCard getCurse() {
        ArrayList<String> tmp = new ArrayList<>();
        for (Map.Entry<String, AbstractCard> c : curses.entrySet()) {
            if (!((AbstractCard) c.getValue()).cardID.equals("AscendersBane")
                    && !((AbstractCard) c.getValue()).cardID.equals("Necronomicurse") &&
                    !((AbstractCard) c.getValue()).cardID.equals("CurseOfTheBell")
                    && !((AbstractCard) c.getValue()).cardID.equals("Pride")) {
                tmp.add(c.getKey());
            }
        }

        return cards.get(tmp.get(AbstractDungeon.cardRng.random(0, tmp.size() - 1)));
    }

    public static AbstractCard getCurse(AbstractCard prohibitedCard, Random rng) {
        ArrayList<String> tmp = new ArrayList<>();
        for (Map.Entry<String, AbstractCard> c : curses.entrySet()) {
            if (!Objects.equals(((AbstractCard) c.getValue()).cardID, prohibitedCard.cardID)
                    && !Objects.equals(((AbstractCard) c
                            .getValue()).cardID, "Necronomicurse")
                    &&
                    !Objects.equals(((AbstractCard) c.getValue()).cardID, "AscendersBane")
                    && !Objects.equals(((AbstractCard) c
                            .getValue()).cardID, "CurseOfTheBell")
                    &&
                    !Objects.equals(((AbstractCard) c.getValue()).cardID, "Pride")) {
                tmp.add(c.getKey());
            }
        }

        return cards.get(tmp.get(rng.random(0, tmp.size() - 1)));
    }

    public static AbstractCard getCurse(AbstractCard prohibitedCard) {
        return getCurse(prohibitedCard, new Random());
    }

    public static void uploadCardData() {
        ArrayList<String> data = new ArrayList<>();

        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            data.add(((AbstractCard) c.getValue()).gameDataUploadData());
            AbstractCard c2 = ((AbstractCard) c.getValue()).makeCopy();
            if (c2.canUpgrade()) {
                c2.upgrade();
                data.add(c2.gameDataUploadData());
            }
        }
        BotDataUploader.uploadDataAsync(BotDataUploader.GameDataType.CARD_DATA, AbstractCard.gameDataUploadHeader(),
                data);
    }

    public static ArrayList<AbstractCard> getAllCards() {
        ArrayList<AbstractCard> retVal = new ArrayList<>();
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            retVal.add(c.getValue());
        }
        return retVal;
    }

    public static AbstractCard getAnyColorCard(AbstractCard.CardType type, AbstractCard.CardRarity rarity) {
        CardGroup anyCard = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            if (((AbstractCard) c.getValue()).rarity == rarity
                    && !((AbstractCard) c.getValue()).hasTag(AbstractCard.CardTags.HEALING) && ((AbstractCard) c
                            .getValue()).type != AbstractCard.CardType.CURSE
                    && ((AbstractCard) c.getValue()).type != AbstractCard.CardType.STATUS && ((AbstractCard) c
                            .getValue()).type == type
                    && (!UnlockTracker.isCardLocked(c.getKey()) ||
                            Settings.treatEverythingAsUnlocked())) {
                anyCard.addToBottom(c.getValue());
            }
        }

        anyCard.shuffle(AbstractDungeon.cardRandomRng);
        return anyCard.getRandomCard(true, rarity);
    }

    public static AbstractCard getAnyColorCard(AbstractCard.CardRarity rarity) {
        CardGroup anyCard = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            if (((AbstractCard) c.getValue()).rarity == rarity
                    && ((AbstractCard) c.getValue()).type != AbstractCard.CardType.CURSE && ((AbstractCard) c
                            .getValue()).type != AbstractCard.CardType.STATUS
                    && (!UnlockTracker.isCardLocked(c.getKey()) ||
                            Settings.treatEverythingAsUnlocked())) {
                anyCard.addToBottom(c.getValue());
            }
        }

        anyCard.shuffle(AbstractDungeon.cardRng);
        return anyCard.getRandomCard(true, rarity).makeCopy();
    }

    public static CardGroup getEachRare(AbstractPlayer p) {
        CardGroup everyRareCard = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            if (((AbstractCard) c.getValue()).color == p.getCardColor()
                    && ((AbstractCard) c.getValue()).rarity == AbstractCard.CardRarity.RARE) {
                everyRareCard.addToBottom(((AbstractCard) c.getValue()).makeCopy());
            }
        }
        return everyRareCard;
    }

    public static ArrayList<AbstractCard> getCardList(LibraryType type) {
        ArrayList<AbstractCard> retVal = new ArrayList<>();
        switch (type) {
            case COLORLESS:
                for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
                    if (((AbstractCard) c.getValue()).color == AbstractCard.CardColor.COLORLESS) {
                        retVal.add(c.getValue());
                    }
                }
                break;
            case CURSE:
                for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
                    if (((AbstractCard) c.getValue()).color == AbstractCard.CardColor.CURSE) {
                        retVal.add(c.getValue());
                    }
                }
                break;
            case RED:
                for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
                    if (((AbstractCard) c.getValue()).color == AbstractCard.CardColor.RED) {
                        retVal.add(c.getValue());
                    }
                }
                break;
            case GREEN:
                for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
                    if (((AbstractCard) c.getValue()).color == AbstractCard.CardColor.GREEN) {
                        retVal.add(c.getValue());
                    }
                }
                break;
            case BLUE:
                for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
                    if (((AbstractCard) c.getValue()).color == AbstractCard.CardColor.BLUE) {
                        retVal.add(c.getValue());
                    }
                }
                break;
            case PURPLE:
                for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
                    if (((AbstractCard) c.getValue()).color == AbstractCard.CardColor.PURPLE) {
                        retVal.add(c.getValue());
                    }
                }
                break;
        }

        return retVal;
    }

    public static void addCardsIntoPool(ArrayList<AbstractCard> tmpPool, AbstractCard.CardColor color) {
        logger.info("[INFO] Adding " + color + " cards into card pool.");
        AbstractCard card = null;
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            card = c.getValue();
            if (card.color == color && card.rarity != AbstractCard.CardRarity.BASIC
                    && card.type != AbstractCard.CardType.STATUS
                    && (!UnlockTracker.isCardLocked(c.getKey()) || Settings.treatEverythingAsUnlocked())) {
                tmpPool.add(card);
            }
        }
    }

    public static void addRedCards(ArrayList<AbstractCard> tmpPool) {
        logger.info("[INFO] Adding red cards into card pool.");
        AbstractCard card = null;
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            card = c.getValue();
            if (card.color == AbstractCard.CardColor.RED && card.rarity != AbstractCard.CardRarity.BASIC
                    && (!UnlockTracker.isCardLocked(c.getKey()) || Settings.treatEverythingAsUnlocked())) {
                tmpPool.add(card);
            }
        }
    }

    public static void addGreenCards(ArrayList<AbstractCard> tmpPool) {
        logger.info("[INFO] Adding green cards into card pool.");
        AbstractCard card = null;
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            card = c.getValue();
            if (card.color == AbstractCard.CardColor.GREEN && card.rarity != AbstractCard.CardRarity.BASIC
                    && (!UnlockTracker.isCardLocked(c.getKey()) || Settings.treatEverythingAsUnlocked())) {
                tmpPool.add(card);
            }
        }
    }

    public static void addBlueCards(ArrayList<AbstractCard> tmpPool) {
        logger.info("[INFO] Adding blue cards into card pool.");
        AbstractCard card = null;
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            card = c.getValue();
            if (card.color == AbstractCard.CardColor.BLUE && card.rarity != AbstractCard.CardRarity.BASIC
                    && (!UnlockTracker.isCardLocked(c.getKey()) || Settings.treatEverythingAsUnlocked())) {
                tmpPool.add(card);
            }
        }
    }

    public static void addPurpleCards(ArrayList<AbstractCard> tmpPool) {
        logger.info("[INFO] Adding purple cards into card pool.");
        AbstractCard card = null;
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            card = c.getValue();
            if (card.color == AbstractCard.CardColor.PURPLE && card.rarity != AbstractCard.CardRarity.BASIC
                    && (!UnlockTracker.isCardLocked(c.getKey()) || Settings.treatEverythingAsUnlocked())) {
                tmpPool.add(card);
            }
        }
    }

    public static void addColorlessCards(ArrayList<AbstractCard> tmpPool) {
        logger.info("[INFO] Adding colorless cards into card pool.");
        AbstractCard card = null;
        for (Map.Entry<String, AbstractCard> c : cards.entrySet()) {
            card = c.getValue();
            if (card.color == AbstractCard.CardColor.COLORLESS && card.type != AbstractCard.CardType.STATUS
                    && (!UnlockTracker.isCardLocked(c.getKey()) || Settings.treatEverythingAsUnlocked()))
                tmpPool.add(card);
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\
 * CardLibrary.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

