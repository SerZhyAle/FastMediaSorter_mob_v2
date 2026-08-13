# Phase 04 - BrowseActivity focus

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Wire focusable chain in BrowseActivity: top control row (Back / Sort / Filter / Refresh / Toggle / Select / Deselect / CreateFolder / CreateTextFile / CreateDrawing / ResourceOps / MicRecord) ↔ resource list. Confirm or override the existing `getInitialFocusView()` to land on the list (or Back button when list is empty).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Timestamped backup of `BrowseActivity.kt` (547 LOC > 500 threshold) in `temp/`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_browse.xml` | Modified (attrs) | unchanged structure |
| `app_v2/src/main/res/layout-land/activity_browse.xml` | Modified (attrs) | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 580 (current 547, +≤33 LOC) |
| `temp/BrowseActivity_<timestamp>.bak.kt` | Backup | n/a |

> Landscape parity (Strict Rule 12): both layouts exist - apply changes to both.

---

## Steps

### Step 04.1 - Backup `BrowseActivity.kt`

**Files:** `temp/BrowseActivity_<timestamp>.bak.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Per Strict Rule 5 (file > 500 LOC). Copy `BrowseActivity.kt` to `temp/BrowseActivity_<YYYYMMDD_HHMMSS>.bak.kt`.

**Verification:**

- `Glob` - `temp/BrowseActivity_*.bak.kt` returns ≥ 1 file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Backup: temp/BrowseActivity_20260521_232402.bak.kt (30.2 KB).

---

### Step 04.2 - Wire focus chain in `activity_browse.xml` + landscape mirror

**Files:** `app_v2/src/main/res/layout/activity_browse.xml`, `app_v2/src/main/res/layout-land/activity_browse.xml` (Modified)
**Depends on:** Step 04.1

**Prompt for developer:**

> On every visible control button in `@id/layoutControls` of both layouts (`btnBack`, `btnSort`, `btnFilter`, `btnRefresh`, `btnToggleView`, `btnSelectAll`, `btnDeselectAll`, `btnCreateFolder`, `btnCreateTextFile`, `btnCreateDrawing`, `btnResourceOps`, `btnMicRecord` - some are `visibility="gone"` by default; runtime visibility flips are honoured by Step 04.3):
> - `android:focusable="true"`, `android:clickable="true"`.
> - `android:background="@drawable/focus_button_background"` (layered with existing if needed).
> - Horizontal `nextFocusLeft`/`nextFocusRight` chain in visible order, no wrap at edges.
> - `nextFocusDown` → the browse `RecyclerView` id (verify exact id by reading the central body of the file).
>
> On the browse RecyclerView root: `nextFocusUp` → `@id/btnBack` (or the leftmost always-visible control). On the breadcrumb view (`@id/breadcrumbView`, visible only during subfolder nav): if it itself contains focusable inner views, leave their focus behaviour to the breadcrumb component; if the breadcrumb is a flat clickable, give it `focusable="true"` and chain it like a button.
>
> Mirror **exactly** the same changes into `layout-land/activity_browse.xml`. Note: the existing `activity_browse.xml` packs everything on one line per element - keep that style for minimal-diff.

**Verification:**

- `Grep` - `android:focusable="true"` matches at least 10 times in each of `layout/activity_browse.xml` and `layout-land/activity_browse.xml`.
- `Grep` - `android:foreground="@drawable/focus_button_background"` matches at least 10 times in each file.
- `Grep` - `android:nextFocusDown=` matches in the top control row of both files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Counts: portrait focusable=15, landscape focusable=15, portrait foreground=14, landscape foreground=14, portrait nextFocusDown=14. Touched 14 control buttons + RecyclerView in each layout. Author note: replaced spec's `background=` with `foreground=` (same rationale as Phase 02 - MaterialButton ripple preservation); rvMediaFiles got `nextFocusUp=@id/btnBack` so UP from list returns to back button.

---

### Step 04.3 - `BrowseActivity.kt`: confirm/override `getInitialFocusView` + restitch dynamic chain

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` (Modified)
**Depends on:** Step 04.2

**Prompt for developer:**

> 1. `BrowseActivity` already overrides `getInitialFocusView()` (per S0230). Confirm the existing return target is sensible for S0289: if the override currently returns the back button or first list item, keep as-is. If it returns `null`, set it to the first item of the `RecyclerView` (via `recyclerView.findViewHolderForAdapterPosition(0)?.itemView`) when the list is non-empty, else `binding.btnBack`. Document the choice in a one-line KDoc.
> 2. Add `restitchBrowseControlChain()` mirroring Phase 02 Step 02.4 part 2: recompute `nextFocusLeft`/`nextFocusRight` across only visible buttons in `layoutControls` after any state-driven visibility flip (look for the existing "show CreateFolder if writable" / "show MicRecord if recordable" callsites and call `restitchBrowseControlChain()` from them; the existing helper that owns these visibility flips - if any - should host the call).
> 3. Insert `Timber.d("S0289: browse initial-focus + chain restitch - listSize=$size")` at the end of `setupViews` or wherever the chain is first stitched.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `BrowseActivity.kt`.
- `Grep` - `restitchBrowseControlChain` matches at the declaration and at least one callsite.
- `Grep` - `Timber.d("S0289: browse initial-focus` matches exactly once.
- Build: `.\a.ps1 bd` exits `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: ui/browse/BrowseActivity.kt (+35 LOC). Adjusted `getInitialFocusView()` to fall back to btnBack on empty list; added `restitchBrowseControlChain()` (14-button candidates, View.VISIBLE filter); call from setupViews + Timber probe. First build attempt failed with `Unresolved reference 'files'` - fixed: state field is `mediaFiles` not `files`. Second build: BUILD SUCCESSFUL in 2m 46s.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- Browse confirms the "dynamic chain restitch" pattern from Phase 02 works on a screen with many runtime-visibility buttons; subsequent phases (Forms, Lists) follow the same minor pattern when applicable.

---

## Rollback Plan

Revert phase commit(s). Restore `BrowseActivity.kt` from Step 04.1 backup if surgical revert is needed.
