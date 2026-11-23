---
name: aiplayspire-formal-verification
description: Model and verify AIPlaySpire concurrency, retries, and picker state transitions against historical counterexamples. Use for protocol or isolated runtime changes, not ordinary game balance tests.
---

# AIPlaySpire formal verification

1. Start from `docs/REGRESSION_EVIDENCE.md`. Identify the oldest available failing implementation or equivalent mutation supported by its diff. Separate reproduced incidents from source-derived cases and fault injection.
2. Define state, enabled transitions, bounds, and safety invariants before reading implementation call order. Explore nondeterministic scheduling with `python tools/formal_headless.py`; extend the model only for states relevant to the failure.
3. Require a shortest counterexample for the historical variant and none for the corrected model at the stated bounds. Match each invariant to a public runtime contract. Test that contract against real processes, stored records or a temporary SQLite database where relevant.
4. Keep failed or incomplete episodes censored. Do not replace tests that check permission gates, action legality, receipt identity, terminal evidence, data isolation, or tamper rejection.
5. Report model bounds and concrete runtime coverage separately. GitHub CI uses no production credentials or game assets; keep raw production data and saved games out of fixtures.
