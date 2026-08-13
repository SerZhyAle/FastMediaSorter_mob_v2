# Phase 03 - Settings useful-links entry

**Strategic spec:** [`../S0994_companion-publish-folders-help-link.md`](../S0994_companion-publish-folders-help-link.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-07-11
**Completed:** 2026-07-11 (compile proof: `fc` - BUILD SUCCESSFUL, kapt validated Hilt graph)

---

## Objective

Add a new "useful links" button in Settings > General that opens the publish-folders guide, shown only when companion import is available on the build (hidden on lite/vr).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (URL accessor + label string exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 700 |

> Landscape parity (MANDATORY): both `layout/` and `layout-land/` variants of `fragment_settings_general.xml` exist - edit both.

---

## Steps

### Step 03.1 - Add the link button to both layout variants

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new link button (e.g. `btnCompanionPublishGuide`) in the useful-links block next to `btnUserGuide` / `btnHowToGuides`, matching their style/`?attr/` usage and D-pad `nextFocus*` chain. Label with `@string/companion_publish_folders_guide`. Apply the same edit to portrait and landscape variants; in landscape respect the existing multi-column arrangement of the link block. No hardcoded hex (Rule 19).

**Verification:**

- `Grep` - `btnCompanionPublishGuide` present in BOTH `layout/` and `layout-land/` variants.
- `Grep` - `@string/companion_publish_folders_guide` referenced in both variants.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - Verification PASS (2 hits/file in both variants). Added `btnCompanionPublishGuide` (`ic_open_in_browse`) after `btnHowToGuides` and into `flowDocLinks` `constraint_referenced_ids` in portrait + landscape.

---

### Step 03.2 - Wire the click and gate by companion availability

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `setupLinkButtons()`, wire the new button to open `SupportIntentFactory.companionPublishGuideUrl()` through the existing `openUrl(url, fallbackMsg)` helper (reuse the "No browser found" fallback string). Gate visibility by companion-import availability - the same signal that hides the SFTP/companion block on lite/vr (the `RemoteSourceGate` SFTP node used in `AddResourceFormManager`). This helper currently holds only `SettingsViewModel`; expose the availability as a read-only property on `SettingsViewModel` (or inject the gate) rather than a `BuildConfig.IS_*` guard in `src/main` (Rule 14). Hide the button when unavailable. Add the required `Timber.d("S0994: <entry-point>")` probe at this click flow entry.

**Verification:**

- `Grep` - `companionPublishGuideUrl(` referenced in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `btnCompanionPublishGuide` visibility is set from a companion/remote-source availability value (not a raw `BuildConfig.IS_` guard).
- `Grep` - `Timber.d("S0994:` present for this flow.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - Verification 3/3 PASS. Wired via `SupportIntentFactory.companionPublishGuideUrl()`; visibility = `viewModel.isCompanionImportAvailable` (new `SettingsViewModel` property backed by injected `@Singleton RemoteSourceAvailabilityGate.isEnabled(SFTP)`); `Timber.d("S0994:` probe present. kapt validated the Hilt graph in the `fc` build.

---

### Step 03.3 - Confirm no BuildConfig flavor guard leaked into main

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Verify the gating uses the capability node, not a flavor flag. There must be no new `BuildConfig.IS_LITE` / `IS_VR` / `SUPPORT_*` branch added around the new button in `src/main`.

**Verification:**

- `Grep` - zero new `BuildConfig.IS_` / `BuildConfig.SUPPORT_` occurrences around `btnCompanionPublishGuide` wiring.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - Verification PASS. Gating uses the capability node (`RemoteSourceAvailabilityGate`), no `BuildConfig.IS_*`/`SUPPORT_*` guard in `src/main` (Rule 14).

---

## Phase Done Criteria

- [ ] All three steps are `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the touched files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

New General link button exists and is a tracked settings entry - Phase 05 MUST regenerate the settings manifest/reference/annotations (Rule 22).

---

## Rollback Plan

Revert the phase commit - additive button + gating, no data migration. Settings-manifest regen in Phase 05 must be reverted together.
