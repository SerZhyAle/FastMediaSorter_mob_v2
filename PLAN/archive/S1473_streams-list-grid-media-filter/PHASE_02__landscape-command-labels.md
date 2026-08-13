# Phase 02 - Landscape command labels

**Strategic spec:** [`../S1473_streams-list-grid-media-filter.md`](../S1473_streams-list-grid-media-filter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Show text labels on the visible toolbar commands while the screen is horizontal, rebuilding the menu on every orientation change so the labels survive rotation.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `research/01__landscape-command-label-width.md` read - it carries the rebuild requirement this phase exists for.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsCommandLabelManager.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1300 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> The label logic lives in a helper manager rather than in the Activity, per Rule 3 and Rule 2 - `StreamsActivity.kt` is already 1257 LOC.

---

## Steps

### Step 02.1 - Add `StreamsCommandLabelManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsCommandLabelManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamsCommandLabelManager` in `ui/streams/helpers`, constructed with the `MaterialToolbar`, the menu resource id, and a lambda the Activity supplies to re-apply its own post-inflate fixups. Give it one public method `applyForOrientation(isLandscape: Boolean)` that clears the toolbar menu, inflates the menu resource again, sets `MenuItemCompat.setShowAsAction(item, SHOW_AS_ACTION_ALWAYS or SHOW_AS_ACTION_WITH_TEXT)` on the always-visible items when `isLandscape` is true and plain `SHOW_AS_ACTION_ALWAYS` otherwise, then invokes the fixup lambda. Skip the whole body when the requested orientation equals the one already applied, so an unrelated configuration change does not rebuild the menu.

**Why:**

Research artifact 01 found that the framework decides text visibility once, in the item view's constructor, and strategic §4 records that this window handles orientation changes without recreating - so without a rebuild the labels would appear only when the screen is opened already horizontal.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsCommandLabelManager.kt` exists.
- `Grep` - `class StreamsCommandLabelManager` matches exactly once.
- `Grep` - `fun applyForOrientation(isLandscape: Boolean)` present.
- `Grep` - `SHOW_AS_ACTION_WITH_TEXT` present.
- `Grep` - `Log\.d\(` returns zero hits in the new file.

**Status:** `[x]` done

---

### Step 02.2 - Wire the manager into setup and rotation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Construct `StreamsCommandLabelManager` next to `StreamsControlsPlacementManager` in the setup path, holding it in a field named `commandLabels`, and pass a fixup lambda that calls `tintToolbarMenuIcons()` and `updateDisplayToggleIcon(currentDisplayMode)`. Call `commandLabels.applyForOrientation` once for the launch orientation, and again from `onConfigurationChanged` beside the existing `controlsPlacement.applyForOrientation` call, guarded by the same initialization check the other managers use.

**Why:**

Strategic §5.1 pillar B ties the label mechanism to the existing orientation-driven relocation, and research artifact 01 records that both post-inflate fixups are lost with the discarded item views unless the caller re-applies them.

**Verification:**

- `Grep` - `StreamsCommandLabelManager(` matches exactly once in `StreamsActivity.kt`.
- `Grep` - `commandLabels.applyForOrientation` matches at least twice in `StreamsActivity.kt`.
- `Grep` - `tintToolbarMenuIcons()` appears inside the lambda passed to the manager.

**Status:** `[x]` done

---

### Step 02.3 - Keep the menu-item listener attached across rebuilds

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Confirm the toolbar's menu-item click listener is registered on the toolbar rather than on individual items, so clearing and re-inflating the menu does not detach it. If any handler is bound per item, move it into the toolbar-level `when` block. Add one comment at the listener recording that the menu is rebuilt on every orientation change, so a future per-item listener is not introduced.

**Why:**

Phase 02 replaces every menu item view on rotation, and strategic §11 criterion 2 requires the three overflow entries to keep working afterwards - a per-item listener would leave the commands present but inert after the first rotation.

**Verification:**

- `Grep` - `binding.toolbar.setOnMenuItemClickListener` matches exactly once in `StreamsActivity.kt`.
- `Grep` - `setOnMenuItemClickListener` matches exactly once in `StreamsActivity.kt` overall.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fc` BUILD SUCCESSFUL, exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - this phase adds a class.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits").

---

## Handoff Notes to Next Phase

The toolbar menu is now rebuilt on orientation change, and every post-inflate mutation of a menu item must go through the fixup lambda rather than run once at setup.

---

## Rollback Plan

Revert the phase commit and delete `StreamsCommandLabelManager.kt` - no persisted state and no resource contract changed.

---

## Step Log

- 2026-08-08 - Step 02.1 done. StreamsCommandLabelManager created. First draft applied the label flag to every menu item via a forEach, which would have dragged the three overflow entries back onto the row and undone Phase 01; rewritten to take an explicit list of pinned item ids before verification. All predicates PASS.
- 2026-08-08 - Step 02.2 done. Manager wired into setup and onConfigurationChanged as `commandLabels`. The fixup lambda handles the pre-first-emission case where the display mode is still null by tinting only. All predicates PASS.
- 2026-08-08 - Step 02.3 done. Listener confirmed toolbar-level, single registration, rationale comment added. All predicates PASS.
- 2026-08-08 - Phase build: `a.ps1 fc` BUILD SUCCESSFUL in 21s, exit 0.
- 2026-08-08 - Phase-boundary audit (Layers 1 and 3): manager is Activity-owned and holds no listener the Activity does not already own; the rebuild guard stops unrelated configuration changes from re-inflating. No P0/P1 findings.
- 2026-08-08 - Screenshot deferred (no device attached).
