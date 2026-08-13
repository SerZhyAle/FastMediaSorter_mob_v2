# Phase 02 - Internal routes

**Strategic spec:** [`../S1170_launcher-desktop-app-widgets.md`](../S1170_launcher-desktop-app-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Objective

Add the five internal routes the mechanical widgets need, so a launcher cell can express the same destination their tap PendingIntent does.

---

## Prerequisites

- [ ] `research/01` read - it names exactly which five are missing and why `os:` cannot cover them.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/InternalRouteCatalog.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/AppLaunchPanelRouteIntents.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt` | Modified | ≤ 140 |
| `docs/icons/icon-inventory.json` | Regenerated | n/a |
| ~~`app_v2/src/main/res/values{,-ru,-uk}/strings.xml`~~ | Not needed | n/a |

**Two corrections to this phase's file list, found while implementing (2026-07-30).**

- `ResolvePanelRouteAvailabilityUseCase` was missing from the plan and is **mandatory**, not optional. Its `resolve()` is a closed `when` ending in `else -> Availability(false, false)`, and `ExecuteLauncherCommandUseCase.launchFeature` refuses to start anything whose availability is not launchable. A route registered in the catalog but absent from that `when` therefore compiles, appears in the picker, and silently does nothing - which is exactly what every Phase 03 gadget would have done. Each of the five now mirrors its own widget provider's gate.
- No new string keys. Every one of the five destinations is already a `HomeWidgetCatalog` entry with a shipped trilingual label, so the routes reuse `widget_*_label` and the matching `iconRes`. That is what Step 02.2 asked for ("reuse the existing label rather than mint a near-duplicate") taken to its conclusion: the set of new keys is empty, so `set-android-string.ps1` and the localisation audit have nothing to run against.
- Consequence of registering routes: `docs/icons/icon-inventory.json` is scanned from `InternalRouteCatalog`, so it goes stale and `IconInventoryExportTest` fails freshness until re-exported. Regenerated in generate mode; the failure is a reporting one, not a defect.

---

## Steps

### Step 02.1 - Add the five route intents

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/AppLaunchPanelRouteIntents.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add intent builders mirroring exactly what the corresponding widget provider fires, so the desktop cell and the Android-home widget land on the same screen: `camera_photos` -> `MainActivity` with action `ACTION_CAMERA_PHOTOS`; `launch_camera` -> `CameraLaunchActivity` with action `ACTION_LAUNCH` (photo mode, not the existing `videoIntent`); `continue_reading` -> `MainActivity` with action `ACTION_START_SLIDESHOW`; `random_music` -> `MainActivity` with action `ACTION_RANDOM_MUSIC`; `scheduled_tasks` -> `SettingsActivity` with `EXTRA_OPEN_SCHEDULED`. Take the action constants from the providers rather than retyping their string values. The camera-launch widget also puts a `fms://cam-launch/<widgetId>` data URI on its intent purely to keep PendingIntents distinct per widget instance; a launcher cell has no widget id, so omit it and say so in a comment.

**Verification:**

- `Grep` - all five builder functions present.
- `Grep` - `ACTION_CAMERA_PHOTOS`, `ACTION_START_SLIDESHOW`, `ACTION_RANDOM_MUSIC`, `EXTRA_OPEN_SCHEDULED` each referenced by constant, not by a retyped string literal.

**Status:** `[x]` done

**Note.** The route key for the camera trampoline is `camera_launch`, not the `launch_camera` this prompt first wrote - Phase 03.3 already refers to it as `camera_launch`, and one spelling has to win before Phase 06 persists it. The four MainActivity/SettingsActivity routes carry `NEW_TASK or CLEAR_TOP` (a new `withWidgetEntryFlags`) because that is what their providers fire; `CLEAR_TOP` is what makes a second tap reach the running instance's `onNewIntent` instead of stacking a duplicate.

---

### Step 02.2 - Register the five route keys with trilingual labels

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/InternalRouteCatalog.kt`, `app_v2/src/main/res/values{,-ru,-uk}/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Register the five keys in `InternalRouteCatalog` in the same shape as the existing fourteen, wiring each to its Step 02.1 intent. Each route needs a user-visible label: add every new key across EN/RU/UK in one lockstep call, `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>` - not three manual edits. Reuse the corresponding widget's existing label string where one already says the same thing rather than minting a near-duplicate. Check the new copy against `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist).

**Verification:**

- `Grep` - the five keys present in `InternalRouteCatalog`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0.
- Strings pass the `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done - no new keys were minted, so the localisation audit has an empty subject; the labels shown are the widgets' own already-shipped trilingual strings.

**Side effect, accepted deliberately.** `InternalRouteCatalog` also feeds the quick-access panel picker, so these five now appear there too. That is what "register in the same shape as the existing fourteen" means and it is coherent - they are our own launchable features, gated exactly as their widgets are. Suppressing them would need a hide-from-panel flag the catalog does not have and the strategic spec never asked for.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Unit` reached `testStandardDebugUnitTest` with every compile task green.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `check_strings_localized.ps1` exits 0 - vacuously, no key was added.
- [x] Phase-boundary audit run - one P1 found and fixed in-phase: the missing availability branches (see the file-list correction above), which would have shipped five silently dead gadgets.

---

## Handoff Notes to Next Phase

Phase 03's mechanical gadgets resolve their destination through these route keys. A gadget must never build an Activity intent of its own.

---

## Rollback Plan

Revert the phase commit and remove the new keys with `set-android-string.ps1 -Action remove` - no persisted data or user setting is touched.
