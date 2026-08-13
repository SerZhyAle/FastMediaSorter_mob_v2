# Tactical Plan: S1161 - landscape-settings-collapsed-groups-columns

**Strategic spec:** [`../S1161_landscape-settings-collapsed-groups-columns.md`](../S1161_landscape-settings-collapsed-groups-columns.md)
**Research inputs:** none (architecture resolved inline - see strategic §4 and §6)
**Feature:** Two-column layout for collapsed settings groups in landscape
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-24

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | grid-layout | - | ✅ Done | 3/3 | [PHASE_01__grid-layout.md](PHASE_01__grid-layout.md) |
| 02 | central-wiring | 01 | ✅ Done | 4/4 | [PHASE_02__central-wiring.md](PHASE_02__central-wiring.md) |
| 03 | header-fit | 02 | ✅ Done | 2/2 | [PHASE_03__header-fit.md](PHASE_03__header-fit.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Design summary (binding for all phases)

Every settings tab shares one shape: `NestedScrollView` → vertical `LinearLayout` → a flat run of
`MaterialCardView`s, each holding a `CollapsibleSectionHeader` plus a content container. Group cards
are hand-written XML, not adapter items (strategic §4).

Chosen mechanism - a custom `ViewGroup` installed **programmatically**, so no settings layout XML is
edited and every tab (including flavor-only tabs) is covered by one hook:

1. `SettingsGroupsGridLayout` lays out its children in `R.integer.settings_group_columns` columns.
   A child whose `CollapsibleSectionHeader.isExpanded()` is `true`, or which is not a group card at
   all, spans every column. Span is read at measure time - no listener registration, so
   `CollapsibleSectionsManager`'s single `setOnExpandedChangeListener` slot is never contested.
2. `SettingsGroupColumnsManager` moves the existing children of the tab's vertical stack into one
   `SettingsGroupsGridLayout` and inserts it in their place. Called once from
   `BaseSettingsFragment.onViewCreated`.
3. Column count comes from an integer resource (1 portrait / 2 landscape), so rotation only needs a
   `requestLayout()` - `SettingsActivity` absorbs config changes and never re-inflates `layout-land`.

Column order is column-major **per run of consecutive collapsed cards** (strategic §5.1): a
full-width expanded card terminates the current run and the next run starts fresh below it.

---

## Pre-Implementation Blockers

None - strategic §6 has no `Open` items.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 capability is recorded in `docs/ALL_FEATURES.jsonl` only.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (two new classes).
- [ ] `/spec-check S1161` returns `Verified` - runs after the device test, not before.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1161`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-24 - Initial tactical plan authored by `/spec-tech`.
