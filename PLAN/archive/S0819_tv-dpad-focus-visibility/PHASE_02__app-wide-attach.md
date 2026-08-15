# Phase 02 - App-wide attach (activity windows)

**Strategic spec:** [`../S0819_tv-dpad-focus-visibility.md`](../S0819_tv-dpad-focus-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 3 / 3
**Started:** 2026-07-01
**Completed:** 2026-07-01

---

## Objective

Attach one `FocusFrameOverlay` to every Activity window and drive it from global focus + touch-mode changes, registered once in `FastMediaSorterApp` - no per-Activity edits.

---

## Prerequisites

- [x] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameController.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameActivityCallbacks.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 500 |

---

## Steps

### Step 02.1 - Create `FocusFrameController`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class FocusFrameController(private val window: Window)`. On `attach()`: build a `FocusFrameOverlay` from `window.context` and add it via `window.decorView.overlay.add(overlay)`. Register a `ViewTreeObserver.OnGlobalFocusChangeListener` on `decorView.viewTreeObserver` that, when the new focus is a real focusable View and the window is NOT in touch mode (`decorView.isInTouchMode == false`), computes the focused view bounds in decor coordinates (use `getLocationInWindow` + width/height, or `offsetDescendantRectToMyCoords`) and calls `overlay.moveTo(rect)`; otherwise `overlay.clear()`. Register an `addOnTouchModeChangeListener` that clears the frame on entering touch mode and re-evaluates current focus on leaving it. Register an `OnPreDrawListener` that re-syncs the frame to the current focused view bounds each frame the frame is showing (keeps it aligned during scroll/layout) - keep it cheap: early-return when not showing. On `detach()`: remove the overlay from the overlay group and remove ALL three listeners (listener symmetry - no leak). Do not hold the focused View strongly across frames beyond what is needed.

**Verification:**

- `Glob` - `core/ui/focus/FocusFrameController.kt` exists.
- `Grep` - `class FocusFrameController` once.
- `Grep` - `decorView.overlay` and `OnGlobalFocusChangeListener` present.
- `Grep` - `addOnTouchModeChangeListener` present.
- `Grep` - `removeOnGlobalFocusChangeListener` AND `removeOnTouchModeChangeListener` present (symmetry).
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - Created `FocusFrameController(window: Window)` with `attach()`/`detach()`. Three listeners (`OnGlobalFocusChangeListener`, `OnTouchModeChangeListener`, `OnPreDrawListener`) registered on `decorView.viewTreeObserver`; all three removed in `detach()` behind an `isAlive` guard, overlay removed from `decorView.overlay`. Bounds computed in decorView-local coords via `offsetDescendantRectToMyCoords` (reused `Rect`, allocation-free). Frame shown only when `!isInTouchMode`. Verification: all predicates PASS (Glob exists; `class FocusFrameController` x1; `decorView.overlay` + `OnGlobalFocusChangeListener` present; `addOnTouchModeChangeListener` present; both remove* present; `Log.d(` 0 hits).

---

### Step 02.2 - Create `FocusFrameActivityCallbacks`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameActivityCallbacks.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class FocusFrameActivityCallbacks : Application.ActivityLifecycleCallbacks`. Maintain a `WeakHashMap<Activity, FocusFrameController>`. In `onActivityCreated`, create a `FocusFrameController(activity.window)` and `attach()` it, store it. In `onActivityDestroyed`, `detach()` and remove. Implement the other callbacks as no-ops. Guard against activities that opt out (see Handoff) via an interface check `activity !is FocusFrameExcluded`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `Application.ActivityLifecycleCallbacks` present.
- `Grep` - `onActivityCreated` and `onActivityDestroyed` both call controller `attach`/`detach`.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - Created `FocusFrameActivityCallbacks : Application.ActivityLifecycleCallbacks` with a `WeakHashMap<Activity, FocusFrameController>`. `onActivityCreated` skips `activity is FocusFrameExcluded`, else builds `FocusFrameController(activity.window)`, `attach()`, stores it; `onActivityDestroyed` removes from map and `detach()`. Other five callbacks are `Unit` no-ops. Marker `interface FocusFrameExcluded` created in the same `core/ui/focus` package (own file `FocusFrameExcluded.kt`) so 02.2 references compile; not applied to any Activity (satisfies Step 02.3's marker declaration too). Verification: all predicates PASS (Glob exists; `Application.ActivityLifecycleCallbacks` present; `onActivityCreated`->`attach`, `onActivityDestroyed`->`detach`).

---

### Step 02.3 - Register callbacks in `FastMediaSorterApp`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `FastMediaSorterApp.onCreate()`, after `super.onCreate()`, call `registerActivityLifecycleCallbacks(FocusFrameActivityCallbacks())`. Also declare a marker interface `interface FocusFrameExcluded` in the `core/ui/focus` package for future opt-out (e.g. VR `DiagnosticXrActivity`); do not apply it anywhere yet.

**Verification:**

- `Grep` - `registerActivityLifecycleCallbacks(FocusFrameActivityCallbacks())` in `FastMediaSorterApp.kt`.
- `Grep` - `interface FocusFrameExcluded` present in the focus package.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - Inserted `registerActivityLifecycleCallbacks(FocusFrameActivityCallbacks())` in `FastMediaSorterApp.onCreate()` at lines 208-210, directly after the existing `registerActivityLifecycleCallbacks(appOrientationManager)` (line 205). Used fully-qualified `com.sza.fastmediasorter.core.ui.focus.FocusFrameActivityCallbacks` (no new import, matches the file's existing FQ-reference style). `interface FocusFrameExcluded` was created in Step 02.2 (`FocusFrameExcluded.kt`) and is not applied to any Activity. Verification: both predicates PASS (`registerActivityLifecycleCallbacks(FocusFrameActivityCallbacks())` present; `interface FocusFrameExcluded` present in focus package); `TODO(phase-02)` 0 hits. Note: `FastMediaSorterApp.kt` is 638 LOC, above the phase table's advisory <=500 budget - but it was already 562 LOC before this edit; net +6 lines, well under the 1500 LOC hard limit. Splitting the file is out of scope for this phase.

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - `/build` standard debug. (Central build owned by caller; not run in this session.)
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new controller + callbacks classes; 2095 records).

---

## Handoff Notes to Next Phase

- Every Activity now shows the travelling frame on real Android focus, non-touch only. Dialogs with their OWN window are NOT yet covered - Phase 03 handles them.
- `FocusFrameExcluded` marker exists for opt-out; VR exclusion applied in Phase 06 if needed.
- MainActivity resource items receive real focus (`FocusManager.requestFocus`) so the frame already shows there - the redundant `FocusRingHelper` ring is neutralized in Phase 04.

---

## Rollback Plan

Revert the phase commit - removing the single `registerActivityLifecycleCallbacks` call fully disables the feature; no data or layout change.
