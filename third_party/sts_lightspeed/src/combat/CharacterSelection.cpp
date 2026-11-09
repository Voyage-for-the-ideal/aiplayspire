#include "combat/CharacterSelection.h"
#include <algorithm>
#include <stdexcept>

using namespace sts;

bool sts::isCharacterSelection(CardSelectTask task) {
    return task==CardSelectTask::DISCARD || task==CardSelectTask::SCRY || task==CardSelectTask::RETAIN
        || task==CardSelectTask::SETUP || task==CardSelectTask::MEDITATE || task==CardSelectTask::NIGHTMARE
        || task==CardSelectTask::OMNISCIENCE || task==CardSelectTask::FOREIGN_INFLUENCE || task==CardSelectTask::WISH;
}

void sts::openCharacterSelection(BattleContext &b,CardSelectTask task,int count) {
    CardSelectInfo info;
    info.cardSelectTask=task;
    if(task==CardSelectTask::SCRY) {
        if(b.player.hasStatus<PS::NIRVANA>())b.addToBot(Actions::GainBlock(b.player.getStatus<PS::NIRVANA>()));
        if(b.player.hasRelic<R::GOLDEN_EYE>())count+=2;
        for(int i=int(b.cards.drawPile.size())-1;i>=0 && int(info.options.size())<count;--i)info.options.push_back(b.cards.drawPile[i]);
    } else if(task==CardSelectTask::OMNISCIENCE) {
        for(const auto &c:b.cards.drawPile)info.options.push_back(c);
    } else if(task==CardSelectTask::MEDITATE) {
        for(const auto &c:b.cards.discardPile)info.options.push_back(c);
    } else {
        for(int i=0;i<b.cards.cardsInHand;++i)info.options.push_back(b.cards.hand[i]);
    }
    info.pickCount=std::min(count,int(info.options.size()));
    if(task==CardSelectTask::NIGHTMARE || task==CardSelectTask::OMNISCIENCE){info.data0=count;info.pickCount=1;}
    info.canPickAnyNumber=task==CardSelectTask::SCRY || task==CardSelectTask::RETAIN;
    info.canPickZero=info.canPickAnyNumber;
    if(info.options.empty() || info.pickCount<=0){b.cardSelectInfo=CardSelectInfo{};return;}
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
    if(option==CHARACTER_SELECT_CONFIRM)return s.canPickAnyNumber || (s.canPickZero && s.selected.empty()) || int(s.selected.size())==s.pickCount;
    return option>=0 && option<int(s.options.size()) && int(s.selected.size())<s.pickCount
        && std::find(s.selected.begin(),s.selected.end(),option)==s.selected.end();
}

void sts::applyCharacterSelection(BattleContext &b,int option) {
    if(!isCharacterSelection(b.cardSelectInfo.cardSelectTask))throw std::runtime_error("invalid_character_selector");
    auto &s=b.cardSelectInfo;
    if(option!=CHARACTER_SELECT_CONFIRM)s.selected.push_back(option);
    if(option!=CHARACTER_SELECT_CONFIRM && (s.canPickAnyNumber || s.explicitConfirm || int(s.selected.size())<s.pickCount)) {
        b.inputState=InputState::CARD_SELECT;
        return;
    }
    auto selected=s.selected;
    const auto task=s.cardSelectTask;
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
                item.autoplay=true;item.randomTarget=c.requiresTarget();item.exhaustOnUse=true;
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
    // Canonical subset enumeration avoids permutation duplicates. Reimported
    // partial selections can still complete any subset of the remaining cards.
    int start=!b.cardSelectInfo.canonicalOrder || b.cardSelectInfo.selected.empty()?0:b.cardSelectInfo.selected.back()+1;
    for(int i=start;i<int(b.cardSelectInfo.options.size());++i)
        if(validCharacterSelection(b,i))out.emplace_back(search::ActionType::SINGLE_CARD_SELECT,i);
    if(validCharacterSelection(b,CHARACTER_SELECT_CONFIRM))out.emplace_back(search::ActionType::SINGLE_CARD_SELECT,CHARACTER_SELECT_CONFIRM);
    return out;
}
