# Phase 02 - Play flavor capture impl

**Goal:** Add a MediaProjection-only controller + Hilt `@IntoSet` binding in a new Play source set `src/screenCapturePlay/java`, mounted into `standard` + `photos`. This makes the capability appear in those flavors (settings section un-gates via the existing `screenGestureControllers.isEmpty()` check) and routes capture exclusively through the MediaProjection machinery.

**Depends on:** Phase 01.

---

## Steps

### 2.1 Create the MediaProjection-only controller

New file `app_v2/src/screenCapturePlay/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayControllerImpl.kt`, package `com.sza.fastmediasorter.screencapture`, implementing `com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController`:

- `@Inject constructor(@ApplicationContext context, settingsRepository: dagger.Lazy<SettingsRepository>)`.
- `isOverlayPermissionGranted(context) = Settings.canDrawOverlays(context)`.
- `permissionSettingsIntent(context) = Intent(ACTION_MANAGE_OVERLAY_PERMISSION, "package:" + packageName)`.
- `permissionRationaleResId() = R.string.screenshot_overlay_permission_rationale`.
- `isFallbackCaptureAvailable() = false`.
- `fallbackPermissionSettingsIntent(context) = ` the overlay intent (only meaningful when fallback exists; return the same overlay intent to satisfy the contract).
- `setEnabled(enabled)`: `enabled && canDrawOverlays` -> `OverlayHostService.start(appContext)`; else -> `OverlayHostService.stop(appContext)`. No `ScreenshotAccessibilityServiceHolder` reference.
- `isEnabled() = runBlocking { settingsRepository.get().getSettings().first().gestureOverlayEnabled }`.

No accessibility imports. Mirror the noLegal impl's overlay branch only.

**Verification:** file compiles; `grep -n "Accessibility" app_v2/src/screenCapturePlay/java/.../ScreenGestureOverlayControllerImpl.kt` empty; `R.string.screenshot_overlay_permission_rationale` resolves (confirm it exists in `src/main/res`; if absent, add EN/RU/UK via `set-android-string.ps1 -Action add`).

### 2.2 Create the Play Hilt binding module

New file `app_v2/src/screenCapturePlay/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt`: `@Module @InstallIn(SingletonComponent::class) abstract class ScreenCaptureModule` with `@Binds @IntoSet abstract fun bindController(impl: ScreenGestureOverlayControllerImpl): ScreenGestureOverlayController`. Identical shape to the noLegal module; binds the Play impl from the same source set.

**Verification:** file present; FQN `com.sza.fastmediasorter.di.ScreenCaptureModule` (same as noLegal's, never compiled together).

### 2.3 Mount the Play set + build standard

In `app_v2/build.gradle.kts` `sourceSets`:

- `getByName("standard")`: add `kotlin.directories.add("src/screenCapturePlay/java")`.
- `getByName("photos")`: add `kotlin.directories.add("src/screenCapturePlay/java")`.

Do NOT mount it into noLegal (noLegal keeps its a11y controller + module).

Build `standard` debug.

**Verification:** `.\a.ps1 dq` (`assembleStandardDebug`) exits 0. Hilt graph resolves a single `ScreenGestureOverlayController` in the multibound set for standard (no duplicate-binding error - confirms noLegal module is not on the standard classpath).

---

## Phase Done Criteria

- [ ] Play controller + module in `src/screenCapturePlay/java`, no a11y references.
- [ ] `src/screenCapturePlay/java` mounted into standard + photos only.
- [ ] `assembleStandardDebug` green.
