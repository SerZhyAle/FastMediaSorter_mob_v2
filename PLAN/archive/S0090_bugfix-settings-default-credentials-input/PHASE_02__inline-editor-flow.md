# Phase 02 - Inline Editor Flow

**Strategic spec:** [`../S0090_bugfix-settings-default-credentials-input.md`](../S0090_bugfix-settings-default-credentials-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Replace the passive focus-only credential editing path with an explicit activation flow and a deterministic commit path for `Default User` and `Default Password`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | <= 460 |

> File is currently 387 lines - no backup required.

---

## Steps

### Step 02.1 - Add explicit activation helper for credential editors

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `GeneralSettingsViewSetupHelper.kt`, introduce a private helper named `activateCredentialEditor(target: TextInputEditText)` that requests focus, moves the cursor to the end, and posts `showSoftInput(target, SHOW_IMPLICIT)` through `InputMethodManager`. Wire this helper into the credentials row so direct taps on `tilDefaultUser`, `etDefaultUser`, `tilDefaultPassword`, and `etDefaultPassword` all enter editing explicitly instead of depending only on `OnFocusChangeListener` side effects.

**Verification:**

- `Grep` - `private fun activateCredentialEditor\(` returns exactly **one** hit in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `binding.tilDefaultUser.setOnClickListener` returns exactly **one** hit in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `binding.tilDefaultPassword.setOnClickListener` returns exactly **one** hit in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `showSoftInput\(target, InputMethodManager.SHOW_IMPLICIT\)` returns exactly **one** hit in `GeneralSettingsViewSetupHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` (+15 LOC). Dev log recorded.

---

### Step 02.2 - Add IME action commit path and preserve single user-commit side effect

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Still in `GeneralSettingsViewSetupHelper.kt`, set `IME_ACTION_NEXT` on `etDefaultUser` and `IME_ACTION_DONE` on `etDefaultPassword`, then add `setOnEditorActionListener` handlers so the user field commits once and advances to the password field, while the password field commits once, hides IME, and clears focus. Keep the existing focus-loss save as a fallback, but route the `OWNER_TRIGGER` import dialog through a single shared default-user commit path so it cannot fire twice for one edit session.

**Verification:**

- `Grep` - `IME_ACTION_NEXT` returns exactly **one** hit in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `IME_ACTION_DONE` returns exactly **one** hit in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `setOnEditorActionListener` returns exactly **two** hits in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `OWNER_TRIGGER` returns exactly **one** hit in `GeneralSettingsViewSetupHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 - Verification 4/4 PASS after one local repair. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` (+33 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build standard-debug`.
- [x] `Grep` for `TODO\(phase-02\)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Credentials editing no longer relies on incidental focus-change behavior to open IME.
- `Default User` and `Default Password` both have an explicit commit path in addition to blur fallback.
- Phase 03 may guard the settings shortcut layer under the assumption that the editor activation path is deterministic.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persistent schema change involved.
