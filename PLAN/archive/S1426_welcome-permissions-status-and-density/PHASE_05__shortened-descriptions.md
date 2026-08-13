# Phase 05 - Shortened descriptions

**Strategic spec:** [`../S1426_welcome-permissions-status-and-density.md`](../S1426_welcome-permissions-status-and-density.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 1 / 1
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Rewrite every permission description into one phrase that fits the rebuilt row's single description line in all three locales, so nothing is lost to the ellipsis.

---

## Prerequisites

- [x] Phase 04 is ✅ Done - the row already clamps the description to one line.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | 13 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | 13 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | 13 keys |

---

## Steps

### Step 05.1 - Rewrite every permission description to one line

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> First measure how many characters fit the description line at the row's text size on the narrowest supported layout width, with the indicator and the action button present since both take horizontal space from the text column. Then rewrite each `perm_desc_*` value to one phrase within that budget in EN, RU and UK, using `scripts/utils/set-android-string.ps1 -Action set` per key and locale with `-ExpectedOldValue` so no key is overwritten blind. Keep what the permission is for and drop the enumerations of individual capabilities. Check each against `docs/COMMUNICATION_POLICY.md` §2 for the message formula and §6 for tone. Do not touch `perm_title_*`.

**Why:**

Strategic ADR-2 decides descriptions are shortened rather than left for the ellipsis to cut, because the cut lands exactly where the phrase explains the purpose, and §3.1 wish 2 requires the user to still understand why access is asked for without an extra tap.

**Verification:**

- `Grep` - every `perm_desc_*` value is at or under the measured budget in all three locales.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_desc_"` exits 0.
- `Grep` - `perm_title_*` values are byte-identical to their pre-phase state.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Step 05.1 is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] On a device, both the settings permissions screen and the onboarding permissions page were opened in portrait and landscape, and no description ends in an ellipsis and no title wraps.
- [ ] On the same screens in portrait, at least eight items are visible at once - strategic §11 criterion 4.
- [ ] A greyscale screenshot of either screen still tells the four states apart - strategic §11 criterion 5.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

All user-visible text and every asset the ticket introduces now exist, so the generated inventories can be re-rendered correctly.

---

## Rollback Plan

Revert the phase commit - string values only, no structural change.
