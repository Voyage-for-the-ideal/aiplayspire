package com.megacrit.cardcrawl.audio;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class SoundMaster {
    private static final Logger logger = LogManager.getLogger(SoundMaster.class.getName());
    private HashMap<String, Sfx> map = new HashMap<>();
    private ArrayList<SoundInfo> fadeOutList = new ArrayList<>();
    private static final String SFX_DIR = "audio/sound/";

    public SoundMaster() {
        long startTime = System.currentTimeMillis();
        Settings.SOUND_VOLUME = Settings.soundPref.getFloat("Sound Volume", 0.5F);

        this.map.put("AMBIANCE_BOTTOM", load("SOTE_Level1_Ambience_v6.ogg"));
        this.map.put("AMBIANCE_CITY", load("SOTE_SFX_CityAmb_v1.ogg"));
        this.map.put("AMBIANCE_BEYOND", load("STS_SFX_BeyondAmb_v1.ogg"));
        this.map.put("SCENE_TORCH_EXTINGUISH", load("STS_SFX_BGTorchExtinguish_v1.ogg"));
        this.map.put("APPEAR", load("SOTE_SFX_Appear_v2.ogg"));

        this.map.put("ATTACK_DAGGER_1", load("STS_SFX_DaggerThrow_1.ogg"));
        this.map.put("ATTACK_DAGGER_2", load("STS_SFX_DaggerThrow_2.ogg"));
        this.map.put("ATTACK_DAGGER_3", load("STS_SFX_DaggerThrow_3.ogg"));
        this.map.put("ATTACK_DAGGER_4", load("STS_SFX_DaggerThrow_2_1.ogg"));
        this.map.put("ATTACK_DAGGER_5", load("STS_SFX_DaggerThrow_2_2.ogg"));
        this.map.put("ATTACK_DAGGER_6", load("STS_SFX_DaggerThrow_2_3.ogg"));
        this.map.put("ATTACK_DEFECT_BEAM", load("STS_SFX_DefectBeam_v1.ogg"));
        this.map.put("ATTACK_FAST", load("SOTE_SFX_FastAtk_v2.ogg"));
        this.map.put("ATTACK_FIRE", load("SOTE_SFX_FireIgnite_2_v1.ogg"));
        this.map.put("ATTACK_FLAME_BARRIER", load("STS_SFX_FlameBarrier_v2.ogg"));
        this.map.put("ATTACK_HEAVY", load("SOTE_SFX_HeavyAtk_v2.ogg"));
        this.map.put("ATTACK_IRON_1", load("SOTE_SFX_IronClad_Atk_RR1_v2.ogg"));
        this.map.put("ATTACK_IRON_2", load("SOTE_SFX_IronClad_Atk_RR2_v2.ogg"));
        this.map.put("ATTACK_IRON_3", load("SOTE_SFX_IronClad_Atk_RR3_v2.ogg"));
        this.map.put("ATTACK_MAGIC_BEAM", load("SOTE_SFX_SlowMagic_Beam_v1.ogg"));
        this.map.put("ATTACK_MAGIC_BEAM_SHORT", load("SOTE_SFX_SlowMagic_BeamShort_v1.ogg"));
        this.map.put("ATTACK_MAGIC_FAST_1", load("SOTE_SFX_MagicFast_1_v1.ogg"));
        this.map.put("ATTACK_MAGIC_FAST_2", load("SOTE_SFX_MagicFast_2_v1.ogg"));
        this.map.put("ATTACK_MAGIC_FAST_3", load("SOTE_SFX_MagicFast_3_v1.ogg"));
        this.map.put("ATTACK_MAGIC_SLOW_1", load("SOTE_SFX_SlowMagic_1_v1.ogg"));
        this.map.put("ATTACK_MAGIC_SLOW_2", load("SOTE_SFX_SlowMagic_2_v1.ogg"));
        this.map.put("ATTACK_PIERCING_WAIL", load("STS_SFX_PiercingWail_v2.ogg"));
        this.map.put("ATTACK_POISON", load("SOTE_SFX_PoisonCard_1_v1.ogg"));
        this.map.put("ATTACK_POISON2", load("SOTE_SFX_PoisonCard_2_v1.ogg"));
        this.map.put("ATTACK_WHIFF_1", load("SOTE_SFX_SlowThrow_1_v1.ogg"));
        this.map.put("ATTACK_WHIFF_2", load("SOTE_SFX_SlowThrow_2_v1.ogg"));
        this.map.put("ATTACK_WHIRLWIND", load("STS_SFX_Whirlwind_v2.ogg"));
        this.map.put("ATTACK_BOWLING", load("bowling.ogg"));

        this.map.put("CARD_DRAW_8", load("STS_SFX_CardDeal8_v1.ogg"));
        this.map.put("KEY_OBTAIN", load("SOTE_SFX_Key_v2.ogg"));

        this.map.put("AUTOMATON_ORB_SPAWN", load("STS_SFX_AutomatonOrbSpawn_v1.ogg"));
        this.map.put("BATTLE_START_BOSS", load("STS_SFX_BattleStart_Boss_v1.ogg"));
        this.map.put("BATTLE_START_1", load("STS_SFX_BattleStart_1_v1.ogg"));
        this.map.put("BATTLE_START_2", load("STS_SFX_BattleStart_2_v1.ogg"));
        this.map.put("BELL", load("SOTE_SFX_Bell_v1.ogg"));
        this.map.put("BLOCK_ATTACK", load("SOTE_SFX_BlockAtk_v2.ogg"));
        this.map.put("BLOCK_BREAK", load("SOTE_SFX_DefenseBreak_v2.ogg"));
        this.map.put("BLOCK_GAIN_1", load("SOTE_SFX_GainDefense_RR1_v3.ogg"));
        this.map.put("BLOCK_GAIN_2", load("SOTE_SFX_GainDefense_RR3_v3.ogg"));
        this.map.put("BLOCK_GAIN_3", load("SOTE_SFX_GainDefense_RR2_v3.ogg"));
        this.map.put("BLOOD_SPLAT", load("SOTE_SFX_Blood_2_v2.ogg"));
        this.map.put("BLOOD_SWISH", load("SOTE_SFX_Blood_1_v2.ogg"));
        this.map.put("BLUNT_FAST", load("SOTE_SFX_FastBlunt_v2.ogg"));
        this.map.put("BLUNT_HEAVY", load("SOTE_SFX_HeavyBlunt_v2.ogg"));
        this.map.put("BOSS_VICTORY_STINGER", load("STS_BossVictoryStinger_1_v3_SFX.ogg"));
        this.map.put("BUFF_1", load("SOTE_SFX_Buff_1_v1.ogg"));
        this.map.put("BUFF_2", load("SOTE_SFX_Buff_2_v1.ogg"));
        this.map.put("BUFF_3", load("SOTE_SFX_Buff_3_v1.ogg"));
        this.map.put("BYRD_DEATH", load("STS_SFX_ByrdDefeat_v2.ogg"));
        this.map.put("CARD_BURN", load("STS_SFX_BurnCard_v1.ogg"));
        this.map.put("CARD_EXHAUST", load("SOTE_SFX_ExhaustCard.ogg"));
        this.map.put("CARD_OBTAIN", load("SOTE_SFX_ObtainCard_v2.ogg"));
        this.map.put("CARD_REJECT", load("SOTE_SFX_CardReject_v1.ogg"));
        this.map.put("CARD_SELECT", load("SOTE_SFX_CardSelect_v2.ogg"));
        this.map.put("CARD_UPGRADE", load("SOTE_SFX_UpgradeCard_v1.ogg"));
        this.map.put("CEILING_BOOM_1", load("SOTE_SFX_CeilingDust1_Boom_v1.ogg"));
        this.map.put("CEILING_BOOM_2", load("SOTE_SFX_CeilingDust2_Boom_v1.ogg"));
        this.map.put("CEILING_BOOM_3", load("SOTE_SFX_CeilingDust3_Boom_v1.ogg"));
        this.map.put("CEILING_DUST_1", load("SOTE_SFX_CeilingDust1_v1.ogg"));
        this.map.put("CEILING_DUST_2", load("SOTE_SFX_CeilingDust2_v1.ogg"));
        this.map.put("CEILING_DUST_3", load("SOTE_SFX_CeilingDust3_v1.ogg"));
        this.map.put("CHEST_OPEN", load("SOTE_SFX_ChestOpen_v2.ogg"));
        this.map.put("CHOSEN_DEATH", load("STS_SFX_ChosenDefeat_v2.ogg"));
        this.map.put("DARKLING_REGROW_1", load("STS_SFX_DarklingRegrow_v2.ogg"));
        this.map.put("DARKLING_REGROW_2", load("STS_SFX_DarklingRegrow_2_v2.ogg"));
        this.map.put("DEATH_STINGER", load("STS_DeathStinger_v4_SFX.ogg"));
        this.map.put("DEBUFF_1", load("SOTE_SFX_Debuff_1_v1.ogg"));
        this.map.put("DEBUFF_2", load("SOTE_SFX_Debuff_2_v1.ogg"));
        this.map.put("DEBUFF_3", load("SOTE_SFX_Debuff_3_v1.ogg"));
        this.map.put("DECK_CLOSE", load("SOTE_SFX_UI_Parchment_2_v1.ogg"));
        this.map.put("DECK_OPEN", load("SOTE_SFX_UI_Parchment_3_v1.ogg"));
        this.map.put("DUNGEON_TRANSITION", load("SOTE_SFX_DungeonGate.ogg"));
        this.map.put("END_TURN", load("SOTE_SFX_EndTurn_v2.ogg"));
        this.map.put("ENEMY_TURN", load("SOTE_SFX_EnemyTurn_v3.ogg"));
        this.map.put("EVENT_PURCHASE", load("SOTE_SFX_EventPurchase.ogg"));

        this.map.put("EVENT_ANCIENT", load("STS_SFX_AncientWriting_v1.ogg"));
        this.map.put("EVENT_FALLING", load("STS_SFX_Falling_v1.ogg"));
        this.map.put("EVENT_FORGE", load("STS_SFX_OminousForge_v1.ogg"));
        this.map.put("EVENT_FORGOTTEN", load("STS_SFX_ForgottenShrine_v1.ogg"));
        this.map.put("EVENT_FOUNTAIN", load("STS_SFX_CursedTome_v1.ogg"));
        this.map.put("EVENT_GHOSTS", load("STS_SFX_CouncilGhosts-Mausoleum_v1.ogg"));
        this.map.put("EVENT_GOLDEN", load("STS_SFX_GoldenIdolBoulder_v1.ogg"));
        this.map.put("EVENT_GOOP", load("STS_SFX_WorldOfGoop_v1.ogg"));
        this.map.put("EVENT_LAB", load("STS_SFX_Lab_v1.ogg"));
        this.map.put("EVENT_LIVING_WALL", load("STS_SFX_LivingWall_v1.ogg"));
        this.map.put("EVENT_NLOTH", load("STS_SFX_NLoth_v1.ogg"));
        this.map.put("EVENT_OOZE", load("STS_SFX_ScrapOoze_v1.ogg"));
        this.map.put("EVENT_PORTAL", load("STS_SFX_SecretPortal_v1.ogg"));
        this.map.put("EVENT_SENSORY", load("STS_SFX_SensoryStone_v1.ogg"));
        this.map.put("EVENT_SERPENT", load("STS_SFX_Ssserpent_v1.ogg"));
        this.map.put("EVENT_SHINING", load("STS_SFX_ShiningLight_v1.ogg"));
        this.map.put("EVENT_SKULL", load("STS_SFX_KnowingSkull_v1.ogg"));
        this.map.put("EVENT_SPIRITS", load("STS_SFX_BonfireSpirits_v1.ogg"));
        this.map.put("EVENT_TOME", load("STS_SFX_CursedTome_v1.ogg"));
        this.map.put("EVENT_WINDING", load("STS_SFX_WindingHalls_v1.ogg"));
        this.map.put("EVENT_VAMP_BITE", load("STS_SFX_VampireBite_v2.ogg"));

        this.map.put("GHOST_FLAMES", load("SOTE_SFX_GhostGuardianFlames_v1.ogg"));
        this.map.put("GHOST_ORB_IGNITE_1", load("SOTE_SFX_BossOrbIgnite1_v2.ogg"));
        this.map.put("GHOST_ORB_IGNITE_2", load("SOTE_SFX_BossOrbIgnite2_v2.ogg"));
        this.map.put("GOLD_GAIN", load("SOTE_SFX_Gold_RR1_v3.ogg"));
        this.map.put("GOLD_GAIN_2", load("SOTE_SFX_Gold_RR2_v3.ogg"));
        this.map.put("GOLD_GAIN_3", load("SOTE_SFX_Gold_RR3_v3.ogg"));
        this.map.put("GOLD_GAIN_4", load("SOTE_SFX_Gold_RR4_v3.ogg"));
        this.map.put("GOLD_GAIN_5", load("SOTE_SFX_Gold_RR5_v3.ogg"));
        this.map.put("GOLD_JINGLE", load("SOTE_SFX_Gold_v1.ogg"));
        this.map.put("GUARDIAN_ROLL_UP", load("SOTE_SFX_BossBallTransform_v1.ogg"));
        this.map.put("HEAL_1", load("SOTE_SFX_HealShort_1_v2.ogg"));
        this.map.put("HEAL_2", load("SOTE_SFX_HealShort_2_v2.ogg"));
        this.map.put("HEAL_3", load("SOTE_SFX_HealShort_3_v2.ogg"));
        this.map.put("HEART_BEAT", load("SLS_SFX_HeartBeat_Resonant_v1.ogg"));
        this.map.put("HEART_SIMPLE", load("SLS_SFX_HeartBeat_Simple_v1.ogg"));
        this.map.put("HOVER_CHARACTER", load("SOTE_SFX_UI_Parchment_3_v1.ogg"));
        this.map.put("INTIMIDATE", load("SOTE_SFX_IntimidateCard_v1.ogg"));
        this.map.put("MAP_CLOSE", load("SOTE_SFX_UI_Parchment_1_v2.ogg"));
        this.map.put("MAP_HOVER_1", load("SOTE_SFX_MapHover_1_v1.ogg"));
        this.map.put("MAP_HOVER_2", load("SOTE_SFX_MapHover_2_v1.ogg"));
        this.map.put("MAP_HOVER_3", load("SOTE_SFX_MapHover_3_v1.ogg"));
        this.map.put("MAP_HOVER_4", load("SOTE_SFX_MapHover_4_v1.ogg"));
        this.map.put("MAP_OPEN", load("SOTE_SFX_Map_1_v3.ogg"));
        this.map.put("MAP_OPEN_2", load("SOTE_SFX_Map_2_v3.ogg"));
        this.map.put("MAP_SELECT_1", load("SOTE_SFX_MapSelect_1_v1.ogg"));
        this.map.put("MAP_SELECT_2", load("SOTE_SFX_MapSelect_2_v1.ogg"));
        this.map.put("MAP_SELECT_3", load("SOTE_SFX_MapSelect_3_v1.ogg"));
        this.map.put("MAP_SELECT_4", load("SOTE_SFX_MapSelect_4_v1.ogg"));
        this.map.put("MAW_DEATH", load("STS_SFX_MawDefeat_v2.ogg"));
        this.map.put("NECRONOMICON", load("SOTE_SFX_NecroLaugh_v2.ogg"));
        this.map.put("NULLIFY_SFX", load("STS_SFX_Nullify_v1.ogg"));
        this.map.put("POTION_1", load("SOTE_SFX_Potion_1_v2.ogg"));
        this.map.put("POTION_2", load("SOTE_SFX_Potion_2_v2.ogg"));
        this.map.put("POTION_3", load("SOTE_SFX_Potion_3_v2.ogg"));
        this.map.put("POTION_DROP_1", load("SOTE_SFX_DropPotion_1_v1.ogg"));
        this.map.put("POTION_DROP_2", load("SOTE_SFX_DropPotion_2_v1.ogg"));

        this.map.put("JAW_WORM_DEATH", load("STS_SFX_JawWormDefeat_v2.ogg"));
        this.map.put("MONSTER_AUTOMATON_SUMMON", load("STS_SFX_BronzeAutomatonSummon_v2.ogg"));
        this.map.put("MONSTER_AWAKENED_ATTACK", load("STS_SFX_AwakenedOne3Atk_v1.ogg"));
        this.map.put("MONSTER_AWAKENED_POUNCE", load("STS_SFX_AwakenedOnePounce_v2.ogg"));
        this.map.put("MONSTER_BYRD_ATTACK_0", load("STS_SFX_ByrdAtk1_v2.ogg"));
        this.map.put("MONSTER_BYRD_ATTACK_1", load("STS_SFX_ByrdAtk2_v2.ogg"));
        this.map.put("MONSTER_BYRD_ATTACK_2", load("STS_SFX_ByrdAtk3_v2.ogg"));
        this.map.put("MONSTER_BYRD_ATTACK_3", load("STS_SFX_ByrdAtk4_v2.ogg"));
        this.map.put("MONSTER_BYRD_ATTACK_4", load("STS_SFX_ByrdAtk5_v2.ogg"));
        this.map.put("MONSTER_BYRD_ATTACK_5", load("STS_SFX_ByrdAtk6_v2.ogg"));
        this.map.put("MONSTER_CHAMP_CHARGE", load("STS_SFX_ChampChargeUp_v2.ogg"));
        this.map.put("MONSTER_CHAMP_SLAP", load("STS_SFX_ChampSlap_v2.ogg"));
        this.map.put("MONSTER_COLLECTOR_DEBUFF", load("STS_SFX_CollectorDebuff_v2.ogg"));
        this.map.put("MONSTER_COLLECTOR_SUMMON", load("STS_SFX_CollectorSummon_v2.ogg"));
        this.map.put("MONSTER_DONU_DEFENSE", load("STS_SFX_DonuDecaDefense_v2.ogg"));
        this.map.put("MONSTER_GUARDIAN_DESTROY", load("STS_SFX_Guardian3Destroy_v2.ogg"));
        this.map.put("MONSTER_JAW_WORM_BELLOW", load("STS_SFX_JawWormBellow_v1.ogg"));
        this.map.put("MONSTER_SLIME_ATTACK", load("STS_SFX_SlimedAtk_v2.ogg"));
        this.map.put("MONSTER_BOOK_STAB_0", load("STS_SFX_BookofStabbing1_v1.ogg"));
        this.map.put("MONSTER_BOOK_STAB_1", load("STS_SFX_BookofStabbing2_v1.ogg"));
        this.map.put("MONSTER_BOOK_STAB_2", load("STS_SFX_BookofStabbing3_v1.ogg"));
        this.map.put("MONSTER_BOOK_STAB_3", load("STS_SFX_BookofStabbing4_v1.ogg"));
        this.map.put("MONSTER_SNECKO_GLARE", load("STS_SFX_SneckoGlareWave_v1.ogg"));

        this.map.put("POWER_CONFUSION", load("STS_SFX_Confused_v2.ogg"));
        this.map.put("POWER_CONSTRICTED", load("STS_SFX_Constrict_v2.ogg"));
        this.map.put("POWER_DEXTERITY", load("STS_SFX_Dexterity_v2.ogg"));
        this.map.put("POWER_ENTANGLED", load("STS_SFX_Entangle_v2.ogg"));
        this.map.put("POWER_FLIGHT", load("STS_SFX_Flight_v2.ogg"));
        this.map.put("POWER_FOCUS", load("STS_SFX_Focus_v2.ogg"));
        this.map.put("POWER_INTANGIBLE", load("STS_SFX_Intangible_v1.ogg"));
        this.map.put("POWER_METALLICIZE", load("STS_SFX_Metallicize_v2.ogg"));
        this.map.put("POWER_PLATED", load("STS_SFX_PlateArmor_v2.ogg"));
        this.map.put("POWER_POISON", load("STS_SFX_PoisonApply_v1.ogg"));
        this.map.put("POWER_SHACKLE", load("STS_SFX_Shackled_v1.ogg"));
        this.map.put("POWER_STRENGTH", load("STS_SFX_Strength_v1.ogg"));
        this.map.put("POWER_TIME_WARP", load("STS_SFX_TimeWarp_v2.ogg"));

        this.map.put("RAGE", load("SOTE_SFX_RageCard_v1.ogg"));
        this.map.put("RELIC_DROP_CLINK", load("SOTE_SFX_DropRelic_Clink.ogg"));
        this.map.put("RELIC_DROP_FLAT", load("SOTE_SFX_DropRelic_Flat.ogg"));
        this.map.put("RELIC_DROP_HEAVY", load("SOTE_SFX_DropRelic_Heavy.ogg"));
        this.map.put("RELIC_DROP_MAGICAL", load("SOTE_SFX_DropRelic_Magical.ogg"));
        this.map.put("RELIC_DROP_ROCKY", load("SOTE_SFX_DropRelic_Rocky.ogg"));
        this.map.put("REST_FIRE_DRY", load("SOTE_SFX_RestFireDry_v2.ogg"));
        this.map.put("REST_FIRE_WET", load("SOTE_SFX_RestFireWet_v2.ogg"));
        this.map.put("SHOP_CLOSE", load("SOTE_SFX_ShopRugClose_v1.ogg"));
        this.map.put("SHOP_OPEN", load("SOTE_SFX_ShopRugOpen_v1.ogg"));
        this.map.put("SHOP_PURCHASE", load("SOTE_SFX_CashRegister.ogg"));
        this.map.put("SHOVEL", load("sts_sfx_shovel_v1.ogg"));
        this.map.put("SINGING_BOWL", load("SOTE_SFX_Relic_PrayerBowl_Soft.ogg"));
        this.map.put("SLEEP_1-1", load("STS_SleepJingle_1a_NewMix_v1.ogg"));
        this.map.put("SLEEP_1-2", load("STS_SleepJingle_1b_NewMix_v1.ogg"));
        this.map.put("SLEEP_1-3", load("STS_SleepJingle_1c_NewMix_v1.ogg"));
        this.map.put("SLEEP_2-1", load("STS_SleepJingle_2a_NewMix_v1.ogg"));
        this.map.put("SLEEP_2-2", load("STS_SleepJingle_2b_NewMix_v1.ogg"));
        this.map.put("SLEEP_2-3", load("STS_SleepJingle_2c_NewMix_v1.ogg"));
        this.map.put("SLEEP_3-1", load("STS_SleepJingle_3a_NewMix_v1.ogg"));
        this.map.put("SLEEP_3-2", load("STS_SleepJingle_3b_NewMix_v1.ogg"));
        this.map.put("SLEEP_3-3", load("STS_SleepJingle_3c_NewMix_v1.ogg"));
        this.map.put("SLEEP_BLANKET", load("SOTE_SFX_SleepBlanket_v1.ogg"));
        this.map.put("SLIME_ATTACK", load("SOTE_SFX_SlimeAtk_1_v1.ogg"));
        this.map.put("SLIME_ATTACK_2", load("SOTE_SFX_SlimeAtk_2_v1.ogg"));
        this.map.put("SLIME_BLINK_1", load("SOTE_SFX_SlimeBlink_1_v2.ogg"));
        this.map.put("SLIME_BLINK_2", load("SOTE_SFX_SlimeBlink_2_v1.ogg"));
        this.map.put("SLIME_BLINK_3", load("SOTE_SFX_SlimeBlink_3_v1.ogg"));
        this.map.put("SLIME_BLINK_4", load("SOTE_SFX_SlimeBlink_4_v1.ogg"));
        this.map.put("SLIME_SPLIT", load("SOTE_SFX_SlimeSplit_v1.ogg"));
        this.map.put("SNECKO_DEATH", load("STS_SFX_SerpentSneckoDefeat_v2.ogg"));
        this.map.put("SPHERE_DETECT_VO_1", load("STS_SFX_GuardianOutsiderDetected_1_v1.ogg"));
        this.map.put("SPHERE_DETECT_VO_2", load("STS_SFX_GuardianOutsiderDetected_2_v1.ogg"));
        this.map.put("SPLASH", load("SOTE_Logo_Echoing_ShortTail.ogg"));
        this.map.put("SPORE_CLOUD_RELEASE", load("STS_SFX_SporeCloud.ogg"));
        this.map.put("STAB_BOOK_DEATH", load("STS_SFX_BookOfStabbingDefeat_v2.ogg"));
        this.map.put("THUNDERCLAP", load("SOTE_SFX_ThunderclapCard_v1.ogg"));
        this.map.put("TINGSHA", load("SOTE_SFX_Relic_Tingsha.ogg"));
        this.map.put("DAMARU", load("damaru.ogg"));
        this.map.put("TURN_EFFECT", load("SOTE_SFX_PlayerTurn_v4_1.ogg"));
        this.map.put("UI_CLICK_1", load("SOTE_SFX_UIClick_1_v2.wav"));
        this.map.put("UI_CLICK_2", load("SOTE_SFX_UIClick_2_v2.wav"));
        this.map.put("UI_HOVER", load("SOTE_SFX_UIHover_v2.wav"));
        this.map.put("UNLOCK_SCREEN", load("STS_UnlockScreen_v1.ogg"));
        this.map.put("UNLOCK_WHIR", load("STS_XPBar_Classic_v1.ogg"));
        this.map.put("UNLOCK_PING", load("STS_NewUnlock_v1.ogg"));
        this.map.put("VICTORY", load("SOTE_SFX_Victory_v1.ogg"));
        this.map.put("WHEEL", load("SOTE_SFX_Wheel_v2.ogg"));
        this.map.put("WIND", load("SOTE_SFX_WindAmb_v1.ogg"));

        this.map.put("VO_AWAKENEDONE_1", load("vo/STS_VO_AwakenedOne_1_v2.ogg"));
        this.map.put("VO_AWAKENEDONE_2", load("vo/STS_VO_AwakenedOne_2_v2.ogg"));
        this.map.put("VO_AWAKENEDONE_3", load("vo/STS_VO_AwakenedOne_3_v2.ogg"));
        this.map.put("VO_CULTIST_1A", load("vo/STS_VO_CrowCultist_1a.ogg"));
        this.map.put("VO_CULTIST_1B", load("vo/STS_VO_CrowCultist_1b.ogg"));
        this.map.put("VO_CULTIST_1C", load("vo/STS_VO_CrowCultist_1c.ogg"));
        this.map.put("VO_CULTIST_2A", load("vo/STS_VO_CrowCultist_2a.ogg"));
        this.map.put("VO_CULTIST_2B", load("vo/STS_VO_CrowCultist_2b.ogg"));
        this.map.put("VO_CULTIST_2C", load("vo/STS_VO_CrowCultist_2c.ogg"));
        this.map.put("VO_FLAMEBRUISER_1", load("vo/STS_VO_FlameBruiser_1_v3.ogg"));
        this.map.put("VO_FLAMEBRUISER_2", load("vo/STS_VO_FlameBruiser_2_v3.ogg"));
        this.map.put("VO_GIANTHEAD_1A", load("vo/STS_VO_GiantHead_1a.ogg"));
        this.map.put("VO_GIANTHEAD_1B", load("vo/STS_VO_GiantHead_1b.ogg"));
        this.map.put("VO_GIANTHEAD_1C", load("vo/STS_VO_GiantHead_1c.ogg"));
        this.map.put("VO_GIANTHEAD_2A", load("vo/STS_VO_GiantHead_2a.ogg"));
        this.map.put("VO_GIANTHEAD_2B", load("vo/STS_VO_GiantHead_2b.ogg"));
        this.map.put("VO_GIANTHEAD_2C", load("vo/STS_VO_GiantHead_2c.ogg"));
        this.map.put("VO_GREMLINANGRY_1A", load("vo/STS_VO_GremlinAngry_1a.ogg"));
        this.map.put("VO_GREMLINANGRY_1B", load("vo/STS_VO_GremlinAngry_1b.ogg"));
        this.map.put("VO_GREMLINANGRY_1C", load("vo/STS_VO_GremlinAngry_1c.ogg"));
        this.map.put("VO_GREMLINANGRY_2A", load("vo/STS_VO_GremlinAngry_2a.ogg"));
        this.map.put("VO_GREMLINANGRY_2B", load("vo/STS_VO_GremlinAngry_2b.ogg"));
        this.map.put("VO_GREMLINCALM_1A", load("vo/STS_VO_GremlinCalm_1a.ogg"));
        this.map.put("VO_GREMLINCALM_1B", load("vo/STS_VO_GremlinCalm_1b.ogg"));
        this.map.put("VO_GREMLINCALM_2A", load("vo/STS_VO_GremlinCalm_2a.ogg"));
        this.map.put("VO_GREMLINCALM_2B", load("vo/STS_VO_GremlinCalm_2b.ogg"));
        this.map.put("VO_GREMLINDOPEY_1A", load("vo/STS_VO_GremlinDopey_1a.ogg"));
        this.map.put("VO_GREMLINDOPEY_1B", load("vo/STS_VO_GremlinDopey_1b.ogg"));
        this.map.put("VO_GREMLINDOPEY_2A", load("vo/STS_VO_GremlinDopey_2a.ogg"));
        this.map.put("VO_GREMLINDOPEY_2B", load("vo/STS_VO_GremlinDopey_2b.ogg"));
        this.map.put("VO_GREMLINDOPEY_2C", load("vo/STS_VO_GremlinDopey_2c.ogg"));
        this.map.put("VO_GREMLINFAT_1A", load("vo/STS_VO_GremlinFat_1a.ogg"));
        this.map.put("VO_GREMLINFAT_1B", load("vo/STS_VO_GremlinFat_1b.ogg"));
        this.map.put("VO_GREMLINFAT_1C", load("vo/STS_VO_GremlinFat_1c.ogg"));
        this.map.put("VO_GREMLINFAT_2A", load("vo/STS_VO_GremlinFat_2a.ogg"));
        this.map.put("VO_GREMLINFAT_2B", load("vo/STS_VO_GremlinFat_2b.ogg"));
        this.map.put("VO_GREMLINFAT_2C", load("vo/STS_VO_GremlinFat_2c.ogg"));
        this.map.put("VO_GREMLINNOB_1A", load("vo/STS_VO_GremlinNob_1a_v3.ogg"));
        this.map.put("VO_GREMLINNOB_1B", load("vo/STS_VO_GremlinNob_1b_v3.ogg"));
        this.map.put("VO_GREMLINNOB_1C", load("vo/STS_VO_GremlinNob_1d2b_v3.ogg"));
        this.map.put("VO_GREMLINNOB_2A", load("vo/STS_VO_GremlinNob_2a_v3.ogg"));
        this.map.put("VO_GREMLINSPAZZY_1A", load("vo/STS_VO_GremlinSpazzy_1a.ogg"));
        this.map.put("VO_GREMLINSPAZZY_1B", load("vo/STS_VO_GremlinSpazzy_1b.ogg"));
        this.map.put("VO_GREMLINSPAZZY_2A", load("vo/STS_VO_GremlinSpazzy_2a.ogg"));
        this.map.put("VO_GREMLINSPAZZY_2B", load("vo/STS_VO_GremlinSpazzy_2b.ogg"));
        this.map.put("VO_GREMLINSPAZZY_2C", load("vo/STS_VO_GremlinSpazzy_2c.ogg"));
        this.map.put("VO_HEALER_1A", load("vo/STS_VO_Healer_1a.ogg"));
        this.map.put("VO_HEALER_1B", load("vo/STS_VO_Healer_1b.ogg"));
        this.map.put("VO_HEALER_2A", load("vo/STS_VO_Healer_2a.ogg"));
        this.map.put("VO_HEALER_2B", load("vo/STS_VO_Healer_2b.ogg"));
        this.map.put("VO_HEALER_2C", load("vo/STS_VO_Healer_2c.ogg"));
        this.map.put("VO_IRONCLAD_1A", load("vo/STS_VO_Ironclad_1a.ogg"));
        this.map.put("VO_IRONCLAD_1B", load("vo/STS_VO_Ironclad_1b.ogg"));
        this.map.put("VO_IRONCLAD_1C", load("vo/STS_VO_Ironclad_1c.ogg"));
        this.map.put("VO_IRONCLAD_2A", load("vo/STS_VO_Ironclad_2a.ogg"));
        this.map.put("VO_IRONCLAD_2B", load("vo/STS_VO_Ironclad_2b.ogg"));
        this.map.put("VO_IRONCLAD_2C", load("vo/STS_VO_Ironclad_2c.ogg"));
        this.map.put("VO_LOOTER_1A", load("vo/STS_VO_Looter_1a.ogg"));
        this.map.put("VO_LOOTER_1B", load("vo/STS_VO_Looter_1b.ogg"));
        this.map.put("VO_LOOTER_1C", load("vo/STS_VO_Looter_1c.ogg"));
        this.map.put("VO_LOOTER_2A", load("vo/STS_VO_Looter_2a.ogg"));
        this.map.put("VO_LOOTER_2B", load("vo/STS_VO_Looter_2b.ogg"));
        this.map.put("VO_LOOTER_2C", load("vo/STS_VO_Looter_2c.ogg"));
        this.map.put("VO_MERCENARY_1A", load("vo/STS_VO_Mercenary_1a.ogg"));
        this.map.put("VO_MERCENARY_1B", load("vo/STS_VO_Mercenary_1b.ogg"));
        this.map.put("VO_MERCENARY_2A", load("vo/STS_VO_Mercenary_2a.ogg"));
        this.map.put("VO_MERCENARY_3A", load("vo/STS_VO_Mercenary_3a.ogg"));
        this.map.put("VO_MERCENARY_3B", load("vo/STS_VO_Mercenary_3b.ogg"));
        this.map.put("VO_MERCHANT_2A", load("vo/STS_VO_Merchant_2a.ogg"));
        this.map.put("VO_MERCHANT_2B", load("vo/STS_VO_Merchant_2b.ogg"));
        this.map.put("VO_MERCHANT_2C", load("vo/STS_VO_Merchant_2c.ogg"));
        this.map.put("VO_MERCHANT_3A", load("vo/STS_VO_Merchant_3a.ogg"));
        this.map.put("VO_MERCHANT_3B", load("vo/STS_VO_Merchant_3b.ogg"));
        this.map.put("VO_MERCHANT_3C", load("vo/STS_VO_Merchant_3c.ogg"));
        this.map.put("VO_MERCHANT_KA", load("vo/STS_VO_Merchant_Kekeke_a.ogg"));
        this.map.put("VO_MERCHANT_KB", load("vo/STS_VO_Merchant_Kekeke_b.ogg"));
        this.map.put("VO_MERCHANT_KC", load("vo/STS_VO_Merchant_Kekeke_c.ogg"));
        this.map.put("VO_MERCHANT_MA", load("vo/STS_VO_Merchant_Mlyah_a.ogg"));
        this.map.put("VO_MERCHANT_MB", load("vo/STS_VO_Merchant_Mlyah_b.ogg"));
        this.map.put("VO_MERCHANT_MC", load("vo/STS_VO_Merchant_Mlyah_c.ogg"));
        this.map.put("VO_MUGGER_1A", load("vo/STS_VO_Mugger_1a.ogg"));
        this.map.put("VO_MUGGER_1B", load("vo/STS_VO_Mugger_1b.ogg"));
        this.map.put("VO_MUGGER_2A", load("vo/STS_VO_Mugger_2a.ogg"));
        this.map.put("VO_MUGGER_2B", load("vo/STS_VO_Mugger_2b.ogg"));
        this.map.put("VO_NEMESIS_1A", load("vo/STS_VO_Nemesis_1a.ogg"));
        this.map.put("VO_NEMESIS_1B", load("vo/STS_VO_Nemesis_1b.ogg"));
        this.map.put("VO_NEMESIS_1C", load("vo/STS_VO_Nemesis_1c.ogg"));
        this.map.put("VO_NEMESIS_2A", load("vo/STS_VO_Nemesis_2a.ogg"));
        this.map.put("VO_NEMESIS_2B", load("vo/STS_VO_Nemesis_2b.ogg"));
        this.map.put("VO_NEOW_1A", load("vo/STS_VO_Neow_1a.ogg"));
        this.map.put("VO_NEOW_1B", load("vo/STS_VO_Neow_1b.ogg"));
        this.map.put("VO_NEOW_2A", load("vo/STS_VO_Neow_2a.ogg"));
        this.map.put("VO_NEOW_2B", load("vo/STS_VO_Neow_2b.ogg"));
        this.map.put("VO_NEOW_3A", load("vo/STS_VO_Neow_3a.ogg"));
        this.map.put("VO_NEOW_3B", load("vo/STS_VO_Neow_3b.ogg"));
        this.map.put("VO_SILENT_1A", load("vo/STS_VO_Silent_1a.ogg"));
        this.map.put("VO_SILENT_1B", load("vo/STS_VO_Silent_1b.ogg"));
        this.map.put("VO_SILENT_2A", load("vo/STS_VO_Silent_2a.ogg"));
        this.map.put("VO_SILENT_2B", load("vo/STS_VO_Silent_2b.ogg"));
        this.map.put("VO_SLAVERBLUE_1A", load("vo/STS_VO_SlaverBlue_1a.ogg"));
        this.map.put("VO_SLAVERBLUE_1B", load("vo/STS_VO_SlaverBlue_1b.ogg"));
        this.map.put("VO_SLAVERBLUE_2A", load("vo/STS_VO_SlaverBlue_2a.ogg"));
        this.map.put("VO_SLAVERBLUE_2B", load("vo/STS_VO_SlaverBlue_2b.ogg"));
        this.map.put("VO_SLAVERLEADER_1A", load("vo/STS_VO_SlaverLeader_1a.ogg"));
        this.map.put("VO_SLAVERLEADER_1B", load("vo/STS_VO_SlaverLeader_1b.ogg"));
        this.map.put("VO_SLAVERLEADER_2A", load("vo/STS_VO_SlaverLeader_2a.ogg"));
        this.map.put("VO_SLAVERLEADER_2B", load("vo/STS_VO_SlaverLeader_2b.ogg"));
        this.map.put("VO_SLAVERRED_1A", load("vo/STS_VO_SlaverRed_1a.ogg"));
        this.map.put("VO_SLAVERRED_1B", load("vo/STS_VO_SlaverRed_1b.ogg"));
        this.map.put("VO_SLAVERRED_2A", load("vo/STS_VO_SlaverRed_2a.ogg"));
        this.map.put("VO_SLAVERRED_2B", load("vo/STS_VO_SlaverRed_2b.ogg"));
        this.map.put("VO_SLIMEBOSS_1A", load("vo/STS_VO_SlimeBoss_1a.ogg"));
        this.map.put("VO_SLIMEBOSS_1B", load("vo/STS_VO_SlimeBoss_1b.ogg"));
        this.map.put("VO_SLIMEBOSS_1C", load("vo/STS_VO_SlimeBoss_1c.ogg"));
        this.map.put("VO_SLIMEBOSS_2A", load("vo/STS_VO_SlimeBoss_2a.ogg"));
        this.map.put("VO_TANK_1A", load("vo/STS_VO_Centurion_1_v2.ogg"));
        this.map.put("VO_TANK_1B", load("vo/STS_VO_Centurion_2_v2.ogg"));
        this.map.put("VO_TANK_1C", load("vo/STS_VO_Centurion_3_v2.ogg"));
        this.map.put("VO_CHAMP_1A", load("vo/STS_VO_TheChamp_1.ogg"));
        this.map.put("VO_CHAMP_2A", load("vo/STS_VO_TheChamp_2a.ogg"));
        this.map.put("VO_CHAMP_3A", load("vo/STS_VO_TheChamp_3a.ogg"));
        this.map.put("VO_CHAMP_3B", load("vo/STS_VO_TheChamp_3b.ogg"));
        this.map.put("ORB_DARK_CHANNEL", load("orb/STS_SFX_DarkOrb_Channel_v1.ogg"));
        this.map.put("ORB_DARK_EVOKE", load("orb/STS_SFX_DarkOrb_Evoke_v1.ogg"));
        this.map.put("ORB_FROST_CHANNEL", load("orb/STS_SFX_FrostOrb_Channel_v1.ogg"));
        this.map.put("ORB_FROST_DEFEND_1", load("orb/STS_SFX_FrostOrb_GainDefense_1_v1.ogg"));
        this.map.put("ORB_FROST_DEFEND_2", load("orb/STS_SFX_FrostOrb_GainDefense_2_v1.ogg"));
        this.map.put("ORB_FROST_DEFEND_3", load("orb/STS_SFX_FrostOrb_GainDefense_3_v1.ogg"));
        this.map.put("ORB_FROST_EVOKE", load("orb/STS_SFX_FrostOrb_Evoke_v1.ogg"));
        this.map.put("ORB_LIGHTNING_CHANNEL", load("orb/STS_SFX_LightningOrb_Channel_v1.ogg"));
        this.map.put("ORB_LIGHTNING_EVOKE", load("orb/STS_SFX_LightningOrb_Evoke_v1.ogg"));
        this.map.put("ORB_LIGHTNING_PASSIVE", load("orb/STS_SFX_LightningOrb_Passive_v1.ogg"));
        this.map.put("ORB_PLASMA_CHANNEL", load("orb/STS_SFX_PlasmaOrb_Channel_v1.ogg"));
        this.map.put("ORB_PLASMA_EVOKE", load("orb/STS_SFX_PlasmaOrb_Evoke_v1.ogg"));
        this.map.put("ORB_SLOT_GAIN", load("orb/STS_SFX_GainSlot_v1.ogg"));

        this.map.put("WATCHER_HEART_PUNCH", load("SOTE_SFX_BossGhostFireAtk_3_v1.ogg"));
        this.map.put("STANCE_ENTER_CALM", load("watcher/STS_SFX_Watcher-Calm_v2.ogg"));
        this.map.put("STANCE_ENTER_WRATH", load("watcher/STS_SFX_Watcher-Wrath_v2.ogg"));
        this.map.put("STANCE_ENTER_DIVINITY", load("watcher/STS_SFX_Watcher-Divinity_v3.ogg"));
        this.map.put("STANCE_LOOP_CALM", load("watcher/STS_SFX_Watcher-CalmLoop_v2.ogg"));
        this.map.put("STANCE_LOOP_WRATH", load("watcher/STS_SFX_Watcher-WrathLoop_v2.ogg"));
        this.map.put("STANCE_LOOP_DIVINITY", load("watcher/STS_SFX_Watcher-DivinityLoop_v2.ogg"));
        this.map.put("SELECT_WATCHER", load("watcher/STS_SFX_Watcher-Select_v2.ogg"));
        this.map.put("POWER_MANTRA", load("watcher/STS_SFX_Watcher-Mantra_v3.ogg"));

        this.map.put("CARD_POWER_WOOSH", load("STS_SFX_PowerWoosh_v1.ogg"));
        this.map.put("CARD_POWER_IMPACT", load("STS_SFX_Power_v1.ogg"));

        logger.info("Sound Effect Volume: " + Settings.SOUND_VOLUME);
        logger.info("Loaded " + this.map.size() + " Sound Effects");
        logger.info("SFX load time: " + (System.currentTimeMillis() - startTime) + "ms");
    }

    private Sfx load(String filename) {
        return load(filename, false);
    }

    private Sfx load(String filename, boolean preload) {
        return new Sfx("audio/sound/" + filename, preload);
    }

    public void update() {
        for (Iterator<SoundInfo> i = this.fadeOutList.iterator(); i.hasNext();) {
            SoundInfo e = i.next();
            e.update();
            Sfx sfx = this.map.get(e.name);

            if (sfx != null) {
                if (e.isDone) {
                    sfx.stop(e.id);
                    i.remove();
                    continue;
                }
                sfx.setVolume(e.id, Settings.SOUND_VOLUME * Settings.MASTER_VOLUME * e.volumeMultiplier);
            }
        }
    }

    public void preload(String key) {
        if (this.map.containsKey(key)) {
            logger.info("Preloading: " + key);
            long id = ((Sfx) this.map.get(key)).play(0.0F);
            ((Sfx) this.map.get(key)).stop(id);
        } else {
            logger.info("Missing: " + key);
        }
    }

    public long play(String key, boolean useBgmVolume) {
        if (CardCrawlGame.MUTE_IF_BG && Settings.isBackgrounded) {
            return 0L;
        }

        if (this.map.containsKey(key)) {
            if (useBgmVolume) {
                return ((Sfx) this.map.get(key)).play(Settings.MUSIC_VOLUME * Settings.MASTER_VOLUME);
            }
            return ((Sfx) this.map.get(key)).play(Settings.SOUND_VOLUME * Settings.MASTER_VOLUME);
        }

        logger.info("Missing: " + key);
        return 0L;
    }

    public long play(String key) {
        if (CardCrawlGame.MUTE_IF_BG && Settings.isBackgrounded) {
            return 0L;
        }

        return play(key, false);
    }

    public long play(String key, float pitchVariation) {
        if (CardCrawlGame.MUTE_IF_BG && Settings.isBackgrounded) {
            return 0L;
        }

        if (this.map.containsKey(key)) {
            return ((Sfx) this.map.get(key)).play(Settings.SOUND_VOLUME * Settings.MASTER_VOLUME, 1.0F +

                    MathUtils.random(-pitchVariation, pitchVariation), 0.0F);
        }

        logger.info("Missing: " + key);
        return 0L;
    }

    public long playA(String key, float pitchAdjust) {
        if (CardCrawlGame.MUTE_IF_BG && Settings.isBackgrounded) {
            return 0L;
        }

        if (this.map.containsKey(key)) {
            return ((Sfx) this.map.get(key)).play(Settings.SOUND_VOLUME * Settings.MASTER_VOLUME, 1.0F + pitchAdjust,
                    0.0F);
        }
        logger.info("Missing: " + key);
        return 0L;
    }

    public long playV(String key, float volumeMod) {
        if (CardCrawlGame.MUTE_IF_BG && Settings.isBackgrounded) {
            return 0L;
        }

        if (this.map.containsKey(key)) {
            return ((Sfx) this.map.get(key)).play(Settings.SOUND_VOLUME * Settings.MASTER_VOLUME * volumeMod, 1.0F,
                    0.0F);
        }
        logger.info("Missing: " + key);
        return 0L;
    }

    public long playAV(String key, float pitchAdjust, float volumeMod) {
        if (CardCrawlGame.MUTE_IF_BG && Settings.isBackgrounded) {
            return 0L;
        }

        if (this.map.containsKey(key)) {
            return ((Sfx) this.map.get(key)).play(Settings.SOUND_VOLUME * Settings.MASTER_VOLUME * volumeMod,
                    1.0F + pitchAdjust, 0.0F);
        }
        logger.info("Missing: " + key);
        return 0L;
    }

    public long playAndLoop(String key) {
        if (this.map.containsKey(key)) {
            return ((Sfx) this.map.get(key)).loop(Settings.SOUND_VOLUME * Settings.MASTER_VOLUME);
        }
        logger.info("Missing: " + key);
        return 0L;
    }

    public long playAndLoop(String key, float volume) {
        if (this.map.containsKey(key)) {
            return ((Sfx) this.map.get(key)).loop(volume);
        }
        logger.info("Missing: " + key);
        return 0L;
    }

    public void adjustVolume(String key, long id, float volume) {
        ((Sfx) this.map.get(key)).setVolume(id, volume);
    }

    public void adjustVolume(String key, long id) {
        ((Sfx) this.map.get(key)).setVolume(id, Settings.SOUND_VOLUME * Settings.MASTER_VOLUME);
    }

    public void fadeOut(String key, long id) {
        this.fadeOutList.add(new SoundInfo(key, id));
    }

    public void stop(String key, long id) {
        ((Sfx) this.map.get(key)).stop(id);
    }

    public void stop(String key) {
        if (this.map.get(key) != null)
            ((Sfx) this.map.get(key)).stop();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\audio\SoundMaster.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



