# Phase 01 - Skip-message strings

**Strategic spec:** [`../S0413_bugfix-audio-graceful-skip.md`](../S0413_bugfix-audio-graceful-skip.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Completed:** 2026-06-13
**Started:** -
**Completed:** -

---

## Objective

Add the trilingual user-facing strings the skip flow will reference: one for a skipped track, one for a fully unplayable queue. No code change yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +2 keys |

---

## Steps

### Step 01.1 - Add skip + unplayable-queue strings (EN/RU/UK lockstep)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two string keys across all three locales in one lockstep call using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (parity-enforced; do not hand-edit the three files separately).
>
> - `s0413_audio_track_skipped` - shown when one track is skipped because it cannot be decoded. Takes the file name as `%1$s`. Proposed copy:
>   - EN: `Skipped "%1$s" — can't play this file.`
>   - RU: `Пропущен файл «%1$s» — его не удалось воспроизвести.`
>   - UK: `Пропущено файл «%1$s» — не вдалося відтворити.`
> - `s0413_audio_queue_unplayable` - shown once when every track in the queue failed and playback stops. No args. Proposed copy:
>   - EN: `Nothing here could be played.`
>   - RU: `Не удалось воспроизвести ни один файл.`
>   - UK: `Не вдалося відтворити жоден файл.`
>
> These are Toast messages: follow `docs/COMMUNICATION_POLICY.md` §2.1 (Toast formula) and §6 tone checklist - no error codes, no extractor/technical jargon, plain human wording. Keep `«»` quotes in RU/UK, straight quotes in EN. Do not reuse `error_invalid_format` / `s0213_*` (different context).

**Verification:**

- `Grep` - `s0413_audio_track_skipped` matches once in each of the three `strings.xml` files.
- `Grep` - `s0413_audio_queue_unplayable` matches once in each of the three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "s0413_audio"` → exit 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 4/4 PASS. Added `s0413_audio_track_skipped` + `s0413_audio_queue_unplayable` to EN/RU/UK via set-android-string.ps1 -Action add. Parity audit exit 0. Cyrillic verified clean (no mojibake), EN quotes escaped to `&quot;`. Comm policy §6 OK.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `check_strings_localized.ps1 -KeyPrefix "s0413_audio"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Both string keys exist in all three locales; Phase 02 references `R.string.s0413_audio_track_skipped` and `R.string.s0413_audio_queue_unplayable`.

---

## Rollback Plan

Remove the two keys from all three `strings.xml` files - no code references them yet.
