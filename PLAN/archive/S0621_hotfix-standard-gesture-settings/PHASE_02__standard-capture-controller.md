# Phase 02 - Standard capture controller

**Strategic spec:** [`../S0621_hotfix-standard-gesture-settings.md`](../S0621_hotfix-standard-gesture-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

**Step Log:** 02.1/02.2 PASS - created src/standard ScreenGestureOverlayControllerImpl (MediaProjection-only, isFallbackCaptureAvailable=false, no a11y) + di/ScreenCaptureModule (@Binds @IntoSet). 02.3 PASS - `.\a.ps1 fc` BUILD SUCCESSFUL in 14s; kaptStandardDebugKotlin resolved the single-controller multibound set (no duplicate/missing binding).

---

## Objective

Add a MediaProjection-only `ScreenGestureOverlayController` implementation + Hilt `@IntoSet` binding in the `standard` flavor source set. This un-gates the settings group on standard (the `screenGestureControllers` set becomes non-empty) and routes capture exclusively through the consent path - no accessibility.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`OverlayHostService` on the shared classpath).
- [ ] `R.string.screenshot_overlay_permission_rationale` exists in `src/main/res` (verified: `values/strings.xml:2533`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/standard/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayControllerImpl.kt` | New | ≤ 90 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt` | New | ≤ 25 |

> Flavor placement: standard-only impl lives under the `src/standard` default source set (auto-compiled by AGP - no `build.gradle.kts sourceSets` edit, no new bucket). The contract interface stays in `src/main/java/.../core/screencapture/ScreenGestureOverlayController.kt`. Do NOT recreate the `src/screenCapturePlay` bucket deleted by S0450. No `BuildConfig.IS_*` guards.

---

## Steps

### Step 02.1 - Create the MediaProjection-only controller

**Files:** `app_v2/src/standard/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayControllerImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ScreenGestureOverlayControllerImpl` in package `com.sza.fastmediasorter.screencapture` implementing `com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController`. MediaProjection/overlay path only, no accessibility imports. `@Inject constructor(@ApplicationContext context: Context, settingsRepository: dagger.Lazy<SettingsRepository>)`.
> - `isOverlayPermissionGranted(context) = Settings.canDrawOverlays(context)`.
> - `permissionSettingsIntent(context) = Intent(ACTION_MANAGE_OVERLAY_PERMISSION, "package:" + packageName)`.
> - `permissionRationaleResId() = R.string.screenshot_overlay_permission_rationale`.
> - `isFallbackCaptureAvailable() = false` (no accessibility silent path on standard - this also drives the settings UI to hide the accessibility-shortcut rows in Phase 04).
> - `fallbackPermissionSettingsIntent(context) = ` the overlay intent (contract requires a non-null return; only meaningful when fallback exists).
> - `setEnabled(enabled)`: `enabled && Settings.canDrawOverlays(appContext)` -> `OverlayHostService.start(appContext)`; else -> `OverlayHostService.stop(appContext)`. No `ScreenshotAccessibilityServiceHolder` reference.
> - `isEnabled() = runBlocking { settingsRepository.get().getSettings().first().gestureOverlayEnabled }`.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class ScreenGestureOverlayControllerImpl` matches once (declaration).
- `Grep` - `Accessibility` returns zero hits in the file.
- `Grep` - `OverlayHostService` present (start/stop calls).

**Status:** `[ ]` not done

---

### Step 02.2 - Create the standard Hilt binding module

**Files:** `app_v2/src/standard/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) abstract class ScreenCaptureModule` with `@Binds @IntoSet abstract fun bindController(impl: ScreenGestureOverlayControllerImpl): ScreenGestureOverlayController`. Same FQN shape as the noLegal module (`com.sza.fastmediasorter.di.ScreenCaptureModule`) - the two never compile together, so no clash. This populates the `Set<ScreenGestureOverlayController>` that `src/main/.../di/ScreenGestureOverlayModule.kt` declares via `@Multibinds`.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `@IntoSet` and `ScreenGestureOverlayController` both present.

**Status:** `[ ]` not done

---

### Step 02.3 - Build standard - capability un-gates

**Files:** - (build only)
**Depends on:** Step 02.2

**Prompt for developer:**

> Build standard debug. Hilt must resolve a single `ScreenGestureOverlayController` in the multibound set (no duplicate-binding error - confirms the noLegal module is not on the standard classpath). The settings group `groupScreenGestures` now un-gates on standard because `screenGestureControllers` is non-empty.

**Verification:**

- `.\a.ps1 dq` (`assembleStandardDebug`) exits 0.
- `Grep` - in `OperationsGesturesManager.setup`, the `screenGestureControllers.firstOrNull()` null-check still governs group visibility (unchanged) - the set is now non-empty on standard.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] standard controller + module under `src/standard/java`, no a11y references.
- [ ] `assembleStandardDebug` green.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the two new files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (deferred to Phase 05 with catalog hints).

---

## Handoff Notes to Next Phase

The capability is live on standard but its components/permissions are not yet declared in the standard manifest (Phase 03), and the settings group still shows the accessibility-shortcut rows that are meaningless on standard (Phase 04).

---

## Rollback Plan

Delete the two new files. The standard `screenGestureControllers` set goes empty again and the group re-hides. No data migration.
