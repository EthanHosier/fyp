# Literature pull: metrics weighting for composite quality scoring

Generated: 2026-05-13
Scope: Composition, weight-justification, sensitivity, framing for a weighted-sum process-quality score over refactoring trajectories.

## Bucket A — Composition of quality sub-metrics

### A Hierarchical Model for Object-Oriented Design Quality Assessment (Bansiya & Davis, 2002)
- **Venue:** IEEE Transactions on Software Engineering 28(1), 4–17
- **URL:** https://doi.org/10.1109/32.979986
- **Contribution:** QMOOD defines a hierarchy that maps low-level OO design properties (encapsulation, coupling, cohesion, complexity) onto high-level quality attributes (reusability, flexibility, understandability, effectiveness, extendibility) via weighted-sum aggregation.
- **Weighting scheme used:** Expert-elicited fixed weights (+/-0.5, +/-1) per design-property to quality-attribute link, justified by empirical and anecdotal correlation.
- **Sensitivity analysis:** Light — validated by expert opinion against several commercial OO systems; weights are not formally sensitivity-tested.
- **Applicability to thesis:** Canonical Layer 1 grounding for "weighted sum of structural sub-signals yields a quality score." Cite as the axiomatic precedent for hand-picked sign/magnitude weights on coupling/cohesion/complexity terms, exactly the shape of the proposed process score.

### Operationalised product quality models and assessment: The Quamoco approach (Wagner, Goeb, Heinemann, Kläs, Lampasona, Lochmann et al., 2015)
- **Venue:** Information and Software Technology 62, 101–123 (preprint arXiv:1611.09230)
- **URL:** https://doi.org/10.1016/j.infsof.2015.02.009
- **Contribution:** Quamoco bridges abstract ISO/IEC 25010-style quality attributes and concrete measures via "product factors" with explicit aggregation weights; base model for Java/C# spans ~300 factors and ~500 measures.
- **Weighting scheme used:** Expert-workshop ranking, normalised utility functions, and weighted aggregation up the quality tree.
- **Sensitivity analysis:** Validated empirically against expert quality judgements on real systems; reports correlation between aggregated score and expert opinion (a de-facto external sensitivity check).
- **Applicability to thesis:** Strongest modern precedent for justifying a multi-level composite score grounded in ISO/IEC 25010. The "product factor" abstraction is a clean analogue for the thesis's sub-signals (CBO/LCOM/CPD/etc.).

### High Dimensional Search-Based Software Engineering: NSGA-III for Refactoring (Mkaouer, Kessentini, Bechikh, Deb & Ó Cinnéide, 2014; journal extension 2016)
- **Venue:** GECCO '14, pp. 1263–1270; journal: Empirical Software Engineering 21(6), 2503–2545 (2016)
- **URL:** https://doi.org/10.1145/2598394.2605681 (GECCO); https://doi.org/10.1007/s10664-015-9414-4 (EMSE)
- **Contribution:** Demonstrates many-objective (up to 15) optimisation of QMOOD-style quality factors for automated refactoring; argues that aggregating quality dimensions into a single weighted sum collapses information and motivates Pareto fronts.
- **Weighting scheme used:** None for selection (Pareto), but each objective is itself a weighted QMOOD attribute — i.e. weights live one level down.
- **Sensitivity analysis:** Yes — compares NSGA-III vs NSGA-II vs IBEA vs MOEA/D vs mono-objective weighted-sum on convergence and code-smell coverage. Gives empirical evidence that weighted-sum loses information at high dimensionality.
- **Applicability to thesis:** Cite as the counter-argument the thesis must address: why a weighted sum is acceptable here (low dimension, single-developer signal, interpretability) even though many-objective work prefers Pareto.

### Pareto Optimal Search Based Refactoring at the Design Level (Harman & Tratt, 2007)
- **Venue:** GECCO '07, pp. 1106–1113
- **URL:** https://doi.org/10.1145/1276958.1277176
- **Contribution:** First paper to argue that combining refactoring quality metrics via a weighted sum is brittle, and to propose Pareto-front presentation instead.
- **Weighting scheme used:** Critiques weighted-sum; uses Pareto dominance over coupling + cohesion.
- **Sensitivity analysis:** Implicit — demonstrates that small weight perturbations change the chosen refactoring sequence.
- **Applicability to thesis:** Foundational reference for the "weights matter" framing in the justification chapter. Acknowledge it as the precedent that motivates the sensitivity analysis the thesis must run.

### The SQALE Method for Evaluating Technical Debt (Letouzey, 2012)
- **Venue:** Third International Workshop on Managing Technical Debt (MTD '12) @ ICSE
- **URL:** https://doi.org/10.1109/MTD.2012.6225997
- **Contribution:** SQALE encodes quality as a hierarchy of non-compliance items each priced with a "remediation cost" (minutes), summed to a debt index. The industry-standard composite quality score (powers SonarQube).
- **Weighting scheme used:** Hand-tuned, expert-elicited remediation-cost weights per rule violation; characteristic-level aggregation by sum.
- **Sensitivity analysis:** No, in original paper — weights are fiat.
- **Applicability to thesis:** Direct precedent for the thesis's penalty terms (cleanliness gain, broken-build, hygiene) being valued in fixed weights. Cite SQALE as the canonical "weighted-sum with expert weights" composite, then justify why your weights deviate.

### MORE: A multi-objective refactoring recommendation approach (Ouni, Kessentini, Sahraoui, Inoue & Deb, 2017)
- **Venue:** Journal of Software: Evolution and Process 29(5), e1843
- **URL:** https://doi.org/10.1002/smr.1843
- **Contribution:** Three-objective refactoring (quality improvement, design-pattern introduction, smell removal) framed as SBSE problem.
- **Weighting scheme used:** Multi-objective (NSGA-II); per-objective uses summed QMOOD-style quality factors.
- **Sensitivity analysis:** Reports trade-off curves but no formal weight-perturbation study.
- **Applicability to thesis:** Useful as a Bucket-A multi-objective foil; reinforces that sub-signal composition is an open methodological choice.

## Bucket B — Weight-justification methodologies

### Axiomatic Foundation of the Analytic Hierarchy Process (Saaty, 1986)
- **Venue:** Management Science 32(7), 841–855
- **URL:** https://doi.org/10.1287/mnsc.32.7.841
- **Contribution:** Formal axioms behind AHP — pairwise comparison, eigenvector priorities, consistency index. Foundational pre-2020 reference.
- **Weighting scheme used:** Eigenvector-derived weights from pairwise judgements; defines the consistency ratio that legitimises the resulting weights.
- **Sensitivity analysis:** AHP's consistency ratio is itself a sensitivity check on judgement coherence.
- **Applicability to thesis:** Cite as the formal justification methodology if you defend your weights via pairwise expert (or self-) comparison rather than fiat. Even if not adopting AHP wholesale, it is the textbook reference for any "why these weights" argument.

### Detection Strategies: Metrics-Based Rules for Detecting Design Flaws (Marinescu, 2004)
- **Venue:** ICSM 2004, pp. 350–359
- **URL:** https://doi.org/10.1109/ICSM.2004.1357820
- **Contribution:** Establishes the convention of composing thresholded raw metrics (LOC, CYCLO, CBO, LCOM, WMC, etc.) into boolean detection strategies for code smells.
- **Weighting scheme used:** Implicit binary weights via threshold filters joined by AND/OR — i.e. the "weights" are the thresholds.
- **Sensitivity analysis:** Limited; uses statistical filters (mean + stdev) over a project corpus to calibrate thresholds.
- **Applicability to thesis:** Cite for the threshold-justification half of your weight argument (the PMD smell-count and CBO/LCOM/cognitive-complexity components all carry implicit thresholds).

### Defining and Assessing Software Quality by Quality Models (Lochmann, 2013, PhD thesis, TU München)
- **Venue:** PhD dissertation, Technische Universität München
- **URL:** https://mediatum.ub.tum.de/1107457
- **Contribution:** Provides the meta-model and aggregation operators (LOC-weighted sum, normalisation, utility functions) used inside Quamoco. Devotes a chapter to the question "how should sub-measures be aggregated?".
- **Weighting scheme used:** LOC-weighted aggregation per measure; tree-aggregation up the factor hierarchy with elicited weights.
- **Sensitivity analysis:** Discusses choice of aggregation operator and the impact on resulting quality verdicts.
- **Applicability to thesis:** The most direct methodological reference for "how to defend the aggregation operator and the per-term weights" in a composite score.

## Bucket C — Sensitivity analysis of metric weights/thresholds

### Software metrics thresholds calculation techniques to predict fault-proneness: An empirical comparison (Arcelli Fontana, Ferme, Zanoni & Yamashita, 2017)
- **Venue:** Information and Software Technology 89, 38–57
- **URL:** https://doi.org/10.1016/j.infsof.2017.04.002
- **Contribution:** Empirical comparison of six threshold-derivation techniques (Alves, Bender, Erni, etc.) on fault prediction. Shows threshold choice materially shifts downstream decisions.
- **Weighting scheme used:** N/A — focus is threshold derivation, the dual of weight justification.
- **Sensitivity analysis:** Yes, this is the centerpiece — compares techniques on AUC/precision/recall across many systems.
- **Applicability to thesis:** Direct precedent for "we tried multiple threshold/weight schemes and report robustness." Strong template for the sensitivity-analysis chapter.

### Empirical evaluation of an architectural technical debt index (Verdecchia, Malavolta, Lago & Ozkaya, 2022)
- **Venue:** PeerJ Computer Science 8, e833
- **URL:** https://doi.org/10.7717/peerj-cs.833
- **Contribution:** Empirically evaluates the ATDx data-driven debt index against SonarQube's predefined remediation costs on Apache and ONAP, exposing the fragility of fiat weights.
- **Weighting scheme used:** Compares predefined-weight (SonarQube SQALE) vs clustering-derived severity (ATDx).
- **Sensitivity analysis:** Yes — explicit head-to-head on portfolio-level rankings.
- **Applicability to thesis:** Modern (2022) precedent making exactly the thesis's argument: predefined composite-score weights need empirical justification. Cite to motivate the sensitivity-analysis chapter.

### Improving Code: The (Mis)Perception of Quality Metrics (Pantiuchina, Lanza & Bavota, 2018; follow-ups to 2020)
- **Venue:** ICSME 2018, pp. 80–91
- **URL:** https://doi.org/10.1109/ICSME.2018.00017
- **Contribution:** Shows that the structural metrics developers say they refactor for (cohesion/coupling/complexity) frequently fail to detect the change after a "quality-improving" commit — i.e. metric responsiveness is weak.
- **Weighting scheme used:** N/A.
- **Sensitivity analysis:** Indirect — sensitivity of metric *outputs* to refactoring intent.
- **Applicability to thesis:** Critical caveat for the thesis: justifies why penalty terms (build broken, tests skipped) and process-level signals matter alongside structural metrics — structural metrics alone are noisy.

## Bucket D — Loss-aversion / disruption-vs-improvement

### An Empirical Study of Cohesion and Coupling: Balancing Optimization and Disruption (Paixão, Harman, Zhang & Yu, 2018)
- **Venue:** IEEE Transactions on Evolutionary Computation 22(3), 394–414
- **URL:** https://doi.org/10.1109/TEVC.2017.2691281
- **Contribution:** Across 233 sequential releases of 10 systems, shows that developers leave ~25% potential cohesion/coupling improvement on the table to avoid the ~57% structural disruption that optimum solutions demand.
- **Weighting scheme used:** Bi-objective: quality improvement vs disruption magnitude.
- **Sensitivity analysis:** Yes — trade-off front explored systematically.
- **Applicability to thesis:** Empirical anchor for the "build broken" penalty term — quantifies how aversion to disruption is a first-class signal in developer behaviour, motivating $W_\text{BROKEN}$ as the largest negative weight.

### Understanding the impact of refactoring on smells: a longitudinal study of 23 software projects (Cedrim, Garcia, Mongiovi, Gheyi, Sousa, Ribeiro, Chávez & Mens, 2017)
- **Venue:** FSE 2017, pp. 465–475
- **URL:** https://doi.org/10.1145/3106237.3106259
- **Contribution:** ~57% of refactorings are neutral on smells; some (e.g. Pull Up Method) introduce new smells in 28% of applications.
- **Weighting scheme used:** N/A.
- **Sensitivity analysis:** No — descriptive longitudinal study.
- **Applicability to thesis:** Direct empirical justification for treating $W_\text{GAIN}$ as a *signed* term — refactoring often makes things worse, so the score must let cleanliness regressions cost points (negative `cleanlinessGain · W_GAIN`) rather than only rewarding gains.

### How We Refactor, and How We Know It (Murphy-Hill, Parnin & Black, 2012)
- **Venue:** IEEE Transactions on Software Engineering 38(1), 5–18 (2012)
- **URL:** https://doi.org/10.1109/TSE.2011.41
- **Contribution:** Eclipse-telemetry study of professional refactoring practice (~700 sessions across the Mylyn corpus + lab study). Introduces the *batch refactoring* concept and quantifies it: refactorings executed within 60 s of each other form a batch, and ~40 % of tool-invoked refactorings cluster this way. Provides the canonical empirical anchor for treating a composite, rather than an individual step, as the unit at which test cadence should be assessed.
- **Weighting scheme used:** N/A — empirical telemetry study.
- **Sensitivity analysis:** No (descriptive).
- **Applicability to thesis:** Directly backs the 60 s composite window used as the denominator for `W_SKIP_TESTS`. Cited at the constant `COMPOSITE_GAP_MS` and in the penalty-normalisation section. Provides the *empirical* counterweight to Fowler 2018's per-step prescription: the textbook says "test after every micro-step", but professional practice batches and tests at the boundary; the metric scores against the latter for fairness.

### When and Why Your Code Starts to Smell Bad (Tufano, Palomba, Bavota, Oliveto, Di Penta, De Lucia & Poshyvanyk, 2017; journal 2020)
- **Venue:** IEEE Transactions on Software Engineering 43(11), 1063–1088 (2017); extended in TSE 2020
- **URL:** https://doi.org/10.1109/TSE.2017.2653105
- **Contribution:** Longitudinal evidence that most code smells are introduced when files are first written, not gradually, and that refactoring is not the primary smell-removal mechanism.
- **Weighting scheme used:** N/A.
- **Sensitivity analysis:** No.
- **Applicability to thesis:** Framing reference for why a *process* score (i.e. trajectory-aware) is better than a snapshot score — smell history matters.

## Closest-related work

The strongest related-work anchors for the thesis's framing chapter are:
1. **Paixão et al. 2017 (TEVC)** — the only paper that formally treats developer behaviour as a balance between structural improvement and trajectory disruption. This is the thesis's intellectual neighbour.
2. **Quamoco / Wagner 2015** — the canonical operationalised weighted-sum composite over ISO/IEC 25010 sub-signals. Use as the structural blueprint.
3. **ATDx empirical evaluation 2022 (PeerJ-CS)** — the most recent paper that empirically critiques predefined composite-score weights; use to motivate the sensitivity chapter.

## Investigated but excluded

- **"Yadav AHP for software metrics"** — could not verify a single canonical citation matching the framework prompt (multiple authors named Yadav apply AHP in software contexts; none stand out as load-bearing). Recommend dropping in favour of Saaty 1986 + a direct AHP-in-SE application of your choice.
- **Tahir et al. on sensitivity analysis of metric thresholds** — Tahir's body of work (Han & Tahir 2022 on code-smell reviews; Tahir et al. 2020 on developer context) is empirical perception research, not weight-sensitivity analysis. Replaced with Arcelli Fontana et al. 2017 (IST) which is a closer fit.
- **Foucault et al.** — verified as ownership-metric work; tangential to weight justification. Excluded.
- **Plösch (standalone)** — Plösch's primary contribution is inside Quamoco/Lochmann. Excluded as duplicate.

## Suggested BibTeX-ready references

```bibtex
@article{bansiya2002qmood,
  author = {Bansiya, Jagdish and Davis, Carl G.},
  title = {A Hierarchical Model for Object-Oriented Design Quality Assessment},
  journal = {IEEE Transactions on Software Engineering},
  volume = {28}, number = {1}, pages = {4--17}, year = {2002},
  doi = {10.1109/32.979986}
}

@article{wagner2015quamoco,
  author = {Wagner, Stefan and Goeb, Andreas and Heinemann, Lars and Kl{\"a}s, Michael and Lampasona, Constanza and Lochmann, Klaus and others},
  title = {Operationalised Product Quality Models and Assessment: The Quamoco Approach},
  journal = {Information and Software Technology},
  volume = {62}, pages = {101--123}, year = {2015},
  doi = {10.1016/j.infsof.2015.02.009}
}

@article{mkaouer2016manyobjective,
  author = {Mkaouer, Wiem and Kessentini, Marouane and Shaout, Adnan and Koc, Patrice and Liu, Kevin and Deb, Kalyanmoy and {\'O} Cinn{\'e}ide, Mel},
  title = {On the Use of Many Quality Attributes for Software Refactoring: A Many-Objective Search-Based Software Engineering Approach},
  journal = {Empirical Software Engineering},
  volume = {21}, number = {6}, pages = {2503--2545}, year = {2016},
  doi = {10.1007/s10664-015-9414-4}
}

@inproceedings{harman2007pareto,
  author = {Harman, Mark and Tratt, Laurence},
  title = {Pareto Optimal Search Based Refactoring at the Design Level},
  booktitle = {GECCO '07}, pages = {1106--1113}, year = {2007},
  doi = {10.1145/1276958.1277176}
}

@inproceedings{letouzey2012sqale,
  author = {Letouzey, Jean-Louis},
  title = {The SQALE Method for Evaluating Technical Debt},
  booktitle = {3rd Int. Workshop on Managing Technical Debt (MTD '12)},
  pages = {31--36}, year = {2012},
  doi = {10.1109/MTD.2012.6225997}
}

@article{ouni2017more,
  author = {Ouni, Ali and Kessentini, Marouane and Sahraoui, Houari and Inoue, Katsuro and Deb, Kalyanmoy},
  title = {{MORE}: A Multi-Objective Refactoring Recommendation Approach to Introducing Design Patterns and Fixing Code Smells},
  journal = {Journal of Software: Evolution and Process},
  volume = {29}, number = {5}, pages = {e1843}, year = {2017},
  doi = {10.1002/smr.1843}
}

@article{saaty1986axiomatic,
  author = {Saaty, Thomas L.},
  title = {Axiomatic Foundation of the Analytic Hierarchy Process},
  journal = {Management Science},
  volume = {32}, number = {7}, pages = {841--855}, year = {1986},
  doi = {10.1287/mnsc.32.7.841}
}

@inproceedings{marinescu2004detection,
  author = {Marinescu, Radu},
  title = {Detection Strategies: Metrics-Based Rules for Detecting Design Flaws},
  booktitle = {ICSM 2004}, pages = {350--359}, year = {2004},
  doi = {10.1109/ICSM.2004.1357820}
}

@phdthesis{lochmann2013thesis,
  author = {Lochmann, Klaus},
  title = {Defining and Assessing Software Quality by Quality Models},
  school = {Technische Universit{\"a}t M{\"u}nchen}, year = {2013},
  url = {https://mediatum.ub.tum.de/1107457}
}

@article{fontana2017thresholds,
  author = {Arcelli Fontana, Francesca and Ferme, Vincenzo and Zanoni, Marco and Yamashita, Aiko},
  title = {Software Metrics Thresholds Calculation Techniques to Predict Fault-Proneness: An Empirical Comparison},
  journal = {Information and Software Technology},
  volume = {89}, pages = {38--57}, year = {2017},
  doi = {10.1016/j.infsof.2017.04.002}
}

@article{verdecchia2022atdx,
  author = {Verdecchia, Roberto and Malavolta, Ivano and Lago, Patricia and Ozkaya, Ipek},
  title = {Empirical Evaluation of an Architectural Technical Debt Index in the Context of the Apache and ONAP Ecosystems},
  journal = {PeerJ Computer Science},
  volume = {8}, pages = {e833}, year = {2022},
  doi = {10.7717/peerj-cs.833}
}

@inproceedings{pantiuchina2018misperception,
  author = {Pantiuchina, Jevgenija and Lanza, Michele and Bavota, Gabriele},
  title = {Improving Code: The (Mis)Perception of Quality Metrics},
  booktitle = {ICSME 2018}, pages = {80--91}, year = {2018},
  doi = {10.1109/ICSME.2018.00017}
}

@article{paixao2018balancing,
  author = {Paix{\~a}o, Matheus and Harman, Mark and Zhang, Yuanyuan and Yu, Yijun},
  title = {An Empirical Study of Cohesion and Coupling: Balancing Optimization and Disruption},
  journal = {IEEE Transactions on Evolutionary Computation},
  volume = {22}, number = {3}, pages = {394--414}, year = {2018},
  doi = {10.1109/TEVC.2017.2691281}
}

@inproceedings{cedrim2017impact,
  author = {Cedrim, Diego and Garcia, Alessandro and Mongiovi, Melina and Gheyi, Rohit and Sousa, Leonardo and Ribeiro, Marcio and Ch{\'a}vez, Anderson and Mens, Tom},
  title = {Understanding the Impact of Refactoring on Smells: A Longitudinal Study of 23 Software Projects},
  booktitle = {ESEC/FSE 2017}, pages = {465--475}, year = {2017},
  doi = {10.1145/3106237.3106259}
}

@article{tufano2017whenwhy,
  author = {Tufano, Michele and Palomba, Fabio and Bavota, Gabriele and Oliveto, Rocco and Di Penta, Massimiliano and De Lucia, Andrea and Poshyvanyk, Denys},
  title = {When and Why Your Code Starts to Smell Bad (and Whether the Smells Go Away)},
  journal = {IEEE Transactions on Software Engineering},
  volume = {43}, number = {11}, pages = {1063--1088}, year = {2017},
  doi = {10.1109/TSE.2017.2653105}
}
```

---

# Proposed process-score metric: composition, weights, and justifications

This section is the thesis-ready synthesis of the literature above onto the
proposed score. The structure is the two-layer split from
`PLAN-metric-justification.md`: a literature-backed **cleanliness layer**
and a novel-but-axiomatically-justified **process layer**. Each row links
the choice to the papers that legitimise it; sensitivity analysis closes
the loop on the magnitudes.

## Layer 1 — Cleanliness sub-score (literature-backed composition)

The cleanliness composite aggregates six range-normalised sub-signals
(cohesion, coupling, smells, duplication, readability, cognitive
complexity) into a single per-checkpoint scalar. The composition
pattern — weighted aggregation of structural sub-signals up an
ISO/IEC 25010-style tree — follows **Quamoco (Wagner et al. 2015)**
as its structural blueprint, with **QMOOD (Bansiya & Davis 2002)** as
the older OO-design precedent. The cleanliness composite is *not
novel*; the contribution is applying it per-checkpoint along a
trajectory, with trajectory-touched-set aggregation localising the
signal to the developer's working footprint.

**See [`RESEARCH-cleanliness-metrics.md`](RESEARCH-cleanliness-metrics.md)
for per-sub-signal methodology**: raw-signal selection, aggregation
choice (mean over trajectory-touched set, touched-file rate for
duplication, line-count-weighted mean for readability), citation
trail, and limitations. The within-composite weights are uniform
(`1/6` each) under Laplace's principle of insufficient reason in the
absence of an in-domain calibration corpus — the same defence used
within the readability blend itself. Sensitivity analysis in
Experiment 1 (`PLAN-experiment.md`) tests the robustness of both the
within-composite weights and each sub-signal's aggregation choice.

**Caveat from Pantiuchina et al. 2018:** structural metrics often fail to
detect developer-claimed quality improvements. This is *the reason* the
process layer adds non-structural penalty terms (build broken, tests
skipped) — structural cleanliness alone is too noisy to evaluate process.

→ **See:** [`RESEARCH-cleanliness-metrics.md`](RESEARCH-cleanliness-metrics.md)
(per-sub-signal methodology); Wagner 2015 (Quamoco); Bansiya & Davis
2002 (QMOOD); Pantiuchina et al. 2018 (limitations).

## Layer 2 — Process score (novel composition, axiomatically justified weights)

The process score is a weighted sum of five terms evaluated **over the
trajectory**, not per-checkpoint. To our knowledge no prior work weights
developer-trajectory-level signals into a single process-quality score —
the lit review supports this gap (endpoint-only or single-step
formulations dominate: Harman & Tratt 2007, Mkaouer 2016, ReSynth,
Refactoring Navigator). The composition follows the **weighted-sum-with-
expert-weights** precedent set by **SQALE (Letouzey 2012)** — the
industrial standard composite that powers SonarQube — replacing
expert-elicited remediation costs with explicit axioms.

### Why weighted sum and not Pareto (defending against Harman & Tratt 2007 / Mkaouer 2016)

Harman & Tratt 2007 and Mkaouer 2016 argue that weighted-sum collapses
information at high dimensionality and prefer Pareto fronts. Three
reasons that critique does not apply here:

1. **Low dimensionality.** Five process-layer terms, not the 15+ that
   motivated NSGA-III.
2. **Single-developer consumer.** Pareto fronts presume a recommendation
   system selecting from options; the thesis presents a single
   interpretable score to one developer.
3. **Interpretability requirement.** A scalar score lets the dashboard
   show a single line per trajectory and compute concrete divergence
   magnitudes between user and counterfactual trajectories. Pareto fronts
   would force the user to interpret dominance relations.

### Weight derivation: axiom-constrained, not literature-fitted

The literature legitimises the *structure* (weighted-sum composite over
trajectory signals, by analogy to SQALE / Quamoco / QMOOD) and the
*direction* of each term (Paixão 2017 for disruption-aversion; Cedrim
2017 for "many refactorings make things worse"). It does **not** uniquely
imply specific magnitudes like 28, 50, or 21.

The thesis therefore presents the weights as *one instantiation of a
larger family* satisfying four ordering constraints. The constants are
heuristic priors, defended by axioms and checked empirically by the
sensitivity analysis and ablation experiments in
`PLAN-experiment.md`.

**Constraint C1 — Build-broken dominance.** A trajectory with a broken
build must not outscore an otherwise equivalent build-passing
trajectory because of a small cleanliness gain. Formally:
$W_\text{BROKEN} \geq \alpha \cdot W_\text{GAIN}$ for a typical
cleanliness-gain magnitude $\alpha$. *Anti-gaming axiom.* Literature
analogue: Paixão et al. 2018 — disruption is a real cost developers
attend to.

**Constraint C2 — Process-hygiene severity ordering.** Process-hygiene
signals are ordered by safety-criticality: test execution >
IDE-correctness guarantee > commit cadence. Formally:
$W_\text{BROKEN} > W_\text{SKIP\_TESTS} > W_\text{MANUAL\_IDE} >
W_\text{COMMIT\_GAP} > 0$. Build correctness is the headline disruption
term; running tests corroborates it; using the IDE's mechanically-safe
refactoring instead of a hand-edit is a correctness-guarantee axis
(structural transformations the IDE applies are AST-preserving by
construction); committing frequently is review-and-rollback ergonomics.
The 28:14:11:7 instantiation gives each adjacent pair a roughly 2:1 or
~3:2 step, mirroring SQALE's "blocker:critical:major:minor" severity
gradient. Literature analogue: SQALE / Letouzey 2012.

**Constraint C3 — Dominant positive.** Sustained cleanliness
improvement is the dominant signed term: $W_\text{GAIN}$ is large
enough that a maximally-clean trajectory reaches the score's
$\text{BASELINE} + W_\text{GAIN} = 100$ ceiling, and `cleanlinessGain`
is itself signed — a trajectory ending dirtier than it started
contributes $W_\text{GAIN} \cdot \text{(cleanT − cleanliness0)} < 0$,
so end-state regression is already on the books without a separate
term. Literature analogue: Cedrim et al. 2017 (refactoring often
neutral or worse) motivates the asymmetric framing.

**Current instantiation:**

| Weight             | Magnitude | Constraints it satisfies |
|--------------------|-----------|--------------------------|
| W_GAIN             | 50        | dominant positive (C3); signed, so end-state regression already costs ≤ −50 |
| W_BROKEN           | 28        | C1 (dominates typical gain)        |
| W_SKIP_TESTS       | 14        | C2 (half W_BROKEN)                 |
| W_MANUAL_IDE       | 11        | C2 (≈¾ W_SKIP_TESTS)               |
| W_COMMIT_GAP       | 7         | C2 (half W_SKIP_TESTS)             |

Two terms are deliberately absent:
- **Smells** as a standalone process-score term — the PMD smell delta
  already feeds the cleanliness composite that drives $W_\text{GAIN}$.
  Surfacing it again would double-count.
- **Intermediate degradation** (running-peak cleanliness dip integral)
  — the only thing it captured uniquely was "structural quality
  regressed mid-trajectory but recovered by the end". `W_BROKEN`
  handles build/test regressions, signed `W_GAIN` handles end-state
  regressions; the residual case (cleanliness dipped and recovered)
  is also already de-emphasised by Cedrim 2017's "interim dips are
  normal during a refactor" framing. Drop simplifies the ablation
  table without losing coverage of the actual regression axes.

Crucially, **any other instantiation satisfying C1–C3 would be an
equally defensible thesis.** What the thesis defends is the
*ranking-stability of its conclusions* under perturbation of these
magnitudes (the sensitivity-analysis result), and the *non-redundancy*
of each term (the ablation result).

### Penalty normalisation (per-step vs per-trajectory)

A reasonable concern: if W_BROKEN fires per checkpoint, longer
refactoring sessions would accumulate more broken-state penalties just
by virtue of having more checkpoints. The implementation forestalls
this by pro-rating each penalty against a denominator that scales with
trajectory length:
- **W_BROKEN** is applied as `brokenMs / elapsedMs` — the fraction of
  wall-clock time the trajectory spent in a broken state (build red or
  tests failed, excluding skipped). Using elapsed *time* rather than
  *checkpoint count* matters because manual-edit-heavy sessions
  generate many small checkpoints that would dilute the count-based
  fraction even when the broken stretches are long. Alt paths borrow
  the user's time slice at each step (`altStepDurations` in
  `DerivedMetricsRunner`) so an alt that avoids a long broken stretch
  correctly out-scores the user.
- **W_SKIP_TESTS** is applied as `missedComposites / totalComposites`
  (Laplace-smoothed). A *composite* is a run of consecutive
  refactoring steps whose inter-step gap is ≤ 60 s, following
  Murphy-Hill, Parnin & Black 2012 (TSE), who report ~40 % of
  tool-invoked refactorings cluster within 60 s of each other on
  their Eclipse-telemetry corpus. A composite is "tested" iff any
  `TEST_RUN_FINISHED` event lands in the half-open interval
  `[composite.firstStep.ts, nextComposite.firstStep.ts)`. Switching
  the denominator from per-step to per-composite avoids penalising
  the empirically-common pattern of testing once at the batch
  boundary rather than after every micro-step — Fowler 2018's
  per-step prescription remains the textbook ideal, but
  Murphy-Hill 2012 and Negara/Vakilian 2013 establish that the
  composite is the unit professional developers actually reason
  about.
- **W_MANUAL_IDE** is applied as a fraction of its full weight,
  scaled by `1 / refactoringStepsCount` per step (Laplace-smoothed).
  Per-step is the natural denominator here because the IDE-vs-manual
  choice is made at each individual refactor invocation, not at the
  batch boundary.
- **W_COMMIT_GAP** fires a fixed −7 each time the running count of
  green refactor checkpoints since the last commit crosses
  `MIN_COMMIT_GAP = 6` (the threshold inherited from the hygiene
  detector), with the score's overall [0, 100] clamp acting as the
  ceiling.

These conventions are documented at the call sites in
`DerivedMetricsRunner.advanceMainStep` / `.advanceAltStep`. The
methodology chapter should state them explicitly so the marker
doesn't have to read code to confirm them.

→ **See:** Paixão et al. 2018 (TEVC) for disruption framing; Cedrim
et al. 2017 (FSE) for refactoring-degradation prevalence;
Murphy-Hill, Parnin & Black 2012 (TSE) for the 60 s composite
window; Letouzey 2012 (SQALE) for fiat-weight composite precedent;
Wagner 2015 (Quamoco) for structural blueprint.

## Sensitivity analysis + ablation (closes the loop on fiat weights)

Two empirical defences sit alongside the axiomatic ones — the protocol
and operational detail live in **`PLAN-experiment.md`**. In summary:

- **Sensitivity analysis** — perturb each weight independently by
  ±50% on N fixture trajectories and measure whether the top-K
  divergence-point ranking remains stable (Kendall's τ, top-K hit
  rate). Motivated by Verdecchia et al. 2022 (PeerJ-CS) which showed
  predefined composite-score weights produce fragile portfolio
  rankings vs data-driven alternatives. Methodological template from
  Arcelli Fontana et al. 2017 (IST).
- **Ablation** — run the pipeline with subsets of the process layer
  (cleanliness-only, +broken, +hygiene triplet (skipTests / manualIde /
  commitGap), full) and compare divergence-point output. Each term must
  be demonstrably non-redundant; a term that doesn't change the output
  gets deleted from the score (and that deletion is itself a stronger
  thesis result).

Together these convert "I picked weights and cited papers" into:
*structure literature-backed → terms axiomatically constrained →
magnitudes empirically stable under perturbation → terms empirically
non-redundant.*

→ **See:** Verdecchia et al. 2022 (motivation for sensitivity); Arcelli
Fontana et al. 2017 (methodological template); `PLAN-experiment.md`
(protocol and metrics).

## Defending the contribution as novel

The lit review supports the gap claim:

> *No prior work weights developer-trajectory-level signals (build
> state, test state, cleanliness trajectory, smell-ledger delta) into
> a single composite process-quality score. Existing composite quality
> scores (QMOOD, Quamoco, SQALE) evaluate snapshots, not trajectories;
> existing trajectory-aware work (Paixão 2017, Cedrim 2017, Tufano 2017)
> is descriptive empirical analysis, not a constructive metric.*

This is the strongest framing-chapter claim the lit review supports.
Pair with **Tufano et al. 2017** as the supporting reference for *why*
a trajectory-aware score is needed at all (smells are introduced at
file creation, not gradually — snapshot scores can't see this).

→ **See:** Paixão et al. 2017; Cedrim et al. 2017; Tufano et al. 2017
(empirical evidence trajectory matters); Harman & Tratt 2007; Mkaouer
2016 (counter-arguments to address).

## At-a-glance citation map

| Choice in the score                   | Primary citation                  | Secondary / context                              |
|---------------------------------------|-----------------------------------|--------------------------------------------------|
| Layer 1 composition pattern           | Wagner et al. 2015 (Quamoco)      | Bansiya & Davis 2002 (QMOOD)                     |
| CBO, LCOM raw signals                 | Chidamber & Kemerer 1994          | Quamoco product factors                          |
| Duplication as smell                  | Fowler; Letouzey 2012 (SQALE)     | —                                                |
| Readability                           | Buse & Weimer 2010                | Scalabrino et al. 2018                            |
| Cognitive complexity                  | Campbell 2018 (SonarSource)       | —                                                |
| Smell count + thresholding            | Marinescu 2004                    | Arcelli Fontana et al. 2017                       |
| Process-layer composition (weighted sum + fiat weights) | Letouzey 2012 (SQALE)             | Quamoco                                          |
| W_BROKEN axiom                        | Paixão et al. 2017                | —                                                |
| W_GAIN dominant positive (signed)      | Paixão et al. 2017                | Cedrim et al. 2017                               |
| W_SKIP_TESTS composite-window (60 s)   | Murphy-Hill, Parnin & Black 2012  | Negara, Vakilian et al. 2013 (semantic composite) |
| Non-structural penalty terms (caveat) | Pantiuchina et al. 2018           | —                                                |
| "Why weighted sum, not Pareto"        | Harman & Tratt 2007 (counter)     | Mkaouer 2016 (counter)                           |
| Sensitivity-analysis chapter motivation | Verdecchia et al. 2022 (ATDx)     | Arcelli Fontana et al. 2017 (template)           |
| Trajectory framing                    | Tufano et al. 2017                | Paixão 2017; Cedrim 2017                         |

