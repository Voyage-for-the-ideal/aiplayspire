#pragma once
#include "constants/Cards.h"
namespace sts {
struct BattleContext;
bool characterCardSupported(CardId id);
bool useCharacterCard(BattleContext &bc);
}
