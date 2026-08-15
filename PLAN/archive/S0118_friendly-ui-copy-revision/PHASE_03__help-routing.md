# Phase 03 - Help Routing

**Strategic spec:** [`../S0118_friendly-ui-copy-revision.md`](../S0118_friendly-ui-copy-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Centralize documentation, bug-report, and review destinations so S0118 can offer one contextual next step without scattering URLs and mail targets across screens.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic support destinations remain website, `sza@ukr.net`, and Google Play reviews.
- [ ] No parallel task is rewriting `InputHelpLinkResolver` or settings link routing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportDestination.kt` | New | <= 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt` | New | <= 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpDialogFragment.kt` | Modified | <= 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpLinkResolver.kt` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | <= 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactoryTest.kt` | New | <= 240 |

---

## Steps

### Step 03.1 - Add support destination primitives

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportDestination.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a small destination model for the three allowed S0118 follow-up actions: open help, report a problem, and leave product feedback. Keep the model independent from any one screen.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportDestination.kt` exists.
- `Grep` - `enum class SupportDestination` or `sealed class SupportDestination` matches exactly once.
- `Grep` - `HELP|REPORT_PROBLEM|LEAVE_FEEDBACK` present.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: SupportDestination.kt (+18 LOC). Dev log recorded.

---

### Step 03.2 - Implement the shared intent factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Implement a shared factory that resolves localized documentation URLs, the `mailto:sza@ukr.net` bug-report intent, and the Google Play review intent. Make the API simple enough for settings, dialogs, and future error surfaces to call without duplicating URI construction.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt` exists.
- `Grep` - `class SupportIntentFactory` or `object SupportIntentFactory` matches exactly once.
- `Grep` - `mailto:sza@ukr.net` present exactly once inside the factory.
- `Grep` - `play.google.com` or `market://details` present inside the factory.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: SupportIntentFactory.kt (+93 LOC). Dev log recorded.

---

### Step 03.3 - Migrate current help entry points

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpDialogFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpLinkResolver.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Replace inline docs and email URI creation with the shared support factory. Keep the current destinations and locale routing, but remove the need for each surface to know raw URLs or the bug-report email address.

**Verification:**

- `Grep` - `SupportIntentFactory` present in `InputHelpDialogFragment.kt`.
- `Grep` - `SupportIntentFactory` present in `GeneralSettingsLogHelper.kt`.
- `Grep` - `https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/` returns zero hits in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `mailto:sza@ukr.net` returns zero hits outside `SupportIntentFactory.kt`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: InputHelpDialogFragment.kt, InputHelpLinkResolver.kt, GeneralSettingsLogHelper.kt, GeneralSettingsViewSetupHelper.kt, SupportIntentFactory.kt (+openUrl helper). Dev log recorded.

---

### Step 03.4 - Add support-routing tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactoryTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/input/InputHelpRegistryTest.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add tests that cover the localized help URL path, the bug-report email path, and the review destination path. Extend the existing help-registry coverage if needed, but keep the new tests focused on URI generation.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactoryTest.kt` exists.
- `Grep` - `class SupportIntentFactoryTest` matches exactly once.
- `Grep` - `link resolver produces a valid URL for every surface` still present in `InputHelpRegistryTest.kt` or equivalent coverage remains.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: SupportIntentFactoryTest.kt (+114 LOC). Existing InputHelpRegistryTest coverage retained. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL in 37s.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` regenerated after the touched `.kt` files change.

---

## Handoff Notes to Next Phase

Help, bug-report, and review destinations now come from one place, so later phases can attach one contextual next step without duplicating URIs or locale branching.

---

## Rollback Plan

Revert the Phase 03 commit(s) and restore direct URI wiring. No persistent state change is introduced.