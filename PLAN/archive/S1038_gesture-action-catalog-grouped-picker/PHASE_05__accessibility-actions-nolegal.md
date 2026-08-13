# Phase 05 - accessibility-actions-nolegal

**Goal:** Add system actions via accessibility global actions - noLegal only (group SYSTEM).

## Steps

- [x] **5.1** New enum values + catalog entries (group SYSTEM): `OPEN_NOTIFICATION_SHADE`, `OPEN_QUICK_SETTINGS`, `LOCK_SCREEN`, `TOGGLE_SPLIT_SCREEN`, `PREVIOUS_APP`. Update the exhaustive `when`. Because the enum lives in `src/main` (shared), the values exist everywhere but the CATALOG only surfaces this group when the accessibility capability is present. Verify: enum + when cover all; compiles on standard AND noLegal.
- [x] **5.2** noLegal dispatch: in `src/noLegal/.../ScreenshotAccessibilityService.kt` implement `performGlobalAction` for shade (`GLOBAL_ACTION_NOTIFICATIONS`), quick settings (`GLOBAL_ACTION_QUICK_SETTINGS`), lock (`GLOBAL_ACTION_LOCK_SCREEN`, API 28+), split-screen (`GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`, API 24+), recents/previous (`GLOBAL_ACTION_RECENTS`). API-guard the newer ones; below-API -> Timber.w degrade. Route the SYSTEM-group actions to this via the noLegal-only handler. Verify: noLegal compiles.
- [x] **5.3** Picker gating: the SYSTEM group appears only where the accessibility capability is compiled (noLegal) AND, at runtime, degrade safely if `ScreenshotAccessibilityServiceHolder.instance == null` (service off). On standard the group is absent (no accessibility seam). Verify: group hidden on standard; `a.ps1 dq` standard + `a.ps1 nd` noLegal both PASS.

## Done criteria
- [x] SYSTEM actions dispatch via accessibility on noLegal; absent on standard; both flavors build green.

## Step Log

- 2026-07-19 - Steps 5.1-5.3 done. Enum +5 SYSTEM (src/main, shared); catalog +5 SYSTEM entries; runPostSave when kept exhaustive. Flavor seam: new `GestureAccessibilityActions` interface (src/main) + `GestureAccessibilityActionsModule` (`@Multibinds`, empty set off noLegal) + noLegal `NoLegalGestureAccessibilityActions` (`@IntoSet` in ScreenCaptureModule) routing to `ScreenshotAccessibilityService.performSystemAction` (maps to GLOBAL_ACTION_NOTIFICATIONS/QUICK_SETTINGS/LOCK_SCREEN[28+]/TOGGLE_SPLIT_SCREEN[24+]/RECENTS with API guards + Timber.w degrade). Dispatcher injects the set and delegates SYSTEM actions (always skip capture; performer degrades when the service is off). Picker: `systemActionsAvailable` flag filters the SYSTEM group; the fragment computes it from the injected seam set (empty -> hidden on standard). PREVIOUS_APP maps to GLOBAL_ACTION_RECENTS (no direct previous-app global action), labelled "Recent apps". Files: ScreenshotGestureAction.kt, ScreenshotGestureActionCatalog.kt, ScreenshotGestureActionDispatcher.kt, ScreenshotGestureActionPickerManager.kt, EdgeGestureConfigDialogFragment.kt, gesture/GestureAccessibilityActions.kt (new), di/GestureAccessibilityActionsModule.kt (new), noLegal ScreenshotAccessibilityService.kt, noLegal NoLegalGestureAccessibilityActions.kt (new), noLegal di/ScreenCaptureModule.kt, strings x3 (+10 keys). Verification: `a.ps1 fk` standard + `a.ps1 fkn` noLegal both BUILD SUCCESSFUL (Hilt graph valid on both flavors); `check_strings_localized -KeyPrefix gesture_` OK 48/48; neuroslop no regression.
