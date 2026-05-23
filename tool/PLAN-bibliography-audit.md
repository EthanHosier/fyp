# Plan: bibliography audit

## Context

`final_report/bibs/sample.bib` contains 87 entries; 71 are actively cited across 84 citation sites in the .tex files. The Communication-band rubric requires that references are clean — but the more important risk is **claim mis-citation**: a `\cite{}` that supports a sentence the cited paper does not actually say. The audit task is therefore not just metadata hygiene; for each cited entry, verify (i) the metadata is complete and consistent, and (ii) every place the entry is cited makes a claim the source actually supports.

The intended outcome is a per-entry verification log (`tool/PLAN-bibliography-audit-log.md`) with one section per cited citekey, each carrying a fixed checklist. The user works through entries one by one in subsequent sessions; this plan defines the checklist and the log document's shape so that work is a fill-in-the-blanks exercise rather than re-planning each entry.

## Inventory (from survey)

- **87 total entries**, **71 cited** (84 citation sites), **16 uncited orphans**.
- **2 stub entries** with missing required fields: `wang2018traceability` (empty title + journal), `bouzenia2025trajectories` (missing journal).
- **13 entries** carry in-bib TODO comments flagging incomplete metadata.
- **No undefined citations** — every `\cite{}` resolves to a bib entry.
- **Possible duplicate**: `kim2011api` vs `kim2011apilevel` (both ICSE 2011).
- **Possibly superseded**: `falleri2014gumtree` (superseded by `falleri2024gumtree`), `tsantalis2018/2020refactoringminer` (superseded by `alikhanifard2025rminer`).

## Per-entry checklist (the unit of work)

Each cited entry gets the following six checks. Mark each pass / fail / N/A.

1. **Metadata complete for entry type.** All required BibTeX fields for the entry type are present and well-formed:
   - `article`: author, title, journal, year, volume/number/pages where known.
   - `inproceedings`: author, title, booktitle, year, pages where known.
   - `book`: author/editor, title, publisher, year.
   - `phdthesis`: author, title, school, year.
   - `misc`: author or organisation, title, year, howpublished/url.
2. **Metadata accurate against the canonical source.** Title, author list, year, venue match the published version (cross-checked against DOI, publisher page, or the canonical PDF). Common drift: pre-print author orderings, "to appear" → finalised year, conference vs journal version conflated.
3. **Source obtainable.** A working DOI, conference-proceedings URL, arXiv/eprint, or downloaded PDF exists. If the paper cannot be obtained at all, the entry cannot be used to substantiate a load-bearing claim and must be either replaced or downgraded to a "background mention" framing.
4. **Every citation site enumerated.** All `\cite{citekey}` (and `\citep`, `\citet`, multi-key cite) sites for this entry are listed, with the surrounding sentence quoted verbatim so the supported claim is visible.
5. **Each citation site's claim is supported by the source.** For each cite site, identify the specific section / figure / claim in the paper that backs the sentence in the thesis. Three outcomes:
   - **Supported.** Paper says what the thesis says it says. Note the section / page.
   - **Loosely supported.** Paper is a reasonable general reference for the area, but does not state the specific claim verbatim. Acceptable for "as surveyed in \cite{...}" framings; not acceptable when the citation is the only support for a specific number, definition, or finding.
   - **Unsupported.** Paper does not say what the thesis claims it says. Either rewrite the thesis sentence to match what the paper does say, find a different source, or drop the citation.
6. **No internal contradiction across cite sites.** If the entry is cited in more than one place, the claims attributed to it are mutually consistent — the paper is not being used to support contradicting statements.

Notes on scope of check 5: this is the load-bearing check and is the one that requires actually reading the source. For high-citation-density entries (e.g. `fowler1999refactoring`, `murphyhill2009howrefactor`, `gautam2025refactorbench`), expect each citation site to need a separate paragraph or page in the source identified.

## Document format

Single file: `tool/PLAN-bibliography-audit-log.md`. Header carries shared status counters, then one block per cited citekey, then a "Disposition" block for uncited orphans.

### Per-entry block template

```markdown
### `citekey`

- **Type / title:** {article|inproceedings|...} — "{title}" ({year})
- **Authors:** {author list as it appears in .bib}
- **Venue:** {journal / booktitle / publisher}
- **DOI / URL:** {DOI or link, "—" if none}
- **Local PDF:** {path under tool/papers/ if downloaded, "—" if not needed}

**Checklist:**

- [ ] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source
- [ ] 3. Source obtainable (DOI / arXiv / local PDF)
- [ ] 4. All cite sites enumerated below
- [ ] 5. Each cite site's claim is supported by the source
- [ ] 6. No internal contradiction across cite sites

**Cite sites:**

1. `chapter/file.tex:LINE` — claim: "{verbatim sentence containing the cite}"
   - Source support: {Supported §X.Y, p. Z | Loosely supported — general reference | Unsupported — see notes}
2. ...

**Notes / fixes required:** {free text — rewrite needed, replacement source, metadata fix, etc.}
```

### Header (top of file)

```markdown
# Bibliography Audit Log

**Entries cited:** 71  
**Entries verified:** 0 / 71  
**Entries with fixes required:** 0  
**Uncited orphans:** 16 (disposition decisions tracked below)

## Working order

Process in roughly this order so the highest-leverage entries are verified first:

1. Load-bearing methodology citations (used to justify score-formula term choices): fowler1999refactoring, fowler2018refactoring, murphyhill2009howrefactor, paixao2017balancing, gautam2025refactorbench, biemankang1995tcc, chidamberkemerer1994ckmetrics, buse2010readability, scalabrino2018readability, campbell2018cognitive, ...
2. Load-bearing background citations (used to position the thesis's gap): mens2004survey, alikhanifard2025rminer, falleri2024gumtree, bouzenia2025trajectories, alomar2025slr, raychev2013resynth, lin2016navigator, harman2007pareto, mohan2019manyobjective.
3. Statistical-method citations: cohen1960kappa, landis1977kappa, kendall1948rank, webber2010rbo, wohlin2012experimentation, lundberg2017shap, agresti2010ordinal.
4. Remaining cited entries.
5. Uncited-orphan disposition.

For each entry, fill in the per-entry block, tick the boxes, and record any required fix.
```

### Uncited-orphan block

```markdown
## Uncited orphans (disposition)

For each orphan: decide **keep** (intentional background reference, leave in bib), **delete** (no longer relevant), or **integrate** (find a place in the thesis that should cite it).

- [ ] `falleri2014gumtree` — superseded by `falleri2024gumtree`. Decision: ___
- [ ] `tsantalis2018refactoringminer` — superseded by `alikhanifard2025rminer`. Decision: ___
- [ ] `tsantalis2020refactoringminer` — superseded by `alikhanifard2025rminer`. Decision: ___
- [ ] `kim2011api` — possible duplicate of `kim2011apilevel`. Decision: ___
- ... (remaining 12)
```

## Pre-fill step (one-time, before per-entry work begins)

A single pass generates `tool/PLAN-bibliography-audit-log.md` populated with:
- One per-entry block per cited citekey, with the `citekey`, type/title/year/authors/venue/DOI lines pre-filled from `bibs/sample.bib`.
- Each block's **Cite sites** subsection pre-filled with every `\cite{citekey}` site found by `grep -rn "\\cite.*citekey" final_report/`, the file:line, and the surrounding sentence quoted from the .tex.
- All checkboxes empty, all "Source support" lines empty, all "Notes" sections empty.

After pre-fill, the user works through entries one at a time. The plan above defines what each entry's verification looks like; the per-entry work is then a fill-in-the-blanks exercise rather than fresh design every time.

## Critical files

- `final_report/bibs/sample.bib` — source of truth for metadata.
- `final_report/**/*.tex` — citation sites; grepped for the cite-site enumeration.
- `tool/PLAN-bibliography-audit-log.md` — **new** log file produced by the pre-fill step; the unit of subsequent work.
- `tool/papers/` — **new** directory for downloaded PDFs cached locally during check 3 / check 5.

## Verification

The audit is complete when:
1. Every cited citekey's per-entry block has all six checkboxes ticked.
2. Every entry flagged as "Unsupported" in check 5 has either had the thesis sentence rewritten, the citation replaced, or the citation dropped — and the .tex / .bib changes have been made.
3. Every entry flagged as needing a metadata fix in check 1 or 2 has had the fix applied to `sample.bib`.
4. Every uncited orphan has a disposition recorded.
5. `./build.sh --light` produces zero `LaTeX Warning: Citation 'foo' undefined` and zero `Package natbib Warning: Citation ... multiply defined`.

## Out of scope (this plan, not the audit work itself)

- Page numbers for general book citations (Fowler et al.) — not required; only add `\cite[p.~XX]{...}` when attributing a specific quote or claim.
- Style harmonisation (Last/First vs First Last across all entries) — cosmetic; can be a separate sweep after substance audit is done.
- Adding new references to fill in gaps the audit might uncover — if a claim turns out to be unsupported, the first fix is to rewrite the claim; finding a new citation is a fallback.
