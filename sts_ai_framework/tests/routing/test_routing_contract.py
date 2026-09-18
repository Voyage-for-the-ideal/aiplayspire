import unittest

from ._helpers import load_root_symbol, load_symbol


class RoutingContractTests(unittest.TestCase):
    def test_required_routing_symbols_are_importable(self):
        required_module_symbols = {
            "map_graph": "MapGraph",
            "context": "RouteContext",
            "features": "RouteFeatureExtractor",
            "planner": "RoutePlanner",
            "evaluator": "RouteEvaluator",
            "policy": "RoutePolicy",
            "decision": "RouteDecision",
        }
        for module_key, symbol in required_module_symbols.items():
            with self.subTest(module=module_key, symbol=symbol):
                try:
                    _ = load_symbol(module_key, symbol)
                except AssertionError as exc:
                    self.fail(str(exc))

        # Some implementations may re-export from routing package root.
        # Validate the symbols exist there too when possible; allow package-local
        # exports only when the project chooses that shape.
        try:
            load_root_symbol("RoutePlanner")
        except AssertionError:
            pass


if __name__ == "__main__":
    unittest.main()
