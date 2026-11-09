#include "Snapshot.h"
#include "combat/CharacterSelection.h"
#include <algorithm>
#include <map>
#include <set>

using namespace sts;
namespace bridge {
namespace {
void need(bool ok,const std::string &why){if(!ok)throw Unsupported(why);}
std::string type(const Json &j) {
    auto name=j.at("state_class").get<std::string>();
    const std::string prefix="savestate.actions.";
    need(name.compare(0,prefix.size(),prefix)==0,"continuation_namespace");
    return name.substr(prefix.size());
}
const Json &payload(const Json &j){need(j.at("state").is_object(),"continuation_state");return j.at("state");}
std::string uuid(const Json &c){need(c.contains("uuid") && c.at("uuid").is_string(),"selection_card_identity");return c.at("uuid").get<std::string>();}
Stance stance(const std::string &s) {
    if(s=="Neutral")return Stance::NEUTRAL;if(s=="Calm")return Stance::CALM;
    if(s=="Wrath")return Stance::WRATH;if(s=="Divinity")return Stance::DIVINITY;
    throw Unsupported("continuation_stance:"+s);
}
struct Reader {
    Imported &out;
    std::map<std::string,int> ids;
    int target(int external)const {
        if(external<0)return external;
        for(int i=0;i<5;++i)if(out.monsterMap[i]==external)return i;
        throw Unsupported("continuation_target");
    }
    CardInstance card(const Json &raw) {
        std::string id=uuid(raw);
        auto it=ids.find(id);
        int native=it==ids.end()?out.battle.cards.nextUniqueCardId++:it->second;
        ids[id]=native;out.selectionIdentities[native]=id;
        return snapshotCard(raw,native,true);
    }
    Action power(const Json &p,int owner,int amount) {
        const std::string id=p.at("power_id");
        if(owner==-1) {
            if(id=="DevaForm")return {[amount,p](BattleContext &b){
                b.player.devaFormEnergyPerTurn=b.player.hasStatus<PS::DEVA>()?b.player.devaFormEnergyPerTurn+1:p.at("energy_gain_amount").get<int>();
                b.player.buff<PS::DEVA>(amount);
            }};
            if(id=="Night Terror") {
                auto c=card(p.at("card"));c.costForTurn=c.cost;
                return {[c,amount](BattleContext &b){if(!b.player.hasStatus<PS::NIGHTMARE>())b.nightmareCard=c;b.player.buff<PS::NIGHTMARE>(amount);}};
            }
            PS ps=getPlayerStatusFromId(id);
            const bool debuff=(ps>=PS::BIAS && ps<=PS::WRAITH_FORM) || ps==PS::WEAK || ps==PS::VULNERABLE || ps==PS::FRAIL
                || ((ps==PS::STRENGTH || ps==PS::DEXTERITY || ps==PS::FOCUS) && amount<0);
            switch(ps) {
#define PLAYER_POWER(P) case PS::P:return debuff?Actions::DebuffPlayer<PS::P>(amount,false):Actions::BuffPlayer<PS::P>(amount);
                PLAYER_POWER(STRENGTH) PLAYER_POWER(DEXTERITY) PLAYER_POWER(FOCUS) PLAYER_POWER(ARTIFACT)
                PLAYER_POWER(WEAK) PLAYER_POWER(VULNERABLE) PLAYER_POWER(FRAIL) PLAYER_POWER(WRAITH_FORM)
                PLAYER_POWER(INTANGIBLE) PLAYER_POWER(AFTER_IMAGE) PLAYER_POWER(ACCURACY) PLAYER_POWER(NOXIOUS_FUMES)
                PLAYER_POWER(BURST) PLAYER_POWER(DOUBLE_DAMAGE) PLAYER_POWER(ENVENOM) PLAYER_POWER(RUSHDOWN)
                PLAYER_POWER(MENTAL_FORTRESS) PLAYER_POWER(ESTABLISHMENT) PLAYER_POWER(NIRVANA) PLAYER_POWER(STUDY)
                PLAYER_POWER(MANTRA) PLAYER_POWER(DEVOTION) PLAYER_POWER(DRAW_CARD_NEXT_TURN) PLAYER_POWER(ENERGIZED)
                PLAYER_POWER(BLUR) PLAYER_POWER(PLATED_ARMOR) PLAYER_POWER(WAVE_OF_THE_HAND) PLAYER_POWER(VIGOR)
                PLAYER_POWER(WELL_LAID_PLANS) PLAYER_POWER(NO_DRAW) PLAYER_POWER(BLASPHEMER) PLAYER_POWER(FREE_ATTACK_POWER)
                PLAYER_POWER(OMEGA) PLAYER_POWER(FASTING) PLAYER_POWER(TOOLS_OF_THE_TRADE) PLAYER_POWER(NEXT_TURN_BLOCK)
                PLAYER_POWER(INFINITE_BLADES) PLAYER_POWER(THOUSAND_CUTS) PLAYER_POWER(THORNS) PLAYER_POWER(COLLECT)
                PLAYER_POWER(WRATH_NEXT_TURN) PLAYER_POWER(MASTER_REALITY) PLAYER_POWER(LIKE_WATER) PLAYER_POWER(BATTLE_HYMN)
#undef PLAYER_POWER
                default:throw Unsupported("continuation_player_power:"+id);
            }
        }
        need(owner>=0,"continuation_power_owner");
        switch(getMonsterStatusFromId(id)) {
#define ENEMY_POWER(P) case MS::P:return Actions::DebuffEnemy<MS::P>(owner,amount,false);
            ENEMY_POWER(POISON) ENEMY_POWER(WEAK) ENEMY_POWER(VULNERABLE) ENEMY_POWER(CHOKED)
            ENEMY_POWER(CORPSE_EXPLOSION) ENEMY_POWER(MARK) ENEMY_POWER(BLOCK_RETURN)
#undef ENEMY_POWER
            case MS::STRENGTH:return amount<0?Actions::DebuffEnemy<MS::STRENGTH>(owner,amount,false):Actions::BuffEnemy<MS::STRENGTH>(owner,amount);
            case MS::SHACKLED:return Actions::BuffEnemy<MS::SHACKLED>(owner,amount);
            default:throw Unsupported("continuation_monster_power:"+id);
        }
    }
    CardQueueItem queued(const Json &raw) {
        if(raw.at("card").is_null())return CardQueueItem::endTurnItem();
        CardQueueItem item(card(raw.at("card")),raw.value("monster_index",Json()).is_null()?0:target(raw.at("monster_index").get<int>()),raw.value("energy_on_use",0));
        item.ignoreEnergyTotal=raw.value("ignore_energy_total",false);item.autoplay=raw.value("autoplay_card",false);
        item.randomTarget=raw.value("random_target",false);item.purgeOnUse=raw.at("card").value("purge_on_use",false);
        item.exhaustOnUse=raw.at("card").value("exhaust",false);
        return item;
    }
    Action action(const Json &raw) {
        auto name=type(raw);const auto &s=payload(raw);
        if(name=="UseCardActionState") {
            CardQueueItem item(card(s.at("card")),target(s.value("targetIndex",-2)),0);
            item.purgeOnUse=s.at("card").value("purge_on_use",false);item.exhaustOnUse=s.at("exhaustCard");
            return {[item](BattleContext &b){b.curCardQueueItem=item;b.onAfterUseCard();}};
        }
        if(name=="GainBlockActionState") {
            int t=target(s.at("ownerIndex")),n=s.at("amount");return t==-1?Actions::GainBlock(n):Actions::MonsterGainBlock(t,n);
        }
        if(name=="GainEnergyActionState")return Actions::GainEnergy(s.at("energyGain"));
        if(name=="DrawCardActionState")return Actions::DrawCards(s.at("amount"));
        if(name=="MakeTempCardInHandActionState")return Actions::MakeTempCardInHand(card(s.at("c")),s.at("amount"));
        if(name=="MakeTempCardInDrawPileActionState") {
            need(!s.value("toBottom",false),"continuation_draw_bottom");
            return Actions::MakeTempCardInDrawPile(card(s.at("cardToMake")),s.at("amount"),s.at("randomSpot"));
        }
        if(name=="DiscardActionState") {
            const int amount=s.at("amount");const bool random=s.at("isRandom");
            need(amount>=0,"continuation_discard_amount");
            return {[amount,random](BattleContext &b){
                if(!random){openCharacterSelection(b,CardSelectTask::DISCARD,amount);return;}
                const bool all=amount>=b.cards.cardsInHand;
                for(int i=0;i<amount && b.cards.cardsInHand>0;++i)
                    Actions::DiscardCardAtIdx(all?b.cards.cardsInHand-1:b.cards.getRandomCardIdxInHand(b.cardRandomRng)).actFunc(b);
            }};
        }
        if(name=="NewQueueCardActionState") {
            if(!s.contains("cardState") || s.at("cardState").is_null())return {[](BattleContext &b){
                for(const auto &item:b.cardQueue.orderedItems())if(item.isEndTurn)return;
                b.addToBotCard(CardQueueItem::endTurnItem());
            }};
            CardQueueItem item(card(s.at("cardState")),target(s.value("targetIndex",-2)),0);
            item.randomTarget=s.at("randomTarget");item.autoplay=s.at("autoplayCard");
            item.purgeOnUse=s.at("cardState").value("purge_on_use",false);item.exhaustOnUse=s.at("cardState").value("exhaust",false);
            need(item.randomTarget || !item.card.requiresTarget() || item.target>=0,"continuation_queue_target");
            bool immediate=s.at("immediateCard");
            return {[item,immediate](BattleContext &b){
                if(b.cardQueue.containsCardWithId(item.card.uniqueId))return;
                auto next=item;next.energyOnUse=b.player.energy;
                // GameActionManager.addCardQueueItem inserts at index 1, not
                // index 0, when immediateCard is true and a card is queued.
                if(immediate && !b.cardQueue.isEmpty()) {
                    auto first=b.cardQueue.popFront();
                    b.addToTopCard(next);b.addToTopCard(first);
                } else b.addToBotCard(next);
            }};
        }
        if(name=="RemoveSpecificPowerActionState") {
            int owner=target(s.at("ownerIndex"));std::string id=s.at("powerId");
            if(owner==-1) {
                auto ps=getPlayerStatusFromId(id);need(ps!=PS::INVALID && ps!=PS::DRAW_REDUCTION,"continuation_remove_power:"+id);
                return {[ps](BattleContext &b){b.player.setHasStatus(ps,false);}};
            }
            throw Unsupported("continuation_remove_monster_power:"+id);
        }
        if(name=="ChangeStanceActionState")return Actions::ChangeStance(stance(s.at("id")));
        if(name=="ApplyPowerActionState")return power(s.at("powerToApply"),target(s.at("targetIndex")),s.at("amount"));
        if(name=="DamageActionState") {
            int t=target(s.at("targetIndex")),owner=target(s.at("ownerIndex")),n=s.at("info").at("output");
            const std::string kind=s.at("info").at("type_name");
            if(t==-2)return {[](BattleContext &){}};
            if(t==-1) {
                if(kind=="HP_LOSS")return Actions::PlayerLoseHp(n,owner==-1);
                if(kind=="NORMAL" && owner>=0)return Actions::AttackPlayer(owner,n);
                need(kind=="THORNS","continuation_damage_kind");return Actions::DamagePlayer(n);
            }
            if(kind=="NORMAL")return Actions::AttackEnemy(t,n);
            if(kind=="THORNS")return Actions::DamageEnemy(t,n);
            need(kind=="HP_LOSS","continuation_damage_kind");
            return {[t,n](BattleContext &b){auto &m=b.monsters.arr[t];if(m.isTargetable())m.damageUnblockedHelper(b,m.hasStatus<MS::INTANGIBLE>()?1:n);b.checkCombat();}};
        }
        if(name=="ClearCardQueueActionState")return Actions::ClearCardQueue();
        if(name=="DiscardAtEndOfTurnActionState")return Actions::DiscardAtEndOfTurn();
        if(name=="EndTurnActionState")return Actions::UnnamedEndOfTurnAction();
        if(name=="MonsterStartTurnActionState")return Actions::MonsterStartTurnAction();
        if(name=="TriggerEndOfTurnOrbsActionState")return Actions::TriggerEndOfTurnOrbsAction();
        if(name=="PressEndTurnButtonActionState" || name=="EnqueueEndTurnActionState")return {[](BattleContext &b){if(!b.endTurnQueued)b.endTurn();}};
        if(name=="HandCheckActionState" || name=="EnableEndTurnButtonActionState")return {[](BattleContext &){}};
        throw Unsupported("continuation_action:"+name);
    }
};
}

Json selectionContext(const Json &s) {
    Json out={{"screen",s.value("is_screen_up",false)?s.at("screen_name"):Json("NONE")}};
    if(s.value("is_screen_up",false)) {
        for(const char *key:{"hand_select_screen_state","grid_card_select_screen_state","card_reward_screen_state"})
            if(s.contains(key) && !s.at(key).is_null())out[key]=s.at(key);
        out["limbo"]=s.at("player_state").at("limbo");out["card_in_use"]=s.at("player_state").at("card_in_use");
    }
    return out;
}

std::string decisionScreen(const BattleContext &b) {
    if(b.inputState==InputState::PLAYER_NORMAL)return "NONE";
    if(b.inputState!=InputState::CARD_SELECT)return "UNSUPPORTED";
    // Potion/card Discovery selections are CardRewardScreen in the game but
    // the engine stores the three generated cards instead of hand options.
    if(b.cardSelectInfo.cardSelectTask==CardSelectTask::DISCOVERY)return "CARD_REWARD";
    if(!isCharacterSelection(b.cardSelectInfo.cardSelectTask))return "UNSUPPORTED";
    switch(b.cardSelectInfo.cardSelectTask) {
        case CardSelectTask::SCRY:case CardSelectTask::MEDITATE:case CardSelectTask::OMNISCIENCE:return "GRID";
        case CardSelectTask::WISH:case CardSelectTask::FOREIGN_INFLUENCE:return "CARD_REWARD";
        default:return "HAND_SELECT";
    }
}

Imported importSelectionSnapshot(const Json &s) {
    std::string screen=s.at("screen_name");
    need(s.value("is_screen_up",false),"selection_screen");
    const char *key=screen=="HAND_SELECT"?"hand_select_screen_state":screen=="GRID"?"grid_card_select_screen_state":screen=="CARD_REWARD"?"card_reward_screen_state":nullptr;
    need(key && s.contains(key) && s.at(key).is_object(),"selection_screen");
    const auto &view=s.at(key);const auto &current=view.at("current_action_state");
    need(current.is_object(),"selection_current_action");auto name=type(current);const auto &data=payload(current);
    CardSelectInfo info;info.canonicalOrder=false;
    if(name=="DiscardActionState")info.cardSelectTask=CardSelectTask::DISCARD;
    else if(name=="ScryActionState")info.cardSelectTask=CardSelectTask::SCRY;
    else if(name=="RetainCardsActionState")info.cardSelectTask=CardSelectTask::RETAIN;
    else if(name=="NightmareActionState"){info.cardSelectTask=CardSelectTask::NIGHTMARE;info.data0=data.at("amount");}
    else if(name=="SetupActionState")info.cardSelectTask=CardSelectTask::SETUP;
    else if(name=="MeditateActionState")info.cardSelectTask=CardSelectTask::MEDITATE;
    else if(name=="OmniscienceActionState"){info.cardSelectTask=CardSelectTask::OMNISCIENCE;info.data0=data.at("playAmt");}
    else if(name=="ForeignInfluenceActionState"){need(!data.value("retrieveCard",false),"selection_already_retrieved");info.cardSelectTask=CardSelectTask::FOREIGN_INFLUENCE;info.data0=data.at("upgraded");}
    else if(name=="ChooseOneActionState")info.cardSelectTask=CardSelectTask::WISH;
    // Skill/Attack/Power/Colorless Potion and the Discovery card all open the
    // vanilla CardRewardScreen through DiscoveryAction; the three offered cards
    // arrive in reward_group, and amount is the copy count (2 with Sacred Bark).
    else if(name=="DiscoveryActionState"){need(!data.value("retrieveCard",false),"selection_already_retrieved");info.cardSelectTask=CardSelectTask::DISCOVERY;info.data0=data.value("amount",1);}
    else throw Unsupported("selection_current_action:"+name);

    Json normal=s;auto &p=normal["player_state"];
    Json all=Json::array();for(const char *pile:{"master_deck","draw_pile","hand","discard_pile","exhaust_pile","limbo"})for(const auto &c:p.value(pile,Json::array()))all.push_back(c);
    auto container=[&](const Json &c)->Json {if(c.contains("card_id"))return c;
        if(!c.at("card_state").is_null())return c.at("card_state");
        int i=c.at("card_index");need(i>=0 && i<int(all.size()),"selection_card_reference");return all.at(i);};
    // Freeze identity-indexed histories before changing hand/limbo group sizes.
    for(const auto &i:s.value("cards_played_this_turn",Json::array())){int n=i;need(n>=0 && n<int(all.size()),"combat_history_index");normal["cards_played_this_turn_backup"].push_back(all[n]);}
    normal["cards_played_this_turn"]=Json::array();
    for(auto &entry:normal["cards_played_this_combat"])entry["card_state"]=container(entry);
    Json selected=Json::array();
    for(const auto &c:view.value("selected_cards",Json::array()))selected.push_back(container(c));
    if(screen=="HAND_SELECT") {
        need(!view.value("were_cards_retrieved",false),"selection_already_retrieved");
        for(const auto &c:selected)p["hand"].push_back(c);
    }
    normal["screen_name"]="NONE";normal["is_screen_up"]=false;normal["end_turn_queued"]=false;
    p["is_ending_turn"]=false;p["card_in_use"]=nullptr;p["limbo"]=Json::array();
    Imported out=importSnapshot(normal);Reader reader{out,{}};auto &b=out.battle;
    auto remember=[&](const CardInstance &c,const Json &raw){auto id=uuid(raw);reader.ids[id]=c.uniqueId;out.selectionIdentities[c.uniqueId]=id;};
    for(int i=0;i<b.cards.cardsInHand;++i)remember(b.cards.hand[i],p.at("hand")[i]);
    for(const auto &pair:{std::pair<const std::vector<CardInstance>*,const char*>(&b.cards.drawPile,"draw_pile"),{&b.cards.discardPile,"discard_pile"},{&b.cards.exhaustPile,"exhaust_pile"}}) {
        int i=0;for(const auto &c:*pair.first)remember(c,p.at(pair.second)[i++]);
    }
    Json options=Json::array();
    if(screen=="HAND_SELECT")for(const auto &c:p.at("hand"))options.push_back(c);
    else if(screen=="GRID")for(const auto &c:view.at("group_cards"))options.push_back(container(c));
    else options=view.at("reward_group");
    for(const auto &c:options)info.options.push_back(reader.card(c));
    if(info.cardSelectTask==CardSelectTask::DISCOVERY) {
        // DISCOVERY reads discovery_Cards() for enumeration and application;
        // keep that array aligned with the live reward_group order so the
        // emitted CARD_REWARD_SELECT index maps to the card the player sees.
        need(info.options.size()==3,"selection_discovery_options");
        for(int i=0;i<3;++i)info.cards[i]=info.options[i].id;
    }
    out.selectionIndices.resize(info.options.size(),-1);
    for(int i=0;i<int(options.size());++i)out.selectionIndices[i]=i;
    for(const auto &raw:selected){const auto id=uuid(raw);bool found=false;for(int i=0;i<int(options.size());++i)if(uuid(options[i])==id){info.selected.push_back(i);out.selectionIndices[i]=-1;found=true;break;}need(found,"selection_selected_identity");}
    info.pickCount=screen=="HAND_SELECT"?view.at("num_cards_to_select").get<int>():screen=="GRID"?view.at("num_cards").get<int>():1;
    info.canPickAnyNumber=view.value("any_number",false) || view.value("up_to",false);
    info.canPickZero=view.value("can_pick_zero",false) || view.value("skippable",false) || info.canPickAnyNumber;
    const bool fast=s.value("_combat_card_pools",Json::object()).value("fast_hand_confirm",false);
    info.explicitConfirm=screen=="HAND_SELECT" && !(fast && info.pickCount==1 && !info.canPickZero);
    if(info.cardSelectTask==CardSelectTask::WISH)for(const auto &c:info.options)
        need(c.id==CardId::BECOME_ALMIGHTY || c.id==CardId::FAME_AND_FORTUNE || c.id==CardId::LIVE_FOREVER,"selection_choose_one_option");
    // Parse the entire continuation before returning any command. Unknown
    // executable actions stop here; they are never silently dropped.
    b.actionQueue.clear();b.cardQueue.clear();
    need(view.at("action_queue").is_array() && view.at("action_queue").size()<=4096,"continuation_queue_schema");
    std::set<std::string> cleanup;
    for(const auto &a:view.at("action_queue")) {
        if(type(a)=="UseCardActionState")cleanup.insert(uuid(payload(a).at("card")));
        b.addToBot(reader.action(a));
    }
    for(const auto &c:s.at("player_state").at("limbo"))need(cleanup.count(uuid(c))>0,"continuation_card_in_flight");
    for(const auto &q:view.at("card_queue_state"))b.addToBotCard(reader.queued(q));
    b.cardSelectInfo=std::move(info);b.inputState=InputState::CARD_SELECT;
    out.selectionScreen=screen;out.before=projection(s);
    need(out.before==projection(out),"selection_import_projection_mismatch");
    return out;
}
}
