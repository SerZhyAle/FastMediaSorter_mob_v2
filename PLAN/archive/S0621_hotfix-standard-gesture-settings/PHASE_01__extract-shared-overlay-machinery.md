# Phase 01 - Extract shared overlay machinery

**Strategic spec:** [`../S0621_hotfix-standard-gesture-settings.md`](../S0621_hotfix-standard-gesture-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

**Step Log:** 01.1/01.2 PASS - moved OverlayHostService.kt + ScreenGestureOverlayManager.kt to src/screenCapture (package unchanged), stale a11y comments reworded; no a11y imports in either. 01.3 PASS - `.\a.ps1 nd` BUILD SUCCESSFUL in 54s (noLegal regression green).

---

## Objective

Move the a11y-agnostic overlay-strip machinery (`OverlayHostService`, `ScreenGestureOverlayManager`) from `src/noLegal` into the already-shared `src/screenCapture` source set (mounted into standard + noLegal). After this phase noLegal behaves identically; standard gains the machinery on its classpath but no controller binding yet, so the capability is still invisible there.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `src/screenCapture/java` is mounted into both standard and noLegal (verified: `app_v2/build.gradle.kts` lines ~574 and ~592).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | Moved (delete) | - |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Moved (delete) | - |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | New (moved here) | unchanged |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | New (moved here) | unchanged |

> Package stays `com.sza.fastmediasorter.screencapture` - only the source-set directory changes. The notification drawable is already shared (`src/screenCapture/res/drawable/ic_notification_screen_capture.xml`) - no move needed. The a11y service + holder + the noLegal controller stay in `src/noLegal`.

---

## Steps

### Step 01.1 - Move OverlayHostService to the shared source set

**Files:** `app_v2/src/noLegal/.../screencapture/OverlayHostService.kt` -> `app_v2/src/screenCapture/.../screencapture/OverlayHostService.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Move `OverlayHostService.kt` from `src/noLegal/java/com/sza/fastmediasorter/screencapture/` to `src/screenCapture/java/com/sza/fastmediasorter/screencapture/` (package unchanged). Do not change code. The existing comment saying "this overlay launcher is noLegal (sideload) only" is now stale - reword it to state the launcher is shared and used by the standard MediaProjection path and the noLegal fallback. Comment only, no code change.

**Verification:**

- `Glob` - `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` exists.
- `Glob` - old path `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` no longer exists.
- `Grep` - `noLegal (sideload) only` returns zero hits in the moved file.

**Status:** `[ ]` not done

---

### Step 01.2 - Move ScreenGestureOverlayManager to the shared source set

**Files:** `app_v2/src/noLegal/.../screencapture/ScreenGestureOverlayManager.kt` -> `app_v2/src/screenCapture/.../screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Move `ScreenGestureOverlayManager.kt` to `src/screenCapture/java/com/sza/fastmediasorter/screencapture/` (package unchanged). Do not change behavior. The class is host-agnostic: it is driven by `OverlayHostService` (TYPE_APPLICATION_OVERLAY, standard + noLegal) and by the noLegal accessibility service (TYPE_ACCESSIBILITY_OVERLAY). Keep the existing window-type comment but make clear it is shared by both hosts; no a11y class import is introduced.

**Verification:**

- `Glob` - `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` exists; old noLegal path gone.
- `Grep` - `import .*ScreenshotAccessibility` returns zero hits in the moved file (no a11y class dependency).
- `Grep` - `src/noLegal/java/com/sza/fastmediasorter/screencapture/` retains exactly `ScreenshotAccessibilityService.kt`, `ScreenshotAccessibilityServiceHolder.kt`, `ScreenGestureOverlayControllerImpl.kt`.

**Status:** `[ ]` not done

---

### Step 01.3 - Build noLegal - regression gate

**Files:** - (build only)
**Depends on:** Step 01.2

**Prompt for developer:**

> Build the noLegal debug flavor. The a11y service + noLegal controller must resolve `OverlayHostService` / `ScreenGestureOverlayManager` from the shared set with no behavior change.

**Verification:**

- `.\a.ps1 nd` (or `:app_v2:assembleNoLegalDebug`) exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Both machinery classes live in `src/screenCapture/java`; only the a11y service/holder + noLegal controller remain in `src/noLegal/.../screencapture`.
- [ ] `assembleNoLegalDebug` green.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the moved files.

---

## Handoff Notes to Next Phase

`OverlayHostService` + `ScreenGestureOverlayManager` are now on the standard classpath. Phase 02 adds a standard controller that starts/stops `OverlayHostService`. No controller is bound for standard yet, so the settings group is still hidden there.

---

## Rollback Plan

Move the two files back to `src/noLegal/.../screencapture/`. No data migration or user-facing surface changed.
