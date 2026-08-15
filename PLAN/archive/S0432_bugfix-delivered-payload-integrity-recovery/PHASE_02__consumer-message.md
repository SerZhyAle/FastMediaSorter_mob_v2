# Phase 02 - Consumer Message

**Strategic spec:** [`../S0432_bugfix-delivered-payload-integrity-recovery.md`](../S0432_bugfix-delivered-payload-integrity-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

At the OCR consumer, distinguish a payload-corruption failure from a generic OCR failure and show an actionable, localized message that points the user to reinstall in Extensions (one-tap self-recovery), instead of the generic `ocr_error` toast.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`DeliveredPayloadCorruptException` exists and is thrown by the loader).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 key |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/RecognitionBackend.kt` | Modified | ≤ 200 |

> No `res/layout` edits - no landscape parity concern. String is in `src/main` (compiled for all flavors; harmless where OCR is absent).

---

## Steps

### Step 02.1 - Add the trilingual "OCR data damaged" string

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one user-facing string key `ocr_engines_damaged` across EN/RU/UK in lockstep with a single call:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key ocr_engines_damaged -En "<EN>" -Ru "<RU>" -Uk "<UK>"`.
> Suggested copy (align the screen-name reference with the existing Extensions-manager title string before finalizing):
> - EN: `OCR data is damaged. Reinstall it in Extensions.`
> - RU: `Данные OCR повреждены. Переустановите их в разделе «Расширения».`
> - UK: `Дані OCR пошкоджені. Перевстановіть їх у розділі «Розширення».`
> Message must follow `docs/COMMUNICATION_POLICY.md` §2 (state what happened + the recovery action) and pass the §6 tone checklist (no blame, concrete next step, no jargon). Use `..` not `...`; use `ё` where grammatically correct in RU.

**Verification:**

- `Grep` - `name="ocr_engines_damaged"` matches once in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `name="ocr_engines_damaged"` matches once in `values-ru/strings.xml`.
- `Grep` - `name="ocr_engines_damaged"` matches once in `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ocr_engines_damaged"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 02.2 - Map the corruption exception to the actionable message

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/RecognitionBackend.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `RecognitionBackend`, in the two `catch` blocks that currently call `callback.showError(context.getString(R.string.ocr_error))` after `libraryLoader.load(DeliverableSet.OCR_ENGINES)` (in `recognizeText` and `recognizeAndTranslateBlocks`), branch on the exception type: if it is a `DeliveredPayloadCorruptException`, call `callback.showError(context.getString(R.string.ocr_engines_damaged))`; otherwise keep the existing `R.string.ocr_error`. Keep the existing `Timber.e(e, "Failed to load OCR engines native libraries")` log (it is a real error, no `Sxxxx:` tag). Leave the third `catch` (in `recognizeTextBlocksForSelection`, which shows no toast) unchanged. Do not broaden any catch or swallow the exception silently.

**Verification:**

- `Grep` - `DeliveredPayloadCorruptException` present in `RecognitionBackend.kt`.
- `Grep` - `R.string.ocr_engines_damaged` matches at least twice in `RecognitionBackend.kt`.
- `Grep` - `R.string.ocr_error` still present (generic-failure fallback retained).
- `Grep -n "Log\.d\("` - zero hits in `RecognitionBackend.kt`.
- Compiles - phase build (Phase Done Criteria).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ocr_engines_damaged"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- New user-facing string `ocr_engines_damaged` shipped in EN/RU/UK. Functionality-log FIX entry covers the behavior change. No `docs/FEATURES` change (strategic §8).

---

## Rollback Plan

Revert phase commit(s) and remove the `ocr_engines_damaged` key from all three locale files - no data migration; the consumer falls back to the prior generic `ocr_error` message.
