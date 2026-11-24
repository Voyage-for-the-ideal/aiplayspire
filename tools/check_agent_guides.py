"""Check that each pair of repository agent guides has matching content."""

import sys
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    failed = False
    for directory in (root, root / "selectcard"):
        paths = [directory / name for name in ("AGENTS.md", "CLAUDE.md")]
        texts = []
        for path in paths:
            try:
                texts.append(path.read_bytes().decode("utf-8").replace("\r\n", "\n"))
            except (OSError, UnicodeError) as exc:
                print(f"Could not read {path.relative_to(root)}: {exc}", file=sys.stderr)
                failed = True
        if len(texts) == 2 and texts[0] != texts[1]:
            left, right = (path.relative_to(root) for path in paths)
            print(f"Agent guides differ: {left} and {right}", file=sys.stderr)
            failed = True

    if failed:
        return 1
    print("Agent guides match in the repository root and selectcard/.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
