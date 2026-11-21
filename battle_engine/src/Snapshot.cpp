#include "Snapshot.h"
#include "TurnSearch.h"
#include "HeadlessRng.h"
#include "combat/CharacterCards.h"
#include "combat/ExecutionBudget.h"
#include "combat/CharacterSelection.h"
#include "sim/search/CharacterStrategy.h"
#include "sim/search/ActionEnumerator.h"
#include "convert/BattleConverter.h"
#include "game/Game.h"
#include "sim/search/BattleScumSearcher2.h"
#include "combat/MonsterSpawnObserver.h"
#include <chrono>
#include <set>

using namespace sts;
namespace bridge {
struct Session::Cache {
    std::string owner,profile,mode,policy;bool tacticalOrdering=true; std::string dedup; Json expected,identities,pools,attributes;
    Json pathContext, expectedProjection;
    int cap=0;long budget=0;
    std::vector<search::Action> path;std::size_t offset=0;
};
Session::Session() : state(new Cache{}) {}
Session::~Session() = default;
namespace {
Json layeredPathContext(const Json &snapshot,const std::array<int,5> &targets,bool exactRngSupported=false) {
    if(snapshot.value("is_screen_up",false))return nullptr;
    Json context={{"targets",targets},{"act",snapshot.value("act_num",0)},
        {"room_type",snapshot.at("cur_map_node_state").value("room_type",std::string())}};
    if(snapshot.contains("_headless_rng")) {
        if(!exactRngSupported)return nullptr;
        const auto &rng=snapshot.at("_headless_rng");
        const auto &streams=rng.at("streams");
        Json fixed={{"policy",rng.at("policy")},{"math",rng.at("math")}};
        // Modeled streams are already part of the physical native key.
        for(auto name:{"monster","map","event","merchant","treasure","relic"})
            fixed[name]=streams.at(name);
        context["headless"]=std::move(fixed);
    }
    return context;
}
Json cardAttributes(const Json &snapshot) {
    Json result=Json::object();
    for(auto pile:{"hand","draw_pile","discard_pile","exhaust_pile"})for(const auto &card:snapshot.at("player_state").at(pile)) {
        if(!card.contains("uuid") || card.at("uuid").is_null())continue;
        Json attributes=Json::object();
        for(auto field:{"base_damage","base_block","base_magic_number","misc","exhaust","is_ethereal","self_retain","purge_on_use","shuffle_back_into_draw_pile","target_enum_ordinal"})
            if(card.contains(field))attributes[field]=card.at(field);
        result[card.at("uuid").get<std::string>()]=attributes;
    }
    return result;
}
void require(bool ok, const std::string &reason) { if (!ok) throw Unsupported(reason); }
Json cardView(const Json &c) {
    return {{"id",c.at("card_id")},{"upgrades",c.value("times_upgraded",c.value("upgraded",false)?1:0)},
        {"cost",c.at("cost")},{"turn_cost",c.at("cost_for_turn")},
        {"free",c.value("free_to_play_once",false)},{"retain",c.value("retain",false)}};
}
Json cardView(const CardInstance &c) {
    return {{"id",getCardStringId(c.id)},{"upgrades",c.getUpgradeCount()},
        {"cost",int(c.cost)},{"turn_cost",int(c.costForTurn)},
        {"free",c.freeToPlayOnce},{"retain",c.retain}};
}
Json cards(const Json &list) {
    Json result=Json::array(); for (const auto &c:list) result.push_back(cardView(c)); return result;
}
template<class Range> Json cards(const Range &list) {
    Json result=Json::array(); for (const auto &c:list) result.push_back(cardView(c)); return result;
}
const char *rngNames[]={"ai_rng_counter","card_random_rng_counter","misc_rng_counter",
    "monster_hp_rng_counter","potion_rng_counter","shuffle_rng_counter"};
Json convertCard(const Json &c) {
    auto id=c.at("card_id").get<std::string>();
    require(getCardIdFromId(id)!=CardId::INVALID,"unknown_card:"+id);
    if(getCardIdFromId(id)==CardId::RAMPAGE)
        require(c.contains("base_damage") && c.at("base_damage").is_number_integer(),"missing_base_damage:Rampage");
    if(getCardIdFromId(id)==CardId::GLASS_KNIFE || getCardIdFromId(id)==CardId::WINDMILL_STRIKE)
        require(c.contains("base_damage") && c.at("base_damage").is_number_integer(),"missing_base_damage:"+id);
    if(getCardIdFromId(id)==CardId::PERSEVERANCE)
        require(c.contains("base_block") && c.at("base_block").is_number_integer(),"missing_base_block:"+id);
    const auto color=getCardColor(getCardIdFromId(id));
    require((color!=CardColor::GREEN && color!=CardColor::PURPLE) || characterCardSupported(getCardIdFromId(id)),"unimplemented_card:"+id);
    require(c.value("modifiers",Json()).is_null(),"card_modifiers:"+id);
    // Tantrum's shuffle flag is its intrinsic destination, implemented by
    // onAfterUseCard. Arbitrary card destination overrides stay gated.
    require(!c.value("purge_on_use",false) &&
        (!c.value("shuffle_back_into_draw_pile",false) || getCardIdFromId(id)==CardId::TANTRUM),"card_flags:"+id);
    Json result=c;
    result["id"]=id;
    result["upgrades"]=c.value("times_upgraded",c.value("upgraded",false)?1:0);
    result["combat_cost"]=c.at("cost"); result["cost"]=c.at("cost_for_turn");
    return result;
}
std::pair<int,int> headlessBomb(const Json &power) {
    // The source allocates a distinct decimal ID per TheBombPower instance;
    // amount is remaining turns, not damage. Do not accept an arbitrary prefix.
    const auto id=power.at("power_id").get<std::string>();
    const auto suffix=id.substr(7);
    require(id.rfind("TheBomb",0)==0 && !suffix.empty() && suffix.size()<=10 &&
        std::all_of(suffix.begin(),suffix.end(),[](char c){return c>='0' && c<='9';}) &&
        (suffix.size()==1 || suffix.front()!='0') &&
        (suffix.size()<10 || suffix<="2147483647"),"invalid_headless_bomb_id");
    const auto &turns=power.at("amount");
    require(turns.is_number_integer() && turns>=1 && turns<=3,"invalid_headless_bomb_turns");
    require(power.contains("_private") && power.at("_private").is_object() &&
        power.at("_private").contains("TheBombPower.damage"),"missing_headless_bomb_damage");
    const auto &damage=power.at("_private").at("TheBombPower.damage");
    require(damage.is_number_integer() && damage>=1 && damage<=127,"invalid_headless_bomb_damage");
    return {turns.get<int>(),damage.get<int>()};
}
Json convertPowers(const Json &powers,bool player,const std::string &owner="",bool headless=false) {
    Json result=Json::array();
    for (const auto &p:powers) {
        auto id=p.at("power_id").get<std::string>();
        if(headless && player && id.rfind("TheBomb",0)==0 && id!="TheBomb") {
            headlessBomb(p);
            // Restore into native countdown buckets after the generic converter:
            // that legacy path uses amount as damage and sets an unused status bit.
            continue;
        }
        if(!player && id=="Split") {
            auto monster=getMonsterIdFromId(owner);
            require(monster==MonsterId::SLIME_BOSS || monster==MonsterId::ACID_SLIME_L ||
                monster==MonsterId::SPIKE_SLIME_L,"split_power_owner");
            // Native Monster::onHpLost implements this marker as intrinsic slime behavior.
            continue;
        }
        if(!player && owner=="AwakenedOne" && id=="Unawakened") {
            require(p.at("amount")==-1,"invalid_unawakened_marker");
            // The source marker has no callbacks. The actual form is bound
            // by its private latch below, and participates in the native key.
            continue;
        }
        if(!player && owner=="AwakenedOne" && id=="Regenerate") {
            const auto &amount=p.at("amount");
            require(amount.is_number_integer() && amount>0,"invalid_awakened_regenerate");
            id="Regeneration";  // The native REGEN already heals each turn without decrement.
        }
        if(headless && !player && id=="Compulsive") {
            require(owner=="WrithingMass" && p.at("amount")==-1,"invalid_reactive_marker");
        }
        if(headless && player && id=="Heatsink")id="Heatsinks"; // Java power ID, distinct from its card ID.
        if(headless && player && id=="EnergizedBlue")id="Energized"; // Defect's variant has the same energy-recharge effect.
        if (id=="Explosive") {
            const auto &amount=p.at("amount");
            require(headless && !player && owner=="Exploder" && amount.is_number_integer()
                && amount>=1 && amount<=3,"invalid_explosive_power");
        }
        require(player ? getPlayerStatusFromId(id)!=PlayerStatus::INVALID :
            getMonsterStatusFromId(id)!=MonsterStatus::INVALID,"unknown_power:"+id);
        // These need additional private state with different SaveState/upstream schemas.
        static const std::set<std::string> extra={"Combust","Echo Form","Panache","TheBomb","Stasis"};
        const bool mappedCombust=headless && player && id=="Combust";
        const bool mappedPanache=headless && player && id=="Panache";
        require(!extra.count(id) || mappedCombust || mappedPanache,"power_private_state:"+id);
        Json q=p; q["id"]=id;
        if(mappedPanache) {
            const auto &damage=p.at("_private").at("PanachePower.damage");
            require(damage.is_number_integer() && damage>0 && damage<=32767 && p.at("amount").is_number_integer()
                && p.at("amount")>=1 && p.at("amount")<=5,"invalid_panache_state");
            q["damage"]=damage;
        }
        if(headless && player && id=="WrathNextTurnPower") {
            require(p.at("amount").is_number_integer() && p.at("amount")==-1,"invalid_wrath_next_turn_marker");
            // A source marker with no numeric strength. Native stores its
            // presence as 1; the status bit still binds creation/removal.
            q["amount"]=1;
        }
        if(mappedCombust) {
            require(p.contains("_private") && p.at("_private").is_object()
                && p.at("_private").contains("CombustPower.hpLoss"),"missing_combust_hp_loss");
            const auto &hpLoss=p.at("_private").at("CombustPower.hpLoss");
            require(hpLoss.is_number_integer() && hpLoss>=1 && hpLoss<=127,"invalid_combust_hp_loss");
            // The fixed source stores one HP per application, independently of
            // damage/upgrades and card history. Native importer already binds
            // misc to combustHpLoss, which participates in the full native key.
            q["misc"]=hpLoss;
        }
        if(player && id=="DevaForm") {
            require(p.contains("energy_gain_amount") && p.at("energy_gain_amount").is_number_integer(),"missing_power_state:DevaForm");
            q["misc"]=p.at("energy_gain_amount");
        }
        result.push_back(q);
    }
    return result;
}
void restoreCards(BattleContext &bc,const Json &p) {
    auto restore=[](CardInstance &c,const Json &j) {
        c.freeToPlayOnce=j.value("free_to_play_once",false); c.retain=j.value("retain",false);
        c.forcedExhaust=j.value("exhaust",false) && !doesCardExhaust(c.id,c.upgraded);
    };
    for (int i=0;i<bc.cards.cardsInHand;++i) restore(bc.cards.hand[i],p.at("hand").at(i));
    int i=0; for(auto &c:bc.cards.drawPile) restore(c,p.at("draw_pile").at(i++));
    i=0; for(auto &c:bc.cards.discardPile) restore(c,p.at("discard_pile").at(i++));
    i=0; for(auto &c:bc.cards.exhaustPile) restore(c,p.at("exhaust_pile").at(i++));
}
}

CardInstance snapshotCard(const Json &raw,int uniqueId,bool inFlight) {
    Json c=raw;
    if(inFlight)c["purge_on_use"]=false;
    auto card=importCombatCard(convertCard(c),uniqueId);
    card.freeToPlayOnce=raw.value("free_to_play_once",false);card.retain=raw.value("retain",false);
    card.forcedExhaust=raw.value("exhaust",false) && !doesCardExhaust(card.id,card.upgraded);
    return card;
}

Json projection(const Json &s) {
    const auto &p=s.at("player_state"), &pc=p.at("creature");
    Json out={{"format",1},{"turn",s.at("turn")},{"hp",pc.at("current_health")},
        {"max_hp",pc.at("max_health")},{"block",pc.at("current_block")},
        {"energy",p.at("energy_panel_total_energy")}};
    for(auto name:{"hand","draw_pile","discard_pile","exhaust_pile"}) out[name]=cards(p.at(name));
    out["monsters"]=Json::array();
    for(const auto &m:s.at("cur_map_node_state").at("monster_data")) {
        const auto &c=m.at("creature");
        out["monsters"].push_back({{"id",c.at("id")},{"hp",c.at("current_health")},
            {"max_hp",c.at("max_health")},{"block",c.at("current_block")}});
    }
    out["potions"]=Json::array(); for(const auto &v:p.at("potions")) out["potions"].push_back(v.at("id"));
    out["orbs"]=Json::array();
    for(const auto &o:p.at("orbs")) {
        auto id=o.at("lookup_key").get<std::string>();
        out["orbs"].push_back({{"id",id},{"dark",id=="Dark"?o.at("evoke_amount").get<int>():0}});
    }
    out["rng"]=Json::object(); for(auto name:rngNames) out["rng"][name]=s.at("rng_state").at(name);
    return out;
}

Json projection(const BattleContext &bc,const std::array<int,5> &map) {
    Json out={{"format",1},{"turn",bc.turn+1},{"hp",bc.player.curHp},
        {"max_hp",bc.player.maxHp},{"block",bc.player.block},{"energy",bc.player.energy}};
    out["hand"]=Json::array(); for(int i=0;i<bc.cards.cardsInHand;++i) out["hand"].push_back(cardView(bc.cards.hand[i]));
    out["draw_pile"]=cards(bc.cards.drawPile); out["discard_pile"]=cards(bc.cards.discardPile);
    out["exhaust_pile"]=cards(bc.cards.exhaustPile);
    std::map<int,Json> monsters;
    for(int i=0;i<bc.monsters.monsterCount;++i) {
        const auto &m=bc.monsters.arr[i];
        if(map[i]<0 || m.id==MonsterId::INVALID) continue;
        monsters[map[i]]={{"id",monsterIdIds[int(m.id)]},{"hp",m.curHp},{"max_hp",m.maxHp},{"block",m.block}};
    }
    out["monsters"]=Json::array(); for(const auto &entry:monsters) out["monsters"].push_back(entry.second);
    out["potions"]=Json::array(); for(int i=0;i<bc.potionCapacity;++i)
        out["potions"].push_back(bc.potions[i]==Potion::EMPTY_POTION_SLOT?"Potion Slot":potionIds[int(bc.potions[i])]);
    const char *orbNames[]={"EmptyOrbSlot","Lightning","Frost","Dark","Plasma"};
    out["orbs"]=Json::array(); for(int i=0;i<bc.player.orbSlots;++i) {
        // Orb enum is checked below; avoid relying on its order in the wire format.
        const auto o=bc.player.orbTypes[i];
        const char *name=o==Orb::LIGHTNING?orbNames[1]:o==Orb::FROST?orbNames[2]:
            o==Orb::DARK?orbNames[3]:o==Orb::PLASMA?orbNames[4]:orbNames[0];
        out["orbs"].push_back({{"id",name},{"dark",o==Orb::DARK?int(bc.player.orbData[i]):0}});
    }
    out["rng"]={{rngNames[0],bc.aiRng.counter},{rngNames[1],bc.cardRandomRng.counter},
        {rngNames[2],bc.miscRng.counter},{rngNames[3],bc.monsterHpRng.counter},
        {rngNames[4],bc.potionRng.counter},{rngNames[5],bc.shuffleRng.counter}};
    return out;
}

Json projection(const Imported &imported) {
    auto result=projection(imported.battle,imported.monsterMap);
    result["monsters"]=imported.roster.project(imported.battle,imported.monsterMap);
    const auto &b=imported.battle;
    if(decisionScreen(b)=="HAND_SELECT") {
        std::set<int> selected;
        for(const auto &c:b.cardSelectInfo.hiddenHand)selected.insert(c.uniqueId);
        for(int i:b.cardSelectInfo.selected)selected.insert(b.cardSelectInfo.options.at(i).uniqueId);
        result["hand"]=Json::array();
        for(int i=0;i<b.cards.cardsInHand;++i)if(!selected.count(b.cards.hand[i].uniqueId))result["hand"].push_back(cardView(b.cards.hand[i]));
    }
    return result;
}
void executeImported(Imported &imported,const search::Action &action) {
    ScopedMonsterSpawnObserver observer([&](const BattleContext &bc,int slot,double x,int relativeTo,bool split) {
        imported.roster.spawn(bc,imported.monsterMap,slot,x,relativeTo,split);
    });
    action.execute(imported.battle);
}

Json gamblerSelection(const BattleContext &b,const std::map<int,Json> &identities,const std::array<int,5> &targets) {
    if(b.inputState!=InputState::CARD_SELECT)return nullptr;
    if(isHeadlessSingleHand(b.cardSelectInfo.cardSelectTask)) {
        const auto &s=b.cardSelectInfo;auto &q=b.curCardQueueItem;
        require(!q.exhaustOnUse && !q.purgeOnUse && q.triggerOnUse && (b.cardQueue.isEmpty() || q.autoplay),"single_hand_pending_variant");
        auto pending=cardView(q.card);pending["uuid"]=identities.at(q.card.uniqueId);
        Json selected=Json::array(),visible=Json::array(),hidden=Json::array();std::set<int> except;
        for(auto i:s.selected){auto c=s.options.at(i);selected.push_back(identities.at(c.uniqueId));except.insert(c.uniqueId);}
        for(auto c:s.hiddenHand){hidden.push_back(identities.at(c.uniqueId));except.insert(c.uniqueId);}
        for(int i=0;i<b.cards.cardsInHand;i++)if(!except.count(b.cards.hand[i].uniqueId))visible.push_back(identities.at(b.cards.hand[i].uniqueId));
        Json result={{"schema","headless-single-hand-v1"},{"remaining",visible},{"selected",selected},{"hidden",hidden},
                     {"pending_card",pending},{"copies",s.data0}};
        if(s.cardSelectTask==CardSelectTask::FORETHOUGHT_HAND)
            result.update(Json{{"count",s.pickCount},{"any_number",s.canPickAnyNumber}});
        if(!b.cardQueue.isEmpty()) {
            result["autoplay_queue"]=Json::array();
            for(const auto &item:b.cardQueue.orderedItems()) {
                require(item.autoplay && !item.ignoreEnergyTotal && !item.randomTarget && !item.isEndTurn &&
                        !item.purgeOnUse && item.target>=0 && targets.at(item.target)>=0,"single_hand_autoplay_item");
                auto card=cardView(item.card);card["uuid"]=identities.at(item.card.uniqueId);
                card["target"]=targets.at(item.target);card["energy"]=item.energyOnUse;
                result["autoplay_queue"].push_back(card);
            }
        }
        return result;
    }
    const bool liquid=b.cardSelectInfo.cardSelectTask==CardSelectTask::LIQUID_MEMORIES_GRID;
    const bool headbutt=b.cardSelectInfo.cardSelectTask==CardSelectTask::HEADBUTT_GRID;
    const bool fetch=isHeadlessFetchGrid(b.cardSelectInfo.cardSelectTask);
    if(!fetch && !liquid && !headbutt && !isHeadlessMultiHand(b.cardSelectInfo.cardSelectTask))return nullptr;
    const auto &s=b.cardSelectInfo;Json selected=Json::array(),remaining=Json::array();std::set<int> ids;
    for(int i:s.selected){int id=s.options.at(i).uniqueId;require(identities.count(id) && identities.at(id).is_string(),"gamble_selected_uuid");ids.insert(id);selected.push_back(identities.at(id));}
    if(liquid || headbutt || fetch) {
        Json options=Json::array();for(const auto &c:s.options){require(identities.count(c.uniqueId) && identities.at(c.uniqueId).is_string(),"liquid_option_uuid");options.push_back(identities.at(c.uniqueId));}
        if(fetch) {
            const auto &item=b.curCardQueueItem;const auto &c=item.card;
            const bool seek=s.cardSelectTask==CardSelectTask::SEEK_GRID, omni=s.cardSelectTask==CardSelectTask::OMNISCIENCE;
            const auto source=seek?CardId::SEEK:omni?CardId::OMNISCIENCE:
                s.cardSelectTask==CardSelectTask::SECRET_WEAPON_GRID?CardId::SECRET_WEAPON:
                s.cardSelectTask==CardSelectTask::SECRET_TECHNIQUE_GRID?CardId::SECRET_TECHNIQUE:CardId::HOLOGRAM;
            auto uid=[&](const CardQueueItem &row) -> Json {
                auto it=identities.find(row.card.uniqueId);
                if(it!=identities.end())return it->second;
                require(omni && row.purgeOnUse,"fetch_generated_identity");
                return nullptr;
            };
            require(c.id==source && (!item.purgeOnUse || (omni && item.autoplay)) && item.triggerOnUse,"fetch_pending_uuid");
            auto pending=cardView(c);pending["uuid"]=uid(item);pending["exhaust"]=item.exhaustOnUse;
            if(item.purgeOnUse)pending["purge"]=true;
            Json result={{"schema","headless-fetch-grid-v1"},{"source",getCardStringId(source)},{"options",options},{"selected",selected},{"count",s.pickCount},{"pending_card",pending}};
            if(omni && !b.cardQueue.isEmpty()) {
                result["autoplay_queue"]=Json::array();
                for(const auto &row:b.cardQueue.orderedItems()) {
                    require(row.autoplay && row.randomTarget && !row.ignoreEnergyTotal && !row.isEndTurn &&
                            (row.purgeOnUse || row.exhaustOnUse),"omniscience_queued_item");
                    auto next=cardView(row.card);next["uuid"]=uid(row);next["exhaust"]=row.exhaustOnUse;
                    next["purge"]=row.purgeOnUse;next["energy"]=row.energyOnUse;
                    result["autoplay_queue"].push_back(next);
                }
            }
            return result;
        }
        if(headbutt) {
            const auto &item=b.curCardQueueItem;const auto &c=item.card;
            require(c.id==CardId::HEADBUTT && !item.exhaustOnUse && (!item.purgeOnUse || item.autoplay) && item.triggerOnUse,"headbutt_cleanup_variant");
            require(identities.count(c.uniqueId) && identities.at(c.uniqueId).is_string(),"headbutt_pending_uuid");
            require(item.target>=0 && item.target<5 && targets[item.target]>=0,"headbutt_pending_target");
            auto pending=cardView(c);pending["uuid"]=identities.at(c.uniqueId);pending["target"]=targets[item.target];
            return {{"schema","headless-headbutt-selection-v1"},{"options",options},{"selected",selected},{"count",1},{"pending_card",pending}};
        }
        return {{"schema","headless-liquid-selection-v1"},{"options",options},{"selected",selected},{"count",s.pickCount},{"new_cost",0}};
    }
    for(int i=0;i<b.cards.cardsInHand;++i)if(!ids.count(b.cards.hand[i].uniqueId)) {
        int id=b.cards.hand[i].uniqueId;require(identities.count(id) && identities.at(id).is_string(),"gamble_hand_uuid");remaining.push_back(identities.at(id));
    }
    return {{"schema",s.cardSelectTask==CardSelectTask::ELIXIR_EXHAUST?"headless-elixir-selection-v1":"headless-gambler-selection-v1"},{"selected",selected},{"remaining",remaining},
        {"count",s.pickCount},{"any_number",s.canPickAnyNumber},{"can_pick_zero",s.canPickZero}};
}

Json gamblerGuardKey(const Imported &imported) {
    const auto &b=imported.battle;
    if(b.inputState!=InputState::CARD_SELECT || (!isHeadlessSingleHand(b.cardSelectInfo.cardSelectTask) && !isHeadlessMultiHand(b.cardSelectInfo.cardSelectTask) && !isHeadlessFetchGrid(b.cardSelectInfo.cardSelectTask)
            && b.cardSelectInfo.cardSelectTask!=CardSelectTask::LIQUID_MEMORIES_GRID && b.cardSelectInfo.cardSelectTask!=CardSelectTask::HEADBUTT_GRID))return nullptr;
    BattleContext guard=b;std::set<int> selected;std::vector<CardInstance> ordered;
    if(isHeadlessMultiHand(b.cardSelectInfo.cardSelectTask) || isHeadlessSingleHand(b.cardSelectInfo.cardSelectTask)) {
        for(const auto &c:b.cardSelectInfo.hiddenHand)selected.insert(c.uniqueId);
        for(int i:b.cardSelectInfo.selected)selected.insert(b.cardSelectInfo.options.at(i).uniqueId);
        for(int i=0;i<b.cards.cardsInHand;++i)if(!selected.count(b.cards.hand[i].uniqueId))ordered.push_back(b.cards.hand[i]);
        for(int i:b.cardSelectInfo.selected)ordered.push_back(b.cardSelectInfo.options.at(i));
        for(const auto &c:b.cardSelectInfo.hiddenHand)ordered.push_back(c);
        require(int(ordered.size())==guard.cards.cardsInHand,"gamble_guard_hand");
        for(int i=0;i<int(ordered.size());++i)guard.cards.hand[i]=ordered[i];
    }
    // This dedicated boundary only permits the exported derived hand refresh
    // and presentation wait; native card calculations use the bound game fields.
    // Preserve all gameplay fields, canonicalize the UI's removed selections,
    // and serialize full native state separately from the ordinary null key.
    guard.inputState=InputState::PLAYER_NORMAL;guard.actionQueue.clear();guard.cardQueue.clear();
    auto key=stateKey(guard,&imported.monsterMap);
    if(isHeadlessSingleHand(b.cardSelectInfo.cardSelectTask)){
        const auto &c=b.curCardQueueItem.card;
        key.push_back(Json::array({"single-hand-cleanup-v1",int(c.id),c.specialData,c.cost,c.costForTurn,c.upgraded,
              c.freeToPlayOnce,c.retain,c.forcedExhaust,b.curCardQueueItem.exhaustOnUse,b.curCardQueueItem.purgeOnUse}));
        if(!b.cardQueue.isEmpty()) {
            Json queue=Json::array();
            for(const auto &item:b.cardQueue.orderedItems()) {
                const auto &card=item.card;
                queue.push_back(Json::array({int(card.id),card.specialData,card.cost,card.costForTurn,card.upgraded,
                    card.freeToPlayOnce,card.retain,card.forcedExhaust,imported.monsterMap.at(item.target),item.energyOnUse}));
            }
            key.push_back(Json::array({"single-hand-autoplay-v1",queue}));
        }
    }
    if(isHeadlessFetchGrid(b.cardSelectInfo.cardSelectTask)) {
        const auto &c=b.curCardQueueItem.card;
        key.push_back(Json::array({"fetch-cleanup-v1",int(c.id),c.specialData,c.cost,c.costForTurn,c.upgraded,c.freeToPlayOnce,c.retain,c.forcedExhaust,b.curCardQueueItem.exhaustOnUse}));
        if(b.cardSelectInfo.cardSelectTask==CardSelectTask::OMNISCIENCE &&
                (b.curCardQueueItem.purgeOnUse || !b.cardQueue.isEmpty())) {
            Json queue=Json::array();
            for(const auto &row:b.cardQueue.orderedItems()) {
                const auto &card=row.card;
                queue.push_back(Json::array({int(card.id),card.specialData,card.cost,card.costForTurn,card.upgraded,
                    card.freeToPlayOnce,card.retain,card.forcedExhaust,row.exhaustOnUse,row.purgeOnUse,row.energyOnUse}));
            }
            key.push_back(Json::array({"omniscience-queue-v1",b.curCardQueueItem.purgeOnUse,queue}));
        }
    }
    const auto &s=b.cardSelectInfo;
    if(s.sourceDraw || s.hexDazed || s.inkDraw)
        key.push_back(Json::array({"selection-skill-followup-v1",s.sourceDraw,s.hexDazed,s.inkDraw}));
    if(s.letterOpener)key.push_back(Json::array({"selection-letter-opener-v1",5}));
    if(s.juggernautDamage>0)key.push_back(Json::array({"selection-juggernaut-v1",s.juggernautDamage}));
    if(b.cardSelectInfo.ornithopterHeal)key.push_back(Json::array({"potion-heal-v1",-1,5}));
    if(b.cardSelectInfo.cardSelectTask==CardSelectTask::HEADBUTT_GRID) {
        const auto &c=b.curCardQueueItem.card;
        key.push_back(Json::array({"headbutt-cleanup-v1",int(c.id),c.specialData,c.cost,c.costForTurn,c.upgraded,
            c.freeToPlayOnce,c.retain,c.forcedExhaust,b.curCardQueueItem.exhaustOnUse,b.curCardQueueItem.purgeOnUse}));
        if(s.sharpHide)key.push_back(Json::array({"headbutt-sharp-hide-v1",s.sharpHide}));
        if(int rage=b.player.getStatus<PS::RAGE>();rage>0)key.push_back(Json::array({"headbutt-rage-block-v1",rage}));
        if(b.player.hasRelic<R::SHURIKEN>() && b.player.attacksPlayedThisTurn>0 && b.player.attacksPlayedThisTurn%3==0)
            key.push_back(Json::array({"headbutt-shuriken-v1",1}));
        if(b.player.hasRelic<R::KUNAI>() && b.player.attacksPlayedThisTurn>0 && b.player.attacksPlayedThisTurn%3==0)
            key.push_back(Json::array({"headbutt-kunai-v1",1}));
        if(b.headbuttTail!=BattleContext::HeadbuttTail::NONE)
            key.push_back(Json::array({"headbutt-monster-tail-v1",int(b.headbuttTail),imported.monsterMap.at(b.curCardQueueItem.target)}));
        if(!b.cardQueue.isEmpty()) {
            const auto q=b.cardQueue.orderedItems().front();
            require(b.cardQueue.size==1 && q.purgeOnUse && q.autoplay && q.ignoreEnergyTotal && !q.randomTarget &&
                    q.target==b.curCardQueueItem.target && q.card.uniqueId==c.uniqueId,"headbutt_unsupported_replay_queue");
            key.push_back(Json::array({"headbutt-double-tap-v1",imported.monsterMap.at(q.target),q.energyOnUse}));
        }
    }
    return key;
}

Json gamblerCardUuids(const BattleContext &b,const std::map<int,Json> &identities) {
    Json result=Json::object();std::set<int> selected;
    if(b.inputState==InputState::CARD_SELECT && isHeadlessSingleHand(b.cardSelectInfo.cardSelectTask))
        for(auto &c:b.cardSelectInfo.hiddenHand)selected.insert(c.uniqueId);
    if(b.inputState==InputState::CARD_SELECT && (isHeadlessMultiHand(b.cardSelectInfo.cardSelectTask) || isHeadlessSingleHand(b.cardSelectInfo.cardSelectTask)))
        for(int i:b.cardSelectInfo.selected)selected.insert(b.cardSelectInfo.options.at(i).uniqueId);
    auto add=[&](const char *pile,const CardInstance &c){
        auto it=identities.find(c.uniqueId);
        if (it==identities.end()) result[pile].push_back(nullptr); // Java allocates a fresh UUID for this generated card.
        else {
            require(it->second.is_string(),"missing_source_card_uuid");
            result[pile].push_back(it->second);
        }
    };
    for(auto pile:{"hand","draw_pile","discard_pile","exhaust_pile"})result[pile]=Json::array();
    for(int i=0;i<b.cards.cardsInHand;++i)if(!selected.count(b.cards.hand[i].uniqueId))add("hand",b.cards.hand[i]);
    for(const auto &c:b.cards.drawPile)add("draw_pile",c);
    for(const auto &c:b.cards.discardPile)add("discard_pile",c);
    for(const auto &c:b.cards.exhaustPile)add("exhaust_pile",c);
    return result;
}

Imported importSnapshot(const Json &s) {
    if(s.value("is_screen_up",false) || s.value("screen_name",std::string("NONE"))!="NONE")return importSelectionSnapshot(s);
    const bool headless=s.contains("_headless_rng");
    if(headless)validateHeadlessRng(s.at("_headless_rng"));
    if(s.value("_engine",std::string())=="headless")require(headless,"headless_rng_missing");
    if(headless)require(s.at("act_num").is_number_integer() && s.at("act_num").get<int>()>=1 && s.at("act_num").get<int>()<=4,"headless_act");
    runtimeCombatPools=RuntimeCombatPools{};
    runtimeCombatPools.strict=true;
    if(s.contains("_combat_card_pools")) {
        const auto &pools=s.at("_combat_card_pools");
        auto read=[&](const char *name,std::vector<CardId> &out) {
            const auto &list=pools.at(name);
            require(list.is_array() && !list.empty() && list.size()<=1000,"runtime_card_pool_invalid");
            for(const auto &id:list) {
                auto mapped=getCardIdFromId(id.get<std::string>());
                require(mapped!=CardId::INVALID,"runtime_card_pool_unknown:"+id.get<std::string>());
                out.push_back(mapped);
            }
        };
        read("any",runtimeCombatPools.any); read("attacks",runtimeCombatPools.attacks);
        read("skills",runtimeCombatPools.skills); read("powers",runtimeCombatPools.powers);
        read("colorless",runtimeCombatPools.colorless);
        read("common",runtimeCombatPools.common); read("uncommon",runtimeCombatPools.uncommon);
        read("rare",runtimeCombatPools.rare);
        if(pools.contains("live_common"))read("live_common",runtimeCombatPools.liveCommon);
        else if(headless)runtimeCombatPools.liveCommon.assign(runtimeCombatPools.common.rbegin(),runtimeCombatPools.common.rend());
        const char *names[]={"library_attacks_common","library_attacks_uncommon","library_attacks_rare"};
        for(int i=0;i<3;++i)if(pools.contains(names[i]))read(names[i],runtimeCombatPools.libraryAttacks[i]);
    }
    const auto &p=s.at("player_state"), &pc=p.at("creature"), &room=s.at("cur_map_node_state");
    auto role=p.at("chosen_class_name").get<std::string>();
    require(role=="IRONCLAD" || role=="DEFECT" || role=="THE_SILENT" || role=="WATCHER","character:"+role);
    require(s.at("screen_name")=="NONE" && !s.at("is_screen_up").get<bool>(),"selection_screen");
    require(room.at("phase_name")=="COMBAT","not_combat");
    require(p.at("card_in_use").is_null() && p.at("limbo").empty(),"card_in_flight");
    require(!p.value("is_ending_turn",false) && !s.value("end_turn_queued",false),"ending_turn");
    require(pc.at("current_health").get<int>()>0,"terminal_player");
    require(p.at("hand").size()<=10 && p.at("potions").size()<=5,"capacity");
    require(s.at("turn").get<int>()>=1,"invalid_turn");
    Json g={{"seed",s.at("rng_state").at("seed")},{"ascension_level",s.at("ascension_level")},
        {"act",s.at("floor_num").get<int>()>51?4:s.at("floor_num").get<int>()>34?3:s.at("floor_num").get<int>()>17?2:1},
        {"floor",s.at("floor_num")},{"class",role=="THE_SILENT"?"SILENT":role},{"current_hp",pc.at("current_health")},
        {"max_hp",pc.at("max_health")},{"gold",pc.at("gold")}};
    if(headless)g["act"]=s.at("act_num");
    g["relics"]=Json::array();
    unsigned orangeBits=0;
    for(const auto &r:p.at("relics")) {
        auto id=r.at("relic_id").get<std::string>();
        require(getRelicFromId(id)!=RelicId::INVALID,"unknown_relic:"+id);
        static const std::set<std::string> privateRelics={"Necronomicon","OrangePellets","Emotion Chip","Centennial Puzzle"};
        if(headless && id=="Centennial Puzzle") {
            const auto &used=r.at("_private").at("CentennialPuzzle.usedThisCombat");
            require(used.is_boolean(),"invalid_centennial_puzzle_latch");
        } else if(headless && id=="Necronomicon") {
            require(r.at("_private").at("Necronomicon.activated").is_boolean(),"invalid_necronomicon_latch");
        } else if(headless && id=="Unceasing Top") {
            const auto &latches=r.at("_private");
            require(latches.at("UnceasingTop.canDraw")==true && latches.at("UnceasingTop.disabledUntilEndOfTurn")==false,
                "unmapped_unceasing_top_latch");
        } else if(headless && id=="OrangePellets") {
            for(const auto &entry:{std::pair{"OrangePellets.ATTACK",CardType::ATTACK},std::pair{"OrangePellets.SKILL",CardType::SKILL},std::pair{"OrangePellets.POWER",CardType::POWER}}) {
                const auto &facts=r.at("_private");
                require(facts.contains(entry.first) && facts.at(entry.first).is_boolean(),"invalid_orange_pellets_latch");
                if(facts.at(entry.first).get<bool>())orangeBits|=1u<<static_cast<unsigned>(entry.second);
            }
            require(orangeBits!=7,"unsettled_orange_pellets_latch");
        } else require(!privateRelics.count(id),"relic_private_state:"+id);
        g["relics"].push_back({{"id",id},{"counter",r.at("counter")}});
    }
    g["potions"]=p.at("potions");
    for(const auto &v:g["potions"]) require(getPotionFromId(v.at("id").get<std::string>())!=Potion::INVALID,"unknown_potion");
    Json combat={{"turn",s.at("turn")},{"cards_discarded_this_turn",s.at("total_discarded_this_turn")},
        {"player",{{"energy",p.at("energy_panel_total_energy")},{"block",pc.at("current_block")},
        {"powers",convertPowers(pc.at("powers"),true,"",headless)},{"orb_slots",p.at("max_orbs")},{"orbs",Json::array()}}}};
    combat["times_damaged"]=p.value("damaged_this_combat",0);
    if(headless)for(const auto &r:p.at("relics"))if(r.at("relic_id")=="Centennial Puzzle")
        combat["centennial_puzzle_used_this_combat"]=r.at("_private").at("CentennialPuzzle.usedThisCombat");
    for(auto name:{"draw_pile","discard_pile","hand","exhaust_pile"}) {
        combat[name]=Json::array(); for(const auto &c:p.at(name)) combat[name].push_back(convertCard(c));
    }
    int lightning=0,frost=0;
    for(const auto &o:p.at("orbs_channeled_this_combat")) {
        lightning+=o.at("lookup_key")=="Lightning"; frost+=o.at("lookup_key")=="Frost";
    }
    combat["lightning_channeled_this_combat"]=lightning; combat["frost_channeled_this_combat"]=frost;
    combat["emotion_chip_pending"]=false; // Emotion Chip is explicitly gated above.
    int powers=0,attacks=0,skills=0;
    auto history=s.value("cards_played_this_turn_backup",Json::array());
    require(s.at("turn")==1 || (s.contains("cards_played_this_turn_backup") && s.contains("cards_played_this_turn")),"missing_combat_history");
    // This concatenation is the SaveState.getAllCards identity-index contract.
    Json allCards=Json::array();
    for(auto pile:{"master_deck","draw_pile","hand","discard_pile","exhaust_pile","limbo"})
        for(const auto &c:p.value(pile,Json::array())) allCards.push_back(c);
    auto indexedCard=[&](int index)->const Json & {
        require(index>=0 && index<int(allCards.size()),"combat_history_index"); return allCards.at(index);
    };
    for(const auto &index:s.value("cards_played_this_turn",Json::array())) {
        require(index.is_number_integer(),"combat_history_index_schema");
        history.push_back(indexedCard(index.get<int>()));
    }
    for(const auto &c:history) {
        auto type=getCardType(getCardIdFromId(c.at("card_id").get<std::string>()));
        attacks+=type==CardType::ATTACK; skills+=type==CardType::SKILL;
    }
    // Container-based full-combat history is not reconstructed by guessing.
    CardType lastCombatCardType=CardType::INVALID;
    if(headless)require(s.contains("cards_played_this_combat") && s.at("cards_played_this_combat").is_array(),"missing_full_combat_history");
    for(const auto &entry:s.value("cards_played_this_combat",Json::array())) {
        require(entry.is_object() && entry.contains("card_state"),"combat_history_schema");
        const auto &card=entry.at("card_state").is_null()?indexedCard(entry.at("card_index").get<int>()):entry.at("card_state");
        const auto id=getCardIdFromId(card.at("card_id").get<std::string>());
        if(headless)require(id!=CardId::INVALID,"unknown_full_combat_history_card");
        lastCombatCardType=getCardType(id);
        powers+=lastCombatCardType==CardType::POWER;
    }
    combat["cards_played_this_turn"]=history.size(); combat["attacks_played_this_turn"]=attacks;
    combat["skills_played_this_turn"]=skills; combat["powers_played_this_combat"]=powers;
    for(const auto &o:p.at("orbs")) {
        Json v=o; v["id"]=o.at("lookup_key"); combat["player"]["orbs"].push_back(v);
    }
    combat["monsters"]=Json::array();
    for(const auto &m:room.at("monster_data")) {
        const auto &c=m.at("creature"); auto id=c.at("id").get<std::string>();
        require(getMonsterIdFromId(id)!=MonsterId::INVALID,"unknown_monster:"+id);
        // SaveState keeps dead/escaped monsters in their original slots. Import
        // them too: HP and escape state disable targeting/turns in the engine,
        // while retaining the slots preserves target indices and projections.
        Json v={{"id",id},{"current_hp",c.at("current_health")},{"max_hp",c.at("max_health")},
            {"block",c.at("current_block")},{"is_escaping",c.at("is_escaping").get<bool>() || m.value("escaped",false)},
            {"half_dead",c.at("half_dead")},{"move_id",m.at("next_move")},
            {"intent",m.at("intent_name")},{"powers",Json::array()}};
        for(auto name:{"move_base_damage","move_adjusted_damage","active_orbs"})if(m.contains(name))v[name]=m.at(name);
        const auto &mh=m.at("move_history");
        if(headless && (id=="Looter" || id=="Mugger")) {
            const auto &value=m.at("_private").at(id+".stolenGold");
            require(value.is_number_integer() && value.get<int>()>=0,"invalid_thief_stolen_gold");
            v["miscInt"]=value;
        }
        if (headless && (id=="FuzzyLouseNormal" || id=="FuzzyLouseDefensive")) {
            const auto &bite=m.at("_private").at(id.substr(5)+".biteDamage");
            const int lo=s.at("ascension_level").get<int>()>=2 ? 6 : 5;
            require(bite.is_number_integer() && bite.get<int>()>=lo && bite.get<int>()<=lo+2, "invalid_louse_bite");
            require(m.at("next_move")!=3 || m.value("move_base_damage",-1)==bite.get<int>(), "conflicting_louse_intent");
            v["miscInt"]=bite;
        }
        if(headless && id=="TheGuardian")v["miscInt"]=m.at("_private").at("TheGuardian.dmgThreshold");
        if(headless && id=="Maw") {
            const auto &p=m.at("_private");
            const auto &count=p.at("Maw.turnCount"),&roared=p.at("Maw.roared");
            require(count.is_number_integer() && count>=2 && count<=1000000 && roared.is_boolean(),
                    "invalid_maw_turn_state");
            const int asc=s.at("ascension_level").get<int>();
            require(p.at("Maw.slamDmg")== (asc>=2?30:25) && p.at("Maw.nomDmg")==5 &&
                    p.at("Maw.strUp")== (asc>=17?5:3) && p.at("Maw.terrifyDur")== (asc>=17?5:3),
                    "invalid_maw_constants");
            if(c.at("current_health")>0)
                require(roared.get<bool>()==(m.at("next_move")!=2) &&
                        (m.at("next_move")!=5 || m.at("move_hits")==count.get<int>()/2),
                        "conflicting_maw_intent");
            // High bits store the Java roll count; the low bit remembers Roar.
            v["miscInt"]=(count.get<int>()<<1)|int(roared.get<bool>());
        }
        if(headless && id=="WrithingMass") {
            const auto &p=m.at("_private");
            require(p.at("WrithingMass.firstMove")==false && p.at("WrithingMass.normalDebuffAmt")==2 &&
                    p.at("WrithingMass.usedMegaDebuff").is_boolean(),"invalid_writhing_mass_state");
            v["miscInt"]=int(p.at("WrithingMass.usedMegaDebuff").get<bool>());
        }
        if(headless && id=="AwakenedOne") {
            const auto &p=m.at("_private");
            const auto &form1=p.at("AwakenedOne.form1"),&first=p.at("AwakenedOne.firstTurn");
            require(form1.is_boolean() && first.is_boolean(),"invalid_awakened_form_state");
            if(c.at("current_health")>0 || c.at("half_dead")==true) {
                const int move=m.at("next_move").get<int>();
                const bool hasMarker=std::any_of(c.at("powers").begin(),c.at("powers").end(),
                    [](const Json &p){return p.at("power_id")=="Unawakened";});
                require(hasMarker==form1.get<bool>() &&
                        (form1==true ? (!first.get<bool>() && (move==1 || move==2)) :
                         (first==true ? (move==3 || move==5) : (move==6 || move==8))) &&
                        (c.at("half_dead")==true)==(move==3),"conflicting_awakened_intent");
            }
            // Low bit denotes the source's second form, including pending
            // rebirth. Bit 1 preserves its first Dark Echo roll.
            v["miscInt"]=int(!form1.get<bool>())|(first.get<bool>()?2:0);
        }
        if(headless && id=="Nemesis") {
            const auto &facts=m.at("_private"), &cooldown=facts.at("Nemesis.scytheCooldown");
            require(cooldown.is_number_integer() && cooldown>=-1000000 && cooldown<=2 &&
                    facts.at("Nemesis.firstMove")==false &&
                    facts.at("Nemesis.fireDmg")== (s.at("ascension_level").get<int>()>=3?7:6),"invalid_nemesis_state");
            v["miscInt"]=cooldown;
        }
        if(headless && id=="GiantHead") {
            const auto &facts=m.at("_private"), &count=facts.at("GiantHead.count");
            const int base=s.at("ascension_level").get<int>()>=3 ? 40 : 30;
            require(count.is_number_integer() && count>=-6 && count<=4 &&
                    facts.at("GiantHead.startingDeathDmg")==base,"invalid_giant_head_counter");
            if(c.at("current_health")>0) {
                const int move=m.at("next_move").get<int>();
                require((move==2 && count<=0 && m.at("move_base_damage")==base-5*count.get<int>()) ||
                        ((move==1 || move==3) && count>0),"conflicting_giant_head_intent");
            }
            v["miscInt"]=count;
        }
        if(headless && id=="Transient") {
            const auto &facts=m.at("_private");
            const auto &count=facts.at("Transient.count");
            const int base=s.at("ascension_level").get<int>()>=2 ? 40 : 30;
            require(count.is_number_integer() && count>=0 && count<=6 &&
                    facts.at("Transient.startingDeathDmg")==base,"invalid_transient_counter");
            if (c.at("current_health")>0)
                require(m.at("next_move")==1 && m.at("move_base_damage")==base+10*count.get<int>(),
                        "conflicting_transient_intent");
            v["miscInt"]=count;
        }
        if(headless && id=="Darkling") {
            const auto &state=m.at("_private");
            const auto &nip=state.at("Darkling.nipDmg");
            const int offset=s.at("ascension_level").get<int>()>=2 ? 2 : 0;
            require(nip.is_number_integer() && nip.get<int>()>=7+offset && nip.get<int>()<=11+offset,
                    "invalid_darkling_nip");
            require(m.at("next_move")!=3 || m.value("move_base_damage",-1)==nip.get<int>(),
                    "conflicting_darkling_nip_intent");
            // The same fixed Nip damage survives harden, knockdown and regrowth.
            v["miscInt"]=nip.get<int>()-offset;
        }
        if(headless && (id=="Exploder" || id=="Spiker")) {
            const char *field=id=="Exploder"?"Exploder.turnCount":"Spiker.thornsCount";
            const auto &count=m.at("_private").at(field);
            const int limit=id=="Exploder"?3:6;
            require(count.is_number_integer() && count>=0 && count<=limit,"invalid_shape_counter:"+id);
            v["miscInt"]=count;
            if(id=="Exploder" && c.at("current_health")>0) {
                int matches=0;
                for (const auto &power:c.at("powers")) if(power.at("power_id")=="Explosive") {
                    ++matches;
                    require(power.at("amount")==3-count.get<int>(),"conflicting_explosive_counter");
                }
                require(matches==1 && m.at("next_move")== (count<2?1:2),"invalid_exploder_move");
            }
        }
        if(mh.size()>1) v["last_move_id"]=mh[mh.size()-2].is_string()?std::stoi(mh[mh.size()-2].get<std::string>()):mh[mh.size()-2].get<int>();
        combat["monsters"].push_back(v);
    }
    Imported result;
    result.roster.prepare(s,combat["monsters"]);
    for(std::size_t i=0;i<combat["monsters"].size();++i)if(!combat["monsters"][i].at("is_gone").get<bool>()) {
        const auto &c=room.at("monster_data")[i].at("creature");
        combat["monsters"][i]["powers"]=convertPowers(c.at("powers"),false,c.at("id").get<std::string>(),headless);
    }
    g["combat_state"]=combat;
    BattleConverter converter; result.battle=converter.convertFromJson({{"game_state",g}},result.monsterMap.data(),headless);
    auto &bc=result.battle;
    if(headless)bc.player.orangePelletsCardTypesPlayed=orangeBits;
    if(headless) {
        for(const auto &relic:p.at("relics")) if(relic.at("relic_id")=="Pen Nib") {
            const auto &counter=relic.at("counter");
            require(counter.is_number_integer() && counter.get<int>()>=0 && counter.get<int>()<=9,"invalid_pen_nib_counter");
            if(counter.get<int>()==9) {
                bool armed=false;
                for(const auto &power:pc.at("powers"))
                    if(power.at("power_id")=="Pen Nib" && power.at("amount")==1)armed=true;
                require(armed,"pen_nib_armed_power_missing");
                // Native uses -1 as the armed sentinel; Java exports counter 9.
                bc.player.penNibCounter=-1;
            }
        }
        std::array<int,3> bombs{};std::set<std::string> identities;
        for(const auto &power:pc.at("powers")) {
            const auto id=power.at("power_id").get<std::string>();
            if(id.rfind("TheBomb",0)!=0 || id=="TheBomb")continue;
            const auto [turns,damage]=headlessBomb(power);
            require(identities.insert(id).second,"duplicate_headless_bomb_id");
            // Existing native buckets combine same-timer damage into one hit.
            // Preserve exact single-instance semantics instead of accepting that
            // approximation for Buffer/Intangible/Invincible interactions.
            require(bombs[turns-1]==0,"multiple_headless_bombs_same_timer");
            bombs[turns-1]=damage;
        }
        bc.player.bomb1=bombs[0];bc.player.bomb2=bombs[1];bc.player.bomb3=bombs[2];
    }
    if(headless)for(const auto &monster:combat["monsters"])
        if((monster.at("id")=="Looter" || monster.at("id")=="Mugger") && monster.value("miscInt",0)>0)bc.setRequiresStolenGoldCheck(true);
    if(headless)for(int slot=0;slot<bc.monsters.monsterCount;++slot)
        if(bc.monsters.arr[slot].id==MonsterId::HEXAGHOST)bc.monsters.arr[slot].uniquePower1=room.at("monster_data").at(result.monsterMap[slot]).at("burn_upgraded").get<bool>();
    if(headless)for(int slot=0;slot<bc.monsters.monsterCount;++slot)
        if(bc.monsters.arr[slot].id==MonsterId::GREMLIN_LEADER)
            bc.monsters.arr[slot].buff<MS::MINION_LEADER>(); // Vanilla die() orders all remaining minions to flee.
    if(headless)for(int slot=0;slot<bc.monsters.monsterCount;++slot) {
        auto &m=bc.monsters.arr[slot];
        if(m.id==MonsterId::AWAKENED_ONE && (m.miscInfo&1) && !m.halfDead)
            m.buff<MS::MINION_LEADER>();
    }
      if(headless)for(int slot=0;slot<bc.monsters.monsterCount;++slot)
         if(bc.monsters.arr[slot].id==MonsterId::GREMLIN_WIZARD) {
            const auto &charge=room.at("monster_data").at(result.monsterMap[slot]).at("_private").at("GremlinWizard.currentCharge");
            require(charge.is_number_integer() && charge.get<int>()>=0 && charge.get<int>()<=3,"invalid_wizard_charge");
            bc.monsters.arr[slot].miscInfo=charge.get<int>();
         }
    if(headless)for(int slot=0;slot<bc.monsters.monsterCount;++slot)
        if(bc.monsters.arr[slot].id==MonsterId::RED_SLAVER) {
            const auto &used=room.at("monster_data").at(result.monsterMap[slot]).at("_private").at("SlaverRed.usedEntangle");
            require(used.is_boolean(),"invalid_red_slaver_entangle");
            bc.monsters.arr[slot].miscInfo=used.get<bool>() ? 1 : 0;
        }
    if(headless)for(int slot=0;slot<bc.monsters.monsterCount;++slot)
        if(bc.monsters.arr[slot].id==MonsterId::BOOK_OF_STABBING) {
            // The source increments this when selecting a move, not only when
            // executing Multi Stab. A Single Stab and truncated move history
            // cannot reconstruct it, even when current intent hit count exists.
            const auto &count=room.at("monster_data").at(result.monsterMap[slot]).at("_private").at("BookOfStabbing.stabCount");
            require(count.is_number_integer() && count.get<int>()>=1,"invalid_book_stab_count");
            bc.monsters.arr[slot].miscInfo=count.get<int>();
        }
    if(headless)for(int slot=0;slot<bc.monsters.monsterCount;++slot)
        if(bc.monsters.arr[slot].id==MonsterId::THE_CHAMP) {
            const auto &privateState=room.at("monster_data").at(result.monsterMap[slot]).at("_private");
            const auto &forges=privateState.at("Champ.forgeTimes"), &turns=privateState.at("Champ.numTurns");
            const auto &threshold=privateState.at("Champ.thresholdReached");
            require(forges.is_number_integer() && forges.get<int>()>=0 && forges.get<int>()<=2,"invalid_champ_forge_count");
            require(turns.is_number_integer() && turns.get<int>()>=0 && turns.get<int>()<=1000000,"invalid_champ_turn_count");
            require(threshold.is_boolean(),"invalid_champ_threshold");
            require(privateState.at("Champ.forgeThreshold")==2,"invalid_champ_forge_threshold");
            // Low two bits retain the native forge counter and bit 2 the
            // phase latch. Headless additionally preserves the source's
            // independent getMove counter, including its Taunt reset.
            bc.monsters.arr[slot].miscInfo=forges.get<int>() | (threshold.get<bool>()?4:0) | (turns.get<int>()<<3);
        }
    auto stance=p.value("stance",std::string("Neutral"));
    require(stance=="Neutral" || stance=="Wrath" || stance=="Calm" || stance=="Divinity","unknown_stance:"+stance);
    bc.player.stance=stance=="Wrath"?Stance::WRATH:stance=="Calm"?Stance::CALM:stance=="Divinity"?Stance::DIVINITY:Stance::NEUTRAL;
    bc.mantraGained=s.value("mantra_gained",0);
    if(headless)bc.lastCardType=lastCombatCardType;
    else if(!history.empty())bc.lastCardType=getCardType(getCardIdFromId(history.back().at("card_id")));
    const auto &rng=s.at("rng_state"); auto seed=rng.at("seed").get<std::uint64_t>();
    auto floorSeed=seed+s.at("floor_num").get<int>();
    auto restore=[&](const char *name,std::uint64_t base) {
        int n=rng.at(name).get<int>(); require(n>=0 && n<=10000000,"rng_counter_range");
        if(headless) {
            const std::string key(name);return exactRandom(s.at("_headless_rng"),key.substr(0,key.size()-12).c_str(),n);
        }
        return Random(base,n);
    };
    bc.aiRng=restore(rngNames[0],floorSeed); bc.cardRandomRng=restore(rngNames[1],floorSeed);
    bc.miscRng=restore(rngNames[2],floorSeed); bc.monsterHpRng=restore(rngNames[3],floorSeed);
    bc.potionRng=restore(rngNames[4],seed); bc.shuffleRng=restore(rngNames[5],floorSeed);
    bc.cardRngKnown=rng.contains("card_rng_counter");
    if(bc.cardRngKnown)bc.cardRng=restore("card_rng_counter",seed);
    bc.headlessRngKnown=headless;
    if (headless) {
        size_t i=0;
        for (const auto &relic:p.at("relics")) {
            const auto &id=relic.at("relic_id");
            for (const auto kind:{R::SUNDIAL,R::MELANGE,R::THE_ABACUS}) {
                if (id!=relicIds[static_cast<int>(kind)]) continue;
                require(i<bc.headlessShuffleRelics.size() &&
                    std::find(bc.headlessShuffleRelics.begin(),bc.headlessShuffleRelics.end(),kind)==bc.headlessShuffleRelics.end(),
                    "duplicate_shuffle_relic");
                bc.headlessShuffleRelics[i++]=kind;
                break;
            }
        }
    }
    if(headless)for(const auto &relic:p.at("relics"))if(relic.at("relic_id")=="Necronomicon")
        bc.player.haveUsedNecronomiconThisTurn=!relic.at("_private").at("Necronomicon.activated").get<bool>();
    if(headless)for(const auto &relic:p.at("relics"))if(relic.at("relic_id")=="Omamori") {
        const auto &charges=relic.at("counter");
        require(charges.is_number_integer() && charges>=0 && charges<=2,"invalid_omamori_charges");
        bc.player.omamoriCounter=charges.get<int>();
        bc.player.setHasRelic<R::OMAMORI>(bc.player.omamoriCounter>0);
    }
    bc.headlessSettledExhaust=headless && s.value("_settled_exhaust_costs",false);
    // Legacy snapshots could stop with ExhaustCardEffect still running.
    // The card need not have innate Exhaust (Corruption), and opening a grid
    // can leave an earlier played card's animation pending until confirmation.
    if (headless && !bc.headlessSettledExhaust) {
        std::set<std::pair<std::string,std::string>> playedIds;
        for(const auto &played:s.at("cards_played_this_combat")) {
            const auto &card=played.at("card_state").is_null()?indexedCard(played.at("card_index")):played.at("card_state");
            const auto id=card.value("uuid",std::string());
            if(!id.empty())playedIds.emplace(id,card.at("card_id"));
        }
        for (std::size_t i=0;i<p.at("exhaust_pile").size();++i) {
            const auto &raw=p.at("exhaust_pile")[i];
            if (raw.at("cost")!=raw.at("cost_for_turn") &&
                playedIds.count({raw.value("uuid",std::string()),raw.at("card_id").get<std::string>()}))
                bc.headlessExhaustEffects.push_back(bc.cards.exhaustPile[i].uniqueId);
        }
    }
    if(headless)bc.headlessRng=exactRandom(s.at("_headless_rng"),"headless",rng.at("headless_rng_counter").get<int>());
    bc.masterDeckKnown=p.contains("master_deck") && p.at("master_deck").is_array();bc.masterDeck.clear();
    if(bc.masterDeckKnown)for(const auto &card:p.at("master_deck"))bc.masterDeck.push_back(importCombatCard(convertCard(card),int(bc.masterDeck.size())));
    for(const auto &power:pc.at("powers"))if(power.at("power_id")=="Night Terror") {
        require(power.contains("card") && power.at("card").is_object(),"missing_power_state:Nightmare");
        bc.nightmareCard=importCombatCard(convertCard(power.at("card")),-1);
    }
    bc.player.energyPerTurn=p.at("energy_manager_energy").get<int>();
    bc.player.cardDrawPerTurn=p.at("game_hand_size").get<int>();
    restoreCards(bc,p);
    if(headless && bc.masterDeckKnown)for(std::size_t j=0;j<bc.masterDeck.size();++j) {
        auto &master=bc.masterDeck[j];
        if(master.id!=CardId::RITUAL_DAGGER && master.id!=CardId::GENETIC_ALGORITHM)continue;
        const auto &uuid=p.at("master_deck").at(j).at("uuid");
        require(uuid.is_string() && !uuid.get_ref<const std::string&>().empty(),"missing_permanent_card_uuid");
        master.uniqueId=-1;
        auto bind=[&](const CardInstance &card,const Json &raw) {
            if(card.id!=master.id || raw.at("uuid")!=uuid)return;
            require(master.uniqueId==-1,"ambiguous_permanent_card_uuid");
            master.uniqueId=card.uniqueId;
        };
        for(int i=0;i<bc.cards.cardsInHand;++i)bind(bc.cards.hand[i],p.at("hand").at(i));
        std::size_t i=0;for(const auto &card:bc.cards.drawPile)bind(card,p.at("draw_pile").at(i++));
        i=0;for(const auto &card:bc.cards.discardPile)bind(card,p.at("discard_pile").at(i++));
        i=0;for(const auto &card:bc.cards.exhaustPile)bind(card,p.at("exhaust_pile").at(i++));
    }
    require(bc.inputState==InputState::PLAYER_NORMAL,"import_not_at_decision");
    result.before=projection(s);
    require(result.before==projection(result),"import_projection_mismatch");
    return result;
}

Json Session::search(const Json &request) {
    auto &cache=*state;
    if(request.value("operation",std::string())=="release_session") {
        if(cache.owner==request.value("session_id",std::string()))cache=Cache{};
        return {{"supported",true}};
    }
    const int segment=request.value("segment_length",1);
    require(segment==1 || segment==2 || segment==4,"invalid_segment_length");
    auto start=std::chrono::steady_clock::now();
    auto imported=importSnapshot(request.at("snapshot"));
    auto importedAt=std::chrono::steady_clock::now();
    auto &bc=imported.battle;
    const bool headless=request.at("snapshot").contains("_headless_rng");
    if(request.value("operation","")=="fingerprint")return {{"supported",true},{"native_key",stateKey(bc,&imported.monsterMap)},
        {"path_context",layeredPathContext(request.at("snapshot"),imported.monsterMap,headless)},{"projection",projection(imported)},{"exact_rng",exactRngView(bc)},{"selection",gamblerSelection(bc,imported.selectionIdentities,imported.monsterMap)},
        {"selection_native_key",gamblerGuardKey(imported)},
        {"rng_restore_mode",headless?"exact_state":"legacy_counter"}};
    if(request.value("operation","")=="import") return {{"supported",true},{"projection",imported.before},
        {"mechanics",{{"cards_played",bc.player.cardsPlayedThisTurn},{"attacks_played",bc.player.attacksPlayedThisTurn},
        {"skills_played",bc.player.skillsPlayedThisTurn},{"powers_played",bc.powersPlayedThisCombat}}}};
    if(request.value("operation","")=="rng_probe") {
        Json samples=Json::array();
        for(auto rng:{bc.aiRng,bc.cardRandomRng,bc.miscRng,bc.monsterHpRng,bc.potionRng,bc.shuffleRng}) {
            Json row=Json::array(); for(int i=0;i<16;++i) row.push_back(rng.random(999)); samples.push_back(row);
        }
        return {{"supported",true},{"samples",samples},{"exact_rng",exactRngView(bc)},
            {"rng_restore_mode",headless?"exact_state":"legacy_counter"}};
    }
    if(request.value("operation","")=="evaluate") {
        auto score=evaluate(bc,bc);return {{"supported",true},{"evaluation",score.json()},{"mechanism_rules",score.mechanismRules}};
    }
    if(request.value("operation","")=="strategy") {
        std::vector<search::Action> actions;search::ActionEnumerationOptions options;
        search::enumerateActions(bc,options,true,actions);
        Json ranks=Json::array();
        const bool layered=request.value("search_policy",std::string("legacy"))=="layered-v1";
        for(const auto &a:actions)ranks.push_back({{"type",int(a.getActionType())},{"source",a.getSourceIdx()},
            {"target",a.getTargetIdx()},{"priority",layered?layeredPriority(bc,a):priority(bc,a)}});
        Json selections=Json::array();
        for(int i=0;i<bc.cards.cardsInHand;++i){Json scores=Json::array();
            for(auto purpose:{search::SelectionPurpose::DISCARD,search::SelectionPurpose::EXHAUST,
                search::SelectionPurpose::SCRY,search::SelectionPurpose::SEEK,search::SelectionPurpose::RETAIN})
                scores.push_back(search::characterSelectionPriority(bc,bc.cards.hand[i],purpose));
            selections.push_back(scores);}
        return {{"supported",true},{"actions",ranks},{"selections",selections},{"mechanism_rules",search::characterMechanisms(bc).rules}};
    }
    if(request.value("operation","")=="step" || request.value("operation","")=="sequence") {
        auto run=[&](const Json &req) {
        auto kind=req.value("action_type",std::string("CARD"));
        require(kind=="CARD" || kind=="END" || kind=="POTION" || kind=="SELECT","invalid_step_type");
        auto type=kind=="SELECT"?search::ActionType::SINGLE_CARD_SELECT:kind=="END"?search::ActionType::END_TURN:kind=="POTION"?search::ActionType::POTION:search::ActionType::CARD;
        int index=kind=="CARD"?req.at("card_index").get<int>():kind=="SELECT"?req.at("select_index").get<int>():req.value("potion_index",0);
        search::Action action(type,index,req.value("monster_index",0));
        require(action.isValidAction(bc),"invalid_step_action");executeImported(imported,action);
        };
        ExecutionBudget stepBudget(std::chrono::steady_clock::now()+std::chrono::milliseconds(std::clamp(request.value("timeout_ms",1000),1,90000)));
        if(request.value("operation","")=="sequence") {
            const auto &list=request.at("actions");require(list.is_array() && list.size()<=1024,"sequence_capacity");
            for(const auto &a:list)run(a);
        } else run(request);
        Json selection={{"task",cardSelectTaskStrings[int(bc.cardSelectInfo.cardSelectTask)]},
            {"count",bc.cardSelectInfo.pickCount},{"selected",bc.cardSelectInfo.selected},{"options",Json::array()}};
        for(const auto &c:bc.cardSelectInfo.options)selection["options"].push_back(cardView(c));

        Json powers=Json::object();for(const auto &[p,n]:bc.player.statusMap)if(bc.player.hasStatusRuntime(p))powers[playerStatusIds[int(p)]]=n;
        return {{"supported",true},{"projection",projection(imported)},{"powers",powers},{"selection",selection},{"outcome",int(bc.outcome)},{"master_deck",cards(bc.masterDeck)},{"card_rng_counter",bc.cardRngKnown?Json(bc.cardRng.counter):Json()},
            {"player_stats",{{"strength",bc.player.strength},{"dexterity",bc.player.dexterity},{"focus",bc.player.focus},{"artifact",bc.player.artifact},{"gold",bc.player.gold}}},
            {"stance",int(bc.player.stance)},{"enemy_poison",bc.monsters.arr[0].poison},{"enemy_weak",bc.monsters.arr[0].weak},
            {"enemy_strength",bc.monsters.arr[0].strength}};
    }
    const auto &snapshot=request.at("snapshot");
    const auto &player=snapshot.at("player_state");
    auto identity=[&](const BattleContext &b,const std::map<int,Json> &ids) {
        Json result=Json::array();bool valid=true;
        auto add=[&](const CardInstance &c) {auto it=ids.find(c.uniqueId);if(it==ids.end() || it->second.is_null())valid=false;else result.push_back(it->second);};
        for(int i=0;i<b.cards.cardsInHand;++i)add(b.cards.hand[i]);
        for(const auto *pile:{&b.cards.drawPile,&b.cards.discardPile,&b.cards.exhaustPile})for(const auto &c:*pile)add(c);
        return valid?result:Json();
    };
    std::map<int,Json> currentIds;
    auto remember=[&](const auto &range,const Json &cards) {int i=0;for(const auto &c:range)currentIds[c.uniqueId]=cards.at(i++).value("uuid",Json());};
    if(imported.selectionScreen.empty()) {
        for(int i=0;i<bc.cards.cardsInHand;++i)currentIds[bc.cards.hand[i].uniqueId]=player.at("hand").at(i).value("uuid",Json());
        remember(bc.cards.drawPile,player.at("draw_pile"));remember(bc.cards.discardPile,player.at("discard_pile"));remember(bc.cards.exhaustPile,player.at("exhaust_pile"));
    } else currentIds=imported.selectionIdentities;
    auto owner=request.value("session_id",std::string());auto profile=request.value("search_profile",std::string("BALANCED"));
    auto mode=request.value("search_mode",std::string("turn"));
    const auto policy=request.value("search_policy",std::string("legacy"));
    const auto dedup=request.value("dedup_mode",std::string("default"));
    require(dedup=="default" || (dedup=="new_turn" && policy=="legacy" && mode=="turn"),"unknown_dedup_mode");
    const bool tacticalOrdering=request.value("tactical_ordering",true);
    int cap=std::clamp(request.value("max_expansions",15000),1,1000000);const long requestedBudget=request.value("timeout_ms",2000L);
    const long budget=requestedBudget==0?0:std::clamp(requestedBudget,1L,90000L);
    require(profile=="FAST" || profile=="BALANCED" || profile=="DEEP","unknown_search_profile");
    require(mode=="turn" || mode=="mcts","unknown_search_mode");
    require(policy=="legacy" || (policy=="layered-v1" && mode=="turn"),"unknown_search_policy");
    require(budget!=0 || (policy=="legacy" && mode=="turn"),"unlimited_time_requires_legacy_turn_search");
    auto pools=snapshot.value("_combat_card_pools",Json());auto ids=identity(bc,currentIds);auto key=stateKey(bc);auto attributes=cardAttributes(snapshot);
    const bool layered=policy=="layered-v1";
    const auto pathContext=layered?layeredPathContext(snapshot,imported.monsterMap,headless):Json();
    const bool layeredReuse=layered && request.value("operation",std::string("search"))=="search" &&
        request.value("reuse_path",true) && !pathContext.is_null();
    const bool reusable=layered?layeredReuse:!headless;
    bool hit=reusable && !owner.empty() && cache.owner==owner && cache.profile==profile && cache.mode==mode && cache.policy==policy &&
        cache.dedup==dedup && cache.tacticalOrdering==tacticalOrdering && cache.pools==pools && cache.cap==cap && cache.budget==budget &&
        !key.is_null() && key==cache.expected && !ids.is_null() && ids==cache.identities && cache.attributes==attributes && cache.offset<cache.path.size();
    if(hit && layered)hit=cache.pathContext==pathContext && cache.expectedProjection==imported.before &&
        cache.path[cache.offset].isValidAction(bc);
    Json cacheDiff;
    if(request.value("debug_keys",false) && cache.owner==owner)cacheDiff={{"state",Json::diff(cache.expected,key)},{"identity",Json::diff(cache.identities,ids)}};
    int expansions=0,duplicates=0;std::string stop="PATH_CACHE";
    TurnSearchResult effort;
    if(!hit) {
        cache=Cache{};cache.owner=owner;cache.profile=profile;cache.mode=mode;cache.pools=pools;cache.attributes=attributes;
        cache.policy=policy;cache.dedup=dedup;cache.tacticalOrdering=tacticalOrdering;
        cache.cap=cap;cache.budget=budget;
        if(request.value("operation",std::string())=="predict") {
            const auto &forced=request.at("command");const std::string type=forced.at("type");
            const bool grid=type=="GRID_SELECT" || type=="GRID_SELECT_CONFIRM";
            const bool selection=type=="HAND_SELECT" || type=="HAND_SELECT_CONFIRM" || grid;
            require(type=="CARD" || type=="POTION" || type=="END" || selection,"prediction_command_type");
            auto kind=selection?search::ActionType::SINGLE_CARD_SELECT:type=="CARD"?search::ActionType::CARD:type=="POTION"?search::ActionType::POTION:search::ActionType::END_TURN;
            int index=type=="CARD"?forced.at("card_index").get<int>():type=="POTION"?forced.at("potion_index").get<int>():0;
            if(selection) {
                require((!headless && isCharacterSelection(bc.cardSelectInfo.cardSelectTask) &&
                        ((imported.selectionScreen=="GRID" && grid) ||
                         (imported.selectionScreen=="HAND_SELECT" && !grid))) || (headless && ((imported.selectionScreen=="HAND_SELECT" && (isHeadlessMultiHand(bc.cardSelectInfo.cardSelectTask) || isHeadlessSingleHand(bc.cardSelectInfo.cardSelectTask)) && !grid)
                     || (imported.selectionScreen=="GRID" && (bc.cardSelectInfo.cardSelectTask==CardSelectTask::LIQUID_MEMORIES_GRID
                          || bc.cardSelectInfo.cardSelectTask==CardSelectTask::HEADBUTT_GRID || isHeadlessFetchGrid(bc.cardSelectInfo.cardSelectTask)) && type=="GRID_SELECT"))),"prediction_selection_source");
                if(type=="HAND_SELECT_CONFIRM" || type=="GRID_SELECT_CONFIRM")index=CHARACTER_SELECT_CONFIRM;
                else {int external=forced.at("card_index");auto it=std::find(imported.selectionIndices.begin(),imported.selectionIndices.end(),external);
                    require(it!=imported.selectionIndices.end(),"prediction_selection_index");index=int(it-imported.selectionIndices.begin());}
            }
            int external=forced.value("monster_index",-1),target=0;
            if(headless && type=="CARD" && external<0 && bc.cards.hand[index].requiresTarget()
                    && snapshot.at("player_state").at("hand").at(index).contains("target_enum_ordinal")) {
                const auto &card=snapshot.at("player_state").at("hand").at(index);
                const int sourceTarget=card.at("target_enum_ordinal");
                require(sourceTarget!=0 && sourceTarget!=4,"prediction_missing_required_target");
                bool found=false;for(int i=0;i<bc.monsters.monsterCount;++i)if(bc.monsters.arr[i].isTargetable()){target=i;found=true;break;}
                require(found,"prediction_no_random_target");
            }
            if(external>=0) {
                auto it=std::find(imported.monsterMap.begin(),imported.monsterMap.end(),external);
                require(it!=imported.monsterMap.end(),"prediction_target_index");target=int(it-imported.monsterMap.begin());
            }
            search::Action chosen(kind,index,target);require(chosen.isValidAction(bc),"invalid_prediction_action");
            cache.path={chosen};stop="PREDICT_ONLY";
        } else if(mode=="mcts") {
            search::BattleScumSearcher2 solver(bc);solver.allowPotions=true;solver.allowRootPotions=true;solver.search(cap,budget);
            cache.path=solver.bestActionSequence;expansions=int(solver.root.simulationCount);stop=solver.stopReason;
        } else {
            effort=policy=="layered-v1"?layeredTurnSearch(bc,cap,budget,tacticalOrdering):turnSearch(bc,cap,budget,profile,dedup=="new_turn");
            cache.path=std::move(effort.path);expansions=effort.expansions;duplicates=effort.duplicates;stop=effort.reason;
        }
    }
    require(cache.offset<cache.path.size(),"search_no_action");
    auto action=cache.path[cache.offset];require(action.isValidAction(bc),"invalid_search_action");
    Json cmd;
    switch(action.getActionType()) {
    case search::ActionType::CARD: {
        auto &c=bc.cards.hand[action.getSourceIdx()];
        bool targeted=c.requiresTarget();
        if(headless && snapshot.at("player_state").at("hand").at(action.getSourceIdx()).contains("target_enum_ordinal")) {
            // Engine-internal target slots also serve random-target attacks.
            // The external driver must follow the actual source CardTarget.
            const int sourceTarget=snapshot.at("player_state").at("hand").at(action.getSourceIdx()).at("target_enum_ordinal");
            require(sourceTarget>=0 && sourceTarget<=5,"source_card_target");targeted=sourceTarget==0 || sourceTarget==4;
        }
        int target=targeted?imported.monsterMap.at(action.getTargetIdx()):-1;
        require(!targeted || target>=0,"unmapped_target");
        cmd={{"type","CARD"},{"card_index",action.getSourceIdx()},{"monster_index",target},{"display_string",c.getName()}};
        break;
    }
    case search::ActionType::POTION: {
        require(action.getTargetIdx()<=5,"potion_discard");
        bool targeted=potionRequiresTarget(bc.potions[action.getSourceIdx()]);
        int target=targeted?imported.monsterMap.at(action.getTargetIdx()):-1;
        require(!targeted || target>=0,"unmapped_target");
        cmd={{"type","POTION"},{"potion_index",action.getSourceIdx()},{"monster_index",target}}; break;
    }
    case search::ActionType::END_TURN: cmd={{"type","END"}}; break;
    case search::ActionType::SINGLE_CARD_SELECT: {
        const auto task=bc.cardSelectInfo.cardSelectTask;
        require(!imported.selectionScreen.empty() &&
                (isCharacterSelection(task) || task==CardSelectTask::DISCOVERY),"selection_action");
        int index=action.getSelectIdx();auto screen=imported.selectionScreen;
        if(index==CHARACTER_SELECT_CONFIRM) {
            if(screen=="CARD_REWARD")cmd={{"type","CARD_REWARD_SELECT"},{"card_index",-1}};
            else cmd={{"type",screen=="GRID"?"GRID_SELECT_CONFIRM":"HAND_SELECT_CONFIRM"}};
        } else {
            int external=imported.selectionIndices.at(index);require(external>=0,"selection_index");
            cmd={{"type",screen=="GRID"?"GRID_SELECT":screen=="HAND_SELECT"?"HAND_SELECT":"CARD_REWARD_SELECT"},{"card_index",external}};
        }
        break;
    }
    default: throw Unsupported("selection_action");
    }
    const auto desktopGrid=[](const BattleContext &b) {
        const auto task=b.cardSelectInfo.cardSelectTask;
        return b.inputState==InputState::CARD_SELECT && (task==CardSelectTask::SCRY || task==CardSelectTask::MEDITATE || task==CardSelectTask::EXHUME || isHeadlessFetchGrid(task));
    };
    const bool verifiesDesktopGrid=!headless && (desktopGrid(bc) ||
        (bc.inputState==InputState::CARD_SELECT && bc.cardSelectInfo.cardSelectTask==CardSelectTask::FORETHOUGHT));
    const bool omniscienceScry=cmd.at("type")=="GRID_SELECT" &&
        bc.cardSelectInfo.cardSelectTask==CardSelectTask::OMNISCIENCE &&
        bc.cardSelectInfo.options.at(action.getSelectIdx()).id==CardId::CUT_THROUGH_FATE;
    auto phase=[](const BattleContext &state){
                Json result=Json::array();
                for(int k=0;k<state.monsters.monsterCount;++k){const auto &m=state.monsters.arr[k];
                    result.push_back({int(m.id),m.curHp>0,m.halfDead,int(m.moveHistory[0]),m.miscInfo});}
                return result;
            };
    const auto firstPhase=segment>1?phase(bc):Json();
    const int beforeTurn=bc.turn;
    executeImported(imported,action);
    ++cache.offset;
    require(!bc.undefinedBehaviorEvoked,"engine_undefined_behavior");
    bool normal=bc.inputState==InputState::PLAYER_NORMAL && bc.outcome==Outcome::UNDECIDED;
    cache.expected=normal && !imported.roster.changed?stateKey(bc):Json();cache.identities=identity(bc,currentIds);
    if(layered) {
        // A suffix may be useful after moves in the same player turn. Never
        // commit the remainder across an opponent turn, selector or spawn.
        const bool retain=layeredReuse && action.getActionType()!=search::ActionType::END_TURN && beforeTurn==bc.turn &&
            imported.selectionScreen.empty() && !cache.expected.is_null() && !cache.identities.is_null() &&
            cache.offset<cache.path.size();
        cache.pathContext=retain?layeredPathContext(snapshot,imported.monsterMap,headless):Json();
        cache.expectedProjection=retain?projection(imported):Json();
    }
    const auto &source=request.at("snapshot"), &sourcePlayer=source.at("player_state");
    Json context={{"seed",source.at("rng_state").at("seed")},{"floor",source.at("floor_num")},
        {"stance",sourcePlayer.value("stance",std::string("Neutral"))},
        {"card_attributes",cardAttributes(source)},
        {"role",sourcePlayer.at("chosen_class_name")},{"hand_uuids",Json::array()},
        {"player_powers",sourcePlayer.at("creature").at("powers")},{"monster_powers",Json::array()},
        {"monster_moves",Json::array()},{"relics",Json::array()}};
    for(const auto &c:sourcePlayer.at("hand")) context["hand_uuids"].push_back(c.value("uuid",Json()));
    for(const auto &m:source.at("cur_map_node_state").at("monster_data")) {
        context["monster_powers"].push_back(m.at("creature").at("powers"));
        context["monster_moves"].push_back(m.at("next_move"));
    }
    for(const auto &r:sourcePlayer.at("relics")) context["relics"].push_back({{"id",r.at("relic_id")},{"counter",r.at("counter")}});
    if(source.value("is_screen_up",false))context["selection"]=selectionContext(source);
    auto end=std::chrono::steady_clock::now();
    auto ms=[](auto a,auto b){return std::chrono::duration_cast<std::chrono::milliseconds>(b-a).count();};
    Json response={{"type","COMMAND_LIST"},{"supported",true},
        {"commands",Json::array({{{"command",cmd.dump()},{"expected_before",imported.before},{"expected_context",context}}})},
        {"should_replan",true},{"battle_complete",false},{"stop_reason","single_action_replan"},
        {"metrics",{{"backend","cpp"},{"import_ms",ms(start,importedAt)},{"search_ms",ms(importedAt,end)},
        {"simulations",mode=="mcts"?expansions:0},{"expanded_nodes",expansions},{"duplicates",duplicates},
        {"cache_hit",hit},{"cached_actions_remaining",cache.path.size()-cache.offset},
        {"search_mode",mode},{"search_profile",profile},{"search_stop_reason",stop}}},
        {"predicted_outcome",int(bc.outcome)}};
    response["metrics"].update(Json{{"search_policy",policy},{"requested_expansions",cap},{"requested_timeout_ms",budget},
        {"segment_length",segment},{"dedup_mode",dedup},{"tactical_ordering",tacticalOrdering},{"dedup_checks",effort.dedupChecks},{"dedup_ns",effort.dedupNanos}});
    if(policy=="layered-v1")response["metrics"].update(Json{
        {"search_policy",policy},{"requested_expansions",cap},{"requested_timeout_ms",budget},
        {"max_completed_turns",effort.maxCompletedTurns},{"selected_completed_turns",effort.selectedCompletedTurns},
        {"planner_us",effort.searchMicros}});
    // Supported selectors have an explicit observable screen; terminal cleanup
    // and unknown selectors still have no validated next-state boundary.
    const auto screen=decisionScreen(bc);
    if(normal || (bc.outcome==Outcome::UNDECIDED && screen!="UNSUPPORTED")) {
        response["expected_after"]=projection(imported);response["expected_screen"]=screen;
    }
    if(!headless && bc.outcome==Outcome::UNDECIDED &&
            (bc.cardSelectInfo.cardSelectTask==CardSelectTask::HEADBUTT_GRID || imported.desktopHeadbutt)) {
        response["expected_desktop_headbutt_uuids"]=gamblerCardUuids(bc,currentIds);
        if(bc.inputState==InputState::CARD_SELECT)
            response["expected_desktop_headbutt_pending_uuid"]=currentIds.at(bc.curCardQueueItem.card.uniqueId);
    }
    if(!headless && bc.outcome==Outcome::UNDECIDED && desktopGrid(bc)) {
        Json picked=Json::array();
        for(int index:bc.cardSelectInfo.selected)
            picked.push_back(currentIds.at(bc.cardSelectInfo.options.at(index).uniqueId));
        response["expected_desktop_grid_selected_uuids"]=std::move(picked);
    }
    if(!headless && bc.inputState==InputState::CARD_SELECT &&
            bc.cardSelectInfo.cardSelectTask==CardSelectTask::FORETHOUGHT) {
        Json picked=Json::array();
        for(int index:bc.cardSelectInfo.selected)
            picked.push_back(currentIds.at(bc.cardSelectInfo.options.at(index).uniqueId));
        response["expected_desktop_hand_selected_uuids"]=std::move(picked);
    }
    if(verifiesDesktopGrid && normal) {
        // Continuations such as Cut Through Fate can add cards mid-action. Bind UUIDs
        // only where the simulator still knows the matching live instance.
        response["expected_desktop_grid_uuids"]=gamblerCardUuids(bc,currentIds);
    }
    if(bc.outcome!=Outcome::UNDECIDED)
        response["terminal_prediction"]=bc.outcome==Outcome::PLAYER_VICTORY?"victory":"defeat";
    if(headless) {
        response["rng_restore_mode"]="exact_state";
        const auto &select=bc.cardSelectInfo;
        if(bc.inputState==InputState::CARD_SELECT &&
           (select.headlessShuffleScry || (omniscienceScry && select.cardSelectTask==CardSelectTask::SCRY) ||
                select.cardSelectTask==CardSelectTask::EXHAUST_MANY)) {
            const bool scry=select.cardSelectTask==CardSelectTask::SCRY;
            Json options=Json::array();
            for(const auto &card:select.options) {
                auto found=currentIds.find(card.uniqueId);
                options.push_back({{"id",getCardStringId(card.id)},{"upgrades",card.getUpgradeCount()},
                    {"uuid",found==currentIds.end()?Json():found->second}});
            }
            response["expected_menu_picker"]={{"kind",scry?"grid":"hand"},{"purpose",scry?"scry":"exhaust"},
                {"count",scry?select.data0:select.pickCount},{"options",options}};
        }
        if(screen=="CARD_REWARD" && bc.cardSelectInfo.cardSelectTask==CardSelectTask::DISCOVERY) {
            Json offers=Json::array();for(auto c:bc.cardSelectInfo.discovery_Cards())offers.push_back(getCardStringId(c));
            response["expected_discovery"]={{"offers",offers},{"copies",bc.cardSelectInfo.data0}};
        }
        response["expected_native_key"]=normal?stateKey(bc,&imported.monsterMap):Json();
        response["expected_exact_rng"]=exactRngView(bc);
        response["expected_selection"]=gamblerSelection(bc,currentIds,imported.monsterMap);
        response["expected_selection_native_key"]=gamblerGuardKey(imported);
        response["expected_card_uuids"]=gamblerCardUuids(bc,currentIds);
        if(bc.outcome!=Outcome::UNDECIDED) {
            response["terminal_prediction"]=bc.outcome==Outcome::PLAYER_VICTORY?"victory":"defeat";
            if(bc.outcome==Outcome::PLAYER_VICTORY && bc.masterDeckKnown)
                response["expected_master_deck"]=cards(bc.masterDeck);
            response["expected_after"]=projection(imported);
            response["expected_screen"]=bc.outcome==Outcome::PLAYER_VICTORY?"COMBAT_REWARD":"GAME_OVER";
        }
        response["unmodelled_rng"]=Json::object();
        for(auto name:{"monster","map","event","merchant","treasure","relic"})
            response["unmodelled_rng"][name]=snapshot.at("_headless_rng").at("streams").at(name);
    }
    // Preview ordinary same-turn cards only. The source adapter still verifies each
    // real transition. Unknown identities, selectors, lethal and phase changes end a prefix.
    if(segment>1 && normal && phase(bc)==firstPhase && bc.turn==beforeTurn && !imported.roster.changed && !cache.identities.is_null() &&
            action.getActionType()==search::ActionType::CARD && imported.selectionScreen.empty() &&
            request.value("operation",std::string("search"))=="search") {
        auto sim=imported;
        auto &b=sim.battle;
        Json suffix=Json::array();
        const auto deadline=budget==0?std::chrono::steady_clock::time_point::max():start+std::chrono::milliseconds(budget);
        for(auto i=cache.offset;i<cache.path.size() && suffix.size()<size_t(segment-1);++i) {
            if(std::chrono::steady_clock::now()>=deadline)break;
            auto next=cache.path[i];
            if(next.getActionType()!=search::ActionType::CARD || !next.isValidAction(b))break;
            const auto &card=b.cards.hand[next.getSourceIdx()];
            const std::string id=getCardStringId(card.id);
            if(id.rfind("Strike_",0)!=0 && id.rfind("Defend_",0)!=0)break;
            auto guardKey=stateKey(b,&sim.monsterMap);
            if(guardKey.is_null())break;
            const auto uuids=gamblerCardUuids(b,currentIds);
            auto ctx=context;
            ctx["hand_uuids"]=uuids.at("hand");
            auto before=projection(sim);
            const int target=card.requiresTarget()?sim.monsterMap.at(next.getTargetIdx()):-1;
            if(card.requiresTarget() && target<0)break;
            Json command={{"type","CARD"},{"card_index",next.getSourceIdx()},{"monster_index",target},{"display_string",card.getName()}};
            auto oldPhase=phase(b);
            try {ExecutionBudget limit(deadline);executeImported(sim,next);}
            catch(const ExecutionTimeout &){break;}
            if(b.undefinedBehaviorEvoked || b.turn!=beforeTurn || b.inputState!=InputState::PLAYER_NORMAL ||
                    b.outcome!=Outcome::UNDECIDED || sim.roster.changed || phase(b)!=oldPhase || identity(b,currentIds).is_null())break;
            auto afterKey=stateKey(b,&sim.monsterMap);
            if(afterKey.is_null())break;
            Json item={{"supported",true},{"type","COMMAND_LIST"},{"required_native_key",guardKey},{"required_uuids",uuids},
                {"required_path_context",layeredPathContext(snapshot,sim.monsterMap,headless)},
                {"commands",Json::array({{{"command",command.dump()},{"expected_before",before},{"expected_context",ctx}}})},
                {"expected_after",projection(sim)},{"expected_screen","NONE"},{"expected_native_key",afterKey},
                {"expected_card_uuids",gamblerCardUuids(b,currentIds)},{"predicted_outcome",int(b.outcome)},
                {"metrics",{{"expanded_nodes",0},{"search_ms",0},{"search_stop_reason","GUARDED_SEGMENT"},
                            {"search_policy",policy},{"segment_length",segment},{"requested_expansions",cap},{"requested_timeout_ms",budget}}}};
            if(headless)item.update(Json{{"rng_restore_mode","exact_state"},{"expected_exact_rng",exactRngView(b)},
                                       {"unmodelled_rng",response.at("unmodelled_rng")},{"expected_selection",nullptr}});
            suffix.push_back(std::move(item));
        }
        if(!suffix.empty())response["continuations"]=std::move(suffix);
    }
    if(!cacheDiff.is_null())response["cache_diff"]=cacheDiff;
    return response;
}
}
