//
// Created by gamerpuppy on 7/4/2021.
//

#include "combat/Actions.h"
#include "combat/BattleContext.h"
#include "game/Game.h"
#include "combat/MonsterSpawnObserver.h"

using namespace sts;

Action Actions::SetState(InputState state) {
    return {[=] (BattleContext &bc) {
        bc.setState(state);
    }};
}

Action Actions::AttackEnemy(int idx, int damage) {
    return {[=] (BattleContext &bc) {
        if (bc.monsters.arr[idx].isDeadOrEscaped()) {
            return;
        }

        bc.monsters.arr[idx].attacked(bc, damage);
        bc.checkCombat();
    }};
}

Action Actions::AttackAllEnemy(int baseDamage) {
    return {[=] (BattleContext &bc) {
        // assume bc.curCard is the card being used

        int damageMatrix[5];
        for (int i = 0; i < bc.monsters.monsterCount; ++i) {
            if (!bc.monsters.arr[i].isDeadOrEscaped()) {
                damageMatrix[i] = bc.calculateCardDamage(bc.curCardQueueItem.card, i, baseDamage);
            }
        }

        for (int i = 0; i < bc.monsters.monsterCount; ++i) {
            if (!bc.monsters.arr[i].isDeadOrEscaped()) {
                bc.monsters.arr[i].attacked(bc, damageMatrix[i]);
            }
        }

        bc.checkCombat();
    }};
}

Action Actions::AttackAllEnemy(DamageMatrix damageMatrix) {
    return {[=] (BattleContext &bc) {
        // assume bc.curCard is the card being used

        for (int i = 0; i < bc.monsters.monsterCount; ++i) {
            if (!bc.monsters.arr[i].isDeadOrEscaped()) {
                bc.monsters.arr[i].attacked(bc, static_cast<int>(damageMatrix[i]));
            }
        }

        bc.checkCombat();
    }};
}

Action Actions::DamageEnemy(int idx, int damage) {
    return {[=] (BattleContext &bc) {
        if (bc.monsters.arr[idx].isDeadOrEscaped()) {
            return;
        }

        bc.monsters.arr[idx].damage(bc, damage);
        bc.checkCombat();
    }};
}

Action Actions::DamageAllEnemy(int damage) { // todo this is probably broken
    return {[=] (BattleContext &bc) {
        for (int idx = 0; idx < bc.monsters.monsterCount; idx++) {
            if (!bc.monsters.arr[idx].isDeadOrEscaped()) {
                bc.monsters.arr[idx].damage(bc, damage); // possible should addToBot here todo
            }
        }
        bc.checkCombat();
    }};
}

Action Actions::AttackPlayer(int idx, int damage) {
    return {[=] (BattleContext &bc) {
        // Ordinary queued hits stop if retaliation killed their source.
        // THORNS and HP-loss effects use separate damage actions.
        if (bc.monsters.arr[idx].isDeadOrEscaped()) return;
        bc.player.attacked(bc, idx, damage);
    }, false};
}

Action Actions::DamagePlayer(int damage, bool selfDamage) {
    return {[=] (BattleContext &bc) {
        bc.player.damage(bc, damage, selfDamage);
    }, false};
}

Action Actions::VampireAttack(int damage) {
    return {[=] (BattleContext &bc) {
        const auto mIdx = 0;
        auto &m = bc.monsters.arr[mIdx]; // only used by shelled parasite so idx is 0
        bc.player.attacked(bc, mIdx, damage);
        if (m.isAlive()) {
            m.heal(std::min(damage, static_cast<int>(bc.player.lastAttackUnblockedDamage)));
        }
    }};
}

Action Actions::PlayerLoseHp(int hp, bool selfDamage) {
    return {[=] (BattleContext &bc) {
        bc.player.loseHp(bc, hp, selfDamage);
    }, false};
}

Action Actions::HealPlayer(int amount) {
    return {[=] (BattleContext &bc) {
        bc.player.heal(amount);
    }, false};
}

Action Actions::MonsterGainBlock(int idx, int amount) {
    return {[=] (BattleContext &bc) {
        bc.monsters.arr[idx].block += amount;
    }};
}

Action Actions::RollMove(int monsterIdx) {
    return {[=] (BattleContext &bc) {
        Monster &m = bc.monsters.arr[monsterIdx];
        m.rollMove(bc);
    }};
}

Action Actions::ReactiveRollMove() {
    return {[=] (BattleContext &bc) {
        // writhing mass is always monster 0
        Monster &m = bc.monsters.arr[0];

        for (int i = 0 ; i < m.getStatus<MS::REACTIVE>(); ++i) {
            m.rollMove(bc);
        }
        m.setStatus<MS::REACTIVE>(0);
    }};
}

Action Actions::NoOpRollMove() {
    return {[=] (BattleContext &bc) {
        bc.noOpRollMove();
    }};
}

Action Actions::ChangeStance(Stance stance) {
    return {[=](BattleContext &bc) {
        auto &p=bc.player;
        if(p.stance==stance)return;
        if(p.stance==Stance::CALM)bc.addToBot(Actions::GainEnergy(p.hasRelic<R::VIOLET_LOTUS>()?3:2));
        p.stance=stance;
        if(stance==Stance::DIVINITY)bc.addToBot(Actions::GainEnergy(3));
        if(p.hasStatus<PS::MENTAL_FORTRESS>())bc.addToBot(Actions::GainBlock(p.getStatus<PS::MENTAL_FORTRESS>()));
        if(stance==Stance::WRATH && p.hasStatus<PS::RUSHDOWN>())bc.addToBot(Actions::DrawCards(p.getStatus<PS::RUSHDOWN>()));

        for(int i=int(bc.cards.discardPile.size())-1;i>=0;--i) {
            if(bc.cards.discardPile[i].id==CardId::FLURRY_OF_BLOWS) {
                bc.addToBot({[id=bc.cards.discardPile[i].uniqueId](BattleContext &b){
                    if(b.cards.cardsInHand>=10)return;
                    for(int n=0;n<int(b.cards.discardPile.size());++n)if(b.cards.discardPile[n].uniqueId==id) {
                        auto card=b.cards.discardPile[n];b.cards.removeFromDiscard(n);b.cards.moveToHand(card);break;
                    }
                }});
            }
        }
    }};
}

Action Actions::GainEnergy(int amount) {
    return {[=] (BattleContext &bc) {
        bc.player.gainEnergy(amount);
    }};
}

Action Actions::GainBlock(int amount) {
    return {[=] (BattleContext &bc) {
        bc.player.gainBlock(bc, amount);
    }, false};
}
//
//Action Actions::GainBlockFromCard(int amount) {
//    return {[=] (BattleContext &bc) {
//
//        // TODO
//        bc.player.block += amount;
//    };
//}

Action Actions::DrawCards(int amount) {
    return {[=] (BattleContext &bc) {
        bc.drawCards(amount);
    }};
}

Action Actions::EmptyDeckShuffle() {
    return {[=] (BattleContext &bc) {
        java::Collections::shuffle(
                bc.cards.discardPile.begin(),
                bc.cards.discardPile.end(),
                java::Random(bc.shuffleRng.randomLong())
        );

        bc.cards.moveDiscardPileIntoToDrawPile();
    }};
}

Action Actions::ShuffleDrawPile() {
    return {[=] (BattleContext &bc) {
        java::Collections::shuffle(
                bc.cards.drawPile.begin(),
                bc.cards.drawPile.end(),
                java::Random(bc.shuffleRng.randomLong())
        );
    }};
}

Action Actions::ShuffleTempCardIntoDrawPile(CardId id, int count) {
    return {[=] (BattleContext &bc) {
        CardInstance c(id);
        for (int i = 0; i < count; ++i) {
            const int idx = bc.cards.drawPile.empty() ? 0 : bc.cardRandomRng.random(static_cast<int>(bc.cards.drawPile.size()-1));
            bc.cards.createTempCardInDrawPile(idx, c);
        }
    }};
}

Action Actions::PlayTopCard(int monsterTargetIdx, bool exhausts) {
    return {[=](BattleContext &bc) {
        bc.playTopCardInDrawPile(monsterTargetIdx, exhausts);
    }};
}


// todo fix the arguments are used wrongly all over ? what did this mean
// todo check for master reality
Action Actions::MakeTempCardInHand(CardId card, bool upgraded, int amount) {
    CardInstance c(card, upgraded);
    return Actions::MakeTempCardInHand(c, amount);
}

Action Actions::MakeTempCardInHand(CardInstance card, int amount) {
    // todo master reality when the action is created
    return {[=](BattleContext &bc) {
        for (int i = 0; i < amount; ++i) {
            CardInstance c(card);
            bc.configureCostsOnNewCard(c);
            c.uniqueId = bc.cards.nextUniqueCardId++;
            bc.cards.notifyAddCardToCombat(c);
            bc.moveToHandHelper(c);
        }
    }};
}

Action Actions::MakeTempCardInDrawPile(const CardInstance &c, int amount, bool shuffleInto) {
    // the random calculation is done in an effect so it be wrong to do it here?
    return {[=](BattleContext &bc) {
        for (int i = 0; i < amount; ++i) {
            if (shuffleInto) {
                CardInstance card(c);
                bc.configureCostsOnNewCard(card);
                const int idx = bc.cards.drawPile.empty() ? 0 : bc.cardRandomRng.random(static_cast<int>(bc.cards.drawPile.size()-1));
                bc.cards.createTempCardInDrawPile(idx, card);
            }
            // todo else
        }
    }};
}

Action Actions::MakeTempCardInDiscard(const CardInstance &c, int amount) {
    CardInstance c_copy(c);
    return {[=](BattleContext &bc) { // todo maybe store by reference instead of copy if CardInstance is too big to fit directly in the lambda. Make a seperate space where the card will be stored
        for (int i = 0; i < amount; ++i) {
            auto card = c_copy;
            bc.configureCostsOnNewCard(card);
            bc.cards.createTempCardInDiscard(card);
        }
    }};
}

Action Actions::MakeTempCardsInHand(std::vector<CardInstance> cards) {
    return {[=](BattleContext &bc) {
        for (auto c : cards) {
            bc.configureCostsOnNewCard(c);
            c.uniqueId = bc.cards.nextUniqueCardId++;
            bc.cards.notifyAddCardToCombat(c);
            bc.moveToHandHelper(c);
        }
    }};
}


Action Actions::DiscardNoTriggerCard() {
    return {[](BattleContext &bc) {
        const auto &c = bc.curCardQueueItem.card;
        bc.cards.notifyRemoveFromHand(c);
        bc.cards.moveToDiscardPile(c);
    }};
}

Action Actions::DiscardCardAtIdx(int handIdx) {
    return {[=] (BattleContext &bc) {
        if (handIdx < 0 || handIdx >= bc.cards.cardsInHand) {
            return;
        }
        const auto c = bc.cards.hand[handIdx];
        bc.cards.removeFromHandAtIdx(handIdx);
        bc.onManualDiscard(c);
        bc.cards.moveToDiscardPile(c);
        ++bc.player.cardsDiscardedThisTurn;
    }};
}

Action Actions::ClearCardQueue() {
    return {[](BattleContext &bc) {
        bc.cardQueue.clear();
    }};
}

Action Actions::DiscardAtEndOfTurn() {
    return {[] (BattleContext &bc) {
        bc.discardAtEndOfTurn();
    }};
}

Action Actions::DiscardAtEndOfTurnHelper() {
    return {[] (BattleContext &bc) {
        bc.discardAtEndOfTurnHelper();
    }};
}

Action Actions::RestoreRetainedCards(int count) {
    return {[=] (BattleContext &bc) {
        bc.restoreRetainedCards(count);
    }};
}

Action Actions::UnnamedEndOfTurnAction() {
    // EndTurnAction does this:
    //        AbstractDungeon.player.resetControllerValues();
    //        this.turnHasEnded = true;
    //        playerHpLastTurn = AbstractDungeon.player.currentHealth;

    return {[=] (BattleContext &bc) {
        bc.turnHasEnded = true;
        if (!bc.skipMonsterTurn) {
            bc.addToBot(Actions::MonsterStartTurnAction());
            bc.monsterTurnIdx = 0; // monstergroup preincrements this
        }
    }};
}

Action Actions::MonsterStartTurnAction() {
    return {[] (BattleContext &bc) {
        bc.monsters.applyPreTurnLogic(bc);
    }};
}

Action Actions::TriggerEndOfTurnOrbsAction() {
    return {[] (BattleContext &bc) {
        // all orbs trigger their passive at the end of the player's turn,
        // leftmost first. Plasma's passive triggers at the start of the turn.
        const int count = bc.player.getOrbCount();
        for (int i = 0; i < count; ++i) {
            if (bc.player.orbTypes[i] == Orb::PLASMA) {
                continue;
            }
            bc.player.triggerOrbPassive(bc, i);
        }
    }};
}

Action Actions::TriggerOrbPassive(int orbIdx, int times) {
    return {[=] (BattleContext &bc) {
        for (int i = 0; i < times; ++i) {
            bc.player.triggerOrbPassive(bc, orbIdx);
        }
    }};
}

Action Actions::ChannelOrb(Orb orb, int count) {
    return {[=] (BattleContext &bc) {
        for (int i = 0; i < count; ++i) {
            bc.player.channelOrb(bc, orb);
        }
    }};
}

Action Actions::EvokeNextOrb(int times) {
    return {[=] (BattleContext &bc) {
        // "your next Orb" is orbs[0], the oldest orb. Its evoke effect
        // repeats `times` times, then the orb is removed.
        const int idx = bc.player.getFirstOrbIdx();
        if (idx < 0) {
            return;
        }
        for (int i = 0; i < times; ++i) {
            bc.player.triggerOrbEvoke(bc, idx);
        }
        bc.player.removeOrb(idx);
    }};
}

Action Actions::RecursionAction() {
    return {[=] (BattleContext &bc) {
        // Evoke your next Orb (orbs[0]) and channel the Orb that was just
        // evoked, keeping its stored charge (dark charge is preserved).
        const int idx = bc.player.getFirstOrbIdx();
        if (idx < 0) {
            return;
        }
        const Orb orb = bc.player.orbTypes[idx];
        const int data = bc.player.orbData[idx];
        bc.player.evokeOrb(bc, idx);
        bc.player.channelOrbWithData(bc, orb, data);
    }};
}

Action Actions::ChaosAction(int count) {
    return {[=] (BattleContext &bc) {
        const Orb orbs[] = {Orb::LIGHTNING, Orb::FROST, Orb::DARK, Orb::PLASMA};
        for (int i = 0; i < count; ++i) {
            const Orb orb = orbs[bc.cardRandomRng.random(3)];
            bc.player.channelOrb(bc, orb);
        }
    }};
}

namespace {
    int applyLockOn(int damage, const Monster &target) {
        if (target.hasStatus<MS::LOCK_ON>()) {
            return static_cast<int>(damage * 1.5);
        }
        return damage;
    }
}

Action Actions::OrbLightningDamageRandom(int damage) {
    return {[=] (BattleContext &bc) {
        const int idx = bc.monsters.getRandomMonsterIdx(bc.cardRandomRng, true);
        if (idx == -1) {
            return;
        }
        bc.monsters.arr[idx].damage(bc, applyLockOn(damage, bc.monsters.arr[idx]));
        bc.checkCombat();
    }};
}

Action Actions::OrbLightningAttackAllEnemy(int damage) {
    return {[=] (BattleContext &bc) {
        // Electrodynamics: Lightning orbs hit ALL enemies, Lock-On applied per target
        for (int i = 0; i < bc.monsters.monsterCount; ++i) {
            if (bc.monsters.arr[i].isDeadOrEscaped()) {
                continue;
            }
            bc.monsters.arr[i].damage(bc, applyLockOn(damage, bc.monsters.arr[i]));
        }
        bc.checkCombat();
    }};
}

Action Actions::OrbDarkDamageLowestHp(int damage) {
    return {[=] (BattleContext &bc) {
        // Dark deals its damage to the living enemy with the lowest HP
        int bestIdx = -1;
        int bestHp = std::numeric_limits<int>::max();
        for (int i = 0; i < bc.monsters.monsterCount; ++i) {
            const auto &m = bc.monsters.arr[i];
            if (m.isDeadOrEscaped() || !m.isTargetable()) {
                continue;
            }
            if (m.curHp < bestHp) {
                bestHp = m.curHp;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            return;
        }
        bc.monsters.arr[bestIdx].damage(bc, applyLockOn(damage, bc.monsters.arr[bestIdx]));
        bc.checkCombat();
    }};
}

Action Actions::RandomTargetAttack(int baseDamage, CardInstance card) {
    return {[=] (BattleContext &bc) {
        const int idx = bc.monsters.getRandomMonsterIdx(bc.cardRandomRng, true);
        if (idx == -1) {
            return;
        }
        const int dmg = bc.calculateCardDamage(card, idx, baseDamage);
        bc.monsters.arr[idx].attacked(bc, dmg);
        bc.checkCombat();
    }};
}

Action Actions::FissionAction(int drawPerOrb, bool upgraded) {
    return {[=] (BattleContext &bc) {
        const int count = bc.player.getOrbCount();
        if (count <= 0) {
            return;
        }
        if (upgraded) {
            // Evoke all orbs, leftmost first
            for (int i = 0; i < count; ++i) {
                bc.player.evokeOrb(bc, 0);
            }
        } else {
            // unupgraded removes the orbs without evoking them
            for (int i = 0; i < count; ++i) {
                bc.player.removeOrb(0);
            }
        }
        bc.player.gainEnergy(count);
        for (int i = 0; i < count * drawPerOrb; ++i) {
            bc.addToBot(Actions::DrawCards(1));
        }
    }};
}

Action Actions::RebootAction(int drawCount) {
    return {[=] (BattleContext &bc) {
        // ShuffleAllAction returns the hand and discard pile to the draw pile
        // without discard triggers, fires onShuffle relics once, then Reboot's
        // ShuffleAction shuffles the combined draw pile.
        bc.onShuffle();

        while (bc.cards.cardsInHand > 0) {
            // ShuffleAllAction uses PutOnDeckAction(..., isRandom=true), so
            // returning the hand consumes cardRandomRng once per card.
            const int idx = bc.cardRandomRng.random(
                bc.cards.cardsInHand - 1
            );
            const auto c = bc.cards.hand[idx];
            bc.cards.removeFromHandAtIdx(idx);
            bc.cards.moveToDrawPileTop(c);
        }

        java::Collections::shuffle(
            bc.cards.discardPile.begin(),
            bc.cards.discardPile.end(),
            java::Random(bc.shuffleRng.randomLong())
        );
        bc.cards.moveDiscardPileIntoToDrawPile();

        java::Collections::shuffle(
            bc.cards.drawPile.begin(),
            bc.cards.drawPile.end(),
            java::Random(bc.shuffleRng.randomLong())
        );

        bc.addToBot(Actions::DrawCards(drawCount));
    }};
}

Action Actions::ExhaustTopCardInHand() {
    return {[=] (BattleContext &bc) {
        bc.exhaustTopCardInHand();
    }};
}

Action Actions::ExhaustSpecificCardInHand(int idx, std::int16_t uniqueId) {
    return {[=] (BattleContext &bc) {
        bc.exhaustSpecificCardInHand(idx, uniqueId);
    }};
}

Action Actions::DamageRandomEnemy(int damage) {
    return {[=] (BattleContext &bc) {
        const int idx = bc.monsters.getRandomMonsterIdx(bc.cardRandomRng, true);
        if (idx == -1) {
            return;
        }
        bc.monsters.arr[idx].damage(bc, damage);
        bc.checkCombat();
    }};
}

Action Actions::ExhaustRandomCardInHand(int count) {
    return {[=] (BattleContext &bc) {
        for (int i = 0; i < count; ++i) {
            if (bc.cards.cardsInHand <= 0) {
                return;
            }
            const auto idx = bc.cards.getRandomCardIdxInHand(bc.cardRandomRng);
            auto c = bc.cards.hand[idx];
            bc.cards.removeFromHandAtIdx(idx);
            bc.triggerAndMoveToExhaustPile(c);
        }
    }};
}

Action Actions::MadnessAction() {
    return {[=] (BattleContext &bc) {

        bool haveNonZeroCost = false;
        bool haveNonZeroTurnCost = false;
        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            const auto &c = bc.cards.hand[i];

            if (c.costForTurn > 0) {
                haveNonZeroTurnCost = true;
                break;
            }

            if (c.cost > 0) {
                haveNonZeroCost = true;
            }
        }

        const auto haveValidCard = haveNonZeroCost || haveNonZeroTurnCost;
        if (!haveValidCard) {
            return;
        }

        // always have 1 or more cards in hand here
        while (true) {
            const auto randomIdx = bc.cardRandomRng.random(bc.cards.cardsInHand-1);
            auto &c = bc.cards.hand[randomIdx];

            if (haveNonZeroTurnCost) {
                if (c.costForTurn > 0) {
                    c.cost = 0;
                    c.costForTurn = 0;
                    break;
                } else {
                    continue;
                }

            } else {
                if (c.cost > 0) {
                    c.cost = 0;
                    c.costForTurn = 0;
                    break;

                } else {
                    continue;
                }
            }
        }

    }};
}

Action Actions::RandomizeHandCost() {
    return {[=] (BattleContext &bc) {
        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            auto &c = bc.cards.hand[i];
            if (c.cost >= 0) {
                int newCost = bc.cardRandomRng.random(3);
                // RandomizeHandCostAction changes the card's combat cost, not
                // merely a one-turn discount. This is the behavior used by
                // Snecko Oil in the base game.
                if (c.cost != newCost) {
                    c.cost = newCost;
                    c.costForTurn = newCost;
                }
            }
        }
    }};
}

Action Actions::GainBlockRandomEnemy(int sourceMonster, int amount) {
    return {[=] (BattleContext &bc) {
        int validIdxs[5];
        int validCount = 0;

        for (int i = 0; i < bc.monsters.monsterCount; ++i) {
            const auto &m = bc.monsters.arr[i];
            if (i != sourceMonster && !m.isDying()) {
                validIdxs[validCount++] = i;
            }
        }

        int targetIdx;
        if (validCount > 0) {
            targetIdx = validIdxs[bc.aiRng.random(validCount - 1)];
        } else {
            targetIdx = sourceMonster;
        }

        bc.monsters.arr[targetIdx].addBlock(amount);
    }};
}

Action Actions::SummonGremlins() {
    // gremlin leader searches in the order 1, 2, 0 for open space

    return {[=] (BattleContext &bc) {
        int openIdxCount = 0;
        int newGremlinIdxs[2] {};
        if (bc.monsters.arr[1].isDying()) {
            newGremlinIdxs[openIdxCount++] = 1;
        }
        if (bc.monsters.arr[2].isDying()) {
            newGremlinIdxs[openIdxCount++] = 2;
        }
        if (openIdxCount < 2 && bc.monsters.arr[0].isDying()) {
            newGremlinIdxs[openIdxCount++] = 0;
        }
#ifdef sts_asserts
        assert(openIdxCount == 2);
#endif

        auto &gremlin0 = bc.monsters.arr[newGremlinIdxs[0]];
        auto &gremlin1 = bc.monsters.arr[newGremlinIdxs[1]];

        constexpr double positions[]={-532,-366,-170};
        notifyMonsterSpawn(bc,newGremlinIdxs[0],positions[newGremlinIdxs[0]]);
        notifyMonsterSpawn(bc,newGremlinIdxs[1],positions[newGremlinIdxs[1]]);
        gremlin0 = Monster();
        gremlin1 = Monster();

        gremlin0.construct(bc, MonsterGroup::getGremlin(bc.aiRng), newGremlinIdxs[0]);
        gremlin1.construct(bc, MonsterGroup::getGremlin(bc.aiRng), newGremlinIdxs[1]);
        bc.monsters.monstersAlive += 2;

        if (bc.player.hasRelic<R::PHILOSOPHERS_STONE>()) {
            gremlin0.buff<MS::STRENGTH>(1);
            gremlin1.buff<MS::STRENGTH>(1);
        }
        gremlin0.buff<MS::MINION>();
        gremlin1.buff<MS::MINION>();

        gremlin0.rollMove(bc);
        gremlin1.rollMove(bc);
    }};
}

Action Actions::SpawnTorchHeads() {
    return {[=] (BattleContext &bc) {
        const auto spawnCount = 3-bc.monsters.monstersAlive;
#ifdef sts_asserts
        assert(spawnCount > 0);
#endif
        const int spawnIdxs[2] {(bc.monsters.arr[1].isDying() ? 1 : 0), 0};

        for (int i = 0; i < spawnCount; ++i) {
            const auto idx = spawnIdxs[i];
            auto &torchHead = bc.monsters.arr[idx];
            notifyMonsterSpawn(bc,idx,idx==0?-470:-285);
            torchHead = Monster();
            torchHead.construct(bc, MonsterId::TORCH_HEAD, idx);
            torchHead.initHp(bc.monsterHpRng, bc.ascension); // bug somewhere in game
            torchHead.setMove(MMID::TORCH_HEAD_TACKLE);
            torchHead.buff<MS::MINION>();

            if (bc.player.hasRelic<R::PHILOSOPHERS_STONE>()) {
                torchHead.buff<MS::STRENGTH>(1);
            }
            ++bc.monsters.monstersAlive;
        }

        for (int i = 0; i < spawnCount; ++i) {
            bc.noOpRollMove();
        }
    }};
}

Action Actions::SpireShieldDebuff() {
    return {[] (BattleContext &bc) {
        if (bc.aiRng.randomBoolean()) {
            bc.player.debuff<PS::FOCUS>(-1);
        } else {
            bc.player.debuff<PS::STRENGTH>(-1);
        }
    }};
}


Action Actions::OnAfterCardUsed() {
    return {[] (BattleContext &bc) {
        bc.onAfterUseCard();
    }, false};
}

Action Actions::PutRandomCardsInDrawPile(CardType type, int count) {
    return {[=] (BattleContext &bc) {
        CardId ids[5];
        for (int i = 0; i < count; ++i) {
            ids[i] = getTrulyRandomCardInCombat(bc.cardRandomRng, bc.player.cc, type);
        }

        for (int i = 0; i < count; ++i) {
            CardInstance card(ids[i], false);
            bc.configureCostsOnNewCard(card);
            card.cost = 0;
            card.costForTurn = 0;

            const int idx = bc.cards.drawPile.empty() ? 0 : bc.cardRandomRng.random(static_cast<int>(bc.cards.drawPile.size()-1));
            bc.cards.createTempCardInDrawPile(idx, card);
        }
    }};
}

Action Actions::DiscoveryAction(CardType type, int amount) {
    return {[=] (BattleContext &bc) {
        bc.haveUsedDiscoveryAction = true;
        bc.openDiscoveryScreen(sts::generateDiscoveryCards(bc.cardRandomRng, bc.player.cc, type), amount);
    }};
}

Action Actions::InfernalBladeAction() {
    return {[=] (BattleContext &bc) {
        const auto cid = getTrulyRandomCardInCombat(bc.cardRandomRng, bc.player.cc, CardType::ATTACK);
        CardInstance c(cid);
        c.setCostForTurn(0);
        bc.addToTop( Actions::MakeTempCardInHand(c) );
    }};
}

Action Actions::JackOfAllTradesAction(bool upgraded) {
    return {[=] (BattleContext &bc) {
        const auto c1 = sts::getTrulyRandomColorlessCardInCombat(bc.cardRandomRng);
        bc.addToTop( Actions::MakeTempCardInHand(c1) );
        if (upgraded) {
            auto c2 = sts::getTrulyRandomColorlessCardInCombat(bc.cardRandomRng);
            bc.addToTop( Actions::MakeTempCardInHand(c2) );
        }
    }};
}

Action Actions::TransmutationAction(bool upgraded, int energy, bool useEnergy) {
    return {[=] (BattleContext &bc) {
        const auto effectAmount = energy + (bc.player.hasRelic<R::CHEMICAL_X>() ? 2 : 0);

        if (effectAmount == 0) {
            return;
        }

        std::vector<CardInstance> cards;
       for (int i = 0; i < effectAmount; ++i) {
           const auto cid = sts::getTrulyRandomColorlessCardInCombat(bc.cardRandomRng);
           CardInstance c(cid, upgraded);
           c.setCostForTurn(0);
           cards.push_back(c);
       }
       bc.addToBot( Actions::MakeTempCardsInHand(cards) );

       if (useEnergy) {
           bc.player.useEnergy(bc.player.energy);
       }
    }};
}

Action Actions::ViolenceAction(int count) { // todo a faster algorithm for inserting into the attack list
    return {[=] (BattleContext &bc) {
        fixed_list<int,CardManager::MAX_GROUP_SIZE> attackIdxList;
        for (std::size_t i = 0; i < bc.cards.drawPile.size(); ++i) {
            const auto &c = bc.cards.drawPile[i];
            if (c.getType() == CardType::ATTACK) {

                if (attackIdxList.empty()) {
                    attackIdxList.push_back(static_cast<int>(i));
                } else {
                    const auto randomIdx = bc.cardRandomRng.random(attackIdxList.size() - 1);
                    attackIdxList.insert(randomIdx, static_cast<int>(i));
                }
            }
        }

        if (attackIdxList.empty()) {
            return;
        }

        std::vector<int> removeIdxs;
        // hack to do this faster: the attackList is just pushed forward by i so we skip removing from bottom
        int i = 0;
        for (; i < count; ++i) {
            if (attackIdxList.size()-i <= 0) {
                break;
            }

            java::Collections::shuffle(attackIdxList.begin()+i, attackIdxList.end(), java::Random(bc.shuffleRng.randomLong()));
            const auto removeIdx = attackIdxList[i];
            removeIdxs.push_back(removeIdx);

            const auto &c = bc.cards.drawPile[removeIdx];
            if (bc.cards.cardsInHand == 10) {
                bc.cards.moveToDiscardPile(c);
            } else {
                bc.cards.moveToHand(c);
            }
        }

        std::sort(removeIdxs.begin(), removeIdxs.end());
        for (int x = static_cast<int>(removeIdxs.size())-1; x >= 0; --x) {
            const auto drawPileRemoveIdx = removeIdxs[x];
            bc.cards.removeFromDrawPileAtIdx(drawPileRemoveIdx);
        }

    }};
}

Action Actions::BetterDiscardPileToHandAction(int count, CardSelectTask task) {
    return {[=] (BattleContext &bc) {
        if (bc.cards.discardPile.empty()) {
            return;
        }
        if (static_cast<int>(bc.cards.discardPile.size()) <= count) {
            while (!bc.cards.discardPile.empty() && bc.cards.cardsInHand < CardManager::MAX_HAND_SIZE) {
                bc.chooseDiscardToHandCard(0, task==CardSelectTask::LIQUID_MEMORIES_POTION);
            }
        } else {
            bc.openSimpleCardSelectScreen(task, count);
        }
    }};
}

Action Actions::ArmamentsAction() {
    return {[=] (BattleContext &bc) {
        int canUpgradeCount = 0;
        int lastUpgradeIdx = 0;
        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            if (bc.cards.hand[i].canUpgrade()) {
                ++canUpgradeCount;
                lastUpgradeIdx = i;
            }
        }

        if (canUpgradeCount == 0) {
            // do nothing

        } else if (canUpgradeCount == 1) {
            bc.cards.hand[lastUpgradeIdx].upgrade();

        } else {
            bc.openSimpleCardSelectScreen(CardSelectTask::ARMAMENTS, 1);
        }
    }};
}

Action Actions::DualWieldAction(int copyCount) {
    return {[=] (BattleContext &bc) {

//        fixed_list<CardInstance, 10> validCards;
//        fixed_list<CardInstance, 10> invalidCards;
//
//        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
//            const bool valid = bc.cards.hand[i].getType() == CardType::ATTACK || bc.cards.hand[i].getType() == CardType::POWER;
//            if (valid) {
//                validCards.push_back(bc.cards.hand[i]);
//
//            } else {
//                invalidCards.push_back(bc.cards.hand[i]);
//            }
//        }

        int validCount = 0;
        int lastValidIdx = 0;

        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            const bool valid = bc.cards.hand[i].getType() == CardType::ATTACK || bc.cards.hand[i].getType() == CardType::POWER;
            if (valid) {
                ++validCount;
                lastValidIdx = i;
            }
        }

        if (validCount == 0) {
            return;
        }

        if (validCount == 1) {
            for (int i = 0; i < copyCount; ++i) {
                auto c = bc.cards.hand[lastValidIdx];
                if (bc.cards.cardsInHand + 1 <= CardManager::MAX_HAND_SIZE) {
                    bc.cards.createTempCardInHand(c);

                } else {
                    bc.cards.createTempCardInDiscard(c);

                }
            }

        } else {
            bc.inputState = InputState::CARD_SELECT;
            bc.cardSelectInfo.cardSelectTask = CardSelectTask::DUAL_WIELD;
            bc.cardSelectInfo.dualWield_CopyCount() = copyCount;

        }


    }};
}

Action Actions::ExhumeAction() { // todo this is bugged because the selected card cannot be exhume
    return {[=] (BattleContext &bc) {
        if (bc.cards.exhaustPile.empty() || bc.cards.cardsInHand == 10) {
            return;
        }

        int nonExhumeCards = 0;
        int lastNonExhumeIdx = -1;
        for (std::size_t i = 0; i < bc.cards.exhaustPile.size(); ++i) {
            if (bc.cards.exhaustPile[i].id != CardId::EXHUME) {
                ++nonExhumeCards;
                lastNonExhumeIdx = static_cast<int>(i);
            }
        }

        if (nonExhumeCards == 0) {
            return;

        } else if (nonExhumeCards == 1) {
            bc.chooseExhumeCard(lastNonExhumeIdx);

        } else {
            bc.cardSelectInfo.cardSelectTask = CardSelectTask::EXHUME;
            bc.inputState = InputState::CARD_SELECT;
        }

    }};
}

Action Actions::ForethoughtAction(bool) {
    return {[=] (BattleContext &bc) {
        if (bc.cards.cardsInHand == 0) {
            return;
        }

        // todo implement Upgraded version
//        //
//        if (upgraded) {
//            bc.cardSelectInfo.cardSelectTask = CardSelectTask::FORETHOUGHT;
//            bc.cardSelectInfo.canPickAnyNumber = true;
//            bc.inputState = InputState::CARD_SELECT;
//
//        } else {
        if (bc.cards.cardsInHand == 1) {
            bc.chooseForethoughtCard(0);
        } else {
            bc.cardSelectInfo.cardSelectTask = CardSelectTask::FORETHOUGHT;
            bc.cardSelectInfo.canPickAnyNumber = false;
            bc.inputState = InputState::CARD_SELECT;
        }
//        }


    }};
}

Action Actions::HeadbuttAction() {
    return {[=] (BattleContext &bc) {
        if (bc.cards.discardPile.empty()) {
            return;

        } else if (bc.cards.discardPile.size() == 1) {
            bc.chooseHeadbuttCard(0);
        } else {
            bc.openSimpleCardSelectScreen(CardSelectTask::HEADBUTT, 1);
        }
    }};
}

Action Actions::ChooseExhaustOne() {
    return {[=] (BattleContext &bc) {
        if (bc.cards.cardsInHand == 0) {
            return;

        } else if (bc.cards.cardsInHand == 1) {
            bc.chooseExhaustOneCard(0);

        } else {
            bc.openSimpleCardSelectScreen(CardSelectTask::EXHAUST_ONE, 1);

        }
    }};
}

Action Actions::DrawToHandAction(CardSelectTask task, CardType cardType) {
    return {[=] (BattleContext &bc) {
        int count = 0;
        int idx = 0;

        for (std::size_t i = 0; i < bc.cards.drawPile.size(); ++i) {
            const auto &c = bc.cards.drawPile[i];
            if (c.getType() == cardType) {
                if (count > 0) {
                    // for keeping rng consistent with game
                    // the game creates a temporary list with the skills
                    bc.cardRandomRng.random(count - 1);
                }
                idx = static_cast<int>(i);
                ++count;
            }
        }

        if (count == 0) {
            return;
        }

        if (count == 1) {
            bc.chooseDrawToHandCards(&idx, 1);

        } else {
            bc.cardSelectInfo.cardSelectTask = task;
            bc.inputState = InputState::CARD_SELECT;
        }

    }};
}

Action Actions::WarcryAction() {
    return {[=] (BattleContext &bc) {
        // todo if the handSize equals or less than the cardsToChoose just choose them here
        if (bc.cards.cardsInHand == 0) {
            return;
        }

        if (bc.cards.cardsInHand == 1) {
            bc.cardRandomRng.random(1);
            bc.chooseWarcryCard(0);

        } else {
            bc.inputState = InputState::CARD_SELECT;
            bc.cardSelectInfo.cardSelectTask = CardSelectTask::WARCRY;
        }
    }};
}

Action Actions::TimeEaterPlayCardQueueItem(const CardQueueItem &x) {
    return {[=] (BattleContext &bc) {
        auto item = x;
        item.exhaustOnUse |= bc.curCardQueueItem.card.doesExhaust();
        item.triggerOnUse = false;
        bc.curCardQueueItem = item;
        bc.onAfterUseCard();

    }, false};
}

Action Actions::UpgradeAllCardsInHand() {
    return {[=] (BattleContext &bc) {
        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            if (bc.cards.hand[i].canUpgrade()) bc.cards.hand[i].upgrade();
        }
    }};
}

Action Actions::EssenceOfDarkness(int darkOrbsPerSlot) {
    return {[=] (BattleContext &bc) {
        const int total = static_cast<int>(bc.player.orbSlots) * darkOrbsPerSlot;
        for (int i = 0; i < total; ++i) {
            bc.player.channelOrb(bc, Orb::DARK);
        }
    }};
}

Action Actions::IncreaseOrbSlots(int count) {
    return {[=] (BattleContext &bc) {
        bc.player.increaseOrbSlots(count);
    }};
}

Action Actions::SunderAction(int targetIdx, int energy) {
    return {[=] (BattleContext &bc) {
        if (targetIdx >= 0 && targetIdx < bc.monsters.monsterCount
            && bc.monsters.arr[targetIdx].isDeadOrEscaped()) {
            bc.player.gainEnergy(energy);
        }
    }};
}

Action Actions::AllForOneAction() {
    return {[=] (BattleContext &bc) {
        // put all base-cost 0 cards from the discard pile into the hand
        for (int i = static_cast<int>(bc.cards.discardPile.size()) - 1; i >= 0; --i) {
            auto &c = bc.cards.discardPile[i];
            if (c.cost == 0 || c.freeToPlayOnce) {
                CardInstance card = c;
                bc.cards.removeFromDiscard(i);
                bc.moveToHandHelper(card);
            }
        }
    }};
}

Action Actions::ScrapeAction(int drawCount) {
    return {[=] (BattleContext &bc) {
        // cards drawn by Scrape occupy the hand indices after the current hand
        const int oldHandSize = bc.cards.cardsInHand;
        bc.addToBot(Actions::DrawCards(drawCount));
        bc.addToBot({[=] (BattleContext &bc2) {
            int i = oldHandSize;
            while (i < bc2.cards.cardsInHand) {
                const auto &c = bc2.cards.hand[i];
                if (c.costForTurn != 0 && !c.freeToPlayOnce) {
                    const auto card = c;
                    bc2.cards.removeFromHandAtIdx(i);
                    bc2.onManualDiscard(card);
                    bc2.cards.moveToDiscardPile(card);
                    ++bc2.player.cardsDiscardedThisTurn;
                } else {
                    ++i;
                }
            }
        }});
    }};
}

Action Actions::WhiteNoiseAction(int count) {
    return {[=] (BattleContext &bc) {
        for (int i = 0; i < count; ++i) {
            const auto cardId = getTrulyRandomCardInCombat(bc.cardRandomRng, bc.player.cc, CardType::POWER);
            CardInstance c(cardId);
            c.setCostForTurn(0);
            bc.addToBot(Actions::MakeTempCardInHand(c));
        }
    }};
}

Action Actions::RecycleCardSelectAction() {
    return {[=] (BattleContext &bc) {
        if (bc.cards.cardsInHand <= 0) {
            return;
        }
        if (bc.cards.cardsInHand == 1) {
            bc.chooseRecycleCard(0);
        } else {
            bc.openSimpleCardSelectScreen(CardSelectTask::RECYCLE, 1);
        }
    }};
}

Action Actions::SeekAction(int count) {
    return {[=] (BattleContext &bc) {
        if (bc.cards.drawPile.empty()) {
            return;
        }
        const int toSelect = std::min(count, static_cast<int>(bc.cards.drawPile.size()));
        if (toSelect == 1 && bc.cards.drawPile.size() == 1) {
            const int idx = 0;
            bc.chooseDrawToHandCards(&idx, 1);
        } else {
            bc.openSimpleCardSelectScreen(CardSelectTask::SEEK, toSelect);
        }
    }};
}

Action Actions::SuicideAction(int monsterIdx, bool triggerRelics) {
    return {[=] (BattleContext &bc) {
        auto &m = bc.monsters.arr[monsterIdx];
        if (triggerRelics) {
            if (m.isAlive()) {
                m.damage(bc, m.curHp);
            }
        } else {
            bc.monsters.arr[monsterIdx].suicideAction(bc);
        }
    }};
}

Action Actions::PoisonLoseHpAction() {
    return sts::Action();
}

Action Actions::RemovePlayerDebuffs() {
    return {[] (BattleContext &bc) {
        bc.player.removeDebuffs();
    }};
}

Action Actions::UpgradeRandomCardAction() {
    return {[] (BattleContext &bc) {
        fixed_list<int,10> upgradeableHandIdxs;
        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            if (bc.cards.hand[i].canUpgrade()) {
                upgradeableHandIdxs.push_back(i);
            }
        }

        if (upgradeableHandIdxs.empty()) {
            return;
        }

        java::Collections::shuffle(
                upgradeableHandIdxs.begin(),
                upgradeableHandIdxs.end(),
                java::Random(bc.shuffleRng.randomLong())
        );

        const auto upgradeIdx = upgradeableHandIdxs[0];
        bc.cards.hand[upgradeIdx].upgrade();
    }};
}

Action Actions::CodexAction() {
    return {[] (BattleContext &bc) {
        bc.inputState = InputState::CARD_SELECT;
        bc.cardSelectInfo.cardSelectTask = CardSelectTask::CODEX;
        bc.cardSelectInfo.codexCards() =
                generateDiscoveryCards(bc.cardRandomRng, CharacterClass::IRONCLAD, CardType::INVALID);
    }};
}

Action Actions::ExhaustMany(int limit) {
    return {[=] (BattleContext &bc) {
        bc.inputState = InputState::CARD_SELECT;
        bc.cardSelectInfo.cardSelectTask = CardSelectTask::EXHAUST_MANY;
        bc.cardSelectInfo.pickCount = limit;
    }};
}

Action Actions::GambleAction() {
    return {[] (BattleContext &bc) {
        bc.inputState = InputState::CARD_SELECT;
        bc.cardSelectInfo.cardSelectTask = CardSelectTask::GAMBLE;
    }};
}

Action Actions::ToolboxAction() {
    return {[] (BattleContext &bc) {
        bc.openToolboxScreen(
            generateDiscoveryCards(
                bc.cardRandomRng,
                bc.player.cc,
                CardType::STATUS
            )
        ); // status is mapped to colorless
    }};
}

Action Actions::DualityAction() {
    return {[](BattleContext &bc) {
        bc.player.buff<PS::DEXTERITY>(1);
        bc.player.debuff<PS::LOSE_DEXTERITY>(1);
    }};
}

Action Actions::ApotheosisAction() {
    return {[](BattleContext &bc) {

        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            auto &c = bc.cards.hand[i];
            if (c.canUpgrade()) {
                c.upgrade();
            }
        }

        for (auto &c : bc.cards.drawPile) {
            if (c.canUpgrade()) {
                c.upgrade();
            }
        }

        for (auto &c : bc.cards.discardPile) {
            if (c.canUpgrade()) {
                c.upgrade();
            }
        }

        for (auto &c : bc.cards.exhaustPile) {
            if (c.canUpgrade()) {
                c.upgrade();
            }
        }
    }};
}

Action Actions::DropkickAction(int targetIdx) {
    // assume bc.curCard is the card being used
    return {[=](BattleContext &bc) {
        if (bc.monsters.arr[targetIdx].isTargetable() && bc.monsters.arr[targetIdx].hasStatus<MS::VULNERABLE>()) {
            bc.addToTop(Actions::DrawCards(1));
            bc.addToTop(Actions::GainEnergy(1));
        }

        const auto &c = bc.curCardQueueItem.card;
        const int damage = bc.calculateCardDamage(c, targetIdx, c.isUpgraded() ? 8 : 5);
        bc.addToTop(Actions::AttackEnemy(targetIdx, damage));
    }};
}

Action Actions::EnlightenmentAction(bool upgraded) {
    return {[=] (BattleContext &bc) {
        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            auto &c = bc.cards.hand[i];
            if (c.costForTurn > 1) {
                c.costForTurn = 1;
            }
            if (upgraded && c.cost > 1) {
                c.costForTurn = 1;
                c.cost = 1;
            }
        }
    }};
}

Action Actions::EntrenchAction() {
    return {[=] (BattleContext &bc) {
        bc.player.gainBlock(bc, bc.player.block);
    }};
}

Action Actions::FeedAction(int idx, int damage, bool upgraded) {
    return {[=] (BattleContext &bc) {
        auto &m = bc.monsters.arr[idx];
        if (m.isDeadOrEscaped()) {
            return;
        }
        bc.monsters.arr[idx].attacked(bc, damage);

        const bool effectTriggered = !m.hasStatus<MS::MINION>()
                && !m.isAlive()
                && !m.isHalfDead()
                && !(m.hasStatus<MS::REGROW>() && bc.monsters.monstersAlive > 0);

        if (effectTriggered) {
            bc.player.increaseMaxHp(upgraded ? 4 : 3);
        }

        bc.checkCombat();
    }};
}

Action Actions::FiendFireAction(int targetIdx, int calculatedDamage) {
    return {[=] (BattleContext &bc) {
        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            bc.addToTop(Actions::AttackEnemy(targetIdx, calculatedDamage));
        }

        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            bc.addToTop( Actions::ExhaustRandomCardInHand(1) );
        }
    }};
}

Action Actions::HandOfGreedAction(int idx, int damage, bool upgraded) {
    return {[=] (BattleContext &bc) {
        auto &m = bc.monsters.arr[idx];
        if (m.isDeadOrEscaped()) {
            return;
        }
        bc.monsters.arr[idx].damage(bc, damage);

        const bool effectTriggered = !m.hasStatus<MS::MINION>()
                && !m.isAlive()
                && !m.isHalfDead()
                && !(m.hasStatus<MS::REGROW>() && bc.monsters.monstersAlive > 0);

        if (effectTriggered) {
            bc.player.gainGold(bc, upgraded ? 25 : 20);
        }

        bc.checkCombat();
    }};
}

Action Actions::LimitBreakAction() {
    return {[] (BattleContext &bc) {
        bc.player.buff<PS::STRENGTH>(bc.player.getStatus<PS::STRENGTH>());
    }};
}

Action Actions::ReaperAction(int baseDamage) {
    return {[=] (BattleContext &bc) {

        int healAmount = 0;
        for (int i = 0; i < bc.monsters.monsterCount; ++i) {
            auto &m = bc.monsters.arr[i];
            if (m.isDeadOrEscaped()) {
                continue;
            }
            int preDamageHp = m.curHp;
            m.attacked(bc, bc.calculateCardDamage(bc.curCardQueueItem.card, i, baseDamage));
            healAmount += preDamageHp-m.curHp;
        }

        if (healAmount > 0) {
            bc.addToBot( Actions::HealPlayer(healAmount) );
        }

        //if (AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()) {
        //                AbstractDungeon.actionManager.clearPostCombatActions();
        //            }
    }};
}

Action Actions::RitualDaggerAction(int idx, int damage) {
    return {[=] (BattleContext &bc) {
        auto &m = bc.monsters.arr[idx];
        if (m.isDeadOrEscaped()) {
            return;
        }
        bc.monsters.arr[idx].attacked(bc, damage);

        const bool shouldUpgrade = !m.hasStatus<MS::MINION>()
                                   && !m.isAlive()
                                   && !(m.hasStatus<MS::REGROW>() && bc.monsters.monstersAlive > 0);
        if (shouldUpgrade) {
            auto &c = bc.curCardQueueItem.card;
            const auto upgradeAmt = c.isUpgraded() ? 5 : 3;

            if (bc.curCardQueueItem.purgeOnUse) {
                bc.cards.findAndUpgradeSpecialData(c.uniqueId, upgradeAmt);
            }
            c.specialData += upgradeAmt;
        }

        bc.checkCombat();
    }};
}

Action Actions::SecondWindAction(int blockPerCard) {
    return {[=] (BattleContext &bc) {
        int cardIdxsToExhaust[10];
        int toExhaustCount = 0;

        for (int i = 0; i < bc.cards.cardsInHand; ++i) {
            const auto &c = bc.cards.hand[i];
            if (c.getType() != CardType::ATTACK) {
                cardIdxsToExhaust[toExhaustCount++] = i;
                bc.addToTop( Actions::GainBlock(blockPerCard) );
            }
        }

        for (int i = 0; i < toExhaustCount; ++i) {
            const auto handIdx = cardIdxsToExhaust[i];
            const auto &c = bc.cards.hand[handIdx];

            bc.addToTop( Actions::ExhaustSpecificCardInHand(handIdx, c.uniqueId) );
        }
    }};
}

Action Actions::SeverSoulExhaustAction() {
    return {[=] (BattleContext &bc) {
        for (int i = bc.cards.cardsInHand-1; i >= 0; --i) {
            const auto &c = bc.cards.hand[i];
            if (c.getType() != CardType::ATTACK) {
                bc.addToBot( Actions::ExhaustSpecificCardInHand(i, c.getUniqueId()) );
            }
        }
    }};
}

Action Actions::SwordBoomerangAction(int baseDamage) { // pretty hacky until I can figure out a better solution
    return {[=](BattleContext &bc) {
        const static CardInstance swordBoomerang {CardId::SWORD_BOOMERANG};
        const auto idx = bc.monsters.getRandomMonsterIdx(bc.cardRandomRng, true);
        if (idx == -1) {
            return;
        }

        int damage = bc.calculateCardDamage(swordBoomerang, idx, baseDamage);
        bc.addToTop(Actions::AttackEnemy(idx, damage));
    }};
}

Action Actions::SpotWeaknessAction(int target, int strength) {
    return {[=](BattleContext &bc) {
        if (bc.monsters.arr[target].isAttacking()) {
            bc.player.buff<PS::STRENGTH>(strength);
        }
    }};
}

Action Actions::WhirlwindAction(int baseDamage, int energy, bool useEnergy) {
    return {[=] (BattleContext &bc) {
        // assume bc.curCard is the card being used

        if (useEnergy) {
            bc.player.useEnergy(bc.player.energy);
        }

        DamageMatrix damageMatrix {0};
        for (int i = 0; i < bc.monsters.monsterCount; ++i) {
            if (!bc.monsters.arr[i].isDeadOrEscaped()) {
                const auto calcDamage = bc.calculateCardDamage(bc.curCardQueueItem.card, i, baseDamage);

                damageMatrix[i] = static_cast<std::uint16_t>( // fit damage into uint16
                    std::min(
                        static_cast<int>(std::numeric_limits<std::uint16_t>::max()),
                        calcDamage
                    )
                );
            }
        }

        const auto effectAmount = energy + (bc.player.hasRelic<R::CHEMICAL_X>() ? 2 : 0);
        if (effectAmount > 0) {
            Actions::AttackAllMonsterRecursive(damageMatrix, effectAmount).actFunc(bc);
        }
    }};
}

Action Actions::AttackAllMonsterRecursive(DamageMatrix matrix, int timesRemaining) {
    return {[=] (BattleContext &bc) {
        if (timesRemaining <= 0) {
            return;
        }

        Actions::AttackAllEnemy(matrix).actFunc(bc);

        if (timesRemaining > 1) {
            bc.addToTop(AttackAllMonsterRecursive(matrix, timesRemaining-1)); // todo should this be to the top? test with
        }
    }};
}
