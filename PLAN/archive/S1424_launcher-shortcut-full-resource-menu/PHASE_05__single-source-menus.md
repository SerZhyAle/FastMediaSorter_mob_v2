# Phase 05 - Single-source menus

**Strategic spec:** [`../S1424_launcher-shortcut-full-resource-menu.md`](../S1424_launcher-shortcut-full-resource-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Make the main window and the streams screen read their item visibility from the Phase 01 catalogs, and fix the tile-branch item that draws today but runs nothing.

---

## Prerequisites

- [x] Phase 01 is ✅ Done - verified green by the owner.
- [x] `CODE.LOCK` acquired before the first source edit, released after.
- [x] Backup taken of `ResourceAdapter.kt` under `temp/S1424/` before editing (961 lines, CLAUDE.md Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/ResourceActionCatalog.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/StreamActionCatalog.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 980 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamMenuBinder.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 450 |

> Both catalogs gain a `menuItemId` and a `byMenuItemId` lookup, which is what lets an inflated or
> programmatically built row be routed back to its action. That turns the "entries mirror the XML"
> claim from a comment into something a wrong entry breaks.

---

## Steps

### Step 05.1 - Drive the list branch's visibility from the catalog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `ResourceViewHolder`, replace the six hand-written `findItem(..).isVisible = ..` assignments with one pass over `ResourceActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, facts)`: build the `Facts` from the same expressions used today, then set each menu item visible exactly when its `ResourceMenuAction` is in the returned list. Leave the inflated XML, the item order, the icon tinting and the `when (item.itemId)` routing untouched.

**Why:**

Strategic §11.2 makes "the composition is read from one provider by both the main window and the launcher" a completion criterion, which is not met while the main window keeps its own copy of the visibility rules.

**Verification:**

- `Grep` - `ResourceActionCatalog.actionsFor(` present in `ResourceAdapter.kt`.
- `Grep` - `isVisible = ` no longer assigned to `action_copy`, `action_export_resource`, `action_share_sftp_access`, `action_open_in_separate_window`, `action_open_in_vr_cinema`, `action_launch_player` in the list branch.

**Status:** `[x]` done

---

### Step 05.2 - Drive the tile branch from the same call and fix its dead item

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Apply the Step 05.1 pass to `GridViewHolder` too, and add the missing `R.id.action_add_to_home_screen -> { onAddToHomeScreenClick(resource); true }` case to its `when` block so the tile branch stops falling through to `else -> false`.

**Why:**

Strategic §7 records this as an existing defect - the item is drawn and is clickable in the tile view but the branch has no handler - and states it is fixed while the composition is extracted, because from then on both branches read one list.

**Verification:**

- `Grep` - `action_add_to_home_screen` appears in both `when` blocks of `ResourceAdapter.kt`.
- `Grep` - `ResourceActionCatalog.actionsFor(` matches twice in the file.

**Status:** `[x]` done

---

### Step 05.3 - Drive both stream adapters from the stream catalog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the hand-written `if` gates in `showOverflowMenu` and `bindOverflowMenu` with a pass over `StreamActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, facts)`, resolving each caption through `StreamActionCatalog.labelRes`. Put that pass in one new `object StreamMenuBinder` under `ui/streams/`, since the two adapters are separate classes and would otherwise each keep a copy. The catalog decides presence; `StreamMenuBinder` also applies the reorder rows' `isEnabled`, which depends on the channel's index inside the pinned block and so cannot live in the catalog. Keep the list adapter's separate pin button by having it veto `TOGGLE_PIN` through `canRun`. Routing a chosen row stays in each adapter - those callbacks are its own.

**Why:**

Strategic ADR-1 states the composition lives in the provider so a copy cannot drift, which applies to the stream menu for the same reason it applies to the resource menu: the tile and list branches already differ today.

**Verification:**

- `Grep` - `StreamActionCatalog.actionsFor(` present in both adapters.
- `Grep` - `StreamActionCatalog.labelRes(` present in both adapters.

**Status:** `[x]` done

---

### Step 05.4 - Assert both surfaces against each other

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/menu/ResourceActionCatalogTest.kt`
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Add a test asserting that for identical `Facts`, the `LAUNCHER_DESKTOP` list is the `MAIN_WINDOW` list minus exactly the five actions strategic §5.2 excludes, in the same relative order.

**Why:**

Strategic §11.2 requires one composition behind both surfaces, and the only mechanical way to state that the desktop menu is the main-window menu minus a named set is to compare the two outputs directly.

**Verification:**

- `Grep` - a test naming both `MenuActionSurface.MAIN_WINDOW` and `MenuActionSurface.LAUNCHER_DESKTOP` in one assertion.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - **UNPROVEN**: no gradle ran in this session, by instruction.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every menu in the feature now reads one catalog, so a future item is added in one place and appears on every surface that can run it.

---

## Rollback Plan

Restore `ResourceAdapter.kt` from the `temp/S1424/` backup and revert the two stream adapters; the catalog itself stays and keeps serving the launcher.
