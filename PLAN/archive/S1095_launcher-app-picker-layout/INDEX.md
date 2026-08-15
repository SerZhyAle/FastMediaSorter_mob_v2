# Tactical Plan: S1095 - launcher-app-picker-layout

**Strategic spec:** [`../S1095_launcher-app-picker-layout.md`](../S1095_launcher-app-picker-layout.md)
**Research inputs:** none
**Feature:** Launcher: taller, adaptive multi-column, readable app picker dialog
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 2 / 2 done
**Last updated:** 2026-07-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | app-picker-grid | - | ✅ Done | 3/3 | [PHASE_01__app-picker-grid.md](PHASE_01__app-picker-grid.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 lists no open research items.

---

## Design decision (non-blocking)

- Keep the shared `dialog_searchable_option_picker.xml` and all 8 `SearchableOptionPickerController.attach` callers byte-identical. Two scoped changes only: (1) `attach` gains an optional `columns: Int = 1` param (default keeps `LinearLayoutManager`, so the 7 other pickers are unchanged; `>1` uses `GridLayoutManager`), plus it applies the current search text as an initial filter so a restored filter survives rotation. (2) `AppPickerDialogFragment` alone shows the title, computes adaptive columns (>=2 portrait), gives its root an opaque rounded `?attr/colorSurface` background, and makes its list taller. `app_picker_title` already exists in EN/RU/UK - no string work.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip here; strategic §8 has a FEATURES sentence but FEATURES is release-owned (recorded to ALL_FEATURES in Phase 02).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (SearchableOptionPickerController signature changed).
- [ ] `/spec-check S1095` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1095`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-21 - Initial tactical plan authored by `/spec-tech`.
