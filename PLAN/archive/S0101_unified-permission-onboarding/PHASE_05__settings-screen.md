# Phase 05 — Settings Permission Management Screen

**Strategic spec:** [`../S0101_unified-permission-onboarding.md`](../S0101_unified-permission-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Add a "Manage Permissions" entry in General Settings that opens a new `PermissionsManagementFragment` showing all registry entries with live status. Replace the existing ad-hoc permission buttons in `GeneralSettingsPermissionsHelper` with a single redirect to this screen.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` | New | ≤ 250 |
| `app_v2/src/main/res/layout/fragment_permissions_management.xml` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 500 |

> Check `GeneralSettingsFragment.kt` size before editing — backup to `temp/` if > 500 LOC.
> Check `SettingsActivity.kt` size before editing — backup to `temp/` if > 500 LOC.

---

## Steps

### Step 5.1 — Create fragment_permissions_management.xml + PermissionsManagementFragment

**Files:** `res/layout/fragment_permissions_management.xml`, `ui/settings/fragments/PermissionsManagementFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `fragment_permissions_management.xml`. Root: `ConstraintLayout`. Contents:
> - `Button @id/btn_grant_all` (top, full-width) — "Grant All" / "Выдать все" / "Надати всі"; visible only when at least one permission is DENIED (not PERMANENTLY_DENIED, not NOT_APPLICABLE).
> - `Button @id/btn_open_system_settings` (below btn_grant_all, secondary style) — "Open App Settings" / "Открыть настройки системы" / "Відкрити налаштування системи"; always visible.
> - `RecyclerView @id/rv_permissions` (fills remaining space below).
> - `Button @id/btn_continue_to_app` (bottom, `View.GONE` by default — shown in Welcome context; see Phase 03).
>
> Create `PermissionsManagementFragment.kt` in `ui/settings/fragments/`. Annotate with `@AndroidEntryPoint`.
> - Inject `PermissionRegistryRepository`, `CheckPermissionStatusUseCase`, `RequestContextualPermissionUseCase`.
> - In `onViewCreated`: build the item list from `PermissionRegistryRepository.getEntries()` including group headers; set up `PermissionRowAdapter`; `statusProvider` calls `CheckPermissionStatusUseCase`.
> - `btn_grant_all` tap: collect all entries where status is `DENIED`; launch `registerForActivityResult(RequestMultiplePermissions)` with their manifest names; on callback → `PermissionRowAdapter.refresh()` + update `btn_grant_all` visibility.
> - `btn_open_system_settings` tap: launch `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the app package.
> - `onActionClick` per item: if `DENIED` → single `RequestPermission`; if `PERMANENTLY_DENIED` → `PermissionDenialHandler.handle()`; if `GRANTED` → open app settings.
> - `onResume`: `PermissionRowAdapter.refresh()` + update `btn_grant_all` visibility.
> - Timber debug tag: `Timber.d("S0101: PermissionsManagementFragment loaded")`

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` exists.
- `Grep` — `class PermissionsManagementFragment` matches exactly once.
- `Grep` — `S0101: PermissionsManagementFragment loaded` present in that file.
- `Glob` — `app_v2/src/main/res/layout/fragment_permissions_management.xml` exists.
- `Grep` — `rv_permissions` present in `fragment_permissions_management.xml`.
- `Grep` — `btn_grant_all` present in `fragment_permissions_management.xml`.
- `Grep` — `btn_open_system_settings` present in `fragment_permissions_management.xml`.
- `Grep` — `btn_continue_to_app` present in `fragment_permissions_management.xml`.
- `Glob` — `app_v2/src/main/res/layout-land/fragment_permissions_management.xml` does NOT exist (landscape variant not needed for settings fragment hosted in scrollable container).

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 9/9 PASS. Files: res/layout/fragment_permissions_management.xml (new), res/layout/item_permission_entry.xml (new, implied), ui/settings/fragments/PermissionRowAdapter.kt (new, implied by prompt), ui/settings/fragments/PermissionsManagementFragment.kt (new, 99 LOC). landscape-land counterpart confirmed absent. Dev log recorded.

---

### Step 5.2 — Add "Manage Permissions" entry to GeneralSettingsFragment

**Files:** `ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 5.1

**Prompt for developer:**

> In `GeneralSettingsFragment`, add a "Manage Permissions" row (using the existing settings row pattern — a `TextView` or clickable `LinearLayout` in the existing permissions section). On click, navigate to `PermissionsManagementFragment` via the `SettingsActivity` fragment back stack: `requireActivity().supportFragmentManager.beginTransaction().replace(R.id.<container_id>, PermissionsManagementFragment()).addToBackStack(null).commit()`.
> Identify the correct container ID from `SettingsActivity` before writing.

**Verification:**

- `Grep` — `PermissionsManagementFragment` present in `GeneralSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 1/1 PASS. Added headerPermissions click → PermissionsManagementFragment via android.R.id.content (no dedicated settings container in ViewPager2 layout). Files: GeneralSettingsFragment.kt (+9 LOC). Dev log recorded.

---

### Step 5.3 — Replace ad-hoc permission buttons in GeneralSettingsPermissionsHelper

**Files:** `ui/settings/helpers/GeneralSettingsPermissionsHelper.kt`
**Depends on:** Steps 5.1, 5.2

**Prompt for developer:**

> Backup `GeneralSettingsPermissionsHelper.kt` to `temp/` (timestamped).
> The current helper manages 5 ad-hoc permission buttons (`btnLocalFilesPermission`, `btnNetworkPermission`, `btnManageMediaPermission`, `btnNotificationPermission`, `btnBatteryOptimizationPermission`). After Phase 05, these are surfaced via `PermissionsManagementFragment`.
> Replace `updatePermissionButtonsState()` content with a single visibility/text update for the "Manage Permissions" shortcut row (if it exists in the layout) pointing to the new screen. Remove per-button click handlers that are now covered by `PermissionsManagementFragment`.
> Retain any button that the layout `fragment_settings_general.xml` still references (do not break the binding) — hide those buttons rather than remove them if their IDs still exist in the layout.

**Verification:**

- `Grep` — `PermissionsManagementFragment` present in `GeneralSettingsPermissionsHelper.kt`.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Files: ui/settings/helpers/GeneralSettingsPermissionsHelper.kt (modified: hides 5 ad-hoc buttons, routes handleLocalFilesPermissionAction() → navigateToPermissionsManagement()). Backup in temp/. Dev log recorded.

---

### Step 5.5 — Add trilingual strings for Grant All and Open Settings buttons

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 5.1

**Prompt for developer:**

> Add string keys:
> - `perm_btn_grant_all` — "Grant All" / "Выдать все" / "Надати всі"
> - `perm_btn_open_system_settings` — "Open App Settings" / "Открыть настройки системы" / "Відкрити налаштування системи"
>
> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_btn_"` to verify parity.

**Verification:**

- `Grep` — `perm_btn_grant_all` present in all three `strings.xml` locales.
- `Grep` — `perm_btn_open_system_settings` present in all three `strings.xml` locales.
- `scripts/check_strings_localized.ps1 -KeyPrefix "perm_btn_"` exits with code 0.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. check_strings_localized.ps1 exit 0. Dev log recorded.

---

### Step 5.4 — Add trilingual strings for settings screen

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 5.1

**Prompt for developer:**

> Add:
> - `settings_manage_permissions` ("Manage Permissions" / "Управление разрешениями" / "Керування дозволами") — settings entry label.
> - `settings_manage_permissions_desc` (one-line description shown below the label) — EN/RU/UK.
> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_manage_permissions"` to verify parity.

**Verification:**

- `Grep` — `settings_manage_permissions` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `settings_manage_permissions` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `settings_manage_permissions` present in `app_v2/src/main/res/values-uk/strings.xml`.
- `scripts/check_strings_localized.ps1 -KeyPrefix "settings_manage_permissions"` exits with code 0.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 4/4 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. check_strings_localized.ps1 exit 0. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 5.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 05 delivers the Settings entry point. Phase 06 wires the contextual request into existing feature toggles (S0100, S0035).

---

## Rollback Plan

Revert phase commit(s). `GeneralSettingsPermissionsHelper.kt` backup in `temp/` restores the old per-button behavior if needed.
