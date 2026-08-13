# Phase 03 — Browse + Player Wiring

**Strategic spec:** [`../S0115_unified-error-display.md`](../S0115_unified-error-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Replace direct `Toast.makeText` error calls in `BrowseErrorDisplayManager` and `PlayerEventHandler` with `AppErrorNotifier.show`. These are the highest-traffic error paths in the app.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt` | Modified | ≤ 260 |

> Both files are under 500 LOC — no backup required.

---

## Steps

### Step 03.1 — Wire BrowseErrorDisplayManager to AppErrorNotifier

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `BrowseErrorDisplayManager.showError(message, details, exception?)`, the `else` branch (when `showDetailedErrors == false`) currently calls `Toast.makeText(activity, message, Toast.LENGTH_LONG).show()`. Replace that call with:
> ```kotlin
> AppErrorNotifier.show(
>     activity = activity,
>     message = message,
>     severity = ErrorSeverity.CRITICAL,
>     showDetailedErrors = false
> )
> ```
> Add `Timber.d("S0115: BrowseErrorDisplayManager.showError delegating to AppErrorNotifier")` just before the `AppErrorNotifier.show(...)` call. Do not change the `showDetailedErrors == true` branch (that path continues to use `ErrorDialog`). Do not change `showUndoSnackbar` or `isNonCriticalNetworkImageError`.

**Verification:**

- `Grep` — `AppErrorNotifier.show(` present in `BrowseErrorDisplayManager.kt`.
- `Grep` — `ErrorSeverity.CRITICAL` present in `BrowseErrorDisplayManager.kt`.
- `Grep` — `Timber.d("S0115:` present in `BrowseErrorDisplayManager.kt`.
- `Grep` — `Toast.makeText(activity, message,` must NOT appear in `BrowseErrorDisplayManager.kt` (old error toast removed).
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: BrowseErrorDisplayManager.kt — ToastThrottler import removed, AppErrorNotifier+ErrorSeverity imports added, else-branch replaced. Dev log recorded.

---

### Step 03.2 — Wire PlayerEventHandler to AppErrorNotifier

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt`
**Depends on:** — start of phase (parallel with Step 03.1)

**Prompt for developer:**

> In `PlayerEventHandler`, locate all places where `Toast.makeText(activity, ...)` is called with an error message (as opposed to informational/status messages such as "playback order" toasts or frame-capture confirmations — keep those as-is). Specifically:
>
> - In `showError(message, throwable?)`: the `else` branch after the `showDetailedErrors` check calls `ToastThrottler.showNetworkError(activity, message)`. Replace that call with:
>   ```kotlin
>   AppErrorNotifier.show(
>       activity = activity,
>       message = message,
>       severity = ErrorSeverity.CRITICAL,
>       showDetailedErrors = false
>   )
>   ```
>   Pass the `activity` instance that `PlayerEventHandler` already holds.
>
> - Any other `Toast.makeText(activity, event.message, ...)` call that surfaces an error event (look for `event.message` inside `showError` branches or `OnPlayerError` / `OnFileError` handlers) — replace with `AppErrorNotifier.show(activity, event.message, ErrorSeverity.CRITICAL)`.
>
> Do NOT replace toasts that are informational (playback mode changes, save confirmations, VR state, etc.).
>
> Add `Timber.d("S0115: PlayerEventHandler.showError delegating to AppErrorNotifier")` before each replaced call.

**Verification:**

- `Grep` — `AppErrorNotifier.show(` present in `PlayerEventHandler.kt`.
- `Grep` — `ErrorSeverity.CRITICAL` present in `PlayerEventHandler.kt`.
- `Grep` — `Timber.d("S0115:` present in `PlayerEventHandler.kt`.
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: PlayerEventHandler.kt — AppErrorNotifier+ErrorSeverity imports added, else-branch in showError() replaced. Informational toasts (ShowMessage, cast, playback order, unsupported format non-error path) left as-is per spec. Dev log recorded.

---

### Step 03.3 — Verify build

**Files:** _(none — build check only)_
**Depends on:** Steps 03.1, 03.2

**Prompt for developer:**

> Run `/build`. Confirm no compile errors. Run a quick grep to check for `TODO(phase-03)`.

**Verification:**

- Build exits code 0.
- `Grep` — `TODO(phase-03)` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Build PASS (1m 2s). Zero TODO(phase-03) hits.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build` passed in Step 03.3.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for every file in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- Browse and Player error paths now use `AppErrorNotifier` for the toast path and `ErrorDialog` for the detailed path.
- Informational toasts (playback order, VR mode, save confirmations) are intentionally left as plain `Toast.makeText` — they are not error indicators.

---

## Rollback Plan

Revert phase commit(s). The original `Toast.makeText` calls are removed but the `ToastThrottler` fallback still exists via `showNetworkError(context, message)` (null-activity path). Worst case: revert the two modified files.
