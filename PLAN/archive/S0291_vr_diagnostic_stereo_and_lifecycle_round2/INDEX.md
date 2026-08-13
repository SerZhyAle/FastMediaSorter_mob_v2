# Tactical Plan: S0291 - vr-diagnostic-stereo-and-lifecycle-round2

**Strategic spec:** [`../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md`](../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md)
**Feature:** VR diagnostic round 2 implementation
**Tier:** 4 - Strategic
**Priority:** 85
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | lifecycle-reentry | - | ✅ Done | 2/2 | [PHASE_01__lifecycle-reentry.md](PHASE_01__lifecycle-reentry.md) |
| 02 | sample-provisioning | 01 | ✅ Done | 4/4 | [PHASE_02__sample-provisioning.md](PHASE_02__sample-provisioning.md) |
| 03 | render-quality | 01 | ✅ Done | 2/2 | [PHASE_03__render-quality.md](PHASE_03__render-quality.md) |
| 04 | regression-verification | 02,03 | ✅ Done | 2/2 | [PHASE_04__regression-verification.md](PHASE_04__regression-verification.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |
| 06 | lifecycle-round10-exit-and-hud-rebind | 01 | ✅ Done | 1/2+deferred | [PHASE_06__lifecycle-round10-exit-and-hud-rebind.md](PHASE_06__lifecycle-round10-exit-and-hud-rebind.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Open strategic research items are handled by the phase steps below.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [x] noLegal debug build completes.
- [x] Strategic spec moves to `BlockNeedUserTest` for Quest 3 owner verification.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. All done: flip `Status:` to `Done`, then move strategic status to `BlockNeedUserTest`.

---

## Blockers Log

- None.

---

## Change Log

- 2026-05-22 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-22 - Implemented lifecycle re-entry guard, deterministic labeled samples, static texture filtering, and validation handoff probes.
- 2026-05-30 - Reopened for round 10 (strategic §1.7). Added Phase 06: Step 06.1 (HUD re-bind on session-ready, all media types) done; Step 06.2 (exit handshake / passthrough) deferred and re-routed - see strategic §6.8 and `## Last Audit`.
- 2026-06-04 - Phase 06 closed (06.2 formally deferred). S0291 §6.2 + §6.4 fixed: SBS synthetic capped to ≤4320×2160 in setup_test_vr.ps1; decodeFilePooled now catches IllegalArgumentException (moraine_lake inBitmap incompatibility). Moving to BlockNeedUserTest.
