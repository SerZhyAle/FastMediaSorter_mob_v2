> **SUPERSEDED** — replaced by [PHASE_05__browse-entry-points.md](PHASE_05__browse-entry-points.md) + [PHASE_06__player-tear-off.md](PHASE_06__player-tear-off.md) (2026-05-04 redesign). Do not use.

# Phase 04 — "Open in New Window" UI

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add an "Open in new window" command to the Browse resource popup menu (VR flavor only). The command creates a new `PlayerActivity` task with a fresh `windowId`, visible only when `BuildConfig.SUPPORT_VR_PLAYER` is true. Includes trilingual strings.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt` | Modified | ≤ 380 |

---

## Steps

### Step 04.1 — Add trilingual strings for "open in new window"

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following string resource to all three locale files. Use the ellipsis style `..` (two dots) for Russian and Ukrainian where natural; English uses no ellipsis.
>
> | File | Key | Value |
> |------|-----|-------|
> | `values/strings.xml` | `action_open_in_new_window` | `Open in new window` |
> | `values-ru/strings.xml` | `action_open_in_new_window` | `Открыть в новом окне` |
> | `values-uk/strings.xml` | `action_open_in_new_window` | `Відкрити в новому вікні` |

**Verification:**

- `Grep` — `action_open_in_new_window` matches in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `action_open_in_new_window` matches in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `action_open_in_new_window` matches in `app_v2/src/main/res/values-uk/strings.xml`.

**Status:** `[ ]` not done

---

### Step 04.2 — Add `openInNewWindow()` helper to `BrowseEventHandler`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a method `fun openPlayerInNewWindow(resourceId: Long, initialFilePath: String)` to `BrowseEventHandler`. The method must:
>
> 1. Generate a unique window ID: `val windowId = java.util.UUID.randomUUID().toString()`.
> 2. Build a `PlayerActivity` intent reusing the existing `createStandardPlayerIntent` helper (or equivalent). Pass `windowId` via `putExtra(PlayerActivity.EXTRA_WINDOW_ID, windowId)`.
> 3. Add flags: `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK`.
> 4. Call `activity.startActivity(intent)`.
>
> Do not close or finish the current Browse activity — new window opens alongside it. No VR immersive path (do not call `VrTaskTransition.enterImmersive`).

**Verification:**

- `Grep` — `openPlayerInNewWindow` matches in `BrowseEventHandler.kt`.
- `Grep` — `FLAG_ACTIVITY_MULTIPLE_TASK` matches in `BrowseEventHandler.kt`.
- `Grep` — `EXTRA_WINDOW_ID` matches in `BrowseEventHandler.kt`.
- `Grep` — `UUID.randomUUID` matches in `BrowseEventHandler.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseEventHandler.kt`.

**Status:** `[ ]` not done

---

### Step 04.3 — Add "Open in new window" menu item to `ResourceOpsMenuManager` (VR only)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `ResourceOpsMenuManager`, locate the method that builds or inflates the resource popup menu options. Add an "Open in new window" option guarded by `BuildConfig.SUPPORT_VR_PLAYER`:
>
> ```kotlin
> if (BuildConfig.SUPPORT_VR_PLAYER) {
>     // add menu item: R.string.action_open_in_new_window
>     // on click: browseEventHandler.openPlayerInNewWindow(resourceId, firstFilePath)
> }
> ```
>
> The item must be visible only in the VR flavor. Position it after the standard "Open" / "Play" option and before destructive actions (delete, rename). Adapt the exact insertion point to the existing menu building pattern in this file.

**Verification:**

- `Grep` — `SUPPORT_VR_PLAYER` matches in `ResourceOpsMenuManager.kt`.
- `Grep` — `action_open_in_new_window` matches in `ResourceOpsMenuManager.kt`.
- `Grep` — `openPlayerInNewWindow` matches in `ResourceOpsMenuManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `ResourceOpsMenuManager.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — `/build` passes for standard (non-VR) flavor (menu item absent).
- [ ] Project compiles — `/build` passes for VR flavor (menu item present).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added for all 5 files in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

"Open in new window" is fully wired. Phase 05 finalizes user-facing documentation and catalog sync.

---

## Rollback Plan

Revert phase commit(s). The menu item disappears; `openPlayerInNewWindow` method is removed; string resources cleaned up. No persistent state affected.
