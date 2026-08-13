# Phase 02 - baseactivity-secure-hook

**Goal:** Centralize the gated FLAG_SECURE policy on `BaseActivity`, mirroring `applyKeepScreenAwake()`.

Depends on: Phase 01.

## Steps

- [ ] **2.1** In `core/ui/BaseActivity.kt` add:
  - `protected open fun isSensitiveScreen(): Boolean = false`
  - `private fun applySecureFlagIfEnabled(settings: AppSettings)`: if `isSensitiveScreen() && settings.secureSensitiveScreens` -> `window.addFlags(FLAG_SECURE)`, else `window.clearFlags(FLAG_SECURE)`.
  - Verify: both symbols present; compiles.
- [ ] **2.2** Wire the reactive apply exactly like keep-screen-awake: call `applySecureFlagIfEnabled` from `onCreate` after settings are available, from the existing `collectOnLifecycle(getSettings())` block (`:136-141`), and from `onResume` (`:182-192`). Reuse the already field-injected `SettingsRepository` - add no new DI.
  - Verify: `applySecureFlagIfEnabled` called from the same three lifecycle points as `applyKeepScreenAwake`; `a.ps1 fk` compiles.

## Done criteria
- Base hook applies/clears the flag reactively for any subclass that overrides `isSensitiveScreen()`.
