import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from battle_engine.tools.oracle import isolated_java_env
from sts_ai_framework.headless.receipt import verify_driver

JAVA_CHILD = """
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

class TempAsset {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            String java = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            System.exit(new ProcessBuilder(java, "TempAsset.java", "child").inheritIO().start().waitFor());
        }
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "same-native-asset"),
                          "asset", StandardOpenOption.CREATE_NEW);
    }
}
"""


class RuntimeInvariants(unittest.TestCase):
    def test_late_receipt_cannot_advance_a_new_state_with_same_sequence(self):
        before = {"state_token": "generation-one", "sequence": 0}
        token = {"source_state_token": "generation-zero", "sequence": 1, "request_id": 1,
                 "method": "confirmGrid", "args": []}
        after = {"state": {"driver_receipt": token}, "request_id": 1}
        with self.assertRaisesRegex(ValueError, "does not bind"):
            verify_driver(before, after, "confirmGrid", ())
        token["source_state_token"] = before["state_token"]
        verify_driver(before, after, "confirmGrid", ())
        token["request_id"] = True
        with self.assertRaisesRegex(ValueError, "Invalid driver receipt"):
            verify_driver(before, after, "confirmGrid", ())

    @unittest.skipUnless(shutil.which("java"), "JDK required for actual parent and child processes")
    def test_legacy_shared_temp_collides_but_each_relaunched_jvm_is_isolated(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "TempAsset.java").write_text(JAVA_CHILD, encoding="utf-8")
            shared = root / "legacy-shared"
            shared.mkdir()

            def run(envs):
                with subprocess.Popen(["java", "TempAsset.java"], cwd=root, env=envs[0],
                                      stdout=subprocess.PIPE, stderr=subprocess.STDOUT) as a:
                    with subprocess.Popen(["java", "TempAsset.java"], cwd=root, env=envs[1],
                                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT) as b:
                        return [(proc, proc.communicate(timeout=30)[0]) for proc in (a, b)]

            legacy = dict(os.environ, JAVA_TOOL_OPTIONS=f'-Djava.io.tmpdir="{shared}"')
            result = run([legacy, legacy])
            self.assertEqual(sum(proc.returncode == 0 for proc, _ in result), 1)
            failed = next(output for proc, output in result if proc.returncode)
            self.assertIn(b"java.nio.file.FileAlreadyExistsException", failed)
            instances = [root / "first", root / "second"]
            for p in instances:
                p.mkdir()
            result = run([isolated_java_env(p) for p in instances])
            self.assertEqual([proc.returncode for proc, _ in result], [0, 0], result)
            for p in instances:
                self.assertEqual((p / "tmp" / "same-native-asset").read_text(), "asset")


if __name__ == "__main__":
    unittest.main()
