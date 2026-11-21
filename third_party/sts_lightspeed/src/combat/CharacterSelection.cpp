#include "combat/CharacterSelection.h"
#include <algorithm>
#include <stdexcept>

using namespace sts;

namespace {
void fetchDrawCard(BattleContext &b, int id) {
    auto &pile=b.cards.drawPile;
    auto it=std::find_if(pile.begin(),pile.end(),[&](const CardInstance &c){return c.uniqueId==id;});
    if(it==pile.end())throw std::runtime_error("fetch_draw_card_identity");
    auto card=*it;b.cards.removeFromDrawPileAtIdx(int(it-pile.begin()));
    if(b.cards.cardsInHand<10)b.cards.moveToHand(card);
    else {card.costForTurn=card.cost;b.cards.moveToDiscardPile(card);}
}
}

bool sts::isCharacterSelection(CardSelectTask task) {
    if(isHeadlessSingleHand(task))return true;
    return task==CardSelectTask::DISCARD || task==CardSelectTask::WARCRY || task==CardSelectTask::SCRY || task==CardSelectTask::RETAIN
        || task==CardSelectTask::SETUP || task==CardSelectTask::MEDITATE || task==CardSelectTask::NIGHTMARE
        || task==CardSelectTask::OMNISCIENCE || task==CardSelectTask::FOREIGN_INFLUENCE || task==CardSelectTask::WISH
        || isHeadlessMultiHand(task) || isHeadlessFetchGrid(task) || task==CardSelectTask::LIQUID_MEMORIES_GRID || task==CardSelectTask::HEADBUTT_GRID;
}

void sts::sortHeadlessSeekOptions(std::vector<CardInstance> &options) {
    auto rarity=[](CardId id) {
        // Java CardRarity ordinal, not the native enum ordinal.
        switch(getCardRarity(id)) {
            case CardRarity::BASIC:return 0; case CardRarity::SPECIAL:return 1;
            case CardRarity::COMMON:return 2; case CardRarity::UNCOMMON:return 3;
            case CardRarity::RARE:return 4; case CardRarity::CURSE:return 5;
            default:throw std::runtime_error("unmapped_seek_rarity");
        }
    };
    auto name=[](const CardInstance &c) {
        auto n=c.getName();
        if(c.id==CardId::SEARING_BLOW && c.isUpgraded())n+=std::to_string(c.getUpgradeCount());
        return n;
    };
    std::stable_sort(options.begin(),options.end(),[&](const CardInstance &a,const CardInstance &b) {
        const bool sa=a.getType()==CardType::STATUS,sb=b.getType()==CardType::STATUS;
        if(sa!=sb)return !sa;  // StatusCardsLastComparator
        int ra=rarity(a.id),rb=rarity(b.id);if(ra!=rb)return ra>rb;
        return name(a)<name(b);
    });
}

void sts::openCharacterSelection(BattleContext &b,CardSelectTask task,int count) {
    CardSelectInfo info;
    info.cardSelectTask=task;
    if(b.headlessRngKnown && (isHeadlessFetchGrid(task) || isHeadlessSingleHand(task))) {
        if(task==CardSelectTask::BURNING_PACT_HAND)info.sourceDraw=b.curCardQueueItem.card.isUpgraded()?3:2;
        info.hexDazed=b.player.getStatus<PS::HEX>();
        info.inkDraw=b.player.hasRelic<R::INK_BOTTLE>() && b.player.inkBottleCounter==0;
        if(task==CardSelectTask::TRUE_GRIT_HAND && b.calculateCardBlock(9)>0)
            info.juggernautDamage=b.player.getStatus<PS::JUGGERNAUT>();
    }
    if(b.headlessRngKnown && task==CardSelectTask::HEADBUTT_GRID)
        info.sharpHide=b.monsters.arr[0].getStatus<MS::SHARP_HIDE>();
    if(isHeadlessSingleHand(task)) {
        for(int i=0;i<b.cards.cardsInHand;i++) {
            const auto &c=b.cards.hand[i];
            bool eligible=task==CardSelectTask::TRUE_GRIT_HAND || task==CardSelectTask::BURNING_PACT_HAND || (task==CardSelectTask::ARMAMENTS_HAND?c.canUpgrade():
                    c.getType()==CardType::ATTACK || c.getType()==CardType::POWER);
            (eligible?info.options:info.hiddenHand).push_back(c);
        }
        info.explicitConfirm=true;info.data0=count;count=1;
    } else if(task==CardSelectTask::SCRY) {
        if(b.player.hasStatus<PS::NIRVANA>())b.addToBot(Actions::GainBlock(b.player.getStatus<PS::NIRVANA>()));
        if(b.player.hasRelic<R::GOLDEN_EYE>())count+=2;
        info.data0=count; // Java opens with the requested count even for a short pile.
        for(int i=int(b.cards.drawPile.size())-1;i>=0 && int(info.options.size())<count;--i)info.options.push_back(b.cards.drawPile[i]);
    } else if(task==CardSelectTask::SEEK_GRID) {
        info.options=b.cards.drawPile;
        if(int(info.options.size())>count)sortHeadlessSeekOptions(info.options);
    } else if(isHeadlessSecretFetch(task)) {
        const auto type=task==CardSelectTask::SECRET_WEAPON_GRID?CardType::ATTACK:CardType::SKILL;
        for(const auto &card:b.cards.drawPile)if(card.getType()==type) {
            // CardGroup.addToRandomSpot: the first entry costs no call;
            // each later one inserts before an existing entry, even at size 1.
            const int n=int(info.options.size());
            const int slot=n==0?0:b.cardRandomRng.random(n-1);
            info.options.insert(info.options.begin()+slot,card);
        }
        if(info.options.size()==1) {
            fetchDrawCard(b,info.options.front().uniqueId);
            return;
        }
    } else if(task==CardSelectTask::OMNISCIENCE) {
        info.options=b.cards.drawPile;
        if(b.headlessRngKnown)sortHeadlessSeekOptions(info.options);
    } else if(task==CardSelectTask::MEDITATE || task==CardSelectTask::HOLOGRAM_GRID || task==CardSelectTask::LIQUID_MEMORIES_GRID || task==CardSelectTask::HEADBUTT_GRID) {
        for(const auto &c:b.cards.discardPile)info.options.push_back(c);
    } else {
        for(int i=0;i<b.cards.cardsInHand;++i)info.options.push_back(b.cards.hand[i]);
    }
    info.pickCount=std::min(count,int(info.options.size()));
    if(task==CardSelectTask::WARCRY)info.explicitConfirm=true;
    if(task==CardSelectTask::NIGHTMARE || task==CardSelectTask::OMNISCIENCE){info.data0=count;info.pickCount=1;}
    info.canPickAnyNumber=task==CardSelectTask::SCRY || task==CardSelectTask::RETAIN || isHeadlessMultiHand(task);
    info.canPickZero=info.canPickAnyNumber;
    if(isHeadlessMultiHand(task)){info.pickCount=99;info.explicitConfirm=true;}
    if(info.options.empty() || info.pickCount<=0){b.cardSelectInfo=CardSelectInfo{};return;}
    if(task==CardSelectTask::MEDITATE && info.pickCount==int(info.options.size())) {
        // With at most the requested count, Meditate moves cards in discard
        // order without a picker and continues to the original end turn.
        for(int i=0;i<int(b.cards.discardPile.size());) {
            b.cards.discardPile[i].retain=true;
            if(b.cards.cardsInHand>=10){++i;continue;}
            auto card=b.cards.discardPile[i];b.cards.removeFromDiscard(i);b.cards.moveToHand(card);
        }
        return;
    }
    if(task==CardSelectTask::DISCARD && info.pickCount==int(info.options.size())) {
        for(int i=b.cards.cardsInHand-1;i>=0;--i)Actions::DiscardCardAtIdx(i).actFunc(b);
        return;
    }
    b.cardSelectInfo=std::move(info);
    b.inputState=InputState::CARD_SELECT;
    if(task==CardSelectTask::NIGHTMARE && b.cardSelectInfo.options.size()==1) {
        b.inputState=InputState::EXECUTING_ACTIONS;
        applyCharacterSelection(b,0);
    }
}

bool sts::validCharacterSelection(const BattleContext &b,int option) {
    const auto &s=b.cardSelectInfo;
    if(b.inputState!=InputState::CARD_SELECT || !isCharacterSelection(s.cardSelectTask))return false;
    if(option==CHARACTER_SELECT_CONFIRM)return !isHeadlessFetchGrid(s.cardSelectTask) && s.cardSelectTask!=CardSelectTask::LIQUID_MEMORIES_GRID && s.cardSelectTask!=CardSelectTask::HEADBUTT_GRID
        && (s.canPickAnyNumber || (s.canPickZero && s.selected.empty()) || int(s.selected.size())==s.pickCount);
    if(isHeadlessFetchGrid(s.cardSelectTask) || s.cardSelectTask==CardSelectTask::LIQUID_MEMORIES_GRID)
        return option>=0 && option<int(s.options.size()) && int(s.selected.size())<s.pickCount;
    if(isHeadlessSingleHand(s.cardSelectTask))
        return option>=0 && option<int(s.options.size()) && std::find(s.selected.begin(),s.selected.end(),option)==s.selected.end();
    return option>=0 && option<int(s.options.size()) && int(s.selected.size())<s.pickCount
        && std::find(s.selected.begin(),s.selected.end(),option)==s.selected.end();
}

void sts::applyCharacterSelection(BattleContext &b,int option) {
    if(!isCharacterSelection(b.cardSelectInfo.cardSelectTask))throw std::runtime_error("invalid_character_selector");
    auto &s=b.cardSelectInfo;
    if(isHeadlessSingleHand(s.cardSelectTask) && option!=CHARACTER_SELECT_CONFIRM && !s.selected.empty()){
        auto old=s.options.at(s.selected.front());s.selected.clear();
        b.cards.removeFromHandById(old.uniqueId);b.cards.moveToHand(old);
    }
    if(isHeadlessSingleHand(s.cardSelectTask) && option==CHARACTER_SELECT_CONFIRM) {
        if(s.selected.size()!=1)throw std::runtime_error("single_hand_missing_choice");
        auto c=s.options.at(s.selected.front());std::vector<CardInstance> remaining;
        for(int j=0;j<b.cards.cardsInHand;j++) {
            const auto &card=b.cards.hand[j];
            if(card.uniqueId!=c.uniqueId && std::none_of(s.hiddenHand.begin(),s.hiddenHand.end(),
                    [&](const CardInstance &x){return x.uniqueId==card.uniqueId;}))remaining.push_back(card);
        }
        for(int j=b.cards.cardsInHand-1;j>=0;j--)b.cards.removeFromHandAtIdx(j);
        for(auto &v:remaining)b.cards.moveToHand(v);
        if(s.cardSelectTask==CardSelectTask::ARMAMENTS_HAND){c.upgrade();b.cards.moveToHand(c);}
        for(auto &v:s.hiddenHand)b.cards.moveToHand(v);
        if(s.cardSelectTask==CardSelectTask::TRUE_GRIT_HAND || s.cardSelectTask==CardSelectTask::BURNING_PACT_HAND)b.triggerAndMoveToExhaustPile(c);
        if(s.cardSelectTask==CardSelectTask::DUAL_WIELD_HAND) {
            // With an actual choice, Java does not return the original: it
            // creates amount+1 fresh stat-equivalent copies after restoring
            // the hidden non-attack/non-power cards.
            b.cards.notifyRemoveFromCombat(c);
            for(int n=0;n<=s.data0;n++)b.addToTop(Actions::MakeTempCardInHand(c));
        }
        b.cardSelectInfo=CardSelectInfo{};return;
    }
    if((isHeadlessFetchGrid(s.cardSelectTask) || s.cardSelectTask==CardSelectTask::LIQUID_MEMORIES_GRID) && option!=CHARACTER_SELECT_CONFIRM) {
        auto previous=std::find(s.selected.begin(),s.selected.end(),option);
        if(previous!=s.selected.end()){s.selected.erase(previous);b.inputState=InputState::CARD_SELECT;return;}
    }
    if(option!=CHARACTER_SELECT_CONFIRM)s.selected.push_back(option);
    if(option!=CHARACTER_SELECT_CONFIRM && (s.canPickAnyNumber || s.explicitConfirm || int(s.selected.size())<s.pickCount)) {
        b.inputState=InputState::CARD_SELECT;
        return;
    }
    auto selected=s.selected;
    const auto task=s.cardSelectTask;
    if(task==CardSelectTask::HEADBUTT_GRID) {
        if(selected.size()!=1)throw std::runtime_error("headbutt_selection_count");
        const auto &chosen=s.options.at(selected.front());
        bool found=false;
        for(int i=0;i<int(b.cards.discardPile.size());++i)if(b.cards.discardPile[i].uniqueId==chosen.uniqueId) {
            auto c=b.cards.discardPile[i];b.cards.removeFromDiscard(i);c.costForTurn=c.cost;
            b.cards.moveToDrawPileTop(c);found=true;break;
        }
        if(!found)throw std::runtime_error("headbutt_selection_identity");
        b.cardSelectInfo=CardSelectInfo{};return;
    }
    if(task==CardSelectTask::SEEK_GRID || isHeadlessSecretFetch(task)) {
        for(int i:selected)fetchDrawCard(b,s.options.at(i).uniqueId);
        b.cardSelectInfo=CardSelectInfo{};return;
    }
    if(task==CardSelectTask::LIQUID_MEMORIES_GRID || task==CardSelectTask::HOLOGRAM_GRID) {
        for(int i:selected)for(int n=0;n<int(b.cards.discardPile.size());++n)
            if(b.cards.discardPile[n].uniqueId==s.options.at(i).uniqueId) {
                if(b.cards.cardsInHand<10) {
                    auto c=b.cards.discardPile[n];b.cards.removeFromDiscard(n);if(task==CardSelectTask::LIQUID_MEMORIES_GRID)c.setCostForTurn(0);
                    // Source addToHand is a move, not a draw/creation/Soul callback.
                    b.cards.moveToHand(c);
                }
                break;
            }
        b.cardSelectInfo=CardSelectInfo{};return;
    }
    if(isHeadlessMultiHand(task)) {
        // The UI already removed *all* selected cards from the live hand.
        // Reproduce that before manual-discard callbacks so selected
        // Eviscerates do not receive cost reductions meant for remaining cards.
        for(int i:selected)for(int n=0;n<b.cards.cardsInHand;++n)if(b.cards.hand[n].uniqueId==s.options.at(i).uniqueId) {
            b.cards.removeFromHandAtIdx(n);break;
        }
        if(task==CardSelectTask::GAMBLER_DISCARD && !selected.empty())b.addToTop(Actions::DrawCards(int(selected.size())));
        for(int i:selected) {
            auto c=s.options.at(i);
            if(task==CardSelectTask::ELIXIR_EXHAUST) {
                b.triggerAndMoveToExhaustPile(c);continue;
            }
            // The fixed Headless driver waits for Soul.discard completion;
            // clearPowers/resetAttributes restores temporary costs there.
            c.costForTurn=c.cost;
            b.cards.moveToDiscardPile(c);
            ++b.player.cardsDiscardedThisTurn;b.onManualDiscard(c);
        }
        b.cardSelectInfo=CardSelectInfo{};
        return;
    }
    for(int i:selected) {
        auto c=s.options.at(i);
        if(task==CardSelectTask::NIGHTMARE) {
            // NightmarePower stacks its count while preserving its first target.
            if(!b.player.hasStatus<PS::NIGHTMARE>()) {
                c.costForTurn=c.cost;c.retain=false;c.forcedExhaust=false;
                b.nightmareCard=c;
            }
            b.player.buff<PS::NIGHTMARE>(s.data0);
            for(int n=0;n<b.cards.cardsInHand;++n)if(b.cards.hand[n].uniqueId==c.uniqueId) {
                auto original=b.cards.hand[n];b.cards.removeFromHandAtIdx(n);b.cards.moveToHand(original);break;
            }
        } else if(task==CardSelectTask::OMNISCIENCE) {
            for(int n=0;n<int(b.cards.drawPile.size());++n)if(b.cards.drawPile[n].uniqueId==c.uniqueId){b.cards.removeFromDrawPileAtIdx(n);break;}
            for(int copy=0;copy<s.data0;++copy) {
                CardQueueItem item(c,0,b.player.energy);
                item.card.forcedExhaust=true;
                item.autoplay=true;item.randomTarget=b.headlessRngKnown || c.requiresTarget();item.exhaustOnUse=true;
                item.purgeOnUse=copy>0;
                if(copy>0)item.card.uniqueId=b.cards.nextUniqueCardId++;
                b.addToBot({[item](BattleContext &next){next.addToBotCard(item);}});
            }
        } else if(task==CardSelectTask::WISH) {
            if(c.id==CardId::BECOME_ALMIGHTY)b.addToBot(Actions::BuffPlayer<PS::STRENGTH>(c.upgraded?4:3));
            else if(c.id==CardId::LIVE_FOREVER)b.addToBot(Actions::BuffPlayer<PS::PLATED_ARMOR>(c.upgraded?8:6));
            else if(c.id==CardId::FAME_AND_FORTUNE)b.addToBot({[amount=c.upgraded?30:25](BattleContext &next){next.player.gainGold(next,amount);}});
            else throw std::runtime_error("invalid_wish_option");
        } else if(task==CardSelectTask::FOREIGN_INFLUENCE) {
            if(s.data0)c.costForTurn=0;
            b.addToBot(Actions::MakeTempCardInHand(c));
        } else if(task==CardSelectTask::SCRY) {
            for(int n=0;n<int(b.cards.drawPile.size());++n)if(b.cards.drawPile[n].uniqueId==c.uniqueId) {
                b.cards.removeFromDrawPileAtIdx(n);b.cards.moveToDiscardPile(c);break;
            }
        } else if(task==CardSelectTask::MEDITATE) {
            for(int n=0;n<int(b.cards.discardPile.size());++n)if(b.cards.discardPile[n].uniqueId==c.uniqueId) {
                if(b.cards.cardsInHand<10){b.cards.removeFromDiscard(n);c.retain=true;b.cards.moveToHand(c);}break;
            }
        } else {
            for(int n=0;n<b.cards.cardsInHand;++n)if(b.cards.hand[n].uniqueId==c.uniqueId) {
                if(task==CardSelectTask::RETAIN){auto retained=b.cards.hand[n];retained.retain=true;b.cards.removeFromHandAtIdx(n);b.cards.moveToHand(retained);}
                else if(task==CardSelectTask::DISCARD)Actions::DiscardCardAtIdx(n).actFunc(b);
                else if(task==CardSelectTask::WARCRY)b.chooseWarcryCard(n);
                else if(task==CardSelectTask::SETUP){b.cards.removeFromHandAtIdx(n);c.costForTurn=0;b.cards.moveToDrawPileTop(c);}
                break;
            }
        }
    }
    if(task==CardSelectTask::SCRY) {
        for(const auto &c:b.cards.discardPile)if(c.id==CardId::WEAVE) {
            b.addToBot({[id=c.uniqueId](BattleContext &next){
                if(next.cards.cardsInHand>=10)return;
                for(int n=0;n<int(next.cards.discardPile.size());++n)if(next.cards.discardPile[n].uniqueId==id) {
                    auto card=next.cards.discardPile[n];next.cards.removeFromDiscard(n);next.cards.moveToHand(card);break;
                }
            }});
        }
    }
    b.cardSelectInfo=CardSelectInfo{};
}

std::vector<search::Action> sts::characterSelectionActions(const BattleContext &b) {
    std::vector<search::Action> out;
    const auto &s=b.cardSelectInfo;
    // Canonical subset enumeration avoids permutation duplicates. Reimported
    // partial selections can still complete any subset of the remaining cards.
    int start=!s.canonicalOrder || s.selected.empty()?0:s.selected.back()+1;
    // Selection replacement is checked for forced input. Search can reach
    // each target directly as a sibling, so never loop on replacements.
    if(isHeadlessSingleHand(s.cardSelectTask) && !s.selected.empty())
        return {search::Action(search::ActionType::SINGLE_CARD_SELECT,CHARACTER_SELECT_CONFIRM)};
    for(int i=start;i<int(s.options.size());++i) {
        // The real grid can undo a partial pick, and forced replay must still
        // model that click. Never offer an undo to fresh search: reimported
        // grids would otherwise oscillate between the same two decisions.
        if((isHeadlessFetchGrid(s.cardSelectTask) || s.cardSelectTask==CardSelectTask::LIQUID_MEMORIES_GRID)
                && std::find(s.selected.begin(),s.selected.end(),i)!=s.selected.end())continue;
        if(validCharacterSelection(b,i))out.emplace_back(search::ActionType::SINGLE_CARD_SELECT,i);
    }
    if(validCharacterSelection(b,CHARACTER_SELECT_CONFIRM))out.emplace_back(search::ActionType::SINGLE_CARD_SELECT,CHARACTER_SELECT_CONFIRM);
    return out;
}
