//
// Created by gamerpuppy on 8/24/2021.
//

#ifndef STS_LIGHTSPEED_CARDSELECTINFO_H
#define STS_LIGHTSPEED_CARDSELECTINFO_H

#include <array>
#include <vector>
#include "combat/CardInstance.h"

#include "constants/Cards.h"

namespace sts {

    enum class CardSelectTask {
        INVALID=0,
        ARMAMENTS,
        CODEX,
        DISCOVERY,
        TOOLBOX,
        DUAL_WIELD,
        EXHAUST_ONE,
        EXHAUST_MANY,
        EXHUME,
        FORETHOUGHT,
        GAMBLE,
        HEADBUTT,
        HOLOGRAM,
        LIQUID_MEMORIES_POTION,
        MEDITATE,
        NIGHTMARE,
        RECYCLE,
        SECRET_TECHNIQUE,
        SECRET_WEAPON,
        SEEK,
        SETUP,
        WARCRY,
        DISCARD,
        SCRY,
        RETAIN,
        OMNISCIENCE,
        FOREIGN_INFLUENCE,
        WISH,
    };

    static constexpr const char* cardSelectTaskStrings[] = {
            "INVALID",
            "ARMAMENTS",
            "CODEX",
            "DISCOVERY",
            "TOOLBOX",
            "DUAL_WIELD",
            "EXHAUST_ONE",
            "EXHAUST_MANY",
            "EXHUME",
            "FORETHOUGHT",
            "GAMBLE",
            "HEADBUTT",
            "HOLOGRAM",
            "LIQUID_MEMORIES_POTION",
            "MEDITATE",
            "NIGHTMARE",
            "RECYCLE",
            "SECRET_TECHNIQUE",
            "SECRET_WEAPON",
            "SEEK",
            "SETUP",
            "WARCRY",
            "DISCARD", "SCRY", "RETAIN",
            "OMNISCIENCE", "FOREIGN_INFLUENCE", "WISH",
    };

    struct CardSelectInfo {
        std::array<CardId, 3> cards{};
        bool canPickZero = false;
        bool canPickAnyNumber = false;
        int pickCount = 0;
        int data0 = 0;

        CardSelectTask cardSelectTask = CardSelectTask::INVALID;
        // Stable option identities; selections are applied only at completion.
        // This also handles stacked Scry counts beyond the old 10-bit mask.
        std::vector<CardInstance> options;
        std::vector<int> selected;
        bool canonicalOrder = true;
        bool explicitConfirm = false;

        std::array<CardId, 3>& discovery_Cards() { return cards; }
        const std::array<CardId, 3>& discovery_Cards() const { return cards; }
        int& discovery_CopyCount() { return data0; }
        std::array<CardId, 3>& toolboxCards() { return cards; }
        const std::array<CardId, 3>& toolboxCards() const { return cards; }
        int& dualWield_CopyCount() { return data0; }
        std::array<CardId, 3>& codexCards() { return cards; }
        const std::array<CardId, 3>& codexCards() const { return cards; }
    };

}

#endif //STS_LIGHTSPEED_CARDSELECTINFO_H
