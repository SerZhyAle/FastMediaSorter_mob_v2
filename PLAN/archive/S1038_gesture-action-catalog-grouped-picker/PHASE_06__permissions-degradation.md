# Phase 06 - permissions-degradation

**Goal:** WRITE_SETTINGS grant flow for brightness + explicit API/capability degradation across the catalog.

## Steps

- [x] **6.1** `WRITE_SETTINGS` flow: when a brightness action is selected (or first triggered) and `!Settings.System.canWrite(context)`, launch `ACTION_MANAGE_WRITE_SETTINGS` via a FragmentActivity trampoline; until granted, the brightness action is inactive and the picker/slot reflects that honestly (not a silent no-op). Verify: grant intent launches; ungranted brightness logs + no-ops with a user-visible signal.
- [x] **6.2** API gating: actions unavailable at the current API level are hidden from the picker (or shown disabled with explanation) - lock (28+), split-screen (24+). Centralize the availability predicate in the catalog. Verify: on a below-API build/emulator the item is hidden; compiles.
- [x] **6.3** Missing-target degradation audit: every launch/intent action confirms `runCatching`/resolve-check so a missing app is a clean no-op with `Timber.w`, never a crash. Verify: grep the handlers - no unguarded `startActivity`.

## Done criteria
- [x] Brightness requests WRITE_SETTINGS and degrades honestly; unavailable actions hidden; no silent failures.

## Step Log

- 2026-07-19 - Steps 6.1-6.3 done. 6.1: brightness selection in `EdgeGestureConfigManager` now calls `ensureWriteSettingsPermission`, which prompts + routes to `ACTION_MANAGE_WRITE_SETTINGS` when `!Settings.System.canWrite`; declining leaves brightness inactive (dispatch-time `DeviceActionHandler` already degrades with a Timber.w, Phase 03). The grant is requested from the settings dialog (already an Activity context) rather than a dispatch-time Service trampoline - re-opening a settings screen mid-gesture would be intrusive, and the honest degradation covers the ungranted path. 6.2: `ScreenshotGestureActionCatalog.isAvailableOnApi` (lock 28+, split-screen 24+) centralises the API predicate; the picker ANDs it into `availableActions`, hiding below-API items. 6.3: grep confirms every `startActivity` in the gesture handlers + dispatcher is inside `runCatching`/`startGuarded` (no unguarded launch); Device/Media handlers make no startActivity. Files: ScreenshotGestureActionCatalog.kt, ScreenshotGestureActionPickerManager.kt, EdgeGestureConfigManager.kt, strings x3 (+2 keys). Verification: `a.ps1 fc` standard + `a.ps1 fkn` noLegal both BUILD SUCCESSFUL; `check_strings_localized -KeyPrefix gesture_` OK 50/50.
