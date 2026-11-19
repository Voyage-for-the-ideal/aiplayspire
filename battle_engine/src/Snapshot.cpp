#include "Snapshot.h"
#include "TurnSearch.h"
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
namespace {
struct Cache {
    std::string owner,profile,mode,policy;bool tacticalOrdering=true;Json expected,identities,pools,attributes;
    int cap=0;long budget=0;
    std::vector<search::Action> path;std::size_t offset=0;
};
Cache cache;
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
Json convertPowers(const Json &powers,bool player,const std::string &owner="") {
    Json result=Json::array();
    for (const auto &p:powers) {
        auto id=p.at("power_id").get<std::string>();
        if(!player && id=="Split") {
            auto monster=getMonsterIdFromId(owner);
            require(monster==MonsterId::SLIME_BOSS || monster==MonsterId::ACID_SLIME_L ||
                monster==MonsterId::SPIKE_SLIME_L,"split_power_owner");
            // Native Monster::onHpLost implements this marker as intrinsic slime behavior.
            continue;
        }
        require(player ? getPlayerStatusFromId(id)!=PlayerStatus::INVALID :
            getMonsterStatusFromId(id)!=MonsterStatus::INVALID,"unknown_power:"+id);
        // These need additional private state with different SaveState/upstream schemas.
        static const std::set<std::string> extra={"Combust","Echo Form","Panache","TheBomb","Stasis"};
        require(!extra.count(id),"power_private_state:"+id);
        Json q=p; q["id"]=id;
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

Imported importSnapshot(const Json &s) {
    if(s.value("is_screen_up",false) || s.value("screen_name",std::string("NONE"))!="NONE")return importSelectionSnapshot(s);
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
    g["relics"]=Json::array();
    for(const auto &r:p.at("relics")) {
        auto id=r.at("relic_id").get<std::string>();
        require(getRelicFromId(id)!=RelicId::INVALID,"unknown_relic:"+id);
        static const std::set<std::string> privateRelics={"Necronomicon","OrangePellets","Emotion Chip","Centennial Puzzle"};
        require(!privateRelics.count(id),"relic_private_state:"+id);
        g["relics"].push_back({{"id",id},{"counter",r.at("counter")}});
    }
    g["potions"]=p.at("potions");
    for(const auto &v:g["potions"]) require(getPotionFromId(v.at("id").get<std::string>())!=Potion::INVALID,"unknown_potion");
    Json combat={{"turn",s.at("turn")},{"cards_discarded_this_turn",s.at("total_discarded_this_turn")},
        {"player",{{"energy",p.at("energy_panel_total_energy")},{"block",pc.at("current_block")},
        {"powers",convertPowers(pc.at("powers"),true)},{"orb_slots",p.at("max_orbs")},{"orbs",Json::array()}}}};
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
    for(const auto &entry:s.value("cards_played_this_combat",Json::array())) {
        require(entry.is_object() && entry.contains("card_state"),"combat_history_schema");
        const auto &card=entry.at("card_state").is_null()?indexedCard(entry.at("card_index").get<int>()):entry.at("card_state");
        powers+=getCardType(getCardIdFromId(card.at("card_id").get<std::string>()))==CardType::POWER;
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
        const auto &mh=m.at("move_history");
        if(mh.size()>1) v["last_move_id"]=mh[mh.size()-2].is_string()?std::stoi(mh[mh.size()-2].get<std::string>()):mh[mh.size()-2].get<int>();
        combat["monsters"].push_back(v);
    }
    Imported result;
    result.roster.prepare(s,combat["monsters"]);
    for(std::size_t i=0;i<combat["monsters"].size();++i)if(!combat["monsters"][i].at("is_gone").get<bool>()) {
        const auto &c=room.at("monster_data")[i].at("creature");
        combat["monsters"][i]["powers"]=convertPowers(c.at("powers"),false,c.at("id").get<std::string>());
    }
    g["combat_state"]=combat;
    BattleConverter converter; result.battle=converter.convertFromJson({{"game_state",g}},result.monsterMap.data());
    auto &bc=result.battle;
    auto stance=p.value("stance",std::string("Neutral"));
    require(stance=="Neutral" || stance=="Wrath" || stance=="Calm" || stance=="Divinity","unknown_stance:"+stance);
    bc.player.stance=stance=="Wrath"?Stance::WRATH:stance=="Calm"?Stance::CALM:stance=="Divinity"?Stance::DIVINITY:Stance::NEUTRAL;
    bc.mantraGained=s.value("mantra_gained",0);
    if(!history.empty())bc.lastCardType=getCardType(getCardIdFromId(history.back().at("card_id")));
    const auto &rng=s.at("rng_state"); auto seed=rng.at("seed").get<std::uint64_t>();
    auto floorSeed=seed+s.at("floor_num").get<int>();
    auto restore=[&](const char *name,std::uint64_t base) {
        int n=rng.at(name).get<int>(); require(n>=0 && n<=10000000,"rng_counter_range"); return Random(base,n);
    };
    bc.aiRng=restore(rngNames[0],floorSeed); bc.cardRandomRng=restore(rngNames[1],floorSeed);
    bc.miscRng=restore(rngNames[2],floorSeed); bc.monsterHpRng=restore(rngNames[3],floorSeed);
    bc.potionRng=restore(rngNames[4],seed); bc.shuffleRng=restore(rngNames[5],floorSeed);
    bc.cardRngKnown=rng.contains("card_rng_counter");
    if(bc.cardRngKnown)bc.cardRng=restore("card_rng_counter",seed);
    bc.masterDeckKnown=p.contains("master_deck") && p.at("master_deck").is_array();bc.masterDeck.clear();
    if(bc.masterDeckKnown)for(const auto &card:p.at("master_deck"))bc.masterDeck.push_back(importCombatCard(convertCard(card),int(bc.masterDeck.size())));
    for(const auto &power:pc.at("powers"))if(power.at("power_id")=="Night Terror") {
        require(power.contains("card") && power.at("card").is_object(),"missing_power_state:Nightmare");
        bc.nightmareCard=importCombatCard(convertCard(power.at("card")),-1);
    }
    bc.player.energyPerTurn=p.at("energy_manager_energy").get<int>();
    bc.player.cardDrawPerTurn=p.at("game_hand_size").get<int>();
    restoreCards(bc,p);
    require(bc.inputState==InputState::PLAYER_NORMAL,"import_not_at_decision");
    result.before=projection(s);
    require(result.before==projection(result),"import_projection_mismatch");
    return result;
}

Json search(const Json &request) {
    if(request.value("operation",std::string())=="release_session") {
        if(cache.owner==request.value("session_id",std::string()))cache=Cache{};
        return {{"supported",true}};
    }
    auto start=std::chrono::steady_clock::now();
    auto imported=importSnapshot(request.at("snapshot"));
    auto importedAt=std::chrono::steady_clock::now();
    auto &bc=imported.battle;
    if(request.value("operation","")=="import") return {{"supported",true},{"projection",imported.before},
        {"mechanics",{{"cards_played",bc.player.cardsPlayedThisTurn},{"attacks_played",bc.player.attacksPlayedThisTurn},
        {"skills_played",bc.player.skillsPlayedThisTurn},{"powers_played",bc.powersPlayedThisCombat}}}};
    if(request.value("operation","")=="rng_probe") {
        Json samples=Json::array();
        for(auto rng:{bc.aiRng,bc.cardRandomRng,bc.miscRng,bc.monsterHpRng,bc.potionRng,bc.shuffleRng}) {
            Json row=Json::array(); for(int i=0;i<16;++i) row.push_back(rng.random(999)); samples.push_back(row);
        }
        return {{"supported",true},{"samples",samples}};
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
    const bool tacticalOrdering=request.value("tactical_ordering",true);
    int cap=std::clamp(request.value("max_expansions",15000),1,1000000);long budget=std::clamp(request.value("timeout_ms",2000L),1L,90000L);
    require(profile=="FAST" || profile=="BALANCED" || profile=="DEEP","unknown_search_profile");
    require(mode=="turn" || mode=="mcts","unknown_search_mode");
    require(policy=="legacy" || (policy=="layered-v1" && mode=="turn"),"unknown_search_policy");
    auto pools=snapshot.value("_combat_card_pools",Json());auto ids=identity(bc,currentIds);auto key=stateKey(bc);auto attributes=cardAttributes(snapshot);
    bool hit=!owner.empty() && cache.owner==owner && cache.profile==profile && cache.mode==mode && cache.policy==policy &&
        cache.tacticalOrdering==tacticalOrdering && cache.pools==pools && cache.cap==cap && cache.budget==budget &&
        !key.is_null() && key==cache.expected && !ids.is_null() && ids==cache.identities && cache.attributes==attributes && cache.offset<cache.path.size();
    Json cacheDiff;
    if(request.value("debug_keys",false) && cache.owner==owner)cacheDiff={{"state",Json::diff(cache.expected,key)},{"identity",Json::diff(cache.identities,ids)}};
    int expansions=0,duplicates=0;std::string stop="PATH_CACHE";
    TurnSearchResult effort;
    if(!hit) {
        cache=Cache{};cache.owner=owner;cache.profile=profile;cache.mode=mode;cache.pools=pools;cache.attributes=attributes;
        cache.policy=policy;cache.tacticalOrdering=tacticalOrdering;
        cache.cap=cap;cache.budget=budget;
        if(mode=="mcts") {
            search::BattleScumSearcher2 solver(bc);solver.allowPotions=true;solver.allowRootPotions=true;solver.search(cap,budget);
            cache.path=solver.bestActionSequence;expansions=int(solver.root.simulationCount);stop=solver.stopReason;
        } else {
            effort=policy=="layered-v1"?layeredTurnSearch(bc,cap,budget,tacticalOrdering):turnSearch(bc,cap,budget,profile);
            cache.path=std::move(effort.path);expansions=effort.expansions;duplicates=effort.duplicates;stop=effort.reason;
        }
    }
    require(cache.offset<cache.path.size(),"search_no_action");
    auto action=cache.path[cache.offset];require(action.isValidAction(bc),"invalid_search_action");
    Json cmd;
    switch(action.getActionType()) {
    case search::ActionType::CARD: {
        auto &c=bc.cards.hand[action.getSourceIdx()];
        int target=c.requiresTarget()?imported.monsterMap.at(action.getTargetIdx()):-1;
        require(!c.requiresTarget() || target>=0,"unmapped_target");
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
    executeImported(imported,action);
    ++cache.offset;
    require(!bc.undefinedBehaviorEvoked,"engine_undefined_behavior");
    bool normal=bc.inputState==InputState::PLAYER_NORMAL && bc.outcome==Outcome::UNDECIDED;
    cache.expected=normal && !imported.roster.changed?stateKey(bc):Json();cache.identities=identity(bc,currentIds);
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
    if(!cacheDiff.is_null())response["cache_diff"]=cacheDiff;
    return response;
}
}
