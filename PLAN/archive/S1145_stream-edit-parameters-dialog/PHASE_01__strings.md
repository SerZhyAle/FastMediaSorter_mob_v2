# Phase 01 - Strings

**Strategic spec:** [`../S1145_stream-edit-parameters-dialog.md`](../S1145_stream-edit-parameters-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 1 / 1
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

Add the trilingual strings for the media-kind picker (label + Auto/Audio/Video) and the duplicate-URL error, consumed by the ViewModel message (Phase 03) and the dialog UI (Phase 04).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 01.1 - Add media-kind and duplicate-URL strings (EN/RU/UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add these five keys across EN/RU/UK with one `set-android-string.ps1 -Action add` call per key (parity-enforced). Copy is short UI text: apply `docs/COMMUNICATION_POLICY.md` §2 (label/error formulas) and §6 (tone checklist) - the duplicate error states the fact plainly, no blame, no "Are you sure".
> - `streams_edit_type_label` = `Type` / `Тип` / `Тип`
> - `streams_edit_type_auto` = `Auto` / `Авто` / `Авто`
> - `streams_edit_type_audio` = `Audio` / `Аудио` / `Аудіо`
> - `streams_edit_type_video` = `Video` / `Видео` / `Відео`
> - `streams_error_duplicate_url` = `A stream with this address already exists` / `Трансляция с таким адресом уже есть` / `Трансляція з такою адресою вже є`

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_edit_type"` - exit 0.
- `Grep` - `streams_error_duplicate_url` present in all three `values*/strings.xml`.
- `Grep` - `streams_edit_type_auto` present in all three `values*/strings.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-07-22 - Added 5 keys via set-android-string.ps1 (EN/RU/UK). Verification 4/4 PASS: type-audit exit 0, dup-audit exit 0, both keys present x3 locales, §6 tone clean (duplicate error states the fact, no blame). Dev log recorded.

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_edit_type"` exit 0 and `-KeyPrefix "streams_error_duplicate_url"` exit 0.
- [x] Dev log entry added for the string files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Keys `streams_edit_type_label|auto|audio|video` and `streams_error_duplicate_url` exist; Phase 03 uses the error, Phase 04 uses the label/options.

---

## Rollback Plan

Remove the five keys from all three locales via `set-android-string.ps1 -Action remove` - no code depends on them until Phase 03/04.
