# Phase 05 — String Fixes

**Strategic spec:** [`../S0054_m2ts-playback-support.md`](../S0054_m2ts-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — pure resource change
**Blocks:** Phase 06
**Steps done:** 1 / 1
**Started:** 2026-05-03
**Completed:** 2026-05-04

---

## Objective

Fix the existing `error_bdts_format_message` strings (which incorrectly say "network source") and add new strings for the audio-unsupported diagnostic shown in Phase 06.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | existing |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | existing |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | existing |

---

## Steps

### Step 05.1 — Update BD-TS error strings and add audio-unsupported strings in all three locales

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> **EN (`values/strings.xml`):**
>
> Replace the existing `error_bdts_format_message`:
> ```xml
> <string name="error_bdts_format_message">Blu-ray Transport Stream (.m2ts) could not be played.\n\nIf all audio tracks use TrueHD or DTS-HD MA, select a different track or transcode the file to MP4/MKV (HandBrake, ffmpeg).</string>
> ```
>
> Add two new strings immediately after `error_bdts_format_message`:
> ```xml
> <string name="warning_m2ts_audio_unsupported">Audio tracks not decodable in this build: %1$s.\nPlaying without sound. Supported: AC-3, E-AC-3, DTS (core), AAC.</string>
> <string name="warning_m2ts_audio_unsupported_title">Unsupported Audio Tracks</string>
> ```
>
> **RU (`values-ru/strings.xml`):**
>
> Replace `error_bdts_format_message`:
> ```xml
> <string name="error_bdts_format_message">Blu-ray Transport Stream (.m2ts) не удалось воспроизвести.\n\nЕсли все аудиодорожки используют только TrueHD или DTS-HD MA, выберите другую дорожку или перекодируйте файл в MP4/MKV (HandBrake, ffmpeg).</string>
> ```
>
> Add after it:
> ```xml
> <string name="warning_m2ts_audio_unsupported">Аудиодорожки недоступны в этой сборке: %1$s.\nВоспроизведение без звука. Поддерживаются: AC-3, E-AC-3, DTS (core), AAC.</string>
> <string name="warning_m2ts_audio_unsupported_title">Неподдерживаемые аудиодорожки</string>
> ```
>
> **UK (`values-uk/strings.xml`):**
>
> Replace `error_bdts_format_message`:
> ```xml
> <string name="error_bdts_format_message">Blu-ray Transport Stream (.m2ts) не вдалося відтворити.\n\nЯкщо всі аудіодоріжки використовують лише TrueHD або DTS-HD MA, оберіть іншу доріжку або перекодуйте файл у MP4/MKV (HandBrake, ffmpeg).</string>
> ```
>
> Add after it:
> ```xml
> <string name="warning_m2ts_audio_unsupported">Аудіодоріжки недоступні в цій збірці: %1$s.\nВідтворення без звуку. Підтримуються: AC-3, E-AC-3, DTS (core), AAC.</string>
> <string name="warning_m2ts_audio_unsupported_title">Непідтримувані аудіодоріжки</string>
> ```

**Verification:**

- `Grep` — `warning_m2ts_audio_unsupported` present in `values/strings.xml`.
- `Grep` — `warning_m2ts_audio_unsupported` present in `values-ru/strings.xml`.
- `Grep` — `warning_m2ts_audio_unsupported` present in `values-uk/strings.xml`.
- `Grep` — `сетевого ресурса` absent in `values-ru/strings.xml` (old wording removed).
- `Grep` — `network source` absent in `values/strings.xml` (old wording removed).
- `Grep` — `мережевого ресурсу` absent in `values-uk/strings.xml` (old wording removed).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 6/6 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 05.* above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-05-04 (assembleStandardDebug).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `R.string.warning_m2ts_audio_unsupported` and `R.string.warning_m2ts_audio_unsupported_title` are available for Phase 06 audio diagnostics.
- `error_bdts_format_message` is now source-agnostic (no more "network source" wording).

---

## Rollback Plan

Revert phase commit(s) — string-only change, no code or schema impact.
