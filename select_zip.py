from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path


IGNORED_PARTS = {
    ".git",
    ".github",
    "dist",
    "build",
    "deliverables",
    "__pycache__",
}


def git_changed_zips(before: str, after: str) -> list[Path]:
    if not before or not after or set(before) == {"0"}:
        return []

    result = subprocess.run(
        ["git", "diff", "--name-only", before, after],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        return []

    files = []
    for raw in result.stdout.splitlines():
        path = Path(raw.strip())
        if (
            path.suffix.lower() == ".zip"
            and path.is_file()
            and not any(part.lower() in IGNORED_PARTS for part in path.parts)
        ):
            files.append(path)
    return files


def all_repository_zips() -> list[Path]:
    files: list[Path] = []
    for path in Path(".").rglob("*.zip"):
        if not path.is_file():
            continue
        if any(part.lower() in IGNORED_PARTS for part in path.parts):
            continue
        files.append(path)
    return files


def newest_by_git_or_mtime(files: list[Path]) -> Path:
    if not files:
        raise FileNotFoundError(
            "No ZIP file was found. Upload any Android or Windows source ZIP."
        )

    scored = []
    for path in files:
        commit_time = 0
        result = subprocess.run(
            ["git", "log", "-1", "--format=%ct", "--", str(path)],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode == 0 and result.stdout.strip().isdigit():
            commit_time = int(result.stdout.strip())
        scored.append((commit_time, path.stat().st_mtime, str(path), path))

    scored.sort(reverse=True)
    return scored[0][3]


def write_output(path: Path, github_output: str | None) -> None:
    if github_output:
        with open(github_output, "a", encoding="utf-8") as handle:
            handle.write(f"zip_file={path.as_posix()}\n")
            handle.write(f"zip_name={path.name}\n")

    print(
        json.dumps(
            {
                "zip_file": path.as_posix(),
                "zip_name": path.name,
                "size_bytes": path.stat().st_size,
            },
            indent=2,
        )
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", default="")
    parser.add_argument("--after", default="")
    parser.add_argument(
        "--github-output",
        default=os.environ.get("GITHUB_OUTPUT"),
    )
    args = parser.parse_args()

    changed = git_changed_zips(args.before, args.after)
    selected = newest_by_git_or_mtime(changed or all_repository_zips())
    write_output(selected, args.github_output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
