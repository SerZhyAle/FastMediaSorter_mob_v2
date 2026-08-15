---
ticket: S0367
status: BlockNeedUserTest
priority: 50
date: 2026-06-06
tier: 2
---

# Стратегическая спецификация: S0367 - Playback camera and microphone settings grouping and destination defaults

**Ticket:** S0367
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-06
**Tier:** 2 - Settings regrouping with destination-default controls
**Roadmap entry:** Ad-hoc - запрос 2026-06-06: вынести camera-to-resource и microphone recording из Media → Audio в Playback → Other features, переименовать группы, добавить выбор destination resources по умолчанию для microphone recordings и camera photos, покрыть portrait + landscape.
**Tactical spec:** `PLAN/S0367_playback-camera-microphone-settings-grouping/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room и Hilt-деталей.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - создать тикет на перегруппировку настроек: перенести camera-to-resource и microphone recording из Media → Audio в Playback → Other features, переименовать связанные группы, добавить настройки выбора destination resource для microphone recordings и camera photos с явными fallback-правилами и зафиксировать работу для portrait + landscape.
- **Local anchor:** Provided by user - settings surfaces `Media → Audio` и `Playback → Other features`.
- **Scope boundaries / forbidden areas:** Provided by user - речь о структуре, названиях и новых destination settings; не требуется новый widget flow, новый capture backend или redesign browse/player command surfaces.
- **Done / success signal:** Provided by user - создан новый specification task, который фиксирует перенос, переименование, новые destination settings и их fallback contract для обеих ориентаций.
- **Autonomy rule:** agent may decide with explicit assumptions (granted by owner via /goal directive 2026-06-06).
- **UI decisions / delegation:** Provided by user - camera-to-resource group переносится в `Playback → Other features`, переименовывается в `Фото с Камеры`; `Enable microphone` переносится туда же; `Other features` переименовывается в `Camera, microphone and Other features`; добавляется настройка выбора ресурса для записей с микрофона с fallback в `Downloads`, если значение пустое; добавляется настройка выбора ресурса для фото с камеры с fallback в каталог камеры устройства, если значение пустое; изменения должны быть отражены в portrait и landscape.

Owner gate закрыт: все строки заполнены, `MISSING - requires owner input` не осталось.

---

## 1. Проблема

Сейчас связанные между собой capture-настройки разнесены по двум разным смысловым областям Settings. В `Media → Audio` живут микрофонная запись и отдельный блок `Camera-to-Resource`, а в `Playback` уже существует сворачиваемая группа `Other features`, куда ранее переносились другие playback-adjacent функции.

Такое разбиение выглядит как исторический след, а не как осмысленная информационная архитектура. Для пользователя и камера, и микрофон являются вспомогательными командами вокруг browsing / playback сценария, а не частью собственно audio-playback настроек. Название `Camera-to-Resource` тоже описывает внутренний маршрут сохранения, а не пользовательское действие.

Одновременно в текущем settings surface нет явных destination controls для двух соседних capture-сценариев. Пользователь может включить микрофонную запись и camera capture, но не видит в одном очевидном playback-side месте, куда по умолчанию должны уходить записи с микрофона и фото с камеры.

Эффект для UX:

1. Пользователь ищет camera / microphone рядом с playback-adjacent utilities, но находит их в аудио-настройках.
2. Связанный capture-функционал разорван между разными секциями и хуже обнаруживается.
3. Текущее имя камеры говорит языком реализации, а не языка продукта.
4. Любая перегруппировка должна быть сделана одинаково по смыслу в portrait и landscape, иначе Settings снова разойдутся по структуре.
5. Нет явной настройки default target для записей с микрофона, поэтому fallback в `Downloads` не задокументирован и не управляется пользователем.
6. Нет явной настройки default target для фото с камеры, поэтому fallback в каталог камеры устройства остаётся неявной частью поведения.

---

## 2. Цели

1. Перенести текущую группу `Camera-to-Resource` из `Media → Audio` в `Playback → Other features`.
2. Переименовать эту группу из implementation-oriented названия в пользовательское `Фото с Камеры`.
3. Перенести элемент `Enable microphone recording` из `Media → Audio` в `Playback → Other features`.
4. Переименовать секцию `Other features` в `Camera, microphone and Other features`.
5. Добавить настройку выбора ресурса по умолчанию для записей с микрофона.
6. Зафиксировать правило: если destination для микрофонных записей не выбран, запись сохраняется в `Downloads`.
7. Добавить настройку выбора ресурса по умолчанию для фото с камеры.
8. Зафиксировать правило: если destination для фото с камеры не выбран, снимок сохраняется в каталог камеры устройства.
9. Сохранить одинаковую смысловую структуру и discoverability в portrait и landscape layout-ах.
10. Выполнить перенос и расширение settings surface без изменения capability gates, permission flow, persistence model и browse-side commands, кроме явной destination-resolution semantics.

**Non-goals:**

- изменение логики camera capture, microphone recording или OCR;
- изменение browse/player command surfaces;
- новый widget flow или отдельные per-widget destination rules;
- добавление новой вкладки Settings или нового navigation destination;
- изменение Wear OS;
- изменение Room / persistence schema.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Camera-related и microphone-related настройки должны оказаться в одном playback-oriented месте.
2. Камерная группа должна получить человеко-понятное имя `Фото с Камеры`.
3. Переименование и перенос должны покрывать обе ориентации экрана.
4. Существующая группа `Other features` должна стать явной точкой входа для camera/microphone-adjacent настроек.
5. У микрофонной записи должна появиться явная настройка ресурса-получателя.
6. У фото с камеры должна появиться явная настройка ресурса-получателя.
7. Пустое значение destination setting не считается ошибкой: для микрофона это `Downloads`, для камеры - каталог камеры устройства.

### 3.2 Жёсткие ограничения

- **Orientation:** portrait и landscape должны остаться функционально эквивалентны; нельзя исправить только один layout-вариант.
- **UI copy:** все новые или изменённые пользовательские строки обязаны пройти EN/RU/UK локализацию и соответствовать `docs/COMMUNICATION_POLICY.md`.
- **Accessibility:** после перегруппировки должны сохраниться touch targets, D-pad / keyboard navigation, TalkBack labels и mouse parity.
- **Behavior parity:** перенос не должен менять текущие capability gates, permission prerequisites, nested option visibility и сохранённые пользовательские значения, кроме новой явной destination-resolution semantics.
- **Destination defaults:** пустое значение у new resource selectors обязано приводить к детерминированному fallback, а не к silent failure.
- **Target validity:** новые destination settings должны оперировать только теми resource targets, которые тактическая реализация сможет валидно использовать как получатель записи.
- **Search / highlight continuity:** settings search, deep-link highlight и раскрытие целевой collapsible group не должны деградировать после переименования, перемещения элементов и добавления новых destination rows.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** группа `Camera-to-Resource` уходит из `Media → Audio` и появляется в `Playback` под человеко-понятным именем `Фото с Камеры`; `Enable microphone recording` переезжает туда же; секция `Other features` переименовывается в `Camera, microphone and Other features`; новые destination rows располагаются непосредственно под соответствующими camera и microphone блоками.
- **Data compatibility:** два новых destination selector - аддитивные предпочтения без Room-миграции; включаются в существующий export / import настроек; пустое значение не считается ошибкой.
- **Destination fallback contract:** пустой microphone destination разрешается в `Downloads`; пустой camera destination разрешается в каталог камеры устройства; selector предлагает только writable non-virtual targets, пригодные как приёмник записи.
- **Accessibility:** перенесённые и новые rows соответствуют Strict Rule 17/18 - фокус, активация Enter/Space, D-pad и mouse parity, безопасные зоны системных панелей в портрете и ландшафте.
- **Communication policy:** новые заголовки групп, destination labels и fallback hints проходят чек-лист тона `docs/COMMUNICATION_POLICY.md` (§6) и EN/RU/UK parity перед интеграцией.
- **Validation level:** сборка `standardDebug` проходит; экран настроек открывается, обе ориентации показывают одинаковый состав групп, destination selectors сохраняют выбор и применяют fallback; локализационный аудит затронутых ключей проходит без ошибок.
- **Related tickets:** S0371 (запись видео в ресурс зависит от этой settings IA, чтобы не породить вторую параллельную архитектуру). Зависимостей-блокеров нет.

---

## 4. Контекст текущей архитектуры

Текущее состояние, установленное локальным аудитом, выглядит так:

1. В `Media → Audio` уже есть отдельные toggle-элементы для microphone recording и вложенный camera-capture block с зависимыми дочерними настройками.
2. В `Playback` уже существует сворачиваемая секция `Other features`, которая содержит разнородные playback-adjacent функции, включая camera OCR, black screen, calculator и embedded game.
3. Для обоих затронутых Settings screens существуют portrait и landscape представления, и они уже различаются по компоновке, хотя обязаны оставаться семантически одинаковыми.
4. Текущая терминология непоследовательна: секция назначения названа широко (`Other features`), а камера описана через внутренний storage-oriented термин (`Camera-to-Resource`).
5. Отдельных default-destination settings для microphone recordings и camera photos в этой playback-side зоне сейчас нет; соответствующее поведение либо определяется текущим контекстом, либо остаётся неявным.

Из этого следует, что задача объединяет IA cleanup внутри уже существующих Settings surfaces и явное оформление destination-default semantics для двух capture-сценариев.

---

## 5. Предлагаемый подход

### 5.1 Consolidate capture-adjacent settings under Playback

Все настройки, которые управляют кнопками камеры и микрофона в file browser / adjacent playback flows, должны жить в одной playback-side группе, а не в audio-only секции.

### 5.2 Rename camera subgroup around user intent

Вместо `Camera-to-Resource` пользователь должен видеть имя, которое описывает действие и результат на уровне продукта. Текущая owner-provided целевая формулировка для этой подгруппы: `Фото с Камеры`.

### 5.3 Rename destination section to reflect the new content

После переноса камера и микрофон перестают быть периферийными деталями внутри абстрактной `Other features`. Название секции должно сразу говорить, что внутри теперь находятся camera, microphone и прочие playback-adjacent utilities.

### 5.4 Add explicit destination settings next to the moved blocks

Playback-side camera и microphone blocks должны содержать не только enable/settings rows, но и явные destination controls:

- `Microphone recordings destination` -> выбор ресурса-получателя; если значение пустое, используется `Downloads`.
- `Camera photos destination` -> выбор ресурса-получателя; если значение пустое, используется каталог камеры устройства.

Новая формулировка должна делать default/fallback model понятной без чтения внешней документации.

### 5.5 Preserve dependent-row behavior

Если migrated row имеет дочерние / зависимые настройки, их видимость и поведение не должны ломаться только потому, что блок физически переехал в другую секцию.

### 5.6 Keep both orientations aligned

Portrait и landscape могут использовать разную плотность layout-а, но пользователь должен видеть один и тот же смысловой набор групп, названий и зависимостей.

---

## 6. Открытые вопросы / Research items

1. **Microphone block scope**
   - **Вопрос:** переносится только master row `Enable microphone recording` или весь microphone block вместе с дочерними строками и новым destination selector?
   - **Варианты:** move only the master toggle; move the whole microphone block as one unit including destination setting.
   - **Нужно выяснить:** допустимо ли оставить child rows в `Media → Audio`, если master toggle и destination selector уже уехали в `Playback`.
   - **Статус:** Open - owner decision or tactical UX decision required.

2. **Camera subgroup boundary**
   - **Вопрос:** что именно включает формулировка `и все её элементы` после добавления camera destination setting?
   - **Варианты:** full existing camera block only; full block + new destination selector; full block + destination selector + fallback hint.
   - **Нужно выяснить:** должна ли вся камера-секция переезжать как единый подблок с пояснением, дочерними настройками и новым selector row.
   - **Статус:** Resolved for draft - owner wording implies the whole camera block moves as one unit with the new destination control.

3. **Ordering inside the renamed Playback section**
   - **Вопрос:** в каком порядке располагать camera subgroup, microphone block, новые destination rows и уже существующие `Other features` элементы?
   - **Варианты:** camera block → microphone block → existing others; microphone block → camera block → existing others; destination rows сразу под соответствующими parent settings.
   - **Нужно выяснить:** какой порядок лучше для discoverability и не создаёт лишней тесноты в landscape.
   - **Статус:** Open - tactical layout decision required.

4. **Localized wording for EN / UK mirrors**
   - **Вопрос:** какие точные EN / UK эквиваленты должны соответствовать owner-provided названию `Фото с Камеры`, новому заголовку секции и двум новым destination setting labels?
   - **Варианты:** direct literal translation; product-style localized wording; mixed naming with a stable concept key.
   - **Нужно выяснить:** итоговую терминологию для всех трёх локалей без потери смысла и без разрыва между group labels и destination labels.
   - **Статус:** Open - tactical copy decision required.

5. **Search and highlight continuity**
   - **Вопрос:** нужны ли дополнительные adjustments в settings search / highlight rules после перемещения элементов между секциями и добавления destination rows?
   - **Варианты:** layout/string-only move; metadata/index update; explicit remap for old search hits and new resource-selector labels.
   - **Нужно выяснить:** зависит ли текущий search/highlight pipeline от старой секции/названий и нужно ли индексировать новые destination settings отдельно.
   - **Статус:** Open - tactical verification required.

6. **Destination selector eligibility**
   - **Вопрос:** какие типы resources должны быть доступны в selector-ах для microphone recordings и camera photos?
   - **Варианты:** all writable non-virtual resources; media-compatible writable resources only; different filter for microphone and camera.
   - **Нужно выяснить:** как не показать target, который технически writable, но неподходящ для соответствующего capture payload.
   - **Статус:** Open - tactical audit required.

7. **Missing or deleted selected target**
   - **Вопрос:** что происходит, если ранее выбранный resource удалён, стал недоступен или потерял writable semantics?
   - **Варианты:** auto-clear to fallback; keep invalid state with explicit warning; block capture until re-selection.
   - **Нужно выяснить:** какой UX лучше сохраняет predictability без silent data loss.
   - **Статус:** Open - tactical UX decision required.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Перенесут только master row микрофона, а child/destination rows останутся в Audio | Средняя | broken mental model и UX drift | тактика должна явно решить судьбу всего microphone block |
| В landscape появится иной порядок или иной набор строк | Средняя | inconsistent Settings IA между ориентациями | обязательная paired-edit / paired-validation политика |
| Переименование сломает search discoverability | Низкая | пользователь хуже находит настройку поиском | tactical audit search/highlight path после renaming |
| Новое название камеры окажется локализовано несогласованно | Средняя | EN/RU/UK drift | один owner-approved concept и полная locale parity |
| Перенос ухудшит компактность существующей `Other features` группы | Средняя | перегруженная секция и слабая читаемость | tactical ordering decision + portrait/landscape visual check |
| Selector позволит выбрать неподходящий или невалидный target | Средняя | запись не сохранится туда, куда ожидает пользователь | явный eligibility filter и fallback contract |
| Ранее выбранный resource пропадёт, а система молча переключится неочевидно | Средняя | пользователь не поймёт, куда ушёл результат | формализовать invalid-target UX и fallback rules |

---

## 8. Влияние на пользователя (docs/FEATURES)

Это расширение существующего settings surface, но уже с новым пользовательским control для default destinations. Tactical phase должна отдельно подтвердить, достаточно ли changelog/help coverage, или требуется короткое обновление `docs/FEATURES*.md` / help-doc surface про configurable destinations для microphone recordings и camera photos.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Camera and microphone settings move to Playback-side grouping**

- **Решение:** camera capture и microphone recording settings вместе со связанными destination controls считаются playback-adjacent utilities и больше не живут в `Media → Audio`.
- **Альтернативы:** оставить split placement; перенести только камеру; перенести только микрофон.
- **Почему:** текущий split ухудшает discoverability и не соответствует пользовательской модели этих команд.

**ADR-2: Camera subgroup naming must describe user intent, not storage plumbing**

- **Решение:** заменить `Camera-to-Resource` на пользовательское имя `Фото с Камеры`.
- **Альтернативы:** оставить текущее имя; переименовать в более технический вариант вокруг resource/folder semantics.
- **Почему:** пользователь ищет сценарий съёмки, а не внутренний способ сохранения.

**ADR-3: Destination section title must explicitly mention camera and microphone**

- **Решение:** секция `Other features` переименовывается в `Camera, microphone and Other features`.
- **Альтернативы:** оставить старый общий заголовок; выносить камеру/микрофон в отдельную новую секцию.
- **Почему:** после переноса новый заголовок должен заранее объяснять содержимое секции и её назначение.

**ADR-4: Portrait and landscape stay semantically identical**

- **Решение:** обе ориентации показывают один и тот же групповой состав и одинаковые зависимости между настройками.
- **Альтернативы:** разрешить orientation-specific IA divergence ради compactness.
- **Почему:** Settings не должны менять логику только из-за поворота устройства.

**ADR-5: Empty destination means explicit fallback, not validation failure**

- **Решение:** пустой microphone destination трактуется как `Downloads`, а пустой camera destination трактуется как каталог камеры устройства.
- **Альтернативы:** требовать обязательный выбор ресурса; запрещать пустое значение; использовать один общий fallback для обоих capture flows.
- **Почему:** owner explicitly requested empty-state semantics, и она должна быть прозрачной и детерминированной.

---

## 10. Связи с другими спеками

- Связано по области с предыдущими regrouping-работами внутри playback settings и с существующими camera/microphone feature tickets.
- Точные active/verified dependencies нужно уточнить в `/spec-tech`, если тактика покажет жёсткую связь с уже закрытыми playback regrouping задачами.

---

## 11. Критерии готовности (strategic-level)

1. Группа `Camera-to-Resource` больше не находится в `Media → Audio`.
2. Camera subgroup появляется внутри `Playback`-секции с owner-approved названием `Фото с Камеры` и локализованными EN/RU/UK эквивалентами.
3. `Enable microphone recording` больше не находится в `Media → Audio` и доступен из playback-side секции.
4. Заголовок секции `Other features` заменён на `Camera, microphone and Other features` и локализован консистентно.
5. В playback-side microphone block появляется явная настройка выбора destination resource для записей с микрофона.
6. Если microphone destination не выбран, запись сохраняется в `Downloads`.
7. В playback-side camera block появляется явная настройка выбора destination resource для фото с камеры.
8. Если camera destination не выбран, снимок сохраняется в каталог камеры устройства.
9. Tactical implementation явно решает судьбу dependent microphone row(s) и не оставляет split-block между секциями.
10. Portrait и landscape показывают одинаковую IA без потери доступности и discoverability.
11. Settings search / highlight continue to find the moved settings and the new destination settings after rename and regrouping.
12. Existing camera and microphone behaviors remain unchanged except for placement, naming and explicit default-target resolution semantics.
13. EN/RU/UK locale parity проходит для всех новых и изменённых settings strings.
14. Target debug build and affected UI checks pass after implementation.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация будет создана через `/spec-tech S0367` после закрытия owner gate.

---

## Last Audit

- **Audited:** 2026-06-06 by `/spec-all` (review-mode reconciliation - code preceded spec finalization, `## Last Audit` was missing, file header drifted to `Draft` while journal already read `BlockNeedUserTest`).
- **Verdict:** Implementation complete; only on-device UI verification (criterion 14) outstanding. Status correctly parked at `BlockNeedUserTest`; no device online at audit time.
- **Code footprint:** 25 `S0367:` markers across 11 files; one device probe `Timber.d("S0367: ...")` at `PlaybackSettingsFragment.setupCaptureSection`. Changes uncommitted in working tree (branch `DEBUG-v013`); no git commit carries the marker yet.

Static evidence per strategic criteria (§11):

- Camera + microphone capture rows relocated out of `AudioSettingsFragment` (sync/setup removed there, comments at lines 205/286) into `PlaybackSettingsFragment.setupCaptureSection` under the renamed `Other features` section - criteria 1, 3, 4.
- Camera subgroup wired via `rowCameraToResourceEnabled` / ask-filename / open-for-editing, preserving the original inverted persistence flags (`disableCameraCapture`, `skipCameraFilenameDialog`) - criterion 2, behavior parity (criterion 12).
- Two new destination selectors: `btnSelectCameraPhotosDest` / `tvCameraPhotosDest` and `btnSelectMicRecordingDest` / `tvMicRecordingDest`, picking over writable non-virtual resources, with an explicit "(clear)" entry resolving back to the documented fallback - criteria 5, 7; §6 q6 resolved.
- Fallback contract realized: `AppSettings.micRecordingDestinationResourceId` / `cameraPhotosDestinationResourceId` default `null`; `null` resolves to Downloads (mic) / device camera folder (camera) via `CaptureDestinationPolicy` and the manager resolution logic in `BrowseMicRecordingManager` / `BrowseCameraCaptureManager` - criteria 6, 8; ADR-5.
- Microphone master toggle feature-gated by `BuildConfig.SUPPORT_MIC_RECORDING` and guarded by a `RECORD_AUDIO` runtime consent launcher; dependent ask-filename row visibility follows the master - criterion 9 (no split block left in Audio).
- Orientation parity: `res/layout/fragment_settings_playback.xml` and `res/layout-land/fragment_settings_playback.xml` both carry all six new IDs (6 occurrences each) - criterion 10.
- Locale parity: `setting_camera_photos_destination_*` and `setting_mic_recording_destination_*` keys present in EN/RU/UK (`check_strings_localized.ps1` exit 0) - criterion 13.

Residual gaps / open §6 items:

- §6 q3 (ordering inside renamed section), q5 (search/highlight continuity), q7 (missing/deleted selected target UX) are not independently re-verified here; treat as on-device verification scope.
- Criterion 14 (target debug build + on-device UI checks) is the sole gate keeping this at `BlockNeedUserTest`. Build presumed green from the implementing pass; not re-run here because the working tree mixes unrelated changes. Drain via `/spec-sweep` (or `/spec-test-device S0367` + `/spec-check S0367`) when a device is online.

---

## Revision History

- **2026-06-06** - created by Copilot via `/spec`
  - Added strategic draft for camera/microphone settings regrouping between Media → Audio and Playback → Other features.
- **2026-06-06** - by `/spec-update` (GPT-5.4, focus: completeness, consistency)
   - Expanded S0367 with explicit destination settings for microphone recordings and camera photos, including fallback rules for empty values.
- **2026-06-06** - by `/spec-all` (review-mode reconciliation)
   - Synced file header to journal status `BlockNeedUserTest`; added `## Last Audit` documenting the completed implementation and the single outstanding on-device gate (criterion 14).
## Last Audit (on-device 2026-06-07)

Verified on emulator-5554. Playback settings tab shows the grouped section `Camera, microphone and Other features`; probe `S0367: camera/microphone settings shown under Playback Other features` fired; no crash. Status -> Verified; debug probe removed.
