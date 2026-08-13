# Phase 04 - webview-dialog-secure

**Goal:** Secure the 3rd-party login WebView dialog (its window does not inherit the Activity flag).

Depends on: Phase 01.

## Steps

- [ ] **4.1** `ui/share/auth/WebViewAuthDialogFragment.kt`: in `onCreateDialog`/`onStart`, read current settings (inject `SettingsRepository` or read the already-available AppSettings source used elsewhere in the fragment) and, when `secureSensitiveScreens` is ON, apply `dialog?.window?.setFlags(FLAG_SECURE, FLAG_SECURE)`. Short-lived modal - apply once on show, no reactive re-apply.
  - Verify: `dialog?.window` receives the gated flag; `a.ps1 fk` compiles; no lifecycle-unsafe collection introduced.

## Done criteria
- The login WebView is excluded from screenshots/Recents when the setting is ON.
