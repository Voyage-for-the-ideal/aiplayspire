import argparse
import subprocess
from pathlib import PurePosixPath


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--message")
    parser.add_argument("paths", nargs="+")
    args = parser.parse_args()
    paths = set(args.paths)
    if any(PurePosixPath(p).as_posix() != p or PurePosixPath(p).is_absolute()
           or ".." in PurePosixPath(p).parts or p in ("", ".") or ":" in p or "\\" in p for p in paths):
        parser.error("PASS_EXACT_REPOSITORY_RELATIVE_FILE_PATHS")
    command = ["git", "--literal-pathspecs"]
    staged = subprocess.check_output(command + ["diff", "--cached", "--name-only", "--no-renames", "-z"])
    names = {p.decode() for p in staged.split(b"\0") if p}
    if names != paths:
        parser.exit(2, f"STAGE_ONLY_DECLARED_FILES: undeclared={sorted(names - paths)} missing={sorted(paths - names)}\n")
    if args.message is not None:
        unstaged = subprocess.check_output(command + ["diff", "--name-only", "-z", "--", *sorted(paths)])
        if unstaged:
            parser.exit(2, "STAGE_DECLARED_FILES_AT_THE_INTENDED_REVISION\n")
        subprocess.run(command + ["commit", "--only", "-m", args.message, "--", *sorted(paths)], check=True)


if __name__ == "__main__":
    main()
