# Phase 02 — Wire Existing Settings-Intent Callers

**Strategic spec:** [`../S0043_bugfix-settings-window-bounds-xr.md`](../S0043_bugfix-settings-window-bounds-xr.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-01
**Completed:** 2026-05-01

---

## Objective

Route every existing system-Settings-intent launch site through `SettingsIntentLauncher.launch(..)`. Three direct-call sites in `PermissionHelper` migrate trivially. Three `ActivityResultLauncher`-based sites (two in `WelcomeActivity`, one driven through `MainStoragePermissionsHelper` → `MainActivity`) are converted to `Activity.startActivityForResult` + `onActivityResult` overrides because `ActivityOptionsCompat` cannot carry `setLaunchBounds`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done — `SettingsIntentLauncher.launch(activity, intent, requestCode)` available.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1000 |

> No file in this list crosses the 500-LOC backup threshold based on current line counts (`PermissionHelper.kt` 322, `WelcomeActivity.kt` ~640, `MainStoragePermissionsHelper.kt` 89, `MainActivity.kt` to be verified — if >500 LOC at edit time, take a timestamped backup into `temp/` first).

---

## Steps

### Step 02.1 — Migrate `PermissionHelper` Settings launches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> In `PermissionHelper.kt`, replace every `activity.startActivityForResult(intent, <CODE>)` that launches a `Settings.ACTION_*` intent with `SettingsIntentLauncher.launch(activity, intent, <CODE>)`. Apply this to all three functions:
> - `requestManageMediaPermission` (try-branch and catch-branch).
> - `requestAllFilesAccessPermission` (try-branch, first catch-branch, nested catch-branch).
> - `routeToStorageSettings` (every `when` arm: API 30+ try, API 30+ catch, API 29 branch, API 28 branch, API 23..27 branch).
> Add `import com.sza.fastmediasorter.core.util.SettingsIntentLauncher` at the top — already in the same package, so import not required; verify and remove if it appears redundant. Do not change the request-code constants, do not change the existence/behaviour of the try/catch fallbacks.

**Verification:**

- `Grep` — `activity\.startActivityForResult\(intent` returns zero hits in `PermissionHelper.kt`.
- `Grep` — `SettingsIntentLauncher\.launch\(activity, intent` matches at least 8 times in `PermissionHelper.kt` (3 in `requestManageMediaPermission` is wrong — count: `requestManageMediaPermission` 2 branches, `requestAllFilesAccessPermission` 3 branches, `routeToStorageSettings` 5 branches = 10 hits; allow ≥ 8 to absorb minor refactors).
- `Grep` — `Settings\.ACTION_REQUEST_MANAGE_MEDIA` still matches at least once.
- `Grep` — `Settings\.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` still matches at least twice.
- `Grep` — `Log\.d\(` returns zero hits in `PermissionHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 5/5 PASS. 10 `SettingsIntentLauncher.launch` hits (2 in `requestManageMediaPermission`, 3 in `requestAllFilesAccessPermission`, 5 in `routeToStorageSettings`). Files: `PermissionHelper.kt` (modified, ~322 LOC). Dev log recorded.

---

### Step 02.2 — Convert `WelcomeActivity` permission launchers to `onActivityResult`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> In `WelcomeActivity.kt`:
> 1. Delete the two fields `manageMediaPermissionLauncher` and `allFilesAccessPermissionLauncher` (both registered via `ActivityResultContracts.StartActivityForResult()`).
> 2. In `requestManageMediaPermissionIfNeeded()`, replace `manageMediaPermissionLauncher.launch(intent)` with `SettingsIntentLauncher.launch(this, intent, PermissionHelper.REQUEST_CODE_MANAGE_MEDIA)`.
> 3. In `requestAllFilesAccessPermissionIfNeeded()`, replace `allFilesAccessPermissionLauncher.launch(intent)` with `SettingsIntentLauncher.launch(this, intent, PermissionHelper.REQUEST_CODE_ALL_FILES_ACCESS)`.
> 4. Add an override:
>    ```kotlin
>    @Deprecated("Required for Settings panel bounds — see S0043")
>    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
>        super.onActivityResult(requestCode, resultCode, data)
>        if (requestCode == PermissionHelper.REQUEST_CODE_MANAGE_MEDIA ||
>            requestCode == PermissionHelper.REQUEST_CODE_ALL_FILES_ACCESS) {
>            continueSpecialPermissionsFlowOrComplete()
>        }
>    }
>    ```
> 5. Add import `com.sza.fastmediasorter.core.util.SettingsIntentLauncher`.
> 6. Leave `batteryOptimizationPermissionLauncher` and `notificationsPermissionLauncher` untouched — they launch non-Settings intents.

**Verification:**

- `Grep` — `manageMediaPermissionLauncher` returns zero hits in `WelcomeActivity.kt`.
- `Grep` — `allFilesAccessPermissionLauncher` returns zero hits in `WelcomeActivity.kt`.
- `Grep` — `SettingsIntentLauncher\.launch\(this, intent, PermissionHelper\.REQUEST_CODE_MANAGE_MEDIA\)` matches exactly once.
- `Grep` — `SettingsIntentLauncher\.launch\(this, intent, PermissionHelper\.REQUEST_CODE_ALL_FILES_ACCESS\)` matches exactly once.
- `Grep` — `override fun onActivityResult\(requestCode: Int, resultCode: Int, data: Intent\?\)` matches exactly once in `WelcomeActivity.kt`.
- `Grep` — `batteryOptimizationPermissionLauncher` still matches (untouched).

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 6/6 PASS. Removed two launcher fields, replaced with `SettingsIntentLauncher.launch(..)`, added `onActivityResult` override (deprecated annotation acknowledged in KDoc — necessary because `ActivityOptionsCompat` cannot carry `setLaunchBounds`). Files: `WelcomeActivity.kt` (modified, ~644 LOC). Dev log recorded.

---

### Step 02.3 — Convert `MainStoragePermissionsHelper` to request-code path; wire `MainActivity` callback

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`

**Depends on:** Phase 01

**Prompt for developer:**

> 1. **`MainStoragePermissionsHelper.kt`:**
>    - Drop the constructor parameter `settingsPermissionLauncher: ActivityResultLauncher<Intent>` and the related `import androidx.activity.result.ActivityResultLauncher`.
>    - Replace `settingsPermissionLauncher.launch(intent)` in `launchStoragePermissionFlow()` with `SettingsIntentLauncher.launch(activity, intent, PermissionHelper.REQUEST_CODE_MANAGE_STORAGE)`.
>    - Add a public `fun onSettingsResult()` method that re-runs `permissionCheckDoneThisSession = false; checkLocalPermissionsOnStartup()` (so a returning user is re-evaluated). KDoc explains it must be called from the host activity's `onActivityResult` for `REQUEST_CODE_MANAGE_STORAGE`.
>    - Add import `com.sza.fastmediasorter.core.util.SettingsIntentLauncher`.
> 2. **`MainActivity.kt`:**
>    - Update the `MainStoragePermissionsHelper(..)` instantiation to drop the `settingsPermissionLauncher` argument. Remove the field that holds that launcher if it is unused elsewhere.
>    - Add (or extend if it already exists) `override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)` that calls `super` and routes `PermissionHelper.REQUEST_CODE_MANAGE_STORAGE` to the helper's `onSettingsResult()`.

**Verification:**

- `Grep` — `settingsPermissionLauncher` returns zero hits in `MainStoragePermissionsHelper.kt`.
- `Grep` — `SettingsIntentLauncher\.launch\(activity, intent, PermissionHelper\.REQUEST_CODE_MANAGE_STORAGE\)` matches exactly once in `MainStoragePermissionsHelper.kt`.
- `Grep` — `fun onSettingsResult\(\)` matches exactly once in `MainStoragePermissionsHelper.kt`.
- `Grep` — `helper\.onSettingsResult\(\)` OR `mainStoragePermissionsHelper\.onSettingsResult\(\)` matches exactly once in `MainActivity.kt`.
- `Grep` — `override fun onActivityResult\(requestCode: Int, resultCode: Int, data: Intent\?\)` matches at least once in `MainActivity.kt`.
- `Grep` — `MainStoragePermissionsHelper\(` in `MainActivity.kt` no longer passes `settingsPermissionLauncher` (verify by reading the constructor call).

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 6/6 PASS. Constructor parameter dropped, helper now uses `SettingsIntentLauncher.launch(..)`, public `onSettingsResult()` exposed and forwarded from `MainActivity.onActivityResult`. Files: `MainStoragePermissionsHelper.kt` (~98 LOC), `MainActivity.kt` (modified). Dev log recorded.

---

### Step 02.4 — Sweep for any remaining direct `Settings.ACTION_*` launches

**Files:** project-wide (read-only sweep)
**Depends on:** Steps 02.1, 02.2, 02.3

**Prompt for developer:**

> Run a project-wide search:
> - `Grep` for `startActivityForResult\(.*Settings\.ACTION` across `app_v2/src/main/`.
> - `Grep` for `\.launch\(.*Settings\.ACTION` across `app_v2/src/main/`.
> - `Grep` for `startActivity\(Intent\(Settings\.ACTION` across `app_v2/src/main/`.
> Every hit must either route through `SettingsIntentLauncher.launch(..)` already, or be a `startActivity(..)` call for a non-permission Settings page (informational links, e.g. opening notification settings from a button) — leave those untouched if the user-flow does not need a callback. Document any deliberate skips inline with a one-line comment `// SettingsIntentLauncher.launch not needed — fire-and-forget link to <page>` so future audits can recognise them.

**Verification:**

- `Grep` — `startActivityForResult\(intent, PermissionHelper\.REQUEST_CODE_(MANAGE_MEDIA|MANAGE_STORAGE|ALL_FILES_ACCESS)` returns zero hits anywhere in `app_v2/src/main/`.
- `Grep` — `Settings\.ACTION_REQUEST_MANAGE_MEDIA` only appears inside `PermissionHelper.kt` and `WelcomeActivity.kt`.
- `Grep` — `Settings\.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` only appears inside `PermissionHelper.kt`, `WelcomeActivity.kt`, `MainStoragePermissionsHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 3/3 PASS. Sweep found 3 fire-and-forget `startActivity(Intent(Settings.ACTION_*))` callsites — battery-optimization page (`GeneralSettingsPermissionsHelper.kt:90`) and default-apps page (`DefaultPlayerHelper.kt:324, 339`). Per spec, marked with inline comment `// SettingsIntentLauncher.launch not needed — fire-and-forget link to <page>`; not migrated (no callback needed, system Settings page rendering on XR is acceptable for these informational links). Dev log recorded for 2 modified files.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO\(phase-02\)` returns zero hits.
- [ ] `Grep` for `Log\.d\(` in any file modified by this phase returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `pwsh -File scripts/add_to_dev_log.ps1`.
- [ ] Smoke run on a normal phone (any Android 12+) — system Settings opens full-screen as before for both Manage Media and All Files Access (no regression).

---

## Handoff Notes to Next Phase

All system-Settings permission intents now flow through `SettingsIntentLauncher`. On Android XR / freeform / foldable the system honours the supplied launch bounds and the dialog opens centred at 80%×85% of the display. Phase 03 finalises documentation and catalog.

---

## Rollback Plan

Revert phase commit(s). The change is local to four files; no data migration, no schema, no config flag. After revert, behaviour returns to the pre-S0043 state with cut-off Settings dialog on XR but no functional regression elsewhere.
