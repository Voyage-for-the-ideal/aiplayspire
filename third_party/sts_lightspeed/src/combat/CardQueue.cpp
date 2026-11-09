//
// Created by gamerpuppy on 8/24/2021.
//

#include "combat/CardQueue.h"

using namespace sts;


bool CardQueue::isEmpty() const {
    return size == 0;
}

bool CardQueue::containsCardWithId(int uniqueId) const {
    for(const auto &item:orderedItems())if(item.card.getUniqueId()==uniqueId)return true;
    return false;
}

void CardQueue::clear() {
    expanded.reset();
    size = 0;
    backIdx = 0;
    frontIdx = 0;
}

void CardQueue::pushFront(CardQueueItem item) {
    if(size==capacity && !expanded)expand();
    if(expanded){expanded->push_front(std::move(item));++size;return;}
    --frontIdx;
    ++size;
    if (frontIdx < 0) {
        frontIdx = capacity - 1;
    }
    arr.at(frontIdx) = std::move(item);
}

void CardQueue::pushBack(CardQueueItem item) {
    if(size==capacity && !expanded)expand();
    if(expanded){expanded->push_back(std::move(item));++size;return;}
    arr.at(backIdx) = std::move(item);
    ++backIdx;
    ++size;
    if (backIdx >= capacity) {
        backIdx = 0;
    }
}

CardQueueItem CardQueue::popFront() {
    if(expanded){auto item=expanded->front();expanded->pop_front();--size;return item;}
    assert(size > 0);
    CardQueueItem &item = arr.at(frontIdx);
    ++frontIdx;
    --size;
    if (frontIdx >= capacity) {
        frontIdx = 0;
    }
    return item;
}

CardQueueItem CardQueue::popBack() {
    if(expanded){auto item=expanded->back();expanded->pop_back();--size;return item;}
    assert(size > 0);
    --backIdx;
    --size;
    if (backIdx < 0) {
        backIdx = arr.size() - 1;
    }
    CardQueueItem &item = arr.at(backIdx);
    return item;
}

CardQueueItem &CardQueue::front() {
    if(expanded)return expanded->front();
    assert(size > 0);
    return arr.at(frontIdx);
}

std::vector<CardQueueItem> CardQueue::orderedItems() const {
    if(expanded)return {expanded->begin(),expanded->end()};
    std::vector<CardQueueItem> out;out.reserve(size);
    for(int i=0;i<size;++i)out.push_back(arr[(frontIdx+i)%capacity]);
    return out;
}
void CardQueue::expand(){auto items=orderedItems();expanded.emplace(items.begin(),items.end());}
