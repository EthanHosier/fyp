#!/usr/bin/env python3
import difflib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

DIFF_TYPES = ("EDIT_BURST", "REFACTORING_FINISHED")


def relpath(abs_path: str) -> str:
    if "/src/" in abs_path:
        return "src/" + abs_path.split("/src/", 1)[1]
    return Path(abs_path).name


def load_initial_src(initial_src: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not initial_src.is_dir():
        return out
    for p in initial_src.rglob("*"):
        if not p.is_file():
            continue
        rel = str(p.relative_to(initial_src))
        try:
            out[rel] = p.read_text()
        except UnicodeDecodeError:
            continue
    return out


def fmt_ts(ms: int | None) -> str:
    if ms is None:
        return "?"
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).strftime("%H:%M:%S")


def payload_summary(ev: dict) -> str:
    p = ev.get("payload") or {}
    t = ev["type"]
    if t == "REFACTORING_FINISHED":
        bits = []
        if "refactoringId" in p:
            bits.append(f"refactoringId={p['refactoringId']}")
        if "outcome" in p:
            bits.append(f"outcome={p['outcome']}")
        return " ".join(bits)
    if t == "EDIT_BURST":
        bits = []
        for k in ("charsInserted", "charsDeleted", "fileCount"):
            if k in p:
                bits.append(f"{k}={p[k]}")
        return " ".join(bits)
    return ""


def diff_block(rel: str, before: str, after: str) -> str:
    lines = list(
        difflib.unified_diff(
            before.splitlines(keepends=True),
            after.splitlines(keepends=True),
            fromfile=f"a/{rel}",
            tofile=f"b/{rel}",
            n=3,
        )
    )
    if not lines:
        return f"--- a/{rel}\n+++ b/{rel}\n(no textual change)\n"
    # Ensure trailing newline on the last line so terminal output is clean.
    if not lines[-1].endswith("\n"):
        lines[-1] += "\n"
    return "".join(lines)


def main() -> None:
    args = sys.argv[1:]
    only: str | None = None
    positional: list[str] = []
    i = 0
    while i < len(args):
        if args[i] == "--only" and i + 1 < len(args):
            only = args[i + 1]
            i += 2
        else:
            positional.append(args[i])
            i += 1

    if not positional:
        print(__doc__, file=sys.stderr)
        sys.exit(2)

    session_dir = Path(positional[0]).resolve()
    events_path = session_dir / "events.jsonl"
    if not events_path.is_file():
        print(f"error: {events_path} not found", file=sys.stderr)
        sys.exit(2)

    prev_contents = load_initial_src(session_dir / "initial-src")

    with events_path.open() as f:
        for idx, line in enumerate(f):
            line = line.strip()
            if not line:
                continue
            ev = json.loads(line)
            etype = ev.get("type")
            if etype not in DIFF_TYPES:
                continue
            if only and etype != only:
                continue

            changed = ev.get("changedFiles") or []
            header = (
                f"=== event {idx} [{fmt_ts(ev.get('timestamp'))}] {etype} "
                f"({payload_summary(ev)}) ==="
            )
            print(header)

            if not changed:
                print("(no changedFiles)\n")
                continue

            for cf in changed:
                rel = relpath(cf["path"])
                after = cf.get("contents") or ""
                change_type = cf.get("changeType")
                prev_path_abs = cf.get("previousPath")

                if prev_path_abs:
                    prev_rel = relpath(prev_path_abs)
                    print(f"--- rename: a/{prev_rel} -> b/{rel} ---")
                    before = prev_contents.pop(prev_rel, "")
                elif change_type == "ADDED":
                    before = ""
                elif change_type == "DELETED":
                    before = prev_contents.pop(rel, "")
                    after = ""
                else:
                    before = prev_contents.get(rel, "")

                print(diff_block(rel, before, after))
                if change_type != "DELETED":
                    prev_contents[rel] = after

            print()


if __name__ == "__main__":
    main()
