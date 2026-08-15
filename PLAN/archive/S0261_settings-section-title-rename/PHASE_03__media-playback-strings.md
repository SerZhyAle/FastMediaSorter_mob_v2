# Phase 03 - Media Playback Strings

**Strategic spec:** [`../S0261_settings-section-title-rename.md`](../S0261_settings-section-title-rename.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Rename the Media and Playback collapsible section headers in EN, RU, and UK while preserving search navigation and the current section structure.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Existing string keys already exist in `values`, `values-ru`, and `values-uk`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 60 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 60 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 60 |

---

## Steps

### Step 03.1 - Rename Media section titles

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Phase 02

**Prompt for developer:**

> Update the existing string values for `settings_category_images`, `settings_category_video`, `vr_settings_block_title`, `settings_category_audio`, `settings_category_documents`, and `settings_category_other` so the Media section headers describe the real content of each subsection. Preserve the existing keys because layouts and search formatting already depend on them.

**Verification:**

- `Grep` - all three locale files contain `vr_settings_block_title`.
- `Grep` - all three locale files contain `settings_category_other`.
- `Grep -n "Google Lens|Google Lens|Google Lens"` - matches at least once across the touched locale files.

**Status:** `[x] done`

---

### Step 03.2 - Rename Playback section titles

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Update the existing string values for `settings_category_sorting_slideshow`, `settings_category_file_operations`, `settings_category_player_ui`, `settings_category_touch_zones`, and `settings_category_behaviour` so the Playback section headers explain the contained settings more clearly. Keep the current keys and avoid renaming any search section ids or Kotlin constants.

**Verification:**

- `Grep` - all three locale files contain `settings_category_sorting_slideshow`.
- `Grep` - all three locale files contain `settings_category_behaviour`.
- `Grep -n "touch|касани|дотик"` - matches at least once across the touched locale files.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Trilingual Media and Playback titles are updated in place.
- [x] Project compiles - run `.\scripts\builders\build-debug.PS1`.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: app_v2/src/main/res/values/strings.xml, app_v2/src/main/res/values-ru/strings.xml, app_v2/src/main/res/values-uk/strings.xml. Build: `.\scripts\builders\build-debug.PS1` -> PASS. Dev log recorded.

---

## Handoff Notes to Next Phase

All section title strings are renamed. Final phase must sync spec metadata, run validation, and prepare the ticket for audit.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
