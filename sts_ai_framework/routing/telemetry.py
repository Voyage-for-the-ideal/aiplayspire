"""Compare a submitted one-room forecast with the next confirmed map state."""


class RouteForecastTracker:
    def __init__(self):
        self.pending = None

    def begin(self, state, diagnostics):
        trajectory = (diagnostics or {}).get("trajectory", ())
        if trajectory and self.pending is None:
            self.pending = {"floor": state.floor, "act": state.act,
                            "seed_group": state.seed_group,
                            "step": trajectory[0],
                            "upgrades": sum(card.upgrades for card in state.deck)}

    def observe(self, state):
        pending = self.pending
        if pending is None:
            return None
        terminal = state.screen_type == "GAME_OVER"
        if state.seed_group != pending["seed_group"] and not (terminal and state.seed_group is None):
            self.pending = None
            return {"status": "run_changed", "floor": pending["floor"]}
        if not terminal and (state.screen_type != "MAP" or state.floor == pending["floor"]):
            return None
        self.pending = None
        if state.player is None:
            return {"status": "missing_player", "floor": pending["floor"]}
        step = pending["step"]
        after = step["after"]
        matched = (state.act == pending["act"] and state.floor == (pending["floor"] or 0) + 1)
        actual_action = None
        if step["symbol"] == "R":
            if float(state.keys.ruby) > step["before"]["ruby"]:
                actual_action = "recall"
            elif sum(card.upgrades for card in state.deck) > pending["upgrades"]:
                actual_action = "upgrade"
            elif state.player.current_hp > step["before"]["hp"]:
                actual_action = "rest"
        return {"status": "terminal" if terminal else "matched" if matched else "observation_gap",
                "floor": pending["floor"], "act": pending["act"], "symbol": step["symbol"],
                "hp_error": state.player.current_hp - after["hp"] if matched else None,
                "gold_error": state.player.gold - after["gold"] if matched else None,
                "predicted_hp": after["hp"], "actual_hp": state.player.current_hp,
                "predicted_action": step["action"], "actual_action": actual_action}
