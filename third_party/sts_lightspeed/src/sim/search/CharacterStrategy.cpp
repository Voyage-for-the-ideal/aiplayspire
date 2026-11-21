#include "sim/search/CharacterStrategy.h"
#include "sim/search/ExpertKnowledge.h"
#include <algorithm>
#include <cstdint>

using namespace sts;
using namespace sts::search;

namespace {
template<class F> void available(const BattleContext &b, F f) {
    for (int i=0;i<b.cards.cardsInHand;++i) f(b.cards.hand[i]);
    for (const auto &c:b.cards.drawPile) f(c);
    for (const auto &c:b.cards.discardPile) f(c);
}
bool has(const BattleContext &b, CardId id) {
    bool found=false; available(b,[&](const auto &c){found |= c.id==id;}); return found;
}
bool handHas(const BattleContext &b, CardId id) {
    for(int i=0;i<b.cards.cardsInHand;++i) if(b.cards.hand[i].id==id)return true;
    return false;
}
int power(const BattleContext &b, PS id) { return b.player.getStatusRuntime(id); }
int effectiveCost(const BattleContext &b,const CardInstance &c) {
    return c.isFreeToPlay(b)?0:int(c.costForTurn);
}
bool attacking(const BattleContext &b,int target) {
    return target>=0 && target<b.monsters.monsterCount && b.monsters.arr[target].isTargetable()
        && b.monsters.arr[target].getMoveBaseDamage(b).attackCount>0;
}
bool usableExit(const BattleContext &b,const CardInstance &entry) {
    int energy=b.player.energy-std::max(0,effectiveCost(b,entry));
    for(int i=0;i<b.cards.cardsInHand;++i) {
        const auto &c=b.cards.hand[i];
        if(c.uniqueId==entry.uniqueId || !isStanceExit(c.id))continue;
        int cost=effectiveCost(b,c);
        if(cost<0 || cost>energy)continue;
        if(c.id==CardId::FEAR_NO_EVIL) {
            if(b.player.hasStatus<PS::ENTANGLED>())continue;
            bool valid=false;
            for(int t=0;t<b.monsters.monsterCount;++t) valid|=attacking(b,t);
            if(!valid)continue;
        }
        return true;
    }
    return false;
}
int blockValue(CardId id,bool up) {
    switch(id) {
        case CardId::DEFEND_RED:case CardId::DEFEND_BLUE:case CardId::DEFEND_GREEN:case CardId::DEFEND_PURPLE:return up?8:5;
        case CardId::DEFLECT:return up?7:4;
        case CardId::BACKFLIP:case CardId::BLUR:return up?8:5;
        case CardId::CLOAK_AND_DAGGER:return 6;
        case CardId::DODGE_AND_ROLL:return up?6:4;
        case CardId::DASH:return up?13:10;
        case CardId::LEG_SWEEP:return up?14:11;
        case CardId::SURVIVOR:return up?11:8;
        case CardId::VIGILANCE:return up?12:8;
        case CardId::SAFETY:return up?16:12;
        case CardId::EMPTY_BODY:return up?10:7;
        case CardId::PROTECT:return up?16:12;
        case CardId::PERSEVERANCE:return up?7:5;
        case CardId::THIRD_EYE:return up?9:7;
        case CardId::EVALUATE:return up?10:6;
        case CardId::SANCTITY:return up?9:6;
        case CardId::SWIVEL:return up?11:8;
        case CardId::PROSTRATE:return 4;
        case CardId::HALT:return up?4:3;
        default:return 0;
    }
}
int cardValue(const BattleContext &b,const CardInstance &c) {
    if(c.costForTurn==-2 || c.getType()==CardType::CURSE || c.getType()==CardType::STATUS)return -100;
    if(c.id==CardId::REFLEX || c.id==CardId::TACTICIAN)return 5;
    int value=10+std::max(0,getBaseDamage(c.id,c.upgraded))
        +std::min(std::max(0,blockValue(c.id,c.upgraded)),projectedIncomingLoss(b));
    value+=std::max(0,characterPlayPriority(b,c,-1));
    if(isStanceExit(c.id) && b.player.stance==Stance::WRATH)value+=100;
    if(c.id==CardId::CATALYST && has(b,CardId::DEADLY_POISON))value+=60;
    if(c.retain || c.hasSelfRetain())value+=5;
    return value;
}
}

void MechanismScore::add(const char *name,int value) { if(value)rules[name]+=value; }
int MechanismScore::total() const {
    std::int64_t sum=0; for(const auto &[name,value]:rules)sum+=value;
    return int(std::clamp(sum,std::int64_t(-LIMIT),std::int64_t(LIMIT)));
}

int sts::search::projectedIncomingLoss(const BattleContext &b) {
    int block=std::max(0,b.player.block),buffer=std::max(0,power(b,PS::BUFFER)),loss=0;
    for(int i=0;i<b.monsters.monsterCount;++i) {
        const auto &m=b.monsters.arr[i];if(!m.isTargetable())continue;
        const auto info=m.getMoveBaseDamage(b);
        const int hit=std::max(0,m.calculateDamageToPlayer(b,info.damage));
        for(int j=0;j<info.attackCount;++j) {
            int absorbed=std::min(block,hit);block-=absorbed;
            int unblocked=hit-absorbed;
            if(unblocked>0 && buffer>0)--buffer;else loss+=unblocked;
        }
    }
    return loss;
}

bool sts::search::isStanceExit(CardId id) {
    switch(id) {
        case CardId::EMPTY_BODY:case CardId::EMPTY_FIST:case CardId::EMPTY_MIND:
        case CardId::VIGILANCE:case CardId::FEAR_NO_EVIL:case CardId::INNER_PEACE:
        case CardId::TRANQUILITY:case CardId::MEDITATE:return true;
        default:return false;
    }
}

MechanismScore sts::search::characterMechanisms(const BattleContext &b) {
    MechanismScore score;
    if(b.player.curHp<=0 || b.outcome!=Outcome::UNDECIDED)return score;
    double poison=0;
    for(int i=0;i<b.monsters.monsterCount;++i) {
        const auto &m=b.monsters.arr[i];if(!m.isTargetable())continue;
        int p=std::max(0,int(m.poison));
        poison+=std::min(double(m.curHp),p+std::max(0,p-1)*0.5);
    }
    score.add("poison-two-turns",int(poison*10));
    int shivs=has(b,CardId::BLADE_DANCE)||has(b,CardId::CLOAK_AND_DAGGER)||has(b,CardId::SHIV)?4:0;
    score.add("shiv-engine",shivs*(power(b,PS::ACCURACY)*10+power(b,PS::AFTER_IMAGE)*12));
    bool discard=has(b,CardId::ACROBATICS)||has(b,CardId::PREPARED)||has(b,CardId::CALCULATED_GAMBLE);
    score.add("discard-engine",discard*((has(b,CardId::REFLEX)?25:0)+(has(b,CardId::TACTICIAN)?35:0)));
    score.add("intangible-future",std::max(0,power(b,PS::INTANGIBLE)-1)*30);
    score.add("wraith-dexterity-cost",std::min(0,power(b,PS::WRAITH_FORM))*20);
    bool exit=false,leaveCalm=false;
    available(b,[&](const auto &c){
        exit|=isStanceExit(c.id);
        leaveCalm|=c.id==CardId::EMPTY_BODY || c.id==CardId::EMPTY_FIST || c.id==CardId::EMPTY_MIND
            || c.id==CardId::ERUPTION || c.id==CardId::TANTRUM || c.id==CardId::CRESCENDO
            || c.id==CardId::INDIGNATION || c.id==CardId::BLASPHEMY;
    });
    score.add("calm-exit",b.player.stance==Stance::CALM && leaveCalm?40:0);
    int mantra=std::max(0,power(b,PS::MANTRA));
    score.add("mantra-progress",mantra*8+(mantra>=7?25:0));
    int retained=0;
    for(int i=0;i<b.cards.cardsInHand;++i) {
        const auto &c=b.cards.hand[i];
        if((c.retain||c.hasSelfRetain()) && c.costForTurn!=-2)
            retained+=std::min(20,std::max({0,getBaseDamage(c.id,c.upgraded),blockValue(c.id,c.upgraded)}));
    }
    score.add("retained-options",retained*2);
    // Power IDs are the game's internal names, not their displayed names.
    if(exit && (has(b,CardId::ERUPTION)||has(b,CardId::TANTRUM)||has(b,CardId::CRESCENDO)))
        score.add("stance-engine",power(b,PS::RUSHDOWN)*25+power(b,PS::MENTAL_FORTRESS)*15);
    return score;
}

int sts::search::characterPlayPriority(const BattleContext &b,const CardInstance &c,int target) {
    const auto *m=target>=0 && target<b.monsters.monsterCount?&b.monsters.arr[target]:nullptr;
    int p=0,cost=effectiveCost(b,c);
    switch(c.id) {
        case CardId::AFTER_IMAGE:case CardId::ACCURACY:case CardId::FOOTWORK:p=80;break;
        case CardId::CATALYST:p=m && m->poison>0?55:-100;break;
        case CardId::DEADLY_POISON:case CardId::BOUNCING_FLASK:case CardId::CRIPPLING_CLOUD:
        case CardId::POISONED_STAB:p=handHas(b,CardId::CATALYST)?70:15;break;
        case CardId::SNEAKY_STRIKE:p=cost==0 || b.player.cardsDiscardedThisTurn>0?95:-10;break;
        case CardId::EVISCERATE:p=cost==0?95:-10;break;
        case CardId::HEEL_HOOK:p=m && m->weak>0?100:0;break;
        case CardId::ACROBATICS:case CardId::PREPARED:case CardId::CALCULATED_GAMBLE:
            p=handHas(b,CardId::REFLEX)||handHas(b,CardId::TACTICIAN)||handHas(b,CardId::SNEAKY_STRIKE)?70:10;break;
        case CardId::WRAITH_FORM:p=projectedIncomingLoss(b)>0 && !power(b,PS::INTANGIBLE)?85:-70;break;
        case CardId::CONCENTRATE:p=b.cards.cardsInHand>(c.upgraded?3:4) && b.player.energy<2?40:-80;break;
        case CardId::TALK_TO_THE_HAND:case CardId::RUSHDOWN:case CardId::MENTAL_FORTRESS:p=90;break;
        case CardId::ERUPTION:case CardId::TANTRUM:case CardId::CRESCENDO:case CardId::INDIGNATION:
            if(b.player.stance==Stance::WRATH)p=c.id==CardId::INDIGNATION?20:-60;
            else p=usableExit(b,c)||projectedIncomingLoss(b)==0?65:-90;
            break;
        case CardId::BLASPHEMY:p=-100;break;
        case CardId::WORSHIP:case CardId::PRAY:case CardId::PROSTRATE:
            p=power(b,PS::MANTRA)+(c.id==CardId::WORSHIP?5:c.id==CardId::PRAY?(c.upgraded?4:3):(c.upgraded?3:2))>=10?75:10;break;
        default:
            if(isStanceExit(c.id)) {
                if(b.player.stance==Stance::WRATH)p=projectedIncomingLoss(b)>=b.player.curHp?85:-15;
                else p=b.player.stance==Stance::CALM && (c.id==CardId::EMPTY_BODY||c.id==CardId::EMPTY_FIST||c.id==CardId::EMPTY_MIND)?30:-30;
            }
    }
    if(c.getType()==CardType::POWER && p==0)p=20;
    if(cost==0 && getBaseDamage(c.id,c.upgraded)>0)p+=5;
    return p;
}

int sts::search::characterSelectionPriority(const BattleContext &b,const CardInstance &c,SelectionPurpose purpose) {
    int value=cardValue(b,c);
    switch(purpose) {
        case SelectionPurpose::DISCARD:return c.id==CardId::TACTICIAN||c.id==CardId::REFLEX?200:-value;
        case SelectionPurpose::EXHAUST:return c.id==CardId::SENTINEL?200:-value+(c.isEthereal()?-10:0);
        case SelectionPurpose::SCRY:
            if(c.costForTurn==-2 || c.getType()==CardType::CURSE || c.getType()==CardType::STATUS)return 200;
            return effectiveCost(b,c)>b.player.energy?30:-std::max(1,value);
        case SelectionPurpose::SEEK:return value+(effectiveCost(b,c)>=0 && effectiveCost(b,c)<=b.player.energy?40:-60);
        case SelectionPurpose::RETAIN:return c.isEthereal()||c.retain||c.hasSelfRetain()?-200:value;
        default:return 0;
    }
}

int sts::search::characterFallbackRank(const BattleContext &bc,CardId id) {
    // Legacy SilentPlayOrder is deliberately kept as a checked-in table; other
    // colors retain the vendor expert order. Dynamic priorities dominate it.
    static constexpr CardId silentOrder[]={
        CardId::PANACHE,CardId::GRAND_FINALE,CardId::MASTER_OF_STRATEGY,CardId::SADISTIC_NATURE,
        CardId::APOTHEOSIS,CardId::CREATIVE_AI,CardId::LESSON_LEARNED,CardId::WAVE_OF_THE_HAND,
        CardId::WOUND,CardId::BURN,CardId::DAZED,CardId::PAIN,CardId::DOUBT,CardId::NORMALITY,
        CardId::SHAME,CardId::ASCENDERS_BANE,CardId::CURSE_OF_THE_BELL,CardId::CORPSE_EXPLOSION,
        CardId::BACKFLIP,CardId::DARK_EMBRACE,CardId::ACROBATICS,CardId::BULLET_TIME,
        CardId::TRANSMUTATION,CardId::CLAW,CardId::TALK_TO_THE_HAND,CardId::FLECHETTES,
        CardId::BURST,CardId::APPARITION,CardId::FOOTWORK,CardId::ADRENALINE,CardId::MALAISE,
        CardId::TOOLS_OF_THE_TRADE,CardId::CHOKE,CardId::AFTER_IMAGE,CardId::CALTROPS,
        CardId::INFINITE_BLADES,CardId::LEG_SWEEP,CardId::NOXIOUS_FUMES,CardId::CRIPPLING_CLOUD,
        CardId::ENVENOM,CardId::A_THOUSAND_CUTS,CardId::NIGHTMARE,CardId::ALCHEMIZE,
        CardId::PHANTASMAL_KILLER,CardId::OUTMANEUVER,CardId::ACCURACY,CardId::CALCULATED_GAMBLE,
        CardId::BOUNCING_FLASK,CardId::DEADLY_POISON,CardId::POISONED_STAB,CardId::CATALYST,
        CardId::TERROR,CardId::WELL_LAID_PLANS,CardId::DASH,CardId::SKEWER,CardId::RIDDLE_WITH_HOLES,
        CardId::NEUTRALIZE,CardId::QUICK_SLASH,CardId::SUCKER_PUNCH,CardId::ALL_OUT_ATTACK,
        CardId::BACKSTAB,CardId::HEEL_HOOK,CardId::BANE,CardId::FLYING_KNEE,CardId::DAGGER_THROW,
        CardId::DIE_DIE_DIE,CardId::SLICE,CardId::DAGGER_SPRAY,CardId::GLASS_KNIFE,
        CardId::MASTERFUL_STAB,CardId::EVISCERATE,CardId::PREDATOR,CardId::CLOAK_AND_DAGGER,
        CardId::SNEAKY_STRIKE,CardId::SHIV,CardId::STORM_OF_STEEL,CardId::BLADE_DANCE,
        CardId::STRIKE_GREEN,CardId::REFLEX,CardId::SETUP,CardId::TACTICIAN,CardId::WRAITH_FORM,
        CardId::UNLOAD,CardId::FINISHER,CardId::DOPPELGANGER,CardId::SURVIVOR,CardId::PREPARED,
        CardId::PIERCING_WAIL,CardId::DISTRACTION,CardId::ENDLESS_AGONY,CardId::DODGE_AND_ROLL,
        CardId::BLUR,CardId::ESCAPE_PLAN,CardId::DEFLECT,CardId::DEFEND_GREEN,CardId::EXPERTISE,CardId::CONCENTRATE};
    const auto color=getCardColor(id);
    const bool generic=color!=CardColor::GREEN && color!=CardColor::PURPLE && color!=CardColor::RED && color!=CardColor::BLUE;
    if(color==CardColor::GREEN || (generic && bc.player.cc==CharacterClass::SILENT)) {
        auto it=std::find(std::begin(silentOrder),std::end(silentOrder),id);
        return it==std::end(silentOrder)?500:int(it-std::begin(silentOrder));
    }
    if(color==CardColor::PURPLE || (generic && bc.player.cc==CharacterClass::WATCHER)) {
        if(id==CardId::TALK_TO_THE_HAND||id==CardId::RUSHDOWN||id==CardId::MENTAL_FORTRESS)return 0;
        if(id==CardId::ERUPTION||id==CardId::TANTRUM||id==CardId::CRESCENDO)return 20;
        return isStanceExit(id)?80:50;
    }
    return Expert::getPlayOrdering(id);
}

SelectionPurpose sts::search::selectionPurpose(CardSelectTask task) {
    switch(task) {
        case CardSelectTask::GAMBLE:case CardSelectTask::GAMBLER_DISCARD:case CardSelectTask::DISCARD:return SelectionPurpose::DISCARD;
        case CardSelectTask::SCRY:return SelectionPurpose::SCRY;
        case CardSelectTask::RETAIN:return SelectionPurpose::RETAIN;
        case CardSelectTask::EXHAUST_ONE:case CardSelectTask::EXHAUST_MANY:case CardSelectTask::ELIXIR_EXHAUST:
        case CardSelectTask::TRUE_GRIT_HAND:case CardSelectTask::BURNING_PACT_HAND:case CardSelectTask::RECYCLE:return SelectionPurpose::EXHAUST;
        case CardSelectTask::MEDITATE:case CardSelectTask::OMNISCIENCE:case CardSelectTask::NIGHTMARE:
        case CardSelectTask::FOREIGN_INFLUENCE:case CardSelectTask::HEADBUTT:case CardSelectTask::HEADBUTT_GRID:case CardSelectTask::HOLOGRAM:
        case CardSelectTask::SEEK:case CardSelectTask::SEEK_GRID:case CardSelectTask::HOLOGRAM_GRID:
        case CardSelectTask::LIQUID_MEMORIES_POTION:case CardSelectTask::LIQUID_MEMORIES_GRID:return SelectionPurpose::SEEK;
        default:return SelectionPurpose::NONE;
    }
}
