# Phase 01 - Add group header string

**Strategic spec:** [`../S0649_settings-operations-additional-programs-group.md`](../S0649_settings-operations-additional-programs-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Add the trilingual `settings_category_additional_programs` string used as the title of the new collapsible group. No layout or code wiring yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | n/a |

> The sibling group title `settings_category_other_features` lives in `strings_settings.xml`; place the new key there for locality. The `set-android-string.ps1 -Action add` call edits all three locales in lockstep.

---

## Steps

### Step 01.1 - Add trilingual group title

**Files:** `app_v2/src/main/res/values{,-ru,-uk}/strings_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new string key `settings_category_additional_programs` across EN/RU/UK in one lockstep call. Values: EN `Additional programs and scenarios`, RU `Дополнительные программы и сценарии`, UK `Додаткові програми та сценарії`. Use:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key settings_category_additional_programs -En "Additional programs and scenarios" -Ru "Дополнительные программы и сценарии" -Uk "Додаткові програми та сценарії"`
> If the tool writes the key to the base `strings.xml` rather than `strings_settings.xml`, relocate it next to `settings_category_other_features` in each `strings_settings.xml` so grouping stays consistent (use Read/Edit, byte-preserving).
> Communication policy: this is a category label (noun phrase, sentence case, no trailing punctuation). Verify it satisfies `docs/COMMUNICATION_POLICY.md` §2 (label formula) and the §6 tone checklist (concise, parallel with sibling category titles, no jargon).

**Verification:**

- `Grep` - `name="settings_category_additional_programs"` matches exactly once in each of `values/`, `values-ru/`, `values-uk/` `strings_settings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_additional_programs"` exits 0 (EN/RU/UK parity).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 3/3 PASS. Added `settings_category_additional_programs` to `strings_settings.xml` EN/RU/UK via `set-android-string.ps1 -Action add`. Parity OK. post-change (Xml) PASS, dev log recorded.

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] `check_strings_localized.ps1 -KeyPrefix "settings_category_additional_programs"` exits 0.
- [x] Dev log entry added for the string change via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`@string/settings_category_additional_programs` exists in all three locales and is ready to be referenced by `csh_title` on the new header in Phase 02.

---

## Rollback Plan

Remove the three string entries via `set-android-string.ps1 -Action remove -Key settings_category_additional_programs` - no data migration or user-facing surface changed.
