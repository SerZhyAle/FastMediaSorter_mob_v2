# Phase 03 - Dialog / bottom-sheet coverage

**Strategic spec:** [`../S0819_tv-dpad-focus-visibility.md`](../S0819_tv-dpad-focus-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-07-01
**Completed:** 2026-07-01 (fk PASS; detekt ReturnCount fixed, scoped PASS)

---

## Objective

Extend the travelling frame to `DialogFragment` / bottom-sheet windows, which own a separate `Window` the Activity-decor listener cannot see.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameController.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameFragmentCallbacks.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameActivityCallbacks.kt` | Modified | ≤ 160 |

---

## Steps

### Step 03.1 - Confirm controller accepts any `Window`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Verify `FocusFrameController` already operates on an injected `Window` (from Phase 02) with no Activity-specific assumptions. If any Activity-only reference exists, generalize it so a `Dialog.window` works identically. No behavior change for the Activity path.

**Verification:**

- `Grep` - constructor param type is `Window` (not `Activity`).
- `Grep` - no `is Activity` / `as Activity` cast inside the controller.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - Verified `FocusFrameController(private val window: Window)` is already Window-generic (Phase 02); only a `View`/`ViewGroup` cast, no `Activity` cast. No edit needed. Predicates PASS.

---

### Step 03.2 - Create `FocusFrameFragmentCallbacks`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameFragmentCallbacks.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `class FocusFrameFragmentCallbacks : FragmentManager.FragmentLifecycleCallbacks`. Maintain a `WeakHashMap<Fragment, FocusFrameController>`. In `onFragmentStarted`, if the fragment is a `DialogFragment` whose `dialog?.window` is non-null AND that window is not the host Activity window, create + `attach()` a controller to `dialog.window`, store it. In `onFragmentStopped`, `detach()` and remove. Non-dialog fragments share the Activity window (already covered) - skip them.

**Verification:**

- `Glob` - file exists.
- `Grep` - `FragmentManager.FragmentLifecycleCallbacks` present.
- `Grep` - `is DialogFragment` and `dialog?.window` present.
- `Grep` - `onFragmentStarted` and `onFragmentStopped` both present.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - Created `FocusFrameFragmentCallbacks : FragmentManager.FragmentLifecycleCallbacks()` with `WeakHashMap<Fragment, FocusFrameController>`; `onFragmentStarted` attaches a controller to a `DialogFragment`'s own `dialog.window` (guards combined into one early-return to satisfy detekt ReturnCount<=2), `onFragmentStopped` detaches. Predicates PASS; scoped detekt PASS after ReturnCount fix.

---

### Step 03.3 - Register fragment callbacks per Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameActivityCallbacks.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `FocusFrameActivityCallbacks.onActivityCreated`, when the Activity is a `FragmentActivity`, register a single shared `FocusFrameFragmentCallbacks` on its `supportFragmentManager` via `registerFragmentLifecycleCallbacks(cb, /*recursive=*/ true)`. Keep a reference so it is unregistered in `onActivityDestroyed` (symmetry).

**Verification:**

- `Grep` - `registerFragmentLifecycleCallbacks(` with `true` (recursive) present.
- `Grep` - `unregisterFragmentLifecycleCallbacks(` present in `onActivityDestroyed`.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - `FocusFrameActivityCallbacks.onActivityCreated` registers a per-Activity `FocusFrameFragmentCallbacks` on `supportFragmentManager` (recursive=true) for `FragmentActivity`; `onActivityDestroyed` unregisters it (kept in a `WeakHashMap`). Predicates PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] Project compiles - fk PASS (19s). Full standard-debug build deferred to pre-device-test.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (2096 records).

---

## Handoff Notes to Next Phase

Dialogs, bottom sheets and forms in dialog windows now show the frame. Remaining: kill the redundant `FocusRingHelper` ring (Phase 04) and the player input redesign (Phase 05).

---

## Rollback Plan

Revert the phase commit - Activity coverage from Phase 02 stays intact; only dialog coverage is removed.
