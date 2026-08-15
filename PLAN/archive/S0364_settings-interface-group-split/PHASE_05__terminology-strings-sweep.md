# Phase 05 - Terminology strings sweep

**Strategic spec:** [`../S0364_settings-interface-group-split.md`](../S0364_settings-interface-group-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Apply the "браузер файлов" term to every `CHANGE` string in the inventory across EN/RU/UK, leaving web-browser strings untouched.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done; `temp/S0364_terminology_inventory.md` exists with `CHANGE` rows.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings*.xml` (per inventory) | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings*.xml` (per inventory) | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings*.xml` (per inventory) | Modified | n/a |

> Edit only keys listed `CHANGE` in the inventory. Each key changed in all three locales in lockstep; preserve grammatical case (RU/UK declensions) - this is hand wording, not blind find-replace.

---

## Steps

### Step 05.1 - Rewrite the CHANGE strings in all three locales

**Files:** `app_v2/src/main/res/values*/strings*.xml` (keys from `temp/S0364_terminology_inventory.md`)
**Depends on:** - start of phase

**Prompt for developer:**

> For each `CHANGE` row in `temp/S0364_terminology_inventory.md`, update the string value in EN/RU/UK to use "браузер файлов" / "file browser" / "браузер файлів" (or the qualified media variant) using `scripts/utils/set-android-string.ps1 -Action set` per locale with `-ExpectedOldValue` guards. Keep correct grammatical case in RU/UK (e.g. «в браузере файлов», «браузера файлов»). Do NOT touch any `EXCLUDE` (web-browser) string. Apply COMMUNICATION_POLICY §2 message formulas and §6 tone checklist.

**Verification:**

- `Grep` - `обозреватель` returns zero hits in `app_v2/src/main/res/values-ru/strings*.xml` (or only inside inventory-`EXCLUDE` keys, documented).
- `Grep` - `браузер файлов` present in at least one RU strings file beyond the category key.
- `Grep` - in `values-ru/strings_google_account.xml`, web-browser strings still contain `браузер` (EXCLUDE preserved).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. Applied 8 CHANGE keys (A-H) × EN/RU/UK = 24 string edits via byte-safe Edit (bulk-rewrite exception; declensions hand-tuned). RU `обозреватель`/`обзоре` ×0 (expected 0 | actual 0). Canonical «браузер файлов» in 8 RU strings beyond the category key. Web-browser EXCLUDE preserved: values-ru/strings_google_account.xml still has `браузер` ×6 (untouched). tooltip_allow_separate_window_message + default_grid_mode title left per inventory EXCLUDE rationale (RU/UK already consistent / no window-name). COMMUNICATION_POLICY §6 OK.

---

### Step 05.2 - Localization parity audit

**Files:** `app_v2/src/main/res/values*/strings*.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the localization audit over the changed keys to confirm EN/RU/UK parity and that no key was changed in one locale only.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1` exits 0 for the changed keys.
- `/build` standardDebug compiles (all referenced keys resolve).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS. check_strings_localized.ps1 for changed keys exit 0 (EN/RU/UK parity OK). `.\a.ps1 dq` standardDebug BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build`. (standardDebug BUILD SUCCESSFUL)
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every modified strings file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All in-app strings naming the Browse window use the canonical term. Phase 06 aligns the documentation wording.

---

## Rollback Plan

Revert the strings edits via `set-android-string.ps1 -Action set` restoring the inventory's recorded old values. No data migration.
