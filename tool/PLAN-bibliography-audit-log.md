# Bibliography Audit Log

**Entries cited:** 67 (1 of the 67 — `croux2010influence` — turns out to be an uncited orphan in the .tex; should be moved to orphan list)  
**Entries verified:** 67 / 67  
**Entries with fixes required:** 36 (paixao2017balancing, biemankang1995tcc, scalabrino2018readability 🚨, mens2004survey, alikhanifard2025rminer, bouzenia2025trajectories 🚨, alomar2025slr 🚨, falleri2024gumtree minor, lin2016navigator 🚨, harman2007pareto 🚨🚨, mohan2019manyobjective, silva2017refdiff, rolim2017refazer 🚨, alizadeh2020interactive, whydevelopersrefactor2020 🚨, metricsmotivation2024 🚨, pan2025swegym 🚨, nikolaidis2024metricsbased, paiva2017evaluation, arcellifontana2016falsepositives, damevski2017usagesmells 🚨, foutsekh2018interactiontraces 🚨🚨, bansiya2002qmood 🚨, chen2024morcora, coleman1994maintainability, cortellessa2023manyobjective, eilertsen2024refactorings, heitlager2007sig, khrishe2016empirical, kim2011apilevel, liu2009smell, marinescu2004, mens2007graph, moha2010decor, poncin2011processmining 🚨, wagner2015quamoco, wang2018traceability 🚨🚨)  
**Uncited orphans:** 16

**Audit-finishing summary (final pass, 18 entries verified):** All entries marked 🚨 / 🚨🚨 need thesis-side rewrites or load-bearing replacements:
- **🚨🚨 wang2018traceability**: stub `.bib`; correct paper is Nyamawe et al. 2018 IEEE Access. Replace entry; consider renaming citekey.
- **🚨 bansiya2002qmood**: two thesis sentences (threats.tex:31, methodology.tex:147) claim QMOOD weights were "regression-fitted" or "tuned against expert assessments". Wrong — QMOOD weights are set theoretically and *validated* via rank-correlation against 13 experts. Rewrite both sentences.
- **🚨 poncin2011processmining**: canonical title is "Process Mining Software *Repositories*" (not "...Development Workflows"); paper centres on VCS/bug-tracker/mail repositories, not Eclipse-plugin IDE event logs. The background.tex:221 "Eclipse plugins" framing should be reworded.
- Remaining 15 newly-flagged entries: metadata fixes (missing pages/volume/DOI) and a few author-name corrections (chen2024morcora `Lerina→Lei`, eilertsen2024refactorings add Murphy, kim2011apilevel `Danny→Dongxiang`, moha2010decor expand "and others", wagner2015quamoco verify `Mäder/Saft → Mayr/Seidl`).

## Working order

Process in this priority order:

1. **Load-bearing methodology citations** (used to justify score-formula term choices): fowler1999refactoring, fowler2018refactoring, murphyhill2009howrefactor, paixao2017balancing, gautam2025refactorbench, biemankang1995tcc, chidamberkemerer1994ckmetrics, buse2010readability, scalabrino2018readability, campbell2018cognitive, cedrim2017refactoring, negara2013manualautomated, vakilian2012usedisuse, dibiase2019decomposition, taokim2015partitioning, kudrjavets2022small, herzig2013tangled, munozbaron2020cognitive, lavazza2023cognitive.
2. **Load-bearing background citations** (used to position the thesis's gap): mens2004survey, alikhanifard2025rminer, falleri2024gumtree, bouzenia2025trajectories, alomar2025slr, raychev2013resynth, lin2016navigator, harman2007pareto, mohan2019manyobjective, opdyke1992refactoring, silva2017refdiff, rolim2017refazer, alizadeh2020interactive.
3. **Statistical-method citations**: cohen1960kappa, landis1977kappa, kendall1948rank, webber2010rbo, wohlin2012experimentation, lundberg2017shap, agresti2010ordinal, croux2010influence, saisana2005composite, strumbelj2014explaining.
4. **Remaining cited entries** in any order.
5. **Uncited-orphan disposition** (at bottom of file).

---

## 1. Load-bearing methodology citations

### `fowler1999refactoring`

- **Type / title:** book — "Refactoring: Improving the Design of Existing Code" (1999)
- **Authors:** Fowler, Martin
- **Venue:** Addison-Wesley
- **DOI / URL:** https://martinfowler.com/books/refactoring.html
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:10` — claim: "Most definitions emphasise that refactorings are applied as small, semantics-preserving steps and that safety is ensured through maintaining preconditions and/or validation (e.g. testing) \cite{opdyke1992refactoring,fowler1999refactoring,fowler2018refactoring}."
   - Source support: Supported — Fowler describes refactoring as "a series of small behavior-preserving transformations".
2. `background/background.tex:10` — claim: "Fowler's catalogue helped popularise refactoring as an incremental activity performed by composing many small operations, grounded in developer-facing mechanics and motivations \cite{fowler1999refactoring,fowler2018refactoring}."
   - Source support: Supported — the book is widely credited with popularising the practice; consistent with the author's own framing.
3. `background/background.tex:15` — claim: "Research on refactoring often defines a set of \emph{operators} (also described as atomic refactorings) such as \textsc{Extract Method}, \textsc{Move Method}, or \textsc{Rename} that can be composed together into larger transformations \cite{fowler1999refactoring,mens2004survey}."
   - Source support: Supported — these are canonical refactorings catalogued in the book (~70 entries).
4. `background/background.tex:15` — claim: "The notion of \emph{code smells} is also closely related: these are heuristic indicators that suggest potential design problems and thus potential refactoring opportunities \cite{fowler1999refactoring,fowler2018refactoring} in codebases."
   - Source support: Supported — code smells are a dedicated chapter in the book.
5. `background/background.tex:20` — claim: "To explicitly represent refactoring as a process, this thesis adopts a state/action/trace framing aligned with the view of refactoring as sequential operator application \cite{fowler1999refactoring,mens2004survey}."
   - Source support: Loosely supported — Fowler describes incremental application but does not formalise state/action/trace; thesis uses Fowler as one of several anchors alongside mens2004survey.
6. `background/background.tex:51` — claim: "Code smells are a common way to link between qualitative design concerns and measurable indicators \cite{fowler1999refactoring}."
   - Source support: Loosely supported — Fowler introduces smells as qualitative heuristics; "measurable indicators" framing is from later metrics work, but Fowler is the reasonable origin citation.
7. `background/background.tex:129` — claim: "This is useful because it matches the operator-based view of refactoring used in catalogues and surveys \cite{fowler1999refactoring,mens2004survey}."
   - Source support: Supported — direct fit to the book's catalogue structure.
8. `background/background.tex:235` — claim: "First, refactoring is well-defined as behaviour-preserving and supported by catalogues of operators and by tool-based precondition checking \cite{opdyke1992refactoring,fowler1999refactoring,mens2004survey}."
   - Source support: Supported.
9. `introduction/introduction.tex:5` — claim: "Traditional definitions emphasise this preservation of behaviour and describe refactoring as a sequence of small, semantics-preserving transformations \cite{opdyke1992refactoring,fowler1999refactoring,fowler2018refactoring}, and subsequent surveys categorise these refactoring operations and the tools that support them \cite{mens2004survey}."
   - Source support: Supported.
10. `methodology/methodology.tex:133` — claim: "Duplication           & 1.0 & Heitlager et al. 2007~\cite{heitlager2007sig}; Fowler 1999~\cite{fowler1999refactoring} & CPD-detected cloned lines on touched files \\"
    - Source support: Supported — "Duplicated Code" is the first code smell listed in Fowler's smell catalogue.

**Notes / fixes required:** None. Optionally add `address={Boston, MA}` and ISBN to .bib; not required.

---

### `fowler2018refactoring`

- **Type / title:** book — "Refactoring: Improving the Design of Existing Code" (2018)
- **Authors:** Fowler, Martin
- **Venue:** Addison-Wesley (2nd edition)
- **DOI / URL:** https://martinfowler.com/books/refactoring.html
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:10` — claim: "Most definitions emphasise that refactorings are applied as small, semantics-preserving steps and that safety is ensured through maintaining preconditions and/or validation (e.g. testing) \cite{opdyke1992refactoring,fowler1999refactoring,fowler2018refactoring}."
   - Source support: Supported — 2nd ed maintains and elaborates the small-step framing.
2. `background/background.tex:10` — claim: "Fowler's catalogue helped popularise refactoring as an incremental activity performed by composing many small operations, grounded in developer-facing mechanics and motivations \cite{fowler1999refactoring,fowler2018refactoring}."
   - Source support: Supported.
3. `background/background.tex:15` — claim: "The notion of \emph{code smells} is also closely related: these are heuristic indicators that suggest potential design problems and thus potential refactoring opportunities \cite{fowler1999refactoring,fowler2018refactoring} in codebases."
   - Source support: Supported — 2nd ed retains the smells chapter.
4. `introduction/introduction.tex:5` — claim: "Traditional definitions emphasise this preservation of behaviour and describe refactoring as a sequence of small, semantics-preserving transformations \cite{opdyke1992refactoring,fowler1999refactoring,fowler2018refactoring}."
   - Source support: Supported.

**Notes / fixes required:** None.

---

### `murphyhill2009howrefactor`

- **Type / title:** inproceedings — "How We Refactor, and How We Know It" (2009)
- **Authors:** Murphy-Hill, Emerson R. and Parnin, Chris and Black, Andrew P.
- **Venue:** Proceedings of the 31st International Conference on Software Engineering (ICSE)
- **DOI / URL:** 10.1109/ICSE.2009.5070529
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `threats/threats.tex:55` — claim: "Both come from Murphy-Hill et al.'s 2009 study of how developers actually refactor~\cite{murphyhill2009howrefactor}, and neither is sensitivity-tested directly."
   - Source support: Supported — meta-reference to the paper as the source of both the skip-tests finding and the 60-s batch heuristic.
2. `methodology/methodology.tex:89` — claim: "$W_{\textsc{st}}$ (skip-tests) & 14 & Murphy-Hill et al. 2009~\cite{murphyhill2009howrefactor} \\"
   - Source support: Loosely supported — the skip-tests behaviour is established by the paper; the specific weight value 14 is a thesis calibration, not attributed to the paper. The citation is appropriate as the source of the underlying finding.
3. `methodology/methodology.tex:108` — claim: "The 60-second cut-off comes from Murphy-Hill et al.~\cite{murphyhill2009howrefactor}: their study of how developers actually refactor shows that tests are run per batch of changes, not after every single step."
   - Source support: Supported — the 60-s batch heuristic is defined verbatim in the paper ("refactorings of the same kind that execute within 60 seconds of each other form a batch"); batch-level test-running follows from the floss-refactoring findings.
4. `methodology/methodology.tex:173` — claim: "and the $60\,\mathrm{s}$ composite-window boundary~\cite{murphyhill2009howrefactor} defines the refactoring batch boundary used by both ORDERING and HYGIENE."
   - Source support: Supported — same 60-s heuristic.
5. `methodology/methodology.tex:193` — claim: "TESTS\_SKIPPED fires once per composite window (60-second batch~\cite{murphyhill2009howrefactor}) that contains at least one refactoring step and no successful test run in between."
   - Source support: Supported — direct use of the 60-s batching boundary; skip-tests behaviour is part of the paper's findings.
6. `introduction/introduction.tex:5` — claim: "At the same time, refactoring can be costly: it consumes developer time, introduces risk, and is frequently intertwined with other work such as feature development and bug fixing rather than occurring as isolated ``refactoring-only'' tasks \cite{murphyhill2009howrefactor,kim2011apilevel}."
   - Source support: Supported — paper confirms "programmers frequently intersperse refactoring with other program changes".
7. `introduction/introduction.tex:11` — claim: "Meanwhile, research on process realism suggests that refactoring is often ``flossed'' into ongoing development instead of being a separate dedicated task, which increases the chance of mixed-intent steps and noisy traces \cite{murphyhill2009howrefactor}."
   - Source support: Supported — "floss refactoring" is Murphy-Hill & Black's coined term.

**Notes / fixes required:** None. Optionally add `doi={10.1109/ICSE.2009.5070529}` and `publisher={IEEE}` to .bib for completeness.

---

### `paixao2017balancing`

- **Type / title:** article — "An Empirical Study of Cohesion and Coupling: Balancing Optimisation and Disruption" (2017)
- **Authors:** Paix\~ao, Matheus and others
- **Venue:** IEEE Transactions on Evolutionary Computation
- **DOI / URL:** 10.1109/TEVC.2017.2691281
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing co-authors, volume, issue, pages, DOI; see Notes)
- [ ] 2. Metadata accurate against canonical source — FAIL (author list elided as "and others"; full list is Paixão, Harman, Zhang, Yu)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:43` — claim: "Empirical work on modularity improvement highlights this trade-off between optimisation and disruption, showing that metric gains can come with substantial structural disturbance \cite{paixao2017balancing}."
   - Source support: Supported — central thesis of the paper.
2. `background/background.tex:62` — claim: "\textbf{Differentiate endpoint improvement from trajectory cost.} Outcome-based improvements remain valuable, but disruption and intermediate degradation can be significant even when the final snapshot improves \cite{paixao2017balancing}."
   - Source support: Supported — paper shows "optimising the structure is highly disruptive (on average more than 57% of the structure must change)".
3. `background/background.tex:169` — claim: "Empirical studies of modularity optimisation show that maximising cohesion/coupling improvements can cause substantial disruption compared to the original modular structure \cite{paixao2017balancing}."
   - Source support: Supported — same ~57% disruption finding.
4. `methodology/methodology.tex:87` — claim: "$W_{\textsc{g}}$ (gain)        & 50 & Cedrim et al. 2017~\cite{cedrim2017refactoring}; Paix{\~a}o et al. 2017~\cite{paixao2017balancing} \\"
   - Source support: Loosely supported — paper grounds magnitude of available cohesion/coupling improvement (~25% on average); specific weight value 50 is a thesis calibration.
5. `methodology/methodology.tex:88` — claim: "$W_{\textsc{b}}$ (broken)      & 28 & Paix{\~a}o et al. 2017~\cite{paixao2017balancing}; Gautam et al. 2025~\cite{gautam2025refactorbench} (FM \#2) \\"
   - Source support: Loosely supported — disruption findings support a broken/disruption penalty existing; specific weight 28 is the thesis's.
6. `methodology/methodology.tex:102` — claim: "Paix{\~a}o et al.~\cite{paixao2017balancing} model the trade-off between disruption and improvement as something developers actively manage, which sets the size of this positive term in relation to the penalty terms below."
   - Source support: Supported — paper explicitly shows developers respect cohesion/coupling fitness functions yet avoid disruption, and introduces a multi-objective approach modelling this trade-off.
7. `methodology/methodology.tex:105` — claim: "Paix{\~a}o et al.~\cite{paixao2017balancing} show that developers care a lot about avoiding disruptive intermediate states - they are willing to give up roughly a quarter of the available metric improvement to avoid them."
   - Source support: Loosely supported — paper reports developers leave ~25% improvement on the table to avoid the ~57% disruption pure optimisation would require. The "roughly a quarter" gloss is a defensible paraphrase, not a verbatim claim.

**Notes / fixes required:** 
- Expand author list in `.bib`: `author = {Paix{\~a}o, Matheus and Harman, Mark and Zhang, Yuanyuan and Yu, Yijun}`.
- Add `volume = {22}`, `number = {3}`, `pages = {394--414}`, `doi = {10.1109/TEVC.2017.2691281}`.
- Year 2017 (early access) or 2018 (formal publication) — keep 2017 unless preferring formal pub year.
- Site 7 framing is a paraphrase; defensible but worth noting.

---

### `gautam2025refactorbench`

- **Type / title:** inproceedings — "RefactorBench: Evaluating Stateful Reasoning in Language Agents Through Code" (2025)
- **Authors:** Gautam, Dhruv and Garg, Spandan and Jang, Jinu and Sundaresan, Neel and Zilouchian Moghaddam, Roshanak
- **Venue:** Proceedings of the International Conference on Learning Representations (ICLR)
- **DOI / URL:** arXiv:2503.07832
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:28` — claim: "This state/action/trace framing is consistent with recent software-engineering work that models agent and developer behaviour as a partially-observable Markov decision process, in which a trajectory is a sequence of (action, observation) pairs \cite{gautam2025refactorbench,bouzenia2025trajectories}."
   - Source support: Supported — paper formalises trajectories as τ_n = (a_1, ω_1, …, a_n, ω_n) under POMDP framing.
2. `background/background.tex:210` — claim: "Recent work has begun to formalise software-engineering agent behaviour as a trajectory of (action, observation) pairs in a POMDP-shaped environment \cite{gautam2025refactorbench,bouzenia2025trajectories,pan2025swegym}."
   - Source support: Supported — same as site 1.
3. `methodology/methodology.tex:88` — claim: "$W_{\textsc{b}}$ (broken)      & 28 & Paix{\~a}o et al. 2017~\cite{paixao2017balancing}; Gautam et al. 2025~\cite{gautam2025refactorbench} (FM \#2) \\"
   - Source support: Supported — FM #2 is "Agents fail due to interactions that necessitate erroneous intermediate states"; the specific weight 28 is a thesis calibration.
4. `methodology/methodology.tex:105` — claim: "The time-weighting choice also lines up with Gautam et al.'s 2025 RefactorBench analysis~\cite{gautam2025refactorbench}: their failure-mode~\#2 shows that strictly rejecting any intermediate broken state hurts LM-agent performance, because some broken intermediates are genuinely necessary for multi-file edits."
   - Source support: Supported (FM #2) — paper says "automatically enforcing strict linting rules and rejecting edits based on errors proves to be an impractical approach" and "making a temporary change that introduces errors becomes necessary to complete the task".
5. `introduction/introduction.tex:13` — claim: "Concurrent work has begun to analyse refactoring trajectories under a state/action/observation formalism, qualitatively classifying failure modes of language-model agents on multi-file refactoring tasks \cite{gautam2025refactorbench} and characterising recurring action patterns in software-engineering agent traces \cite{bouzenia2025trajectories}."
   - Source support: Supported — both claims (trajectory formalism, qualitative failure-mode taxonomy) match the paper.

**Notes / fixes required:** 

---

### `biemankang1995tcc`

- **Type / title:** article — "Cohesion and Reuse in an Object-Oriented System" (1995)
- **Authors:** Bieman, James M. and Kang, Byung-Kyoo
- **Venue:** ACM SIGSOFT Software Engineering Notes (vol. 20, SI; SSR'95 proceedings)
- **DOI / URL:** 10.1145/223427.211856
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing volume, pages, DOI; venue ambiguity)
- [ ] 2. Metadata accurate against canonical source — FAIL (venue is SSR'95 / SIGSOFT SEN vol. 20 SI, pp. 259--262)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:136` — claim: "Cohesion (TCC)        & 1.0 & Bieman \& Kang 1995~\cite{biemankang1995tcc} & Per-class mean, dropped if $< 2$ methods \\"
   - Source support: Supported — paper defines TCC as the ratio of public-method pairs sharing at least one common attribute. Canonical Bieman & Kang 1995 attribution for TCC.

**Notes / fixes required:**
- Add `doi = {10.1145/223427.211856}`.
- Either (a) switch to `@inproceedings` with `booktitle = {Proceedings of the 1995 Symposium on Software Reusability (SSR'95)}`, `pages = {259--262}`, `publisher = {ACM Press}`, or (b) keep `@article` and add `volume = {20}`, `number = {SI}`, `pages = {259--262}`.
- Remove the TODO comment in `.bib`.

---

### `chidamberkemerer1994ckmetrics`

- **Type / title:** article — "A Metrics Suite for Object Oriented Design" (1994)
- **Authors:** Chidamber, Shyam R. and Kemerer, Chris F.
- **Venue:** IEEE Transactions on Software Engineering
- **DOI / URL:** 10.1109/32.295895
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:132` — claim: "Coupling (CBO)        & 1.0 & Chidamber \& Kemerer 1994~\cite{chidamberkemerer1994ckmetrics} & CK library, per-class mean \\"
   - Source support: Supported — paper defines CBO ("Coupling Between Object classes") as a count of the classes a given class is coupled to. Canonical Chidamber & Kemerer 1994 attribution.

**Notes / fixes required:** None.

---

### `buse2010readability`

- **Type / title:** article — "Learning a Metric for Code Readability" (2010)
- **Authors:** Buse, Raymond P. L. and Weimer, Westley R.
- **Venue:** IEEE Transactions on Software Engineering
- **DOI / URL:** 10.1109/TSE.2009.70
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:134` — claim: "Readability           & 1.0 & Buse \& Weimer 2010~\cite{buse2010readability} (feature taxonomy only - \emph{not} the trained model) & Five-feature blend, line-weighted \\"
   - Source support: Supported — Buse & Weimer 2010 is the original feature taxonomy; the disclaimer that the trained model is not used is consistent with the paper.
2. `methodology/methodology.tex:152` — claim: "\emph{Readability.} I use Buse \& Weimer's feature taxonomy~\cite{buse2010readability} (line length, indentation, identifier characteristics) as a feature blend, but \emph{not} their trained readability model."
   - Source support: Supported — line length, indentation, identifier characteristics are all in the paper's per-line feature set.

**Notes / fixes required:** None.

---

### `scalabrino2018readability`

- **Type / title:** article — "A Comprehensive Model for Code Readability" (2018)
- **Authors:** Scalabrino, Simone and others
- **Venue:** Journal of Software: Evolution and Process (JSEP), vol. 30, no. 6, e1958
- **DOI / URL:** 10.1002/smr.1958
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing journal correction, vol/issue/article-id, DOI; author list elided)
- [ ] 2. Metadata accurate against canonical source — **FAIL: WRONG JOURNAL** (`.bib` says "Journal of Systems and Software"; canonical venue is "Journal of Software: Evolution and Process" / JSEP, vol. 30, no. 6, article e1958, DOI 10.1002/smr.1958)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:56` — claim: "For example, readability models that combine structural features (e.g. line structure) with textual features (such as  identifier patterns) tend to align better with human judgements of readability \cite{scalabrino2018readability}."
   - Source support: Supported — central contribution of the paper; the combined model statistically improves over Buse & Weimer (structural-only) and Posnett et al.
2. `background/background.tex:90` — claim: "Readability predictors \cite{scalabrino2018readability} \\"
   - Source support: Loosely supported — single phrase in a table row; framing-level citation.
3. `background/background.tex:235` — claim: "These signals are useful, but they are imperfect and can disagree across tools, which motivates combining multiple signals and metrics \cite{paiva2017evaluation,arcellifontana2016falsepositives,scalabrino2018readability}."
   - Source support: Loosely supported — paper supports the "combine multiple signals" half of the claim via demonstrating value of combining structural + textual readability features. Acceptable as a co-cite.

**Notes / fixes required (HIGH PRIORITY):**
- **Change `journal` from "Journal of Systems and Software" to "Journal of Software: Evolution and Process".**
- Add `volume = {30}`, `number = {6}`, `pages = {e1958}`, `doi = {10.1002/smr.1958}`, `publisher = {Wiley}`.
- Expand author list: `author = {Scalabrino, Simone and Linares-V{\'a}squez, Mario and Oliveto, Rocco and Poshyvanyk, Denys}`.
- Remove TODO comment.

---

### `campbell2018cognitive`

- **Type / title:** misc — "Cognitive Complexity: A New Way of Measuring Understandability" (2018)
- **Authors:** Campbell, G. Ann
- **Venue:** SonarSource (white paper, not peer-reviewed)
- **DOI / URL:** —
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:131` — claim: "Cognitive complexity & 1.0 & Campbell 2018~\cite{campbell2018cognitive}; Mu{\~n}oz Bar{\'o}n et al. 2020~\cite{munozbaron2020cognitive} (partial validation); Lavazza et al. 2023~\cite{lavazza2023cognitive} (contradicting) & Per-method mean \\"
   - Source support: Supported — Campbell 2018 is the canonical reference for cognitive complexity; the partial-validation / contradiction framing matches Muñoz Barón 2020 and Lavazza 2023 accurately.

**Notes / fixes required:** Optional: add `howpublished = {SonarSource white paper}`, `url = {https://www.sonarsource.com/resources/cognitive-complexity/}` for retrievability.

---

### `cedrim2017refactoring`

- **Type / title:** inproceedings — "Understanding the Impact of Refactoring on Smells: A Longitudinal Study of 23 Software Projects" (2017)
- **Authors:** Cedrim, Diego and Garcia, Alessandro and Mongiovi, Melina and Gheyi, Rohit and Sousa, Leonardo and de Mello, Rafael and Fonseca, Baldoino and Ribeiro, M\'arcio and Ch\'avez, Antonio
- **Venue:** Proceedings of the 11th Joint Meeting on Foundations of Software Engineering (ESEC/FSE)
- **DOI / URL:** 10.1145/3106237.3106259
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:87` — claim: "$W_{\textsc{g}}$ (gain)        & 50 & Cedrim et al. 2017~\cite{cedrim2017refactoring}; Paix{\~a}o et al. 2017~\cite{paixao2017balancing} \\"
   - Source support: Loosely supported — paper grounds the magnitude of structural-improvement opportunity (only 9.7% of refactorings remove smells; 57% leave smells unchanged); the specific weight 50 is a thesis calibration.
2. `methodology/methodology.tex:102` — claim: "Cedrim et al.~\cite{cedrim2017refactoring} found that refactoring often makes structural metrics no better or even slightly worse, which means the score should reward improvement explicitly rather than assume it."
   - Source support: Supported — paper reports "57% did not reduce [smell] occurrences" and "33.3% induced the introduction of new ones" (only 9.7% removed smells), and that ">95% of refactoring-induced smells were not removed in successive commits".

**Notes / fixes required:** Optional: expand "A. Ch\'avez" → "Alexander Ch\'avez" for ACM-canonical consistency.

---

### `negara2013manualautomated`

- **Type / title:** inproceedings — "A Comparative Study of Manual and Automated Refactorings" (2013)
- **Authors:** Negara, Stas and Chen, Nicholas and Vakilian, Mohsen and Johnson, Ralph E. and Dig, Danny
- **Venue:** Proceedings of the 27th European Conference on Object-Oriented Programming (ECOOP)
- **DOI / URL:** 10.1007/978-3-642-39038-8_23
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:90` — claim: "$W_{\textsc{mi}}$ (manual)     & 11 & Negara et al. 2013~\cite{negara2013manualautomated}; Vakilian et al. 2012~\cite{vakilian2012usedisuse} \\"
   - Source support: Loosely supported — paper provides the empirical foundation (23 devs, 5,371 refactorings, >50% manual) that motivates a manual-intentional penalty; specific weight 11 is the thesis's.
2. `methodology/methodology.tex:111` — claim: "Negara et al.~\cite{negara2013manualautomated} find that manual refactorings remain common nevertheless: across 23 developers and 5,371 refactorings, developers frequently performed them by hand even when an automated equivalent was available."
   - Source support: Supported — Abstract: "Using a corpus of 5,371 refactorings, ... more than half of the refactorings were performed manually." Matches verbatim. (§3 RQ1, §6.1)

**Notes / fixes required:** None.

---

### `vakilian2012usedisuse`

- **Type / title:** inproceedings — "Use, Disuse, and Misuse of Automated Refactorings" (2012)
- **Authors:** Vakilian, Mohsen and Chen, Nicholas and Negara, Stas and Rajkumar, Balaji Ambresh and Bailey, Brian P. and Johnson, Ralph E.
- **Venue:** Proceedings of the 34th International Conference on Software Engineering (ICSE)
- **DOI / URL:** 10.1109/ICSE.2012.6227190
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:90` — claim: "$W_{\textsc{mi}}$ (manual)     & 11 & Negara et al. 2013~\cite{negara2013manualautomated}; Vakilian et al. 2012~\cite{vakilian2012usedisuse} \\"
   - Source support: Loosely supported — paper supports the existence of misuse and underuse phenomena motivating a graded manual-vs-tool penalty; specific weight 11 is the thesis's.
2. `methodology/methodology.tex:111` — claim: "Vakilian et al.~\cite{vakilian2012usedisuse} show that automated refactorings can also be misused, which motivates a graded penalty rather than a binary one."
   - Source support: Supported — paper's central contribution is the use/disuse/misuse taxonomy; explicitly classifies certain uses of automated refactorings (proceeding without preview, using them in ways not recommended) as misuse.

**Notes / fixes required:** None. Optional: add IDEALS handle (hdl.handle.net/2142/27730) for the extended-TR free-access link.

---

### `dibiase2019decomposition`

- **Type / title:** article — "The Effects of Change Decomposition on Code Review: A Controlled Experiment" (2019)
- **Authors:** Di Biase, Marco and Bruntink, Magiel and van Deursen, Arie and Bacchelli, Alberto
- **Venue:** PeerJ Computer Science
- **DOI / URL:** 10.7717/peerj-cs.193
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:92` — claim: "$W_{\textsc{cg}}$ (commit-gap) & 7  & Tao \& Kim 2015~\cite{taokim2015partitioning}; Di Biase et al. 2019~\cite{dibiase2019decomposition}; Herzig \& Zeller 2013~\cite{herzig2013tangled}; Kudrjavets et al. 2022~\cite{kudrjavets2022small} \\"
   - Source support: Loosely supported — paper provides empirical evidence that decomposed changes improve review outcomes; specific weight 7 is the thesis's.
2. `methodology/methodology.tex:117` — claim: "Di Biase et al.'s controlled experiment~\cite{dibiase2019decomposition} supports the direction of the penalty: decomposed changes produced fewer comments that wrongly flagged sound code as defective (1 rather than 6, $p=0.03$)."
   - Source support: Supported (verbatim numbers) — Table 2 reports Control false positives Total = 6, Treatment = 1; Mann–Whitney U p = 0.03; Cliff's d = 0.36 (medium effect).

**Notes / fixes required:** None.

---

### `taokim2015partitioning`

- **Type / title:** inproceedings — "Partitioning Composite Code Changes to Facilitate Code Review" (2015)
- **Authors:** Tao, Yida and Kim, Sunghun
- **Venue:** Proceedings of the 12th IEEE/ACM Working Conference on Mining Software Repositories (MSR)
- **DOI / URL:** 10.1109/MSR.2015.24
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:92` — claim: "$W_{\textsc{cg}}$ (commit-gap) & 7  & Tao \& Kim 2015~\cite{taokim2015partitioning}; Di Biase et al. 2019~\cite{dibiase2019decomposition}; Herzig \& Zeller 2013~\cite{herzig2013tangled}; Kudrjavets et al. 2022~\cite{kudrjavets2022small} \\"
   - Source support: Loosely supported — paper motivates the commit-gap term by quantifying composite-change prevalence (17%, up to 29%) and demonstrating review benefits from partitioning; specific weight 7 is the thesis's.
2. `methodology/methodology.tex:117` — claim: "Tao \& Kim~\cite{taokim2015partitioning} report that separated commits let reviewers inspect changes more effectively in the same amount of time."
   - Source support: Supported — §V-E Table V: correctness 7.94 → 9.40 (by-file → by-slice), one-tailed paired t-test p = 0.01; time means 35.92 vs 40.60 min, p = 0.81 (not significant). RQ3 conclusion: "in a similar amount of time, participants answered code review questions significantly better when the composite changes under review were partitioned".

**Notes / fixes required:** None.

---

### `kudrjavets2022small`

- **Type / title:** inproceedings — "Do Small Code Changes Merge Faster? A Multi-Language Empirical Investigation" (2022)
- **Authors:** Kudrjavets, Gunnar and Nagappan, Nachiappan and Rastogi, Ayushi
- **Venue:** Proceedings of the 19th International Conference on Mining Software Repositories (MSR)
- **DOI / URL:** arXiv:2203.05045
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:92` — claim: "$W_{\textsc{cg}}$ (commit-gap) & 7  & Tao \& Kim 2015~\cite{taokim2015partitioning}; Di Biase et al. 2019~\cite{dibiase2019decomposition}; Herzig \& Zeller 2013~\cite{herzig2013tangled}; Kudrjavets et al. 2022~\cite{kudrjavets2022small} \\"
   - Source support: Loosely supported — one of four commit-gap anchors; specific weight 7 is thesis-calibrated.
2. `methodology/methodology.tex:117` — claim: "Kudrjavets et al.~\cite{kudrjavets2022small} find no correlation between PR size and time-to-merge across 1.25M PRs in ten languages, so smaller commit gaps are treated as improving review \emph{comprehensibility} and \emph{rollback locality}, not merge speed."
   - Source support: Supported — paper analyses ~1.25M PRs (845,316 GitHub across 10 languages + Gerrit/Phabricator validation) and concludes "pull request size and composition do not relate to time-to-merge".

**Notes / fixes required:** Minor — the "1.25M PRs in ten languages" phrasing collapses two datasets into the ten-language count; acceptable shorthand.

---

### `herzig2013tangled`

- **Type / title:** inproceedings — "The Impact of Tangled Code Changes" (2013)
- **Authors:** Herzig, Kim and Zeller, Andreas
- **Venue:** Proceedings of the 10th Working Conference on Mining Software Repositories (MSR)
- **DOI / URL:** 10.1109/MSR.2013.6624018
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:92` — claim: "$W_{\textsc{cg}}$ (commit-gap) & 7  & Tao \& Kim 2015~\cite{taokim2015partitioning}; Di Biase et al. 2019~\cite{dibiase2019decomposition}; Herzig \& Zeller 2013~\cite{herzig2013tangled}; Kudrjavets et al. 2022~\cite{kudrjavets2022small} \\"
   - Source support: Loosely supported — grounds the commit-gap rationale via the tangled-changes literature; specific weight is thesis-calibrated.
2. `methodology/methodology.tex:117` — claim: "Tangled commits also make later defect attribution less reliable~\cite{herzig2013tangled}."
   - Source support: Supported — paper finds up to 15% of bug fixes are tangled and ≥16.6% of source files are incorrectly associated with bug reports, directly supporting unreliable defect attribution.

**Notes / fixes required:** None.

---

### `munozbaron2020cognitive`

- **Type / title:** inproceedings — "An Empirical Validation of Cognitive Complexity as a Measure of Source Code Understandability" (2020)
- **Authors:** Mu\~noz Bar\'on, Marvin and Wyrich, Marvin and Wagner, Stefan
- **Venue:** Proceedings of the 14th ACM/IEEE International Symposium on Empirical Software Engineering and Measurement (ESEM)
- **DOI / URL:** 10.1145/3382494.3410636
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:131` — claim: "Cognitive complexity & 1.0 & Campbell 2018~\cite{campbell2018cognitive}; Mu{\~n}oz Bar{\'o}n et al. 2020~\cite{munozbaron2020cognitive} (partial validation); Lavazza et al. 2023~\cite{lavazza2023cognitive} (contradicting) & Per-method mean \\"
   - Source support: Supported — paper is the canonical partial validation of Campbell's cognitive complexity.
2. `methodology/methodology.tex:153` — claim: "Mu{\~n}oz Bar{\'o}n et al.~\cite{munozbaron2020cognitive} re-analysed 24,400 human understandability ratings and partially validated Campbell's claim, finding significant positive correlation with comprehension time and subjective ratings; correlation with correctness was weak."
   - Source support: Supported — paper reports ~24,000 understandability evaluations of 427 snippets (close to thesis's 24,400). Finds positive correlation with comprehension time and subjective ratings; correlation with correctness/task accuracy mixed/weak. Thesis claim is essentially accurate.

**Notes / fixes required:** Minor — paper abstract says "about 24,000"; verify 24,400 against methods section, otherwise reword to "about 24,000".

---

### `lavazza2023cognitive`

- **Type / title:** article — "An Empirical Evaluation of the ``Cognitive Complexity'' Measure as a Predictor of Code Understandability" (2023)
- **Authors:** Lavazza, Luigi and Abualkishik, Abdallah Z. and Liu, Geng and Morasca, Sandro
- **Venue:** Journal of Systems and Software
- **DOI / URL:** 10.1016/j.jss.2022.111561
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:131` — claim: "Cognitive complexity & 1.0 & Campbell 2018~\cite{campbell2018cognitive}; Mu{\~n}oz Bar{\'o}n et al. 2020~\cite{munozbaron2020cognitive} (partial validation); Lavazza et al. 2023~\cite{lavazza2023cognitive} (contradicting) & Per-method mean \\"
   - Source support: Supported — paper contradicts a strong-validation reading, finding cognitive complexity offers no substantial improvement over traditional measures.
2. `methodology/methodology.tex:153` — claim: "Lavazza et al.~\cite{lavazza2023cognitive}, on the other hand, find that cognitive complexity predicts understandability about as well as traditional size and cyclomatic-complexity measures, with negligible additional explanatory value."
   - Source support: Supported — paper finds cognitive complexity correlates with understandability "approximately as much as traditional measures" (LOC, McCabe) and adds negligible predictive value.

**Notes / fixes required:** None.

---

## 2. Load-bearing background citations

### `mens2004survey`

- **Type / title:** article — "A Survey of Software Refactoring" (2004)
- **Authors:** Mens, Tom and Tourw\'e, Tom
- **Venue:** IEEE Transactions on Software Engineering, vol. 30, no. 2, pp. 126–139
- **DOI / URL:** 10.1109/TSE.2004.1265817
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing DOI in .bib; vol/no/pages are present)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:10` — claim: "Mens and Tourw\'e later survey the research landscape, bringing together definitions, taxonomies, and tool support, and emphasising the role of precondition checking and program analysis in automated refactoring \cite{mens2004survey}."
   - Source support: Supported — survey covers definitions, taxonomies, tool support, and program analysis / precondition checking for refactoring.
2. `background/background.tex:15` — claim: "Research on refactoring often defines a set of \emph{operators} ... that can be composed together into larger transformations \cite{fowler1999refactoring,mens2004survey}."
   - Source support: Loosely supported — survey discusses operator-based refactoring catalogues; general anchor.
3. `background/background.tex:20` — claim: "this thesis adopts a state/action/trace framing aligned with the view of refactoring as sequential operator application \cite{fowler1999refactoring,mens2004survey}."
   - Source support: Loosely supported — generic framing citation.
4. `background/background.tex:129` — claim: "This is useful because it matches the operator-based view of refactoring used in catalogues and surveys \cite{fowler1999refactoring,mens2004survey}."
   - Source support: Loosely supported — generic framing citation.
5. `background/background.tex:235` — claim: "First, refactoring is well-defined as behaviour-preserving and supported by catalogues of operators and by tool-based precondition checking \cite{opdyke1992refactoring,fowler1999refactoring,mens2004survey}."
   - Source support: Supported — survey emphasises behaviour-preservation and precondition checking.
6. `methodology/methodology.tex:111` — claim: "Mens \& Tourw{\'e}~\cite{mens2004survey} note that IDE refactoring tools run static precondition checks that hand edits may bypass, making the automated path more reliable."
   - Source support: Supported — survey explicitly discusses IDE/tool support and the role of static precondition checks.
7. `introduction/introduction.tex:5` — claim: "and subsequent surveys categorise these refactoring operations and the tools that support them \cite{mens2004survey}."
   - Source support: Loosely supported — generic surveys-categorise framing.

**Notes / fixes required:** Add `doi = {10.1109/TSE.2004.1265817}` to `.bib`.

---

### `alikhanifard2025rminer`

- **Type / title:** article — "A Novel Refactoring and Semantic Aware Abstract Syntax Tree Differencing Tool and a Benchmark for Evaluating the Accuracy of Diff Tools" (2025)
- **Authors:** Alikhanifard, Pouria and Tsantalis, Nikolaos
- **Venue:** ACM Transactions on Software Engineering and Methodology, vol. 34, no. 2, art. 40
- **DOI / URL:** 10.1145/3696002 (arXiv 2403.05939)
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing DOI; `pages = {Article 40}` is non-canonical)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:126` — claim: "RefactoringMiner is a well-known tool that detects a wide range of refactoring types by analysing successive revisions, using structured matching to relate program entities across versions \cite{alikhanifard2025rminer}."
   - Source support: Supported — paper builds on RefactoringMiner extension; detects refactorings via structured matching across versions.
2. `background/background.tex:142` — claim: "Refactoring mining is important infrastructure for building traces and for characterising actions at scale \cite{alikhanifard2025rminer,silva2017refdiff}."
   - Source support: Supported — RefactoringMiner is the canonical refactoring-mining infrastructure used for trace/action extraction.
3. `background/background.tex:235` — claim: "Third, refactoring mining and AST differencing provide practical ways to infer refactoring actions from code changes, which enables trace reconstruction from repository histories \cite{alikhanifard2025rminer,falleri2024gumtree}."
   - Source support: Supported — paper combines refactoring mining with AST diff for revision-pair inference.
4. `methodology/methodology.tex:15` — claim: "in practice a single action can carry both a structured refactoring spec (recovered by RefactoringMiner~\cite{alikhanifard2025rminer}) and some leftover unstructured edits."
   - Source support: Supported — RefactoringMiner produces structured refactoring specs from diffs.
5. `methodology/methodology.tex:183` — claim: "The pattern match is established by RefactoringMiner-style mining of the diff~\cite{alikhanifard2025rminer}: if a non-null IDE-mappable spec is recovered from the manual edit, the step is a candidate."
   - Source support: Supported — matches the paper's approach.
6. `introduction/introduction.tex:9` — claim: "Alongside this, there is a substantial line of work that has improved the ability to \textit{detect} refactorings from code changes, notably by using AST-based differencing ... \cite{alikhanifard2025rminer,falleri2024gumtree}."
   - Source support: Supported.
7. `architecture/architecture.tex:118` — claim: "The pipeline recovers them by running RefactoringMiner~\cite{alikhanifard2025rminer} -- a standard tool that detects refactorings between two code states by AST-level structural matching -- over a sliding window of commit pairs in the shadow repo."
   - Source support: Supported.

**Notes / fixes required:**
- Add `doi = {10.1145/3696002}`.
- Replace `pages = {Article 40}` with `articleno = {40}, numpages = {63}` (or `pages = {1--63}`).

---

### `falleri2024gumtree`

- **Type / title:** inproceedings — "Fine-Grained, Accurate, and Scalable Source Code Differencing" (2024)
- **Authors:** Falleri, Jean-R\'emy and Martinez, Matias
- **Venue:** Proceedings of the 46th IEEE/ACM International Conference on Software Engineering (ICSE)
- **DOI / URL:** 10.1145/3597503.3639148
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:124` — claim: "GumTree is a representative AST differencing approach: it builds fine-grained mappings between AST nodes and produces edit scripts that line up better with the structural changes developers actually intended \cite{falleri2024gumtree}."
   - Source support: Supported — paper builds fine-grained AST mappings + edit scripts (GumTree heuristic).
2. `background/background.tex:235` — claim: "Third, refactoring mining and AST differencing provide practical ways to infer refactoring actions from code changes \cite{alikhanifard2025rminer,falleri2024gumtree}."
   - Source support: Supported — exemplifies AST differencing for inferring refactoring actions.
3. `introduction/introduction.tex:9` — claim: "notably by using AST-based differencing and structured matching to infer refactoring operations between revisions (e.g., RefactoringMiner and related approaches) ... \cite{alikhanifard2025rminer,falleri2024gumtree}."
   - Source support: Supported.

**Notes / fixes required:** Optional: add `pages = {231:1--231:12}` (or `articleno = {231}, numpages = {12}`) and `address = {Lisbon, Portugal}`. Note ACM TOC drops "Code" from the title; either variant is acceptable.

---

### `bouzenia2025trajectories`

- **Type / title:** article — "Understanding Software Engineering Agents: A Study of Thought-Action-Result Trajectories" (2025)
- **Authors:** Bouzenia, Islem and Pradel, Michael
- **Venue:** Proceedings of the 40th IEEE/ACM International Conference on Automated Software Engineering (ASE)
- **DOI / URL:** arXiv:2506.18824
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — **FAIL: WRONG ENTRY TYPE** (`@article` with `booktitle` — BibTeX silently drops `booktitle` from `@article`, so the venue is currently invisible to the bibliography)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:28` — claim: "This state/action/trace framing is consistent with recent software-engineering work that models agent and developer behaviour as a partially-observable Markov decision process \cite{gautam2025refactorbench,bouzenia2025trajectories}."
   - Source support: Loosely supported — paper uses trajectory / action-result framing (thought-action-result) but does not explicitly invoke POMDP terminology; the POMDP framing is more strictly from `gautam2025refactorbench`. Co-cite is acceptable, but consider softening or relying on `gautam2025refactorbench` as the POMDP source.
2. `background/background.tex:210` — claim: "Recent work has begun to formalise software-engineering agent behaviour as a trajectory of (action, observation) pairs in a POMDP-shaped environment \cite{gautam2025refactorbench,bouzenia2025trajectories,pan2025swegym}."
   - Source support: Loosely supported — same POMDP caveat as site 1.
3. `introduction/introduction.tex:13` — claim: "characterising recurring action patterns in software-engineering agent traces \cite{bouzenia2025trajectories}."
   - Source support: Supported — paper explicitly identifies recurring behavioural motifs / anti-patterns in agent traces.

**Notes / fixes required:**
- **Change entry type from `@article` to `@inproceedings`** (then the `booktitle` field becomes correct).
- Optional: add `publisher` and `address` once ASE 2025 proceedings appear in ACM/IEEE DL.
- Consider softening "POMDP" wording at sites 1 and 2, or scoping it to `gautam2025refactorbench`.

---

### `alomar2025slr`

- **Type / title:** article — "Software Refactoring Research with Large Language Models: A Systematic Literature Review" (2025)
- **Authors:** AlOmar, Eman Abdullah and others
- **Venue:** Journal of Systems and Software, vol. 235, art. 112762
- **DOI / URL:** 10.1016/j.jss.2025.112762
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (journal ambiguous "IST / JSS"; missing volume, article-id, DOI; author list elided)
- [ ] 2. Metadata accurate against canonical source — **FAIL: WRONG AUTHOR ORDER + venue needs disambiguation.** Canonical author order per ScienceDirect is `Martinez, Sofia and Xu, Luo and Elnaggar, Mariam and AlOmar, Eman Abdullah` (AlOmar is last/corresponding); journal is JSS (Elsevier PII `S0164121...` ⇒ JSS, ISSN 0164-1212), vol. 235, art. 112762, Dec 2025. DOI: 10.1016/j.jss.2025.112762.
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [ ] 5. Each cite site's claim is supported by the source — see Loose-support caveat below
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:210` — claim: "and a recent systematic literature review of LLM-based refactoring research identifies process-aware evaluation as an open gap in the field \cite{alomar2025slr}."
   - Source support: Loosely supported (with caveat) — the SLR reviews LLM-based refactoring research and discusses evaluation gaps; whether it explicitly names "process-aware evaluation" should be re-verified against the full text. The abstract emphasises lack of an SLR evaluating "overarching impacts" rather than process-aware evaluation specifically. Recommend softening to "evaluation gaps in LLM-based refactoring research" or adding a more specific page reference.
2. `introduction/introduction.tex:13` — claim: "A recent systematic literature review of LLM-based refactoring research likewise identifies process-aware evaluation as an open gap in the field \cite{alomar2025slr}."
   - Source support: Loosely supported — same caveat as site 1.

**Notes / fixes required (HIGH PRIORITY):**
- Correct authors: `Martinez, Sofia and Xu, Luo and Elnaggar, Mariam and AlOmar, Eman Abdullah`.
- Set `journal = {Journal of Systems and Software}`, `volume = {235}`, `pages = {112762}` (or `articleno = {112762}`), `doi = {10.1016/j.jss.2025.112762}`.
- Drop the "IST / JSS" slash.
- **Action**: verify whether the SLR text actually frames "process-aware evaluation" as a named gap; if not, soften both cite-site claims in `background.tex:210` and `introduction.tex:13`.

---

### `raychev2013resynth`

- **Type / title:** inproceedings — "Refactoring with Synthesis" (2013)
- **Authors:** Raychev, Veselin and Sch\"afer, Max and Sridharan, Manu and Vechev, Martin
- **Venue:** Proceedings of the 2013 ACM International Conference on Object Oriented Programming Systems Languages and Applications (OOPSLA)
- **DOI / URL:** 10.1145/2509136.2509535
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:157` — claim: "ReSynth is a representative system in this space \cite{raychev2013resynth}: it synthesises a sequence of IDE-supported refactorings consistent with a developer's partial edits, constraining the search by the entities involved in those edits and by the operators' preconditions."
   - Source support: Supported — paper synthesises sequences of IDE refactorings from partial developer edits, constrained by entities and preconditions.
2. `background/background.tex:162` — claim: "Synthesis systems mainly aim to \emph{reach} a target program (or a class of targets that are consistent with partial edits) and to do so using sequences that satisfy refactoring preconditions \cite{raychev2013resynth}."
   - Source support: Supported — synthesis aims to reach a target consistent with partial edits while satisfying preconditions.
3. `background/background.tex:196` — claim: "generate refactoring sequences that realise a transformation or reach a target (synthesis/navigation) \cite{raychev2013resynth,lin2016navigator}."
   - Source support: Supported.
4. `background/background.tex:235` — claim: "Fourth, recommendation and synthesis systems show that it is feasible to generate refactoring sequences and plan toward final goal states \cite{raychev2013resynth,lin2016navigator,alizadeh2020interactive}."
   - Source support: Supported.
5. `introduction/introduction.tex:21` — claim: "ReSynth synthesises a path to reach a target end state from a developer's partial edits \cite{raychev2013resynth}."
   - Source support: Supported — accurate one-line description of ReSynth.

**Notes / fixes required:** None. Optional: add `series = {OOPSLA '13}`, `address = {Indianapolis, IN, USA}`.

---

### `lin2016navigator`

- **Type / title:** inproceedings — "Interactive and Guided Architectural Refactoring with Search-Based Recommendation" (2016)
- **Authors:** Lin, Yu and Peng, Xin and Cai, Yumin and Dig, Danny and Zhang, Li and Zhao, Wenyun
- **Venue:** Proceedings of the 2016 24th ACM SIGSOFT International Symposium on Foundations of Software Engineering (FSE)
- **DOI / URL:** 10.1145/2950290.2950344 (⚠️ in `.bib`; correct DOI is **10.1145/2950290.2950317**)
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source — **FAIL** (wrong DOI; 5th author should be Zheng, Diwen, not Zhang, Li)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:175` — claim: "Refactoring Navigator is one of these: it takes a current system and a desired target architecture/design, then recommends a sequence of refactoring steps using a search-based strategy, presented through a navigation metaphor \cite{lin2016navigator}."
   - Source support: Supported — paper takes implementation + desired high-level design as target; iteratively recommends refactoring steps via search-based recommendation.
2. `background/background.tex:175` — claim: "Controlled evaluations report large reductions in task time and manual edits compared to unguided refactoring \cite{lin2016navigator}, which suggests that sequence-level guidance can change how developers actually refactor."
   - Source support: Supported — paper's controlled experiment (18 students) measures Time and ELOC; RN helps perform tasks "more effectively and efficiently".
3. `background/background.tex:180` — claim: "Navigation systems typically optimise toward reaching a target design, and they may use design metrics to guide progress \cite{lin2016navigator}."
   - Source support: Loosely supported — paper uses a reflexion model and target-design discrepancies to guide progress; "design metrics" is a generous paraphrase.
4. `background/background.tex:196` — claim: "generate refactoring sequences that realise a transformation or reach a target (synthesis/navigation) \cite{raychev2013resynth,lin2016navigator}."
   - Source support: Supported.
5. `background/background.tex:235` — claim: "Fourth, recommendation and synthesis systems show that it is feasible to generate refactoring sequences and plan toward final goal states \cite{raychev2013resynth,lin2016navigator,alizadeh2020interactive}."
   - Source support: Supported.
6. `introduction/introduction.tex:21` — claim: "Refactoring Navigator suggests refactoring steps to guide a system toward a desired target at an architectural level \cite{lin2016navigator}."
   - Source support: Supported.

**Notes / fixes required:**
- **Fix DOI** from `10.1145/2950290.2950344` (resolves to a different FSE 2016 paper) to `10.1145/2950290.2950317`.
- **Fix 5th author** from `Zhang, Li` to `Zheng, Diwen` (per DBLP).

---

### `harman2007pareto`

- **Type / title:** article in .bib (⚠️ should be `@inproceedings`) — "Pareto Optimal Search-Based Refactoring at the Design Level" (2007)
- **Authors:** Harman, Mark and Tratt, Laurence
- **Venue:** ⚠️ .bib says "Journal of Systems and Software, 80(4):522-534"; canonical record (DBLP) is **GECCO '07 conference, pp. 1106–1113, DOI 10.1145/1276958.1277176**
- **DOI / URL:** 10.1145/1276958.1277176 (correct, after venue fix)
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source — 🚨 **FAIL: WRONG VENUE** (paper is GECCO 2007, not JSS 80(4))
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:36` — claim: "Harman and Tratt demonstrate multi-objective search at the design level, explicitly treating coupling and cohesion as competing objectives and producing Pareto-optimal trade-offs \cite{harman2007pareto}."
   - Source support: Supported — GECCO paper introduces Pareto-optimal multi-objective search-based refactoring at the design level with coupling/cohesion as competing objectives.
2. `background/background.tex:66` — claim: "Cost terms like these are commonly treated as optimisation objectives alongside structural quality goals in search-based refactoring and recommendation settings ... \cite{harman2007pareto,mohan2019manyobjective}."
   - Source support: Loosely supported — the count-as-objective claim is more directly Mohan/Greer; consistent with Harman & Tratt's Pareto-front framing. Joint cite OK.
3. `background/background.tex:165` — claim: "Search-based refactoring methods explore sequences or sets of refactorings and then select those that optimise one or more objective functions based on quality metrics \cite{harman2007pareto}."
   - Source support: Supported.
4. `background/background.tex:165` — claim: "Harman and Tratt argue explicitly for multi-objective formulations (e.g., coupling vs.\ cohesion) and produce Pareto fronts of trade-offs, rather than forcing design quality into a single scalar \cite{harman2007pareto}."
   - Source support: Supported — central contribution of the paper.
5. `background/background.tex:197` — claim: "optimise sequences under explicit metric objectives (search-based refactoring) \cite{harman2007pareto,mohan2019manyobjective}."
   - Source support: Supported.
6. `background/background.tex:235` — claim: "Second, most evaluation work is still focused primarily on endpoints ... \cite{harman2007pareto,moha2010decor}."
   - Source support: Loosely supported — H&T evaluate endpoint metric improvement.
7. `methodology/methodology.tex:91` — claim: "$W_{\textsc{l}}$ (length)      & 11 & Harman \& Tratt 2007~\cite{harman2007pareto}; Mohan \& Greer 2019~\cite{mohan2019manyobjective} \\"
   - Source support: Loosely supported — H&T motivate multi-objective formulation; explicit refactoring-count-as-objective is more squarely Mohan & Greer. Joint cite acceptable.
8. `methodology/methodology.tex:114` — claim: "Harman \& Tratt~\cite{harman2007pareto} and Mohan \& Greer~\cite{mohan2019manyobjective} treat refactoring count as an objective on equal footing with quality in multi-objective search-based refactoring."
   - Source support: Loosely supported — same caveat as site 7.
9. `introduction/introduction.tex:21` — claim: "and search-based refactoring treats refactoring sequences as an optimisation problem under explicit quality functions \cite{harman2007pareto,mohan2019manyobjective}."
   - Source support: Supported.

**Notes / fixes required (HIGH PRIORITY — venue error):**
- **Change entry type `@article` → `@inproceedings`**.
- Replace `journal = {Journal of Systems and Software}` with `booktitle = {Proceedings of the 9th Annual Conference on Genetic and Evolutionary Computation (GECCO '07)}`.
- Add `pages = {1106--1113}`, `publisher = {ACM}`, `doi = {10.1145/1276958.1277176}`.
- **⚠️ User to double-check:** the venue claim is significant — verify against DBLP before applying. The JSS coordinates 80(4):522-534 in the current `.bib` correspond to a *different* 2007 SBR paper (not Harman & Tratt).

---

### `mohan2019manyobjective`

- **Type / title:** article — "Using a Many-Objective Approach to Investigate Automated Refactoring" (2019)
- **Authors:** Mohan, M. and Greer, Des
- **Venue:** Information and Software Technology, vol. 112, pp. 83–101
- **DOI / URL:** 10.1016/j.infsof.2019.04.009
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing volume, pages, DOI; explicit TODO in .bib)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:36` — claim: "Later work extends this to many-objective settings and introduces constraints or preferences that reflect practical concerns such as test coverage or prioritisation \cite{mohan2019manyobjective}."
   - Source support: Supported — paper extends multi-objective to many-objective and adds prioritisation / test-coverage-style objectives.
2. `background/background.tex:66` — claim: "Cost terms like these are commonly treated as optimisation objectives alongside structural quality goals \cite{harman2007pareto,mohan2019manyobjective}."
   - Source support: Supported.
3. `background/background.tex:167` — claim: "For example, many-objective approaches include not only quality improvements, but also concerns like prioritisation and how refactoring effort is spread across the codebase \cite{mohan2019manyobjective}."
   - Source support: Supported.
4. `background/background.tex:197` — claim: "optimise sequences under explicit metric objectives (search-based refactoring) \cite{harman2007pareto,mohan2019manyobjective}."
   - Source support: Supported.
5. `methodology/methodology.tex:91` — claim: "$W_{\textsc{l}}$ (length)      & 11 & Harman \& Tratt 2007~\cite{harman2007pareto}; Mohan \& Greer 2019~\cite{mohan2019manyobjective} \\"
   - Source support: Supported — paper treats refactoring count as one of many objectives.
6. `methodology/methodology.tex:114` — claim: "Harman \& Tratt~\cite{harman2007pareto} and Mohan \& Greer~\cite{mohan2019manyobjective} treat refactoring count as an objective on equal footing with quality in multi-objective search-based refactoring."
   - Source support: Supported.
7. `introduction/introduction.tex:21` — claim: "and search-based refactoring treats refactoring sequences as an optimisation problem under explicit quality functions \cite{harman2007pareto,mohan2019manyobjective}."
   - Source support: Supported.

**Notes / fixes required:**
- Add `volume = {112}`, `pages = {83--101}`, `doi = {10.1016/j.infsof.2019.04.009}`.
- Remove TODO comment line.

---

### `opdyke1992refactoring`

- **Type / title:** phdthesis — "Refactoring Object-Oriented Frameworks" (1992)
- **Authors:** Opdyke, William F.
- **Venue:** University of Illinois at Urbana-Champaign
- **DOI / URL:** https://www.laputan.org/pub/papers/opdyke-thesis.pdf
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:10` — claim: "Most definitions emphasise that refactorings are applied as small, semantics-preserving steps and that safety is ensured through maintaining preconditions and/or validation (e.g. testing) \cite{opdyke1992refactoring,fowler1999refactoring,fowler2018refactoring}."
   - Source support: Supported — thesis defines refactorings with preconditions to preserve behaviour.
2. `background/background.tex:10` — claim: "Opdyke's foundational work explains refactorings as program transformations equipped with applicability conditions (preconditions) intended to preserve behaviour under a static approximation \cite{opdyke1992refactoring}."
   - Source support: Supported.
3. `background/background.tex:235` — claim: "First, refactoring is well-defined as behaviour-preserving and supported by catalogues of operators and by tool-based precondition checking \cite{opdyke1992refactoring,fowler1999refactoring,mens2004survey}."
   - Source support: Supported.
4. `introduction/introduction.tex:5` — claim: "Traditional definitions emphasise this preservation of behaviour and describe refactoring as a sequence of small, semantics-preserving transformations \cite{opdyke1992refactoring,fowler1999refactoring,fowler2018refactoring}."
   - Source support: Supported.

**Notes / fixes required:** None. Optional: add `note = {Also Technical Report UIUCDCS-R-92-1759}`.

---

### `silva2017refdiff`

- **Type / title:** inproceedings — "RefDiff: Detecting Refactorings in Version Histories" (2017)
- **Authors:** Silva, Danilo and Valente, Marco Tulio (`.bib` currently says "Silva, Danilo and others" — only two authors)
- **Venue:** Proceedings of the 14th International Conference on Mining Software Repositories (MSR), pp. 269–279
- **DOI / URL:** 10.1109/MSR.2017.14 (arXiv 1704.01544)
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing pages, publisher, DOI; explicit TODO in .bib)
- [ ] 2. Metadata accurate against canonical source — FAIL ("and others" elision is wrong; only two authors)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:126` — claim: "RefDiff takes a similar direction: it detects common refactoring types in version histories using similarity heuristics and static analysis \cite{silva2017refdiff}."
   - Source support: Supported — RefDiff detects 13 refactoring types using static analysis + code similarity.
2. `background/background.tex:142` — claim: "Refactoring mining is important infrastructure for building traces and for characterising actions at scale \cite{alikhanifard2025rminer,silva2017refdiff}."
   - Source support: Supported.

**Notes / fixes required:**
- Replace `author = {Silva, Danilo and others}` with `author = {Silva, Danilo and Valente, Marco Tulio}` (exactly two authors).
- Add `pages = {269--279}`, `publisher = {IEEE Press}`, `doi = {10.1109/MSR.2017.14}`.
- Remove TODO note in .bib.

---

### `rolim2017refazer`

- **Type / title:** inproceedings — "Learning Syntactic Program Transformations from Examples" (2017)
- **Authors:** Rolim, Reudismam and Soares, Gustavo and D'Antoni, Loris and Polozov, Oleksandr and Gulwani, Sumit and Ge, Sherry and Rungta, Neha
- **Venue:** Proceedings of the 39th International Conference on Software Engineering (ICSE)
- **DOI / URL:** 10.1109/ICSE.2017.44
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source — **FAIL** (authors 6–7 wrong; "Ge, Sherry" + "Rungta, Neha" do not appear on this paper)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:159` — claim: "REFAZER learns program transformations from input-output examples of code edits \cite{rolim2017refazer}."
   - Source support: Supported — abstract describes learning program transformations from input-output examples.

**Notes / fixes required:**
- **Fix authors 6–8.** Canonical author list (per Microsoft Research / arXiv 1608.09000 / DBLP) has 8 authors: `Rolim, Reudismam and Soares, Gustavo and D'Antoni, Loris and Polozov, Oleksandr and Gulwani, Sumit and Gheyi, Rohit and Suzuki, Ryo and Hartmann, Bj\"orn`. The current `.bib` has `Ge, Sherry and Rungta, Neha` at positions 6–7, which appear to be from a different paper.

---

### `alizadeh2020interactive`

- **Type / title:** article — "An Interactive and Dynamic Search-Based Approach to Software Refactoring Recommendations" (2020)
- **Authors:** Alizadeh, Vahid and Kessentini, Marouane and Ouni, Ali and Mkaouer, Mohamed Wiem and \'O Cinn\'eide, Mel and Cai, Yuanfang (.bib order — see fix)
- **Venue:** IEEE Transactions on Software Engineering, vol. 46, no. 9, pp. 932–961
- **DOI / URL:** 10.1109/TSE.2018.2872711
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source — FAIL (author order: Mkaouer/Ouni positions swapped vs IEEE record)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:183` — claim: "Alizadeh and Kessentini propose an interactive and dynamic search-based approach that presents refactoring recommendations iteratively and updates the search based on developer decisions \cite{alizadeh2020interactive}."
   - Source support: Supported.
2. `background/background.tex:186` — claim: "Work on interactive recommendation provides ideas for presenting sequence-level suggestions and for building feedback loops into the developer's workflow \cite{alizadeh2020interactive}."
   - Source support: Supported.
3. `background/background.tex:198` — claim: "incorporate developer feedback into iterative suggestion mechanisms (interactive recommendation) \cite{alizadeh2020interactive}."
   - Source support: Supported.
4. `background/background.tex:235` — claim: "Fourth, recommendation and synthesis systems show that it is feasible to generate refactoring sequences and plan toward final goal states \cite{raychev2013resynth,lin2016navigator,alizadeh2020interactive}."
   - Source support: Supported.

**Notes / fixes required:**
- **Fix author order** to `Alizadeh, Vahid and Kessentini, Marouane and Mkaouer, Mohamed Wiem and {\'O} Cinn{\'e}ide, Mel and Ouni, Ali and Cai, Yuanfang` (Mkaouer is 3rd, Ouni 5th, per IEEE TSE record).

---

## 3. Statistical-method citations

### `cohen1960kappa`

- **Type / title:** article — "A coefficient of agreement for nominal scales" (1960)
- **Authors:** Cohen, Jacob
- **Venue:** Educational and Psychological Measurement
- **DOI / URL:** 10.1177/001316446002000104
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:258` — claim: "Per-kind agreement is reported using Cohen's $\kappa$~\cite{cohen1960kappa}, applied separately to each kind in $\mathcal{K}$."
   - Source support: Supported — Cohen's κ is the canonical coefficient introduced in this paper.
2. `results/results.tex:235` — claim: "Table~\ref{tab:results-userstudy-kappa} reports per-kind Cohen's $\kappa$~\cite{cohen1960kappa} for the three rater pairs against the 45-session set."
   - Source support: Supported.

**Notes / fixes required:** None.

---

### `landis1977kappa`

- **Type / title:** article — "The measurement of observer agreement for categorical data" (1977)
- **Authors:** Landis, J. Richard and Koch, Gary G.
- **Venue:** Biometrics
- **DOI / URL:** 10.2307/2529310
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:258` — claim: "The Landis and Koch thresholds~\cite{landis1977kappa} are used for interpretation: $\kappa < 0.20$ slight, $0.21$--$0.40$ fair, $0.41$--$0.60$ moderate, $0.61$--$0.80$ substantial, $0.81$--$1.00$ almost perfect."
   - Source support: Supported — these are the canonical Landis & Koch thresholds from this paper.
2. `results/results.tex:235` — claim: "All four kinds reach at least substantial agreement by the Landis and Koch thresholds~\cite{landis1977kappa}."
   - Source support: Supported.

**Notes / fixes required:** None.

---

### `kendall1948rank`

- **Type / title:** book — "Rank Correlation Methods" (1948)
- **Authors:** Kendall, Maurice G.
- **Venue:** Charles Griffin \& Co., London
- **DOI / URL:** —
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:29` — claim: "Kendall's $\tau$-b~\cite{kendall1948rank, agresti2010ordinal} is a rank correlation coefficient designed for short rankings on small samples, reported as the percentage of rows where $\tau = 1.0$ (ranking unchanged) and separately as the percentage where $\tau < 0$ (ranking inverted)."
   - Source support: Supported — Kendall's τ originates in this monograph; τ-b/short-ranking detail carried by `agresti2010ordinal` (joint cite).

**Notes / fixes required:** None.

---

### `webber2010rbo`

- **Type / title:** article — "A Similarity Measure for Indefinite Rankings" (2010)
- **Authors:** Webber, William and Moffat, Alistair and Zobel, Justin
- **Venue:** ACM Transactions on Information Systems
- **DOI / URL:** 10.1145/1852102.1852106
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:29` — claim: "on the other two session sets every fixture has at most $5$ divergence points and the top-$5$ comparison collapses to set-preservation~\cite{webber2010rbo}."
   - Source support: Supported — RBO's set-overlap basis (A_d at depth d) is the set-preservation foundation invoked.

**Notes / fixes required:** None.

---

### `wohlin2012experimentation`

- **Type / title:** book — "Experimentation in Software Engineering" (2012)
- **Authors:** Wohlin, Claes and Runeson, Per and H\"ost, Martin and Ohlsson, Magnus C. and Regnell, Bj\"orn and Wessl\'en, Anders
- **Venue:** Springer (2nd edition)
- **DOI / URL:** ISBN 978-3-642-29044-2
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `threats/threats.tex:4` — claim: "The discussion follows the standard four-axis framework from empirical software engineering~\cite{wohlin2012experimentation}: construct validity, internal validity, external validity, and statistical conclusion validity."
   - Source support: Supported — canonical reference for the four-axis validity framework (Ch. 8 in 2nd ed.).

**Notes / fixes required:** None.

---

### `lundberg2017shap`

- **Type / title:** inproceedings — "A Unified Approach to Interpreting Model Predictions" (2017)
- **Authors:** Lundberg, Scott M. and Lee, Su-In
- **Venue:** Advances in Neural Information Processing Systems 30 (NeurIPS 2017)
- **DOI / URL:** https://proceedings.neurips.cc/paper/2017/hash/8a20a8621978632d76c43dfd28b67767-Abstract.html
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:112` — claim: "Shapley-value feature attribution~\cite{lundberg2017shap, strumbelj2014explaining} uses enumeration over all $2^n$ subsets and is exact rather than approximate when $n$ is small."
   - Source support: Supported — SHAP is Shapley-value feature attribution; KernelSHAP is the sampling approximation when enumeration is intractable.

**Notes / fixes required:** None.

---

### `agresti2010ordinal`

- **Type / title:** book — "Analysis of Ordinal Categorical Data" (2010)
- **Authors:** Agresti, Alan
- **Venue:** John Wiley \& Sons, Hoboken NJ (2nd edition)
- **DOI / URL:** 10.1002/9780470594001
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:29` — claim: "Kendall's $\tau$-b~\cite{kendall1948rank, agresti2010ordinal} is a rank correlation coefficient designed for short rankings on small samples."
   - Source support: Supported — Agresti is the standard reference for ordinal/rank-data methods; covers Kendall's τ-b (Ch. 7).

**Notes / fixes required:** None.

---

### `saisana2005composite`

- **Type / title:** article — "Uncertainty and sensitivity analysis techniques as tools for the quality assessment of composite indicators" (2005)
- **Authors:** Saisana, Michaela and Saltelli, Andrea and Tarantola, Stefano
- **Venue:** Journal of the Royal Statistical Society: Series A (Statistics in Society)
- **DOI / URL:** 10.1111/j.1467-985X.2005.00350.x
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:112` — claim: "Composite-indicator sensitivity analysis~\cite{saisana2005composite} establishes systematic weight perturbation as standard practice for composite indices."
   - Source support: Supported — paper systematically applies uncertainty/sensitivity analysis to composite indicators with weighting scheme as a key input varied.

**Notes / fixes required:** None.

---

### `strumbelj2014explaining`

- **Type / title:** article — "Explaining prediction models and individual predictions with feature contributions" (2014)
- **Authors:** \v{S}trumbelj, Erik and Kononenko, Igor
- **Venue:** Knowledge and Information Systems
- **DOI / URL:** 10.1007/s10115-013-0679-x
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:112` — claim: "Shapley-value feature attribution~\cite{lundberg2017shap, strumbelj2014explaining} uses enumeration over all $2^n$ subsets and is exact rather than approximate when $n$ is small."
   - Source support: Supported — canonical paper proposing Shapley-value-based feature contributions; the exact form enumerates all 2^n subsets, with a sampling approximation for large n.

**Notes / fixes required:** None.

---

## 4. Remaining cited entries

### `arcellifontana2016falsepositives`

- **Type / title:** inproceedings — "Antipattern and Code Smell False Positives: Preliminary Conceptualization and Classification" (2016)
- **Authors:** Arcelli Fontana, Francesca and others (⚠️ incomplete)
- **Venue:** ⚠️ .bib says "workshop on code smells / antipatterns" — incorrect; canonical venue is **SANER 2016** (IEEE conference, ERA track)
- **DOI / URL:** 10.1109/SANER.2016.84
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (vague booktitle, missing co-authors/pages/DOI)
- [ ] 2. Metadata accurate against canonical source — FAIL (venue mislabelled as workshop; is SANER 2016 conference)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:53` — Supported (paper explicitly conceptualises false positives in antipattern/code-smell detection).
2. `background/background.tex:61` — Supported (co-cite for no-single-detector-reliable claim).
3. `background/background.tex:235` — Supported.
4. `methodology/methodology.tex:135` — Supported (canonical smell-detection literature pointer).

**Notes / fixes required:**
- Change venue: `booktitle = {Proceedings of the 2016 IEEE 23rd International Conference on Software Analysis, Evolution, and Reengineering (SANER)}`.
- Expand authors: `Arcelli Fontana, Francesca and Dietrich, Jens and Walter, Bartosz and Yamashita, Aiko and Zanoni, Marco`.
- Add `volume = {1}`, `pages = {609--613}`, `doi = {10.1109/SANER.2016.84}`, `publisher = {IEEE}`.

---

### `bansiya2002qmood`

- **Type / title:** article — "A Hierarchical Model for Object-Oriented Design Quality Assessment" (2002)
- **Authors:** Bansiya, Jagdish and Davis, Carl G.
- **Venue:** IEEE Transactions on Software Engineering, Vol. 28, No. 1, pp. 4–17
- **DOI / URL:** 10.1109/32.979986
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing volume/number/pages/doi; see Notes)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — IEEE Xplore (paywalled but DOI valid); abstract + methodology summary verified via Semantic Scholar + secondary surveys
- [x] 4. All cite sites enumerated below
- [ ] 5. Each cite site's claim is supported by the source — FAIL on sites 1 and 3; see Notes
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `threats/threats.tex:31` — claim: "Snapshot composites like the Maintainability Index~\cite{coleman1994maintainability} and QMOOD~\cite{bansiya2002qmood} were regression-fitted against expert ratings of completed code."
   - Source support: **Unsupported (for QMOOD half)**. QMOOD weights are not regression-fitted; they are set by the authors "in accordance with influence and importance" and then *validated* against expert rankings (13 experts × 14 projects; rank-correlation analysis, 11/13 evaluators correlated well with QMOOD ranking). Coleman MI portion is separately handled in that entry. Sentence needs a rewrite, since the regression-fit framing applies to MI, not QMOOD.
2. `methodology/methodology.tex:144` — claim: "Wagner et al.'s Quamoco framework~\cite{wagner2015quamoco} and the QMOOD quality model~\cite{bansiya2002qmood} both use this kind of layered weighted-sum aggregation, which is a defensible choice when there is no calibration data to fit the weights against."
   - Source support: **Supported** — QMOOD uses 6 quality characteristics × 11 design properties with weighted-sum aggregation per the published model equations.
3. `methodology/methodology.tex:147` — claim: "Calibrated weights do exist for snapshot-level static quality composites --- notably Coleman et al.'s Maintainability Index ... and QMOOD~\cite{bansiya2002qmood}, whose design-attribute weights were tuned against expert assessments."
   - Source support: **Unsupported (for QMOOD half)** — QMOOD weights are set on theoretical/anecdotal grounds, *not* tuned against expert assessments. The expert assessment is used for post-hoc rank validation, not weight fitting. "Tuned against expert assessments" is inaccurate.

**Notes / fixes required:**
- `.bib` metadata: add `volume = {28}`, `number = {1}`, `pages = {4--17}`, `doi = {10.1109/32.979986}`.
- Thesis sentence fix at `threats.tex:31`: drop QMOOD from the "regression-fitted against expert ratings" claim (that's specific to MI); QMOOD is validated via rank-correlation, not regression-fit.
- Thesis sentence fix at `methodology.tex:147`: rewrite "whose design-attribute weights were tuned against expert assessments" to e.g. "whose design-attribute weights are set theoretically and validated against expert ranks". Or drop QMOOD from this sentence and keep only Coleman MI, which is the genuine regression-fitted example.

---

### `basha2025codewatcher`

- **Type / title:** inproceedings — "CodeWatcher: IDE Telemetry Data Extraction Tool for Understanding Coding Interactions with LLMs" (2025)
- **Authors:** Basha, Manaal and Ribeiro, Aim\^ee M. and Javahar, Jeena and de Souza, Cleidson R. B. and Rodr\'iguez-P\'erez, Gema
- **Venue:** Proceedings of the 41st IEEE International Conference on Software Maintenance and Evolution (ICSME), Tool Demonstration Track
- **DOI / URL:** arXiv:2510.11536
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — arXiv abstract retrieved
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:223` — claim: "\emph{CodeWatcher} is a representative example: a lightweight client-server VS Code extension that captures fine-grained, semantically labelled IDE events ... with timestamps to support post-hoc reconstruction of coding sessions \cite{basha2025codewatcher}."
   - Source support: **Supported** — arXiv abstract confirms "lightweight, unobtrusive client-server system designed to capture fine-grained interaction events from within the Visual Studio Code (VS Code) editor"; logs insertions/deletions/copy-paste/focus shifts with timestamps; "enables developers to reconstruct coding sessions after the fact". Verbatim alignment with thesis claim.

**Notes / fixes required:** None. Entry is clean.

---

### `chen2024morcora`

- **Type / title:** article — "MORCoRA: Multi-Objective Refactoring Recommendation Considering Review Availability" (2024)
- **Authors:** Chen, Lei and Hayashi, Shinpei (.bib currently says "Lerina" — needs fix)
- **Venue:** International Journal of Software Engineering and Knowledge Engineering
- **DOI / URL:** 10.1142/S0218194024500438; arXiv:2408.06568
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source — FAIL: first-author given name is "Lei" not "Lerina"
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — arXiv:2408.06568
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:169` — claim: "and related work has extended the framework with socio-technical cost proxies such as reviewer availability \cite{chen2024morcora}."
   - Source support: **Supported** — abstract confirms MORCoRA is a multi-objective refactoring search that adds reviewer availability (reviewer expertise + workload) as an objective alongside code-quality improvement. "Socio-technical cost proxy" is an accurate gloss for the review-availability objective.

**Notes / fixes required:**
- `.bib` author-name fix: change `Chen, Lerina` to `Chen, Lei`.

---

### `coleman1994maintainability`

- **Type / title:** article — "Using Metrics to Evaluate Software System Maintainability" (1994)
- **Authors:** Coleman, Don and Ash, Dan and Lowther, Bruce and Oman, Paul
- **Venue:** IEEE Computer, Vol. 27, No. 8, pp. 44–49
- **DOI / URL:** 10.1109/2.303623
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing volume/number/pages)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — Semantic Scholar + open-access mirror
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source — loosely; see Notes
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `threats/threats.tex:31` — claim: "Snapshot composites like the Maintainability Index~\cite{coleman1994maintainability} and QMOOD~\cite{bansiya2002qmood} were regression-fitted against expert ratings of completed code."
   - Source support: **Loosely supported** for MI. The regression-fit on HP engineer-provided 1–100 maintainability ratings is documented in the companion paper Oman & Hagemeister 1994 (JSS 24(3):251–266), which Coleman et al. 1994 builds on. Coleman et al. is the canonical MI citation but the calibration method is described in more detail in the companion. Acceptable here since MI is shorthand for the work as a whole. (QMOOD half of this sentence is Unsupported — see `bansiya2002qmood` entry.)
2. `methodology/methodology.tex:147` — claim: "Calibrated weights do exist for snapshot-level static quality composites --- notably Coleman et al.'s Maintainability Index, whose coefficients were regression-fitted against expert maintainability ratings on industrial systems~\cite{coleman1994maintainability}."
   - Source support: **Loosely supported**. Same caveat: the regression-fit was performed in Oman & Hagemeister 1994 against HP C/Pascal industrial systems with engineer-provided maintainability ratings. Coleman et al. 1994 reports and uses the MI but the calibration is in the companion. Either keep this citation (defensible — Coleman et al. is the canonical reference) or add a second citation to Oman & Hagemeister 1994 for the regression-fit claim specifically.

**Notes / fixes required:**
- `.bib` metadata: add `volume = {27}`, `number = {8}`, `pages = {44--49}`.
- Optional: add a `omanhagemeister1994polynomials` entry (J. Syst. Softw. 24(3):251–266, doi: 10.1016/0164-1212(94)90067-1) and cite alongside `coleman1994maintainability` at the two regression-fit sites for precision. Not load-bearing — the current Coleman-only citation is a defensible shorthand.

---

### `cortellessa2023manyobjective`

- **Type / title:** article — "Many-Objective Optimization of Non-Functional Attributes Based on Refactoring of Software Models" (2023)
- **Authors:** Cortellessa, Vittorio and Di Pompeo, Daniele and Stoico, Vincenzo and Tucci, Michele
- **Venue:** Information and Software Technology, vol. 157, art. 107159
- **DOI / URL:** 10.1016/j.infsof.2023.107159; arXiv:2301.09531
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing volume/article number)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — arXiv:2301.09531
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:169` — claim: "recent many-objective formulations explicitly treat \emph{architectural distance}-a direct disruption proxy-as a first-class Pareto objective alongside structural quality \cite{cortellessa2023manyobjective}."
   - Source support: **Supported** — abstract names "architectural distance, which quantifies the effort to obtain a model alternative from the initial one" as one of four objectives in the NSGA-II Pareto search, alongside performance, reliability, and antipattern count. Direct match for "first-class Pareto objective alongside structural quality".
2. `methodology/methodology.tex:114` — claim: "Cortellessa et al.~\cite{cortellessa2023manyobjective} continue that line of work, trading effort against code-quality objectives explicitly."
   - Source support: **Supported** — architectural distance is the explicit effort proxy traded off against performance/reliability/antipattern objectives.

**Notes / fixes required:**
- `.bib` metadata: add `volume = {157}`, `pages = {107159}` (article number), `doi = {10.1016/j.infsof.2023.107159}`.

---

### `damevski2017usagesmells`

- **Type / title:** article — "Mining Sequences of Developer Interactions in Visual Studio for Usage Smells" (2017)
- **Authors:** Damevski, Kostadin and Fritz, Thomas and Shepherd, David (⚠️ wrong — see fix)
- **Venue:** IEEE Transactions on Software Engineering, vol. 43, no. 4, pp. 359–371
- **DOI / URL:** 10.1109/TSE.2016.2592905
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source — 🚨 **FAIL: WRONG CO-AUTHORS.** Thomas Fritz is not on this paper. Canonical authors per DBLP/IEEE: `Damevski, Kostadin and Shepherd, David C. and Schneider, Johannes and Pollock, Lori L.`
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:219` — Supported (paper mines IDE telemetry event sequences in VS for usage smells).
2. `background/background.tex:226` — Supported (discusses noise/segmentation).
3. `background/background.tex:235` — Supported.

**Notes / fixes required (HIGH PRIORITY):**
- **Replace authors** with canonical: `Damevski, Kostadin and Shepherd, David C. and Schneider, Johannes and Pollock, Lori L.`

---

### `eclipse_jdt_faq`

- **Type / title:** misc — "JDT/FAQ" (2026)
- **Authors:** Eclipse Foundation
- **Venue:** Eclipse Wiki
- **DOI / URL:** https://wiki.eclipse.org/JDT/FAQ
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — wiki page live
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `architecture/architecture.tex:93` — claim: "The Eclipse JDT FAQ~\cite{eclipse_jdt_faq} distinguishes two deployment modes: bare-classpath (for parser and AST features) and runtime-workbench (for workspace-dependent features)."
   - Source support: **Loosely supported** — FAQ explicitly says "JDT Core has no dependency on UI side, however it requires a runtime-workbench. Hence you can use it in an Eclipse headless application or include all the dependent jar files in the class path of your application. (You will have to use ASTParser.setSource() and ASTParser.setEnvironment() to be able to parse non Eclipse Java projects.)" The thesis's "two deployment modes" framing is a defensible paraphrase of this — the FAQ describes one approach (workbench) and a documented workaround (bare classpath via ASTParser configuration). The "bare-classpath for parser/AST features" and "runtime-workbench for workspace-dependent features" labels are the thesis's, not the FAQ's, but they accurately describe what the FAQ documents.

**Notes / fixes required:** None for content. The label "(2026)" reflects when the wiki page was accessed (current); FAQ pages are living docs. Consider adding `urldate = {2026-05-XX}` to `.bib` for clarity, but not required.

---

### `eclipse_jdtls`

- **Type / title:** misc — "Eclipse JDT Language Server (eclipse.jdt.ls)" (2026)
- **Authors:** Eclipse Foundation
- **Venue:** Eclipse Foundation project; reference implementation on GitHub
- **DOI / URL:** https://github.com/eclipse-jdtls/eclipse.jdt.ls
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — GitHub repo live
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `architecture/architecture.tex:93` — claim: "The reference implementation is the Eclipse JDT Language Server~\cite{eclipse_jdtls}, maintained by the Eclipse Foundation as a headless JDT distribution."
   - Source support: **Supported** — README confirms project is a Java LSP implementation built on Eclipse JDT, maintained under the `eclipse-jdtls` GitHub org (Eclipse Foundation stewardship via Eclipse CI, EPL 2.0 license). "Headless JDT distribution" is a fair description — the LSP server runs headlessly and is built atop the JDT bundles. Minor caveat: README phrases the project as a language server built on JDT/LSP4J/M2Eclipse/Buildship rather than literally "headless JDT distribution", but the gloss is accurate.

**Notes / fixes required:** None.

---

### `eilertsen2024refactorings`

- **Type / title:** article — "A Study of Refactorings during Software Change Tasks" (2024)
- **Authors:** Eilertsen, Anna Maria and Murphy, Gail C. (.bib currently missing Murphy — needs fix)
- **Venue:** Journal of Software: Evolution and Process
- **DOI / URL:** 10.1002/smr.2378
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source — FAIL: co-author Gail C. Murphy (UBC) missing from author list
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — Wiley Online Library
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:211` — claim: "Empirical observational studies independently confirm that real-world refactoring proceeds as ordered sequences of operations interleaved with other change activities \cite{eilertsen2024refactorings}, motivating the trajectory-level framing adopted in this thesis."
   - Source support: **Supported** — observational study of 17 professional developers on 3 change tasks; finds developers' workflow strategies shape opportunities for refactoring, with refactorings intermixed with other change activities during change tasks. Aligns with the "interleaved with other change activities" framing. "Ordered sequences of operations" is a reasonable paraphrase of the developer-strategy framing.

**Notes / fixes required:**
- `.bib` author-list fix: add `and Murphy, Gail C.` so the entry reads `author = {Eilertsen, Anna Maria and Murphy, Gail C.}`.

---

### `foutsekh2018interactiontraces`

- **Type / title:** inproceedings — "Developer Interaction Traces Backed by IDE Screen Recordings from Think-Aloud Sessions" (2018)
- **Authors:** Foutsekh, Said and Yamashita, Aiko and Petrillo, Fabio and Khomh, Mourad and Gu\'eh\'eneuc, Yann-Ga\"el (⚠️ INVENTED AUTHOR — see fix)
- **Venue:** Proceedings of the 15th International Conference on Mining Software Repositories (MSR '18), Data Showcase track
- **DOI / URL:** 10.1145/3196398.3196457
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (wrong authors; missing pages/DOI; vague booktitle)
- [ ] 2. Metadata accurate against canonical source — 🚨🚨 **FAIL: INVENTED AUTHOR "Foutsekh, Said".** This is a misparse of "Foutse Khomh" (one person). The citekey `foutsekh2018...` itself reflects this parsing error. "Khomh, Mourad" is also wrong — given name is "Foutse". Canonical author list (DBLP / MSR'18 proceedings): `Yamashita, Aiko and Petrillo, F\'abio and Khomh, Foutse and Gu\'eh\'eneuc, Yann-Ga\"el`. First author is Yamashita.
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:235` — Supported (paper publishes IDE interaction traces with screen-recorded think-aloud sessions; discusses segmentation/interpretation challenges).

**Notes / fixes required (HIGH PRIORITY):**
- **Replace author field** with canonical: `Yamashita, Aiko and Petrillo, F\'abio and Khomh, Foutse and Gu\'eh\'eneuc, Yann-Ga\"el`.
- Add `pages = {50--53}`, `doi = {10.1145/3196398.3196457}`, `publisher = {ACM}`, `address = {Gothenburg, Sweden}`.
- Consider renaming citekey `foutsekh2018interactiontraces` → `yamashita2018interactiontraces` (and update the one cite site in `background.tex:235`).

---

### `heitlager2007sig`

- **Type / title:** inproceedings — "A Practical Model for Measuring Maintainability" (2007)
- **Authors:** Heitlager, Ilja and Kuipers, Tobias and Visser, Joost
- **Venue:** Proceedings of the 6th International Conference on the Quality of Information and Communications Technology (QUATIC), pp. 30–39
- **DOI / URL:** 10.1109/QUATIC.2007.8
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing pages)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — open PDF on ResearchGate + Open Universiteit mirror
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:133` — claim: "Duplication           & 1.0 & Heitlager et al. 2007~\cite{heitlager2007sig}; Fowler 1999~\cite{fowler1999refactoring} & CPD-detected cloned lines on touched files \\"
   - Source support: **Supported** — SIG maintainability model explicitly includes duplication as one of six source-code properties (volume, complexity, duplication, unit length, # units, # modules) mapped onto ISO 9126 maintainability sub-characteristics. Heitlager et al. is the canonical citation for treating duplication as a first-class maintainability sub-signal.

**Notes / fixes required:**
- `.bib` metadata: add `pages = {30--39}`.

---

### `khrishe2016empirical`

- **Type / title:** inproceedings — "An empirical study on the effect of the order of applying software refactoring" (2016)
- **Authors:** Khrishe, Younes and Alshayeb, Mohammad
- **Venue:** Proceedings of the 7th International Conference on Computer Science and Information Technology (CSIT), Amman, Jordan, pp. 1–4
- **DOI / URL:** 10.1109/CSIT.2016.7549471
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing pages)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — IEEE Xplore abstract
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:221` — claim: "Prior empirical work either claims order matters based on interactions between code smells~\cite{liu2009smell} or tests on a single small fixture without checking AST equivalence~\cite{khrishe2016empirical}."
   - Source support: **Supported** — paper runs 6 experiments applying 3 refactoring methods in different orders, evaluating via software metrics at the end of each sequence. The 4-page conference paper is consistent with "single small fixture" framing; methodology is metric-based without an AST-equivalence check.

**Notes / fixes required:**
- `.bib` metadata: add `pages = {1--4}`, `address = {Amman, Jordan}`.

---

### `kim2011apilevel`

- **Type / title:** inproceedings — "An Empirical Investigation into the Role of API-Level Refactorings during Software Evolution" (2011)
- **Authors:** Kim, Miryung and Cai, Dongxiang and Kim, Sunghun (.bib currently says "Cai, Danny" — needs fix)
- **Venue:** Proceedings of the 33rd International Conference on Software Engineering (ICSE), pp. 151–160
- **DOI / URL:** 10.1145/1985793.1985815
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing DOI and pages)
- [ ] 2. Metadata accurate against canonical source — FAIL: middle author is "Dongxiang Cai", not "Danny" (DBLP, ACM DL)
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — ACM DL
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `introduction/introduction.tex:5` — claim: "At the same time, refactoring can be costly: it consumes developer time, introduces risk, and is frequently intertwined with other work such as feature development and bug fixing rather than occurring as isolated ``refactoring-only'' tasks \cite{murphyhill2009howrefactor,kim2011apilevel}."
   - Source support: **Loosely supported** — Kim et al. study API-level refactorings in Eclipse / JEdit / Columba and find that API-level refactorings occur during software evolution (including around bug-fix and feature work, e.g. "more frequently before than after major software releases"). This is a reasonable general reference for the "intertwined with other work" framing. The primary load-bearing citation for this sentence is Murphy-Hill et al. 2009, who explicitly characterise floss-refactoring (refactoring intertwined with other change activities); Kim et al. is a secondary citation.

**Notes / fixes required:**
- `.bib` author-name fix: change `Cai, Danny` to `Cai, Dongxiang`.
- `.bib` metadata: add `doi = {10.1145/1985793.1985815}`, `pages = {151--160}`.

---

### `liu2009smell`

- **Type / title:** inproceedings — "Facilitating software refactoring with appropriate resolution order of bad smells" (2009)
- **Authors:** Liu, Hui and Yang, Limei and Niu, Zhendong and Ma, Zhiyi and Shao, Weizhong (.bib has "Yang, Lu" — may need fix; see Notes)
- **Venue:** Proceedings of the 7th Joint Meeting of the European Software Engineering Conference and the ACM SIGSOFT Symposium on the Foundations of Software Engineering (ESEC/FSE), pp. 265–268
- **DOI / URL:** 10.1145/1595696.1595738
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing pages)
- [ ] 2. Metadata accurate against canonical source — POSSIBLE FAIL: ACM DL lists second author as "Limei Yang", .bib says "Lu Yang" — confirm against ACM
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — ACM DL
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:221` — claim: "Prior empirical work either claims order matters based on interactions between code smells~\cite{liu2009smell} or tests on a single small fixture without checking AST equivalence~\cite{khrishe2016empirical}."
   - Source support: **Supported** — abstract: "We have a bunch of bad smells, refactoring rules, and refactoring tools, but we do not know which kind of bad smells should be resolved first." Paper proposes appropriate resolution order for bad smells (i.e. order matters based on smell interactions). Direct match for thesis claim.

**Notes / fixes required:**
- `.bib` metadata: add `pages = {265--268}`.
- Confirm second author: ACM DL search returned "Limei Yang"; .bib has "Lu Yang". Could be a romanization variant of the same person — verify against ACM author profile / canonical listing before treating as a definitive correction.

---

### `marinescu2004`

- **Type / title:** inproceedings — "Detection Strategies: Metrics-Based Rules for Detecting Design Flaws" (2004)
- **Authors:** Marinescu, Radu
- **Venue:** Proceedings of the 20th IEEE International Conference on Software Maintenance (ICSM), Chicago, IL, USA, pp. 350–359
- **DOI / URL:** 10.1109/ICSM.2004.1357820
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing pages, address)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — open PDF on Academia.edu / ptidej course mirror
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:135` — claim: "Smells                & 1.0 & Marinescu 2004~\cite{marinescu2004}; Arcelli Fontana et al. 2016~\cite{arcellifontana2016falsepositives}; Moha et al. 2010~\cite{moha2010decor} & PMD violation count on touched files \\"
   - Source support: **Loosely supported** — Marinescu introduces detection strategies: metrics-based rules that combine multiple OO metrics (WMC, TCC, ATFD, etc.) to detect ~10 design flaws (God Class, God Method, Data Class, Refused Bequest, etc.). Canonical reference for rule-based smell detection as a category. The thesis uses PMD (a different rule-based tool) but the underlying technique (metric/rule combinations identifying design flaws) traces back to Marinescu. Acceptable as a "category citation" alongside the other two smell-detection references.

**Notes / fixes required:**
- `.bib` metadata: add `pages = {350--359}`, `address = {Chicago, IL, USA}`.

---

### `mens2007graph`

- **Type / title:** article — "Analysing refactoring dependencies using graph transformation" (2007)
- **Authors:** Mens, Tom and Taentzer, Gabriele and Runge, Olga
- **Venue:** Software and Systems Modeling (SoSyM), vol. 6, no. 3, pp. 269–285
- **DOI / URL:** 10.1007/s10270-006-0044-6
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing volume/number/pages)
- [x] 2. Metadata accurate against canonical source
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — preprint on ResearchGate
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `results/results.tex:221` — claim: "Mens, Taentzer and Runge~\cite{mens2007graph} show that refactorings as graph transformations with parallel independence (empty critical pairs) means every permutation reaches the same final state, and IDE refactorings are designed to have this property."
   - Source support: **Supported** — paper represents refactorings as graph transformations and applies critical-pair analysis to detect implicit dependencies/conflicts between refactorings. Keywords include "critical pair analysis" and "sequential independence". The result the thesis paraphrases (empty critical pairs ⇒ parallel-independent ⇒ commuting / order-invariant terminal state) is the standard algebraic-graph-transformation result the paper builds on. The IDE-design parenthetical is the thesis's framing, not the paper's, but the underlying claim about parallel-independence is the paper's central technical apparatus.

**Notes / fixes required:**
- `.bib` metadata: add `volume = {6}`, `number = {3}`, `pages = {269--285}`.

---

### `metricsmotivation2024`

- **Type / title:** misc — "What Were You Thinking? An LLM-Driven Large-Scale Study of Refactoring Motivations in Open-Source Projects" (2025)
- **Authors:** Robredo, Germ\'an and Gonzales, Isabel and Crespo, Yania and others (⚠️ wrong — see fix)
- **Venue:** arXiv:2509.07763
- **DOI / URL:** arXiv:2509.07763
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL
- [ ] 2. Metadata accurate against canonical source — 🚨 **FAIL: WRONG AUTHOR LIST.** Canonical arXiv 2509.07763 authors are `Robredo, Mikel and Esposito, Matteo and Palomba, Fabio and Pe\~naloza, Rafael and Lenarduzzi, Valentina`. The current `.bib` "Germán Robredo / Isabel Gonzales / Yania Crespo" does not match — Gonzales and Crespo do not appear in the arXiv author list at all.
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:191` — claim: "Studies that mine refactoring operations and repository history investigate product/process signals that correlate with refactoring activity and with developer intent, including proposals that use LLMs to infer motivations from commit artefacts \cite{whydevelopersrefactor2020,metricsmotivation2024}."
   - Source support: Supported — LLM-driven mining of refactoring motivations from OSS commits.
2. `background/background.tex:235` — claim: "and that ``quality'' may need to include contextual objectives beyond just structural metrics \cite{wang2018traceability,whydevelopersrefactor2020,metricsmotivation2024}."
   - Source support: Supported — motivations extend beyond structural quality.

**Notes / fixes required (HIGH PRIORITY):**
- **Replace author list entirely.** Canonical: `author = {Robredo, Mikel and Esposito, Matteo and Palomba, Fabio and Pe\~naloza, Rafael and Lenarduzzi, Valentina}`.
- Consider renaming citekey `metricsmotivation2024` → `metricsmotivation2025` (year is 2025).

---

### `moha2010decor`

- **Type / title:** article — "DECOR: A Method for the Specification and Detection of Code and Design Smells" (2010)
- **Authors:** Moha, Naouel and Gu\'eh\'eneuc, Yann-Ga\"el and Duchien, Laurence and Le Meur, Anne-Fran\c{c}oise (.bib currently "and others" — needs expansion)
- **Venue:** IEEE Transactions on Software Engineering, vol. 36, no. 1, pp. 20–36
- **DOI / URL:** 10.1109/TSE.2009.50
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing volume/number/pages/doi/co-authors)
- [ ] 2. Metadata accurate against canonical source — FAIL: author list elided as "and others"; canonical has 4 named authors
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — open Inria HAL and IRISA mirrors
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:51` — claim: "A prominent rule-based system is DECOR, which defines smells and design anti-patterns using a set of measurable symptoms and corresponding detection rules \cite{moha2010decor}."
   - Source support: **Supported** — abstract states DECOR is "a method that embodies and defines all the steps necessary for the specification and detection of code and design smells" via a high-level DSL that generates detection algorithms. The 4 anti-patterns (Blob, Functional Decomposition, Spaghetti Code, Swiss Army Knife) and 15 underlying code smells are specified using a consistent vocabulary. Direct match for "measurable symptoms and detection rules".
2. `background/background.tex:85` — claim: "DECOR-style detectors \cite{moha2010decor}; other smell tools \cite{paiva2017evaluation} \\"
   - Source support: **Supported** — "DECOR-style" is the standard shorthand in the smell-detection literature for the DSL-based, rule-driven approach DECOR introduces.
3. `background/background.tex:235` — claim: "it assesses refactoring success by looking at changes in quality indicators between start and end snapshots, including structural metrics and smell-based proxies \cite{harman2007pareto,moha2010decor}."
   - Source support: **Loosely supported** — DECOR is primarily about *specifying* and *detecting* smells, not about assessing refactoring success per se. The "smell-based proxies" framing is a defensible link (smell counts are a natural quality proxy) but the paper itself does not centrally argue this. Acceptable as a category citation; the load-bearing citation for refactoring-success measurement should be `harman2007pareto`.
4. `methodology/methodology.tex:135` — claim: "Smells                & 1.0 & Marinescu 2004~\cite{marinescu2004}; Arcelli Fontana et al. 2016~\cite{arcellifontana2016falsepositives}; Moha et al. 2010~\cite{moha2010decor} & PMD violation count on touched files \\"
   - Source support: **Loosely supported** — DECOR is a rule-based smell detector (PMD is a different one); citation justifies "rule-based smell detection" as a category, not PMD-specifically. Defensible alongside Marinescu and Arcelli Fontana.

**Notes / fixes required:**
- `.bib` author-list fix: replace `and others` with `and Gu\'eh\'eneuc, Yann-Ga\"el and Duchien, Laurence and Le Meur, Anne-Fran\c{c}oise`.
- `.bib` metadata: add `volume = {36}`, `number = {1}`, `pages = {20--36}`, `doi = {10.1109/TSE.2009.50}`.

---

### `nikolaidis2024metricsbased`

- **Type / title:** article — "A Metrics-Based Approach for Selecting among Various Refactoring Candidates" (2024)
- **Authors:** Nikolaidis, Nikolaos and others (⚠️ incomplete — see fix)
- **Venue:** Empirical Software Engineering, vol. 29, no. 1, art. 25
- **DOI / URL:** 10.1007/s10664-023-10412-w
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing DOI, vol/no/article-no; author list elided)
- [x] 2. Metadata accurate against canonical source (after authors expanded)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:38` — claim: "More recently, researchers have revisited the problem of selecting among large sets of refactoring candidates and investigated which code characteristics tend to correlate with positive or negative impacts in practice \cite{nikolaidis2024metricsbased}."
   - Source support: Supported — paper studies metrics-based ranking among many refactoring candidates and reports which structural-metric profiles correlate with positive/negative impact.

**Notes / fixes required:**
- Expand authors: `Nikolaidis, Nikolaos and Mittas, Nikolaos and Ampatzoglou, Apostolos and Feitosa, Daniel and Chatzigeorgiou, Alexander`.
- Add `volume = {29}`, `number = {1}`, `articleno = {25}`, `doi = {10.1007/s10664-023-10412-w}`, `publisher = {Springer}`.

---

### `paiva2017evaluation`

- **Type / title:** article — "On the Evaluation of Code Smells and Detection Tools" (2017)
- **Authors:** Paiva, Thiago and others (⚠️ first name wrong + incomplete)
- **Venue:** Journal of Software Engineering Research and Development, vol. 5, art. 7
- **DOI / URL:** 10.1186/s40411-017-0041-1
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL
- [ ] 2. Metadata accurate against canonical source — FAIL (first author given name is **Thanis**, not Thiago; co-authors missing)
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:53` — Supported (paper compares 4 detectors with highly variable precision/recall, recall 0-58%, inter-tool disagreement).
2. `background/background.tex:61` — Supported (co-cite for no-single-detector-reliable claim).
3. `background/background.tex:85` — Supported.
4. `background/background.tex:235` — Supported.

**Notes / fixes required:**
- **Fix first author given name** `Thiago` → `Thanis`.
- Expand authors: `Paiva, Thanis and Damasceno, Amanda and Figueiredo, Eduardo and Sant'Anna, Cl\'audio`.
- Add `volume = {5}`, `articleno = {7}`, `doi = {10.1186/s40411-017-0041-1}`, `publisher = {Springer}`.

---

### `pan2025swegym`

- **Type / title:** inproceedings — "Training Software Engineering Agents and Verifiers with SWE-Gym" (2025)
- **Authors:** Pan, Jiayi and Wang, Xingyao and Neubig, Graham and Jaitly, Navdeep and Ji, Heng and Suhr, Alane and Zhang, Yuandong (⚠️ last author wrong — see fix)
- **Venue:** Proceedings of the International Conference on Machine Learning (ICML)
- **DOI / URL:** arXiv:2412.21139
- **Local PDF:** —

**Checklist:**

- [x] 1. Metadata complete for entry type
- [ ] 2. Metadata accurate against canonical source — **FAIL: WRONG LAST AUTHOR.** Last author is **Yizhe Zhang** (Apple), not Yuandong Zhang (Meta FAIR — a different researcher).
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [ ] 5. Each cite site's claim is supported by the source — Loosely supported (POMDP claim only weakly applies)
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:210` — claim: "Recent work has begun to formalise software-engineering agent behaviour as a trajectory of (action, observation) pairs in a POMDP-shaped environment \cite{gautam2025refactorbench,bouzenia2025trajectories,pan2025swegym}."
   - Source support: Loosely supported — SWE-Gym provides an environment producing agent trajectories but does not itself formalise behaviour as a POMDP. POMDP framing properly belongs to `gautam2025refactorbench`.

**Notes / fixes required:**
- **Fix last author:** `Zhang, Yuandong` → `Zhang, Yizhe` (real misattribution risk; Yuandong Zhang is a different researcher).
- Consider either softening the POMDP claim at the cite site or scoping the citation to `gautam2025refactorbench` alone for the formalisation clause.

---

### `poncin2011processmining`

- **Type / title:** inproceedings — "Process Mining Software Repositories" (2011) — **NOTE: canonical title is "Repositories", not "Development Workflows"**
- **Authors:** Poncin, Wouter and Serebrenik, Alexander and van den Brand, Mark
- **Venue:** Proceedings of the 15th European Conference on Software Maintenance and Reengineering (CSMR), pp. 5–13
- **DOI / URL:** 10.1109/CSMR.2011.5 (canonical; .bib currently has 10.1109/CSMR.2011.50 — confirm)
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing pages; DOI/title need verification)
- [ ] 2. Metadata accurate against canonical source — FAIL: canonical title is "Process Mining Software Repositories" (TU/e Research Portal); .bib has "...Software Development Workflows". DOI also differs.
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — TU/e portal, IEEE Xplore, SciSpace PDF mirror
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source — see Notes; this hinges on whether the cited paper is the "Repositories" CSMR 2011 paper or a different Poncin/Serebrenik/van den Brand work
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:221` — claim: "Poncin et al.\ use Eclipse plugins to collect detailed event logs and then apply process mining techniques to infer developer workflows \cite{poncin2011processmining}."
   - Source support: **Loosely supported** — the canonical "Process Mining Software Repositories" paper (CSMR 2011) is about combining heterogeneous repositories (VCS, bug trackers, mail archives) with process mining via FRASR/ProM, not centrally about Eclipse-plugin event logs. The "Eclipse plugins to collect event logs" framing is closer to the FRASR companion work / Damevski-style telemetry. If the thesis means this specific 2011 CSMR paper, the "Eclipse plugins" phrasing is a misleading paraphrase; if it means a related Poncin/Serebrenik/van den Brand work, the citation key may be wrong. **Recommend the user verify which paper is intended.**
2. `background/background.tex:226` — claim: "The process mining and interaction analysis literature highlights that segmenting activity into tasks/episodes is not trivial \cite{poncin2011processmining,damevski2017usagesmells}."
   - Source support: **Loosely supported** — Poncin et al. discuss combining heterogeneous repositories which inherently involves activity segmentation; not a verbatim claim but consistent with the paper's stance on process-mining over noisy repository data.
3. `background/background.tex:235` — claim: "Finally, IDE logging research shows that fine-grained traces can be captured and analysed, but it also highlights challenges around the segmentation and interpretation of them \cite{poncin2011processmining,damevski2017usagesmells,foutsekh2018interactiontraces}."
   - Source support: **Loosely supported** — same caveat; Poncin focuses on repositories rather than IDE traces, so this citation site reads better with `damevski2017usagesmells` and `foutsekh2018interactiontraces` doing the heavy lifting.

**Notes / fixes required:**
- `.bib` title fix: change `"Process Mining Software Development Workflows"` → `"Process Mining Software Repositories"`.
- `.bib` DOI: confirm against TU/e portal — search returned 10.1109/CSMR.2011.5 (page 5–13 paper); the .bib has 10.1109/CSMR.2011.50 which may be a different paper in the same proceedings. Recheck on IEEE Xplore proceedings listing.
- `.bib` metadata: add `pages = {5--13}`.
- **Consider rewording the `background.tex:221` "Eclipse plugins to collect event logs" claim**, since the canonical Poncin paper is about VCS/bug-tracker/mail repositories, not IDE-event logs. The "Eclipse plugin" framing belongs to FRASR or to Damevski's later IDE-telemetry work.

---

### `wagner2015quamoco`

- **Type / title:** article — "Operationalised Product Quality Models and Assessment: The Quamoco Approach" (2015)
- **Authors:** Wagner, Stefan and Goeb, Andreas and Heinemann, Lars and Kl\"as, Michael and Lampasona, Constanza and Lochmann, Klaus and Mayr, Alois and Pl\"osch, Reinhold and Seidl, Andreas and Streit, J\"urgen and Trendowicz, Adam (.bib currently has "M\"ader, Patrick" and "Saft, Matthias" — likely wrong; see Notes)
- **Venue:** Information and Software Technology, vol. 62, pp. 101–123
- **DOI / URL:** 10.1016/j.infsof.2015.02.009; arXiv:1611.09230
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing volume/pages/doi)
- [ ] 2. Metadata accurate against canonical source — FAIL: search result shows "Mayr, Seidl" (not "Mäder, Saft") in the canonical author list. Verify against the arXiv preprint and Elsevier listing before applying a correction.
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — arXiv:1611.09230 (open-access preprint)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `methodology/methodology.tex:144` — claim: "Wagner et al.'s Quamoco framework~\cite{wagner2015quamoco} and the QMOOD quality model~\cite{bansiya2002qmood} both use this kind of layered weighted-sum aggregation, which is a defensible choice when there is no calibration data to fit the weights against."
   - Source support: **Supported** — abstract describes "a meta quality model defines the structure of operationalised quality models. The approach links abstract quality characteristics to concrete measurements through a layered aggregation mechanism". Direct match for "layered weighted-sum aggregation" framing.

**Notes / fixes required:**
- `.bib` metadata: add `volume = {62}`, `pages = {101--123}`, `doi = {10.1016/j.infsof.2015.02.009}`.
- **Verify co-author names against arXiv:1611.09230**: search returned `Mayr, A.` and `Seidl, A.` where .bib has `Mäder, Patrick` and `Saft, Matthias`. The latter two could be wrong — Patrick Mäder works on traceability (different research line) and Matthias Saft is not obviously connected. Highly likely .bib needs correction to `Mayr, Alois` and `Seidl, Andreas`.

---

### `wang2018traceability`

- **Type / title:** article — "Recommending Refactoring Solutions Based on Traceability and Code Metrics" (2018) — **NOTE: identified from cite-site context; first author is Nyamawe, not Wang. Citekey is misleading.**
- **Authors:** Nyamawe, Ally S. and Liu, Hui and Niu, Zhendong and Wang, Wentao and Niu, Nan
- **Venue:** IEEE Access, vol. 6, pp. 49460–49475
- **DOI / URL:** 10.1109/ACCESS.2018.2868990
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL: stub entry (.bib has empty title, "and others" author, no venue/journal)
- [ ] 2. Metadata accurate against canonical source — FAIL (entire entry must be replaced; see Notes)
- [x] 3. Source obtainable (DOI / arXiv / local PDF) — IEEE Access (open-access journal); homepages.uc.edu mirror
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:38` — claim: "For example, some systems extend beyond structure-based metrics by incorporating additional information such as requirements traceability, arguing that designs can be ``better'' not only in properties derivable from the code structure, but also in maintainability tasks like impact analysis \cite{wang2018traceability}."
   - Source support: **Supported** — Nyamawe et al. 2018 abstract: "compare and select among alternative refactoring solutions based on their impact on metrics of source code. However, their impact on the traceability between source code and requirements is ignored although the importance of such traceability has been well recognized." Direct match for the "extend beyond structure metrics by incorporating requirements traceability" framing.
2. `background/background.tex:110` — claim: "Traceability-based ranking methods \cite{wang2018traceability} \\"
   - Source support: **Supported** — paper explicitly proposes ranking alternative refactoring solutions by their impact on requirements traceability.
3. `background/background.tex:189` — claim: "Approaches that include traceability treat ``quality'' as partly determined by how well requirements map to code, and they are motivated by maintenance tasks like impact analysis and comprehension \cite{wang2018traceability}."
   - Source support: **Supported** — paper uses entropy-based traceability quality measures derived from requirements-to-code mappings as a quality dimension alongside coupling/cohesion.
4. `background/background.tex:199` — claim: "and broaden ``quality'' beyond structural metrics where additional artefacts exist (e.g., traceability) \cite{wang2018traceability}."
   - Source support: **Supported** — same claim as site 1.
5. `background/background.tex:235` — claim: "and that ``quality'' may need to include contextual objectives beyond just structural metrics \cite{wang2018traceability,whydevelopersrefactor2020,metricsmotivation2024}."
   - Source support: **Supported** — traceability is a contextual objective explicitly raised alongside structural metrics in the paper.

**Notes / fixes required (HIGH PRIORITY — current `.bib` entry is a stub):**
- **Replace stub entry entirely**. Canonical `.bib`:
  ```
  @article{wang2018traceability,
    author  = {Nyamawe, Ally S. and Liu, Hui and Niu, Zhendong and Wang, Wentao and Niu, Nan},
    title   = {Recommending Refactoring Solutions Based on Traceability and Code Metrics},
    journal = {IEEE Access},
    volume  = {6},
    pages   = {49460--49475},
    year    = {2018},
    doi     = {10.1109/ACCESS.2018.2868990}
  }
  ```
- **Consider renaming the citekey** to `nyamawe2018traceability` to reflect actual first author. If left as `wang2018traceability` (a defensible shortcut), at least document the choice — Wentao Wang is the 4th of 5 authors.
- All five thesis cite sites are well-supported by the canonical paper; no thesis-side rewrites are needed once the bib stub is replaced.

---

### `whydevelopersrefactor2020`

- **Type / title:** article — "Why Developers Refactor Source Code: A Mining-Based Study" (2020)
- **Authors:** Pantiuchina, Valentina and Zampetti, Fiorella and Scalabrino, Simone and others (⚠️ first author given name wrong — see fix)
- **Venue:** ACM Transactions on Software Engineering and Methodology, vol. 29, no. 4, art. 29
- **DOI / URL:** 10.1145/3408302
- **Local PDF:** —

**Checklist:**

- [ ] 1. Metadata complete for entry type — FAIL (missing DOI, article number; author list collapsed to "and others")
- [ ] 2. Metadata accurate against canonical source — 🚨 **FAIL: WRONG FIRST-AUTHOR GIVEN NAME.** First author is `Pantiuchina, Jevgenija`, not Valentina. "Valentina" is the given name of co-author Piantadosi (a name mix-up). Canonical author list: `Pantiuchina, Jevgenija and Zampetti, Fiorella and Scalabrino, Simone and Piantadosi, Valentina and Oliveto, Rocco and Bavota, Gabriele and Di Penta, Massimiliano`.
- [x] 3. Source obtainable (DOI / arXiv / local PDF)
- [x] 4. All cite sites enumerated below
- [x] 5. Each cite site's claim is supported by the source
- [x] 6. No internal contradiction across cite sites

**Cite sites:**

1. `background/background.tex:191` — claim: "Studies that mine refactoring operations and repository history investigate product/process signals that correlate with refactoring activity and with developer intent, including proposals that use LLMs to infer motivations from commit artefacts \cite{whydevelopersrefactor2020,metricsmotivation2024}."
   - Source support: Supported — paper mines repository history to study refactoring motivations/intent.
2. `background/background.tex:235` — claim: "and that ``quality'' may need to include contextual objectives beyond just structural metrics \cite{wang2018traceability,whydevelopersrefactor2020,metricsmotivation2024}."
   - Source support: Supported — paper concludes documented refactoring motivations frequently go beyond pure structural quality concerns.

**Notes / fixes required (HIGH PRIORITY):**
- **Fix first-author given name** `Pantiuchina, Valentina` → `Pantiuchina, Jevgenija`.
- Expand `and others` to full author list (7 authors above).
- Add `doi = {10.1145/3408302}`, `articleno = {29}`, `numpages = {30}` (or pages 1-30).

---

## Uncited orphans (disposition)

For each orphan: decide **keep** (intentional background reference, leave in bib), **delete** (no longer relevant), or **integrate** (find a place in the thesis that should cite it).

- [ ] `amann2016icpc` — FeedBaG: An Interaction Tracker for Visual Studio (Amann, Proksch, Nadi, ICPC 2016). IDE event-capture plugin; bib comment marks this as A2 (IDE event-capture plugins for developer-process research). Decision: ___
- [ ] `bagheri2022goodegg` — Is Refactoring Always a Good Egg? Bugs vs refactorings (Bagheri & Heged{\H{u}}s, MSR 2022). Decision: ___
- [ ] `bavota2012induce` — When Does a Refactoring Induce Bugs? (Bavota et al., SCAM 2012). Decision: ___
- [ ] `beller2019tse` — Developer Testing in the IDE: Patterns, Beliefs, and Behavior (Beller et al., TSE 2019). Decision: ___
- [ ] `croux2010influence` — Influence functions of Spearman and Kendall correlation measures (Croux & Dehon 2010). Listed under R1 in bib for Kendall tau-b methodology. Decision: ___
- [ ] `falleri2014gumtree` — Fine-Grained and Accurate Source Code Differencing (Falleri et al., ASE 2014). Bib comment notes this remains for historical reference; superseded by `falleri2024gumtree`. Decision: ___
- [ ] `kim2011api` — API-level refactorings in software evolution (Kim et al., ICSE 2011). Bib comment flags possible duplicate of `kim2011apilevel`. Decision: ___
- [ ] `kim2019iwor` — Code Transformation Issues in Move-Instance-Method Refactorings (Kim, Batory, Gligoric, IWoR 2019). Decision: ___
- [ ] `murphyhill2009floss` — Better Refactoring Tools for a Better Refactoring Strategy (Murphy-Hill et al., 2009). Bib comment notes multiple forms exist; appears superseded by `murphyhill2009howrefactor`. Decision: ___
- [ ] `negara2014icse` — Mining Fine-Grained Code Changes to Detect Unknown Change Patterns (Negara et al., ICSE 2014). Decision: ___
- [ ] `paixao2020intents` — Behind the Intents: refactoring in modern code review (Paix\~ao et al., MSR 2020). Decision: ___
- [ ] `proksch2018msr` — Enriched Event Streams (Proksch, Amann, Nadi, MSR 2018). Decision: ___
- [ ] `tsantalis2018refactoringminer` — RefactoringMiner: Tool for Automated Refactoring Detection (Tsantalis et al., ICSE 2018). Bib comment notes this remains for historical reference; superseded by `alikhanifard2025rminer` (RefactoringMiner 3.0). Decision: ___
- [ ] `tsantalis2020refactoringminer` — RefactoringMiner 2.0 (Tsantalis et al., TSE 2020). Bib comment notes this remains for historical reference; superseded by `alikhanifard2025rminer`. Decision: ___
- [ ] `wang2025forge` — Testing Refactoring Engine via Historical Bug Report driven LLM (Wang, Xu, Tan, FORGE 2025). Bib comment labels as A1 (JDT vs IntelliJ refactoring-engine divergence). Decision: ___
- [ ] `wang2025tosem` — Towards Understanding Refactoring Engine Bugs (Wang et al., TOSEM 2025). Bib comment labels as A1. Decision: ___
