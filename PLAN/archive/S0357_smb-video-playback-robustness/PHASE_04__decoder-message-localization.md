# Phase 04 - Decoder Message Localization

**Strategic spec:** [`../S0357_smb-video-playback-robustness.md`](../S0357_smb-video-playback-robustness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** none
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Create the user-facing unsupported-decoder refusal string `video_decoder_unsupported_hardware` in EN/RU/UK in lockstep, conforming to the communication policy.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (or in the same working session) - the error branch references `R.string.video_decoder_unsupported_hardware`.
- [ ] `docs/COMMUNICATION_POLICY.md` §2 (error message formula) and §6 (tone checklist) read before authoring the text.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a (one key) |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a (one key) |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a (one key) |

> No layout file is touched - no `res/layout-land` counterpart applies. The three locale files are edited in one lockstep tool call (parity-enforced), not three manual edits.

---

## Steps

### Step 04.1 - Add the trilingual decoder-refusal string in lockstep

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add key `video_decoder_unsupported_hardware` across EN/RU/UK in one lockstep call:
>
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key video_decoder_unsupported_hardware -En "<EN>" -Ru "<RU>" -Uk "<UK>"`
>
> The message states that the device cannot play this file because its video is too high-resolution / its format is not supported by the device's hardware decoder, includes the file name placeholder `%1$s` (match the `video_playback_failed_with_name` style), and does not expose a raw codec name. Phrase it per `docs/COMMUNICATION_POLICY.md` §2 (error message formula: what happened + why + what the user can do, friendly tone). Russian text uses `..` (not `...`) and `ё`/`Ё` where grammatically correct. The string must pass the `docs/COMMUNICATION_POLICY.md` §6 tone checklist before commit.

**Verification:**

- `Grep` - `name="video_decoder_unsupported_hardware"` matches exactly once in each of the three `strings.xml` files.
- `Grep` - the EN/RU/UK values each contain the `%1$s` placeholder.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist (expected: all items pass | actual: recorded in step log).
- Russian value uses `..` not `...` and uses `ё` where applicable (expected: yes | actual: recorded).

**Status:** `[ ]` not done

---

### Step 04.2 - Audit locale parity for the new key

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the localization audit for the new key:
>
> `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "video_decoder_unsupported_hardware"`
>
> Exit code 1 means a locale is missing the key - fix before commit. The tool covers `src/main/res` only; this key lives there, so the audit is authoritative.

**Verification:**

- Script exit code (expected: 0 | actual: recorded in step log).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Target variant build passes - run `/build` (config/xml change). Build confirms the EN key resolves; RU/UK parity is confirmed by Step 04.2, not by the build.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the three `strings.xml` files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- The decoder-refusal string exists in EN/RU/UK; the Phase 03 branch now resolves at runtime.
- Phase 05 (cleanup) regenerates the catalog and the dev log; no FEATURES update (strategic §8 = "Без изменений").

---

## Rollback Plan

Revert the phase commit(s) and remove the key via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action remove -Key video_decoder_unsupported_hardware`. No data migration or schema change.
