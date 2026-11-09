"""Native budgets; Java server expansion counts remain a separate benchmark."""
PROFILES = {"FAST": (5000, 250), "BALANCED": (15000, 2000), "DEEP": (50000, 10000)}


def budgets(request, max_time_ms=90000):
    profile = request.get("search_profile", "BALANCED")
    if profile not in PROFILES:
        raise ValueError(f"unknown_search_profile:{profile}")
    expansions, timeout = PROFILES[profile]
    expansions = min(max(int(request.get("max_expansions", request.get("num_turns", expansions))), 1), 1000000)
    timeout = min(max(int(request.get("timeout_ms", timeout)), 1), max_time_ms)
    return profile, expansions, timeout
