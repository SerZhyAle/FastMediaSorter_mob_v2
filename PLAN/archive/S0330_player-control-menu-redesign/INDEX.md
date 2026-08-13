# Tactical Plan: S0330 - player-control-menu-redesign

**Strategic spec:** [`../S0330_player-control-menu-redesign.md`](../S0330_player-control-menu-redesign.md)
**Feature:** Player Control dialog menu redesign
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 2 / 2 done
**Last updated:** 2026-06-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | navigation-redesign | - | Done | 3/3 | [PHASE_01__navigation-redesign.md](PHASE_01__navigation-redesign.md) |
| 02 | docs-catalog-cleanup | 01 | Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

---

## Pre-Implementation Blockers

- [x] **UI clarification:** portrait top segmented menu, landscape left rail, hidden irrelevant sections, text `Done` action retained.

---

## Completion Gate

- [x] All phases show Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` reviewed; no new feature entry required for a UX-only redesign.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [ ] `/spec-check S0330` returns `Verified` after user/device verification.
- [x] Strategic spec `Status:` advanced by the lifecycle scripts.

---

## How to Track Progress

1. Before starting a phase: flip row to `In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `Done`, bump counter.
4. If blocked: flip to `Blocked`, add bullet to Blockers Log.

---

## Blockers Log

- 2026-06-02 - Phase 01 blocked: `./build-debug.PS1` failed in unrelated untracked `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsProfileDialogFragment.kt` with unresolved `R.style.Theme_MaterialComponents_Dialog`. RESOLVED 2026-06-02 - that file now uses `setStyle(STYLE_NO_TITLE, 0)`; `.\a.ps1 dq` -> `BUILD SUCCESSFUL`. Closeout resumed.

---

## Change Log

- 2026-06-02 - Initial tactical plan authored for implementation.
- 2026-06-02 - Code implementation completed structurally; build closeout blocked by unrelated dirty tree compiler error.
- 2026-06-02 - Unrelated blocker cleared; `.\a.ps1 dq` green; Phase 01 + 02 closed; journal moved to BlockNeedUserTest for device verification.
