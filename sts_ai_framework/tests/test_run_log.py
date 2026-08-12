import logging
import os
import re
import sys
import tempfile
import unittest

from sts_ai_framework.run_log import TeeWriter, _strip_ansi, setup_run_log


class _FakeReal:
    """模拟终端流:记录写入内容,不真正输出。"""

    def __init__(self):
        self.writes = []

    def write(self, s):
        self.writes.append(s)

    def flush(self):
        pass

    def isatty(self):
        return True


def _file_logger(path: str) -> logging.Logger:
    logger = logging.getLogger("test_tee_logger")
    logger.setLevel(logging.INFO)
    logger.propagate = False
    handler = logging.FileHandler(path, encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s %(message)s", datefmt="%Y-%m-%d %H:%M:%S"))
    logger.addHandler(handler)
    return logger


def _close_logger(logger: logging.Logger) -> None:
    """关闭并移除 logger 的全部 handler,释放文件句柄(Windows 上必须 close)。"""
    for handler in logger.handlers[:]:
        handler.close()
        logger.handlers.remove(handler)


class StripAnsiTest(unittest.TestCase):
    def test_strip_colorama_codes(self):
        # Fore.YELLOW / Style.RESET_ALL / 裸 \033[32m 均被剥离
        self.assertEqual(_strip_ansi("\x1b[33m黄色\x1b[0m"), "黄色")
        self.assertEqual(_strip_ansi("\x1b[32m绿色\x1b[0m"), "绿色")
        self.assertEqual(_strip_ansi("无颜色"), "无颜色")


class TeeWriterTest(unittest.TestCase):
    def test_progress_lines_and_plain_lines(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = os.path.join(tmp, "run.log")
            logger = _file_logger(log_path)

            orig_stdout = sys.stdout
            real = _FakeReal()
            try:
                tee = TeeWriter(real, logger)
                # \r 进度行(无换行,末尾有填充空格)+ 带 ANSI 的普通行
                tee.write("\r战斗进行中... (第 5 层 | HP: 52/80 | 能量: 3)   ")
                tee.write("\r战斗进行中... (第 5 层 | HP: 53/80 | 能量: 2)   ")
                tee.write("\x1b[33m行动已提交。\x1b[0m\n")
                tee.close()
            finally:
                sys.stdout = orig_stdout

            _close_logger(logger)
            with open(log_path, encoding="utf-8") as f:
                content = f.read()

            # 无 ANSI 码、无 \r、进度行无行尾填充空格
            self.assertNotIn("\x1b", content)
            self.assertNotIn("\r", content)
            lines = [ln for ln in content.splitlines() if ln.strip()]
            self.assertIn("战斗进行中... (第 5 层 | HP: 52/80 | 能量: 3)", lines[0])
            self.assertIn("战斗进行中... (第 5 层 | HP: 53/80 | 能量: 2)", lines[1])
            self.assertTrue(lines[2].endswith("行动已提交。"), lines[2])

    def test_close_flushes_pending_line(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = os.path.join(tmp, "run.log")
            logger = _file_logger(log_path)

            orig_stdout = sys.stdout
            real = _FakeReal()
            try:
                tee = TeeWriter(real, logger)
                tee.write("未换行的残留内容")
                tee.close()
            finally:
                sys.stdout = orig_stdout

            _close_logger(logger)
            with open(log_path, encoding="utf-8") as f:
                content = f.read()
            self.assertIn("未换行的残留内容", content)


class SetupRunLogTest(unittest.TestCase):
    def tearDown(self):
        # 清理全局 logger 的 handler,避免影响其他用例
        _close_logger(logging.getLogger("sts_ai"))

    def test_filename_format(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = setup_run_log(tmp)
            self.assertTrue(os.path.isfile(path))
            self.assertRegex(os.path.basename(path), r"run_\d{8}_\d{6}\.log")
            # 在临时目录清理前释放文件句柄(Windows)
            _close_logger(logging.getLogger("sts_ai"))

    def test_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            logger = logging.getLogger("sts_ai")
            p1 = setup_run_log(tmp)
            p2 = setup_run_log(tmp)
            self.assertEqual(p1, p2)
            self.assertEqual(len(logger.handlers), 1)
            _close_logger(logger)


if __name__ == "__main__":
    unittest.main()
