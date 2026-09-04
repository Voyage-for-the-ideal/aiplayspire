import unittest

from sts_ai_framework.battle_stall import StallWatcher, battle_progress_signature
from sts_ai_framework.models import GameState, MonsterState, PlayerState


def make_battle_state(*, hp=50, energy=3, block=0, end_turn=True,
                      monster_hp=30, monster_block=0, intent="ATTACK", floor=7):
    return GameState(
        screen_type="NONE", room_phase="COMBAT", floor=floor, act=1,
        is_end_turn_button_enabled=end_turn, choice_list=[],
        player=PlayerState(current_hp=hp, max_hp=80, block=block, energy=energy, gold=99),
        monsters=[MonsterState(name="Jaw Worm", id="JawWorm", current_hp=monster_hp,
                               max_hp=44, block=monster_block, intent=intent)],
    )


class SignatureTests(unittest.TestCase):
    def test_same_state_same_signature(self):
        self.assertEqual(battle_progress_signature(make_battle_state()),
                         battle_progress_signature(make_battle_state()))

    def test_monster_damage_changes_signature(self):
        self.assertNotEqual(battle_progress_signature(make_battle_state(monster_hp=30)),
                            battle_progress_signature(make_battle_state(monster_hp=24)))

    def test_player_hp_changes_signature(self):
        self.assertNotEqual(battle_progress_signature(make_battle_state(hp=50)),
                            battle_progress_signature(make_battle_state(hp=42)))

    def test_end_turn_availability_changes_signature(self):
        self.assertNotEqual(battle_progress_signature(make_battle_state(end_turn=True)),
                            battle_progress_signature(make_battle_state(end_turn=False)))

    def test_monster_intent_changes_signature(self):
        self.assertNotEqual(battle_progress_signature(make_battle_state(intent="ATTACK")),
                            battle_progress_signature(make_battle_state(intent="DEFEND")))


class StallWatcherTests(unittest.TestCase):
    def test_silent_below_threshold(self):
        watcher = StallWatcher(threshold_seconds=300)
        state = make_battle_state()
        self.assertIsNone(watcher.observe(state, now=0.0, in_battle=True))
        self.assertIsNone(watcher.observe(state, now=299.9, in_battle=True))

    def test_fires_once_at_threshold(self):
        watcher = StallWatcher(threshold_seconds=300)
        state = make_battle_state()
        watcher.observe(state, now=0.0, in_battle=True)
        self.assertIsNone(watcher.observe(state, now=100.0, in_battle=True))
        self.assertEqual(watcher.observe(state, now=300.0, in_battle=True), 300.0)
        # 同一停摆片段内不重复告警
        self.assertIsNone(watcher.observe(state, now=900.0, in_battle=True))

    def test_reports_again_after_state_changes(self):
        watcher = StallWatcher(threshold_seconds=300)
        watcher.observe(make_battle_state(), now=0.0, in_battle=True)
        self.assertIsNotNone(watcher.observe(make_battle_state(), now=300.0, in_battle=True))
        # 状态变化 → 开始新的停摆片段
        self.assertIsNone(watcher.observe(make_battle_state(monster_hp=10), now=300.0, in_battle=True))
        self.assertEqual(watcher.observe(make_battle_state(monster_hp=10), now=700.0, in_battle=True), 400.0)

    def test_resets_when_leaving_battle(self):
        watcher = StallWatcher(threshold_seconds=300)
        state = make_battle_state()
        watcher.observe(state, now=0.0, in_battle=True)
        watcher.observe(state, now=1000.0, in_battle=False)
        # 战斗结束重置后，回到战斗重新起算
        self.assertIsNone(watcher.observe(state, now=1000.0, in_battle=True))
        self.assertIsNone(watcher.observe(state, now=1100.0, in_battle=True))

    def test_disabled_when_threshold_zero(self):
        watcher = StallWatcher(threshold_seconds=0)
        state = make_battle_state()
        self.assertIsNone(watcher.observe(state, now=0.0, in_battle=True))
        self.assertIsNone(watcher.observe(state, now=10_000.0, in_battle=True))


if __name__ == "__main__":
    unittest.main()
