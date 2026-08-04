import subprocess
import unittest
from unittest.mock import patch

from battle_test.run import terminate_process_tree


class ProcessCleanupTest(unittest.TestCase):
    def test_windows_cleanup_kills_the_process_tree(self):
        process = unittest.mock.Mock()
        process.poll.return_value = None
        process.pid = 1234
        with patch("battle_test.run.os.name", "nt"), patch("battle_test.run.subprocess.run") as run:
            terminate_process_tree(process)
        run.assert_called_once_with(["taskkill", "/PID", "1234", "/T", "/F"], capture_output=True, timeout=5)

    def test_completed_process_is_not_killed(self):
        process = unittest.mock.Mock()
        process.poll.return_value = 0
        with patch("battle_test.run.subprocess.run") as run:
            terminate_process_tree(process)
        run.assert_not_called()
