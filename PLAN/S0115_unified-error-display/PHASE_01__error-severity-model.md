# Phase 01 — Error Severity Model

**Strategic spec:** [`../S0115_unified-error-display.md`](../S0115_unified-error-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Introduce `ErrorSeverity` enum and `AppErrorNotifier` facade; wire `ToastThrottler.showNetworkError` through the facade. No UI screen changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] No other phases have started.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/error/ErrorSeverity.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/AppErrorNotifier.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/ToastThrottler.kt` | Modified | ≤ 90 |

---

## Steps

### Step 01.1 — Create ErrorSeverity enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/error/ErrorSeverity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `ErrorSeverity.kt` in package `com.sza.fastmediasorter.core.error`. Define a `enum class ErrorSeverity` with two values: `CRITICAL` (shown in both debug and release builds, red indicator) and `DEBUG_ONLY` (shown only when `BuildConfig.DEBUG == true`, yellow/amber indicator). Add a KDoc comment to the class explaining the two-value contract. No other logic.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/error/ErrorSeverity.kt` exists.
- `Grep` — `enum class ErrorSeverity` matches in that file.
- `Grep` — `CRITICAL` and `DEBUG_ONLY` both appear as enum entries.
- `Grep -n "Log\.d\("` — zero hits in the new file.

**Status:** `[ ]` not done

---

### Step 01.2 — Create AppErrorNotifier facade

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/AppErrorNotifier.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `AppErrorNotifier.kt` in package `com.sza.fastmediasorter.util` as a Kotlin `object`.
>
> Implement a single public function:
> ```
> fun show(
>     activity: Activity,
>     message: String,
>     severity: ErrorSeverity,
>     screenName: String? = null,
>     showDetailedErrors: Boolean = false
> )
> ```
>
> Behaviour rules:
> - If `severity == DEBUG_ONLY` and `!BuildConfig.DEBUG` → call `Timber.d(...)` and return immediately (no visual output).
> - If `activity.isFinishing || activity.isDestroyed` → call `Timber.w(...)` and return.
> - Select background color: `CRITICAL` → `R.color.error_color`; `DEBUG_ONLY` → `R.color.warning_color`.
> - Create a `Snackbar` anchored to `activity.window.decorView.rootView`. Use a custom duration of 6 000 ms for `CRITICAL` and 4 000 ms for `DEBUG_ONLY`. Since `Snackbar` only accepts `LENGTH_INDEFINITE`/`LENGTH_LONG`/`LENGTH_SHORT` natively, set `Snackbar.LENGTH_INDEFINITE` and post a `Handler(Looper.getMainLooper()).postDelayed({ snackbar.dismiss() }, durationMs)` to dismiss after the target duration.
> - Set the Snackbar background via `snackbar.view.setBackgroundColor(ContextCompat.getColor(...))`.
> - Set text color to white (`Color.WHITE`) for `CRITICAL`; black (`Color.BLACK`) for `DEBUG_ONLY`.
> - Prefix the message with a non-color indicator: `"⚠ "` for `CRITICAL`, `"● "` for `DEBUG_ONLY`. This satisfies accessibility (colour is not the sole differentiator).
> - Add a `Timber.d("S0115: AppErrorNotifier.show severity=$severity message=...")` at the top of the function body (debug verification tag).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/util/AppErrorNotifier.kt` exists.
- `Grep` — `object AppErrorNotifier` matches exactly once in that file.
- `Grep` — `fun show(` present in that file.
- `Grep` — `Timber.d("S0115:` present in that file.
- `Grep` — `BuildConfig.DEBUG` present (release-build suppression guard).
- `Grep -n "Log\.d\("` — zero hits in the new file.

**Status:** `[ ]` not done

---

### Step 01.3 — Wire ToastThrottler.showNetworkError through AppErrorNotifier

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/ToastThrottler.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `ToastThrottler.showNetworkError`, the current implementation calls `showThrottled()` after a `BuildConfig.DEBUG` guard. Change the signature of `showNetworkError` to accept an optional `Activity?` parameter (default `null`) alongside the existing `Context`. When `activity != null`, delegate to `AppErrorNotifier.show(activity, message, ErrorSeverity.DEBUG_ONLY)` instead of calling `showThrottled`. When `activity == null`, keep the existing throttled plain Toast fallback unchanged. Update the KDoc accordingly. Do not change `showThrottled` behaviour.

**Verification:**

- `Grep` — `AppErrorNotifier.show(` appears in `ToastThrottler.kt`.
- `Grep` — `ErrorSeverity.DEBUG_ONLY` appears in `ToastThrottler.kt`.
- `Grep` — `fun showNetworkError(` signature still present (not deleted).
- `Grep -n "Log\.d\("` — zero hits in the modified file.

**Status:** `[ ]` not done

---

### Step 01.4 — Verify build compiles

**Files:** _(none — build check only)_
**Depends on:** Steps 01.1–01.3

**Prompt for developer:**

> Run `/build` to verify the project assembles without errors after the new files and `ToastThrottler` change. Do not invoke Gradle directly.

**Verification:**

- Build exits with code 0 (no compile errors).
- `Grep` — `TODO(phase-01)` returns zero hits across `app_v2/src/`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — `/build` passed in Step 01.4.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entries added via `.\scripts\add_to_dev_log.ps1` for each file in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run (new `.kt` files added).

---

## Handoff Notes to Next Phase

- `ErrorSeverity` enum is in `com.sza.fastmediasorter.core.error`.
- `AppErrorNotifier.show(activity, message, severity)` is the authoritative entry point for all error notifications going forward.
- `ToastThrottler.showNetworkError` now accepts an optional `Activity?`; callers that pass an Activity get the Snackbar path.
- Phase 02 can independently update `ErrorDialog`; Phase 03/04 wire up Browse/Player/AddResource callers.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. `ToastThrottler` change is backwards-compatible (null Activity falls back to existing path).
