# Tactical Plan: S0271 - Truth Drift Detection (docs vs Gradle)

**Strategic spec:** [`../S0271_truth_drift_detection.md`](../S0271_truth_drift_detection.md)
**Feature:** Documentation-vs-Gradle drift checker
**Tier:** 2 - Moderate (engineering hygiene)
**Priority:** 55
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | decisions-and-contracts | - | ✅ Done | 4/4 | [PHASE_01__decisions-and-contracts.md](PHASE_01__decisions-and-contracts.md) |
| 02 | gradle-source-parser | 01 | ✅ Done | 3/3 | [PHASE_02__gradle-source-parser.md](PHASE_02__gradle-source-parser.md) |
| 03 | pin-manifest-and-doc-parser | 01, 02 | ✅ Done | 4/4 | [PHASE_03__pin-manifest-and-doc-parser.md](PHASE_03__pin-manifest-and-doc-parser.md) |
| 04 | comparator-output-cli | 02, 03 | ✅ Done | 4/4 | [PHASE_04__comparator-output-cli.md](PHASE_04__comparator-output-cli.md) |
| 05 | test-harness-and-integration | 02, 03, 04 | ✅ Done | 4/4 | [PHASE_05__test-harness-and-integration.md](PHASE_05__test-harness-and-integration.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 carries six Research items. Resolution map:

- §6.1 (wear/ coverage) - explicit Non-goal per strategic §2; **not a blocker**, deferred to follow-up.
- §6.2 (PR-gate placement) - strategic §11 criterion #8 accepts "recorded as explicit follow-up"; **not a blocker** for tactical implementation.
- §6.3 (manifest format A/B/C) - **resolved inside Phase 01**, Step 01.1.
- §6.4 (missing-pin semantics) - **resolved inside Phase 01**, Step 01.2.
- §6.5 (range-declaration semantics) - **resolved inside Phase 01**, Step 01.3.
- §6.6 (multi-mention semantics) - **resolved inside Phase 01**, Step 01.4.

No external blocker prevents Phase 01 from starting.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **skipped**: strategic §8 explicitly states "Не затрагивает".
- [x] `dev/CHANGELOG.md` has an entry for every modified or created file.
- [x] `dev/CATALOG/<module>.jsonl` - **skipped**: no Kotlin code is modified.
- [x] `scripts/check-doc-vs-gradle.ps1` exists, runnable under `pwsh -NoProfile`, no external module dependencies.
- [x] Checker run on current `main`-branch repo state produces ≥1 FAIL (strategic §11 criterion #2) and exits with code `1`.
- [x] Hand-rolled test harness under `scripts/doc-drift.tests/` runs green (exit `0`, all asserts pass).
- [x] `/spec-check S0271` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0271`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-20 - All phases completed. Smoke exit 1 (drift present, expected). Tests exit 0.
