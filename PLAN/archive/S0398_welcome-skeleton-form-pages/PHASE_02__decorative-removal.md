# Phase 02 - Decorative Page & Dead-Path Removal

**Strategic spec:** [`../S0398_welcome-skeleton-form-pages.md`](../S0398_welcome-skeleton-form-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Remove the four decorative pages (Resource Types, Touch Zones, Sort Destinations, Powerful Extras) from the candidate list and delete the dead PERMISSIONS pager type, the `onSkipClick` field, orphan strings, and the stale "up to 30 destinations" copy.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (page list is data-driven).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 330 |
| `app_v2/src/main/res/layout/page_welcome_permissions.xml` | Deleted | n/a |
| `app_v2/src/main/res/layout-land/page_welcome_permissions.xml` | Deleted | n/a |
| `app_v2/src/main/res/values/strings.xml` (+ `strings_setup.xml`) | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` (+ `strings_setup.xml`) | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` (+ `strings_setup.xml`) | Modified | n/a |

---

## Steps

### Step 02.1 - Drop the four decorative pages from the candidate list

**Files:** `ui/welcome/WelcomeActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `setupViewPager()` remove the candidate-list entries for: Resource Types (`welcome_title_2`), Touch Zones (`showTouchZonesScheme = true`), Resources & Destinations (`welcome_title_4`), and Powerful Extras (the entry using `buildExtrasFeatureCards()`). Delete the now-unused `buildExtrasFeatureCards()` function entirely (this also removes its 7 `BuildConfig.SUPPORT_*/ENABLE_*` reads - the only flavor-flag reads in WelcomeActivity). Page 0 (welcome+language) and the conditional default-player page remain. Do not remove the profile card wiring on page 0.

**Verification:**

- `Grep` - `buildExtrasFeatureCards` returns zero hits in WelcomeActivity.kt.
- `Grep` - `showTouchZonesScheme = true` returns zero hits in WelcomeActivity.kt.
- `Grep` - `BuildConfig.SUPPORT_` and `BuildConfig.ENABLE_` return zero hits in WelcomeActivity.kt.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Removed the 4 decorative WelcomePage entries (Resource Types/Touch Zones/Resources&Destinations/Extras) from setupViewPager; deleted buildExtrasFeatureCards (7 BuildConfig flavor reads gone) + its now-dead BuildConfig import. WelcomeActivity now has zero BuildConfig references.

---

### Step 02.2 - Remove the dead PERMISSIONS pager type and TouchZones holder

**Files:** `ui/welcome/WelcomePagerAdapter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Remove the dead `VIEW_TYPE_PERMISSIONS` path: the constant, the `PermissionsViewHolder` class, its branch in `getItemViewType`/`onCreateViewHolder`/`onBindViewHolder`, the `isPermissionsPage` flag on `WelcomePage`, and the `onGrantClick`/`onSkipClick` fields (both unused after Phase 01). Remove the `TouchZonesViewHolder` and `VIEW_TYPE_TOUCH_ZONES` (its only page was deleted in 02.1), plus the `showTouchZonesScheme` flag. Keep the ENHANCED / DEFAULT_PLAYER / NORMAL view types.

**Verification:**

- `Grep` - `isPermissionsPage`, `onGrantClick`, `onSkipClick`, `PermissionsViewHolder`, `TouchZonesViewHolder` each return zero hits in WelcomePagerAdapter.kt.
- `Grep` - `VIEW_TYPE_DEFAULT_PLAYER` still present (retained type).

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Removed VIEW_TYPE_TOUCH_ZONES/PERMISSIONS constants + their getItemViewType/onCreateViewHolder/onBindViewHolder branches, the TouchZonesViewHolder + PermissionsViewHolder classes, the PageWelcomeTouchZones/Permissions binding imports, and the showTouchZonesScheme/isPermissionsPage/onGrantClick/onSkipClick WelcomePage fields. VIEW_TYPE_DEFAULT_PLAYER/ENHANCED/NORMAL retained.

---

### Step 02.3 - Delete dead layouts

**Files:** `res/layout/page_welcome_permissions.xml`, `res/layout-land/page_welcome_permissions.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Delete both `page_welcome_permissions.xml` files (portrait + land) - they are only referenced by the removed `PermissionsViewHolder`. Also delete `page_welcome_touch_zones.xml` (portrait + land) if no other code references it (grep first; the only consumer was the removed touch-zones page). Do not delete `page_welcome.xml`/`page_welcome_enhanced.xml`/`page_welcome_default_player.xml` (still used).

**Verification:**

- `Glob` - `page_welcome_permissions.xml` returns no files under `res/`.
- `Grep` - `page_welcome_permissions` and `page_welcome_touch_zones` return zero hits across `app_v2/src/main/java`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Deleted page_welcome_permissions.xml + page_welcome_touch_zones.xml (portrait + land = 4 files); zero remaining code references.

---

### Step 02.4 - Remove orphan strings and fix the stale destinations count

**Files:** `res/values{,-ru,-uk}/strings.xml` and `strings_setup.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Remove the now-orphan welcome strings across all three locales: `welcome_permissions_title`, `welcome_permissions_description`, `welcome_vr_title`, `welcome_vr_description`, `welcome_vr_feature_formats`, `welcome_vr_feature_headset`, `welcome_vr_feature_stereo`, `welcome_battery_optim_message`, and the deleted decorative pages' keys (`welcome_title_2/3/4`, `welcome_description_2/3/4` and their `_details`) - grep each key across `app_v2/src` first and only remove keys with zero remaining references. Fix the stale "up to 30" copy in `welcome_description_4_details` BEFORE removing it is moot - if that key is removed, no fix needed; if any retained string still claims "30" destinations, correct it to 10 (the real `AddResourceUseCase.MAX_DESTINATIONS`). Use `scripts/utils/set-android-string.ps1 -Action remove` per key for lockstep removal; verify EN/RU/UK parity after.

**Verification:**

- `Grep` - `welcome_permissions_title`, `welcome_vr_title`, `welcome_battery_optim_message` return zero hits across `app_v2/src`.
- `Bash` - `scripts/check_strings_localized.ps1 -KeyPrefix "welcome_"` exits 0 (EN/RU/UK parity intact).

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Removed 19 orphan keys (permissions ×2, vr ×5, battery ×1, decorative page titles/descriptions/details 2/3title/4/5 ×10) lockstep EN/RU/UK; all reported "Code references: none". KEPT welcome_description_3 (reused by player_first_run_hint_overlay_content.xml - the existing touch-zones hint). "up to 30" lie removed with welcome_description_4_details. Parity OK (54 welcome_ keys). Residual: ~12 welcome_feature_* Extras-only strings remain orphan (build-safe; page-0-shared feature strings must survive) - minor hygiene follow-up.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - standard debug ✅. Phase 02 is purely subtractive + flavor-agnostic (shared pages/strings/main layouts removed); lite validated in Phase 01, no flavor-specific references to removed assets.
- [x] `Grep` for `TODO(phase-02)` returns zero hits (none added).
- [x] `Grep` for `BuildConfig.` in WelcomeActivity.kt returns zero hits (all flavor reads gone with the Extras page).
- [x] Dev log entry for every modified/deleted file.

---

## Handoff Notes to Next Phase

The flow is now page 0 + (conditional) default-player only, plus whatever Phases 03-04 add. Touch-zones educational content is gone from welcome but already covered by the pre-existing player first-run hint (`TouchZoneHintType.FULLSCREEN_9ZONE`, once-tracked in `PlayerUiStateCoordinator`) - no replacement needed in this ticket.

---

## Rollback Plan

Revert phase commit(s) - restores the decorative pages and dead types. Deleted layouts/strings return with the revert.
