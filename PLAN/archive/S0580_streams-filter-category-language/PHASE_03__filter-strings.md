# Phase 03 - Filter strings

**Strategic spec:** [`../S0580_streams-filter-category-language.md`](../S0580_streams-filter-category-language.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Add the trilingual (EN/RU/UK) user-visible strings the new filter UI and picker need, in lockstep parity.

---

## Prerequisites

- [ ] Read `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist).
- [ ] Confirm none of the intended keys already exist: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key streams_filter_language` (and the others).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

---

## Steps

### Step 03.1 - Add filter and picker strings in EN/RU/UK

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the following keys via one lockstep call each: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"`. Keys:
> - `streams_filter_category` - field label "Category".
> - `streams_filter_language` - field label "Language".
> - `streams_filter_match_mode` - toggle caption, e.g. "Match".
> - `streams_filter_match_all` - AND option, e.g. "All selected".
> - `streams_filter_match_any` - OR option, e.g. "Any selected".
> - `streams_filter_clear` - reset action "Clear filters".
> - `streams_picker_search_hint` - search field hint "Type to search".
> - `streams_picker_empty` - empty result "Nothing found".
>
> Reuse the existing `streams_filter_all` for the in-picker "All / reset" row (do not duplicate). Choose final wording to satisfy COMMUNICATION_POLICY §2/§6 (concise, sentence case, no trailing period on labels). RU must use ё where applicable.

**Verification:**

- `Grep` - each new key present in all three of `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- Locale audit: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_filter_"` exits 0; repeat with `-KeyPrefix "streams_picker_"`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Added 8 keys (streams_filter_category/language/match_mode/match_all/match_any/clear, streams_picker_search_hint/empty) in EN/RU/UK via set-android-string add. Locale audit exit 0 for both prefixes; Cyrillic verified via Read. Phase 03 run before Phase 02 so the new strings exist when the picker layout/dialog reference them. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml.

---

## Phase Done Criteria

- [x] Step 03.1 is `[x] done`.
- [x] `check_strings_localized.ps1` exits 0 for both key prefixes.
- [x] Dev log entry added (post-change.ps1, ChangeType Xml).

---

## Handoff Notes to Next Phase

- Phase 04 references these keys for the filter dialog labels, the AND/OR toggle, the clear action, and the picker search hint / empty state.

---

## Rollback Plan

Remove the added keys via `set-android-string.ps1 -Action remove` across all three locales - no code references them until Phase 04.
