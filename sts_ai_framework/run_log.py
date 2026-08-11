"""运行日志保存:每次运行生成 debug/run_YYYYMMDD_HHMMSS.log。

- 终端输出 (print/sys.stdout/stderr) 通过 TeeWriter 双写:终端原样透传,
  文件侧剥离 ANSI 颜色码、将 \r 进度刷新转为完整行。
- 结构化决策事件通过 log_event() 显式写入,行前缀 "EVENT " 便于 grep 筛选。

模块顶层无副作用,只有调用 setup_run_log() 后才创建文件。
"""

import logging
import os
import re
import sys
import time

# colorama 的 Fore.YELLOW / \033[32m / Style.RESET_ALL 等
ANSI_RE = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")

LOGGER_NAME = "sts_ai"


def setup_run_log(run_log_dir: str = "debug") -> str:
    """配置运行日志 FileHandler(utf-8),生成 run_YYYYMMDD_HHMMSS.log,返回文件路径。

    幂等:logger 已有 handler 时不重复添加。
    """
    os.makedirs(run_log_dir, exist_ok=True)
    path = os.path.join(run_log_dir, time.strftime("run_%Y%m%d_%H%M%S.log"))

    logger = logging.getLogger(LOGGER_NAME)
    logger.setLevel(logging.INFO)
    # 不冒泡到 root logger,避免 INFO 记录触发 lastResort 打到 stderr
    logger.propagate = False
    if not logger.handlers:
        handler = logging.FileHandler(path, encoding="utf-8")
        handler.setFormatter(logging.Formatter("%(asctime)s %(message)s", datefmt="%Y-%m-%d %H:%M:%S"))
        logger.addHandler(handler)
    return path


def _strip_ansi(text: str) -> str:
    return ANSI_RE.sub("", text)


def log_event(**fields) -> None:
    """写一行结构化事件:EVENT key=value | key=value(值为 None 的字段跳过)。"""
    parts = [f"{k}={v}" for k, v in fields.items() if v is not None]
    logging.getLogger(LOGGER_NAME).info("EVENT " + " | ".join(parts))


class TeeWriter:
    """把输出同时写到终端(原样)和日志(去 ANSI、\r 转完整行)。"""

    def __init__(self, real, logger):
        self._real = real  # 终端真实流(安装时的 sys.stdout,即 colorama 包装器)
        self._logger = logger
        self._buf = ""  # 行缓冲:跨 write 调用的残片

    def write(self, s):
        # 1) 终端先写,行为完全不变
        self._real.write(s)
        # 2) 文件侧:去 ANSI,以 \r / \n 为行界拆成完整行
        text = _strip_ansi(s)
        parts = re.split(r"[\r\n]", text)
        parts[0] = self._buf + parts[0]
        has_cr = "\r" in text
        buf_part = parts.pop()
        for line in parts:
            line = line.rstrip()  # 去掉进度行末尾的填充空格
            if line:
                self._logger.info(line)
        if has_cr:
            # 含 \r 的是终端覆盖式进度行:内容本身完整,立即写为一行
            line = buf_part.rstrip()
            if line:
                self._logger.info(line)
            self._buf = ""
        else:
            self._buf = buf_part  # 无行界部分留作缓冲,等 \n 或 close

    def flush(self):
        self._real.flush()

    def close(self):
        """写入残留行缓冲并恢复 sys.stdout。"""
        tail = self._buf.rstrip()
        if tail:
            self._logger.info(tail)
        sys.stdout = self._real

    def isatty(self):
        return self._real.isatty()


def install_stdout_tee() -> TeeWriter:
    """包装当前 sys.stdout 与 sys.stderr 为 TeeWriter,返回 stdout 的 tee(用于收尾)。"""
    logger = logging.getLogger(LOGGER_NAME)
    tee = TeeWriter(sys.stdout, logger)
    sys.stdout = tee
    sys.stderr = TeeWriter(sys.stderr, logger)
    return tee
