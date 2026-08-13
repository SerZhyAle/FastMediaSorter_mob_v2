# Phase 08 - Migration for Existing Installs

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 03, 04, 07
**Blocks:** Phase 09
**Steps done:** 2 / 2
**Started:** 2026-06-02
**Completed:** 2026-06-02

---

## Objective

Handle existing install migration: set profile to `OTHER / Другой` with source `MIGRATION_EXISTING` and confidence `NONE`, without applying preset. Explain in Settings why profile is "Other" (priors settings preserved). Avoid any automatic preset application on upgrade.

---

## Prerequisites

- [ ] Phase 01, 03, 04, 07 are ✅ Done.
- [ ] Room migration in Phase 01 is deployed.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/RealDeviceProfileRepository.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +5 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +5 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +5 keys |

---

## Steps

### Step 08.1 - Enhance repository migration logic to handle existing installs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/RealDeviceProfileRepository.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Update RealDeviceProfileRepository (from Phase 03):
> (1) In repository init or lazy initialization:
>     - Check if device_profile table is empty (fresh insert after migration).
>     - Check SharedPreferences `shared_prefs_has_run_before` or similar flag indicating existing install.
>     - If flag is true and profile table is empty:
>         * Write profile with type=OTHER, source=MIGRATION_EXISTING, confidence=NONE
>         * Log Timber.i("S0327: existing install migrated to OTHER profile")
>     - If flag is false (first run fresh):
>         * Leave profile empty; it will be populated by welcome detector
> (2) Add method `fun isMigrationExisting(): Boolean` for Settings to show explanatory text.

**Verification:**

- `Grep` - migration check logic in repository.
- `Grep` - SharedPreferences flag check.
- `Grep` - Profile written with type=OTHER, source=MIGRATION_EXISTING.
- `Grep` - Timber.i logging (not Timber.d, since this is not a BlockNeedUserTest probe).

**Status:** `[x]` done

---

### Step 08.2 - Add localized migration explanation strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 08.1

**Prompt for developer:**

> Add keys for migration explanation in Settings profile display:
> - `settings_profile_source_migration_existing` - e.g., "Updated from previous version"
> - `settings_profile_migration_hint` - e.g., "Your previous settings were preserved. Choose a profile to apply new defaults."
> Localize to EN/RU/UK.

**Verification:**

- `Grep` - all migration-related keys present in all 3 strings.xml files.
- Keys are used in Settings UI to show explanation when profile source is MIGRATION_EXISTING.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 08.*` above is `[x] done`.
- [ ] Project compiles.
- [ ] Manual test on real device with existing install:
>   - Install old version (before S0327).
>   - Upgrade to new version.
>   - Verify Settings shows profile as "Other" with migration explanation.
>   - Verify no preset was applied (settings look same as before).
> - `Grep` for `TODO(phase-08)` returns zero hits.
> - Dev log entries.

---

## Handoff Notes to Next Phase

Existing install migration complete. New installs follow Welcome flow (Phase 05); upgrades get "Other" profile with no preset. Phase 09 final docs and catalog cleanup.

---

## Rollback Plan

Revert phase commits - no data loss; migration flag check is simply removed, leaving profile table empty (will be re-populated on next Settings open by Phase 03 fresh-install logic).
