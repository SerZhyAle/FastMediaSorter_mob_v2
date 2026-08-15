# Phase 02 - General Operations Strings

**Strategic spec:** [`../S0261_settings-section-title-rename.md`](../S0261_settings-section-title-rename.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Rename the General and Operations collapsible section headers in EN, RU, and UK without changing any section ids or behavior.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Existing string keys already exist in `values`, `values-ru`, and `values-uk`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 40 |

---

## Steps

### Step 02.1 - Rename General section titles

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Phase 01

**Prompt for developer:**

> Update the existing string values for `settings_category_interface`, `settings_category_authorization`, `settings_category_app_data`, `settings_category_system`, and `debug_settings_title` so each title describes the actual contents of its General settings section more precisely. Keep the existing keys, preserve EN/RU/UK parity, and verify the wording against `docs/COMMUNICATION_POLICY.md` §6 for clarity and screen fit.

**Verification:**

- `Grep` - all three locale files contain `settings_category_interface`.
- `Grep` - all three locale files contain `debug_settings_title`.
- `Grep -n "operation completed successfully|Are you sure\?"` - returns zero hits across the three touched locale files.

**Status:** `[x] done`

---

### Step 02.2 - Rename Operations section titles

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update the existing string values for `settings_category_safety`, `settings_category_copy_move`, `destinations_list_header`, and `scheduled_ops_section_title` so each Operations section title reflects the settings inside the group, not only the broad category label. Keep the existing keys and keep the strings readable in portrait and landscape.

**Verification:**

- `Grep` - all three locale files contain `destinations_list_header`.
- `Grep` - all three locale files contain `scheduled_ops_section_title`.
- `Grep -n "Quick Sort|быстрой сортировки|швидкого сортування"` - matches at least once across the touched locale files.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Trilingual General and Operations titles are updated in place.
- [x] Project compiles - run `.\scripts\builders\build-debug.PS1`.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: app_v2/src/main/res/values/strings.xml, app_v2/src/main/res/values-ru/strings.xml, app_v2/src/main/res/values-uk/strings.xml. Build: `.\scripts\builders\build-debug.PS1` -> PASS. Dev log recorded.

---

## Handoff Notes to Next Phase

General and Operations titles are renamed. Phase 03 must continue with Media and Playback only.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
