#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def reconcile(session_dir: Path) -> int:
    events_file = session_dir / "events.jsonl"
    session_file = session_dir / "session.json"
    if not events_file.is_file():
        print(f"[{session_dir.name}] missing events.jsonl — skip", file=sys.stderr)
        return 1
    if not session_file.is_file():
        print(f"[{session_dir.name}] missing session.json — skip", file=sys.stderr)
        return 1

    events = []
    with events_file.open() as f:
        for i, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError as e:
                print(
                    f"[{session_dir.name}] events.jsonl line {i}: {e}",
                    file=sys.stderr,
                )
                return 1

    with session_file.open() as f:
        session = json.load(f)

    before = len(session.get("events", []))
    after = len(events)
    if before == after:
        # Same count — confirm the IDs match too before declaring a no-op.
        before_ids = [e.get("id") for e in session.get("events", [])]
        after_ids = [e.get("id") for e in events]
        if before_ids == after_ids:
            print(f"[{session_dir.name}] already consistent ({before} events)")
            return 0

    session["events"] = events
    with session_file.open("w") as f:
        json.dump(session, f, indent=4)
    print(f"[{session_dir.name}] session.json events updated ({before} -> {after})")
    return 0


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: reconcile-session-events.py <session-dir> [<session-dir> ...]", file=sys.stderr)
        return 2
    exit_code = 0
    for arg in sys.argv[1:]:
        d = Path(arg)
        if not d.is_dir():
            print(f"[{arg}] not a directory — skip", file=sys.stderr)
            exit_code = 1
            continue
        rc = reconcile(d)
        if rc != 0:
            exit_code = rc
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
