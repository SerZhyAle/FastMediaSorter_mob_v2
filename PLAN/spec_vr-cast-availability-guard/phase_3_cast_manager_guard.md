# Phase 3 — Guard Player-Level Cast Init in CastMediaManager

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Status:** [x] done

## Context

`CastMediaManager.init()` (~line 113) calls `CastContext.getSharedInstance(context)` every time
a player activity is created. On Quest 3 this fails silently but emits a `Timber.w` on each player launch.

## Steps

1. At the top of `CastMediaManager.init()`, add an early-return guard before the try/catch:

   ```kotlin
   fun init() {
       if (!BuildConfig.SUPPORT_CAST) {
           Timber.i("CastMediaManager: cast not supported on this platform — init skipped")
           return
       }
       try {
           castContext = CastContext.getSharedInstance(context)
           ...
   ```

   The `BuildConfig` import is already present (or will be via `com.sza.fastmediasorter.BuildConfig`).

## Verification

- On vr flavor: `init()` logs exactly one `CastMediaManager: cast not supported on this platform — init skipped` and returns.
  `castContext` remains null → `isCastAvailable` returns false.
- On standard flavor: `init()` proceeds through existing try/catch; no behavior change.
- No `CastContext: ModuleUnavailableException` spam in vr logcat during a session.
