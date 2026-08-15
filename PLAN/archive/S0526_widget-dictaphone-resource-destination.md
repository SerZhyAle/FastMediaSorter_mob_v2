# Стратегическая спецификация: S0526 - Виджет-диктофон сохраняет в выбранный ресурс

**Ticket:** S0526
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-19
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-19
**Tactical spec:** `PLAN/S0526_widget-dictaphone-resource-destination/` (создан через `/spec-tech`)
**Tactical plan:** `PLAN/S0526_widget-dictaphone-resource-destination/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Запись, сделанная через виджет быстрого диктофона на домашнем экране, всегда сохраняется в приватный внешний каталог приложения и не появляется ни в выбранном пользователем ресурсе назначения, ни в публичной папке. Пользователь, указавший в настройках место сохранения записей с микрофона, обоснованно ждёт, что и виджет положит запись туда же, но виджет полностью игнорирует эту настройку.

В результате запись с виджета практически не видна: её нет в выбранном ресурсе, нет в общих папках устройства, и обнаружить её можно лишь через файловый менеджер в приватной директории приложения. Поведение виджета расходится с записью с микрофона из экрана просмотра, который уже уважает выбранное назначение.

Область - функция быстрой аудиозаписи через виджет домашнего экрана. Этот поток явно вынесен из объёма S0522 (fallback при недоступном ресурсе) как отдельная задача.

---

## 2. Цели

1. Запись с виджета-диктофона сохраняется в то же место назначения, что и запись с микрофона из экрана просмотра (выбранный пользователем ресурс).
2. При отсутствии выбранного назначения запись попадает в публичную папку по умолчанию для аудио, а не в приватную директорию приложения.
3. При недоступном сетевом/облачном назначении запись сохраняется локально по той же логике, что и остальные операции сохранения (без потери результата), и пользователь уведомляется.
4. Сообщение об итоге записи отражает фактическое место сохранения.

**Non-goals:**

- Изменение формата записи, параметров кодека или UX самого виджета.
- Очередь отложенной выгрузки в недоступный ресурс с повтором после восстановления сети.
- Новые места назначения или типы ресурсов.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Переиспользовать существующий путь разрешения назначения и fallback от записи с микрофона, а не вводить параллельную логику.
2. Уведомление о fallback - ненавязчивое, согласованное по тону с остальными уведомлениями о сохранении.

### 3.2 Жёсткие ограничения

- **Flavor:** функция быстрой аудиозаписи присутствует только там, где включена возможность записи с микрофона (standard, noLegal, legacy); в lite и photos она отключена существующим флагом возможностей. Новые флейвор-исходники не вводятся - поведение остаётся в общем коде под уже существующим флагом возможности записи; правила `dev/FLAVOR_DEVELOPMENT_RULES.md` не задействуются, так как флейвор-специфичных классов не добавляется.
- **API level:** без новой API-специфики сверх уже поддерживаемой; запись в публичные папки обязана идти через системное медиахранилище на API 29+ и прямой файловый доступ на более ранних - как в существующем пути сохранения записи.
- **Wear OS:** не затрагивается.
- **Производительность:** перенос/выгрузка записи выполняется вне главного потока; виджет-сервис завершает работу после сохранения без удержания ресурсов.
- **Совместимость данных:** без миграций; используется существующее поле настройки с идентификатором назначения для записей с микрофона.
- **Локализация:** EN/RU/UK - обязательно для любых новых или изменённых пользовательских строк.
- **Доступность:** уведомление самодостаточно по тексту, без опоры на цвет.
- **Коммуникационная политика:** тексты уведомлений/тостов соответствуют `docs/COMMUNICATION_POLICY.md` (тон-чеклист §6 - обязательный гейт перед интеграцией строк).

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** standard / noLegal / legacy - там, где включена запись с микрофона; lite и photos исключены существующим флагом возможностей, новые флейвор-исходники не добавляются.
- **Localization:** новые/изменённые строки итога и fallback - EN/RU/UK в lockstep.
- **Communication policy:** уведомления о сохранении и fallback проходят тон-чеклист §6 `docs/COMMUNICATION_POLICY.md`.
- **Validation level:** сборка затронутых флейворов + ручная проверка на устройстве: запись с виджета в локальное и в недоступное сетевое назначение.
- **Owner sign-off:** 2026-06-19
- **Related tickets:** S0522 (общий путь сохранения записи с микрофона и fallback)

---

## 4. Контекст текущей архитектуры

Запись с микрофона из экрана просмотра проходит через слой разрешения назначения (выбранный ресурс против локальной папки по умолчанию) и общий слой локальной записи, корректно различающий публичные коллекции и сетевую выгрузку; S0522 добавил туда единый fallback по недоступности и уведомление. Виджет-диктофон же реализован как самостоятельный фоновый сервис, который пишет напрямую в приватный каталог и не обращается ни к настройкам, ни к этому общему пути.

Проблему из §1 нельзя закрыть точечно, потому что виджет-сервис сегодня не подключён к слою приложения: он не получает доступ к настройкам, списку ресурсов, слою записи и средствам уведомления. Нужно дать сервису доступ к уже существующему пути сохранения записи и направить туда финальный файл вместо приватной директории.

---

## 5. Предлагаемый подход

Подключить виджет-сервис к существующему пути сохранения записи с микрофона: записывать во временный файл (как сейчас), а на остановке передавать готовый файл в общий механизм разрешения назначения и записи - тот же, что используется при записи из экрана просмотра. Виджет перестаёт самостоятельно решать, куда класть файл, и делегирует это общему слою, получая единый итог (успех в ресурс / fallback с причиной / отказ).

### 5.1 Основные столпы / модули

- **Доступ сервиса к слою приложения.** Дать фоновому виджет-сервису доступ к настройкам, списку ресурсов, слою локальной записи, проверке доступности сети и средству уведомления о fallback - тем же ролям, что уже используются при записи с микрофона.
- **Делегирование сохранения.** На остановке записи финальный файл проходит через общую политику выбора назначения и общий слой записи (локальная публичная папка или сетевая выгрузка) вместо прямого сохранения в приватный каталог.
- **Единый fallback и уведомление.** При недоступном назначении - локальная папка по умолчанию и уведомление, по тем же правилам, что ввёл S0522.

### 5.2 Потоки данных и событий

- Виджет → запись во временный файл → остановка → политика выбора назначения (выбранный ресурс + тип медиа + доступность) → общий слой записи → итог.
- Итог (успех / fallback по недоступности / отказ) → уведомление пользователю и сообщение о фактическом месте сохранения.

### 5.3 Точки расширяемости

- Виджет-сервис становится ещё одним потребителем общей политики выбора назначения - её сигнатура не меняется.
- Способ предоставления зависимостей фоновому сервису остаётся открытым к расширению на другие виджет-сервисы.

---

## 6. Открытые вопросы / Research items

1. **Назначение для виджета: общая настройка записи с микрофона или своя.**
   - **Вопрос:** использовать существующую настройку назначения для записей с микрофона или завести отдельную для виджета.
   - **Решение:** переиспользовать общую настройку назначения записей с микрофона - виджет ведёт себя как запись из приложения, новых настроек не вводится.
   - **Статус:** Resolved

2. **Сетевая выгрузка из фонового виджет-сервиса.**
   - **Вопрос:** допустима ли длительная сетевая выгрузка из виджет-сервиса и как обрабатывать её при завершении сервиса.
   - **Решение:** сетевая/облачная выгрузка входит в объём с полным паритетом - виджет выгружает в выбранный ресурс, при недоступности срабатывает локальный fallback. Конкретный механизм удержания/завершения foreground-сервиса до окончания выгрузки (и при необходимости перенос в отложенную задачу) определяется в тактической спеке.
   - **Статус:** Resolved

3. **Уведомление о fallback для виджета.**
   - **Вопрос:** тост или системное уведомление для фонового виджет-потока.
   - **Решение:** системное уведомление, как для прочих фоновых операций в S0522.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Сетевая выгрузка не успевает до завершения сервиса | Средняя | Запись не доходит до сетевого ресурса | Локальный fallback гарантирует сохранение; рассмотреть отложенную выгрузку (§6) |
| Существующие записи в приватном каталоге у текущих пользователей | Низкая | Прежние записи остаются в старом месте | Изменение действует на новые записи; миграция старых вне объёма |
| Расхождение поведения виджета и экрана просмотра | Низкая | Непредсказуемое место сохранения | Делегировать общий путь, не дублировать логику |

---

## 8. Влияние на пользователя (docs/FEATURES)

Воспринимаемое изменение: запись с виджета-диктофона теперь сохраняется в выбранное место назначения (как запись из приложения), а не в скрытую папку. Одно предложение для `docs/FEATURES.md` + `_RU` + `_UK` (фактический текст готовит `/skill-release` из диффа ALL_FEATURES).

---

## 9. Архитектурные решения (ADR)

**ADR-1: Делегировать сохранение виджета общему пути записи с микрофона**

- **Решение:** виджет-сервис передаёт готовый файл в существующую политику назначения и слой записи, а не сохраняет сам.
- **Альтернативы:** продублировать в виджете логику разрешения назначения и fallback.
- **Почему:** единый путь устраняет расхождение поведения и переиспользует уже проверенный fallback из S0522.

---

## 10. Связи с другими спеками

S0522 - ввёл общий fallback и уведомление для записи с микрофона; данный тикет распространяет тот же путь на виджет-диктофон.

---

## 11. Критерии готовности (strategic-level)

1. Запись, сделанная через виджет, появляется в выбранном пользователем месте назначения.
2. При отсутствии выбранного назначения запись попадает в публичную папку по умолчанию для аудио, а не в приватный каталог.
3. При недоступном сетевом назначении запись сохраняется локально и пользователь уведомляется.
4. Сообщение об итоге записи указывает фактическое место сохранения.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0526` - создаст `PLAN/S0526_widget-dictaphone-resource-destination/` с фазами.

---

## Last Audit

### Manual device test - emulator-5556 - 2026-06-19 (all 4 scenarios)

**Device:** emulator-5556 (Pixel, standard flavor)
**Build:** 2.60.6191.257-DEBUG
**Artifacts:** `temp/S0526_devtest/` (screenshots `01`..`05`, `EVIDENCE.txt`)
**Widget placement:** PASS - "Quick Recorder" 1x1 widget (label `widget_quick_audio_recorder_label`) found in the Pixel launcher widget picker under "Fast Media Sorter & Organizer", dragged onto home, bound to the launcher host (verified via `dumpsys appwidget`). Tap fires `QUICK_RECORDER_TOGGLE` -> `QuickAudioRecorderActivity` -> `QuickAudioRecorderService`.

**Scenario 1 (widget -> selected resource): PASS**

- Mic destination set in Settings (Management -> Camera, microphone and Other features -> Microphone recordings destination) to "Docs" = `/storage/emulated/0/Download/FastMediaSorter_Test/Docs`.
- Widget tap (start) + tap (stop+save). Probe fired: `QuickAudioRecorderService$stopAndSave: S0526: widget recording -> shared MicRecordingSaver.save (selected destination + network upload + local fallback)`.
- expected: clip in selected resource | actual: `/storage/emulated/0/Download/FastMediaSorter_Test/Docs/REC_20260619_181653.m4a` (281175 B), published `content://media/external/downloads/1000000208`. Public Recordings/Music untouched; private `files/Music` empty.

**Scenario 2 (no destination -> public folder): PASS**

- Mic destination cleared ("Default: Downloads folder"). Widget record.
- expected: clip in public default audio folder, not private | actual: `/sdcard/Download/REC_20260619_181847.m4a` (175989 B), published `content://media/external/downloads/1000000209`; private `files/Music` empty.

**Scenario 3 (unreachable network -> local fallback + background notification): PASS**

- Mic destination set to "S0483_BogusSMB" = `smb://10.255.255.1/Common` (resource flagged `isDestination=1` via DB edit so it appears in the picker; host unreachable). Widget record.
- Probe fired; SMB upload attempted and failed: `LocalToSmbStrategy: Copying ... to smb://10.255.255.1/Common/...` -> `SMB TCP precheck failed: 10.255.255.1:445` -> `Server unreachable` -> `SMB upload failed`.
- expected: local fallback + background notification | actual: fallback file `/sdcard/Download/REC_20260619_182316.m4a` (154534 B); background notification on channel `save_fallback` (importance 2, Silent section): "Saved to Download - S0483_BogusSMB is unavailable" (verified via `dumpsys notification` + shade screenshot `05`). No data lost; private temp cleaned.

**Scenario 4 (in-app Browse mic regression): PASS**

- Browse "All Files" (`/storage/emulated/0`); the mic toolbar button is press-and-hold (ACTION_DOWN start, ACTION_UP stop; <300ms release = single tap). A quick tap aborts as a handled too-short race (`MIN_VALID_RECORDING_BYTES`); a 4s long-press produced a valid clip and the "Save Recording" dialog -> OK.
- Probe fired: `BrowseMicRecordingManager: S0526: in-app mic recording -> shared MicRecordingSaver.save (refactor regression check)`.
- expected: saves correctly | actual: `/storage/emulated/0/REC_20260619_182802.m4a` (50601 B) via `FileSystemSink.commit` (into the browsed resource root, mic destination cleared); private `files/Music` empty. Phase 01 shared-saver refactor regression-free.

**Verdict: PASS** - all four acceptance scenarios verified on a real launcher-placed widget. Both S0526 `Timber.d` probes present (consistent with BlockNeedUserTest).

### Earlier audit (emulator-5554)

**Date:** 2026-06-19
**Device:** emulator-5554 (Pixel 4, Android 17 SDK 37)
**Build:** 2.60.6191.257-DEBUG
**Artifacts:** `temp/S0526_device_test_20260619_1016/`

**Scenario 2 (no destination → public folder): PASS**

- Widget placed on home screen and tapped; icon turned red (recording active), then blue after stop.
- File `/sdcard/Download/REC_20260619_143833.m4a` (428 KB) appeared in public Downloads folder.
- App private dir `/sdcard/Android/data/com.sza.fastmediasorter.debug/files/Music/` was empty.
- Logcat S0526 probe fired: `S0526: widget recording -> shared MicRecordingSaver.save (selected destination + network upload + local fallback)`.
- `mic_recording_destination_resource_id` absent from DataStore → `targetResource == null` → `CaptureDestinationPolicy.resolveMicDestination(null)` → public Downloads. Correct.

**Scenario 4 (in-app Browse mic regression): PASS**

- "Запись с диктофона" from overflow menu started recording; dialog showed elapsed time.
- After "Остановить и сохранить": file `/sdcard/Recordings/REC_20260619_144129.m4a` (231 KB) appeared.
- Logcat confirmed `MediaStoreLocalDestinationWriter$MediaStoreSink.commit: published content://media/external/audio/media/139`.
- Browse mic path unaffected by S0526 changes.

**Scenario 1 (widget → selected resource): INCONCLUSIVE** — requires a real local/network resource configured in Settings; not available on emulator without seeded resources.

**Scenario 3 (unreachable network → local fallback + notification): INCONCLUSIVE** — requires an unreachable network resource; not testable on emulator.

**Verdict: PASS (emulator-testable paths)** — emulator-testable scenarios 2 and 4 both pass. Scenarios 1 and 3 require device + network resource for full verification.
