// Additional character effects. An explicit catalogue prevents unimplemented
// green/purple cards from entering a Release build as silent no-ops.
#include "combat/CharacterCards.h"
#include "combat/BattleContext.h"
#include "game/Game.h"
#include "combat/CharacterSelection.h"
#include "combat/ExecutionBudget.h"
#include <stdexcept>
#include <cstring>
using namespace sts;
namespace {
Action bouncingFlask(int target,int times) {
    return {[target,times](BattleContext &b){
        if(target<0 || b.monsters.areMonstersBasicallyDead())return;
        if(times>1) {
            int next=b.monsters.getRandomMonsterIdx(b.cardRandomRng,true);
            b.addToTop(bouncingFlask(next,times-1));
        }
        if(b.monsters.arr[target].curHp>0)b.addToTop(Actions::DebuffEnemy<MS::POISON>(target,3,false));
    }};
}
constexpr CardId supported[] = {
    CardId::STRIKE_GREEN,CardId::STRIKE_PURPLE,CardId::DEFEND_GREEN,CardId::DEFEND_PURPLE,
    CardId::NEUTRALIZE,CardId::DEADLY_POISON,CardId::DEFLECT,CardId::BACKFLIP,
    CardId::BLADE_DANCE,CardId::CLOAK_AND_DAGGER,CardId::CATALYST,CardId::NOXIOUS_FUMES,
    CardId::FOOTWORK,CardId::BURST,CardId::ACCURACY,CardId::AFTER_IMAGE,CardId::INFINITE_BLADES,
    CardId::FLYING_KNEE,CardId::RIDDLE_WITH_HOLES,CardId::HEEL_HOOK,CardId::POISONED_STAB,
    CardId::BLUR,CardId::DODGE_AND_ROLL,CardId::ADRENALINE,CardId::ERUPTION,CardId::VIGILANCE,
    CardId::EMPTY_BODY,CardId::EMPTY_FIST,CardId::EMPTY_MIND,CardId::TRANQUILITY,CardId::CRESCENDO,
    CardId::PROSTRATE,CardId::WORSHIP,CardId::PRAY,CardId::LIKE_WATER,CardId::BATTLE_HYMN,
    CardId::CONSECRATE,CardId::FLYING_SLEEVES,
    CardId::WHEEL_KICK,CardId::THROUGH_VIOLENCE,CardId::FLURRY_OF_BLOWS,CardId::SMITE,
    CardId::SHIV,CardId::INSIGHT,CardId::MIRACLE,CardId::SAFETY,
    CardId::BACKSTAB,CardId::BANE,CardId::DAGGER_SPRAY,CardId::DASH,CardId::DIE_DIE_DIE,
    CardId::SLICE,CardId::SNEAKY_STRIKE,CardId::TERROR,CardId::LEG_SWEEP,CardId::OUTMANEUVER,
    CardId::PREDATOR,CardId::PHANTASMAL_KILLER,CardId::PIERCING_WAIL,CardId::ENVENOM,
    CardId::A_THOUSAND_CUTS,CardId::CALTROPS,CardId::BULLET_TIME,CardId::SKEWER,
    CardId::DOPPELGANGER,CardId::FINISHER,CardId::FLECHETTES,CardId::GRAND_FINALE,
    CardId::MASTERFUL_STAB,CardId::EVISCERATE,CardId::ENDLESS_AGONY,CardId::QUICK_SLASH,
    CardId::SUCKER_PUNCH,CardId::EXPERTISE,CardId::WRAITH_FORM,
    CardId::BOWLING_BASH,CardId::CARVE_REALITY,CardId::REACH_HEAVEN,CardId::DECEIVE_REALITY,
    CardId::EVALUATE,CardId::PROTECT,CardId::SANCTITY,CardId::SASH_WHIP,CardId::FOLLOW_UP,
    CardId::SWIVEL,CardId::INDIGNATION,CardId::INNER_PEACE,CardId::FEAR_NO_EVIL,
    CardId::TANTRUM,CardId::MENTAL_FORTRESS,CardId::RUSHDOWN,CardId::DEVOTION,
    CardId::MASTER_REALITY,CardId::ESTABLISHMENT,CardId::STUDY,CardId::WREATH_OF_FLAME,
    CardId::SCRAWL,CardId::ACROBATICS,CardId::PREPARED,CardId::CONCENTRATE,CardId::SURVIVOR,
    CardId::DAGGER_THROW,CardId::CALCULATED_GAMBLE,CardId::ALL_OUT_ATTACK,CardId::UNLOAD,
    CardId::STORM_OF_STEEL,CardId::REFLEX,CardId::TACTICIAN,CardId::SETUP,
    CardId::CUT_THROUGH_FATE,CardId::JUST_LUCKY,CardId::THIRD_EYE,CardId::FORESIGHT,
    CardId::NIRVANA,CardId::WEAVE,CardId::MEDITATE,CardId::WELL_LAID_PLANS,
    CardId::PERSEVERANCE,CardId::WINDMILL_STRIKE,CardId::SANDS_OF_TIME,
    CardId::HALT,CardId::SIGNATURE_MOVE,CardId::CORPSE_EXPLOSION,CardId::CRIPPLING_CLOUD,
    CardId::ALCHEMIZE,CardId::MALAISE,CardId::WAVE_OF_THE_HAND,CardId::DEUS_EX_MACHINA,
    CardId::BOUNCING_FLASK,CardId::CHOKE,CardId::DISTRACTION,CardId::GLASS_KNIFE,CardId::TOOLS_OF_THE_TRADE,
    CardId::BLASPHEMY,CardId::BRILLIANCE,CardId::COLLECT,CardId::CONCLUDE,CardId::CRUSH_JOINTS,
    CardId::FASTING,CardId::JUDGMENT,CardId::PRESSURE_POINTS,CardId::RAGNAROK,
    CardId::SIMMERING_FURY,CardId::SPIRIT_SHIELD,CardId::TALK_TO_THE_HAND,CardId::WALLOP,
    CardId::VAULT,CardId::ALPHA,CardId::BETA,CardId::OMEGA,
    CardId::CONJURE_BLADE,CardId::EXPUNGER,
    CardId::ESCAPE_PLAN,CardId::NIGHTMARE,CardId::DEVA_FORM,CardId::FOREIGN_INFLUENCE,
    CardId::LESSON_LEARNED,CardId::OMNISCIENCE,CardId::WISH,



};
}
bool sts::characterCardSupported(CardId id) {
    return std::find(std::begin(supported),std::end(supported),id)!=std::end(supported);
}
bool sts::useCharacterCard(BattleContext &bc) {
    const auto c=bc.curCardQueueItem.card;const bool up=c.upgraded;const int t=bc.curCardQueueItem.target;
    auto hit=[&](int damage){bc.addToBot(Actions::AttackEnemy(t,bc.calculateCardDamage(c,t,damage)));};
    auto allHit=[&](int base){DamageMatrix matrix{};
        for(int i=0;i<bc.monsters.monsterCount;++i)if(bc.monsters.arr[i].isTargetable())matrix[i]=bc.calculateCardDamage(c,i,base);
        bc.addToBot(Actions::AttackAllEnemy(matrix));};
    auto block=[&](int amount){bc.addToBot(Actions::GainBlock(bc.calculateCardBlock(amount)));};
    auto select=[&](CardSelectTask task,int count){bc.addToBot({[task,count](BattleContext &b){openCharacterSelection(b,task,count);}});};
    switch(c.id) {
    case CardId::NEUTRALIZE:hit(up?4:3);bc.addToBot(Actions::DebuffEnemy<MS::WEAK>(t,up?2:1,false));break;
    case CardId::POISONED_STAB:hit(up?8:6);bc.addToBot(Actions::DebuffEnemy<MS::POISON>(t,up?4:3,false));break;
    case CardId::FLYING_KNEE:hit(up?11:8);bc.addToBot(Actions::BuffPlayer<PS::ENERGIZED>(1));break;
    case CardId::RIDDLE_WITH_HOLES:for(int i=0;i<5;++i)hit(up?4:3);break;
    case CardId::HEEL_HOOK:
        hit(up?8:5);if(bc.monsters.arr[t].weak>0){bc.addToBot(Actions::DrawCards(1));bc.addToBot(Actions::GainEnergy(1));}break;
    case CardId::SHIV:hit((up?6:4)+bc.player.getStatus<PS::ACCURACY>());break;
    case CardId::DEADLY_POISON:bc.addToBot(Actions::DebuffEnemy<MS::POISON>(t,up?7:5,false));break;
    case CardId::CATALYST:bc.addToBot({[t,up](BattleContext &b){if(b.monsters.arr[t].poison>0)
        b.addToTop(Actions::DebuffEnemy<MS::POISON>(t,b.monsters.arr[t].poison*(up?2:1),false));}});break;
    case CardId::DEFLECT:block(up?7:4);break;
    case CardId::BACKFLIP:block(up?8:5);bc.addToBot(Actions::DrawCards(2));break;
    case CardId::CLOAK_AND_DAGGER:block(6);bc.addToBot(Actions::MakeTempCardInHand(CardId::SHIV,false,up?2:1));break;
    case CardId::BLADE_DANCE:bc.addToBot(Actions::MakeTempCardInHand(CardId::SHIV,false,up?4:3));break;
    case CardId::BLUR:block(up?8:5);bc.addToBot(Actions::BuffPlayer<PS::BLUR>(1));break;
    case CardId::DODGE_AND_ROLL: {
        block(up?6:4);
        const int amount=bc.calculateCardBlock(up?6:4);
        if(bc.headlessRngKnown && amount==0)bc.addToBot({[](BattleContext &b){
            // ApplyPowerAction creates a new power even at zero block. An
            // existing Next Turn Block power keeps its current amount.
            if(!b.player.hasStatus<PS::NEXT_TURN_BLOCK>()) {
                b.player.setHasStatus<PS::NEXT_TURN_BLOCK>(true);
                b.player.setStatusValueNoChecks<PS::NEXT_TURN_BLOCK>(0);
            }
        }});
        else bc.addToBot(Actions::BuffPlayer<PS::NEXT_TURN_BLOCK>(amount));
        break;
    }
    case CardId::ADRENALINE:bc.addToBot(Actions::GainEnergy(up?2:1));bc.addToBot(Actions::DrawCards(2));break;
    case CardId::BURST:bc.addToBot(Actions::BuffPlayer<PS::BURST>(up?2:1));break;
    case CardId::NOXIOUS_FUMES:bc.addToBot(Actions::BuffPlayer<PS::NOXIOUS_FUMES>(up?3:2));break;
    case CardId::FOOTWORK:bc.addToBot(Actions::BuffPlayer<PS::DEXTERITY>(up?3:2));break;
    case CardId::ACCURACY:bc.addToBot(Actions::BuffPlayer<PS::ACCURACY>(up?6:4));break;
    case CardId::AFTER_IMAGE:bc.addToBot(Actions::BuffPlayer<PS::AFTER_IMAGE>(1));break;
    case CardId::INFINITE_BLADES:bc.addToBot(Actions::BuffPlayer<PS::INFINITE_BLADES>(1));break;
    case CardId::ERUPTION:hit(9);bc.addToBot(Actions::ChangeStance(Stance::WRATH));break;
    case CardId::VIGILANCE:block(up?12:8);bc.addToBot(Actions::ChangeStance(Stance::CALM));break;
    case CardId::EMPTY_BODY:block(up?10:7);bc.addToBot(Actions::ChangeStance(Stance::NEUTRAL));break;
    case CardId::EMPTY_FIST:hit(up?14:9);bc.addToBot(Actions::ChangeStance(Stance::NEUTRAL));break;
    case CardId::EMPTY_MIND:bc.addToBot(Actions::DrawCards(up?3:2));bc.addToBot(Actions::ChangeStance(Stance::NEUTRAL));break;
    case CardId::TRANQUILITY:bc.addToBot(Actions::ChangeStance(Stance::CALM));break;
    case CardId::CRESCENDO:bc.addToBot(Actions::ChangeStance(Stance::WRATH));break;
    case CardId::PROSTRATE:bc.addToBot(Actions::BuffPlayer<PS::MANTRA>(up?3:2));block(4);break;
    case CardId::WORSHIP:bc.addToBot(Actions::BuffPlayer<PS::MANTRA>(5));break;
    case CardId::PRAY:bc.addToBot(Actions::BuffPlayer<PS::MANTRA>(up?4:3));bc.addToBot(Actions::MakeTempCardInDrawPile(CardInstance(CardId::INSIGHT),1,true));break;
    case CardId::LIKE_WATER:bc.addToBot(Actions::BuffPlayer<PS::LIKE_WATER>(up?7:5));break;
    case CardId::BATTLE_HYMN:bc.addToBot(Actions::BuffPlayer<PS::BATTLE_HYMN>(1));break;
    case CardId::CONSECRATE:allHit(up?8:5);break;
    case CardId::FLYING_SLEEVES:hit(up?6:4);hit(up?6:4);break;
    case CardId::WHEEL_KICK:hit(up?20:15);bc.addToBot(Actions::DrawCards(2));break;
    case CardId::THROUGH_VIOLENCE:hit(up?30:20);break;
    case CardId::SMITE:hit(up?16:12);break;
    case CardId::FLURRY_OF_BLOWS:hit(up?6:4);break;
    case CardId::INSIGHT:bc.addToBot(Actions::DrawCards(up?3:2));break;
    case CardId::MIRACLE:bc.addToBot(Actions::GainEnergy(up?2:1));break;
    case CardId::SAFETY:block(up?16:12);break;
    case CardId::BACKSTAB:hit(up?15:11);break;
    case CardId::BANE:hit(up?10:7);if(bc.monsters.arr[t].poison>0)hit(up?10:7);break;
    case CardId::DAGGER_SPRAY:allHit(up?6:4);allHit(up?6:4);break;
    case CardId::DASH:block(up?13:10);hit(up?13:10);break;
    case CardId::DIE_DIE_DIE:allHit(up?17:13);break;
    case CardId::SLICE:hit(up?9:6);break;
    case CardId::SNEAKY_STRIKE:hit(up?16:12);if(bc.player.cardsDiscardedThisTurn>0)bc.addToBot(Actions::GainEnergy(2));break;
    case CardId::TERROR:bc.addToBot(Actions::DebuffEnemy<MS::VULNERABLE>(t,99,false));break;
    case CardId::LEG_SWEEP:bc.addToBot(Actions::DebuffEnemy<MS::WEAK>(t,up?3:2,false));block(up?14:11);break;
    case CardId::OUTMANEUVER:bc.addToBot(Actions::BuffPlayer<PS::ENERGIZED>(up?3:2));break;
    case CardId::PREDATOR:hit(up?20:15);bc.addToBot(Actions::BuffPlayer<PS::DRAW_CARD_NEXT_TURN>(2));break;
    case CardId::PHANTASMAL_KILLER:bc.addToBot(Actions::BuffPlayer<PS::PHANTASMAL>(1));break;
    case CardId::PIERCING_WAIL:
        // The restoration marker is queued only for enemies without Artifact
        // at use time, matching the two loops in the original card.
        for(int i=0;i<bc.monsters.monsterCount;++i)if(bc.monsters.arr[i].isTargetable())
            bc.addToBot(Actions::DebuffEnemy<MS::STRENGTH>(i,up?-8:-6,false));
        for(int i=0;i<bc.monsters.monsterCount;++i)if(bc.monsters.arr[i].isTargetable() && !bc.monsters.arr[i].artifact)
            bc.addToBot(Actions::BuffEnemy<MS::SHACKLED>(i,up?8:6));
        break;
    case CardId::ENVENOM:bc.addToBot(Actions::BuffPlayer<PS::ENVENOM>(1));break;
    case CardId::A_THOUSAND_CUTS:bc.addToBot(Actions::BuffPlayer<PS::THOUSAND_CUTS>(up?2:1));break;
    case CardId::CALTROPS:bc.addToBot(Actions::BuffPlayer<PS::THORNS>(up?5:3));break;
    case CardId::WRAITH_FORM:
        bc.addToBot(Actions::BuffPlayer<PS::INTANGIBLE>(up?3:2));
        bc.addToBot(Actions::DebuffPlayer<PS::WRAITH_FORM>(-1,false));break;
    case CardId::BULLET_TIME:
        bc.addToBot(Actions::DebuffPlayer<PS::NO_DRAW>(1,false));
        bc.addToBot({[](BattleContext &b){for(int i=0;i<b.cards.cardsInHand;++i)if(b.cards.hand[i].cost>=0)b.cards.hand[i].costForTurn=0;}});break;
    case CardId::SKEWER: {
        int n=bc.curCardQueueItem.energyOnUse+(bc.player.hasRelic<R::CHEMICAL_X>()?2:0);
        for(int i=0;i<n;++i)hit(up?10:7);
        if(!bc.curCardQueueItem.ignoreEnergyTotal && !bc.curCardQueueItem.freeToPlay && !c.freeToPlayOnce)bc.player.energy=0;
        break;
    }
    case CardId::DOPPELGANGER: {
        int n=bc.curCardQueueItem.energyOnUse+(up?1:0)+(bc.player.hasRelic<R::CHEMICAL_X>()?2:0);
        if(n>0){bc.addToBot(Actions::BuffPlayer<PS::ENERGIZED>(n));bc.addToBot(Actions::BuffPlayer<PS::DRAW_CARD_NEXT_TURN>(n));}
        if(!bc.curCardQueueItem.ignoreEnergyTotal && !bc.curCardQueueItem.freeToPlay && !c.freeToPlayOnce)bc.player.energy=0;
        break;
    }
    case CardId::FINISHER:for(int i=0;i<bc.player.attacksPlayedThisTurn;++i)hit(up?8:6);break;
    case CardId::FLECHETTES:for(int i=0;i<bc.cards.cardsInHand;++i)if(bc.cards.hand[i].getType()==CardType::SKILL)hit(up?6:4);break;
    case CardId::GRAND_FINALE:allHit(up?60:50);break;
    case CardId::MASTERFUL_STAB:hit(up?16:12);break;
    case CardId::EVISCERATE:for(int i=0;i<3;++i)hit(up?9:7);break;
    case CardId::ENDLESS_AGONY:hit(up?6:4);break;
    case CardId::QUICK_SLASH:hit(up?12:8);bc.addToBot(Actions::DrawCards(1));break;
    case CardId::SUCKER_PUNCH:hit(up?9:7);bc.addToBot(Actions::DebuffEnemy<MS::WEAK>(t,up?2:1,false));break;
    case CardId::EXPERTISE:bc.addToBot({[up](BattleContext &b){b.addToTop(Actions::DrawCards((up?7:6)-b.cards.cardsInHand));}});break;
    case CardId::BOWLING_BASH: {
        int n=bc.monsters.getTargetableCount();for(int i=0;i<n;++i)hit(up?10:7);break;
    }
    case CardId::CARVE_REALITY:hit(up?10:6);bc.addToBot(Actions::MakeTempCardInHand(CardId::SMITE));break;
    case CardId::REACH_HEAVEN:hit(up?15:10);bc.addToBot(Actions::MakeTempCardInDrawPile(CardInstance(CardId::THROUGH_VIOLENCE),1,true));break;
    case CardId::DECEIVE_REALITY:block(up?7:4);bc.addToBot(Actions::MakeTempCardInHand(CardId::SAFETY));break;
    case CardId::EVALUATE:block(up?10:6);bc.addToBot(Actions::MakeTempCardInDrawPile(CardInstance(CardId::INSIGHT),1,true));break;
    case CardId::PROTECT:block(up?16:12);break;
    case CardId::SANCTITY:
        block(up?9:6);if(bc.lastCardType==CardType::SKILL)bc.addToBot(Actions::DrawCards(2));break;
    case CardId::SASH_WHIP:
        hit(up?10:8);if(bc.lastCardType==CardType::ATTACK)bc.addToBot(Actions::DebuffEnemy<MS::WEAK>(t,up?2:1,false));break;
    case CardId::FOLLOW_UP:hit(up?11:7);if(bc.lastCardType==CardType::ATTACK)bc.addToBot(Actions::GainEnergy(1));break;
    case CardId::SWIVEL:block(up?11:8);bc.addToBot(Actions::BuffPlayer<PS::FREE_ATTACK_POWER>(1));break;
    case CardId::INDIGNATION:
        if(bc.player.stance==Stance::WRATH)bc.addToBot(Actions::DebuffAllEnemy<MS::VULNERABLE>(up?5:3,false));
        else bc.addToBot(Actions::ChangeStance(Stance::WRATH));break;
    case CardId::INNER_PEACE:
        if(bc.player.stance==Stance::CALM)bc.addToBot(Actions::DrawCards(up?4:3));else bc.addToBot(Actions::ChangeStance(Stance::CALM));break;
    case CardId::FEAR_NO_EVIL:
        hit(up?11:8);if(bc.monsters.arr[t].getMoveBaseDamage(bc).attackCount>0)bc.addToBot(Actions::ChangeStance(Stance::CALM));break;
    case CardId::TANTRUM:for(int i=0;i<(up?4:3);++i)hit(3);bc.addToBot(Actions::ChangeStance(Stance::WRATH));break;
    case CardId::MENTAL_FORTRESS:bc.addToBot(Actions::BuffPlayer<PS::MENTAL_FORTRESS>(up?6:4));break;
    case CardId::RUSHDOWN:bc.addToBot(Actions::BuffPlayer<PS::RUSHDOWN>(2));break;
    case CardId::DEVOTION:bc.addToBot(Actions::BuffPlayer<PS::DEVOTION>(up?3:2));break;
    case CardId::MASTER_REALITY:bc.addToBot(Actions::BuffPlayer<PS::MASTER_REALITY>(bc.headlessRngKnown?-1:1));break;
    case CardId::ESTABLISHMENT:bc.addToBot(Actions::BuffPlayer<PS::ESTABLISHMENT>(bc.headlessRngKnown?1:(up?2:1)));break;
    case CardId::STUDY:bc.addToBot(Actions::BuffPlayer<PS::STUDY>(1));break;
    case CardId::WREATH_OF_FLAME:bc.addToBot(Actions::BuffPlayer<PS::VIGOR>(up?8:5));break;
    case CardId::SCRAWL:bc.addToBot({[](BattleContext &b){b.addToTop(Actions::DrawCards(10-b.cards.cardsInHand));}});break;
    case CardId::ACROBATICS:bc.addToBot(Actions::DrawCards(up?4:3));select(CardSelectTask::DISCARD,1);break;
    case CardId::PREPARED:bc.addToBot(Actions::DrawCards(up?2:1));select(CardSelectTask::DISCARD,up?2:1);break;
    case CardId::CONCENTRATE:select(CardSelectTask::DISCARD,up?2:3);bc.addToBot(Actions::GainEnergy(2));break;
    case CardId::SURVIVOR:block(up?11:8);select(CardSelectTask::DISCARD,1);break;
    case CardId::DAGGER_THROW:hit(up?12:9);bc.addToBot(Actions::DrawCards(1));select(CardSelectTask::DISCARD,1);break;
    case CardId::CALCULATED_GAMBLE:
        bc.addToBot({[](BattleContext &b){int n=b.cards.cardsInHand;
            for(int i=n-1;i>=0;--i)Actions::DiscardCardAtIdx(i).actFunc(b);
            b.addToTop(Actions::DrawCards(n));}});break;
    case CardId::ALL_OUT_ATTACK:
        allHit(up?14:10);
        bc.addToBot({[](BattleContext &b){if(b.cards.cardsInHand>0)Actions::DiscardCardAtIdx(b.cards.cardsInHand==1?0:b.cards.getRandomCardIdxInHand(b.cardRandomRng)).actFunc(b);}});break;
    case CardId::UNLOAD:
        hit(up?18:14);bc.addToBot({[](BattleContext &b){for(int i=b.cards.cardsInHand-1;i>=0;--i)
            if(b.cards.hand[i].getType()!=CardType::ATTACK)Actions::DiscardCardAtIdx(i).actFunc(b);}});break;
    case CardId::STORM_OF_STEEL:
        bc.addToBot({[up](BattleContext &b){int n=b.cards.cardsInHand;
            for(int i=n-1;i>=0;--i)Actions::DiscardCardAtIdx(i).actFunc(b);
            b.addToTop(Actions::MakeTempCardInHand(CardId::SHIV,up,n));}});break;
    case CardId::REFLEX:bc.addToBot(Actions::DrawCards(up?3:2));break;
    case CardId::TACTICIAN:bc.addToBot(Actions::GainEnergy(up?2:1));break;
    case CardId::SETUP:select(CardSelectTask::SETUP,1);break;
    case CardId::CUT_THROUGH_FATE:hit(up?9:7);select(CardSelectTask::SCRY,up?3:2);bc.addToBot(Actions::DrawCards(1));break;
    case CardId::JUST_LUCKY:select(CardSelectTask::SCRY,up?2:1);block(up?3:2);hit(up?4:3);break;
    case CardId::THIRD_EYE:block(up?9:7);select(CardSelectTask::SCRY,up?5:3);break;
    case CardId::FORESIGHT:bc.addToBot(Actions::BuffPlayer<PS::FORESIGHT>(up?4:3));break;
    case CardId::NIRVANA:bc.addToBot(Actions::BuffPlayer<PS::NIRVANA>(up?4:3));break;
    case CardId::WEAVE:hit(up?6:4);break;
    case CardId::MEDITATE:
        select(CardSelectTask::MEDITATE,up?2:1);bc.addToBot(Actions::ChangeStance(Stance::CALM));
        bc.addToBot({[](BattleContext &b){if(!b.endTurnQueued)b.endTurn();}});break;
    case CardId::WELL_LAID_PLANS:bc.addToBot(Actions::BuffPlayer<PS::WELL_LAID_PLANS>(up?2:1));break;
    case CardId::PERSEVERANCE:block((up?7:5)+c.specialData);break;
    case CardId::WINDMILL_STRIKE:hit((up?10:7)+c.specialData);break;
    case CardId::SANDS_OF_TIME:hit(up?26:20);break;
    case CardId::HALT: {
        int amount=bc.calculateCardBlock(up?4:3);
        if(bc.player.stance==Stance::WRATH)amount+=bc.calculateCardBlock(up?14:9);
        bc.addToBot(Actions::GainBlock(amount));break;
    }
    case CardId::SIGNATURE_MOVE:hit(up?40:30);break;
    case CardId::CORPSE_EXPLOSION:
        bc.addToBot(Actions::DebuffEnemy<MS::POISON>(t,up?9:6,false));
        bc.addToBot(Actions::DebuffEnemy<MS::CORPSE_EXPLOSION>(t,1,false));break;
    case CardId::CRIPPLING_CLOUD:
        for(int i=0;i<bc.monsters.monsterCount;++i)if(bc.monsters.arr[i].isTargetable()) {
            bc.addToBot(Actions::DebuffEnemy<MS::POISON>(i,up?7:4,false));bc.addToBot(Actions::DebuffEnemy<MS::WEAK>(i,2,false));
        }break;
    case CardId::ALCHEMIZE: {
        auto potion=returnRandomPotion(bc.potionRng,bc.player.cc,true);
        bc.addToBot({[potion](BattleContext &b){b.obtainPotion(potion);}});break;
    }
    case CardId::MALAISE: {
        int n=bc.curCardQueueItem.energyOnUse+(up?1:0)+(bc.player.hasRelic<R::CHEMICAL_X>()?2:0);
        if(n>0){bc.addToBot(Actions::DebuffEnemy<MS::STRENGTH>(t,-n,false));bc.addToBot(Actions::DebuffEnemy<MS::WEAK>(t,n,false));}
        if(!bc.curCardQueueItem.ignoreEnergyTotal && !bc.curCardQueueItem.freeToPlay && !c.freeToPlayOnce)bc.player.energy=0;
        break;
    }
    case CardId::WAVE_OF_THE_HAND:bc.addToBot(Actions::BuffPlayer<PS::WAVE_OF_THE_HAND>(up?2:1));break;
    case CardId::DEUS_EX_MACHINA:break; // Unplayable; its effect is the draw trigger, including autoplay's empty use().
    case CardId::BOUNCING_FLASK:bc.addToBot(bouncingFlask(bc.monsters.getRandomMonsterIdx(bc.cardRandomRng,true),up?4:3));break;
    case CardId::CHOKE:hit(12);bc.addToBot(Actions::DebuffEnemy<MS::CHOKED>(t,up?5:3,false));break;
    case CardId::DISTRACTION: {
        CardInstance generated(getTrulyRandomCardInCombat(bc.cardRandomRng,bc.player.cc,CardType::SKILL));
        generated.setCostForTurn(0); // Unplayable and X-cost cards retain their sentinels.
        bc.addToBot(Actions::MakeTempCardInHand(generated));break;
    }
    case CardId::GLASS_KNIFE:
        hit(std::max(0,(up?12:8)+c.specialData));hit(std::max(0,(up?12:8)+c.specialData));
        bc.addToBot({[id=c.uniqueId](BattleContext &b){
            if(b.curCardQueueItem.card.uniqueId==id)b.curCardQueueItem.card.specialData-=2;
            // Double Tap/Duplication copies share the UUID and wait in the
            // card queue, so ModifyDamageAction also reaches the next replay.
            if(!b.cardQueue.isEmpty() && b.cardQueue.front().card.uniqueId==id)
                b.cardQueue.front().card.specialData-=2;
            for(int i=0;i<b.cards.cardsInHand;++i)if(b.cards.hand[i].uniqueId==id)b.cards.hand[i].specialData-=2;
            for(auto *pile:{&b.cards.drawPile,&b.cards.discardPile,&b.cards.exhaustPile})
                for(auto &x:*pile)if(x.uniqueId==id)x.specialData-=2;
        }});break;
    case CardId::TOOLS_OF_THE_TRADE:bc.addToBot(Actions::BuffPlayer<PS::TOOLS_OF_THE_TRADE>(1));break;
    case CardId::BLASPHEMY:bc.addToBot(Actions::ChangeStance(Stance::DIVINITY));bc.addToBot(Actions::BuffPlayer<PS::BLASPHEMER>());break;
    case CardId::BRILLIANCE:hit((up?16:12)+bc.mantraGained);break;
    case CardId::COLLECT: {
        int n=bc.curCardQueueItem.energyOnUse+(up?1:0)+(bc.player.hasRelic<R::CHEMICAL_X>()?2:0);
        if(n>0)bc.addToBot(Actions::BuffPlayer<PS::COLLECT>(n));
        if(!bc.curCardQueueItem.ignoreEnergyTotal && !bc.curCardQueueItem.freeToPlay && !c.freeToPlayOnce)bc.player.energy=0;
        break;
    }
    case CardId::CONCLUDE:allHit(up?16:12);bc.addToBot({[](BattleContext &b){
        if(!b.endTurnQueued) {
            if(b.headlessRngKnown)b.callEndTurnEarlySequence();
            else b.endTurn();
        }
    }});break;
    case CardId::CRUSH_JOINTS:hit(up?10:8);if(bc.lastCardType==CardType::SKILL)bc.addToBot(Actions::DebuffEnemy<MS::VULNERABLE>(t,up?2:1,false));break;
    case CardId::FASTING:
        bc.addToBot(Actions::BuffPlayer<PS::STRENGTH>(up?4:3));bc.addToBot(Actions::BuffPlayer<PS::DEXTERITY>(up?4:3));
        bc.addToBot(Actions::DebuffPlayer<PS::FASTING>(1,false));break;
    case CardId::JUDGMENT:
        bc.addToBot({[t,up](BattleContext &b){auto &m=b.monsters.arr[t];if(m.isTargetable() && m.curHp<=(up?40:30)){m.curHp=0;m.die(b);b.checkCombat();}}});break;
    case CardId::PRESSURE_POINTS:
        bc.addToBot(Actions::DebuffEnemy<MS::MARK>(t,up?11:8,false));
        bc.addToBot({[](BattleContext &b){for(int i=0;i<b.monsters.monsterCount;++i){auto &m=b.monsters.arr[i];
            if(m.isTargetable() && m.mark>0)m.damageUnblockedHelper(b,m.hasStatus<MS::INTANGIBLE>()?1:m.mark);
        }b.checkCombat();}});break;
    case CardId::RAGNAROK:for(int i=0;i<(up?6:5);++i)bc.addToBot(Actions::RandomTargetAttack((up?6:5)+bc.player.getStatus<PS::VIGOR>(),c));break;
    case CardId::SIMMERING_FURY:bc.addToBot(Actions::BuffPlayer<PS::WRATH_NEXT_TURN>());bc.addToBot(Actions::BuffPlayer<PS::DRAW_CARD_NEXT_TURN>(up?3:2));break;
    case CardId::SPIRIT_SHIELD:block((up?4:3)*std::max(0,bc.cards.cardsInHand-1));break;
    case CardId::TALK_TO_THE_HAND:hit(up?7:5);bc.addToBot(Actions::DebuffEnemy<MS::BLOCK_RETURN>(t,up?3:2,false));break;
    case CardId::WALLOP: {
        int damage=bc.calculateCardDamage(c,t,up?12:9);
        bc.addToBot({[t,damage](BattleContext &b){if(!b.monsters.arr[t].isTargetable())return;
            Actions::AttackEnemy(t,damage).actFunc(b);
            if(b.outcome==Outcome::UNDECIDED && b.monsters.arr[t].lastDamageTaken>0)b.addToTop(Actions::GainBlock(b.monsters.arr[t].lastDamageTaken));
        }});break;
    }
    case CardId::VAULT:
        bc.addToBot({[](BattleContext &b){b.skipMonsterTurn=true;}});
        bc.addToBot({[](BattleContext &b){
            if (b.endTurnQueued) return;
            if (b.headlessRngKnown && b.player.hasRelic<R::UNCEASING_TOP>()
                    && !b.cards.cardsInHand && b.cardQueue.isEmpty())
                b.addToBot(Actions::DrawCards(1));
            b.endTurn();
        }});break;
    case CardId::ALPHA:bc.addToBot(Actions::MakeTempCardInDrawPile(CardInstance(CardId::BETA),1,true));break;
    case CardId::BETA:bc.addToBot(Actions::MakeTempCardInDrawPile(CardInstance(CardId::OMEGA),1,true));break;
    case CardId::OMEGA:bc.addToBot(Actions::BuffPlayer<PS::OMEGA>(up?60:50));break;
    case CardId::CONJURE_BLADE: {
        int n=bc.curCardQueueItem.energyOnUse+(up?1:0)+(bc.player.hasRelic<R::CHEMICAL_X>()?2:0);
        CardInstance generated(CardId::EXPUNGER);generated.specialData=n;
        bc.addToBot(Actions::MakeTempCardInDrawPile(generated,1,true));
        if(!bc.curCardQueueItem.ignoreEnergyTotal && !bc.curCardQueueItem.freeToPlay && !c.freeToPlayOnce)bc.player.energy=0;
        break;
    }
    case CardId::EXPUNGER:for(int i=0;i<c.specialData;++i)hit(up?15:9);break;
    case CardId::ESCAPE_PLAN: {
        int beforeDraw=bc.cardsDrawn,amount=bc.calculateCardBlock(up?5:3);
        bc.addToBot(Actions::DrawCards(1));
        bc.addToBot({[beforeDraw,amount](BattleContext &b){if(b.cardsDrawn>beforeDraw && b.lastDrawnCardType==CardType::SKILL)b.addToTop(Actions::GainBlock(amount));}});
        break;
    }
    case CardId::NIGHTMARE:select(bc.headlessRngKnown?CardSelectTask::NIGHTMARE_HAND:CardSelectTask::NIGHTMARE,3);break;
    case CardId::DEVA_FORM:
        bc.addToBot({[](BattleContext &b){++b.player.devaFormEnergyPerTurn;b.player.buff<PS::DEVA>(1);}});break;
    case CardId::OMNISCIENCE:select(CardSelectTask::OMNISCIENCE,2);break;
    case CardId::WISH:
        bc.addToBot({[up](BattleContext &b){
            CardSelectInfo info;info.cardSelectTask=CardSelectTask::WISH;info.pickCount=1;
            for(auto id:{CardId::BECOME_ALMIGHTY,CardId::FAME_AND_FORTUNE,CardId::LIVE_FOREVER})info.options.emplace_back(id,up);
            b.cardSelectInfo=std::move(info);b.inputState=InputState::CARD_SELECT;
        }});break;
    case CardId::LESSON_LEARNED: {
        if(!bc.masterDeckKnown)throw std::runtime_error("missing_master_deck:LessonLearned");
        int damage=bc.calculateCardDamage(c,t,up?13:10);
        bc.addToBot({[t,damage](BattleContext &b){
            auto &m=b.monsters.arr[t];if(!m.isTargetable())return;
            Actions::AttackEnemy(t,damage).actFunc(b);
            if(m.curHp>0 || m.halfDead || m.hasStatus<MS::MINION>())return;
            std::vector<int> options;
            for(int i=0;i<int(b.masterDeck.size());++i)if(b.masterDeck[i].canUpgrade())options.push_back(i);
            if(!options.empty())b.masterDeck[options[b.miscRng.random(int(options.size())-1)]].upgrade();
        }});break;
    }
    case CardId::FOREIGN_INFLUENCE: {
        if(!bc.cardRngKnown)throw std::runtime_error("missing_card_rng:ForeignInfluence");
        CardSelectInfo info;info.cardSelectTask=CardSelectTask::FOREIGN_INFLUENCE;info.pickCount=1;info.canPickZero=true;info.data0=up;
        for(int tries=0;info.options.size()<3;++tries) {
            checkExecutionBudget();if(tries>=10000)throw std::runtime_error("foreign_influence_choice_exhausted");
            int roll=bc.cardRandomRng.random(99),rarity=roll<55?0:roll<85?1:2;
            auto pool=runtimeCombatPools.libraryAttacks[rarity];
            if(pool.empty())throw std::runtime_error("runtime_card_pool_missing:library_attacks");
            std::sort(pool.begin(),pool.end(),[](CardId a,CardId b){return std::strcmp(getCardStringId(a),getCardStringId(b))<0;});
            // CardLibrary shuffles with cardRandomRng, then getRandomCard
            // re-sorts by card ID and samples with the independent cardRng.
            bc.cardRandomRng.randomLong();
            CardId id=pool[bc.cardRng.random(int(pool.size())-1)];
            if(std::none_of(info.options.begin(),info.options.end(),[id](const auto &x){return x.id==id;}))info.options.emplace_back(id);
        }
        bc.addToBot({[info](BattleContext &b){b.cardSelectInfo=info;b.inputState=InputState::CARD_SELECT;}});break;
    }
    default:return false;
    }
    return true;
}
