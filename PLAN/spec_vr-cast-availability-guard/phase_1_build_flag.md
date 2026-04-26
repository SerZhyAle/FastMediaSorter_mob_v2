# Phase 1 — Add `SUPPORT_CAST` BuildConfig Flag

**File:** `app_v2/build.gradle.kts`
**Status:** [x] done

## Steps

1. In the `standard` flavor block, add after `ENABLE_DTS_DECODER`:
   ```kotlin
   buildConfigField("boolean", "SUPPORT_CAST", "true")
   ```

2. In the `lite` flavor block, add after `ENABLE_DTS_DECODER`:
   ```kotlin
   buildConfigField("boolean", "SUPPORT_CAST", "true")
   ```

3. In the `photos` flavor block, add after `ENABLE_DTS_DECODER`:
   ```kotlin
   buildConfigField("boolean", "SUPPORT_CAST", "true")
   ```

4. In the `legacy` flavor block, add after `ENABLE_DTS_DECODER`:
   ```kotlin
   buildConfigField("boolean", "SUPPORT_CAST", "true")
   ```

5. In the `vr` flavor block, add after `SUPPORT_WEAR_COMPANION`:
   ```kotlin
   buildConfigField("boolean", "SUPPORT_CAST", "false") // Horizon OS lacks Google Play Services Cast module
   ```

6. In the `vrUnlicensed` flavor block, add after `SUPPORT_WEAR_COMPANION`:
   ```kotlin
   buildConfigField("boolean", "SUPPORT_CAST", "false") // same — Horizon OS, no Cast runtime
   ```

## Verification

- `grep -n "SUPPORT_CAST" app_v2/build.gradle.kts` → 6 lines, true for standard/lite/photos/legacy, false for vr/vrUnlicensed.
- Project syncs without compile errors.
