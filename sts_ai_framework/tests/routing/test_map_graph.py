import unittest

from ._helpers import extract_path, load_symbol, make_node, coords_of


class MapGraphTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.MapGraph = load_symbol("map_graph", "MapGraph")

    def test_graph_build_and_paths_are_valid(self):
        nodes = [
            make_node(0, 1, symbol="M", children=[{"x": 0, "y": 2}, {"x": 1, "y": 2}]),
            make_node(0, 2, children=[{"x": 0, "y": 3}]),
            make_node(1, 2, children=[{"x": 1, "y": 3}]),
            make_node(0, 3, children=[]),
            make_node(1, 3, children=[]),
        ]
        graph = self.MapGraph(nodes)
        paths = graph.paths_from(nodes[0], depth=2, wing_charges=0)
        self.assertIsInstance(paths, list)
        self.assertGreaterEqual(len(paths), 2)

        expected_endings = {(0, 3), (1, 3)}
        endings = set()
        for candidate in paths:
            path_nodes = extract_path(candidate)
            first = coords_of(path_nodes[0])
            self.assertIn(first, {(0, 1), (0, 2), (0, 3), (1, 2), (1, 3)})
            last = coords_of(path_nodes[-1])
            if len(path_nodes) >= 1:
                endings.add(last)
        self.assertGreaterEqual(endings, expected_endings)

    def test_duplicate_node_raises_validation_error(self):
        nodes = [make_node(0, 1), make_node(0, 1)]
        with self.assertRaises(Exception):
            self.MapGraph(nodes)

    def test_missing_child_raises_validation_error(self):
        nodes = [
            make_node(0, 1, children=[{"x": 9, "y": 9}]),
            make_node(0, 2),
        ]
        with self.assertRaises(Exception):
            self.MapGraph(nodes)

    def test_self_and_backward_edges_are_rejected(self):
        with self.subTest(edge="backward"):
            with self.assertRaises(Exception):
                self.MapGraph([
                    make_node(0, 1, children=[{"x": 1, "y": 0}]),
                    make_node(1, 0),
                ])
        with self.subTest(edge="self"):
            with self.assertRaises(Exception):
                self.MapGraph([
                    make_node(0, 1, children=[{"x": 0, "y": 1}]),
                ])


    def test_winged_edges_require_wing_charges(self):
        nodes = [
            make_node(0, 1, children=[{"x": 0, "y": 2, "winged": False}]),
            make_node(0, 2, children=[
                {"x": 0, "y": 3, "winged": False},
                {"x": 0, "y": 4, "winged": True},
            ]),
            make_node(0, 3, children=[]),
            make_node(0, 4, children=[]),
        ]
        graph = self.MapGraph(nodes)
        start = nodes[0]

        no_wing = graph.paths_from(start, depth=2, wing_charges=0)
        no_wing_paths = {coords_of(path[-1]) for path in [extract_path(p) for p in no_wing]}
        self.assertIn((0, 3), no_wing_paths)
        self.assertNotIn((0, 4), no_wing_paths)

        with_wing = graph.paths_from(start, depth=2, wing_charges=1)
        with_wing_paths = {coords_of(path[-1]) for path in [extract_path(p) for p in with_wing]}
        self.assertIn((0, 3), with_wing_paths)
        self.assertIn((0, 4), with_wing_paths)


if __name__ == "__main__":
    unittest.main()
