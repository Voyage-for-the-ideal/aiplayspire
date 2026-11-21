#pragma once
#include "combat/BattleContext.h"
#include "sim/search/Action.h"

namespace sts {
constexpr int CHARACTER_SELECT_CONFIRM = 65535;
bool isCharacterSelection(CardSelectTask task);
void sortHeadlessSeekOptions(std::vector<CardInstance> &options);
void openCharacterSelection(BattleContext &bc,CardSelectTask task,int count);
bool validCharacterSelection(const BattleContext &bc,int option);
void applyCharacterSelection(BattleContext &bc,int option);
std::vector<search::Action> characterSelectionActions(const BattleContext &bc);
}
