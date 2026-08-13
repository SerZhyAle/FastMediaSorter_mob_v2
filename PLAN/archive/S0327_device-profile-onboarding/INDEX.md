# Tactical Plan: S0327 - device-profile-onboarding

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Feature:** Device profile onboarding – first-run and settings-accessible device profile selection with preset application
**Tier:** 4 - Strategic
**Priority:** 50
**Status:** Phases 01-09 Done · **BlockNeedUserTest** (device verification round; full `Verified` gated on owner approval of the provisional matrix values)
**Phases:** 9 / 9 authored phases done
Last updated: 2026-06-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 5/5 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | detector | 01 | ✅ Done | 4/4 | [PHASE_02__detector.md](PHASE_02__detector.md) |
| 03 | repository | 01 | ✅ Done | 5/5 | [PHASE_03__repository.md](PHASE_03__repository.md) |
| 04 | di-bindings | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__di-bindings.md](PHASE_04__di-bindings.md) |
| 05 | welcome-ui | 01, 04 | ✅ Done | 4/4 | [PHASE_05__welcome-ui.md](PHASE_05__welcome-ui.md) |
| 06 | settings-ui | 01, 04 | ✅ Done | 4/4 | [PHASE_06__settings-ui.md](PHASE_06__settings-ui.md) |
| 07 | preset-apply | 03, 04, 05, 06 | ✅ Done ¹ | 3/3 | [PHASE_07__preset-apply.md](PHASE_07__preset-apply.md) |
| 08 | migration-existing | 01, 03, 04, 07 | ✅ Done | 2/2 | [PHASE_08__migration-existing.md](PHASE_08__migration-existing.md) |
| 09 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_09__docs-catalog-cleanup.md](PHASE_09__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

¹ Phase 07 wiring is complete and unit-tested, but the per-profile preset **values are provisional** (engineering best-guess, not owner-approved). Final matrix is gated on owner refinement + matrix-design doc (strategic §6.1, §11.15).

---

## Pre-Implementation Blockers

- [ ] **Design (OWNER-GATED, open):** Profile preset matrix v1 - which settings, which values per profile. A **provisional** 5-field matrix ships in `RepositoryModule.providePresetMatrix()` so the full wiring (matrix -> use case -> `applyBatchSettings` -> DataStore) is exercised and unit-tested, but the values are an engineering guess, not owner-approved. Final field set + values + the matrix-design doc (strategic §11.15) require owner/product decision.

---

## Completion Gate

- [x] All authored phases (01-09) show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated with device profile first-run setup (strategic §8 mandates update).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - new public repo interfaces scanned.
- [ ] `/spec-check S0327` returns `Verified`. **Currently Partial** - see "Remaining for Verified" below.
- [ ] Strategic spec `Status:` is `Partial` (not `Verified`).

---

## Remaining for Verified

The 9 authored phases are implemented, compile (`standardDebug` BUILD SUCCESSFUL), and the unit tests pass. A readiness pass (2026-06-02) closed the documentation/asset gaps and hardened the code; the ticket is now **BlockNeedUserTest** for the device round.

Done this pass:

- [x] **Matrix-design doc** (§11.15) - `dev/DEVICE_PROFILE_PRESET_MATRIX.md` (profiles, managed settings, field mapping, `Other` semantics, preset version, change rules).
- [x] **Doc-first "why profile matters"** (§11.13) - matrix doc + user-facing first-launch docs in QUICK_START / README / howto (EN/RU/UK).
- [x] **Per-profile EN/RU/UK descriptions** (§11.16) - already shipped and wired into Welcome + Settings (confirmed correct).
- [x] **Profile icon asset set** (§11.14) - `ic_profile_*` (11) + asset registry; wired into the shared tile picker (Welcome + Settings).
- [x] **Repository init hardening** - first-run bootstrap moved fully onto `Dispatchers.IO`.

Done since:

- [x] **Matrix owner approval (§6.1)** - matrix is now the owner-authored CSV asset `device_profile_presets.csv` (parsed at runtime); the provisional hardcoded matrix is removed. Verified on device (TV preset applied 83 overrides, persisted).

Still gating `Verified`:

- [ ] **On-device verification** - run the Device Test Plan in the strategic spec (§11.5, §11.7, §11.8, §11.9, §11.11).
- [ ] CSV data fixes: `defaultIconSize=156` invalid (use 152/160); refine the 4 seeded profile columns.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set journal status to `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0327`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-02 - Initial tactical plan authored by `/spec-tech`.
