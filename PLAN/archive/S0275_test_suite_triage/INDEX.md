# Tactical Plan: S0275 - test-suite-triage

**Strategic spec:** [`../S0275_test_suite_triage.md`](../S0275_test_suite_triage.md)
**Feature:** Test Suite Triage
**Tier:** 2 - Moderate (engineering hygiene)
**Priority:** 50
**Status:** Blocked
**Phases:** 0 / 5 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | baseline-unblock | - | ⛔ Blocked | 1/2 | [PHASE_01__baseline-unblock.md](PHASE_01__baseline-unblock.md) |
| 02 | failure-inventory | 01 | ⬜ Not started | 0/3 | [PHASE_02__failure-inventory.md](PHASE_02__failure-inventory.md) |
| 03 | quarantine-infra | 02 | ⬜ Not started | 0/2 | [PHASE_03__quarantine-infra.md](PHASE_03__quarantine-infra.md) |
| 04 | default-suite-stabilization | 02, 03 | ⬜ Not started | 0/2 | [PHASE_04__default-suite-stabilization.md](PHASE_04__default-suite-stabilization.md) |
| 05 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] Strategic §6 research items are resolved enough for implementation. The first runtime inventory is intentionally produced by Phase 01 after the known XR flavor-boundary compile barrier is removed.

---

## Completion Gate

- [ ] All phases show ✅ Done (or ⏭️ Skipped with explicit rationale).
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 says non-user-facing, so skipped with explicit note.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file via `add_to_dev_log.ps1` / `post-change.ps1`.
- [ ] `.claude/agent-memory/android-rd-specialist/feedback_build_pre_existing_test_failures.md` is removed or rewritten to the post-S0275 contract.
- [ ] `dev/test-quarantine/S0275_standard_unit_quarantine.md` matches the final quarantine set (or explicitly says `empty`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2` if any `app_v2/**/*.kt` file changed.
- [ ] `/spec-check S0275` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set the journal status to `BlockQuestions`, `BlockByOtherTask`, `BlockExternal`, or `BlockNeedUserTest` as appropriate.
5. All done: flip `Status:` to `Done`, run `/spec-check S0275`.

---

## Blockers Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech` equivalent flow. No pre-implementation blockers remain; Phase 01 starts with a known compile barrier in `XrEnvironmentDetectorImplTest`.
- 2026-05-20 - Phase 01 blocked after Step 01.1: the XR compile barrier is fixed, but clean `standard` builds still surface new S0274-related compile failures in `VideoPlayerManager` / extracted helpers before XML reports are produced. Next: stabilize S0274 compile state, then resume Step 01.2.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-all` while resuming S0275 from `Draft` / `Approved` state.
- 2026-05-20 - Phase 01 started. First execution slice: relocate `XrEnvironmentDetectorImplTest` out of shared `src/test` so `standard` suite can compile.
- 2026-05-20 - Phase 01 blocked by S0274 fallout before `testStandardDebugUnitTest` could emit XML. Partial inventory captured in `temp/S0275_standard_runtime_inventory.md`.