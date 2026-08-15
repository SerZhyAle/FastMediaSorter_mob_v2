# Phase 02 - Page Content Research

**Strategic spec:** [`../S0395_welcome-screens-redesign-research.md`](../S0395_welcome-screens-redesign-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 4 / 4
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Produce per-page artifacts for target pages 0, 1, 2, 4 (strategic §6.2, §6.3, §6.4, §6.6): data sources, availability rules, persistence target, skip behavior for each.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`research/01__current-flow-inventory.md` exists).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0395_welcome-screens-redesign-research/research/02__page0-language-theme.md` | New | ≤ 300 |
| `PLAN/S0395_welcome-screens-redesign-research/research/03__page1-device-profiles.md` | New | ≤ 300 |
| `PLAN/S0395_welcome-screens-redesign-research/research/04__page2-network-toggles.md` | New | ≤ 300 |
| `PLAN/S0395_welcome-screens-redesign-research/research/06__page4-functionality-toggles.md` | New | ≤ 400 |

---

## Steps

### Step 02.1 - Research page 0: language + colour theme

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/02__page0-language-theme.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Read `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt`, the existing language-picker wiring in `WelcomeActivity.kt`/`WelcomePagerAdapter.kt`, and `LocaleHelper` usage. Author the artifact (uniform skeleton) answering: what colour-theme options exist and how a theme change is applied (recreate? `AppCompatDelegate`? prefs-only until restart); what the current language switch does to the pager (recreate + position restore mechanism - find it); whether language + theme + greeting fit one page without overload; how page-0 state survives the recreate cycle. Conclude with a viable page-0 composition and its recreate-safety requirements.

**Verification:**

- `Glob` - `research/02__page0-language-theme.md` exists under the ticket folder.
- `Grep` - `## Conclusion` present.
- `Grep` - `recreate` (case-insensitive) present (recreate behavior analysed).

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Key facts: theme = AUTO/LIGHT/DARK night-mode setting with dual persistence (DataStore + SP mirror); welcome force-light kills live preview; Settings restartApp pattern is state-destroying and must not be reused mid-flow. Recommendation: deferred theme apply + helper copy.

---

### Step 02.2 - Research page 1: device profiles inline

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/03__page1-device-profiles.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Read `app_v2/src/main/java/com/sza/fastmediasorter/data/model/DeviceProfile.kt` (profile type enum), `ui/profile/DeviceProfilePickerDialogFragment.kt`, `ui/profile/DeviceProfileTileAdapter.kt`, `ui/profile/DeviceProfileAvailability.kt`, `data/preset/DeviceProfilePresetApplier.kt`, `dev/DEVICE_PROFILE_PRESET_MATRIX.md`, and `app_v2/src/main/assets/device_profile_presets.csv`. Author the artifact answering: full profile list and per-flavor availability (incl. VR profile gating via the availability abstraction); whether N profile buttons fit a single onboarding page on phone/tablet/TV/VR form factors (reuse tile grid?); how the recommended profile is detected and badged; what preset application changes when the user picks; whether the existing picker dialog remains needed outside onboarding (it is opened from settings too - check callers). Conclude with the inline-buttons layout feasibility and reuse plan.

**Verification:**

- `Glob` - `research/03__page1-device-profiles.md` exists under the ticket folder.
- `Grep` - `## Conclusion` present.
- `Grep` - `recommended` (case-insensitive) present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Key facts: 10/11 tiles per flavor, pre-selection free via state seeding, Skip silently discards explicit pick (must fix), preset already implies all-files for some profiles (feeds artifact 05), D-pad row-edge page-flip hazard. Recommendation: dedicated page, compact tiles, Skip-semantics fix.

---

### Step 02.3 - Research page 2: network toggles over S0391

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/04__page2-network-toggles.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Read `PLAN/S0391_remote-source-runtime-toggles.md` in full (Approved, NOT implemented - no tactical folder exists). Author the artifact answering: which toggle set S0391 will introduce (SMB, SFTP, FTP, Google Drive, OneDrive, Dropbox) and where it persists; what onboarding granularity is appropriate - per-source, grouped (network protocols / cloud providers), or single question with detail deferred to settings; how the onboarding choice feeds the same persisted settings (no parallel store - strategic §3.2); per-flavor visibility (cloud toggles hidden where cloud unsupported by build); the hard dependency: page-2 dev ticket cannot ship before S0391 - state what, if anything, page 2 shows if onboarding redesign lands first (page absent vs placeholder). Conclude with recommended granularity and dependency handling.

**Verification:**

- `Glob` - `research/04__page2-network-toggles.md` exists under the ticket folder.
- `Grep` - `S0391` present.
- `Grep` - `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. S0391 read in full inline (no agent). Executed ahead of 02.1/02.2 (independent steps, agents still running). Recommendation: two group toggles bulk-writing six S0391 per-source states; page-2 ticket = BlockByOtherTask on S0391.

---

### Step 02.4 - Research page 4: functionality toggles semantics

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/06__page4-functionality-toggles.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Read `PLAN/S0386_ondemand-ocr-translation-delivery.md` + `PLAN/S0386_ondemand-ocr-translation-delivery/INDEX.md` + `delivery/INVENTORY.md`, plus `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableInventory.kt`, `DeliverableCapability.kt`, `DeliverableSet.kt`, `ui/delivery/ExtensionsManagerFragment.kt`, `ui/delivery/DeliveryEnableInterceptor.kt`, `ui/delivery/DeliveryPromptDialogFragment.kt`. For each owner-proposed toggle - file-manager mode (all files), use audio/video/documents, OCR, translation, VR - establish: which existing setting or capability it maps to (grep the settings layer for file-manager/all-files mode and media-type enablement; do not invent settings); whether enabling requires a deliverable download (S0386 element), a permission, or a plain pref flip; the "if available" rule (flavor support, ABI, device class); what the VR toggle can even mean in standard vs the VR family (likely: profile/feature absent in standard - record the honest answer); how the Extensions Manager dialog is opened today and whether it can be launched from a welcome page. Author the artifact and conclude with a toggle → action mapping table and visibility rules.

**Verification:**

- `Glob` - `research/06__page4-functionality-toggles.md` exists under the ticket folder.
- `Grep` - `OCR` present and `VR` present.
- `Grep` - `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Toggle→action map complete: allFiles/support* settings exist; documents needs 4-field aggregation; OCR/translation reuse S0386 enable-path but need prompt-free app-scoped downloads; VR maps to nothing in standard (hide via availability contract); page-1 preset overlap → "preset first, toggles render post-preset defaults".

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] No source/config file modified - S0395 changes confined to the ticket folder + dev/CHANGELOG (pre-existing unrelated DEBUG-v013 working-tree changes noted in Phase 01).
- [x] Dev log entry added for each artifact via post-change.ps1 (Doc): 02, 03, 04 recorded; 06 recorded 2026-06-10.

---

## Handoff Notes to Next Phase

Artifact 06 (toggle semantics incl. all-files mode and download triggers) is mandatory input for Phase 03 (permission ordering + download UX).

---

## Rollback Plan

Delete the artifact files - no code or data surface touched.
