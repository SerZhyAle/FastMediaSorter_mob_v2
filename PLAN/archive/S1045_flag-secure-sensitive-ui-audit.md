# S1045 - flag-secure-sensitive-ui-audit

**Status:** Archived

<!-- auto-approved by /spec-all - 2026-07-14 -->

## 0. Raw capture (verbatim)

> FLAG_SECURE отсутствует во всём приложении - секрет-несущий UI (PIN-диалоги, поля учёток) уязвим к скриншотам/Recents. Отдельный аудит.

Source: side-finding surfaced during S1039 research (`.fmscfg`/companion QR share). Parked via `/spec-draft`.

## 1. Problem

Secret-bearing screens have no screenshot / screen-record / Recents-thumbnail protection. Credentials typed or pre-filled in Add-Resource and Resource-Editor, the plaintext `defaultUser`/`defaultPassword` revealed in Settings, the 3rd-party login WebView, and the companion QR (which rasterizes a plaintext SFTP password) all reach a screenshot and the Recents thumbnail. Only `CompanionQrShareActivity` sets `FLAG_SECURE` today, and it does so unconditionally - there is no shared, user-controllable policy.

## 2. Goals

1. Apply `FLAG_SECURE` to the secret-bearing surfaces (targeted, not blanket) so their content is excluded from screenshots, screen recording, and the Recents thumbnail.
2. Add a single user setting `secureSensitiveScreens` (default ON) that gates this protection, so the user can disable it.
3. Centralize the policy on a shared, reactive `BaseActivity` hook mirroring the existing `keepScreenAwake` mechanism; secure the one Dialog surface explicitly (its window does not inherit the flag).
4. Preserve behavior for the one screen already secured (`CompanionQrShareActivity`) - default-ON matches its current always-on state.

Non-goals: blanket app-wide `FLAG_SECURE` (breaks legitimate screenshots and conflicts with the screen-capture feature); PIN/app-lock/biometric (no such feature exists); instrumented window-flag test automation (deferred).

## 3. Scope

In scope (targeted secret surfaces):
- `CompanionQrShareActivity` - gate its existing unconditional flag behind the setting.
- `AddResourceActivity` - credential input (typed + pre-filled on copy/edit).
- `ResourceEditorActivity` - edit/copy resource credentials.
- `SettingsActivity` - hosts the revealable plaintext `defaultUser`/`defaultPassword` in the Authorization section (window-level flag secures the whole settings window; acceptable - `FLAG_SECURE` cannot be partial).
- `WebViewAuthDialogFragment` - live 3rd-party login WebView; secured via its own `dialog.window` (Activity flag does not propagate to a Dialog).

Out of scope:
- `CompanionQrScanActivity` (camera preview, no on-screen secret text).
- PIN/app-lock/biometric - **N/A**: no such feature exists in the codebase (the raw capture mentioned it, but there is nothing to secure).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1039 (FLAG_SECURE precedent on the QR-share screen), S0984 (SFTP resource share).
- **UI change:** new toggle row "Secure sensitive screens" in the Settings Authorization section; secret screens become blank in Recents / screenshots when enabled.
- **Data change:** new persisted boolean `AppSettings.secureSensitiveScreens` (DataStore key `secure_sensitive_screens`, default `true`).
- **Flavor scope:** all flavors (secret surfaces live in `src/main`; no flavor gating - Rule 14).

## 4. Owner decisions (2026-07-14)

- **Targeted, not blanket** FLAG_SECURE on secret-bearing surfaces only; normal screens stay screenshot-able; no conflict with the `screenCapture` feature.
- Add an **optional user setting** to toggle the secure-screen protection, defaulting to **ON** (secure); the user may disable it. Implication: new boolean `AppSettings.secureSensitiveScreens` (default `true`) gates the per-surface flag; triggers CLAUDE.md Rule 22 (settings-docs-sync).

## 5. Design

### 5.1 Setting
- `AppSettings.secureSensitiveScreens: Boolean = true` (mirror `enableStatistics`, `AppSettings.kt:355`).
- `SettingsRepositoryImpl`: `booleanPreferencesKey("secure_sensitive_screens")`; load `?: true`; save in the settings write path (mirror `enableStatistics` at `:204/:528/:724`).

### 5.2 Centralized apply (Activities)
- `BaseActivity`: add `protected open fun isSensitiveScreen(): Boolean = false` and `applySecureFlagIfEnabled(settings: AppSettings)` that `addFlags(FLAG_SECURE)` when `isSensitiveScreen() && settings.secureSensitiveScreens`, else `clearFlags(FLAG_SECURE)`.
- Call from `onCreate` (post settings load), re-apply reactively via the existing `collectOnLifecycle(getSettings())` and in `onResume` - mirror `applyKeepScreenAwake()` (`BaseActivity.kt:136-141/182-192`). Reuses the already field-injected `SettingsRepository` - no new DI.
- `AddResourceActivity`, `ResourceEditorActivity`, `SettingsActivity`, `CompanionQrShareActivity` override `isSensitiveScreen() = true`. Remove the unconditional `setFlags` line from `CompanionQrShareActivity` (now gated by the base hook).

### 5.3 Dialog surface
- `WebViewAuthDialogFragment`: in `onCreateDialog`/`onStart`, read current settings and apply `FLAG_SECURE` to `dialog?.window` when `secureSensitiveScreens`. Reactive re-apply not required (short-lived modal); apply once on show.

### 5.4 Settings UI
- New `SettingsToggleRow` in `fragment_settings_general.xml` Authorization section (`csh_title=settings_category_authorization`).
- Wire in `GeneralSettingsViewSetupHelper` (setOnCheckedChangeListener -> update AppSettings) + mirror observer in `GeneralSettingsObserversHelper` (`setCheckedSilently`).
- Strings EN/RU/UK: `settings_secure_sensitive_screens_title`, `settings_secure_sensitive_screens_summary`.

### 5.5 Docs (Rule 22)
- Regenerate `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`.

## 6. Open questions

- None. All forks resolved from the owner decision (§4) and the codebase (research/01). CompanionQrShareActivity gating is behavior-preserving (default matches current).

## 7. Risks

- Dialog window does not inherit the flag - `WebViewAuthDialogFragment` secured separately (mitigated in §5.3).
- `SettingsActivity` secures the whole window, not just the auth section - acceptable, flag is window-level.
- LOC pressure on `ResourceEditorFragment` (912) / `GeneralSettingsViewSetupHelper` (688) - keep edits minimal; extract only if crossing 1500.
- Window-flag assertion needs an instrumented test - deferred to device verification (`BlockNeedUserTest`).

## 8. Verification

- Build: standard debug PASS.
- Unit: DataStore round-trip for `secureSensitiveScreens` in `SettingsRepositoryImplTest`.
- Device (deferred): each secret screen shows blank in Recents / blocks screenshot when ON; screenshot allowed again when the setting is OFF.

## Last Audit

**Date:** 2026-07-15
**Mode:** strategic (audit)
**Outcome:** Verified
**Counts:** PASS 3 device + 2 code-confirmed · WARN 0 · FAIL 0

Device-verified on emulator-5554: Settings/AddResource/ResourceEditor blank when 'Secure sensitive screens' ON (default), MainActivity captures normally (scoped not global), toggle OFF reactively re-allows capture. CompanionQrShare + WebViewAuth code-confirmed (same centralized gate, not device-reachable on stock emulator). `S1045:` probe removed on Verified flip.

### Manual (device) - 2026-07-15, emulator-5554 (sdk_gphone16k_x86_64, Android 17/SDK 37), standard-debug v2.60.7151.516

Method: FLAG_SECURE confirmed respected by this emulator's `screencap` - a secured window captures as all-black while the system status bar stays visible; the `BaseActivity` probe `S1045: secure-flag applied secure=<b> screen=<Name>` corroborates each apply. Evidence under `temp/S1045/`.

Setting default: `Secure sensitive screens` toggle found in Settings > General > "Authorization and accounts", summary "Block screenshots and Recents preview on screens showing passwords"; `checked="true"` on fresh install (expected ON, actual ON). PASS.

Per-screen (setting ON):
- SettingsActivity - PASS. expected: screenshot black + Recents thumbnail blank + probe secure=true. actual: `02_settings_sensitive.png` all-black (22 KB), `03_recents_settings.png` Recents card body fully black under the app-name chip, probe `secure=true screen=SettingsActivity`.
- AddResourceActivity - PASS. expected: black + secure=true. actual: `04_addresource_sensitive.png` all-black (15 KB), probe `secure=true screen=AddResourceActivity`.
- ResourceEditorActivity - PASS. expected: black + secure=true. actual: `05_resourceeditor_sensitive.png` all-black (15 KB), probe `secure=true screen=ResourceEditorActivity` (reached via resource overflow -> Edit).
- CompanionQrShareActivity - NOT DEVICE-VERIFIED (code-confirmed). Not reachable on a fresh emulator: the QR-share window is generated only from a saved SFTP resource's share flow (no SFTP server available). Direct `am start` blocked (SecurityException - not exported). Code applies `FLAG_SECURE` secure-first in `onCreate` (line 40) then relaxes only when the setting is OFF, gated by the same `secureSensitiveScreens` proven above; cannot produce an unprotected first frame.
- WebViewAuthDialogFragment - NOT DEVICE-VERIFIED (code-confirmed). Not reachable without a live 3rd-party cloud sign-in (no account). Code secures `dialog.window` secure-first (line 109). Same mechanism as the verified surfaces.

Negative control (scoping proof):
- MainActivity - PASS. expected: content captured + secure=false. actual: `01_main_nonsensitive.png` full resource list (195 KB), probe `secure=false screen=MainActivity`. Flag is scoped, not global.

Toggle OFF path (setting disabled):
- Reactive re-apply fired immediately: probe `secure=false screen=SettingsActivity` on toggle without leaving the screen. `06_settings_toggle_off.png` captures full content (227 KB, toggle visibly OFF). PASS.
- AddResourceActivity re-opened with setting OFF: probe `secure=false screen=AddResourceActivity`, `07_addresource_toggle_off.png` captures full content (143 KB). Confirms the gate propagates to opt-in activities both ways. PASS.
- Setting restored to ON afterward (device left at default).

Verdict: PASS for all reachable surfaces (3/5 sensitive Activities + Recents + negative control + OFF path). CompanionQrShareActivity and WebViewAuthDialogFragment could not be reached on a stock emulator (need an SFTP backend / cloud account) - both code-confirmed to use the identical, device-proven FLAG_SECURE + `secureSensitiveScreens` gate. No defect found.
