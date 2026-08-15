# Спецификация (compact bugfix): S1264 - Из аудиоплеера нельзя выйти кнопкой «назад» при активном воспроизведении

**Ticket:** S1264
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-28
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-28

**Текст:**

Audio player cannot be exited with Back while playback is active. Observed 2026-07-28 on emulator-5556 (Android 13, standard debug 2.60.7262.102) while driving the app for S1256 store screenshots. With an audio file playing in PlayerActivity, six hardware BACK keyevents and one tap on the in-app btnBack all left topResumedActivity = com.sza.fastmediasorter.ui.player.PlayerActivity (same task t240); the UI was alive throughout (position counter advanced 00:10 -> 02:00). Sending KEYCODE_MEDIA_STOP (86) first, then force-stop plus launch, reached MainActivity normally. Suspected cause: the main screen returns to now-playing while a media session is active, so leaving the player is impossible without stopping playback. User-visible symptom: while music plays you cannot get back to the resource list with Back.

**Захвачено во время:** S1256

---

## 1. Проблема / симптом

Пока играет аудиофайл, экран плеера не отпускает пользователя: ни аппаратная кнопка «назад», ни кнопка возврата в верхней панели самого плеера не возвращают к списку файлов.

Эвиденс, снятый 2026-07-28 на `emulator-5556` (Android 13, SDK 33), сборка standard debug `2.60.7262.102`:

- шесть подряд `input keyevent BACK` и один тап по `btnBack` в панели плеера - после каждого `topResumedActivity` остаётся `com.sza.fastmediasorter.ui.player.PlayerActivity`, задача та же (`t240`);
- интерфейс при этом живой, воспроизведение идёт, счётчик позиции шёл с 00:10 до 02:00;
- `input keyevent 86` (`KEYCODE_MEDIA_STOP`), затем force-stop и запуск - главный экран открывается штатно.

Предполагаемая причина: при активной медиасессии возврат на главный экран сразу переоткрывает «сейчас играет», поэтому выйти из плеера, не остановив воспроизведение, невозможно. Требует проверки - возможно, поведение задумано как «вернуться к текущему треку», но тогда оно отрезает пользователю единственный путь назад.

## 2. Корневая причина

Расследовано 2026-07-28 на private emulator-5588 (API 33) с перенесённым с 5556 файлом настроек (`settings.preferences_pb`, read-only копия). Репро воспроизведён точь-в-точь: при `enable_background_audio=true` + `background_audio_exit_behavior=ASK` и играющем аудио два BACK подряд оставляют `topResumedActivity=PlayerActivity`.

Цепочка BACK прослежена рантайм-зондами по всей глубине:

- BACK#1: `handleOnBackPressed` срабатывает (оверлеи не блокируют), `exitPlayerWithAudioCheck` -> serviceAudio активен -> резолвер возвращает `ASK` -> `BackgroundAudioExitDialog.show()` вызывается штатно.
- BACK#2: до колбэка активности НЕ доходит - его потребляет ПОКАЗАННЫЙ диалог (владеет вводом), закрываясь.
- BACK#3 (к этому моменту 23-секундный трек закончился): резолвер по ветке «сервис-аудио уже на паузе» -> `STOP_AND_FINISH` -> штатный выход в Browse.

Сам дефект - **диалог существует, но не рендерится, пока активен `adb shell wm size`/`wm density` оверрайд** (артефакт композитинга API33-эмулятора; в тех же состояниях `screencap` начинает возвращать 0 байт). Оба наблюдения велись под оверрайдами: исходное - при съёмке стор-скриншотов S1256 (экран ресайзился), моё - на 1024x600@160. На родной геометрии тот же сценарий показывает полноценный 4-пунктовый диалог «Music is playing in the background..» (Stop / Keep Playing / Always Stop / Always Continue), выбор пункта выходит из плеера.

Слепой прогон keyevent'ами интерпретировал невидимку как «BACK не работает»: каждая пара BACK открывала/закрывала невидимый диалог. `KEYCODE_MEDIA_STOP` «помогал», потому что останавливал воспроизведение - после этого резолвер выходит без диалога.

---

## 3. Исправление

Не требуется - поведение приложения корректно по дизайну (S0577): при активном фоновом аудио выход из плеера спрашивает, что делать с воспроизведением. Дефект рендеринга диалогов под wm-оверрайдом - квирк эмулятора/платформы, вне контроля приложения; в тестовый обиход занесено правило «диалоги проверяются на родной геометрии» (память агента).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1219 (background-playback-left-panel), S1224 (player-copy-move-background) - соседняя область фонового воспроизведения, пересечения по итогам расследования нет.

---

## 4. Проверка

Выполнено 2026-07-28, emulator-5588, сборка v2.60.7262.102:

- Родная геометрия (1080x2340@440), фоновое аудио включено, поведение ASK, трек играет: BACK -> виден диалог `background_audio_exit_message` с 4 пунктами - `temp/scratch/s1264-native-back.png`.
- Дефолтные настройки (фоновое аудио выключено): BACK мгновенно возвращает в Browse (serviceAudio неактивен -> `doFinish`).
- Настройки с 5556 + wm 1024x600@160: репро «залипания» подтверждён и объяснён (невидимый диалог); лог-цепочка зондов в §2.
- input_bindings на 5556 пуст (SELECT count(*) = 0) - пользовательские ремапы исключены; дефолтная карта байндингов не содержит `key:4` (KEYCODE_BACK).

expected: BACK при играющем аудио даёт видимый выбор, любой пункт покидает плеер | actual: так и есть на родной геометрии - PASS. Вердикт: не баг приложения.

---

## Last Audit

**Дата:** 2026-07-28. **Вердикт:** не баг - поведение по дизайну; наблюдение было артефактом тестовой среды (невидимость диалогов под wm-оверрайдом на API33-эмуляторе + слепой keyevent-прогон). Диагностические зонды из `PlayerLifecycleManager` удалены, код не менялся. Тикет архивируется.
