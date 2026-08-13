# Phase 02 - Settings Profile Flows

**Strategic spec:** [../S0376_file-manager-predefined-resource.md](../S0376_file-manager-predefined-resource.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Wire the shared predefined-resource creator into Welcome, Settings profile re-apply, and the General settings All Files CTA.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [x] Working tree is on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsProfileViewModel.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsProfileHelper.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 360 |
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | ≤ 280 |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | ≤ 280 |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | ≤ 280 |

---

## Steps

### Step 02.1 - Auto-create the resource from profile application flows

**Files:** `WelcomeViewModel.kt`, `SettingsProfileViewModel.kt`, `GeneralSettingsProfileHelper.kt`
**Depends on:** 01.2
**Prompt for developer:** After a device profile preset is applied, call the shared predefined-resource creator when the selected profile implies all-files. Keep Welcome automatic, but in Settings require an explicit confirmation dialog before creating the missing resource during profile re-apply.
**Verification:** Welcome applies the resource automatically for all-files profiles, and Settings shows a confirmation before saving a profile that would create the missing resource.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Welcome auto-creates the predefined resource for all-files profiles, while Settings profile re-apply now gates creation behind a dedicated confirmation dialog.

### Step 02.2 - Add the General settings CTA beside All Files

**Files:** `GeneralSettingsFragment.kt`, `GeneralSettingsViewSetupHelper.kt`, `strings_settings.xml`, `values-ru/strings_settings.xml`, `values-uk/strings_settings.xml`
**Depends on:** 02.1
**Prompt for developer:** Inject a trailing button into the existing `rowAllFiles` settings row that creates the predefined resource, shows a brief success toast, and hides itself once the resource exists. Add EN/RU/UK strings for the button, success feedback, and the Settings confirmation copy. Strings must pass `COMMUNICATION_POLICY.md` §6.
**Verification:** The row renders the trailing CTA only while the predefined resource is missing, button press creates the resource, and all new strings exist in EN/RU/UK.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Added the trailing `Create resource "All Files"` CTA to the existing General settings `All Files` row with EN/RU/UK copy and success feedback.

### Step 02.3 - Keep the CTA state synchronized with live resources

**Files:** `GeneralSettingsViewSetupHelper.kt`, `GeneralSettingsFragment.kt`
**Depends on:** 02.2
**Prompt for developer:** Observe the resource list in the General settings helper so the CTA visibility reacts to initial load, manual creation, and profile-triggered creation without requiring a fragment restart.
**Verification:** Opening General settings with an existing predefined resource hides the CTA, and creating the resource from either trigger removes the CTA in the same session.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. The CTA now follows the live resource list and hides immediately after manual or profile-driven resource creation in the same session.

---

## Phase Done Criteria

- [x] Profile application and Settings confirmation both use the same creator use case.
- [x] The General settings All Files row exposes the CTA only while the predefined resource is missing.
- [x] EN/RU/UK copy exists for the CTA, confirmation, and feedback messages.
