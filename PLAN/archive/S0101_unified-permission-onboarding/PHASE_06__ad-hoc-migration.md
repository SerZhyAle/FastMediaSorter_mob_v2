# Phase 06 — Ad-hoc Permission Migration

**Strategic spec:** [`../S0101_unified-permission-onboarding.md`](../S0101_unified-permission-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04, Phase 05
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Replace ad-hoc `RECORD_AUDIO` (S0100) and `ACCESS_LOCAL_NETWORK` (S0035) permission requests with calls to `RequestContextualPermissionUseCase`, and remove the duplicated denial-handling inline code that is now covered by `PermissionDenialHandler`.

---

## Prerequisites

- [x] Phase 03 is ✅ Done.
- [x] Phase 04 is ✅ Done.
- [x] Phase 05 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 500 |

> Check each file's line count before editing — backup to `temp/` if > 500 LOC.

---

## Steps

### Step 6.1 — Wire RECORD_AUDIO contextual request in AudioSettingsFragment

**Files:** `ui/settings/fragments/AudioSettingsFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the toggle or preference that enables microphone recording in `AudioSettingsFragment` (introduced in S0100). When the user turns it on for the first time:
> 1. Inject `RequestContextualPermissionUseCase` and the `RECORD_AUDIO` `PermissionEntry` (resolved from `PermissionRegistryRepository` by id `"record_audio"`).
> 2. Call `requestContextualPermissionUseCase.invoke(fragment = this, entry = recordAudioEntry) { granted -> if (!granted) { /* revert toggle */ } }`.
> Remove any existing inline `registerForActivityResult(RequestPermission)` block for `RECORD_AUDIO` that is replaced by this call.

**Verification:**

- `Grep` — `RequestContextualPermissionUseCase` present in `AudioSettingsFragment.kt`.
- `Grep` — `record_audio` present in `AudioSettingsFragment.kt`.
- `Grep` — `Manifest.permission.RECORD_AUDIO` is NOT present as a raw `registerForActivityResult` call (may remain in imports but not as a standalone launcher registration).

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: ui/settings/fragments/AudioSettingsFragment.kt (added @AndroidEntryPoint, injected RequestContextualPermissionUseCase + PermissionRegistryRepository, removed recordAudioPermissionLauncher, replaced inline launch with contextual request for record_audio). Dev log recorded.

---

### Step 6.2 — Wire ACCESS_LOCAL_NETWORK contextual request

**Files:** `ui/settings/helpers/GeneralSettingsPermissionsHelper.kt`, `ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the entry point where `PermissionHelper.requestLocalNetworkPermission()` is called from the Settings UI (currently in `GeneralSettingsPermissionsHelper.updatePermissionButtonsState()`).
> Replace the inline network permission button click handler with a call to `RequestContextualPermissionUseCase` using the `ACCESS_LOCAL_NETWORK` registry entry (id `"access_local_network"`).
> `PermissionHelper.isLocalNetworkRuntimePermissionExpected()` and `PermissionHelper.requestLocalNetworkPermission()` may remain in `PermissionHelper` for use by the registry implementation — do not delete them.

**Verification:**

- `Grep` — `RequestContextualPermissionUseCase` present in `GeneralSettingsPermissionsHelper.kt` or `GeneralSettingsFragment.kt`.
- `Grep` — `access_local_network` present in either of those files.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Files: GeneralSettingsPermissionsHelper.kt (added RequestContextualPermissionUseCase + PermissionRegistryRepository params, added handleNetworkPermissionAction() using access_local_network), GeneralSettingsFragment.kt (inject new deps, pass to permissionsHelper). Dev log recorded.

---

### Step 6.3 — Remove redundant denial-handling inline code

**Files:** `ui/settings/helpers/GeneralSettingsPermissionsHelper.kt`, `ui/welcome/WelcomeActivity.kt`
**Depends on:** Steps 6.1, 6.2

**Prompt for developer:**

> Audit `GeneralSettingsPermissionsHelper` for inline "open app settings" intents that duplicate `PermissionDenialHandler.handle()`. Remove duplicates and delegate to `PermissionDenialHandler`.
> In `WelcomeActivity`, the `showPermissionDeniedDialog()` method shows a dialog with a "Retry" path. Verify that for permissions now managed by the registry, `PermissionDenialHandler` is called on `PERMANENTLY_DENIED` instead of retrying. Keep the existing dialog for the sequential special-permission flow (`MANAGE_EXTERNAL_STORAGE`, `MANAGE_MEDIA`, battery optimization) which is outside the registry scope.

**Verification:**

- `Grep` — `PermissionDenialHandler` present in `GeneralSettingsPermissionsHelper.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `GeneralSettingsPermissionsHelper.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Files: GeneralSettingsPermissionsHelper.kt (no inline open-settings duplicates found; added handlePermissionPermanentlyDenied() delegating to PermissionDenialHandler). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 6.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase: docs-catalog-cleanup.

---

## Rollback Plan

Revert phase commit(s). S0100 and S0035 ad-hoc flows are restored from git; no data migration involved.
