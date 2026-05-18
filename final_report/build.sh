#!/bin/bash

# Build script for LaTeX project
# This script compiles the LaTeX document with bibliography support

set -e  # Exit on error

echo "Building LaTeX document..."

# Check if main.tex exists
if [ ! -f "main.tex" ]; then
    echo "Error: main.tex not found!"
    exit 1
fi

# Try to use latexmk if available (recommended)
if command -v latexmk &> /dev/null; then
    echo "Using latexmk..."
    latexmk -pdf -interaction=nonstopmode main.tex
    echo "Build complete! Output: main.pdf"
else
    echo "latexmk not found, using pdflatex directly..."
    echo "Running pdflatex (pass 1/3)..."
    pdflatex -interaction=nonstopmode main.tex
    
    if [ -f "bibs/sample.bib" ]; then
        echo "Running bibtex..."
        bibtex main
    fi
    
    echo "Running pdflatex (pass 2/3)..."
    pdflatex -interaction=nonstopmode main.tex
    
    echo "Running pdflatex (pass 3/3)..."
    pdflatex -interaction=nonstopmode main.tex
    
    echo "Build complete! Output: main.pdf"
fi

# Open the PDF if it exists
if [ -f "main.pdf" ]; then
    echo "Opening main.pdf..."
    open main.pdf
else
    echo "Warning: main.pdf not found after build!"
fi
