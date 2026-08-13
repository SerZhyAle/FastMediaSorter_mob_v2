# Tactical Plan: S1316 - browse-breadcrumb-squeezes-toolbar-buttons

**Strategic spec:** [`../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md`](../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md)
**Research inputs:** none
**Feature:** Browse top bar - path as a fixed-width button with a segment dropdown
**Tier:** 2 - Small
**Priority:** 45
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | path-menu-manager | - | ✅ Done | 1/1 | [PHASE_01__path-menu-manager.md](PHASE_01__path-menu-manager.md) |
| 02 | bar-path-button | 01 | ✅ Done | 4/4 | [PHASE_02__bar-path-button.md](PHASE_02__bar-path-button.md) |
| 03 | overflow-width-accounting | 02 | ✅ Done | 1/1 | [PHASE_03__overflow-width-accounting.md](PHASE_03__overflow-width-accounting.md) |
| 04 | retire-breadcrumb-view | 02 | ✅ Done | 2/2 | [PHASE_04__retire-breadcrumb-view.md](PHASE_04__retire-breadcrumb-view.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `✅ Done` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Owner decision:** strategic §2 leaves the path button's content open (icon only / truncated current folder name / both). Phase 02 writes the button markup and cannot start until this is fixed. Plan assumes **icon only** (`@drawable/ic_folder_24`, `android:contentDescription="@string/current_path"`); see strategic §3.3 `Owner inputs`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; `/skill-release` owns the showcase.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed: class added and class deleted).
- [ ] `/spec-check S1316` returns `Verified`. Pending the device test - the ticket is `BlockNeedUserTest`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`. Pending the device test.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1316`.

---

## Blockers Log

- 2026-07-31 - Phase 02 blocked until the owner fixes the button content (see Pre-Implementation Blockers).

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
