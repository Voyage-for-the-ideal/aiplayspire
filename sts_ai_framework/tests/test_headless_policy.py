import copy
import unittest
from types import SimpleNamespace

from sts_ai_framework.headless.policy import HeadlessValuePolicy
from sts_ai_framework.headless.interaction import IroncladInteractionBridge
from sts_ai_framework.tests.test_headless_interaction import pick, reward, card, event, we_meet


class HeadlessPolicyTest(unittest.TestCase):
    def policy(self):
        policy = HeadlessValuePolicy.__new__(HeadlessValuePolicy)
        policy._skipped_rewards = set(); policy._match_memory = {}
        policy.engine = SimpleNamespace(_apply_choice=lambda state, action: action,
                                        evaluate_state=lambda action: int(action.get("action") == "skip"))
        return policy

    def test_declined_reward_is_not_reopened_forever(self):
        policy = self.policy(); bridge = IroncladInteractionBridge()
        source = pick([card()]); before = bridge.observe(source, run_id="run", sequence=1)
        action, _ = policy.choose(before); self.assertEqual(action.native_method, "skipRewardCard")
        source = reward([{"type": "CARD", "card_uuids": [card()["uuid"]]}])
        frame = bridge.observe(source, run_id="run", sequence=2)
        action, _ = policy.choose(frame); self.assertEqual(action.native_method, "proceed")
        source["interaction"]["rewards"][0]["card_uuids"] = ["00000000-0000-0000-0000-000000000009"]
        frame = bridge.observe(source, run_id="run", sequence=3)
        action, _ = policy.choose(frame); self.assertEqual(action.native_method, "openCardReward")

    def test_reward_decline_memory_is_scoped_to_episode(self):
        policy = self.policy(); policy._skipped_rewards.add(("old", (card()["uuid"],)))
        source = reward([{"type": "CARD", "card_uuids": [card()["uuid"]]}])
        frame = IroncladInteractionBridge().observe(source, run_id="new", sequence=1)
        action, _ = policy.choose(frame); self.assertEqual(action.native_method, "openCardReward")

    def test_episode_start_clears_visible_memory(self):
        policy = self.policy(); policy._skipped_rewards.add(('old', ('uuid',)))
        policy._match_memory[('old', 1, 8)] = {0:'Strike_R'}
        policy.begin_episode()
        self.assertEqual(policy._skipped_rewards, set()); self.assertEqual(policy._match_memory, {})

    def test_library_uses_precomputed_offers_before_opening_grid(self):
        source = event(
            effects=[{"type": "choose_generated_card", "count": 1,
                      "card_ids": [f"card-{i}" for i in range(20)]}],
            phase="0", followup="GENERATED_CARD_GRID", kind="CARD_REWARD",
        )
        view = source["interaction"]["event"]
        view.update(id="The Library", class_name="TheLibrary", decision_kind="COMPLEX")
        heal = event(effects=[{"type": "gain_hp", "amount": 20}])["interaction"]["event"]["choices"][0]
        view["choices"].append(dict(heal, button_index=1, action_index=1))
        frame = IroncladInteractionBridge().observe(source, run_id="run", sequence=1)
        policy = self.policy()
        policy.engine.evaluate_state = lambda c: sum(
            20 if e["type"] == "gain_hp" else 30 * (e["card_id"] == "card-13")
            for e in c["effects"]
        )
        action, meta = policy.choose(frame)
        self.assertEqual((0,), action.native_args)
        self.assertEqual(30, meta["score"])

    def test_we_meet_again_multiple_costs_follow_existing_event_dispatch(self):
        frame=IroncladInteractionBridge().observe(we_meet(),run_id='run',sequence=1)
        kinds=('lose_potion','lose_gold','remove_card',None)
        for selected in range(4):
            with self.subTest(selected=selected):
                policy=self.policy();seen=[]
                def apply(state,action):
                    seen.append(copy.deepcopy(action))
                    return action
                def evaluate(action):
                    effects=action['effects']
                    first=effects[0]['type'] if effects else None
                    return int(first==kinds[selected])
                policy.engine=SimpleNamespace(_apply_choice=apply,evaluate_state=evaluate)
                action,meta=policy.choose(frame)
                self.assertEqual(action.native_method,'chooseDialogOption')
                self.assertEqual(action.native_args,(selected,))
                self.assertEqual(meta['policy'],'format12-post-action-value')
                self.assertEqual(len(seen),4)
                self.assertTrue(all(a['action']=='composite_event' for a in seen))

    def test_we_meet_again_value_simulation_removes_the_selected_upgraded_instance(self):
        frame=IroncladInteractionBridge().observe(we_meet(),run_id='run',sequence=1)
        policy=self.policy();seen=[]
        def apply(state,action):
            result=copy.deepcopy(state)
            for effect in action['effects']:
                if effect['type']=='lose_gold':result['gold']-=effect['amount']
                elif effect['type']=='remove_card':result['deck'].remove(effect['card_id'])
            seen.append(result)
            return result
        policy.engine=SimpleNamespace(_apply_choice=apply,evaluate_state=lambda state:-len(state['deck']))
        action,_=policy.choose(frame)
        self.assertEqual(action.native_args,(2,))
        self.assertEqual(seen[2]['deck'],['Searing Blow'])
        self.assertEqual(frame.state.deck[1].reference,'Searing Blow+1')

    def test_we_meet_again_disabled_costs_keep_native_indices_without_new_candidates(self):
        source=we_meet();costs=source['interaction']['we_meet_again']['costs']
        costs.pop('0');costs.pop('2')
        source['player']['potions']=[{'id':'Potion Slot','name':'slot','empty':True,'targetRequired':False}]
        source['player']['masterDeck']=[card(1,id='Strike_R',rarity='BASIC',timesUpgraded=0)]
        choices=source['interaction']['event']['choices']
        for i in (0,2):choices[i].update(enabled=False,action_index=None)
        choices[1]['action_index']=0;choices[3]['action_index']=1
        frame=IroncladInteractionBridge().observe(source,run_id='run',sequence=1)
        self.assertEqual([a.native_args for a in frame.actions],[(1,),(3,)])
        policy=self.policy();seen=[]
        policy.engine=SimpleNamespace(_apply_choice=lambda state,action:seen.append(action) or action,
                                      evaluate_state=lambda action:int(bool(action['effects'])))
        action,_=policy.choose(frame)
        self.assertEqual(action.native_args,(1,))
        self.assertEqual(action.api_action.choice_index,0)
        self.assertEqual(len(seen),2)
