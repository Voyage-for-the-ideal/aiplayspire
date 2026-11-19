"""Native budgets; Java server expansion counts remain a separate benchmark."""
PROFILES = {"FAST": (5000, 250), "BALANCED": (15000, 2000), "DEEP": (50000, 10000)}
TURN_POLICIES = ("legacy", "layered-v1")


def deployment_budget(room, policy="layered-v1"):
    if policy not in TURN_POLICIES:
        raise ValueError(f"unknown_search_policy:{policy}")
    if policy == "layered-v1" and room in ("MonsterRoomElite", "MonsterRoomBoss", "ELITE", "BOSS"):
        return 10000, 500
    return PROFILES["FAST"]


def benchmark_budget(room, tier):
    """Shared segment and fight caps; use for both A/B arms."""
    if tier not in ("deployment", "complete"):
        raise ValueError(f"unknown_benchmark_tier:{tier}")
    cap, timeout = (15000, 90000) if tier == "complete" else deployment_budget(room)
    multiplier = 40 if room in ("BOSS", "MonsterRoomBoss") else 20
    return (cap, timeout), (multiplier * cap, multiplier * timeout)


def budgets(request, max_time_ms=90000):
    profile = request.get("search_profile", "BALANCED")
    if profile not in PROFILES:
        raise ValueError(f"unknown_search_profile:{profile}")
    expansions, timeout = PROFILES[profile]
    expansions = min(max(int(request.get("max_expansions", request.get("num_turns", expansions))), 1), 1000000)
    timeout = min(max(int(request.get("timeout_ms", timeout)), 1), max_time_ms)
    return profile, expansions, timeout
