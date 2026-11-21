#pragma once
#include <nlohmann/json.hpp>
#include "combat/BattleContext.h"
#include "MonsterRoster.h"
#include <array>
#include <string>
#include <memory>

namespace sts::search {struct Action;}

namespace bridge {
using Json = nlohmann::json;
struct Unsupported : std::runtime_error { using std::runtime_error::runtime_error; };
struct Imported {
    sts::BattleContext battle;
    std::array<int, 5> monsterMap{{-1,-1,-1,-1,-1}};
    Json before;
    MonsterRoster roster;
    std::map<int,Json> selectionIdentities;
    bool desktopHeadbutt=false;
    std::string selectionScreen;
    std::vector<int> selectionIndices;
};
sts::CardInstance snapshotCard(const Json &card,int uniqueId,bool inFlight=false);
Imported importSelectionSnapshot(const Json &snapshot);
Json selectionContext(const Json &snapshot);
std::string decisionScreen(const sts::BattleContext &bc);
Json projection(const Json &snapshot);
Json projection(const sts::BattleContext &bc, const std::array<int,5> &monsterMap);
Json projection(const Imported &imported);
void executeImported(Imported &imported,const sts::search::Action &action);
Imported importSnapshot(const Json &snapshot);
// An owner (a worker or a JVM battle session) is serialized by its adapter.
class Session {
    struct Cache;
    std::unique_ptr<Cache> state;
public:
    Session();
    ~Session();
    Json search(const Json &request);
};
}
