# Phase 01 - Strings

**Strategic spec:** [`../S0318_playback-other-functionality-group.md`](../S0318_playback-other-functionality-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Add the new settings-category title string `settings_category_other_features` in EN/RU/UK; no layout or code wiring yet.

---

## Prerequisites

- [ ] Strategic §6 research items Resolved (owner-confirmed title wording).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 2 |

---

## Steps

### Step 01.1 - Add `settings_category_other_features` in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new string resource `settings_category_other_features` next to the existing `settings_category_*` entries. Values: EN `Other features`, RU `Прочий функционал`, UK `Інші функції`. Do NOT reuse `settings_category_other` (already taken by "Translation, OCR and Google Lens"). Match the surrounding entry style; use `ё` where applicable (none here). Title-only category string - no message-formula or tone-checklist rewrite needed (not a toast/dialog/error).

**Verification:**

- `Grep` - `name="settings_category_other_features">Other features<` matches once in `values/strings.xml`.
- `Grep` - `name="settings_category_other_features">Прочий функционал<` matches once in `values-ru/strings.xml`.
- `Grep` - `name="settings_category_other_features">Інші функції<` matches once in `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_other_features"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 4/4 PASS. EN "Other features" / RU "Прочий функционал" / UK "Інші функції" added via set-android-string.ps1 -CreateIfMissing. check_strings_localized: all 3 OK. expected: key present 1× each locale | actual: 1× each.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `check_strings_localized.ps1 -KeyPrefix "settings_category_other_features"` exits 0.
- [ ] Dev log entry added for each of the three strings.xml files.

---

## Handoff Notes to Next Phase

`@string/settings_category_other_features` exists in all three locales and is available for the new `CollapsibleSectionHeader` title in Phase 02.

---

## Rollback Plan

Revert phase commit - removing the three string lines. No data or behavior impact.
