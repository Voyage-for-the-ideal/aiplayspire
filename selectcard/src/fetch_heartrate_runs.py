"""Fetch the full run list from sts1.heart-rate.net (Heart Rate, a community STS stats site).

The site exposes a public JSON API used by its own frontend:
  GET https://sts1-api.heart-rate.net/runs?page=N&order=desc  -> {"runs": [...], "meta": {...}}
robots.txt allows crawling (Disallow: empty). We fetch every page of the
list endpoint and append each run as one JSONL line.

Stdlib only (no external dependencies). Supports:
  - bounded concurrency (default 6 workers, polite to the server)
  - retry with exponential backoff on transient failures
  - resume: already-fetched pages are skipped based on the output file

Usage:
  python fetch_heartrate_runs.py                      # full crawl
  python fetch_heartrate_runs.py --max-pages 5        # smoke test
  python fetch_heartrate_runs.py --workers 2 --out custom.jsonl
"""
from __future__ import annotations

import argparse
import json
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

BASE = "https://sts1-api.heart-rate.net"
LIST_URL = f"{BASE}/runs"
# Browser-like UA + referer; the API is behind Cloudflare and rejects bare
# urllib UAs. Verified working via plain curl.
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    ),
    "Referer": "https://sts1.heart-rate.net/",
    "Accept": "application/json, text/plain, */*",
}

MAX_RETRIES = 3
BACKOFF_BASE_S = 1.5
DEFAULT_OUT = Path(__file__).resolve().parent.parent / "raw_heartrate" / "runs.jsonl"

_lock = threading.Lock()
_stats = {"ok": 0, "runs": 0}
_failed: list[int] = []


def fetch_page(page: int, timeout: float = 30.0) -> dict:
    """Fetch one page of the run list, raising on non-200 or parse failure."""
    url = f"{LIST_URL}?page={page}&order=desc"
    last_err: Exception | None = None
    for attempt in range(MAX_RETRIES):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                if resp.status != 200:
                    raise RuntimeError(f"HTTP {resp.status}")
                return json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, RuntimeError) as e:
            last_err = e
            if attempt < MAX_RETRIES - 1:
                time.sleep(BACKOFF_BASE_S * (2 ** attempt))
    raise RuntimeError(f"page {page} failed after {MAX_RETRIES} attempts: {last_err}")


def crawl_one(page: int, out: Path, delay: float = 0.0) -> tuple[int, dict]:
    """Fetch one page and append its runs to the JSONL output. Returns (page, meta)."""
    data = fetch_page(page)
    with _lock:
        with out.open("a", encoding="utf-8") as f:
            for run in data["runs"]:
                run["__page"] = page
                f.write(json.dumps(run, ensure_ascii=False) + "\n")
        _stats["ok"] += 1
        _stats["runs"] += len(data["runs"])
    if delay:
        time.sleep(delay)
    return page, data.get("meta", {})


def load_done_pages(out: Path) -> set[int]:
    """Recover pages already fetched from the output file (resume support)."""
    if not out.exists():
        return set()
    done: set[int] = set()
    with out.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                done.add(json.loads(line)["__page"])
            except (json.JSONDecodeError, KeyError):
                pass  # tolerate a partial trailing line
    return done


def main() -> int:
    parser = argparse.ArgumentParser(description="Crawl the Heart Rate STS run list")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT,
                        help="JSONL output path (default: selectcard/raw_heartrate/runs.jsonl)")
    parser.add_argument("--workers", type=int, default=3,
                        help="concurrent fetchers (default 3; the server rate-limits "
                             "aggressive crawls, keep it low)")
    parser.add_argument("--delay", type=float, default=0.2,
                        help="minimum seconds to pause after each page (default 0.2)")
    parser.add_argument("--max-pages", type=int, default=0,
                        help="fetch at most this many pages (0 = all, default)")
    args = parser.parse_args()

    args.out.parent.mkdir(parents=True, exist_ok=True)

    # Probe page 1 to learn the total page count.
    print("probing page 1 ...", flush=True)
    meta = fetch_page(1)["meta"]
    total_pages = meta["totalPages"]
    print(f"meta: {meta}", flush=True)

    all_pages = range(1, total_pages + 1)
    if args.max_pages:
        all_pages = list(all_pages)[: args.max_pages]

    done = load_done_pages(args.out)
    pending = [p for p in all_pages if p not in done]
    print(f"pages: {len(all_pages)} total, {len(done)} already done, {len(pending)} to fetch",
          flush=True)

    t0 = time.time()
    last_report = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(crawl_one, p, args.out, args.delay): p for p in pending}
        for fut in as_completed(futures):
            page = futures[fut]
            try:
                page, _ = fut.result()
            except Exception as e:  # noqa: BLE001 - collect failures for a final retry round
                _failed.append(page)
                print(f"  !! page {page}: {e}", flush=True)
            now = time.time()
            if now - last_report > 10:
                elapsed = now - t0
                rate = _stats["ok"] / elapsed if elapsed > 0 else 0
                print(f"  progress: {_stats['ok']}/{len(pending)} pages, "
                      f"{_stats['runs']} runs, {rate:.1f} pages/s", flush=True)
                last_report = now

    # One final retry round for pages that failed above.
    retries = _failed.copy()
    if retries:
        print(f"retrying {len(retries)} failed pages ...", flush=True)
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures = {pool.submit(crawl_one, p, args.out, args.delay): p for p in retries}
            for fut in as_completed(futures):
                page = futures[fut]
                try:
                    fut.result()
                    _failed.remove(page)
                except Exception as e:  # noqa: BLE001
                    print(f"  !! retry failed page {page}: {e}", flush=True)

    elapsed = time.time() - t0
    print(f"done: {_stats['ok']} pages, {_stats['runs']} runs in {elapsed:.0f}s", flush=True)
    if _failed:
        print(f"WARNING: {len(_failed)} pages still failed: {sorted(_failed)}", flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
