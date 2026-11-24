import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

GUARD = Path(__file__).with_name("commit_scope.py")


class CommitScopeTest(unittest.TestCase):
    def test_previous_staged_deletion_is_not_included(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                return subprocess.check_output(["git", "-c", "user.name=Fixture", "-c",
                                                "user.email=fixture@example.invalid", *args], cwd=root)

            git("init", "-q")
            doc, unrelated = root / "notes.md", root / "fetch_heartrate_runs.py"
            doc.write_text("old\n")
            unrelated.write_text("print(1)\n")
            git("add", "--", doc.name, unrelated.name)
            git("commit", "-qm", "baseline")
            start = git("rev-parse", "HEAD")
            git("rm", "-q", unrelated.name)
            doc.write_text("new\n")
            git("add", "--", doc.name)

            red = subprocess.run([sys.executable, str(GUARD), doc.name], cwd=root, capture_output=True)
            self.assertEqual(red.returncode, 2)
            self.assertIn(b"STAGE_ONLY_DECLARED_FILES", red.stderr)
            self.assertEqual(git("rev-parse", "HEAD"), start)
            git("commit", "-qm", "legacy unrestricted commit")
            self.assertEqual(set(git("diff", "--name-only", "HEAD^", "HEAD").decode().split()),
                             {doc.name, unrelated.name})
            git("reset", "--soft", start.decode().strip())
            git("restore", "--staged", "--", unrelated.name)
            subprocess.run([sys.executable, str(GUARD), doc.name], cwd=root, check=True)
            git("--literal-pathspecs", "commit", "--only", "-qm", "scoped", "--", doc.name)
            self.assertEqual(git("diff", "--name-only", "HEAD^", "HEAD").decode().strip(), doc.name)
            self.assertIn(unrelated.name, git("ls-tree", "--name-only", "HEAD").decode())


if __name__ == "__main__":
    unittest.main()
