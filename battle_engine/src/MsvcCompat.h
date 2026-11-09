#pragma once
// Keep compiler-specific glue outside the vendor. Upstream uses one GCC population-count builtin.
#include <cstdint>
#include <algorithm>
#include <cmath>
#include <limits>
#include <vector>
#include <cassert>
#ifdef _MSC_VER
// Upstream mixes class/struct forward declarations. Establish one ABI spelling in every TU.
namespace sts { struct BattleContext; struct GameContext; struct CardInstance; struct CardQueueItem; struct Deck; }
inline int sts_popcount(unsigned int n) {
    int result = 0;
    while (n) { n &= n - 1; ++result; }
    return result;
}
#define __builtin_popcount sts_popcount
#endif
