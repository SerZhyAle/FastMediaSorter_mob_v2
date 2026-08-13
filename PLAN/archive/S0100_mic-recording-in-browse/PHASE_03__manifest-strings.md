# Phase 03 — Manifest & Strings

**Strategic spec:** [`../S0100_mic-recording-in-browse.md`](../S0100_mic-recording-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04, 05
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Declare `RECORD_AUDIO` permission in the manifest and add all trilingual string resources for the feature.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 500 |

---

## Steps

### Step 3.1 — Declare RECORD_AUDIO in AndroidManifest.xml

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `AndroidManifest.xml`, add the following line in the permissions block, after the `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission (audio section):
> ```xml
> <!-- Microphone recording (S0100) — not required for install -->
> <uses-permission android:name="android.permission.RECORD_AUDIO" android:required="false" />
> ```
> `android:required="false"` ensures the permission is not a hard install requirement on devices without a microphone.

**Verification:**

- `Grep` — `RECORD_AUDIO` present in `AndroidManifest.xml`.
- `Grep` — `android:required="false"` present in `AndroidManifest.xml`.

**Status:** `[ ]` not done

---

### Step 3.2 — Add EN string resources

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** — start of phase (independent of 3.1)

**Prompt for developer:**

> Add the following string resources to `values/strings.xml` in the "Settings — Audio" section:
>
> ```xml
> <!-- S0100 Microphone recording -->
> <string name="settings_mic_recording_section_title">Microphone Recording</string>
> <string name="settings_mic_recording_enable_title">Enable microphone recording</string>
> <string name="settings_mic_recording_enable_desc">Show microphone button in Browse to record audio into the current folder</string>
> <string name="settings_mic_recording_ask_filename_title">Ask for filename</string>
> <string name="settings_mic_recording_ask_filename_desc">Show rename dialog before saving the recording</string>
> <string name="mic_recording_button_content_desc">Record audio</string>
> <string name="mic_recording_hold_hint">Hold to record</string>
> <string name="mic_recording_filename_title">Save Recording</string>
> <string name="mic_recording_permission_rationale">Microphone access is required to record audio</string>
> <string name="mic_recording_permission_denied">Microphone permission denied. Grant it in system settings.</string>
> <string name="mic_recording_saved">Recording saved: %s</string>
> <string name="mic_recording_error_save">Failed to save recording</string>
> <string name="mic_recording_error_start">Failed to start recording</string>
> <string name="mic_recording_cancelled">Recording cancelled</string>
> ```

**Verification:**

- `Grep` — `mic_recording_enable_title` present in `values/strings.xml`.
- `Grep` — `mic_recording_saved` present in `values/strings.xml`.

**Status:** `[ ]` not done

---

### Step 3.3 — Add RU string translations

**Files:** `app_v2/src/main/res/values-ru/strings.xml`
**Depends on:** Step 3.2

**Prompt for developer:**

> Add the following string resources to `values-ru/strings.xml` (same key order as EN):
>
> ```xml
> <!-- S0100 Microphone recording -->
> <string name="settings_mic_recording_section_title">Запись с микрофона</string>
> <string name="settings_mic_recording_enable_title">Включить запись с микрофона</string>
> <string name="settings_mic_recording_enable_desc">Показывать кнопку микрофона в Browse для записи аудио в текущую папку</string>
> <string name="settings_mic_recording_ask_filename_title">Спрашивать имя файла</string>
> <string name="settings_mic_recording_ask_filename_desc">Показывать диалог переименования перед сохранением записи</string>
> <string name="mic_recording_button_content_desc">Запись аудио</string>
> <string name="mic_recording_hold_hint">Удержите для записи</string>
> <string name="mic_recording_filename_title">Сохранить запись</string>
> <string name="mic_recording_permission_rationale">Для записи аудио необходим доступ к микрофону</string>
> <string name="mic_recording_permission_denied">Доступ к микрофону отклонён. Разрешите его в системных настройках.</string>
> <string name="mic_recording_saved">Запись сохранена: %s</string>
> <string name="mic_recording_error_save">Не удалось сохранить запись</string>
> <string name="mic_recording_error_start">Не удалось начать запись</string>
> <string name="mic_recording_cancelled">Запись отменена</string>
> ```

**Verification:**

- `Grep` — `mic_recording_enable_title` present in `values-ru/strings.xml`.
- `Grep` — `mic_recording_saved` present in `values-ru/strings.xml`.

**Status:** `[ ]` not done

---

### Step 3.4 — Add UK string translations

**Files:** `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 3.2

**Prompt for developer:**

> Add the following string resources to `values-uk/strings.xml`:
>
> ```xml
> <!-- S0100 Microphone recording -->
> <string name="settings_mic_recording_section_title">Запис із мікрофона</string>
> <string name="settings_mic_recording_enable_title">Увімкнути запис із мікрофона</string>
> <string name="settings_mic_recording_enable_desc">Показувати кнопку мікрофона в Browse для запису аудіо в поточну папку</string>
> <string name="settings_mic_recording_ask_filename_title">Запитувати ім\'я файлу</string>
> <string name="settings_mic_recording_ask_filename_desc">Показувати діалог перейменування перед збереженням запису</string>
> <string name="mic_recording_button_content_desc">Запис аудіо</string>
> <string name="mic_recording_hold_hint">Утримайте для запису</string>
> <string name="mic_recording_filename_title">Зберегти запис</string>
> <string name="mic_recording_permission_rationale">Для запису аудіо потрібен доступ до мікрофона</string>
> <string name="mic_recording_permission_denied">Доступ до мікрофона відхилено. Надайте його в системних налаштуваннях.</string>
> <string name="mic_recording_saved">Запис збережено: %s</string>
> <string name="mic_recording_error_save">Не вдалося зберегти запис</string>
> <string name="mic_recording_error_start">Не вдалося почати запис</string>
> <string name="mic_recording_cancelled">Запис скасовано</string>
> ```
>
> After adding all three locale files, run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "mic_recording"` and verify exit code 0.

**Verification:**

- `Grep` — `mic_recording_enable_title` present in `values-uk/strings.xml`.
- `Grep` — `mic_recording_saved` present in `values-uk/strings.xml`.
- `check_strings_localized.ps1 -KeyPrefix "mic_recording"` exits with code 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for all four files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All mic recording string keys exist in EN/RU/UK. `RECORD_AUDIO` declared in manifest. Phases 04 and 05 can reference these resources.

---

## Rollback Plan

Revert phase commit(s) — strings and manifest permissions are additive and do not affect existing functionality.

---

## Revision History

- **2026-05-06** — by `/spec-update` (`claude-sonnet-4-6`, focus: all)
  - Applied: 1. Proposed (DISCUSS): 0.
  - Step 3.1 Verification: replaced ambiguous "on the same line or adjacent attribute" predicate with a simple static `Grep` check.
