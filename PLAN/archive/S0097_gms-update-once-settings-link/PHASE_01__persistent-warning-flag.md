# S0097 Phase 01 — Persistent one-time warning flag

## Goal

Replace the in-memory `gmsWarningShown` companion-object flag with a SharedPreferences-backed flag
so the GMS update snackbar is shown at most once per installation (survives process restarts).

## Steps

### 1. Add persistence helpers to `GmsAvailabilityChecker`

File: `app_v2/src/main/java/com/sza/fastmediasorter/core/util/GmsAvailabilityChecker.kt`

- Add private const: `private const val PREFS_KEY_WARNING_SEEN = "gms_warning_seen"`
- Add `fun isWarningSeen(context: Context): Boolean` — reads default SharedPreferences
  (`context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
   .getBoolean(PREFS_KEY_WARNING_SEEN, false)`)
- Add `fun markWarningSeen(context: Context)` — writes `true` to the same key

> **Verification:** `GmsAvailabilityChecker.isWarningSeen(ctx)` returns `false` on fresh install,
> `true` after first call to `markWarningSeen(ctx)`.

### 2. Update `BaseActivity.showGmsWarningIfNeeded()`

File: `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`

- Keep the `gmsWarningShown` in-memory guard (fast path, no I/O on repeat calls).
- Add a second guard before showing the snackbar:
  `if (GmsAvailabilityChecker.isWarningSeen(this)) { gmsWarningShown = true; return }`.
- In the `setAction` callback, also call `GmsAvailabilityChecker.markWarningSeen(this)` before
  launching the intent — so tapping "Update" also marks it seen.
- Call `GmsAvailabilityChecker.markWarningSeen(this)` immediately before `Snackbar.show()` so
  showing the snackbar itself marks it seen (regardless of whether user taps "Update").

> **Verification:** After one run where snackbar was shown, subsequent process restarts never show
> the snackbar again (checked via `isWarningSeen` returning `true`).
