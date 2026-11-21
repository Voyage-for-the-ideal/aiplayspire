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
            const auto &damage=s.at("info");
            const std::string kind=damage.contains("type_name")?damage.at("type_name"):damage.at("type");
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
        if(name=="HandCheckActionState" || name=="EnableEndTurnButtonActionState" || name=="WaitActionState")return {[](BattleContext &){}};
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
    if(b.cardSelectInfo.cardSelectTask==CardSelectTask::CODEX)return "CARD_REWARD";
    if(b.cardSelectInfo.cardSelectTask==CardSelectTask::WARCRY)return "HAND_SELECT";
    if(b.headlessRngKnown && b.cardSelectInfo.cardSelectTask==CardSelectTask::EXHAUST_MANY)return "HAND_SELECT";
    if(!isCharacterSelection(b.cardSelectInfo.cardSelectTask))return "UNSUPPORTED";
    if(isHeadlessFetchGrid(b.cardSelectInfo.cardSelectTask))return "GRID";
    switch(b.cardSelectInfo.cardSelectTask) {
        case CardSelectTask::SCRY:case CardSelectTask::MEDITATE:case CardSelectTask::EXHUME:case CardSelectTask::OMNISCIENCE:case CardSelectTask::LIQUID_MEMORIES_GRID:case CardSelectTask::HEADBUTT_GRID:case CardSelectTask::HOLOGRAM_GRID:case CardSelectTask::SEEK_GRID:return "GRID";
        case CardSelectTask::WISH:case CardSelectTask::FOREIGN_INFLUENCE:return "CARD_REWARD";
        default:return "HAND_SELECT";
    }
}

Imported importSelectionSnapshot(const Json &s) {
    std::string screen=s.at("screen_name");
    need(s.value("is_screen_up",false),"selection_screen");
    const char *key=screen=="HAND_SELECT"?"hand_select_screen_state":screen=="GRID"?"grid_card_select_screen_state":screen=="CARD_REWARD"?"card_reward_screen_state":nullptr;
    need(key && s.contains(key) && s.at(key).is_object(),"selection_screen");
    const bool headless=s.value("_engine",std::string())=="headless";
    const auto &view=s.at(key);const auto &current=view.at("current_action_state");
    need(current.is_object(),"selection_current_action");auto name=type(current);const auto &data=payload(current);
    CardSelectInfo info;info.canonicalOrder=false;
    if(name=="SingleHandActionState") {
        need(s.value("_engine",std::string())=="headless" && screen=="HAND_SELECT" && data.at("owner_index")==-1 &&
             view.at("num_cards_to_select")==1 && !view.at("any_number").get<bool>() && !view.at("can_pick_zero").get<bool>(),"single_hand_controls");
        const std::string source=data.at("source");info.cardSelectTask=source=="Armaments"?CardSelectTask::ARMAMENTS_HAND:
            source=="True Grit"?CardSelectTask::TRUE_GRIT_HAND:source=="Dual Wield"?CardSelectTask::DUAL_WIELD_HAND:
            source=="Burning Pact"?CardSelectTask::BURNING_PACT_HAND:CardSelectTask::INVALID;
        need(isHeadlessSingleHand(info.cardSelectTask),"single_hand_source");
        info.data0=data.at("copies");
    } else if(name=="DiscardActionState")info.cardSelectTask=CardSelectTask::DISCARD;
    else if(name=="ElixirExhaustActionState") {
        need(s.value("_engine",std::string())=="headless" && screen=="HAND_SELECT","elixir_source");
        need(data==Json{{"owner_index",-1},{"amount",99},{"is_random",false},{"any_number",true},{"can_pick_zero",true}} &&
             view.at("num_cards_to_select")==99 && view.value("any_number",false) && view.value("can_pick_zero",false),"elixir_variant");
        info.cardSelectTask=CardSelectTask::ELIXIR_EXHAUST;
    } else if(name=="GamblingChipActionState") {
        need(s.value("_engine",std::string())=="headless" && screen=="HAND_SELECT","gamble_source");
        need(data.at("notchip").is_boolean(),"gamble_source_variant");
        need(view.at("num_cards_to_select")==99 && view.value("any_number",false) && view.value("can_pick_zero",false),"gamble_controls");
        info.cardSelectTask=CardSelectTask::GAMBLER_DISCARD;
    }
    else if(headless && name=="BetterDiscardPileToHandActionState") {
        need(s.value("_engine",std::string())=="headless" && screen=="GRID","liquid_source");
        const int count=data.at("number_of_cards");
        need((count==1 || count==2) && data.at("owner_index")==-1 && data.at("set_cost")==true
             && data.at("optional")==false && data.at("new_cost")==0,"liquid_action");
        need(view.at("num_cards")==count && !view.value("any_number",false) && !view.value("for_clarity",false)
             && !view.value("for_upgrade",false) && !view.value("for_transform",false) && !view.value("for_purge",false)
             && !view.value("confirm_screen_up",false) && !view.value("is_just_for_confirming",false),"liquid_controls");
        info.cardSelectTask=CardSelectTask::LIQUID_MEMORIES_GRID;
    }
    else if(!headless && (name=="BetterDrawPileToHandActionState" || name=="AttackFromDeckToHandActionState"
                         || name=="SkillFromDeckToHandActionState" ||
                         (name=="BetterDiscardPileToHandActionState" && data.value("setCost",true)==false))) {
        const bool seek=name=="BetterDrawPileToHandActionState", hologram=name=="BetterDiscardPileToHandActionState";
        const int count=data.at(seek || hologram?"numberOfCards":"amount");
        need(screen=="GRID" && (count==1 || (seek && count==2)) && view.at("num_cards")==count &&
             !view.value("any_number",false) && !view.value("for_clarity",false) && !view.value("for_upgrade",false) &&
             !view.value("for_transform",false) && !view.value("for_purge",false) && !data.value("optional",false),
             "desktop_fetch_controls");
        info.cardSelectTask=seek?CardSelectTask::SEEK_GRID:hologram?CardSelectTask::HOLOGRAM_GRID:
            name=="AttackFromDeckToHandActionState"?CardSelectTask::SECRET_WEAPON_GRID:CardSelectTask::SECRET_TECHNIQUE_GRID;
    }
    else if(name=="FetchCardGridActionState") {
        const auto id=data.at("source_card");const int count=data.at("number_of_cards");
        need(s.value("_engine",std::string())=="headless" && screen=="GRID" && data.size()==3 && data.at("owner_index")==-1,"fetch_grid_source");
        need((id=="Seek" && (count==1 || count==2) || (id=="Hologram" || id=="Omniscience" ||
                id=="Secret Weapon" || id=="Secret Technique") && count==1) &&
             view.at("num_cards")==count && !view.value("any_number",false) && !view.value("for_clarity",false) &&
             !view.value("for_upgrade",false) && !view.value("for_transform",false) && !view.value("for_purge",false) &&
             !view.value("confirm_screen_up",false) && !view.value("is_just_for_confirming",false),"fetch_grid_controls");
        info.cardSelectTask=id=="Seek"?CardSelectTask::SEEK_GRID:id=="Omniscience"?CardSelectTask::OMNISCIENCE:
            id=="Secret Weapon"?CardSelectTask::SECRET_WEAPON_GRID:id=="Secret Technique"?CardSelectTask::SECRET_TECHNIQUE_GRID:CardSelectTask::HOLOGRAM_GRID;
        if(id=="Omniscience")info.data0=2;
    }
    else if(name=="DiscardPileToTopOfDeckActionState") {
        need(screen=="GRID","headbutt_source");
        if(headless)need(data==Json{{"owner_index",-1}},"headbutt_owner");
        else need(data==Json{{"sourceIndex",-1},{"amount",0}} && view.at("is_discard")==true &&
             view.at("can_cancel")==false && view.at("card_select_amount")==0 &&
             view.at("card_queue_state").is_array() && view.at("card_queue_state").empty(),"desktop_headbutt_source");
        need(view.at("num_cards")==1 && !view.value("any_number",false) && !view.value("for_clarity",false)
             && !view.value("for_upgrade",false) && !view.value("for_transform",false) && !view.value("for_purge",false)
             && !view.value("confirm_screen_up",false) && !view.value("is_just_for_confirming",false),"headbutt_controls");
        info.cardSelectTask=CardSelectTask::HEADBUTT_GRID;
    }
    else if(!headless && name=="PutOnDeckActionState") {
        need(screen=="HAND_SELECT" && data.at("amount")==1 && data.at("isRandom")==false
             && view.at("num_cards_to_select")==1 && !view.value("any_number",false)
             && !view.value("can_pick_zero",false),"desktop_warcry_controls");
        info.cardSelectTask=CardSelectTask::WARCRY;
    }
    else if(!headless && name=="ExhumeActionState") {
        need(screen=="GRID" && view.at("num_cards")==1 && !view.value("any_number",false) &&
            data.at("upgrade")==false && data.at("exhumes").is_array(),"desktop_exhume_controls");
        info.cardSelectTask=CardSelectTask::EXHUME;
    } else if(!headless && name=="ForethoughtActionState") {
        const bool any=data.at("chooseAny");
        need(screen=="HAND_SELECT" && view.at("num_cards_to_select")== (any?99:1)
             && view.at("any_number")==any && view.at("can_pick_zero")==any,"desktop_forethought_controls");
        info.cardSelectTask=CardSelectTask::FORETHOUGHT;
    }
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
    const auto pileSize=all.size();
    // Desktop SaveState includes an orphan cardInUse at the end of its
    // history indices. Screen-local option indices stop at the piles.
    if(!headless && p.at("card_in_use").is_object()) {
        const auto pending=uuid(p.at("card_in_use"));
        // A master-deck copy may still share the active card's UUID.
        const auto firstCombat=all.begin()+(p.contains("master_deck")?p.at("master_deck").size():0);
        if(std::none_of(firstCombat,all.end(),[&](const Json &c){return c.value("uuid",std::string())==pending;}))
            all.push_back(p.at("card_in_use"));
    }
    auto container=[&](const Json &c,bool history=false)->Json {if(c.contains("card_id"))return c;
        if(!c.at("card_state").is_null())return c.at("card_state");
        int i=c.at("card_index");need(i>=0 && size_t(i)<(history?all.size():pileSize),"selection_card_reference");return all.at(i);};
    // Freeze identity-indexed histories before changing hand/limbo group sizes.
    for(const auto &i:s.value("cards_played_this_turn",Json::array())){int n=i;need(n>=0 && n<int(all.size()),"combat_history_index");normal["cards_played_this_turn_backup"].push_back(all[n]);}
    normal["cards_played_this_turn"]=Json::array();
    for(auto &entry:normal["cards_played_this_combat"])entry["card_state"]=container(entry,true);
    Json selected=Json::array();
    for(const auto &c:view.value("selected_cards",Json::array()))selected.push_back(container(c));
    if(screen=="HAND_SELECT") {
        need(!view.value("were_cards_retrieved",false),"selection_already_retrieved");
        for(const auto &c:selected)p["hand"].push_back(c);
        if(isHeadlessSingleHand(info.cardSelectTask))for(const auto &c:data.at("hidden"))p["hand"].push_back(c);
    }
    const bool headbuttDouble=info.cardSelectTask==CardSelectTask::HEADBUTT_GRID && !view.at("card_queue_state").empty();
    if(headbuttDouble) {
        const auto &queue=view.at("action_queue");
        need(queue.size()>=2 && type(queue[0])=="RemoveSpecificPowerActionState" &&
             payload(queue[0])==Json{{"ownerIndex",-1},{"powerId","Double Tap"}},"headbutt_double_order");
        auto &powers=p["creature"]["powers"];auto at=std::find_if(powers.begin(),powers.end(),[](const Json &power) {return power.at("power_id")=="Double Tap";});
        need(at!=powers.end() && at->at("amount")==0,"headbutt_double_power");powers.erase(at);
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
    // These are the supported executable effects that survive the selector.
    // Their order around card cleanup is part of parity.
    auto skillCleanupIndex=[&](const Json &queue,bool pact,bool upgraded,bool trueGrit=false) {
        info.sourceDraw=pact?(upgraded?3:2):0;
        info.hexDazed=b.player.getStatus<PS::HEX>();
        info.inkDraw=b.player.hasRelic<R::INK_BOTTLE>() && b.player.inkBottleCounter==0;
        if(trueGrit && b.calculateCardBlock(9)>0)info.juggernautDamage=b.player.getStatus<PS::JUGGERNAUT>();
        need(queue.is_array() && queue.size()==std::size_t(1+(info.sourceDraw!=0)+(info.hexDazed!=0)+info.inkDraw+(info.juggernautDamage>0)),"selection_skill_queue_length");
        size_t i=0;
        auto check=[&](const char *kind,const Json &fields) {
            const auto &a=queue[i++];
            need(type(a)==kind && payload(a)==fields,"selection_skill_queue_order");
        };
        if(info.sourceDraw)check("SelectionDrawActionState",Json{{"kind","burning_pact"},{"amount",info.sourceDraw}});
        if(info.hexDazed)check("SelectionHexActionState",Json{{"amount",info.hexDazed}});
        if(info.inkDraw)check("SelectionDrawActionState",Json{{"kind","ink_bottle"},{"amount",1}});
        need(type(queue[i])=="UseCardActionState","selection_skill_cleanup");
        if(info.juggernautDamage>0)
            need(type(queue[i+1])=="SelectionJuggernautActionState" &&
                 payload(queue[i+1])==Json{{"amount",info.juggernautDamage}},"selection_juggernaut_queue");
        return i;
    };
    if(isHeadlessSingleHand(info.cardSelectTask)) {
        const auto &source=s.at("player_state"),&queue=view.at("action_queue");
        const auto task=info.cardSelectTask;const auto &c=source.at("card_in_use");
        const bool up=c.at("times_upgraded").get<int>()>0;
        const auto &autoplay=view.at("card_queue_state");
        need(source.at("limbo").empty() && autoplay.is_array() && autoplay.size()<=5,"single_hand_cleanup_queue");
        const auto &q=payload(queue.at(skillCleanupIndex(queue,task==CardSelectTask::BURNING_PACT_HAND,up,task==CardSelectTask::TRUE_GRIT_HAND)));
        need(q.at("card")==c && q.at("targetIndex")==-2 && q.at("exhaustCard")==false && q.at("returnToHand")==false &&
             q.at("reboundCard")==false && !c.at("exhaust").get<bool>() && !c.at("purge_on_use").get<bool>() &&
             !c.at("dont_trigger_on_use_card").get<bool>() && c.at("card_id")==data.at("source"),"single_hand_pending_card");
        need((task!=CardSelectTask::ARMAMENTS_HAND || !up) && (task!=CardSelectTask::TRUE_GRIT_HAND || up)
             && info.data0==(task==CardSelectTask::DUAL_WIELD_HAND && up?2:1),"single_hand_card_variant");
        options=source.at("hand");for(const auto &v:selected)options.push_back(v);
        need(options.size()>=2 && selected.size()<=1,"single_hand_counts");std::set<std::string> ids{uuid(c)};
        auto eligible=[&](const CardInstance &card){return task==CardSelectTask::TRUE_GRIT_HAND || task==CardSelectTask::BURNING_PACT_HAND ||
            (task==CardSelectTask::ARMAMENTS_HAND?card.canUpgrade():card.getType()==CardType::ATTACK || card.getType()==CardType::POWER);};
        for(const auto &v:options)need(ids.insert(uuid(v)).second && eligible(reader.card(v)),"single_hand_option");
        for(const auto &v:data.at("hidden")){
            const auto card=reader.card(v);need(ids.insert(uuid(v)).second && !eligible(card),"single_hand_hidden");info.hiddenHand.push_back(card);
        }
        for(auto name:{"draw_pile","discard_pile","exhaust_pile"})for(const auto &v:p.at(name))need(ids.insert(uuid(v)).second,"single_hand_duplicate");
        for(const auto &item:autoplay) {
            const auto &card=item.at("card");
            need(item.at("autoplay_card")==true && item.at("ignore_energy_total")==false && item.at("random_target")==false &&
                 item.at("monster_index").is_number_integer() && item.at("monster_index")>=0 &&
                 item.at("energy_on_use").is_number_integer() && item.at("energy_on_use")>=0 &&
                 !card.at("purge_on_use").get<bool>() && !card.at("dont_trigger_on_use_card").get<bool>() &&
                 ids.insert(uuid(card)).second,"single_hand_autoplay_item");
        }
        b.curCardQueueItem=CardQueueItem(reader.card(c),0,0);
        b.curCardQueueItem.autoplay=!autoplay.empty();
    }
    if(info.cardSelectTask==CardSelectTask::LIQUID_MEMORIES_GRID) {
        need(options==p.at("discard_pile") && options.size()>std::size_t(data.at("number_of_cards").get<int>()),"liquid_discard_options");
        need(selected.size()<std::size_t(data.at("number_of_cards").get<int>()),"liquid_already_committed");
        for(const auto &chosen:selected) {
            auto matching=std::find(options.begin(),options.end(),chosen);
            need(matching!=options.end(),"liquid_selected_card_state");
        }
        need(data.at("number_of_cards")== (b.player.hasRelic<R::SACRED_BARK>()?2:1),"liquid_potency");
    }
    if(!headless && isHeadlessFetchGrid(info.cardSelectTask) && info.cardSelectTask!=CardSelectTask::OMNISCIENCE) {
        const auto task=info.cardSelectTask;
        const bool seek=task==CardSelectTask::SEEK_GRID,hologram=task==CardSelectTask::HOLOGRAM_GRID;
        const auto id=seek?"Seek":hologram?"Hologram":task==CardSelectTask::SECRET_WEAPON_GRID?"Secret Weapon":"Secret Technique";
        const auto &pile=p.at(hologram?"discard_pile":"draw_pile");
        Json eligible=Json::array();
        for(const auto &c:pile) {
            auto kind=getCardType(getCardIdFromId(c.at("card_id").get<std::string>()));
            if(seek || hologram || kind==(task==CardSelectTask::SECRET_WEAPON_GRID?CardType::ATTACK:CardType::SKILL))eligible.push_back(c);
        }
        const int count=view.at("num_cards");
        need(options.size()==eligible.size() && options.size()>std::size_t(count) &&
             selected.size()<std::size_t(count) && view.at("card_queue_state").empty() && p.at("limbo").empty(),
             "desktop_fetch_options");
        std::set<std::string> offered,picked;
        for(const auto &c:options)need(offered.insert(uuid(c)).second && std::find(eligible.begin(),eligible.end(),c)!=eligible.end(),"desktop_fetch_option");
        for(const auto &c:selected)need(picked.insert(uuid(c)).second && std::find(options.begin(),options.end(),c)!=options.end(),"desktop_fetch_selected");
        const auto &queue=view.at("action_queue");
        need(queue.is_array() && queue.size()==1 && type(queue[0])=="UseCardActionState","desktop_fetch_cleanup_queue");
        const auto &q=payload(queue[0]), &card=q.at("card");
        auto pending=s.at("player_state").at("card_in_use");
        if(pending.contains("modifiers") && pending.at("modifiers").is_null() && !card.contains("modifiers"))pending.erase("modifiers");
        need(q.size()==3 && card==pending && card.at("card_id")==id && q.at("targetIndex")==-2 &&
             !card.value("purge_on_use",false) && !card.value("dont_trigger_on_use_card",false) &&
             (!card.contains("modifiers") || card.at("modifiers").is_null()) && !reader.ids.count(uuid(card)) &&
             q.at("exhaustCard")==card.at("exhaust"),"desktop_fetch_cleanup_card");
    }
    if(isHeadlessFetchGrid(info.cardSelectTask) && name=="FetchCardGridActionState") {
        const bool seek=info.cardSelectTask==CardSelectTask::SEEK_GRID, omni=info.cardSelectTask==CardSelectTask::OMNISCIENCE;
        const bool secret=isHeadlessSecretFetch(info.cardSelectTask);
        auto pile=p.at(seek || omni || secret?"draw_pile":"discard_pile");
        if(secret) {
            const auto filter=info.cardSelectTask==CardSelectTask::SECRET_WEAPON_GRID?CardType::ATTACK:CardType::SKILL;
            auto eligible=Json::array();
            for(const auto &card:pile)
                if(getCardType(getCardIdFromId(card.at("card_id").get<std::string>()))==filter)eligible.push_back(card);
            pile=std::move(eligible);
        }
        need(options.size()==pile.size() && (omni?!options.empty():options.size()>std::size_t(data.at("number_of_cards").get<int>())),"fetch_grid_pile");
        if(seek || omni) {
            auto sorted=b.cards.drawPile;
            sortHeadlessSeekOptions(sorted);
            for(int i=0;i<int(sorted.size());++i)need(reader.ids.count(uuid(options[i])) &&
                reader.ids.at(uuid(options[i]))==sorted[i].uniqueId,"seek_option_order");
        } else if(!secret)need(options==pile,"hologram_option_order");
        std::set<std::string> active,chosen;
        for(const auto &c:options)need(active.insert(uuid(c)).second && std::find(pile.begin(),pile.end(),c)!=pile.end(),"fetch_option_identity");
        for(const auto &c:selected)need(chosen.insert(uuid(c)).second && std::find(options.begin(),options.end(),c)!=options.end(),"fetch_selected_identity");
        need(chosen.size()<std::size_t(view.at("num_cards").get<int>()) && s.at("player_state").at("limbo").empty() && view.at("card_queue_state").empty(),"fetch_grid_partial");
        const auto &queue=view.at("action_queue");
        const auto &q=payload(queue.at(skillCleanupIndex(queue,false,false))),&c=q.at("card");
        const auto id=data.at("source_card");
        need(c==s.at("player_state").at("card_in_use") && c.at("card_id")==id &&
             (c.at("times_upgraded")==0 || c.at("times_upgraded")==1) && (seek?1+c.at("times_upgraded").get<int>():1)==view.at("num_cards") &&
             q.at("targetIndex")==-2 && q.at("returnToHand")==false && q.at("reboundCard")==false && !c.value("purge_on_use",false) &&
             !c.value("dont_trigger_on_use_card",false) && c.value("_private",Json::object()).empty() && q.at("exhaustCard")==c.at("exhaust") && c.at("exhaust")==bool(seek || omni || c.at("times_upgraded")==0),"fetch_cleanup_card");
        for(auto name:{"hand","draw_pile","discard_pile","exhaust_pile"})for(const auto &row:p.at(name))need(uuid(c)!=uuid(row),"fetch_pending_duplicate");
        CardQueueItem item(reader.card(c),-2,0);item.exhaustOnUse=q.at("exhaustCard");b.curCardQueueItem=item;
    }
    if(!headless && info.cardSelectTask==CardSelectTask::EXHUME) {
        const auto &pile=p.at("exhaust_pile");
        need(options==pile && !options.empty() && selected.empty(),"desktop_exhume_options");
        std::set<std::string> seen;
        for(const auto &c:options)need(c.at("card_id")!="Exhume" && seen.insert(uuid(c)).second,"desktop_exhume_option");
        for(const auto &c:data.at("exhumes")) {
            need(c.at("card_id")=="Exhume" && !reader.ids.count(uuid(c)),"desktop_exhume_stashed");
            info.stashedExhumes.push_back(reader.card(c));
        }
    }
    if(!headless && info.cardSelectTask==CardSelectTask::OMNISCIENCE) {
        const auto &draw=p.at("draw_pile");
        need(screen=="GRID" && view.at("num_cards")==1 && info.data0==2 && !view.value("any_number",false) &&
             options.size()==draw.size() && !options.empty() && selected.empty(),"desktop_omniscience_options");
        std::set<std::string> offered;
        for(const auto &c:options)need(offered.insert(uuid(c)).second && std::find(draw.begin(),draw.end(),c)!=draw.end(),"desktop_omniscience_option");
    }
    if(!headless && info.cardSelectTask==CardSelectTask::FORETHOUGHT) {
        std::set<std::string> ids;
        for(const auto &c:options)need(ids.insert(uuid(c)).second,"desktop_forethought_identity");
        need(selected.size()<=(data.at("chooseAny")==true?10u:1u),"desktop_forethought_choices");
    }
    if(!headless && info.cardSelectTask==CardSelectTask::MEDITATE) {
        const int count=data.at("numberOfCards");
        const auto &discard=p.at("discard_pile");
        need(screen=="GRID" && (count==1 || count==2) && view.at("num_cards")==count &&
             !view.value("any_number",false) && options==discard && options.size()>std::size_t(count)
             && selected.size()<std::size_t(count),"desktop_meditate_options");
        std::set<std::string> picked;
        for(const auto &card:selected)need(picked.insert(uuid(card)).second && std::find(options.begin(),options.end(),card)!=options.end(),"desktop_meditate_selected");
    }
    if(!headless && info.cardSelectTask==CardSelectTask::SCRY) {
        const int count=data.at("amount");
        const auto &draw=p.at("draw_pile");
        need(screen=="GRID" && count>0 && view.at("num_cards")==count && view.at("any_number")==true
             && !view.value("for_upgrade",false) && !view.value("for_transform",false) && !view.value("for_purge",false),
             "desktop_scry_controls");
        const auto size=std::min(draw.size(),std::size_t(count));
        need(size>0 && options.size()==size,"desktop_scry_count");
        std::set<std::string> offered, picked;
        const auto first=draw.end()-size;
        for(const auto &card:options)need(offered.insert(uuid(card)).second && std::find(first,draw.end(),card)!=draw.end(),"desktop_scry_option");
        for(const auto &card:selected)need(picked.insert(uuid(card)).second && std::find(options.begin(),options.end(),card)!=options.end(),"desktop_scry_selected");
    }
    if(info.cardSelectTask==CardSelectTask::HEADBUTT_GRID) {
        need(options==p.at("discard_pile") && options.size()>1 && selected.empty(),"headbutt_discard_options");
        if(!headless) {
            // SaveStateMod exports the desktop UseCardAction schema (not the
            // enriched Linux adapter schema). Admit the single known cleanup only.
            const auto &queue=view.at("action_queue");
            need(queue.is_array() && queue.size()==1 && type(queue[0])=="UseCardActionState" &&
                 s.at("player_state").at("limbo").empty(),"desktop_headbutt_cleanup_queue");
            const auto &q=payload(queue[0]), &card=q.at("card");
            auto inUse=s.at("player_state").at("card_in_use");
            need(inUse.is_object(),"desktop_headbutt_pending");
            if(inUse.contains("modifiers") && inUse.at("modifiers").is_null() && !card.contains("modifiers"))inUse.erase("modifiers");
            need(q.size()==3 && q.at("exhaustCard")==false && q.at("targetIndex").is_number_integer() &&
                 q.at("targetIndex").get<int>()>=0 && card==inUse && card.at("card_id")=="Headbutt" &&
                 card.at("exhaust")==false && card.at("purge_on_use")==false &&
                 !card.value("shuffle_back_into_draw_pile",false) && !card.value("dont_trigger_on_use_card",false) &&
                 (!card.contains("modifiers") || card.at("modifiers").is_null()) &&
                 !reader.ids.count(uuid(card)),"desktop_headbutt_cleanup_card");
            b.curCardQueueItem=CardQueueItem(reader.card(card),reader.target(q.at("targetIndex")),0);
            out.desktopHeadbutt=true;
        } else {
            const auto &limbo=s.at("player_state").at("limbo"),&copies=view.at("card_queue_state");
            const auto &queue=view.at("action_queue");
            const auto &m=b.monsters.arr[0];
            info.sharpHide=m.getStatus<MS::SHARP_HIDE>();
            size_t cleanupIndex=headbuttDouble || info.sharpHide?1:0;
            if(info.sharpHide)need(!headbuttDouble && m.id==MonsterId::THE_GUARDIAN && queue.size()>1 &&
                type(queue[0])=="HeadbuttSharpHideActionState" &&
                payload(queue[0])==Json{{"owner_index",out.monsterMap[0]},{"target_index",-1},{"amount",info.sharpHide}},"headbutt_sharp_hide");
            if(b.player.attacksPlayedThisTurn>0 && b.player.attacksPlayedThisTurn%3==0) {
                for(const auto &relic:p.at("relics")) {
                    const auto &id=relic.at("relic_id");
                    if(id!="Kunai" && id!="Shuriken")continue;
                    need(queue.size()>cleanupIndex && type(queue[cleanupIndex])==
                         (id=="Kunai"?"HeadbuttKunaiActionState":"HeadbuttShurikenActionState") &&
                         payload(queue[cleanupIndex])==Json{{"owner_index",-1},{"amount",1}} &&
                         relic.at("counter")==0,"headbutt_attack_relic_buff");
                    ++cleanupIndex;
                }
            }
            need((queue.size()==cleanupIndex+1 ||
                (queue.size()==cleanupIndex+2 && type(queue[cleanupIndex+1])=="HeadbuttMonsterTailState")) &&
                type(queue[cleanupIndex])=="UseCardActionState","headbutt_cleanup_queue");
            const auto &q=payload(queue.at(cleanupIndex)), &card=q.at("card");
            const bool playingCopy=card.at("purge_on_use").get<bool>();
            need(q.at("exhaustCard")==false && q.at("returnToHand")==false && q.at("reboundCard")==false
                 && card==s.at("player_state").at("card_in_use") && card.at("card_id")=="Headbutt"
                 && card.at("exhaust")==false && card.at("dont_trigger_on_use_card")==false
                 && card.value("_private",Json::object()).empty(),"headbutt_cleanup_card");
            const auto pendingId=uuid(card);
            int originals=0;
            for(const char *pile:{"hand","draw_pile","discard_pile","exhaust_pile"})for(const auto &c:p.at(pile))if(uuid(c)==pendingId) {
                need(playingCopy && std::string(pile)=="discard_pile" && c.at("card_id")=="Headbutt" && c.at("times_upgraded")==card.at("times_upgraded") &&
                     c.at("cost")==card.at("cost") && !c.value("purge_on_use",false),"headbutt_pending_duplicate_uuid");++originals;
            }
            if(headbuttDouble) {
                need(!playingCopy && copies.is_array() && copies.size()==1 && limbo==Json::array({copies[0].at("card")}),"headbutt_double_limbo");
                const auto &copy=copies[0],&c=copy.at("card");
                for(const char *k:{"uuid","card_id","cost","cost_for_turn","times_upgraded","base_damage"})need(c.at(k)==card.at(k),"headbutt_double_copy");
                need(c.at("purge_on_use")==true && c.at("dont_trigger_on_use_card")==false && c.at("exhaust")==false &&
                     copy.at("monster_index")==q.at("targetIndex") && copy.at("energy_on_use").is_number_integer() && copy.at("energy_on_use").get<int>()>=0 &&
                     copy.at("ignore_energy_total")==true && copy.at("autoplay_card")==true && copy.at("random_target")==false,"headbutt_double_controls");
            } else if(playingCopy) {
                need(originals==1 && copies.empty() && (limbo.empty() || limbo==Json::array({card})),"headbutt_resolving_copy");
            } else need(limbo.empty(),"headbutt_in_flight");
            auto plain=card;plain["purge_on_use"]=false;
            CardQueueItem pending(reader.card(plain),reader.target(q.at("targetIndex")),0);
            pending.purgeOnUse=playingCopy;pending.autoplay=playingCopy;b.curCardQueueItem=pending;
        }
    }
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
    for(const auto &raw:selected){const auto id=uuid(raw);bool found=false;for(int i=0;i<int(options.size());++i)if(uuid(options[i])==id){info.selected.push_back(i);if(!isHeadlessFetchGrid(info.cardSelectTask) && info.cardSelectTask!=CardSelectTask::LIQUID_MEMORIES_GRID)out.selectionIndices[i]=-1;found=true;break;}need(found,"selection_selected_identity");}
    info.pickCount=screen=="HAND_SELECT"?view.at("num_cards_to_select").get<int>():screen=="GRID"?view.at("num_cards").get<int>():1;
    info.canPickAnyNumber=view.value("any_number",false) || view.value("up_to",false);
    info.canPickZero=view.value("can_pick_zero",false) || view.value("skippable",false) || info.canPickAnyNumber;
    const bool fast=s.value("_combat_card_pools",Json::object()).value("fast_hand_confirm",false);
    info.explicitConfirm=screen=="HAND_SELECT" && !(fast && info.pickCount==1 && !info.canPickZero);
    if(isHeadlessSingleHand(info.cardSelectTask) || info.cardSelectTask==CardSelectTask::WARCRY ||
       info.cardSelectTask==CardSelectTask::FORETHOUGHT)info.explicitConfirm=true;
    if(info.cardSelectTask==CardSelectTask::WISH)for(const auto &c:info.options)
        need(c.id==CardId::BECOME_ALMIGHTY || c.id==CardId::FAME_AND_FORTUNE || c.id==CardId::LIVE_FOREVER,"selection_choose_one_option");
    // Parse the entire continuation before returning any command. Unknown
    // executable actions stop here; they are never silently dropped.
    b.actionQueue.clear();b.cardQueue.clear();
    need(view.at("action_queue").is_array() && view.at("action_queue").size()<=4096,"continuation_queue_schema");
    std::set<std::string> cleanup;
    if(isHeadlessMultiHand(info.cardSelectTask) || info.cardSelectTask==CardSelectTask::LIQUID_MEMORIES_GRID) {
        // Potion use appends HandCheckAction (derived powers/glow refresh),
        // then GamblingChipAction appends its presentation wait. These do not
        // change persistent combat fields; the native engine computes card
        // powers from those bound fields. A different executable continuation
        // must not disappear when constructing the dedicated guard key.
        need(view.at("action_queue").size()<=3,"gamble_refresh_queue_count");
        need(view.at("card_queue_state").is_array() && view.at("card_queue_state").empty(),"gamble_card_queue");
        bool refresh=false,wait=false,heal=false;
        for(const auto &a:view.at("action_queue")) {
            const auto name=type(a);
            if(name=="HandCheckActionState") {
                need(!refresh && !wait && payload(a).value("owner_index",0)==-1
                     && payload(a).value("derived_refresh",false),"gamble_hand_refresh");
                refresh=true;
            } else if(name=="OrnithopterHealActionState") {
                need(refresh && !wait && !heal && payload(a)==Json{{"source_index",-1},{"target_index",-1},{"amount",5}},"selection_ornithopter_heal");
                heal=true;
            } else if(name=="WaitActionState") {need(info.cardSelectTask==CardSelectTask::GAMBLER_DISCARD && !wait,"selection_unexpected_wait");wait=true;}
            else throw Unsupported("gamble_executable_queue:"+name);
        }
        const bool potion=(info.cardSelectTask!=CardSelectTask::GAMBLER_DISCARD || data.at("notchip").get<bool>());
        if(info.cardSelectTask==CardSelectTask::ELIXIR_EXHAUST)need(refresh && !wait,"elixir_refresh_queue");
        need(heal==(potion && b.player.hasRelic<R::TOY_ORNITHOPTER>()),"selection_ornithopter_queue");
        info.ornithopterHeal=heal;
    }
    for(const auto &a:view.at("action_queue")) {
        if(!headless && info.cardSelectTask==CardSelectTask::SCRY && type(a)=="DiscardToHandActionState") {
            const int index=payload(a).at("cardIndex"), handSize=p.at("hand").size();
            need(index>=handSize && index-handSize<int(p.at("discard_pile").size()),"desktop_scry_weave_index");
            const auto card=reader.card(p.at("discard_pile").at(index-handSize));
            need(card.id==CardId::WEAVE,"desktop_scry_weave_card");
            b.addToBot({[id=card.uniqueId](BattleContext &ctx) {
                if(ctx.cards.cardsInHand>=10)return;
                for(int i=0;i<int(ctx.cards.discardPile.size());++i)if(ctx.cards.discardPile[i].uniqueId==id) {
                    auto c=ctx.cards.discardPile[i];ctx.cards.removeFromDiscard(i);ctx.cards.moveToHand(c);break;
                }
            }});
            continue;
        }
        if(type(a)=="SelectionJuggernautActionState") {
            need(info.cardSelectTask==CardSelectTask::TRUE_GRIT_HAND && info.juggernautDamage>0,"selection_juggernaut_owner");
            b.addToBot(Actions::DamageRandomEnemy(info.juggernautDamage));
            continue;
        }
        if(type(a)=="SelectionDrawActionState" || type(a)=="SelectionHexActionState") {
            need(isHeadlessSingleHand(info.cardSelectTask) || isHeadlessFetchGrid(info.cardSelectTask),"selection_skill_action_owner");
            int amount=payload(a).at("amount");
            if(type(a)=="SelectionDrawActionState")b.addToBot(Actions::DrawCards(amount));
            else b.addToBot(Actions::MakeTempCardInDrawPile(CardInstance(CardId::DAZED),amount,true));
            continue;
        }
        if(type(a)=="HeadbuttSharpHideActionState") {
            need(info.cardSelectTask==CardSelectTask::HEADBUTT_GRID && info.sharpHide>0,"headbutt_sharp_hide_owner");
            b.addToBot(Actions::DamagePlayer(info.sharpHide));
            continue;
        }
        if(type(a)=="HeadbuttShurikenActionState") {
            need(info.cardSelectTask==CardSelectTask::HEADBUTT_GRID,"headbutt_shuriken_owner");
            b.addToBot(Actions::BuffPlayer<PS::STRENGTH>(1));
            continue;
        }
        if(type(a)=="HeadbuttKunaiActionState") {
            need(info.cardSelectTask==CardSelectTask::HEADBUTT_GRID,"headbutt_kunai_owner");
            b.addToBot(Actions::BuffPlayer<PS::DEXTERITY>(1));
            continue;
        }
        if(type(a)=="HeadbuttMonsterTailState") {
            need(info.cardSelectTask==CardSelectTask::HEADBUTT_GRID,"headbutt_tail_owner");
            const auto &v=payload(a);int target=reader.target(v.at("owner_index"));
            need(target>=0 && target==b.curCardQueueItem.target,"headbutt_tail_target");
            auto &m=b.monsters.arr[target];const auto kind=v.at("kind").get<std::string>();
            const bool plated=kind=="plated_armor_minus_one" && m.hasStatus<MS::PLATED_ARMOR>() && m.getStatus<MS::PLATED_ARMOR>()>0;
            const bool flight=kind=="flight_minus_one" && m.id==MonsterId::BYRD && m.getStatus<MS::FLIGHT>()>0;
            const bool malleable=kind=="malleable_block" && m.id==MonsterId::SNAKE_PLANT && m.getStatus<MS::MALLEABLE>()>3;
            const auto &sourceMonster=s.at("cur_map_node_state").at("monster_data").at(v.at("owner_index").get<int>());
            const bool guardian=kind=="guardian_defensive_mode" && m.id==MonsterId::THE_GUARDIAN &&
                m.getStatus<MS::MODE_SHIFT>()<=0 && sourceMonster.at("_private").at("TheGuardian.closeUpTriggered")==true;
            need(plated || guardian || flight || malleable,"headbutt_tail_kind_or_status");
            if(guardian)m.setHasStatus<MS::MODE_SHIFT>(true);
            b.enqueueHeadbuttTail(target,malleable?BattleContext::HeadbuttTail::MALLEABLE:flight?BattleContext::HeadbuttTail::FLIGHT:
                                  plated?BattleContext::HeadbuttTail::PLATED:BattleContext::HeadbuttTail::GUARDIAN);
            continue;
        }
        if(type(a)=="OrnithopterHealActionState") {
            need(info.ornithopterHeal,"selection_unexpected_heal");b.addToBot(Actions::HealPlayer(5));continue;
        }
        if(type(a)=="UseCardActionState") {
            const auto &card=payload(a).at("card");cleanup.insert(uuid(card));
            if(info.cardSelectTask==CardSelectTask::HEADBUTT_GRID && card.at("purge_on_use")==true) {
                const auto pending=b.curCardQueueItem;b.addToBot({[pending](BattleContext &ctx){ctx.curCardQueueItem=pending;ctx.onAfterUseCard();}});continue;
            }
        }
        b.addToBot(reader.action(a));
    }
    for(const auto &c:s.at("player_state").at("limbo"))need(cleanup.count(uuid(c))>0,"continuation_card_in_flight");
    for(const auto &q:view.at("card_queue_state")) {
        if(headbuttDouble) {
            auto item=b.curCardQueueItem;item.purgeOnUse=true;item.autoplay=true;item.ignoreEnergyTotal=true;
            item.energyOnUse=q.at("energy_on_use");b.addToBotCard(item);
        } else {
            auto item=reader.queued(q);
            if(isHeadlessSingleHand(info.cardSelectTask)) {
                item.freeToPlay=true;
                // These cards were only removed from draw, not from combat.
                b.cards.notifyAddCardToCombat(item.card);
            }
            b.addToBotCard(item);
        }
    }
    b.cardSelectInfo=std::move(info);b.inputState=InputState::CARD_SELECT;
    out.selectionScreen=screen;out.before=projection(s);
    need(out.before==projection(out),"selection_import_projection_mismatch");
    return out;
}
}
