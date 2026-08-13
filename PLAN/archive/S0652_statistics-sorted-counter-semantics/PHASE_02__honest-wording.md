# Phase 02 - Honest wording for the "Sorted" card

**Strategic spec:** [`../S0652_statistics-sorted-counter-semantics.md`](../S0652_statistics-sorted-counter-semantics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-23
**Completed:** 2026-06-24

---

## Objective

Make the headline metric label honest about its formula (copied + moved) without changing the formula, so the user understands at a glance what "Отсортировано" counts. The same label string is reused by the TXT report, so the clarification propagates there for free.

---

## Prerequisites

- [ ] `statistics_card_sorted` key exists in all three locales (verified: `values/strings.xml:2626`, `values-ru:2579`, `values-uk:2542`).
- [ ] Card label `tvCardLabel` supports `maxLines="2"` (verified: `res/layout/item_stats_card.xml:48`) - a short parenthetical fits without any layout change.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> No layout edit: the existing 2-line label holds the parenthetical. No `SummaryCard` model / adapter change. `item_stats_card.xml` has no landscape variant (item layouts are orientation-agnostic) - parity note satisfied.

---

## Steps

### Step 02.1 - Rewrite `statistics_card_sorted` to name copy + move

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the existing key `statistics_card_sorted` in all three locales so the label honestly states it is the sum of copied and moved files. Use `scripts/utils/set-android-string.ps1 -Action set` once per locale (byte-preserving, `-ExpectedOldValue` guard). Target values (keep them short - they must wrap within the card's 2-line label and read cleanly as a TXT-report line label):
>
> - EN (`values`): `Sorted (copied + moved)`
> - RU (`values-ru`): `Отсортировано (копир. + перемещ.)`
> - UK (`values-uk`): `Відсортовано (копіюв. + перемещ.)`
>
> Do not change the formula, the key name, the icon, or any other card. Check the new strings against `docs/COMMUNICATION_POLICY.md` §2 (label is a neutral noun phrase, no instruction/emotion) and §6 tone checklist (concise, plain, consistent terminology with the existing "Скопировано"/"Перемещено" operation rows).

**Verification:**

- `Grep` - `Sorted (copied + moved)` matches once in `values/strings.xml`.
- `Grep` - `Отсортировано (копир` matches once in `values-ru/strings.xml`.
- `Grep` - `Відсортовано (копіюв` matches once in `values-uk/strings.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 4/4 PASS (EN/RU/UK grep ×1 each; §6 neutral noun-phrase, consistent terminology). Files: values/values-ru/values-uk strings.xml via set-android-string.ps1.

---

### Step 02.2 - Audit locale parity

**Files:** (validation only - no edit)
**Depends on:** Step 02.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "statistics_card_sorted"`. Exit 0 required - the key must be present and non-empty in EN/RU/UK with no missing-translation finding.

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix "statistics_card_sorted"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 1/1 PASS (locale parity EN/RU/UK OK, exit 0).

---

## Phase Done Criteria

- [x] Steps 02.1-02.2 are `[x] done`.
- [x] `check_strings_localized.ps1` exits 0 for the key.
- [x] Project compiles - `pwsh -NoProfile -File .\a.ps1 fc` PASS on 2026-06-24.
- [x] Dev log entry added for the strings change.

---

## Handoff Notes to Next Phase

Label now reads `Sorted (copied + moved)` (and locale equivalents) in both the dashboard card and the TXT report. Device test must confirm the 2-line label renders without clipping in the card on a phone-width grid cell (portrait and landscape).

---

## Rollback Plan

Revert the three string edits via `set-android-string.ps1 -Action set` back to the prior single-word values - no code or schema impact.
