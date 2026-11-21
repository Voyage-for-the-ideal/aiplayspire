#ifndef STS_LIGHTSPEED_BATTLECONVERTER_H
#define STS_LIGHTSPEED_BATTLECONVERTER_H

#include <nlohmann/json.hpp>
#include "combat/CardInstance.h"

namespace sts {
    CardInstance importCombatCard(const nlohmann::json &card, int uniqueCardId);

    class BattleContext;
    class GameContext;

    struct BattleConverter {
        BattleConverter();
        ~BattleConverter();

        BattleContext convertFromJson(const nlohmann::json &json, int *monsterIdxMap, bool alreadyAtBoundary = false);
    };

}

#endif //STS_LIGHTSPEED_BATTLECONVERTER_H
