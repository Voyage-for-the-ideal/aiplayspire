#pragma once
#include "Snapshot.h"
#include "sim/search/Action.h"
#include <chrono>
#include <map>
#include <string>
#include <vector>

namespace bridge {
struct TacticalScore {
    int survival=0, damage=0, threat=0, lethal=0, scaling=0, resource=0, mechanism=0;
    std::map<std::string,int> mechanismRules;
    int total() const { return survival+damage+threat+lethal+scaling+resource+mechanism; }
    Json json() const;
};
TacticalScore evaluate(const sts::BattleContext &root, const sts::BattleContext &state);
int priority(const sts::BattleContext &state, const sts::search::Action &action);
int layeredPriority(const sts::BattleContext &state, const sts::search::Action &action);
// Exact, ordered semantic representation at stable normal decision points.
// A null key means pending executor work is opaque and must never be reused.
Json stateKey(const sts::BattleContext &state);
struct TurnSearchResult {
    std::vector<sts::search::Action> path;
    int expansions=0, duplicates=0;
    int maxCompletedTurns=0, selectedCompletedTurns=0;
    long searchMicros=0;
    bool victory=false;
    std::string reason;
};
TurnSearchResult turnSearch(const sts::BattleContext &root, int maxExpansions, long timeoutMs,
                            const std::string &profile);
TurnSearchResult layeredTurnSearch(const sts::BattleContext &root, int maxExpansions, long timeoutMs,
                                   bool tacticalOrdering=true);
}
