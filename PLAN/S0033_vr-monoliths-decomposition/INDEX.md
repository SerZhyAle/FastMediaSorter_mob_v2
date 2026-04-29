# Tactical Plan: S0033 — vr-monoliths-decomposition

**Strategic spec:** [`../S0033_vr-monoliths-decomposition.md`](../S0033_vr-monoliths-decomposition.md)
**Feature:** Decompose `OpenXrNative.cpp` (3487 LOC) and `VrPlayerActivity.kt` (1956 LOC) so both files satisfy CLAUDE.md rule 2 (≤ 1000 LOC) and unblock S0024 Phase 02.
**Tier:** 3 — Moderate
**Priority:** 60
**Status:** Not started
**Phases:** 0 / 6 done
**Last updated:** 2026-04-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | cpp-logging-and-ctx-header | — | ⬜ Not started | 0/5 | [PHASE_01__cpp-logging-and-ctx-header.md](PHASE_01__cpp-logging-and-ctx-header.md) |
| 02 | cpp-lifecycle | 01 | ⬜ Not started | 0/4 | [PHASE_02__cpp-lifecycle.md](PHASE_02__cpp-lifecycle.md) |
| 03 | cpp-swapchain-and-frame | 01, 02 | ⬜ Not started | 0/5 | [PHASE_03__cpp-swapchain-and-frame.md](PHASE_03__cpp-swapchain-and-frame.md) |
| 04 | cpp-input-and-hand | 01, 02, 03 | ⬜ Not started | 0/5 | [PHASE_04__cpp-input-and-hand.md](PHASE_04__cpp-input-and-hand.md) |
| 05 | activity-helpers | 01..04 | ⬜ Not started | 0/6 | [PHASE_05__activity-helpers.md](PHASE_05__activity-helpers.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

_None — strategic §6 research items are either Resolved (CMake org, test coverage) or deferred to tactical (Activity granularity, addressed inline in Phase 05)._

- [x] **Research §6.1 (CMake organization):** single `openxr_native` target with all `.cpp` listed; resolved (default).
- [x] **Research §6.3 (Test coverage policy):** unit-tests only for pure Kotlin logic extracted into Managers; JNI surface stays smoke-tested. Resolved.
- [ ] **Research §6.2 (Activity granularity):** finalised inline in Phase 05 by mapping each existing function to one of three Managers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `app_v2/src/vr/cpp/OpenXrNative.cpp` ≤ 1000 LOC (`wc -l`).
- [ ] Every new `OpenXr*.cpp` ≤ 800 LOC.
- [ ] `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` ≤ 1000 LOC.
- [ ] `assembleVrDebug` PASS.
- [ ] `assembleStandardDebug` PASS.
- [ ] Smoke test on Quest 3: VR cold-start → video plays → controllers respond → hand-tracking responds (if enabled) → HUD draws. No regression vs. pre-S0033 build.
- [ ] `dev/CHANGELOG.md` has entries for every modified file (added via `scripts/add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public Manager classes).
- [ ] `/spec-check S0033` returns `Verified`.
- [ ] After S0033 lands: `pwsh -File scripts/spec_catalog/update.ps1 -Id S0024 -Status "In Progress"` to unblock S0024 Phase 02.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0033`.

---

## Blockers Log

- _none yet_

---

## Change Log

- 2026-04-29 — Initial tactical plan authored by `/spec-tech`.
