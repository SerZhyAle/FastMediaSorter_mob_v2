# Phase 03 - Trilingual strings

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05, Phase 06, Phase 07
**Steps done:** 1 / 1
**Started:** 2026-06-29
**Completed:** 2026-06-29

> **Phase Step Log (2026-06-29):** 17 keys added via `set-android-string.ps1 -Action add` (4 → strings_settings.xml, 13 → strings.xml). Parity audits: `screen_recording` 13/13, `settings_screen_recording` 2/2, `setting_screen_recording_destination` 2/2 - all EN/RU/UK OK. `.\a.ps1 fr` BUILD SUCCESSFUL.

---

## Objective

Add every user-visible string the feature needs (settings rows, scenario label, notification, in-app card, disclosure, results) across EN/RU/UK in lockstep, before any UI references them.

---

## Prerequisites

- [ ] None.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_settings.xml` (+ `values-ru`, `values-uk`) | Modified | settings keys |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | scenario/notif/card keys |

> Exact target files follow the existing grouping: settings labels live with the other `setting_*` keys; scenario/notification/card/result strings live with the other main-window strings. Use `scripts/utils/set-android-string.ps1 -Action add` so EN/RU/UK stay parity-locked.

---

## Steps

### Step 03.1 - Add all strings (EN/RU/UK lockstep)

**Files:** `strings*.xml` across `values`, `values-ru`, `values-uk`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the keys below, each via one `scripts/utils/set-android-string.ps1 -Action add -Key <k> -En "<en>" -Ru "<ru>" -Uk "<uk>"` call (parity-enforced). Follow `docs/COMMUNICATION_POLICY.md` §2 (message formula per type) and §6 (tone checklist): action labels are imperative, the disclosure is a plain factual notice, results are short confirmations. Use `..` not `...` and Ё where grammatical.
>
> Settings:
> - `settings_screen_recording_enable_title` — EN "Screen video recording" · RU "Видеозапись экрана" · UK "Відеозапис екрана"
> - `settings_screen_recording_enable_desc` — EN "Record the whole screen to a video file with microphone audio. A stop control and a timer appear while recording." · RU "Записывать весь экран в видеофайл со звуком микрофона. Во время записи доступны кнопка остановки и таймер." · UK "Записувати весь екран у відеофайл зі звуком мікрофона. Під час запису доступні кнопка зупинки та таймер."
> - `setting_screen_recording_destination_title` — EN "Screen recordings destination" · RU "Ресурс для записей экрана" · UK "Ресурс для записів екрана"
> - `setting_screen_recording_destination_default_downloads` — EN "Default: Downloads folder" · RU "По умолчанию: папка Downloads" · UK "За замовчуванням: тека Downloads"
>
> Scenario (menu + panel):
> - `screen_recording_menu_label` — EN "Screen video recording" · RU "Видеозапись экрана" · UK "Відеозапис екрана"
>
> Foreground-service notification:
> - `screen_recording_notification_channel` — EN "Screen recording" · RU "Запись экрана" · UK "Запис екрана"
> - `screen_recording_notification_title` — EN "Recording screen" · RU "Идёт запись экрана" · UK "Триває запис екрана"
> - `screen_recording_notification_text` — EN "Tap Stop to finish and save." · RU "Нажмите «Стоп», чтобы завершить и сохранить." · UK "Натисніть «Стоп», щоб завершити та зберегти."
> - `screen_recording_notification_stop` — EN "Stop" · RU "Стоп" · UK "Стоп"
>
> In-app card:
> - `screen_recording_card_title` — EN "Recording screen" · RU "Идёт запись экрана" · UK "Триває запис екрана"
> - `screen_recording_stop` — EN "Stop recording" · RU "Остановить запись" · UK "Зупинити запис"
>
> Disclosure (first-run, before system consent):
> - `screen_recording_disclosure_title` — EN "Screen video recording" · RU "Видеозапись экрана" · UK "Відеозапис екрана"
> - `screen_recording_disclosure_message` — EN "FastMediaSorter will record everything shown on your screen, including other apps, to a video file with microphone audio until you stop it. Recording continues in the background." · RU "FastMediaSorter будет записывать всё, что отображается на экране, включая другие приложения, в видеофайл со звуком микрофона, пока вы не остановите запись. Запись продолжается в фоне." · UK "FastMediaSorter записуватиме все, що показано на екрані, включно з іншими застосунками, у відеофайл зі звуком мікрофона, доки ви не зупините запис. Запис триває у фоні."
> - `screen_recording_disclosure_start` — EN "Start recording" · RU "Начать запись" · UK "Почати запис"
>
> Results / errors:
> - `screen_recording_permission_denied` — EN "Microphone and notification permissions are required to record the screen." · RU "Для записи экрана нужны разрешения на микрофон и уведомления." · UK "Для запису екрана потрібні дозволи на мікрофон і сповіщення."
> - `screen_recording_saved` (positional `%1$s`) — EN "Screen recording saved: %1$s" · RU "Запись экрана сохранена: %1$s" · UK "Запис екрана збережено: %1$s"
> - `screen_recording_error` — EN "Could not save the screen recording." · RU "Не удалось сохранить запись экрана." · UK "Не вдалося зберегти запис екрана."
>
> Reuse existing `cancel` for the disclosure/card negative button - do not add a new cancel key.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screen_recording"` → exit 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_screen_recording"` → exit 0.
- `Grep` - each key present in `values`, `values-ru`, `values-uk`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Step `[x]`.
- [ ] `check_strings_localized.ps1` exits 0 for both prefixes.
- [ ] `.\a.ps1 fr` (resources/manifest) green.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

All feature strings exist and are parity-checked. Phases 05/06/07 reference them by `R.string.*` without adding strings.

---

## Rollback Plan

Revert the phase commit - additive string keys only.
