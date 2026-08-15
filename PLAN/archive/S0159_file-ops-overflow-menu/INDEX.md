# Tactical Plan: S0159 — file-ops-overflow-menu

**Strategic spec:** [`../S0159_file-ops-overflow-menu.md`](../S0159_file-ops-overflow-menu.md)
**Feature:** File operations overflow menu (⋮ button per file row)
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Verified
**Phases:** 5 / 5 done
**Last updated:** 2026-05-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-model | — | ✅ Done | 7/7 | [PHASE_01__settings-model.md](PHASE_01__settings-model.md) |
| 02 | adapter-overflow-menu | 01 | ✅ Done | 6/6 | [PHASE_02__adapter-overflow-menu.md](PHASE_02__adapter-overflow-menu.md) |
| 03 | browse-wiring | 02 | ✅ Done | 4/4 | [PHASE_03__browse-wiring.md](PHASE_03__browse-wiring.md) |
| 04 | settings-ui | 01 | ✅ Done | 3/3 | [PHASE_04__settings-ui.md](PHASE_04__settings-ui.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items in the strategic spec are **Resolved**. No blockers.

---

## Architecture Notes

- `MediaFileAdapter` is constructed in `BrowseManagerInitializer.kt` (line ~152). Add `onOverflowMenuClick` param there.
- `BrowseObserverManager` is where settings-to-adapter observation happens; add `observeFileOpsOverflowMenu()` there.
- `BrowseFileOverflowMenuManager` follows the `ResourceOpsMenuManager` pattern: `@Inject` with `@ActivityContext`. Constructed and injected via `BrowseManagerInitializer` or `BrowseActivity` field injection.
- Grid layout uses a `ViewStub` (`stubOperations`) inflated lazily. The `btnOverflowMenu` is added directly to `item_media_file_grid.xml` (not inside the stub) to avoid two-stub complexity.
- `item_media_file.xml` has no `layout-land/` counterpart — single layout handles both orientations.
- `fileOpsOverflowMenuHintShown` flag tracks whether the one-time Toast was shown; default `false`, set to `true` on first toggle-ON.
- Backup file for any file >500 LOC goes to `temp/` with timestamp before edit.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0159` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0159`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-13 — Initial tactical plan authored by `/spec-tech`.
