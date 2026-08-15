# Phase 05 - Re-entry Fixes

**Strategic spec:** [`../S0398_welcome-skeleton-form-pages.md`](../S0398_welcome-skeleton-form-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Make a re-run of welcome from Settings safe: apply the profile preset only when the profile actually changed (with a warning, as the Settings reapply does), return to the caller instead of `CLEAR_TASK`, and keep page pickers pre-populated from persisted state. `welcome_prefs/welcome_completed` is never renamed (carries S0327 migration).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 700 |

> The Step 05.1 confirm event (`WelcomeEvent.ConfirmProfilePresetReapply`) is emitted by the ViewModel and consumed in `WelcomeActivity` (event observer + `settings_profile_warning` dialog); the Activity side of 05.1 lives alongside the 05.2 Activity edits.
> Step 05.3 also touched `WelcomeViewModel.detectDeviceProfile` (Files-Touched extension) to pre-select the stored profile on re-entry - required so the 05.1 unchanged-profile skip works without a spurious confirm.

---

## Steps

### Step 05.1 - Apply preset only on profile change, with warning on re-entry

**Files:** `ui/welcome/WelcomeViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `saveDeviceProfile(...)`, before calling `ApplyProfilePresetUseCase`, compare the chosen profile to the currently stored one (read via the device-profile repository). When the profile is unchanged, skip the preset apply entirely (just persist the profile/source) so a re-run does not overwrite user-tuned settings. When it changed AND this is a re-entry (welcome already completed once - read the existing `welcome_completed` flag, do not rename it), require the same confirmation the Settings path uses (mirror `GeneralSettingsProfileHelper`'s warn-before-apply); surface a confirm callback to the Activity rather than applying silently. First-run (welcome not yet completed) keeps applying without a prompt. Name the repository read and the use-case call.

**Verification:**

- `Grep` - `saveDeviceProfile` present in WelcomeViewModel.kt.
- `Grep` - a profile-equality comparison guarding `applyProfilePreset` (preset skipped when unchanged) is present.
- `Grep` - `welcome_completed` read (not renamed) present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. `saveDeviceProfile` now reads `isWelcomeCompleted()` (`welcome_completed`, not renamed) + previous profile via `deviceProfileRepository.getCurrentProfile().first().type` before overwriting. Branches: first run → `applyProfilePreset` (extracted helper); re-entry & `finalType == previousType` → skip (keeps tuned settings); re-entry & changed → `sendEvent(WelcomeEvent.ConfirmProfilePresetReapply)`. WelcomeActivity observes `viewModel.events` and shows the `settings_profile_warning` dialog (reuses Settings strings); confirm → `viewModel.confirmProfilePresetReapply`. Catalog re-synced; gates green. Dev log recorded (both files).

---

### Step 05.2 - Return to caller instead of CLEAR_TASK on re-entry

**Files:** `ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In `goToMainActivity()` (the completion exit), stop using `NEW_TASK|CLEAR_TASK` when welcome was re-entered from Settings (the existing first-run-vs-reentry signal is the `welcome_completed`/`first_run_after_welcome` flags). On re-entry, finish the Activity to return to the caller (Settings backstack) instead of clearing the task; keep the original first-run behaviour (Main→Settings redirect) for the genuine first run. Do not change the `welcome_completed` write.

**Verification:**

- `Grep` - `goToMainActivity` present in WelcomeActivity.kt.
- `Grep` - the re-entry branch calls `finish()` (return to caller) rather than only `CLEAR_TASK`.
- `Grep` - `welcome_completed` still written.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. `goToMainActivity()` else branch (re-entry: `isFirstRunAfterWelcome()` false) now just `finish()` to return to the caller (Settings launches Welcome via plain Intent, so the back stack resumes Settings), replacing the `NEW_TASK|CLEAR_TASK` MainActivity relaunch. First-run branch (Main→Settings `TaskStackBuilder`) and `viewModel.setWelcomeCompleted()` write unchanged. `CLEAR_TASK` hits = 0. Gates green. Dev log recorded.

---

### Step 05.3 - Verify page-0 pre-population from persisted state

**Files:** `ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm page 0 pre-populates on re-entry: the language picker checks the current `LocaleHelper` language, the theme picker (Phase 03) checks the current `ColorThemePrefs` value, and the profile card shows the stored/selected profile. If any picker initializes to a hardcoded default instead of the persisted value, fix it to read the persisted state. This step changes code only if a picker is found mis-initialized; otherwise it records the verified existing wiring in the Step Log.

**Verification:**

- `Grep` - the theme picker pre-check reads `ColorThemePrefs` (added in Phase 03) and the language picker reads `LocaleHelper.getLanguage`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 1/1 PASS. Language picker reads `LocaleHelper.getLanguage` (adapter:206) and theme picker reads `ColorThemePrefs.getMode` (adapter:235) - both pre-populate correctly. Found a real gap: the profile card seeded `selectedProfile` from the detector even on re-entry, so re-finishing without changes would differ from the stored profile and wrongly trigger the 05.1 confirm dialog. Fixed in `WelcomeViewModel.detectDeviceProfile` (Files-Touched extension): on `isWelcomeCompleted()` pre-select the stored profile (`deviceProfileRepository.getCurrentProfile().first().type`, availability-guarded), first run keeps the recommendation. Gates green. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL (1m45s, v2.60.6110.209).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry for every modified file (WelcomeViewModel.kt, WelcomeActivity.kt).

---

## Handoff Notes to Next Phase

Re-running welcome from Settings no longer silently reapplies presets or wipes the Settings backstack. Final phase regenerates the catalog and verifies all flavors.

---

## Rollback Plan

Revert phase commit(s) - restores the unconditional preset apply and `CLEAR_TASK` exit. No data migration.
