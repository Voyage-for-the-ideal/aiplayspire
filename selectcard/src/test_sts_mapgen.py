"""Pin sts_mapgen against ground truth produced by the real game jar.

The vectors below were generated on 2026-09-12 by a small Java driver that
instantiates the actual classes from lib/desktop-1.0.jar
(RandomXS128, MapGenerator, RoomTypeAssigner, room classes) and replicates
AbstractDungeon.generateMap's call order. See MODEL_V3_DESIGN.md section 4.2.

Grid convention (driver output): connected node -> upper-case room symbol;
unconnected node with a fixed-row room (rows 0/8/14) -> lower-case symbol;
unconnected node without room -> '.'.
"""

import unittest

import sts_mapgen
from sts_mapgen import RandomXS128, StSRandom, act_path_symbols, generate_act_map

GROUND_TRUTH = """
SEED -3775500262246337531
XS -7125461266918386070;-1615625651036561142;-6059386570791686130;5095339847540845334;-7266876434583720304;-9076037468522061763;-5639851622577013334;3253350368180385772;3;3;4;3;3;
XSINT 773;237;743;667;656;926;141;886;
MAP1 mMmMmmm|.M?.M..|MM.?M..|?.?.$M.|M?.M..M|E.R.E.R|R.EM?.E|.$.R.E$|TTTTtTT|RE.?EM.|?...MM.|?M..?..|.?..RM.|M?.M..M|rRRrRRR|
EDGES1 1,0>1,1 1,0>2,1 3,0>4,1 1,1>0,2 2,1>1,2 2,1>3,2 4,1>3,2 4,1>4,2 0,2>0,3 1,2>0,3 3,2>2,3 4,2>4,3 4,2>5,3 0,3>0,4 0,3>1,4 2,3>1,4 4,3>3,4 5,3>6,4 0,4>0,5 1,4>0,5 1,4>2,5 3,4>4,5 6,4>6,5 0,5>0,6 2,5>2,6 2,5>3,6 4,5>4,6 6,5>6,6 0,6>1,7 2,6>1,7 3,6>3,7 4,6>5,7 6,6>6,7 1,7>0,8 1,7>1,8 1,7>2,8 3,7>3,8 5,7>5,8 6,7>6,8 0,8>0,9 1,8>1,9 2,8>3,9 3,8>4,9 5,8>4,9 6,8>5,9 0,9>0,10 1,9>0,10 3,9>4,10 4,9>4,10 5,9>5,10 0,10>0,11 0,10>1,11 4,10>4,11 5,10>4,11 0,11>1,12 1,11>1,12 4,11>4,12 4,11>5,12 1,12>0,13 1,12>1,13 4,12>3,13 5,12>6,13 0,13>1,14 1,13>2,14 3,13>4,14 6,13>5,14 6,13>6,14 1,14>3,16 2,14>3,16 4,14>3,16 5,14>3,16 6,14>3,16
MAP2 MmmMmMm|.M.M..M|..M..?M|..M?M$M|..?.M?M|.R..EER|E..R?RE|.M.EE.$|tTtTTtT|.E.MRM.|R.M..$.|?.?.M?R|.M.?M?.|..?MM?.|rrRRRRR|
EDGES2 0,0>1,1 3,0>3,1 5,0>6,1 1,1>2,2 3,1>2,2 6,1>5,2 6,1>6,2 2,2>2,3 2,2>3,3 5,2>4,3 5,2>5,3 6,2>5,3 6,2>6,3 2,3>2,4 3,3>4,4 4,3>5,4 5,3>5,4 5,3>6,4 6,3>6,4 2,4>1,5 4,4>4,5 5,4>4,5 6,4>5,5 6,4>6,5 1,5>0,6 4,5>3,6 4,5>4,6 5,5>5,6 6,5>6,6 0,6>1,7 3,6>3,7 4,6>4,7 5,6>6,7 6,6>6,7 1,7>1,8 3,7>3,8 4,7>3,8 4,7>4,8 6,7>6,8 1,8>1,9 3,8>3,9 3,8>4,9 4,8>5,9 6,8>5,9 1,9>0,10 3,9>2,10 4,9>5,10 5,9>5,10 0,10>0,11 2,10>2,11 5,10>4,11 5,10>5,11 5,10>6,11 0,11>1,12 2,11>3,12 4,11>3,12 5,11>4,12 5,11>5,12 6,11>5,12 1,12>2,13 3,12>3,13 4,12>4,13 5,12>4,13 5,12>5,13 2,13>2,14 3,13>3,14 3,13>4,14 4,13>4,14 4,13>5,14 5,13>6,14 2,14>3,16 3,14>3,16 4,14>3,16 5,14>3,16 6,14>3,16
MAP3 MMmmmMM|.MM..MM|M.?M.$M|.?M.?M.|..??M?$|...E.RR|..RM.EE|..E.RMR|tTtTTtT|.EM?.EM|.?.?$?.|?.M.?.R|MMR..M.|ME...?M|RrrrRrR|
EDGES3 0,0>1,1 1,0>2,1 5,0>5,1 6,0>6,1 1,1>0,2 1,1>2,2 2,1>3,2 5,1>5,2 6,1>6,2 0,2>1,3 2,2>2,3 3,2>4,3 5,2>5,3 6,2>5,3 1,3>2,4 2,3>3,4 4,3>3,4 5,3>4,4 5,3>5,4 5,3>6,4 2,4>3,5 3,4>3,5 4,4>5,5 5,4>6,5 6,4>6,5 3,5>2,6 3,5>3,6 5,5>5,6 6,5>6,6 2,6>2,7 3,6>4,7 5,6>4,7 6,6>5,7 6,6>6,7 2,7>1,8 4,7>3,8 4,7>4,8 5,7>4,8 6,7>6,8 1,8>1,9 3,8>2,9 3,8>3,9 4,8>3,9 4,8>5,9 6,8>6,9 1,9>1,10 2,9>1,10 3,9>3,10 3,9>4,10 5,9>5,10 6,9>5,10 1,10>0,11 1,10>2,11 3,10>2,11 4,10>4,11 5,10>4,11 5,10>6,11 0,11>0,12 2,11>1,12 2,11>2,12 4,11>5,12 6,11>5,12 0,12>0,13 1,12>0,13 2,12>1,13 5,12>5,13 5,12>6,13 0,13>0,14 1,13>0,14 5,13>4,14 6,13>6,14 0,14>3,16 4,14>3,16 6,14>3,16
SEED 42
XS 3553440125194606449;-1850319953427250933;2944846008281095542;2967949513766538177;-871195780171771494;8991170170311133055;5782326992556742051;-6738130162589255370;1;6;5;0;6;
XSINT 224;341;771;88;61;527;25;123;
MAP1 mMMmMmm|M.$?.?.|.??M..M|.M?M.$.|?.M?.M.|R.E..E.|$.MRR..|.MR.M..|TtTTTTt|R.?ME.M|.EM.ME.|..?E?..|.?MR...|.ME.M..|rrRRrRr|
EDGES1 1,0>0,1 1,0>2,1 2,0>3,1 4,0>5,1 0,1>1,2 2,1>2,2 3,1>2,2 3,1>3,2 5,1>6,2 1,2>1,3 2,2>1,3 2,2>2,3 3,2>3,3 6,2>5,3 1,3>0,4 1,3>2,4 2,3>2,4 3,3>3,4 5,3>5,4 0,4>0,5 2,4>2,5 3,4>2,5 5,4>5,5 0,5>0,6 2,5>2,6 2,5>3,6 5,5>4,6 0,6>1,7 2,6>1,7 2,6>2,7 3,6>4,7 4,6>4,7 1,7>0,8 1,7>2,8 2,7>3,8 4,7>4,8 4,7>5,8 0,8>0,9 2,8>2,9 3,8>3,9 4,8>3,9 5,8>4,9 5,8>6,9 0,9>1,10 2,9>1,10 3,9>2,10 4,9>4,10 6,9>5,10 1,10>2,11 2,10>3,11 4,10>4,11 5,10>4,11 2,11>1,12 3,11>2,12 3,11>3,12 4,11>3,12 1,12>1,13 1,12>2,13 2,12>2,13 3,12>2,13 3,12>4,13 1,13>2,14 2,13>2,14 2,13>3,14 4,13>5,14 2,14>3,16 3,14>3,16 5,14>3,16
MAP2 MmmmMmm|$M...M.|.M....M|M?....M|M$?..M?|R.ER.EE|E.R.M.M|.M..R?.|TtTtTTt|.M.M.EM|..REM.?|..M?R?.|..EMM?.|.M?.$?M|rrRRRrR|
EDGES2 0,0>0,1 0,0>1,1 4,0>5,1 0,1>1,2 1,1>1,2 5,1>6,2 1,2>0,3 1,2>1,3 6,2>6,3 0,3>0,4 1,3>1,4 1,3>2,4 6,3>5,4 6,3>6,4 0,4>0,5 1,4>0,5 1,4>2,5 2,4>3,5 5,4>5,5 6,4>6,5 0,5>0,6 2,5>2,6 3,5>4,6 5,5>6,6 6,5>6,6 0,6>1,7 2,6>1,7 4,6>4,7 6,6>5,7 1,7>0,8 1,7>2,8 4,7>4,8 5,7>5,8 0,8>1,9 2,8>3,9 4,8>5,9 5,8>5,9 5,8>6,9 1,9>2,10 3,9>3,10 3,9>4,10 5,9>4,10 6,9>6,10 2,10>2,11 3,10>2,11 4,10>3,11 4,10>4,11 6,10>5,11 2,11>2,12 2,11>3,12 3,11>3,12 4,11>4,12 4,11>5,12 5,11>5,12 2,12>1,13 3,12>2,13 3,12>4,13 4,12>5,13 5,12>5,13 5,12>6,13 1,13>2,14 2,13>2,14 4,13>3,14 5,13>4,14 6,13>6,14 2,14>3,16 3,14>3,16 4,14>3,16 6,14>3,16
MAP3 MmMMMmm|.M$M.?.|?.M.?M.|M?.M$..|.M.M.M.|E.RM.M.|RMEE..E|E.RM.M.|tTtTTTT|..E?M??|.?.$.??|.MER.M?|M.M.?RR|E..M.?M|rRrrRRr|
EDGES3 0,0>1,1 2,0>2,1 3,0>3,1 4,0>5,1 1,1>0,2 2,1>2,2 3,1>4,2 5,1>4,2 5,1>5,2 0,2>0,3 0,2>1,3 2,2>3,3 4,2>3,3 4,2>4,3 5,2>4,3 0,3>1,4 1,3>1,4 3,3>3,4 4,3>5,4 1,4>0,5 3,4>2,5 3,4>3,5 5,4>5,5 0,5>0,6 0,5>1,6 2,5>2,6 3,5>3,6 5,5>6,6 0,6>0,7 1,6>2,7 2,6>3,7 3,6>3,7 6,6>5,7 0,7>1,8 2,7>3,8 3,7>4,8 5,7>5,8 5,7>6,8 1,8>2,9 3,8>2,9 4,8>3,9 4,8>4,9 5,8>5,9 6,8>6,9 2,9>1,10 2,9>3,10 3,9>3,10 4,9>5,10 5,9>5,10 6,9>6,10 1,10>1,11 3,10>2,11 3,10>3,11 5,10>5,11 5,10>6,11 6,10>6,11 1,11>0,12 2,11>2,12 3,11>4,12 5,11>5,12 6,11>6,12 0,12>0,13 2,12>3,13 4,12>3,13 5,12>5,13 6,12>5,13 6,12>6,13 0,13>1,14 3,13>4,14 5,13>4,14 5,13>5,14 6,13>5,14 1,14>3,16 4,14>3,16 5,14>3,16
SEED 987654321
XS -8979870017310612517;1758735197655942569;8862677134776340290;5660248312296689272;-1978949210382249140;8142944753993831838;-6650788706791067987;-5968854874423000261;0;1;1;1;3;
XSINT 549;284;145;636;238;919;814;677;
MAP1 MMmmMMM|??.?MM$|.M?.M.?|$?...?.|MM?..M$|.R...R.|E.M..EM|RMR..RE|TTttTtT|.E...EE|MR..M.?|.EM..?M|.?M.M.?|M..M?.M|rRRrRrR|
EDGES1 0,0>0,1 1,0>1,1 4,0>3,1 5,0>4,1 6,0>5,1 6,0>6,1 0,1>1,2 1,1>1,2 3,1>2,2 4,1>4,2 5,1>4,2 6,1>6,2 1,2>0,3 1,2>1,3 2,2>1,3 4,2>5,3 6,2>5,3 0,3>0,4 1,3>1,4 1,3>2,4 5,3>5,4 5,3>6,4 0,4>1,5 1,4>1,5 2,4>1,5 5,4>5,5 6,4>5,5 1,5>0,6 1,5>2,6 5,5>5,6 5,5>6,6 0,6>0,7 0,6>1,7 2,6>2,7 5,6>5,7 6,6>5,7 6,6>6,7 0,7>0,8 1,7>1,8 2,7>1,8 5,7>4,8 5,7>6,8 6,7>6,8 0,8>1,9 1,8>1,9 4,8>5,9 6,8>6,9 1,9>0,10 1,9>1,10 5,9>4,10 6,9>6,10 0,10>1,11 1,10>2,11 4,10>5,11 6,10>5,11 6,10>6,11 1,11>1,12 1,11>2,12 2,11>2,12 5,11>4,12 5,11>6,12 6,11>6,12 1,12>0,13 2,12>3,13 4,12>4,13 6,12>6,13 0,13>1,14 3,13>2,14 3,13>4,14 4,13>4,14 6,13>6,14 1,14>3,16 2,14>3,16 4,14>3,16 6,14>3,16
MAP2 mMmMMmm|?M?M$?.|?MM..?M|.M.$.?.|.M?.?M.|.R.EER.|.ER.ME.|M..ERM.|tTttTTT|.M.?ME.|..M$M?.|...EMR.|..M?.?M|.M..MM.|RrrrRRR|
EDGES2 1,0>0,1 1,0>1,1 3,0>2,1 3,0>3,1 3,0>4,1 4,0>5,1 0,1>0,2 1,1>1,2 2,1>1,2 3,1>2,2 4,1>5,2 5,1>6,2 0,2>1,3 1,2>1,3 2,2>3,3 5,2>5,3 6,2>5,3 1,3>1,4 1,3>2,4 3,3>4,4 5,3>5,4 1,4>1,5 2,4>3,5 4,4>3,5 5,4>4,5 5,4>5,5 1,5>1,6 3,5>2,6 3,5>4,6 4,5>4,6 5,5>5,6 1,6>0,7 2,6>3,7 4,6>4,7 4,6>5,7 5,6>5,7 0,7>1,8 3,7>4,8 4,7>4,8 5,7>5,8 5,7>6,8 1,8>1,9 4,8>3,9 4,8>4,9 5,8>4,9 5,8>5,9 6,8>5,9 1,9>2,10 3,9>2,10 4,9>3,10 5,9>4,10 5,9>5,10 2,10>3,11 3,10>4,11 4,10>5,11 5,10>5,11 3,11>2,12 3,11>3,12 4,11>5,12 5,11>5,12 5,11>6,12 2,12>1,13 3,12>4,13 5,12>4,13 5,12>5,13 6,12>5,13 1,13>0,14 4,13>4,14 4,13>5,14 5,13>5,14 5,13>6,14 0,14>3,16 4,14>3,16 5,14>3,16 6,14>3,16
MAP3 MMmMmmm|?M.?...|$?M....|M.?M...|M?M.?..|RR.E.R.|M?M.E..|.EE.R..|TTTTttt|E.R?...|.M$....|?MR?...|?MMM...|MEMM...|RRRrRrr|
EDGES3 0,0>0,1 1,0>1,1 3,0>3,1 0,1>0,2 1,1>1,2 1,1>2,2 3,1>2,2 0,2>0,3 1,2>0,3 1,2>2,3 2,2>2,3 2,2>3,3 0,3>0,4 0,3>1,4 2,3>2,4 3,3>2,4 3,3>4,4 0,4>0,5 1,4>1,5 2,4>1,5 2,4>3,5 4,4>5,5 0,5>0,6 1,5>0,6 1,5>1,6 3,5>2,6 5,5>4,6 0,6>1,7 1,6>2,7 2,6>2,7 4,6>4,7 1,7>0,8 1,7>1,8 1,7>2,8 2,7>2,8 2,7>3,8 4,7>3,8 0,8>0,9 1,8>0,9 2,8>2,9 3,8>2,9 3,8>3,9 0,9>1,10 2,9>1,10 3,9>2,10 1,10>0,11 1,10>1,11 1,10>2,11 2,10>3,11 0,11>0,12 1,11>0,12 1,11>1,12 2,11>2,12 3,11>3,12 0,12>0,13 0,12>1,13 1,12>1,13 2,12>2,13 3,12>3,13 0,13>0,14 1,13>1,14 1,13>2,14 2,13>2,14 3,13>4,14 0,14>3,16 1,14>3,16 2,14>3,16 4,14>3,16
"""


def _parse_truth():
    blocks = {}
    current = None
    for line in GROUND_TRUTH.strip().splitlines():
        if line.startswith("SEED "):
            current = int(line.split()[1])
            blocks[current] = {}
        elif current is not None:
            key, _, value = line.partition(" ")
            if key.startswith("MAP"):
                value = value.rstrip("|")  # driver appends a separator after the last row
            blocks[current][key] = value
    return blocks


TRUTH = _parse_truth()


def _grid_of(nodes):
    """Match the Java driver's grid convention exactly."""
    rows = []
    for row in nodes:
        line = []
        for node in row:
            connected = node.has_edges or bool(node.parents)
            if connected:
                line.append(node.symbol)
            elif node.room is not None:
                line.append(node.symbol.lower())
            else:
                line.append(".")
        rows.append("".join(line))
    return "|".join(rows)


def _edges_of(nodes):
    parts = []
    for y, row in enumerate(nodes):
        for x, node in enumerate(row):
            for edge in node.edges:
                parts.append(f"{x},{y}>{edge.dst_x},{edge.dst_y}")
    return " ".join(parts)


class RandomXS128Test(unittest.TestCase):
    def test_next_long_and_bounded_ints(self):
        for seed, truth in TRUTH.items():
            with self.subTest(seed=seed):
                xs = RandomXS128(seed)
                values = [v for v in truth["XS"].split(";") if v]
                longs = [int(v) for v in values[:8]]
                small = [int(v) for v in values[8:13]]
                for expected in longs:
                    self.assertEqual(xs.next_long(), expected)
                for expected in small:
                    self.assertEqual(xs.next_int_bounded(7), expected)

    def test_next_int_fresh_instance(self):
        for seed, truth in TRUTH.items():
            with self.subTest(seed=seed):
                xs = RandomXS128(seed)
                for expected in [int(v) for v in truth["XSINT"].split(";") if v]:
                    self.assertEqual(xs.next_int_bounded(1000), expected)


class MapGenerationTest(unittest.TestCase):
    # Ground truth driver ran with ascension >= 1 (1.6x elite multiplier).
    ASCENSION = 1

    def test_maps_match_real_jar(self):
        for seed, truth in TRUTH.items():
            for act in (1, 2, 3):
                with self.subTest(seed=seed, act=act):
                    nodes = generate_act_map(seed, act, ascension_level=self.ASCENSION)
                    self.assertEqual(_grid_of(nodes), truth[f"MAP{act}"])
                    self.assertEqual(_edges_of(nodes), truth[f"EDGES{act}"])


class PathLegalityTest(unittest.TestCase):
    def test_every_actual_map_path_exists(self):
        """Walk one legal path per map via DFS to sanity check path_is_legal."""
        for seed, truth in TRUTH.items():
            for act in (1, 2, 3):
                with self.subTest(seed=seed, act=act):
                    nodes = generate_act_map(seed, act, ascension_level=1)
                    # Pick the lexicographically first legal path greedily.
                    symbols = []
                    x = next(i for i in range(7) if nodes[0][i].has_edges)
                    for y in range(15):
                        symbols.append(nodes[y][x].symbol)
                        edge_targets = [e.dst_x for e in nodes[y][x].edges if e.dst_y == y + 1]
                        if edge_targets:
                            x = min(edge_targets)
                    ok, bad_y, _ = sts_mapgen.path_is_legal(nodes, symbols)
                    self.assertTrue(ok, f"greedy path rejected at y={bad_y}")
                    self.assertIsNone(bad_y)


if __name__ == "__main__":
    unittest.main()
