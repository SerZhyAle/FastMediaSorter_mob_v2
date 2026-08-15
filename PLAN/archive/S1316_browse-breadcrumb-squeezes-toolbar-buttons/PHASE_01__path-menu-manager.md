# Phase 01 - Path Menu Manager

**Strategic spec:** [`../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md`](../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Introduce `BrowsePathMenuManager`, which renders the current path as a `PopupMenu` of segments anchored to a bar button and reports the tapped segment depth; no layout or wiring change yet.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] `scripts/utils/enter-code-lock.ps1 -Reason "S1316 phase 01"` acquired; `scripts/utils/lock-status.ps1 -Name Build` reports no live build.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowsePathMenuManager.kt` | New | ≤ 60 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). New file, no backup needed.

---

## Steps

### Step 01.1 - Create `BrowsePathMenuManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowsePathMenuManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class BrowsePathMenuManager(private val context: Context)` in package `com.sza.fastmediasorter.ui.browse.managers`. Give it one public method
> `fun showPathMenu(anchor: View, resourceName: String, folders: List<String>, onDepthSelected: (Int) -> Unit)`.
> Build an `android.widget.PopupMenu(context, anchor)`, add `resourceName` as item id `0` and each entry of `folders` as item id `index + 1`, in path order, then `popup.setOnMenuItemClickListener { onDepthSelected(it.itemId); true }` and `popup.show()`. Disable the deepest item (`isEnabled = false` on the last added item) because it is the folder already open. Log the tap with `UserActionLogger.logButtonClick("PathMenu_depth", "BrowseActivity")` exactly as `BrowseSortMenuManager.showSortPopupMenu` does. Mirror `BrowseSortMenuManager` in shape: plain class, constructor-injected `Context`, no Hilt annotation, `android.widget.PopupMenu` (not the androidx one). Do not read `BrowseViewModel` or `BrowseState` here - the caller passes the already-resolved parts. Keep every line ≤ 120 chars and use no bare numeric literals other than -1/0/1/2.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowsePathMenuManager.kt` exists.
- `Grep` - `class BrowsePathMenuManager` matches exactly once in that file.
- `Grep` - `fun showPathMenu(` present in that file.
- `Grep` - `import android.widget.PopupMenu` present in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `Grep` - `MaterialAlertDialogBuilder` returns zero hits in that file (strategic §2: no one-off dialog).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

`BrowsePathMenuManager.showPathMenu` exists and is unreferenced. Phase 02 constructs it in `BrowseManagerInitializer` and anchors it to the new `btnPath`.

---

## Rollback Plan

Revert phase commit - a new, unreferenced file; no data migration or user-facing surface changed.
