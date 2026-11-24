import unittest

from analyze_fingerprint_profile import analyze, classify


class FingerprintProfileTest(unittest.TestCase):
    ROOT = ["battleaimod.search.SearchStateKey.fromSaveState(SearchStateKey.java:39)",
            "battleaimod.battleai.BattleAiController.registerTurnState(BattleAiController.java:400)"]

    def test_nested_hash_is_exclusive(self):
        frames = ["com.google.common.hash.AbstractStreamingHasher.putBytesInternal(X.java:1)",
                  "battleaimod.search.SearchStateKey$CanonicalHasher.writeBytes(X.java:2)"]
        self.assertEqual("hash", classify(frames + self.ROOT))

    def test_encoding_and_reading(self):
        self.assertEqual("encoding", classify(["java.util.UUID.toString(UUID.java:380)"] + self.ROOT))
        self.assertEqual("traversal", classify(["java.lang.reflect.Field.get(Field.java:393)"] + self.ROOT))
        self.assertIsNone(classify(self.ROOT[:1]))

    def fixture(self):
        traces = "".join("TRACE %d: (thread=200001)\n%s" % (i, "".join("\t" + f + "\n" for f in frames))
                         for i, frames in [(1, ["java.lang.reflect.Field.get(X.java:1)"] + self.ROOT),
                             (2, ["battleaimod.search.SearchStateKey$CanonicalHasher.writeString(X.java:1)"] + self.ROOT),
                             (3, ["com.google.common.hash.Murmur3_128Hasher.process(X.java:1)"] + self.ROOT),
                             (4, ["startup.Other.work(X.java:1)"])])
        return traces + "CPU SAMPLES BEGIN (total = 20) now\nrank self accum count trace method\n" + \
            " 1 40.00% 40.00% 8 2 encode\n 2 30.00% 70.00% 6 1 read\n" + \
            " 3 20.00% 90.00% 4 3 hash\n 4 10.00% 100.00% 2 4 startup\nCPU SAMPLES END\n"

    def test_shares_and_scaled_estimates_exclude_startup(self):
        result = analyze(self.fixture(), 900)
        self.assertEqual(18, result["fingerprint_samples"])
        self.assertEqual(20, result["all_thread_samples"])
        self.assertEqual(300, result["buckets"]["traversal"]["estimated_ms"])
        self.assertEqual(400, result["buckets"]["encoding"]["estimated_ms"])
        self.assertEqual(200, result["buckets"]["hash"]["estimated_ms"])

    def test_uses_last_complete_table_not_sum(self):
        text = self.fixture()
        self.assertEqual(18, analyze(text + text)["fingerprint_samples"])
        self.assertEqual(18, analyze(text + "CPU SAMPLES BEGIN (total = 4)\n")["fingerprint_samples"])

    def test_rejects_cutoff_and_absent_workload(self):
        with self.assertRaisesRegex(ValueError, "truncated/cut off"):
            analyze(self.fixture().replace("total = 20", "total = 30"))
        with self.assertRaisesRegex(ValueError, "missing trace"):
            analyze(self.fixture().replace("4 startup", "99 startup"))
        with self.assertRaisesRegex(ValueError, "no turn-state"):
            analyze(self.fixture().replace("registerTurnState", "irrelevant"))


if __name__ == "__main__":
    unittest.main()
