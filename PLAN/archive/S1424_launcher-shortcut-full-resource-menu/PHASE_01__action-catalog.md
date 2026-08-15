# Phase 01 - Action catalog

**Strategic spec:** [`../S1424_launcher-shortcut-full-resource-menu.md`](../S1424_launcher-shortcut-full-resource-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Introduce the one node that decides which actions a resource or a stream offers on a given surface, as a pure function covered by unit tests; no caller changes yet.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] Strategic §6 research items blocking this phase are Resolved - all three resolved 2026-08-07.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/MenuActionSurface.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/ResourceActionCatalog.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/StreamActionCatalog.kt` | New | ≤ 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/menu/ResourceActionCatalogTest.kt` | New | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/menu/StreamActionCatalogTest.kt` | New | ≤ 150 |

---

## Steps

### Step 01.1 - Add the surface enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/MenuActionSurface.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `enum class MenuActionSurface` with entries `MAIN_WINDOW` and `LAUNCHER_DESKTOP`. Document on each entry what state that surface has: the main window owns an ordered, visible resource list and a window to sit beside; the launcher desktop owns neither.

**Why:**

Strategic §5.3 requires the provider to take the cell kind as a parameter so the `pin:` kind can join later without reworking the dispatcher, and §5.2 excludes items by surface rather than by resource type - both need surface to be an explicit argument instead of a boolean buried in a call site.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `enum class MenuActionSurface` matches exactly once.
- `Grep` - `LAUNCHER_DESKTOP` present.

**Status:** `[x]` done

---

### Step 01.2 - Add the resource action catalog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/ResourceActionCatalog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `enum class ResourceMenuAction`, one entry per row of `app_v2/src/main/res/menu/resource_item_actions.xml` in that file's order, each carrying its `@StringRes labelRes` and `@DrawableRes iconRes` taken verbatim from that XML: OPEN, LAUNCH_PLAYER, OPEN_IN_VR_CINEMA, ADD_TO_HOME_SCREEN, EDIT, COPY, EXPORT, SHARE_SFTP_ACCESS, SCAN, MOVE_UP, MOVE_DOWN, MOVE_TO_TOP, MOVE_TO_BOTTOM, DELETE, OPEN_IN_SEPARATE_WINDOW.
> Add `object ResourceActionCatalog` with a `data class Facts` carrying `isPredefinedVirtual`, `isSftp`, `isQuickSlideshowEligible`, `isNewWindowAvailable`, `isVrCinemaAvailable`, plus `fun actionsFor(surface: MenuActionSurface, facts: Facts, canRun: (ResourceMenuAction) -> Boolean = { true }): List<ResourceMenuAction>`. Keep the host's veto a parameter rather than a field of `Facts`, so `Facts` stays a comparable value in tests.
> Reproduce the six existing visibility rules of `ResourceAdapter` exactly: COPY and EXPORT hidden when `isPredefinedVirtual`, SHARE_SFTP_ACCESS shown only when `isSftp`, OPEN_IN_SEPARATE_WINDOW only when `isNewWindowAvailable`, OPEN_IN_VR_CINEMA only when `isVrCinemaAvailable`, LAUNCH_PLAYER only when `isQuickSlideshowEligible`. On `LAUNCHER_DESKTOP` additionally drop MOVE_UP, MOVE_DOWN, MOVE_TO_TOP, MOVE_TO_BOTTOM and OPEN_IN_SEPARATE_WINDOW, then drop everything `canRun` rejects.

**Why:**

Strategic ADR-1 states the composition is extracted into a provider rather than copied into the launcher, because a copy would diverge from the original at the first new item and the resource menu gains items regularly; §5.2 names the four reorder items and "open in a separate window" as the ones the desktop must not show, and ADR-2 states that an item which cannot finish is left out rather than shown dead.

**Verification:**

- `Grep` - `enum class ResourceMenuAction` and `object ResourceActionCatalog` each match once.
- `Grep` - `fun actionsFor(` present with a `MenuActionSurface` first parameter.
- `Grep` - `MOVE_TO_BOTTOM` present in the enum and in the `LAUNCHER_DESKTOP` exclusion set.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

---

### Step 01.3 - Add the stream action catalog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/StreamActionCatalog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `enum class StreamMenuAction` carrying only `@DrawableRes iconRes`, one entry per row of `StreamGridAdapter.bindOverflowMenu` in its order: TOGGLE_PIN, MOVE_UP, MOVE_DOWN, MOVE_TO_TOP, TOGGLE_FAVORITE, ADD_SHORTCUT, EDIT, SHARE_LINK, REMOVE.
> Add `object StreamActionCatalog` with `data class Facts` carrying `isPinned`, `isFavorite`, `favoritesEnabled`, `isReorderable`, `isManualOrigin`, a `fun actionsFor(surface, facts, canRun): List<StreamMenuAction>` shaped like the resource one, and a `@StringRes fun labelRes(action, facts): Int` resolving the two rows whose caption depends on state - TOGGLE_PIN to `streams_unpin`/`streams_pin` and TOGGLE_FAVORITE to `streams_remove_from_favorites`/`streams_add_to_favorites`.
> Reproduce the existing gates: the three reorder rows only when `isReorderable`, TOGGLE_FAVORITE only when `favoritesEnabled`, EDIT only when `isManualOrigin`. On `LAUNCHER_DESKTOP` additionally drop MOVE_UP, MOVE_DOWN, MOVE_TO_TOP and TOGGLE_FAVORITE.

**Why:**

Strategic §2 goal 2 requires the stream cell to offer the same actions as the row on the streams screen, and §5.2 excludes the reorder rows and the favourite toggle specifically because the favourite toggle reads state that exists only on the streams screen.

**Verification:**

- `Grep` - `enum class StreamMenuAction` and `object StreamActionCatalog` each match once.
- `Grep` - `fun labelRes(` present.
- `Grep` - `TOGGLE_FAVORITE` appears in both the enum and the `LAUNCHER_DESKTOP` exclusion set.

**Status:** `[x]` done

---

### Step 01.4 - Cover composition with unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/menu/ResourceActionCatalogTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/core/menu/StreamActionCatalogTest.kt`
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> Write JVM unit tests asserting the emitted action lists, not just their size. Cover for resources: the plain-local-folder case on both surfaces, the predefined-virtual case (no COPY, no EXPORT), the SFTP case (SHARE_SFTP_ACCESS present), the slideshow-ineligible case, and that `LAUNCHER_DESKTOP` never emits a reorder action or OPEN_IN_SEPARATE_WINDOW on any `Facts` combination. Cover for streams: pinned versus unpinned labels, favourites disabled, non-manual origin, and that `LAUNCHER_DESKTOP` never emits TOGGLE_FAVORITE. Assert relative order is preserved against the main-window list.

**Why:**

Strategic §11.6 makes the menu composition covered by a unit test a completion criterion, and §7 records that not one affected class is covered today, so a regression in the composition would otherwise pass unnoticed.

**Verification:**

- `Glob` - both test files exist.
- `Grep` - `MenuActionSurface.LAUNCHER_DESKTOP` present in both test files.
- `Grep` - at least one assertion naming `MOVE_TO_TOP` in `ResourceActionCatalogTest.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this phase adds public classes.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`ResourceActionCatalog.actionsFor` and `StreamActionCatalog.actionsFor` are the only producers of a menu item list from here on. `canRunOnSurface` is the seam a host uses to withhold an action it cannot execute yet, so a partially wired host stays free of dead items without the catalog knowing anything about that host.

---

## Rollback Plan

Revert the phase commit - the files are additive and no existing caller reads them yet.
