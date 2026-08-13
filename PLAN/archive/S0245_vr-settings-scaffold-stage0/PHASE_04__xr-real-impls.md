# Phase 04 — Real XR implementations in `src/vr/`

**Strategic spec:** [`../S0245_vr-settings-scaffold-stage0.md`](../S0245_vr-settings-scaffold-stage0.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Provide the real `XrEnvironmentDetector`, `XrDetectionFacade`, `XrEntryGateway`, the DataStore-backed `MasterTogglePreferences`, and the Hilt module for the `vr` (and inheriting `noLegal`) flavor. Stage 0 `XrEntryGatewayImpl` is a stub returning `false` — the real "enter immersive" path lands in Stage 1.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImpl.kt` | New | ≤ 80 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/MasterTogglePreferences.kt` | New | ≤ 90 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrDetectionFacadeImpl.kt` | New | ≤ 70 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt` | New | ≤ 30 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt` | New | ≤ 60 |

> **Flavor placement.** All files MUST live under `src/vr/java/...`. AGP auto-mounts on the `vr` flavor; `noLegal` flavor explicitly mounts `src/vr/java/` via the Phase 01 `sourceSets` block. Other flavors (standard/lite/photos/legacy) do NOT see this code — they consume `src/vrStub/java/` (Phase 03).

---

## Steps

### Step 04.1 — Author `XrEnvironmentDetectorImpl`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImpl.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Detect the XR runtime by inspecting `PackageManager.hasSystemFeature(...)`. Per R-06 / R-07:
> - Meta Quest exposes `android.hardware.vr.headtracking` and `oculus.software.handtracking`.
> - Android XR exposes `android.software.xr.api.openxr` and/or `android.software.xr.api.spatial`.
>
> Decision order: Android XR → Quest → none. If both feature sets are present (extremely unlikely on real hardware), prefer `ANDROID_XR` because it is the more general Android-native runtime.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> import android.content.Context
> import android.content.pm.PackageManager
> import dagger.hilt.android.qualifiers.ApplicationContext
> import javax.inject.Inject
> import javax.inject.Singleton
>
> /**
>  * Real XR environment detector. Reads PackageManager system features. Synchronous: no I/O.
>  *
>  * Detection order:
>  * 1. Android XR — `android.software.xr.api.openxr` OR `android.software.xr.api.spatial`.
>  * 2. Meta Quest — `android.hardware.vr.headtracking` (Horizon OS mandates this since v62).
>  * 3. Otherwise — [XrEnvironment.NONE].
>  */
> @Singleton
> class XrEnvironmentDetectorImpl @Inject constructor(
>     @ApplicationContext private val context: Context,
> ) : XrEnvironmentDetector {
>
>     override fun detect(): XrEnvironment {
>         val pm = context.packageManager
>         return when {
>             pm.hasSystemFeature(FEATURE_ANDROID_XR_OPENXR) ||
>                 pm.hasSystemFeature(FEATURE_ANDROID_XR_SPATIAL) -> XrEnvironment.ANDROID_XR
>             pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE) ||
>                 pm.hasSystemFeature(FEATURE_QUEST_HEADTRACKING) -> XrEnvironment.VR_QUEST
>             else -> XrEnvironment.NONE
>         }
>     }
>
>     private companion object {
>         const val FEATURE_ANDROID_XR_OPENXR = "android.software.xr.api.openxr"
>         const val FEATURE_ANDROID_XR_SPATIAL = "android.software.xr.api.spatial"
>         const val FEATURE_QUEST_HEADTRACKING = "android.hardware.vr.headtracking"
>     }
> }
> ```

**Verification:**

- `Glob` — `XrEnvironmentDetectorImpl.kt` exists at the path above.
- `Grep` — `class XrEnvironmentDetectorImpl` matches once.
- `Grep` — `android.software.xr.api.openxr` matches once.
- `Grep` — `android.hardware.vr.headtracking` matches once.
- No `Log.d(` in the file (Timber-only rule).

**Status:** `[ ]` not done

---

### Step 04.2 — Author `MasterTogglePreferences` (DataStore wrapper)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/MasterTogglePreferences.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Wrap a single boolean preference `pref_vr_enable_3d` backed by Preferences-DataStore (already a project dependency — `androidx.datastore:datastore-preferences:1.0.0`). Default value computed at first read: ON if `XrEnvironmentDetector.detect() != NONE`, OFF otherwise. Persist user overrides indefinitely — do **not** reset when the device changes (UI decision recorded in INDEX).
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> import android.content.Context
> import androidx.datastore.preferences.core.booleanPreferencesKey
> import androidx.datastore.preferences.core.edit
> import androidx.datastore.preferences.preferencesDataStore
> import dagger.hilt.android.qualifiers.ApplicationContext
> import kotlinx.coroutines.flow.Flow
> import kotlinx.coroutines.flow.map
> import javax.inject.Inject
> import javax.inject.Singleton
>
> private val Context.vrMasterToggleStore by preferencesDataStore(name = "vr_master_toggle")
>
> /**
>  * DataStore-backed wrapper for the user master toggle that controls VR feature visibility.
>  * Default value is derived from [XrEnvironmentDetector] at first read (ON for XR-capable
>  * devices, OFF otherwise). Once the user touches it, the explicit value sticks even if the
>  * device changes (e.g. debug build moved Quest 3 → phone).
>  */
> @Singleton
> class MasterTogglePreferences @Inject constructor(
>     @ApplicationContext private val context: Context,
>     private val detector: XrEnvironmentDetector,
> ) {
>
>     val enabled: Flow<Boolean> = context.vrMasterToggleStore.data.map { prefs ->
>         prefs[KEY] ?: defaultValue()
>     }
>
>     suspend fun setEnabled(value: Boolean) {
>         context.vrMasterToggleStore.edit { prefs ->
>             prefs[KEY] = value
>         }
>     }
>
>     private fun defaultValue(): Boolean = when (detector.detect()) {
>         XrEnvironment.VR_QUEST, XrEnvironment.ANDROID_XR -> true
>         XrEnvironment.NONE -> false
>     }
>
>     private companion object {
>         val KEY = booleanPreferencesKey("pref_vr_enable_3d")
>     }
> }
> ```

**Verification:**

- `Glob` — `MasterTogglePreferences.kt` exists.
- `Grep` — `class MasterTogglePreferences` matches once.
- `Grep` — `"pref_vr_enable_3d"` matches once.
- `Grep` — `preferencesDataStore` matches once.
- No `Log.d(` in the file.

**Status:** `[ ]` not done

---

### Step 04.3 — Author `XrDetectionFacadeImpl`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrDetectionFacadeImpl.kt`
**Depends on:** Steps 04.1 / 04.2

**Prompt for developer:**

> Combine `XrEnvironmentDetector.detect()` and `MasterTogglePreferences.enabled` into a single `Flow<XrDetectionState>`. Detector is sync — call it once on the flow start and reuse the value (the device cannot change at runtime for Stage 0). Preference flow updates → emit new state.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> import kotlinx.coroutines.flow.Flow
> import kotlinx.coroutines.flow.distinctUntilChanged
> import kotlinx.coroutines.flow.map
> import javax.inject.Inject
> import javax.inject.Singleton
>
> /**
>  * Real combined-state facade. Folds [XrEnvironmentDetector] output and
>  * [MasterTogglePreferences] into one [XrDetectionState] flow.
>  */
> @Singleton
> class XrDetectionFacadeImpl @Inject constructor(
>     private val detector: XrEnvironmentDetector,
>     private val preferences: MasterTogglePreferences,
> ) : XrDetectionFacade {
>
>     override fun state(): Flow<XrDetectionState> {
>         val env = detector.detect()
>         return preferences.enabled
>             .map { enabled -> fold(env, enabled) }
>             .distinctUntilChanged()
>     }
>
>     private fun fold(env: XrEnvironment, enabled: Boolean): XrDetectionState = when (env) {
>         XrEnvironment.NONE -> XrDetectionState.NONE
>         XrEnvironment.VR_QUEST, XrEnvironment.ANDROID_XR ->
>             if (enabled) XrDetectionState.AVAILABLE_ENABLED else XrDetectionState.AVAILABLE_DISABLED_BY_USER
>     }
> }
> ```

**Verification:**

- `Glob` — `XrDetectionFacadeImpl.kt` exists.
- `Grep` — `class XrDetectionFacadeImpl` matches once.
- `Grep` — `distinctUntilChanged` matches once.

**Status:** `[ ]` not done

---

### Step 04.4 — Author `XrEntryGatewayImpl` (Stage 0 stub)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Stage 0 stub: `tryEnter()` returns `false` because the real immersive entry lands in Stage 1. Keep the class non-no-op (it exists in `src/vr/java/`) so Hilt binding tests confirm the flavor-scoped wiring works.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> import javax.inject.Inject
> import javax.inject.Singleton
> import timber.log.Timber
>
> /**
>  * Stage 0 stub: VR entry is not wired yet — always returns `false`. The real implementation
>  * lands in Stage 1 with the OpenXR runtime.
>  */
> @Singleton
> class XrEntryGatewayImpl @Inject constructor() : XrEntryGateway {
>     override suspend fun tryEnter(): Boolean {
>         Timber.d("XrEntryGatewayImpl: Stage 0 stub — VR entry not wired")
>         return false
>     }
> }
> ```

**Verification:**

- `Glob` — `XrEntryGatewayImpl.kt` exists.
- `Grep` — `class XrEntryGatewayImpl` matches once.
- `Grep` — `return false` matches once in the file.

**Status:** `[ ]` not done

---

### Step 04.5 — Author `XrModule` (Hilt bindings for `vr` / `noLegal`)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt`
**Depends on:** Steps 04.1 / 04.3 / 04.4

**Prompt for developer:**

> Hilt module installed in `SingletonComponent` binding the real impls to their interfaces. Lives in `src/vr/java/` — AGP mounts it for `vr` and `noLegal` flavors only.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr.di
>
> import com.sza.fastmediasorter.core.xr.XrDetectionFacade
> import com.sza.fastmediasorter.core.xr.XrDetectionFacadeImpl
> import com.sza.fastmediasorter.core.xr.XrEntryGateway
> import com.sza.fastmediasorter.core.xr.XrEntryGatewayImpl
> import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
> import com.sza.fastmediasorter.core.xr.XrEnvironmentDetectorImpl
> import dagger.Binds
> import dagger.Module
> import dagger.hilt.InstallIn
> import dagger.hilt.components.SingletonComponent
> import javax.inject.Singleton
>
> /**
>  * Hilt bindings for the `vr` source set — mounted into `vr` (auto) and `noLegal` (explicit,
>  * see `app_v2/build.gradle.kts` `sourceSets` block). Paired with
>  * [com.sza.fastmediasorter.core.xr.di.NoOpXrModule] in `src/vrStub/java/`.
>  */
> @Module
> @InstallIn(SingletonComponent::class)
> abstract class XrModule {
>
>     @Binds
>     @Singleton
>     abstract fun bindXrEnvironmentDetector(
>         impl: XrEnvironmentDetectorImpl
>     ): XrEnvironmentDetector
>
>     @Binds
>     @Singleton
>     abstract fun bindXrDetectionFacade(
>         impl: XrDetectionFacadeImpl
>     ): XrDetectionFacade
>
>     @Binds
>     @Singleton
>     abstract fun bindXrEntryGateway(
>         impl: XrEntryGatewayImpl
>     ): XrEntryGateway
> }
> ```

**Verification:**

- `Glob` — `XrModule.kt` exists at `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/`.
- `Grep` — `abstract class XrModule` matches once.
- `Grep` — `@Binds` matches exactly 3 times in that file.
- Build `assembleVrDebug` and `assembleNoLegalDebug` both compile.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Build `assembleVrDebug` passes — Hilt resolves to real impls.
- [ ] Build `assembleNoLegalDebug` passes — same bindings reachable via source-set mount.
- [ ] Build `assembleStandardDebug` still passes — `vrStub` bindings unaffected.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every new file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

`vr` + `noLegal` flavors now have full real XR contract bindings. The master toggle preference is persistent and the combined `XrDetectionState` flow is observable. Phase 06 builds the actual Settings Fragment that surfaces the master toggle to the user.

---

## Rollback Plan

Delete the five new `.kt` files in `src/vr/java/`. The build will still pass on phone flavors (vrStub keeps providing bindings) but `assembleVrDebug` / `assembleNoLegalDebug` will fail because their source-sets have no XR binding module — pair with Phase 03 rollback to fully revert.
