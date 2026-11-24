import io
from pathlib import Path
import types
import unittest

import probe


class ProbeTest(unittest.TestCase):
    def test_command_allowlist(self):
        process = types.SimpleNamespace(stdin=io.StringIO())
        for command in ("new", "new 42 IRONCLAD 0", "step", "unlockAll", "state\nnew", " state", "quit\nstep"):
            with self.assertRaises(ValueError):
                probe.send(process, command)
        self.assertEqual(process.stdin.getvalue(), "")
        probe.send(process, "state")
        probe.send(process, "quit")
        self.assertEqual(process.stdin.getvalue(), "state\nquit\n")

    def test_run_and_graphics_guards(self):
        state = dict(lifecycle="BOOT", floor=0, act=0, graphicsType="Mock",
                     nativeObservationAvailable=True, nativeObservation={"phase": "BOOT"})
        for key in ("playerInitialized", "mapInitialized", "roomInitialized", "seedInitialized", "seedSet", "glPresent"):
            state[key] = False
        probe.validate_state(state)
        for key in ("playerInitialized", "mapInitialized", "roomInitialized", "seedInitialized", "seedSet", "glPresent"):
            with self.assertRaises(AssertionError):
                probe.validate_state({**state, key: True})

    def test_fixed_heap_and_headless(self):
        self.assertIn("-Xmx512m", probe.JVM_FLAGS)
        self.assertIn("-Djava.awt.headless=true", probe.JVM_FLAGS)

    def test_java_dispatch_is_read_only(self):
        source = Path(__file__).with_name("PocReadProbe.java").read_text()
        self.assertIn('command.equals("state")', source)
        self.assertIn('command.equals("quit")', source)
        self.assertNotIn("kernel.newRun(", source)


if __name__ == "__main__":
    unittest.main()
