"""Supervised serial JSONL worker. A failed transaction is never retried."""
from collections import deque
import json
import os
import queue
import subprocess
import threading


class Worker:
    def __init__(self, executable):
        self.executable = str(executable)
        self.process = None
        self.serial = 0
        self.generation = 0
        self.stderr = deque(maxlen=64)
        self.lock = threading.RLock()

    def _start(self):
        self.generation += 1
        self.replies = queue.Queue()
        self.stderr.clear()
        self.process = subprocess.Popen(
            [self.executable, "--serve"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        p, replies, errors = self.process, self.replies, self.stderr

        def read_stdout():
            try:
                for line in p.stdout:
                    replies.put(line)
            finally:
                replies.put(None)

        def read_stderr():
            for line in p.stderr:
                errors.append(line[-2000:].decode("utf-8", errors="replace"))

        self.readers = [threading.Thread(target=f, daemon=True) for f in (read_stdout, read_stderr)]
        for thread in self.readers:
            thread.start()

    def close(self):
        with self.lock:
            p, self.process = self.process, None
            if p is None:
                return
            if p.poll() is None:
                p.kill()
            p.wait(timeout=5)
            p.stdin.close()
            for thread in self.readers:
                thread.join(timeout=2)
            p.stdout.close()
            p.stderr.close()

    def execute(self, request, timeout_ms):
        with self.lock:
            if self.process is None or self.process.poll() is not None:
                self.close()
                self._start()
            self.serial += 1
            request = dict(request, request_id=self.serial)
            try:
                self.process.stdin.write((json.dumps(request, ensure_ascii=True, separators=(",", ":")) + "\n").encode())
                self.process.stdin.flush()
                try:
                    line = self.replies.get(timeout=timeout_ms / 1000 + 2)
                except queue.Empty:
                    raise subprocess.TimeoutExpired(self.executable, timeout_ms / 1000 + 2)
                if line is None:
                    raise RuntimeError(f"worker_exit:{self.process.poll()} stderr={''.join(self.stderr)[-2000:]}")
                result = json.loads(line)
                if not isinstance(result, dict) or result.get("request_id") != self.serial or not isinstance(result.get("supported"), bool):
                    raise ValueError("worker_response_identity_mismatch")
                if "metrics" in result and not isinstance(result["metrics"], dict):
                    raise ValueError("invalid_worker_metrics")
                result.setdefault("metrics", {})["worker_generation"] = self.generation
                if not result["supported"]:
                    self.close()
                return result
            except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired):
                self.close()
                raise
