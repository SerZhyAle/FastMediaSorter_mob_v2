# Tactical Plan: S0069 — bugfix-atomic-copy-temp-file-missing

**Strategic spec:** [`../S0069_bugfix-atomic-copy-temp-file-missing.md`](../S0069_bugfix-atomic-copy-temp-file-missing.md)
**Feature:** Atomic SMB copy temp lifecycle — cancel vs fail separation
**Tier:** 3 — Moderate
**Priority:** 75
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | scope-and-ownership | — | ✅ Done | 4/4 | [PHASE_01__scope-and-ownership.md](PHASE_01__scope-and-ownership.md) |
| 02 | smb-cancellation-propagation | 01 | ✅ Done | 4/4 | [PHASE_02__smb-cancellation-propagation.md](PHASE_02__smb-cancellation-propagation.md) |
| 03 | atomic-result-contract | 01, 02 | ✅ Done | 4/4 | [PHASE_03__atomic-result-contract.md](PHASE_03__atomic-result-contract.md) |
| 04 | postcondition-and-cleanup | 03 | ✅ Done | 4/4 | [PHASE_04__postcondition-and-cleanup.md](PHASE_04__postcondition-and-cleanup.md) |
| 05 | regression-entrypoints-tests | 02, 03, 04 | ✅ Done | 4/4 | [PHASE_05__regression-entrypoints-tests.md](PHASE_05__regression-entrypoints-tests.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] Approval gate resolved by explicit user invocation `/spec-tech S0069` on 2026-05-03. This tactical bundle advances the strategic spec from `Draft` to `Tactical`.
- [x] **Research §6.1 moved into execution.** Phase 01 Step 01.3 owns the exact temp-deleter audit, so this is no longer a start gate.
- [x] **Research §6.2 moved into execution.** Phases 03–05 own the cancel-path vs success-path split, so this is no longer a start gate.
- [x] **Research §6.3 moved into execution.** Phase 01 Steps 01.2 and 01.4 own the SMB-only vs shared-wrapper decision, so this is no longer a start gate.

**Stop condition:** If Phase 01 proves that the same `temp-missing-invariant` is reproducible on non-SMB delegates or is caused by a shared cross-protocol temp owner (`UnifiedFileOperationHandler`, `TempFileManager`, or a non-SMB `AtomicFileOperationStrategy` wrapper), stop after Phase 01, set the tactical index row to `⛔ Blocked`, keep the strategic ticket at `Tactical`, and spin a follow-up strategic spec instead of broadening S0069 inside the fixed-release branch.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] No `docs/FEATURES*` update was added; strategic §8 remains true.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CATALOG/app_v2.md` regenerated.
- [ ] `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategyTest"` passes.
- [ ] `./gradlew.bat :app_v2:compileStandardDebugKotlin` passes.
- [ ] `/spec-check S0069` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [ ] Targeted reproducer checks pass: user-cancelled SMB copy does not log `Unexpected error during atomic copy`; successful SMB copy reaches final rename; `Temp file doesn't exist after copy!` no longer appears in the reproducer path.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0069`.

---

## Blockers Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-03 — Phase 05 narrow unit-test gate blocked by unrelated unit-test source compilation errors in `SendResourcesToWatchUseCaseTest.kt`; S0069 transfer slice compiles and both UI cancel entrypoints were audited clean.
- 2026-05-03 — External unit-test compile blocker resolved by adding the missing fake repository methods in `SendResourcesToWatchUseCaseTest.kt`; the narrow unit-test gate and Kotlin compile gate both pass.

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech` from explicit user invocation.
