#include "MonsterRoster.h"
#include <algorithm>
#include <cmath>
#include <set>
#include <stdexcept>

using namespace sts;
using J=nlohmann::json;
namespace bridge {
namespace {
J view(const Monster &m) {
    return {{"id",monsterIdIds[int(m.id)]},{"hp",m.curHp},{"max_hp",m.maxHp},{"block",m.block}};
}
bool gone(const J &m) {
    const auto &c=m.at("creature");
    return !c.value("half_dead",false) && (c.value("is_dead",false) ||
        c.at("current_health").get<int>()<=0 || c.value("is_escaping",false) || m.value("escaped",false));
}
}
void MonsterRoster::prepare(const J &snapshot,J &monsters) {
    const auto &source=snapshot.at("cur_map_node_state").at("monster_data");
    std::set<std::string> ids;
    for(const auto &m:source)ids.insert(m.at("creature").at("id").get<std::string>());
    std::string layout;
    for(auto id:{"GremlinLeader","Reptomancer","BronzeAutomaton","TheCollector","SlimeBoss"})
        if(ids.count(id)){layout=id;break;}
    const bool spawning=!layout.empty() || ids.count("AcidSlime_L") || ids.count("SpikeSlime_L");
    // xScale=WIDTH/1920. Prefer the fixed boss anchor; ordinary standalone
    // slimes use the player's normal WIDTH/4 anchor. No rendering state is changed.
    double scale=1;
    const auto &pc=snapshot.at("player_state").at("creature");
    if(pc.contains("draw_x") && pc.at("draw_x").get<double>()>0)scale=pc.at("draw_x").get<double>()/480;
    const double bossX=layout=="GremlinLeader"?35:layout=="Reptomancer"?-20:
        layout=="BronzeAutomaton"?-50:layout=="TheCollector"?60:0;
    for(const auto &m:source)if(m.at("creature").at("id")==layout && m.at("creature").contains("draw_x"))
        scale=m.at("creature").at("draw_x").get<double>()/(1440+bossX);
    if(!std::isfinite(scale) || scale<=0)throw std::runtime_error("invalid_monster_position_scale");
    std::array<double,5> xs{{-400,-200,0,200,400}};
    int bossSlot=-1;
    std::vector<int> fallback;
    if(layout=="GremlinLeader"){xs={{-532,-366,-170,35,400}};bossSlot=3;fallback={1,2,0};}
    if(layout=="Reptomancer"){xs={{-250,-220,-20,180,210}};bossSlot=2;fallback={1,4,3,0};}
    if(layout=="BronzeAutomaton"){xs={{-300,-50,200,400,600}};bossSlot=1;fallback={0,2};}
    if(layout=="TheCollector"){xs={{-470,-285,60,400,600}};bossSlot=2;fallback={0,1};}
    if(layout=="SlimeBoss"){xs={{-519,-251,-14,254,600}};bossSlot=0;}
    std::set<int> occupied;
    int active=0,historyIndex=0,slimeSpike=0,slimeAcid=0;
    for(std::size_t i=0;i<source.size();++i) {
        const auto &m=source[i],&c=m.at("creature"); auto &v=monsters[i];
        const auto id=c.at("id").get<std::string>();
        const bool hasX=c.contains("draw_x");
        double x=hasX?c.at("draw_x").get<double>()/scale-1440:double(i)*200;
        std::vector<int> choices=fallback;
        int slot=-1;
        if(!layout.empty()) {
            if(layout=="SlimeBoss") {
                if(id=="SlimeBoss") {slot=0;if(!hasX)x=0;}
                else if(id=="SpikeSlime_L"){slot=0;if(!hasX)x=-385;}
                else if(id=="AcidSlime_L"){slot=2;if(!hasX)x=120;}
                else if(id=="SpikeSlime_M") {choices={0,1};if(!hasX)x=xs[(slimeSpike++)%2];}
                else if(id=="AcidSlime_M") {choices={2,3};if(!hasX)x=xs[2+(slimeAcid++)%2];}
            } else if(id==layout)slot=bossSlot;
            else if(!hasX && !fallback.empty())x=xs[fallback[(historyIndex++)%fallback.size()]];
            if(slot>=0 && !hasX && id==layout)x=bossX;
        }
        entries.push_back({{{"id",id},{"hp",c.at("current_health")},
            {"max_hp",c.at("max_health")},{"block",c.at("current_block")}},x});
        // Ordinary small fights retain their dead slots for existing path-cache
        // stability. Summoning encounters keep history only in this wire ledger.
        const bool skip=gone(m) && (spawning || source.size()>5 || c.at("current_health").get<int>()>0);
        v["is_gone"]=skip;
        if(skip){v["powers"]=J::array();continue;}
        if(++active>5)throw std::runtime_error("monster_capacity");
        if(layout.empty())continue;
        if(slot<0) {
            if(choices.empty())throw std::runtime_error("unknown_encounter_monster_slot");
            double best=1e100;
            for(int candidate:choices)if(!occupied.count(candidate)) {
                const double distance=hasX || layout=="SlimeBoss"?std::abs(x-xs[candidate]):0;
                if(distance<best){best=distance;slot=candidate;}
            }
        }
        if(slot<0 || !occupied.insert(slot).second)throw std::runtime_error("duplicate_monster_slot");
        v["native_slot"]=slot;
    }
}
void MonsterRoster::spawn(const BattleContext &bc,std::array<int,5> &map,int slot,
                          double x,int relativeTo,bool splitParent) {
    if(relativeTo>=0) {
        const int parent=map.at(relativeTo);
        if(parent<0)throw std::runtime_error("unmapped_split_parent");
        x+=entries.at(parent).x;
    }
    if(map.at(slot)>=0) {
        auto &old=entries.at(map[slot]).view;
        old=view(bc.monsters.arr[slot]);
        if(splitParent)old["hp"]=0; // Vanilla queues SuicideAction before the births.
    }
    const int position=int(std::count_if(entries.begin(),entries.end(),
        [&](const Entry &e){return e.x<x-0.001;}));
    entries.insert(entries.begin()+position,{J::object(),x});
    for(auto &index:map)if(index>=position)++index;
    map[slot]=position;
    changed=true;
}
J MonsterRoster::project(const BattleContext &bc,const std::array<int,5> &map) const {
    J result=J::array();
    for(const auto &entry:entries)result.push_back(entry.view);
    for(int i=0;i<bc.monsters.monsterCount;++i)if(map[i]>=0 && bc.monsters.arr[i].id!=MonsterId::INVALID)
        result.at(map[i])=view(bc.monsters.arr[i]);
    return result;
}
}
