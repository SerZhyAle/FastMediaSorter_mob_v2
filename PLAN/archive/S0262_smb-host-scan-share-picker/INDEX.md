# Tactical Plan: S0262 - smb-host-scan-share-picker

**Strategic spec:** [`../S0262_smb-host-scan-share-picker.md`](../S0262_smb-host-scan-share-picker.md)
**Feature:** SMB host scan share selection
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | inventory-scan-paths | - | ✅ Done | 3/3 | [PHASE_01__inventory-scan-paths.md](PHASE_01__inventory-scan-paths.md) |
| 02 | unify-share-selection | 01 | ✅ Done | 3/3 | [PHASE_02__unify-share-selection.md](PHASE_02__unify-share-selection.md) |
| 03 | empty-state-and-focus | 02 | ✅ Done | 3/3 | [PHASE_03__empty-state-and-focus.md](PHASE_03__empty-state-and-focus.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** authoritative public SMB flow resolved in strategic §6.1.
- [x] **Research:** empty-state UX resolved as cancel-only in strategic §6.2.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if Kotlin structure changed.
- [x] `/spec-check S0262` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0262`.

---

## Blockers Log

- 2026-05-20 - Initial tactical plan created with two open research blockers from strategic §6.
- 2026-05-20 - Research blockers resolved; implementation completed and ready for `/spec-check`.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-20 - Public SMB host-scan flow now auto-runs share discovery, reuses the existing picker, and shows a cancel-only empty-state dialog.
