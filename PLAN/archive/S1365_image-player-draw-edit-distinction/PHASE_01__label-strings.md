# Phase 01 - Label strings

**Strategic spec:** [`../S1365_image-player-draw-edit-distinction.md`](../S1365_image-player-draw-edit-distinction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Land the three menu labels in EN, RU and UK. No consumer is repointed here, so the tree stays green and every later phase can reference a key that already exists.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 6 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 6 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 6 |

> Every edit in this phase goes through `scripts/utils/set-android-string.ps1` (byte-preserving), never a hand edit - per CLAUDE.md "Post-Change" step 3.

---

## Steps

### Step 01.1 - Add the image-correction label key

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add key `menu_edit_adjust` across EN, RU and UK in one call:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key menu_edit_adjust -En "Adjust" -Ru "Коррекция" -Uk "Корекція"
> ```
>
> Do not touch `edit_image` yet - its single consumer still points at it and Phase 02 removes it once that consumer has moved. Check the three values against `docs/COMMUNICATION_POLICY.md` §2 (message formula for a command label) and §6 (tone checklist).

**Why:**

Strategic §5 fixes the owner's wording for this command as «Коррекция», and §5.1 states the correction label must stop sharing `@string/edit` with the unrelated Browse and scheduled-operation screens.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key menu_edit_adjust` - prints a value for EN, RU and UK, none of them `not translated`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_edit_adjust"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.2 - Add the text-file label key

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add key `menu_edit_file_text` across EN, RU and UK in one call:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key menu_edit_file_text -En "File text" -Ru "Текст файла" -Uk "Текст файлу"
> ```
>
> Check the three values against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §11 criterion 1 requires that no two viewer menu items carry the same label, and §4 records that `menu_edit_text` currently shares `@string/edit` with `menu_edit`.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key menu_edit_file_text` - prints a value for EN, RU and UK, none of them `not translated`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_edit_file_text"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.3 - Restate the draw label as a noun

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Change the value of the existing key `menu_draw_overlay` in all three locales, keeping the key name. One call per locale, each guarded by the current value:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action set -Key menu_draw_overlay -Locale en -Value "Drawing" -ExpectedOldValue "Draw"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action set -Key menu_draw_overlay -Locale ru -Value "Рисование" -ExpectedOldValue "Рисовать"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action set -Key menu_draw_overlay -Locale uk -Value "Малювання" -ExpectedOldValue "Малювати"
> ```
>
> The key keeps its name because its only consumers are this one command's menu items and button content descriptions; no code or layout edit follows from this step.

**Why:**

Strategic §5 sets the draw command's label to «Рисование», and §5.1 rules out minting a second key beside `menu_draw_overlay` because a duplicate with the same meaning would be dead weight under CLAUDE.md Rule 20.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key menu_draw_overlay` - EN reads `Drawing`, RU reads `Рисование`, UK reads `Малювання`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_draw_overlay"` - exit 0.
- `Grep` - `name="menu_draw_overlay"` still matches in all three `strings.xml` files.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_"` - exit 0.
- [x] Project compiles - `.\a.ps1 fr` exit 0 (resources-only phase; the Kotlin compile gate belongs to Phase 02).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the string change via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Step Log

- 2026-08-07 - Step 01.1 done. `set-android-string.ps1 -Action add -Key menu_edit_adjust` exit 0; `check_strings_localized.ps1 -KeyPrefix menu_edit_adjust` exit 0. EN `Adjust` / RU `Коррекция` / UK `Корекція`.
- 2026-08-07 - Step 01.2 done. `set-android-string.ps1 -Action add -Key menu_edit_file_text` exit 0; parity check exit 0. EN `File text` / RU `Текст файла` / UK `Текст файлу`.
- 2026-08-07 - Step 01.3 done. Three guarded `-Action set` calls on `menu_draw_overlay`, each reporting its old value: `Draw`→`Drawing`, `Рисовать`→`Рисование`, `Малювати`→`Малювання`. Key name unchanged; parity check exit 0.
- 2026-08-07 - Phase Done Criteria: `check_strings_localized.ps1 -KeyPrefix menu_` exit 0 (11 keys, all present in en/ru/uk); `.\a.ps1 fr` exit 0 in 10s; `TODO(phase-01)` zero hits.
- 2026-08-07 - Phase-boundary audit: resources-only phase, Layer 1 only. Keys follow the sibling `menu_*` convention, values are short noun labels, no string literal moved into code. No findings.

---

## Handoff Notes to Next Phase

`menu_edit_adjust`, `menu_edit_file_text` and the revalued `menu_draw_overlay` all exist in EN, RU and UK. `edit_image` is still present and still referenced - Phase 02 owns its removal.

---

## Rollback Plan

Revert the phase commit. No data migration and no runtime surface changed - the two new keys are not referenced by any consumer yet.
