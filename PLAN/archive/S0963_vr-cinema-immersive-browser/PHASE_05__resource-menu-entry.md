# Phase 05 - Resource menu entry

**Strategic spec:** [`../S0963_vr-cinema-immersive-browser.md`](../S0963_vr-cinema-immersive-browser.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Add "Open in VR Cinema" to the resource (⋮) menu on the main list, gated by XR availability; selecting it cold-launches `ImmersiveBrowseActivity` on that resource via `StartVrPlaybackRequest.resourceBrowse(..)`. Reuses the S0962 strings (`action_open_in_vr_cinema`, `vr_cinema_launch_unavailable`) - no new strings.

---

## Prerequisites

- [ ] Phase 01 ✅ (contract has `RESOURCE_BROWSE` + `resourceId` + `resourceBrowse` factory).
- [ ] Phase 04 ✅ (gateway routes `RESOURCE_BROWSE` to the Activity).
- [ ] `ResourceAdapter.kt`, `MainActivity.kt`, `MainPanelItemActionsManager.kt`, `res/menu/resource_item_actions.xml` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceVrCinemaLaunchManager.kt` | New | ≤ 110 |
| `app_v2/src/main/res/menu/resource_item_actions.xml` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 930 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainPanelItemActionsManager.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1400 |

> No new strings (reuse S0962 keys). No layout XML edit -> no landscape counterpart needed. `MainActivity` is near the 1500 cap - wire through `MainPanelItemActionsManager`, do not add inline logic.

---

## Steps

### Step 05.1 - Resource-scoped launch manager

**Files:** `ui/main/helpers/ResourceVrCinemaLaunchManager.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@ActivityScoped class ResourceVrCinemaLaunchManager @Inject constructor(@ActivityContext context, XrDetectionFacade, StartVrPlaybackUseCase)`, mirroring `BrowseVrCinemaLaunchManager` exactly but scoped to `MainActivity`: `@Volatile latestState`, self-observe `detectionFacade.state()` via `repeatOnLifecycle(STARTED)` in `init`, `val isAvailable get() = latestState == AVAILABLE_ENABLED`, and `fun launch(resource: MediaResource)` building `StartVrPlaybackRequest.resourceBrowse(resource.id, source = VrLaunchPoint.BROWSE_TILE)`, dispatching via `startVrPlaybackUseCase(request, returnTarget = null)` with a `Toast(R.string.vr_cinema_launch_unavailable)` on `Unavailable`/`Failed`. No `S0963:` prefix on the info/warn lines (ticket-log gate); the single BlockNeedUserTest probe already lives in the Activity (Phase 03).

**Verification:**

- `Glob` - `ui/main/helpers/ResourceVrCinemaLaunchManager.kt` exists.
- `Grep` - `class ResourceVrCinemaLaunchManager` matches exactly once.
- `Grep` - `resourceBrowse(` referenced.
- `Grep` - `val isAvailable` present.

**Status:** `[x]` done

---

### Step 05.2 - Menu item

**Files:** `res/menu/resource_item_actions.xml`
**Depends on:** - independent

**Prompt for developer:**

> Add `<item android:id="@+id/action_open_in_vr_cinema" android:title="@string/action_open_in_vr_cinema" android:icon="@drawable/ic_play" />` (reuse an existing suitable drawable; substitute a VR-specific one only if a `ic_vr*` drawable already exists). Place it after `action_launch_player` for menu affinity.

**Verification:**

- `Grep` - `action_open_in_vr_cinema` present in `resource_item_actions.xml`.

**Status:** `[x]` done

---

### Step 05.3 - Adapter callback + visibility flag

**Files:** `ui/main/ResourceAdapter.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add constructor params `onOpenInVrCinemaClick: ((MediaResource) -> Unit)? = null` and `private var isOpenInVrCinemaVisible: Boolean = false` (mirror `onOpenInNewWindowClick`/`isOpenInNewWindowVisible`). Add a setter matching the existing `set...Visible` pattern (~line 268) that `notifyDataSetChanged()` on change. No behaviour beyond the flag + callback plumbing here.

**Verification:**

- `Grep` - `onOpenInVrCinemaClick` present in `ResourceAdapter.kt`.
- `Grep` - `isOpenInVrCinemaVisible` present.

**Status:** `[x]` done

---

### Step 05.4 - Wire menu item in both popup blocks

**Files:** `ui/main/ResourceAdapter.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> In BOTH `PopupMenu` blocks (GridViewHolder ~line 447 and ResourceViewHolder ~line 805), set `menu.findItem(R.id.action_open_in_vr_cinema).isVisible = isOpenInVrCinemaVisible && onOpenInVrCinemaClick != null`, and add the click branch `R.id.action_open_in_vr_cinema -> { onOpenInVrCinemaClick?.invoke(resource); true }`. Mirror the exact structure used by `action_open_in_separate_window`. Both blocks must be edited (duplicate popup construction - research §9).

**Verification:**

- `Grep -c "R.id.action_open_in_vr_cinema"` returns >= 3 in `ResourceAdapter.kt` (visibility set + two click branches, or more).
- `Grep` - `onOpenInVrCinemaClick?.invoke(resource)` present at least twice.

**Status:** `[x]` done

---

### Step 05.5 - MainActivity wiring through the actions manager

**Files:** `ui/main/helpers/MainPanelItemActionsManager.kt`, `ui/main/MainActivity.kt`
**Depends on:** Step 05.1, Step 05.4

**Prompt for developer:**

> In `MainPanelItemActionsManager` add `fun openResourceInVrCinema(resource: MediaResource)` delegating to an injected/passed `ResourceVrCinemaLaunchManager.launch(resource)`, and `fun isVrCinemaAvailable(): Boolean` delegating to `.isAvailable` (mirror `openResourceInNewWindow`/`isNewWindowAvailable`). In `MainActivity`, at the `ResourceAdapter(..)` construction site (~line 938), pass `onOpenInVrCinemaClick = { panelItemActions.openResourceInVrCinema(it) }` and set `isOpenInVrCinemaVisible` from `panelItemActions.isVrCinemaAvailable()` at the same place the `isOpenInNewWindowVisible` flag is set. Keep `MainActivity` delta minimal (< 15 lines) - no business logic inline.

**Verification:**

- `Grep` - `openResourceInVrCinema` present in `MainPanelItemActionsManager.kt`.
- `Grep` - `onOpenInVrCinemaClick` referenced in `MainActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - `/build` -> `standard debug` (No-Op: item hidden, `isAvailable=false`) and `.\a.ps1 fkn` (noLegal).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] String audit clean: `scripts/check_strings_localized.ps1 -KeyPrefix "action_open_in_vr_cinema"` exit 0 (already trilingual from S0962).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Full launch chain complete: resource ⋮ menu -> `ResourceVrCinemaLaunchManager.launch` -> `resourceBrowse` request -> gateway route -> `ImmersiveBrowseActivity` on the resource. Phase 06 does catalog/dev-log/probe finalization and sets `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit - additive menu item + adapter callback default-null; the No-Op flavor already hid it. No data migration.
