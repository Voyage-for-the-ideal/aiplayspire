#pragma once

#include <map>
#include <string>
#include "sim/search/Action.h"

namespace sts::search {

// Read-only tactical guidance. These functions never remove legal actions or
// consume RNG. All scores are potentials, not replacements for simulation.
enum class SelectionPurpose { NONE, DISCARD, EXHAUST, SCRY, SEEK, RETAIN };

struct MechanismScore {
    static constexpr int LIMIT = 20000;
    std::map<std::string, int> rules;
    void add(const char *name, int value);
    int total() const;
};

int projectedIncomingLoss(const BattleContext &bc);
bool isStanceExit(CardId id);
MechanismScore characterMechanisms(const BattleContext &bc);
int characterPlayPriority(const BattleContext &bc, const CardInstance &card, int target);
int characterSelectionPriority(const BattleContext &bc, const CardInstance &card, SelectionPurpose purpose);
int characterFallbackRank(const BattleContext &bc, CardId id);
SelectionPurpose selectionPurpose(CardSelectTask task);

}
