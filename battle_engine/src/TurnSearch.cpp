#include "TurnSearch.h"
#include "sim/search/ActionEnumerator.h"
#include "sim/search/ExpertKnowledge.h"
#include "sim/search/CharacterStrategy.h"
#include "sim/search/BattleEvaluator.h"
#include "combat/ExecutionBudget.h"
#include "combat/CharacterSelection.h"
#include <algorithm>
#include <memory>
#include <queue>
#include <unordered_set>

using namespace sts;
using SA=sts::search::Action;
using AT=sts::search::ActionType;
namespace bridge {
namespace {
template<class F> void available(const BattleContext &b,F f) {
    for(int i=0;i<b.cards.cardsInHand;++i) f(b.cards.hand[i]);
    for(const auto &c:b.cards.drawPile) f(c);
    for(const auto &c:b.cards.discardPile) f(c);
}
bool has(const BattleContext &b,CardId id) { bool found=false; available(b,[&](const auto &c){found|=c.id==id;}); return found; }
int power(const BattleContext &b,PS p) {
    if(p==PS::BARRICADE)return b.player.hasStatus<PS::BARRICADE>();
    if(p==PS::CORRUPTION)return b.player.hasStatus<PS::CORRUPTION>();
    if(p==PS::ELECTRO)return b.player.hasStatus<PS::ELECTRO>();
    auto it=b.player.statusMap.find(p);return it==b.player.statusMap.end()?0:it->second;
}
int enemyHp(const BattleContext &b) {
    int hp=0; for(int i=0;i<b.monsters.monsterCount;++i) {
        const auto &m=b.monsters.arr[i]; hp+=std::max(0,m.curHp);
        if(m.hasStatus<MS::BARRICADE>()) hp+=std::max(0,m.block);
        if(m.id==MonsterId::AWAKENED_ONE && m.miscInfo==0) hp+=m.maxHp;
    } return hp;
}
int incoming(const BattleContext &b, int *threat=nullptr) {
    int block=std::max(0,b.player.block),buffer=std::max(0,power(b,PS::BUFFER)),loss=0,other=0;
    for(int i=0;i<b.monsters.monsterCount;++i) {
        const auto &m=b.monsters.arr[i]; if(!m.isTargetable()) continue;
        const auto info=m.getMoveBaseDamage(b);
        const int hit=m.calculateDamageToPlayer(b,info.damage);
        for(int j=0;j<info.attackCount;++j) {
            int absorbed=std::min(block,hit);block-=absorbed;
            int unblocked=hit-absorbed;
            if(unblocked>0 && buffer>0) --buffer; else loss+=unblocked;
        }
        other+=std::max(0,m.strength)*15+std::max(0,m.block)*10;
    }
    if(threat) *threat=loss*40+other;
    return loss;
}
Json cardKey(const CardInstance &c) {
    return Json::array({int(c.id),c.specialData,c.cost,c.costForTurn,c.upgraded,c.freeToPlayOnce,c.retain,c.forcedExhaust});
}
int resource(const BattleContext &b,bool terminal,const BattleContext &root) {
    int score=b.player.maxHp*30+b.player.gold*2;
    // Lesson Learned changes the permanent deck, not the current combat copy.
    // Count only gains since this search root, so existing upgrades get no
    // repeated reward and an equally lethal line prefers the permanent gain.
    if(b.masterDeckKnown && root.masterDeckKnown && b.masterDeck.size()==root.masterDeck.size()) {
        for(size_t i=0;i<b.masterDeck.size();++i)
            if(b.masterDeck[i].id==root.masterDeck[i].id)
                score+=120*std::max(0,b.masterDeck[i].getUpgradeCount()-root.masterDeck[i].getUpgradeCount());
    }
    available(b,[&](const CardInstance &c) {
        if(c.id==CardId::RITUAL_DAGGER) score+=c.specialData*80+(terminal?0:40);
        if(!terminal) {
            if(c.id==CardId::FEED)score+=40;
            if(c.id==CardId::LESSON_LEARNED)score+=40;
            if(c.id==CardId::CONJURE_BLADE)score+=25;
        }
    });
    for(const auto &c:b.cards.exhaustPile) if(c.id==CardId::RITUAL_DAGGER) score+=c.specialData*80;
    if(terminal) score+=b.player.curHp-root.player.curHp;
    if(terminal) {
        // Same terminal inventory values as SaveState PotionState.POTION_VALUES.
        static const std::map<Potion,int> values={
            {Potion::AMBROSIA,100},{Potion::ANCIENT_POTION,20},{Potion::ATTACK_POTION,15},
            {Potion::BLESSING_OF_THE_FORGE,20},{Potion::BLOCK_POTION,60},{Potion::BLOOD_POTION,120},
            {Potion::BOTTLED_MIRACLE,60},{Potion::COLORLESS_POTION,15},{Potion::CULTIST_POTION,30},
            {Potion::CUNNING_POTION,40},{Potion::DEXTERITY_POTION,12},{Potion::DISTILLED_CHAOS,40},
            {Potion::DUPLICATION_POTION,50},{Potion::ELIXIR_POTION,20},{Potion::ENERGY_POTION,60},
            {Potion::ENTROPIC_BREW,240},{Potion::ESSENCE_OF_DARKNESS,60},{Potion::ESSENCE_OF_STEEL,60},
            {Potion::EXPLOSIVE_POTION,40},{Potion::FAIRY_POTION,100},{Potion::FEAR_POTION,20},
            {Potion::FIRE_POTION,40},{Potion::FOCUS_POTION,60},{Potion::FRUIT_JUICE,30},
            {Potion::GAMBLERS_BREW,500},{Potion::GHOST_IN_A_JAR,80},{Potion::HEART_OF_IRON,48},
            {Potion::LIQUID_BRONZE,20},{Potion::LIQUID_MEMORIES,30},{Potion::POISON_POTION,20},
            {Potion::POTION_OF_CAPACITY,30},{Potion::POWER_POTION,50},{Potion::REGEN_POTION,64},
            {Potion::SNECKO_OIL,75},{Potion::SPEED_POTION,75},{Potion::FLEX_POTION,21},
            {Potion::STRENGTH_POTION,40},{Potion::SWIFT_POTION,40},{Potion::WEAK_POTION,50}};
        for(int i=0;i<b.potionCapacity;++i) {auto it=values.find(b.potions[i]);if(it!=values.end())score+=it->second;}
    }
    if(b.player.hasRelic<R::LIZARD_TAIL>())score+=400;
    return score;
}
}
Json TacticalScore::json() const {
    return {{"survival",survival},{"damage",damage},{"threat",threat},{"lethal",lethal},
        {"scaling",scaling},{"resource",resource},{"mechanism",mechanism},{"total",total()}};
}
TacticalScore evaluate(const BattleContext &root,const BattleContext &b) {
    TacticalScore s;
    if(b.player.curHp<=0 || b.outcome==Outcome::PLAYER_LOSS) {s.survival=-1000000;return s;}
    s.damage=(enemyHp(root)-enemyHp(b))*10;
    if(b.outcome==Outcome::PLAYER_VICTORY) {s.lethal=1000000;s.resource=resource(b,true,root);return s;}
    int threat=0;const int damage=incoming(b,&threat);s.threat=-threat;
    int danger=std::max(damage,std::max(1,b.player.maxHp/5));
    s.survival=b.player.curHp*100-200*std::max(0,2*danger-b.player.curHp)-700*std::max(0,danger-b.player.curHp);
    int enemyTotal=0,alive=0;
    for(int i=0;i<b.monsters.monsterCount;++i) {
        const auto &m=b.monsters.arr[i];
        if(m.id==MonsterId::GREMLIN_NOB || m.id==MonsterId::LAGAVULIN) s.survival/=2;
        if(!m.isTargetable())continue; ++alive;enemyTotal+=m.curHp;
        auto info=m.getMoveBaseDamage(b);if(m.curHp<=5 && info.attackCount>0 && info.damage>0)s.lethal+=150;

    }
    int attacks=0,skills=0,fuel=0,count=0,demand=0;
    available(b,[&](const auto &c){++count;attacks+=c.getType()==CardType::ATTACK;skills+=c.getType()==CardType::SKILL;fuel+=c.doesExhaust();});
    if(power(b,PS::CORRUPTION))fuel=skills;
    else if(has(b,CardId::TRUE_GRIT)||has(b,CardId::SECOND_WIND)||has(b,CardId::FIEND_FIRE)||has(b,CardId::BURNING_PACT))fuel=std::max(fuel,count-1);
    s.mechanism+=b.player.strength*std::min(8,attacks)*10;
    s.mechanism+=std::min(5,fuel)*(power(b,PS::FEEL_NO_PAIN)*10+power(b,PS::DARK_EMBRACE)*25+(power(b,PS::CORRUPTION)?25:0));
    int retained=(power(b,PS::BARRICADE)||power(b,PS::BLUR))?b.player.block:
        b.player.hasRelic<R::CALIPERS>()?std::max(0,b.player.block-15):0;
    s.mechanism+=std::min(retained,std::max(0,enemyTotal))*10;
    auto character=sts::search::characterMechanisms(b);
    s.mechanism+=character.total();s.mechanismRules=character.rules;
    double orbDamage=0,orbBlock=0,energy=0;
    for(int i=0;i<b.player.orbSlots;++i) {
        const auto o=b.player.orbTypes[i];
        if(o==Orb::LIGHTNING)orbDamage+=std::max(0,3+b.player.focus)*1.5*(power(b,PS::ELECTRO)?alive:1);
        if(o==Orb::FROST)orbBlock+=std::max(0,2+b.player.focus)*1.5;
        if(o==Orb::PLASMA)energy+=1.5;
        if(o==Orb::DARK && i==0 && (has(b,CardId::DUALCAST)||has(b,CardId::MULTI_CAST)||has(b,CardId::RECURSION)))orbDamage+=b.player.orbData[i]*0.5;
    }
    for(int i=0;i<b.cards.cardsInHand;++i) {const auto &c=b.cards.hand[i];demand+=std::max(0,int(c.costForTurn));
        if(c.id==CardId::MIRACLE)s.resource+=20;
        if(c.id==CardId::CATALYST)s.resource+=25;

    }
    s.mechanism+=int(std::min(double(enemyTotal),orbDamage)*10)+int(std::min(orbBlock,double(damage))*15)+int(std::min(energy,double(std::max(0,demand-b.player.energy)))*20);
    s.mechanism+=std::max(0,power(b,PS::ECHO_FORM))*std::min(4,count)*15;

    s.mechanism=std::clamp(s.mechanism,-20000,20000);
    s.scaling=b.player.dexterity*3+power(b,PS::DEMON_FORM)*6+power(b,PS::FIRE_BREATHING)*2+power(b,PS::COMBUST)*2+power(b,PS::EVOLVE)*16;
    s.resource+=resource(b,false,root);return s;
}
int priority(const BattleContext &b,const SA &a) {
    if(a.getActionType()==AT::END_TURN || a.getActionType()==AT::POTION)return -50000;
    if(a.getActionType()==AT::SINGLE_CARD_SELECT && isCharacterSelection(b.cardSelectInfo.cardSelectTask)) {
        if(a.getSelectIdx()==CHARACTER_SELECT_CONFIRM)return 0;
        return sts::search::characterSelectionPriority(b,b.cardSelectInfo.options.at(a.getSelectIdx()),
            sts::search::selectionPurpose(b.cardSelectInfo.cardSelectTask))*1000;
    }
    if(a.getActionType()!=AT::CARD)return 0;
    const auto &c=b.cards.hand[a.getSourceIdx()];int p=0;
    const auto *m=c.requiresTarget()?&b.monsters.arr[a.getTargetIdx()]:nullptr;
    switch(c.id) {
    case CardId::CORRUPTION:case CardId::DARK_EMBRACE:case CardId::FEEL_NO_PAIN:p=90;break;
    case CardId::AFTER_IMAGE:case CardId::ACCURACY:case CardId::FOOTWORK:
    case CardId::DEFRAGMENT:case CardId::ELECTRODYNAMICS:case CardId::ECHO_FORM:case CardId::LOOP:p=80;break;
    case CardId::BODY_SLAM:p=b.player.block>0?10:-60;break;
    case CardId::ENTRENCH:p=b.player.block>0?60:-100;break;
    case CardId::DROPKICK:p=m && m->vulnerable>0?100:-10;break;
    default:break;
    }
    const auto color=getCardColor(c.id);
    if(color==CardColor::GREEN || color==CardColor::PURPLE ||
       (color!=CardColor::RED && color!=CardColor::BLUE && (b.player.cc==CharacterClass::SILENT || b.player.cc==CharacterClass::WATCHER)))
        return sts::search::characterPlayPriority(b,c,c.requiresTarget()?a.getTargetIdx():-1)*1000-sts::search::characterFallbackRank(b,c.id);
    if(c.getType()==CardType::POWER && p==0)p=20;
    if(c.costForTurn==0 && getBaseDamage(c.id,c.upgraded)>0)p+=5;
    // Mechanism priority dominates the stable legacy fallback order.
    return p*1000-sts::search::Expert::getPlayOrdering(c.id);
}
Json stateKey(const BattleContext &b) {
    if(b.inputState!=InputState::PLAYER_NORMAL || b.actionQueue.size || b.cardQueue.size)return nullptr;
    const auto &p=b.player;
    Json out=Json::array();
    out.push_back(Json::array({b.seed,b.floorNum,int(b.encounter),b.ascension,int(b.outcome),b.turn,
        b.isBattleOver,b.endTurnQueued,b.turnHasEnded,b.skipMonsterTurn,b.monsterTurnIdx,b.potionCount,b.potionCapacity,b.powersPlayedThisCombat,b.mantraGained,int(b.lastCardType),b.miscBits.to_ullong()}));
    out.push_back(Json::array({b.masterDeckKnown,b.cardRngKnown,b.cardRng.counter,b.cardRng.seed0,b.cardRng.seed1}));
    out.push_back(cardKey(b.nightmareCard));
    Json master=Json::array();for(const auto &card:b.masterDeck)master.push_back(cardKey(card));out.push_back(master);
    out.push_back(Json::array({int(p.cc),p.gold,p.curHp,p.maxHp,p.energy,p.energyPerTurn,p.cardDrawPerTurn,int(p.stance),p.orbSlots,
        p.block,p.artifact,p.dexterity,p.focus,p.strength,p.justAppliedBits,p.statusBits0,p.statusBits1,
        p.relicBits0,p.relicBits1,p.relicBits2,p.happyFlowerCounter,p.incenseBurnerCounter,p.inkBottleCounter,p.inserterCounter,p.nunchakuCounter,p.penNibCounter,p.sundialCounter,
        p.haveUsedNecronomiconThisTurn,p.combustHpLoss,p.devaFormEnergyPerTurn,p.echoFormCardsDoubled,p.panacheCounter,p.bomb1,p.bomb2,p.bomb3,
        p.cardsPlayedThisTurn,p.attacksPlayedThisTurn,p.skillsPlayedThisTurn,p.orangePelletsCardTypesPlayed.to_ulong(),p.cardsDiscardedThisTurn,p.lastAttackUnblockedDamage,p.timesDamagedThisCombat,p.lastDamageTaken,p.lightningChanneled,p.frostChanneled,p.hasStatus<PS::SURROUNDED>()?p.lastTargetedMonster:0}));
    for(const auto &[k,v]:p.statusMap)out.push_back(Json::array({int(k),v}));
    for(int i=0;i<p.orbSlots;++i)out.push_back(Json::array({int(p.orbTypes[i]),p.orbData[i]}));
    for(const auto &r:{b.aiRng,b.cardRandomRng,b.miscRng,b.monsterHpRng,b.potionRng,b.shuffleRng})out.push_back(Json::array({r.counter,r.seed0,r.seed1}));
    for(int i=0;i<b.potionCapacity;++i)out.push_back(int(b.potions[i]));
    out.push_back(Json::array({b.monsters.monsterCount,b.monsters.monstersAlive,b.monsters.extraRollMoveOnTurn.to_ulong(),b.monsters.skipTurn.to_ulong()}));
    for(int i=0;i<b.monsters.monsterCount;++i) {const auto &m=b.monsters.arr[i];out.push_back(Json::array({int(m.id),m.idx,m.curHp,m.maxHp,m.block,m.isEscapingB,m.halfDead,m.escapeNext,int(m.moveHistory[0]),int(m.moveHistory[1]),m.statusBits,m.artifact,m.blockReturn,m.choked,m.corpseExplosion,m.lockOn,m.mark,m.metallicize,m.platedArmor,m.poison,m.regen,m.shackled,m.strength,m.vulnerable,m.weak,m.uniquePower0,m.uniquePower1,m.miscInfo}));}
    Json hand=Json::array();for(int i=0;i<b.cards.cardsInHand;++i)hand.push_back(cardKey(b.cards.hand[i]));out.push_back(hand);
    for(const auto *pile:{&b.cards.drawPile,&b.cards.discardPile,&b.cards.exhaustPile}) {Json a=Json::array();for(const auto &c:*pile)a.push_back(cardKey(c));out.push_back(a);}
    for(const auto &c:b.cards.stasisCards)out.push_back(cardKey(c));
    out.push_back(Json::array({b.cards.handNormalityCount,b.cards.handPainCount,b.cards.strikeCount,b.cards.handBloodCardCount,b.cards.drawPileBloodCardCount,b.cards.discardPileBloodCardCount}));
    return out;
}
TurnSearchResult turnSearch(const BattleContext &root,int cap,long timeout,const std::string &profile) {
    sts::search::ActionEnumerationOptions options;options.allowPotions=true;options.allowRootPotions=true;
    std::vector<SA> rootActions;sts::search::enumerateActions(root,options,true,rootActions);
    if(rootActions.size()==1) {
        TurnSearchResult forced;forced.path=rootActions;forced.reason="FORCED_ROOT_ACTION";return forced;
    }
    struct Node {BattleContext state;std::shared_ptr<Node> parent;SA action;int depth=0;int score=0;std::uint64_t order=0;};
    using Ptr=std::shared_ptr<Node>;
    auto less=[](const Ptr&a,const Ptr&b){return a->score==b->score?a->order>b->order:a->score<b->score;};
    std::priority_queue<Ptr,std::vector<Ptr>,decltype(less)> turns(less);
    std::uint64_t order=0;auto first=std::make_shared<Node>();first->state=root;first->score=evaluate(root,root).total();turns.push(first);
    Ptr best=nullptr,win=nullptr,death=nullptr,progress=nullptr;
    std::unordered_set<std::string> seen;TurnSearchResult result;
    auto rootKey=stateKey(root);if(!rootKey.is_null())seen.insert(rootKey.dump());
    auto deadline=std::chrono::steady_clock::now()+std::chrono::milliseconds(timeout);
    while(!turns.empty() && result.expansions<cap && std::chrono::steady_clock::now()<deadline) {
        auto turn=turns.top();turns.pop();
        std::vector<Ptr> stack{turn};
        while(!stack.empty() && result.expansions<cap && std::chrono::steady_clock::now()<deadline) {
            auto n=stack.back();stack.pop_back();
            if(n->state.outcome==Outcome::PLAYER_LOSS || n->state.player.curHp<=0) {if(!death || n->state.turn>death->state.turn)death=n;continue;}
            if(n->state.outcome==Outcome::PLAYER_VICTORY) {if(!win || n->score>win->score)win=n;continue;}
            if(n->parent && (!progress || n->score>progress->score))progress=n;
            if(n!=turn && n->state.turn>turn->state.turn) {
                if(!best || n->score>best->score)best=n;
                turns.push(n);continue;
            }
            std::vector<SA> actions;sts::search::enumerateActions(n->state,options,n==first,actions);
            std::vector<std::pair<int,SA>> ranked;ranked.reserve(actions.size());
            for(const auto &a:actions)ranked.emplace_back(priority(n->state,a),a);
            std::stable_sort(ranked.begin(),ranked.end(),[](const auto &a,const auto &b){return a.first>b.first;});
            for(std::size_t i=0;i<actions.size();++i)actions[i]=ranked[i].second;
            auto oldSize=stack.size();
            for(auto it=actions.begin();it!=actions.end();++it) {
                if(result.expansions>=cap || std::chrono::steady_clock::now()>=deadline)break;
                auto child=std::make_shared<Node>();child->state=n->state;child->parent=n;child->action=*it;
                try {ExecutionBudget budget(deadline);child->action.execute(child->state);}
                catch(const ExecutionTimeout &) {break;}
                ++result.expansions;
                if(child->state.undefinedBehaviorEvoked)throw Unsupported("engine_undefined_behavior");
                auto key=stateKey(child->state);
                if(!key.is_null() && !seen.insert(key.dump()).second){++result.duplicates;continue;}
                child->depth=child->state.turn>n->state.turn?0:n->depth+1;child->order=++order;
                child->score=evaluate(root,child->state).total();stack.push_back(child);
                if(child->state.outcome==Outcome::PLAYER_VICTORY && child->state.player.curHp>0) {if(!win || child->score>win->score)win=child;}
                else if(child->state.outcome==Outcome::PLAYER_LOSS || child->state.player.curHp<=0) {if(!death || child->state.turn>death->state.turn)death=child;}
                else if(!progress || child->score>progress->score)progress=child;
            }
            std::reverse(stack.begin()+oldSize,stack.end());
        }
        if(win && profile!="DEEP")break;
        if(profile=="FAST" && best && result.expansions>=std::max(1,cap/3))break;
    }
    bool allLose=!win && death && turns.empty() && result.expansions<cap && std::chrono::steady_clock::now()<deadline;
    Ptr chosen=win?win:allLose?death:best?best:progress?progress:death;
    result.victory=bool(win);result.reason=win?"VICTORY":allLose?"ALL_LOSE":result.expansions>=cap?"EXPANSION_LIMIT":std::chrono::steady_clock::now()>=deadline?"TIMEOUT":"SEARCH_EXHAUSTED";
    for(auto n=chosen;n && n->parent;n=n->parent)result.path.push_back(n->action);
    std::reverse(result.path.begin(),result.path.end());return result;
}
}
