# Phase 05 - Welcome UI Profile Selector

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 04
**Blocks:** Phase 07, 08
**Steps done:** 4 / 4
**Started:** 2026-06-02 15:35:00
**Completed:** 2026-06-02 15:45:00

---

## Objective

Add device profile selector to Welcome first-run flow, positioned under language picker inline on Page 1 (resolved by §6 Q7). Show recommended profile. User can select any profile or Skip; finishing welcome saves the chosen/recommended profile.

---

## Prerequisites

- [x] Phase 01, 04 are ✅ Done.
- [x] Welcome flow already exists.
- [x] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 100 |
| `app_v2/src/main/res/layout/page_welcome_enhanced.xml` | Modified | N/A |
| `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml` | Modified | N/A |
| `app_v2/src/main/res/values/strings.xml` | Modified | N/A |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | N/A |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | N/A |

---

## Steps

### Step 05.1 - Integrate Profile Selector in WelcomeViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt`
**Depends on:** - start of phase

**Status:** `[x] done`

---

### Step 05.2 - Add Selector UI elements in page_welcome_enhanced layouts

**Files:** `app_v2/src/main/res/layout/page_welcome_enhanced.xml`, `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml`
**Depends on:** Step 05.1

**Status:** `[x] done`

---

### Step 05.3 - Add localized profile selector strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 05.2

**Status:** `[x] done`

---

### Step 05.4 - Wire profile selection and save triggers in WelcomeActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt`
**Depends on:** Step 05.2

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles.
- [x] Manual test: first-run shows profile selector under language picker, with Recommended badge; Skip applies auto-profile; Select saves chosen profile.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entries for all touched files.

---

## Handoff Notes to Next Phase

Welcome profile selector is integrated directly into Page 1 under the language picker (as mandated by strategic spec §6 Q7). ViewModel runs detection on init, publishes state, and WelcomeActivity binds it reactively to WelcomePagerAdapter. On Skip, AUTO_SKIPPED source is applied. On Finish, MANUAL_SELECTION is applied.
Phase 06 adds Settings profile selector dialog and warning UI.

---

## Rollback Plan

Revert welcome activity and viewmodel changes.
