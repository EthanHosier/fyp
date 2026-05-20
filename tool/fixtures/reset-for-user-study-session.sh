#!/usr/bin/env bash
# Reset the user-study-fixture working tree to baseline, ready for the
# next recording session. Run from anywhere; this script cds into
# fixtures/user-study-fixture/ before touching git.
#
# Wipes every stray edit (including .gradle/, build/, .idea/), then
# discards the previous session's captured trace dir. The IntelliJ
# window stays open — press the "Reload from disk" toolbar button
# (or wait a couple of seconds) for the IDE to pick up the reset.
#
# Bootstrap (run ONCE before the first session):
#   cd fixtures/user-study-fixture
#   git init
#   git add -A && git commit -m "user-study-fixture: baseline"
#   git tag user-study-baseline-v1
#   git branch baseline
#   git checkout -b recording
#
# Then per participant session, from fixtures/:
#   ./reset-for-user-study-session.sh
#   ... participant works in the IDE ...
#   ./end-session.sh <id>            # if reusing the existing pipeline
set -euo pipefail

cd "$(dirname "$0")/user-study-fixture"

# Sanity: are we in the fixture root?
if [ ! -f "settings.gradle.kts" ] || [ ! -d "src/main/java/org/orders" ]; then
  echo "Error: fixtures/user-study-fixture/ doesn't look right." >&2
  exit 1
fi

# Sanity: baseline branch exists?
if ! git rev-parse --verify --quiet baseline >/dev/null; then
  cat >&2 <<'EOF'
Error: 'baseline' branch not found. Bootstrap first:
  cd fixtures/user-study-fixture
  git init
  git add -A && git commit -m "user-study-fixture: baseline"
  git tag user-study-baseline-v1
  git branch baseline
  git checkout -b recording
EOF
  exit 1
fi

# Don't mutate baseline directly — flip to 'recording' if needed.
current="$(git symbolic-ref --short HEAD 2>/dev/null || echo "DETACHED")"
if [ "$current" = "baseline" ]; then
  echo "On 'baseline' branch — switching to 'recording'."
  git checkout -q -B recording baseline
fi

git reset --hard --quiet baseline
git clean -fdxq
rm -rf .refactoring-traces

echo "Reset complete. Reload IntelliJ from disk if it's open."
