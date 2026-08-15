# Phase 03 — Welcome Integration (Variant B)

**Strategic spec:** [`../S0101_unified-permission-onboarding.md`](../S0101_unified-permission-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

After the wizard finishes, `WelcomeActivity` navigates to `PermissionsManagementFragment` (built in Phase 05) full-screen, hosted in its own fragment container. Remove the dedicated permissions page from the wizard — the wizard now handles introduction only. `PermissionsManagementFragment` gets a Welcome-context "Continue to app" button that replaces the sequential permission flow currently inside `WelcomeActivity`.

---

## Prerequisites

- [x] Phase 05 is ✅ Done (`PermissionsManagementFragment` exists and is fully functional).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` | Modified | ≤ 250 |
| `app_v2/src/main/res/layout/activity_welcome.xml` | Modified | — |

> `WelcomeActivity.kt` is 686 lines — backup to `temp/` before editing.

---

## Steps

### Step 3.1 — Add Welcome-context mode to PermissionsManagementFragment

**Files:** `ui/settings/fragments/PermissionsManagementFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a `fromWelcome: Boolean` argument (via `companion fun newInstance(fromWelcome: Boolean)`).
> When `fromWelcome = true`:
> - Show `btn_continue_to_app` ("Continue" / "Продолжить" / "Продовжити") below the permissions list; this button is `View.GONE` in the Settings context.
> - On "Continue" tap: invoke `onWelcomeComplete()` callback (defined as an interface `WelcomeCompleteListener` on the hosting activity).
> - Do NOT show the activity's back-navigation arrow (call `(activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)`).
> Add `btn_continue_to_app` to `fragment_permissions_management.xml` (bottom, always-enabled, outside the scrolling list).

**Verification:**

- `Grep` — `fromWelcome` present in `PermissionsManagementFragment.kt`.
- `Grep` — `btn_continue_to_app` present in `app_v2/src/main/res/layout/fragment_permissions_management.xml`.
- `Grep` — `WelcomeCompleteListener` present in `PermissionsManagementFragment.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: ui/settings/fragments/PermissionsManagementFragment.kt (+25 LOC). fromWelcome arg, WelcomeCompleteListener interface, btn_continue_to_app wiring. Dev log recorded.

---

### Step 3.2 — Remove permissions page from wizard

**Files:** `ui/welcome/WelcomePagerAdapter.kt`, `ui/welcome/WelcomeActivity.kt`
**Depends on:** — start of phase (independent)

**Prompt for developer:**

> Backup `WelcomeActivity.kt` to `temp/` (timestamped).
>
> In `WelcomeActivity.setupViewPager()`: remove the `WelcomePage(isPermissionsPage = true, ...)` entry. The wizard now ends on the last introduction/feature page (or the Default Player page if present).
>
> In `WelcomePagerAdapter`: `VIEW_TYPE_PERMISSIONS` and `PermissionsViewHolder` may remain for now (do not delete referenced view types); simply ensure no page with `isPermissionsPage = true` is added to the list.
>
> Remove the fields and methods that are now dead code due to removing the permissions page:
> `hasTriggeredLastPagePermissionRequest`, `requestPermissions()` call in `updateUI()`.

**Verification:**

- `Grep` — `isPermissionsPage = true` returns zero hits in `WelcomeActivity.kt`.
- `Grep` — `hasTriggeredLastPagePermissionRequest` returns zero hits in `WelcomeActivity.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Files: ui/welcome/WelcomeActivity.kt (removed isPermissionsPage=true page, removed hasTriggeredLastPagePermissionRequest field + all refs). Backup in temp/. Dev log recorded.

---

### Step 3.3 — Add fragment container to WelcomeActivity and navigate on finish

**Files:** `ui/welcome/WelcomeActivity.kt`, `res/layout/activity_welcome.xml`
**Depends on:** Steps 3.1, 3.2

**Prompt for developer:**

> In `activity_welcome.xml`: add a `FragmentContainerView @id/fragment_container_welcome` overlaying the root — initially `View.GONE`.
>
> In `WelcomeActivity`:
> - Implement `WelcomeCompleteListener` (from Step 3.1).
> - In `finishWelcome()` (called when wizard's "Finish" or "Skip" is tapped): instead of the current sequential permission chain, show `fragment_container_welcome`, hide the ViewPager + navigation UI, and commit `PermissionsManagementFragment.newInstance(fromWelcome = true)` into `fragment_container_welcome`.
> - In `onWelcomeComplete()`: hide `fragment_container_welcome`, call the existing `completeWelcomeFlow()` → `goToMainActivity()` logic.
> - Remove the now-dead sequential permission methods: `requestManageMediaPermissionIfNeeded()`, `requestAllFilesAccessPermissionIfNeeded()`, `requestBatteryOptimizationIfNeeded()`, `requestNotificationsPermissionIfNeeded()`, `continueSpecialPermissionsFlowOrComplete()`. These are now handled inside `PermissionsManagementFragment`.
> - Timber debug tag: `Timber.d("S0101: welcome navigating to PermissionsManagementFragment")`

**Verification:**

- `Grep` — `S0101: welcome navigating to PermissionsManagementFragment` present in `WelcomeActivity.kt`.
- `Grep` — `fragment_container_welcome` present in `WelcomeActivity.kt`.
- `Grep` — `requestManageMediaPermissionIfNeeded` returns zero hits in `WelcomeActivity.kt`.
- `Grep` — `continueSpecialPermissionsFlowOrComplete` returns zero hits in `WelcomeActivity.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 4/4 PASS. Files: ui/welcome/WelcomeActivity.kt (implements WelcomeCompleteListener, finishWelcome→PermissionsManagementFragment, removed 5 sequential permission methods + launchers + dead fields), res/layout/activity_welcome.xml + sw480dp + sw720dp (FragmentContainerView added to all three variants). Dev log recorded.

---

### Step 3.4 — Trilingual strings for "Continue to app" button

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 3.1

**Prompt for developer:**

> Add string key `perm_continue_to_app`:
> - EN: "Continue to app"
> - RU: "Продолжить"
> - UK: "Продовжити"
>
> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_continue_to_app"` to verify parity.

**Verification:**

- `Grep` — `perm_continue_to_app` present in all three `strings.xml` locales.
- `scripts/check_strings_localized.ps1 -KeyPrefix "perm_continue_to_app"` exits with code 0.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. check_strings_localized.ps1 exit 0. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 3.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 03 wires the Welcome flow to the shared permissions screen. The `S0101:` Timber tag in `WelcomeActivity` is the verification signal. Phase 06 migrates remaining ad-hoc permission calls from other Settings fragments.

---

## Rollback Plan

Revert phase commit(s). `WelcomeActivity.kt` backup in `temp/` restores the original sequential permission flow. No data migration involved.
