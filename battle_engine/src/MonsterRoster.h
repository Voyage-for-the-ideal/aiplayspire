#pragma once
#include <nlohmann/json.hpp>
#include "combat/BattleContext.h"
#include <array>
#include <vector>

namespace bridge {
// SaveState keeps dead parents/summons and inserts newborns by their screen X.
// The simulator reuses five slots. Keep this wire history outside search states.
struct MonsterRoster {
    struct Entry { nlohmann::json view; double x; };
    std::vector<Entry> entries;
    bool changed=false;
    void prepare(const nlohmann::json &snapshot,nlohmann::json &monsters);
    void spawn(const sts::BattleContext &bc,std::array<int,5> &map,int slot,
               double x,int relativeTo,bool splitParent);
    nlohmann::json project(const sts::BattleContext &bc,const std::array<int,5> &map) const;
};
}
