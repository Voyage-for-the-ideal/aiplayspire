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
        GAMBLER_DISCARD,
        LIQUID_MEMORIES_GRID,
        HEADBUTT_GRID,
        ELIXIR_EXHAUST,
        HOLOGRAM_GRID, SEEK_GRID,
        ARMAMENTS_HAND, TRUE_GRIT_HAND, DUAL_WIELD_HAND, BURNING_PACT_HAND,
        SECRET_WEAPON_GRID, SECRET_TECHNIQUE_GRID, FORETHOUGHT_HAND, NIGHTMARE_HAND,
    };
    inline bool isHeadlessSecretFetch(CardSelectTask t) {
        return t==CardSelectTask::SECRET_WEAPON_GRID || t==CardSelectTask::SECRET_TECHNIQUE_GRID;
    }
    inline bool isHeadlessFetchGrid(CardSelectTask t) {
        return t==CardSelectTask::HOLOGRAM_GRID || t==CardSelectTask::SEEK_GRID || t==CardSelectTask::OMNISCIENCE || isHeadlessSecretFetch(t);
    }
    inline bool isHeadlessSingleHand(CardSelectTask t) {
        return t==CardSelectTask::ARMAMENTS_HAND || t==CardSelectTask::TRUE_GRIT_HAND || t==CardSelectTask::DUAL_WIELD_HAND || t==CardSelectTask::BURNING_PACT_HAND || t==CardSelectTask::FORETHOUGHT_HAND || t==CardSelectTask::NIGHTMARE_HAND;
    }
    inline bool isHeadlessMultiHand(CardSelectTask t) {
        return t==CardSelectTask::GAMBLER_DISCARD || t==CardSelectTask::ELIXIR_EXHAUST;
    }

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
            "OMNISCIENCE", "FOREIGN_INFLUENCE", "WISH", "GAMBLER_DISCARD", "LIQUID_MEMORIES_GRID", "HEADBUTT_GRID", "ELIXIR_EXHAUST", "HOLOGRAM_GRID", "SEEK_GRID",
            "ARMAMENTS_HAND", "TRUE_GRIT_HAND", "DUAL_WIELD_HAND", "BURNING_PACT_HAND",
            "SECRET_WEAPON_GRID", "SECRET_TECHNIQUE_GRID", "FORETHOUGHT_HAND", "NIGHTMARE_HAND",
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
        std::vector<CardInstance> hiddenHand;
        std::vector<CardInstance> stashedExhumes;
        std::vector<int> selected;
        bool canonicalOrder = true;
        bool explicitConfirm = false;
        bool ornithopterHeal = false; // Bound pending +5 heal at a potion selection.
        int sourceDraw = 0; // Burning Pact draw, before skill on-use effects.
        int hexDazed = 0;
        bool inkDraw = false;
        bool letterOpener = false; // Pending third-skill relic hit.
        bool orangePellets = false; // Pending debuff purge after completing the relic trio.
        int juggernautDamage = 0; // A blocking skill queues retaliation after cleanup.
        int sharpHide = 0;
        bool headbuttPenNibRemove = false;
        bool headbuttPenNibBuff = false;
        bool headbuttNunchakuEnergy = false;
        bool headbuttOrnamentalBlock = false;
        bool headbuttDarklingRepeat = false;
        bool headlessDiscovery = false;
        bool headlessShuffleScry = false;
        CardType discoverySourceType = CardType::INVALID; // INVALID=all colored, STATUS=colorless

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
