#!/usr/bin/env bash
# Convert presentation/speaker-notes.md to PDF deterministically.
# Re-run from anywhere with: ./presentation/build_speaker_notes.sh
#
# Requires:
#   - pandoc       (brew install pandoc)
#   - xelatex      (MacTeX / BasicTeX — already used by final_report)
set -euo pipefail

cd "$(dirname "$0")"

SRC="speaker-notes.md"
OUT="speaker-notes.pdf"

command -v pandoc  >/dev/null || { echo "error: pandoc not installed (brew install pandoc)" >&2; exit 1; }
command -v xelatex >/dev/null || { echo "error: xelatex not installed (install MacTeX or BasicTeX)" >&2; exit 1; }

# Header: map Unicode chars that Helvetica Neue / Menlo lack to LaTeX math equivalents.
# Keeps the same font setup while preventing missing-glyph warnings.
HEADER="$(mktemp -t speaker-notes-header.XXXXXX.tex)"
trap 'rm -f "$HEADER"' EXIT
cat > "$HEADER" <<'EOF'
\usepackage{newunicodechar}
\newunicodechar{→}{$\rightarrow$}
\newunicodechar{ℓ}{$\ell$}

% Shrink all tables one font size below body text (10pt -> 9pt).
\usepackage{etoolbox}
\AtBeginEnvironment{longtable}{\small}
\AtBeginEnvironment{tabular}{\small}
EOF

pandoc "$SRC" \
  --pdf-engine=xelatex \
  --variable mainfont="Helvetica Neue" \
  --variable monofont="Menlo" \
  --variable geometry:margin=0.7in \
  --variable fontsize=10pt \
  --variable colorlinks=true \
  --variable linkcolor=blue \
  --include-in-header="$HEADER" \
  --output "$OUT"

echo "Wrote: $OUT"
