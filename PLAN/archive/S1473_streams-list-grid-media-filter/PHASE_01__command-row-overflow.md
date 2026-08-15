# Phase 01 - Command-row overflow

**Strategic spec:** [`../S1473_streams-list-grid-media-filter.md`](../S1473_streams-list-grid-media-filter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Move the three list-population commands into the toolbar's own overflow menu and call both import sources directly, leaving the list/grid toggle and refresh as the only visible icons.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1` before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/menu_streams.xml` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1300 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> `StreamsActivity.kt` is 1257 LOC - over the 500-LOC backup threshold and under the 1500-LOC split limit. Step 01.2 carries the backup sub-step; this phase must not grow the file.

---

## Steps

### Step 01.1 - Rewrite the streams menu resource

**Files:** `app_v2/src/main/res/menu/menu_streams.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Set `app:showAsAction="never"` on `action_stream_add`. Replace the single `action_stream_import` item with two items, `action_stream_import_catalog` (title `@string/streams_import_catalog`, icon `@drawable/ic_import`) and `action_stream_import_url` (title `@string/streams_import_from_url`, icon `@drawable/ic_import`), both `never`. Order the file so the two always-visible items - `action_stream_display_toggle` and `action_stream_refresh` - come first and the three `never` items last. Do not add a menu item for the overflow button itself; the toolbar supplies it.

**Why:**

Strategic §5.1 pillar A puts the three list-population commands into one dropdown, and ADR-1 chooses the toolbar's own overflow so the "last in the command row" requirement holds by construction instead of by computed position; an item marked `never` is what makes the toolbar render that overflow button at all.

**Verification:**

- `Grep` - `app:showAsAction="never"` matches exactly 3 times in `menu_streams.xml`.
- `Grep` - `app:showAsAction="always"` matches exactly 2 times in `menu_streams.xml`.
- `Grep` - `action_stream_import_catalog` and `action_stream_import_url` each match exactly once.
- `Grep` - `action_stream_import"` returns zero hits in `menu_streams.xml`.

**Status:** `[x]` done

---

### Step 01.2 - Route the two new menu ids to their handlers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Back up `StreamsActivity.kt` to `temp/S1473/` with a timestamped name before editing (Rule 5, file is over 500 LOC). In the toolbar menu-item listener replace the `R.id.action_stream_import` branch with two branches: `R.id.action_stream_import_catalog` calls `viewModel.onImportCatalog()`, `R.id.action_stream_import_url` calls `showSourceDialog(isImport = true)`. Both cancel the in-flight health probe first, matching the existing branches that change list content. Leave the `action_stream_add`, `action_stream_display_toggle` and `action_stream_refresh` branches untouched. In `tintToolbarMenuIcons()` also tint `binding.toolbar.overflowIcon` with the same `colorOnPrimary` list the menu items get.

**Why:**

Strategic §2 goal 2 requires both import sources to run on the tap that names them, and §4 records that the current single icon exists only because two different sources hid behind it. The overflow glyph is a toolbar property rather than a menu item, so the existing tint pass does not reach it - and this screen's toolbar is painted `colorPrimary`, which is exactly the case the tint pass exists for.

**Verification:**

- `Grep` - `R.id.action_stream_import_catalog -> ` present in `StreamsActivity.kt`.
- `Grep` - `R.id.action_stream_import_url -> ` present in `StreamsActivity.kt`.
- `Grep` - `R.id.action_stream_import ->` returns zero hits in `StreamsActivity.kt`.
- `Grep` - `overflowIcon` present inside `tintToolbarMenuIcons`.
- `Glob` - a timestamped `temp/S1473/StreamsActivity*.kt` backup exists.

**Status:** `[x]` done

---

### Step 01.3 - Keep the empty-state chooser and prove it still resolves

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Leave `showImportChooser()` and its `btnEmptyImport` caller in place, and confirm no other caller remains. Add one comment above `showImportChooser()` recording that the command row now offers both sources directly and this chooser survives only for the empty-state button, so the next reader does not delete it as dead weight or re-point the toolbar at it.

**Why:**

Strategic §3.3 scopes the owner's consolidation ruling to the command row, and the Non-goals in §2 do not cover the empty state, so silently changing what its import button does would be an unrequested behaviour change; the comment prevents the opposite error of deleting a still-reachable function under Rule 20.

**Verification:**

- `Grep` - `showImportChooser()` matches exactly twice in `StreamsActivity.kt` (declaration plus the `btnEmptyImport` caller).
- `Grep` - `streams_import_choose_title` still referenced in `StreamsActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL, exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `Grep` - `Log\.d\(` returns zero hits in `StreamsActivity.kt`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits").

---

## Handoff Notes to Next Phase

The toolbar menu now carries exactly two always-visible items and three overflow items. Phase 02 re-inflates this menu on every orientation change, so any id added later must be handled in both the listener and the post-inflate fixups.

---

## Rollback Plan

Revert the phase commit - no data migration and no persisted state changed.

---

## Step Log

- 2026-08-08 - Step 01.1 done. menu_streams.xml rewritten: never=3, always=2, old action_stream_import removed. All predicates PASS.
- 2026-08-08 - Step 01.2 done. Two handler branches added, overflow glyph tinted in tintToolbarMenuIcons(). Backup at temp/S1473/StreamsActivity.20260808-0150.kt. All predicates PASS.
- 2026-08-08 - Step 01.3 done. showImportChooser() retained for the empty-state button only, rationale comment added. All predicates PASS.
- 2026-08-08 - Phase build: `..ps1 fc` BUILD SUCCESSFUL in 29s, exit 0.
- 2026-08-08 - Phase-boundary audit (Layer 1): one P2 finding - the overflow tint applied to the result of mutate() without assigning it back, so a drawable whose mutate() returns a copy would keep the untinted instance on the toolbar. Fixed in this phase by assigning the mutated drawable back to toolbar.overflowIcon. No P0/P1 findings.
- 2026-08-08 - Screenshot deferred (no device attached; device-ready reports no-device). Recorded for the BlockNeedUserTest gate.
