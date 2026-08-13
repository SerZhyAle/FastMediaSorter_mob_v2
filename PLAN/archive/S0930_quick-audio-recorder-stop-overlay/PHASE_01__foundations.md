# Phase 01 - Foundations

**Strategic spec:** [`../S0930_quick-audio-recorder-stop-overlay.md`](../S0930_quick-audio-recorder-stop-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Introduce the `QuickRecorderIndicatorController` contract and its empty-set Hilt multibinding in `src/main`, so every flavor compiles with zero behaviour change before any flavor implementation exists.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorController.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/QuickRecorderIndicatorModule.kt` | New | ≤ 20 |

---

## Steps

### Step 01.1 - Define the controller interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `QuickRecorderIndicatorController` in package `com.sza.fastmediasorter.widget` with four members: `fun isAvailable(context: Context): Boolean` (true when the OS permission backing the overlay is granted), `fun show(context: Context, onStop: () -> Unit)` (shows the floating indicator; `onStop` fires when the user taps its Stop control), `fun updateElapsed(text: String)` (updates the elapsed-time text already shown by `show`; no-op if not shown), `fun hide()` (hides if shown; safe to call when not shown). KDoc on the interface: this is the optional floating indicator for `QuickAudioRecorderService` (S0930) - mirrors the empty-set degradation pattern of `com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController`, so the service degrades to its existing notification Stop action and the S0796 repeat-gesture toggle when no controller is bound or `isAvailable` is false. No implementation in this file - `src/main` stays flavor-agnostic.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorController.kt` exists.
- `Grep` - `interface QuickRecorderIndicatorController` matches exactly once.
- `Grep` - `fun isAvailable(context: Context): Boolean`, `fun show(context: Context, onStop: () -> Unit)`, `fun updateElapsed(text: String)`, `fun hide()` each present.

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorController.kt` (new, 24 LOC).

---

### Step 01.2 - Empty-set Hilt multibinding

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/QuickRecorderIndicatorModule.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `QuickRecorderIndicatorModule` - `@Module @InstallIn(SingletonComponent::class) abstract class` with a single `@Multibinds abstract fun controllers(): Set<QuickRecorderIndicatorController>`, copying the exact shape of `app_v2/src/main/java/com/sza/fastmediasorter/di/ScreenGestureOverlayModule.kt`. This lets any `src/main` class `@Inject` a `Set<@JvmSuppressWildcards QuickRecorderIndicatorController>` that resolves to an empty set on flavors with no binding (Phase 02 adds the two flavor bindings).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/di/QuickRecorderIndicatorModule.kt` exists.
- `Grep` - `@Multibinds` and `fun controllers(): Set<QuickRecorderIndicatorController>` both present.
- `.\a.ps1 fc` passes (exit 0) - proves the empty-set module compiles standalone.

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS (`.\a.ps1 fc` -> BUILD SUCCESSFUL in 35s). Files: `app_v2/src/main/java/com/sza/fastmediasorter/di/QuickRecorderIndicatorModule.kt` (new, 13 LOC).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` (src/main only, no flavor-gated file touched yet - no `fkn` needed per project convention).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`QuickRecorderIndicatorController` and its empty-set module exist and compile on every flavor. Phase 02 adds the two concrete flavor implementations and binds them `@IntoSet`.

---

## Rollback Plan

Revert the phase commit - two new files, no existing file modified, no data migration, no user-facing surface changed.
