#pragma once
#include <functional>
#include <utility>

namespace sts {
class BattleContext;
// Only the final single-action replay installs this observer, never search copies.
using MonsterSpawnObserver = std::function<void(const BattleContext &,int,double,int,bool)>;
inline thread_local MonsterSpawnObserver monsterSpawnObserver;
inline void notifyMonsterSpawn(const BattleContext &bc,int slot,double x,
                              int relativeTo=-1,bool splitParent=false) {
    if(monsterSpawnObserver)monsterSpawnObserver(bc,slot,x,relativeTo,splitParent);
}
struct ScopedMonsterSpawnObserver {
    MonsterSpawnObserver previous;
    explicit ScopedMonsterSpawnObserver(MonsterSpawnObserver observer)
        :previous(std::move(monsterSpawnObserver)) {monsterSpawnObserver=std::move(observer);}
    ~ScopedMonsterSpawnObserver(){monsterSpawnObserver=std::move(previous);}
    ScopedMonsterSpawnObserver(const ScopedMonsterSpawnObserver &)=delete;
};
}
