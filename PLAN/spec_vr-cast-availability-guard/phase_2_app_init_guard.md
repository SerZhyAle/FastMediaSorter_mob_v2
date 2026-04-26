# Phase 2 — Guard App-Level CastContext Init

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Status:** [x] done

## Context

`FastMediaSorterApp.onCreate()` calls `CastContext.getSharedInstance(this)` at startup (~line 157).
On Quest 3 (Horizon OS) this throws an exception and emits a `Timber.w` warning.

## Steps

1. Wrap the existing cast init block with `if (BuildConfig.SUPPORT_CAST)`:

   Before:
   ```kotlin
   try {
       com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
       Timber.d("FastMediaSorterApp: Cast SDK initialized")
   } catch (e: Exception) {
       Timber.w("FastMediaSorterApp: Cast SDK not available — ${e.message}")
   }
   ```

   After:
   ```kotlin
   if (BuildConfig.SUPPORT_CAST) {
       try {
           com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
           Timber.d("FastMediaSorterApp: Cast SDK initialized")
       } catch (e: Exception) {
           Timber.w("FastMediaSorterApp: Cast SDK not available — ${e.message}")
       }
   }
   ```

## Verification

- On vr flavor: no `CastContext.getSharedInstance` call in app startup; no cast-related warning in logcat.
- On standard flavor: behavior unchanged.
