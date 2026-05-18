#!/bin/bash

# Build script for LaTeX project
# This script compiles the LaTeX document with bibliography support.
# By default builds both main.pdf (light) and main_dark.pdf (dark mode).
# Pass --light or --dark to build only one variant.

set -e  # Exit on error

MODE="both"
if [ "$1" = "--light" ]; then
    MODE="light"
elif [ "$1" = "--dark" ]; then
    MODE="dark"
fi

build_target() {
    local target="$1"
    local label="$2"

    if [ ! -f "${target}.tex" ]; then
        echo "Error: ${target}.tex not found!"
        exit 1
    fi

    echo "=== Building ${label} (${target}.pdf) ==="

    if command -v latexmk &> /dev/null; then
        latexmk -pdf -interaction=nonstopmode "${target}.tex"
    else
        pdflatex -interaction=nonstopmode "${target}.tex"
        if [ -f "bibs/sample.bib" ]; then
            bibtex "${target}"
        fi
        pdflatex -interaction=nonstopmode "${target}.tex"
        pdflatex -interaction=nonstopmode "${target}.tex"
    fi

    if [ -f "${target}.pdf" ]; then
        echo "${label} build complete: ${target}.pdf"
    else
        echo "Warning: ${target}.pdf not found after build!"
    fi
}

if [ "$MODE" = "both" ] || [ "$MODE" = "light" ]; then
    build_target "main" "light mode"
fi

if [ "$MODE" = "both" ] || [ "$MODE" = "dark" ]; then
    build_target "main_dark" "dark mode"
fi

# Open the requested PDF (default to main_dark.pdf in 'both' mode)
if [ "$MODE" = "light" ] && [ -f "main.pdf" ]; then
    echo "Opening main.pdf..."
    open main.pdf
elif [ "$MODE" = "dark" ] && [ -f "main_dark.pdf" ]; then
    echo "Opening main_dark.pdf..."
    open main_dark.pdf
elif [ "$MODE" = "both" ] && [ -f "main_dark.pdf" ]; then
    echo "Opening main_dark.pdf (use \`open main.pdf\` for the light variant)..."
    open main_dark.pdf
fi
