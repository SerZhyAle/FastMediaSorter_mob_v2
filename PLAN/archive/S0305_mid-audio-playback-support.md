# Стратегическая спецификация: S0305 - Поддержка воспроизведения MID/MIDI как аудио

**Ticket:** S0305
**Status:** Archived
**Implemented date:** 2026-05-30
**Priority:** 50
**Date:** 2026-05-30
**Tier:** 3 - Moderate
**Roadmap entry:** Ad-hoc - запрос 2026-05-30
**Tactical spec:** `PLAN/S0305_mid-audio-playback-support/` (будет создан через `/spec-tech`)
**Tactical plan:** `PLAN/S0305_mid-audio-playback-support/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec для исследования проблемы и подготовки поддержки воспроизведения.
- **Goal / expected outcome:** Provided by user - `.MID` файлы должны воспроизводиться вместе с прочими музыкальными файлами.
- **Local anchor:** Provided by user - проблема воспроизведения `.MID` файлов.
- **Scope boundaries / forbidden areas:** Provided by owner - первая реализация должна поддерживать local + remote/cloud MID/MIDI наравне с прочими музыкальными файлами. Для remote/cloud допускается существующая локальная pre-cache подготовка; отдельный direct MIDI streaming backend не требуется без нового решения владельца.
- **Done / success signal:** Provided by user - пользователь открывает `.MID` из музыкального списка и слышит воспроизведение без ошибки источника.
- **Autonomy rule:** Provided by owner - agent may decide with explicit conservative assumptions and must state those assumptions explicitly.
- **UI decisions / delegation:** N/A - новая экранная поверхность не требуется; существующие аудио-контролы должны использоваться без изменения размещения.

`Approved` is blocked while any mandatory line in this section still contains an unresolved owner-input marker.

---

## 1. Проблема

Приложение уже относит `.mid` и `.midi` к аудио и показывает такие файлы рядом с музыкой, но фактическое воспроизведение не гарантировано. В текущем сценарии Media3/ExoPlayer пытается открыть MIDI как обычный поток и получает `UnrecognizedInputFormatException`, потому что доступные extractors не умеют читать этот формат.

Для пользователя это выглядит как «файл виден как музыка, но не играет». Особенно заметно это в папках рингтонов и старых музыкальных коллекциях, где MID/MIDI часто лежит рядом с MP3, OGG и M4A.

---

## 2. Цели

1. `.mid` и `.midi` открываются из музыкальных списков как обычные аудиофайлы.
2. Локальное воспроизведение MID/MIDI не падает с ошибкой источника Media3.
3. Переходы next/previous, stop, pause и завершение трека работают предсказуемо в общей очереди аудио.
4. Если источник или устройство не поддерживает выбранный способ воспроизведения, пользователь получает понятную ошибку без зацикливания на битом треке.
5. Существующие форматы MP3, FLAC, AAC, OGG, M4A и другие не получают регрессию.

**Non-goals:**

- Редактирование MIDI, смена инструментов, караоке-режим или визуализация нот.
- Добавление отдельного MIDI-синтезатора с пользовательскими soundfont-настройками в первой итерации.
- Расширение поддержки на новые форматы вроде `.kar` или `.rmi` без отдельного решения владельца.
- Переработка всего аудиоплеера или очереди воспроизведения.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Поддержка должна ощущаться как часть обычного музыкального набора, а не как отдельный режим.
2. Приоритет - практичное воспроизведение `.MID`, а не полная MIDI-студия.
3. Диагностика должна отделять «файл распознан как музыка» от «формат реально декодируется выбранным движком».

### 3.2 Жёсткие ограничения

- **Flavor:** затрагиваются только варианты сборки, где доступен аудиоплеер; варианты без аудио не должны получать новую пользовательскую поверхность.
- **API level:** поддержка должна учитывать Android 8+ для основных вариантов и Android 6+ для legacy, если итоговый playback backend это позволяет.
- **Wear OS:** не затрагивается.
- **Производительность:** MIDI-файлы обычно малы, но выбранный backend не должен держать лишний foreground-service, wakelock или CPU-нагрузку после остановки.
- **Совместимость данных:** миграция пользовательских данных не ожидается; если понадобится хранить новое состояние, оно должно быть совместимо со старым поведением.
- **Локализация:** EN/RU/UK обязательны для новых ошибок, подсказок или документации после реализации.
- **Доступность:** новые сообщения об ошибке, если появятся, должны быть доступны TalkBack и не полагаться только на цвет.
- **Communication policy:** любые новые пользовательские строки должны пройти tone checklist из `docs/COMMUNICATION_POLICY.md` перед интеграцией.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0172, S0105 - related archived audio playback work; no active blockers.
- **Source scope:** local + remote/cloud MID/MIDI must be supported on par with other music files.
- **Remote strategy:** use existing local pre-cache preparation for remote/cloud sources; no direct MIDI streaming backend in the first implementation.
- **Format scope:** `.mid` and `.midi` only; `.kar`, `.rmi`, XMF and ringtone-family formats require a separate owner decision.
- **Autonomy rule:** agent may decide with explicit conservative assumptions.

---

## 4. Контекст текущей архитектуры

Аудиофайлы проходят через общий путь обнаружения типа, список медиа, экран плеера и сервис фонового воспроизведения. Сейчас MID/MIDI уже попадает в аудио-набор на уровне расширений и MIME, поэтому проблема находится не в видимости файла, а в выборе playback backend и обработке ошибок декодирования.

Текущий аудио-стек опирается на Media3/ExoPlayer для фонового воспроизведения и интеграции с notification controls. Базовый набор подключённых Media3 1.2.1 зависимостей не содержит MIDI extractor, поэтому `.mid` падает как неизвестный source format. При этом Android platform documented formats подтверждают поддержку MIDI Type 0/1, а Google Maven содержит `androidx.media3:media3-exoplayer-midi` версии 1.2.1, совместимой с текущим Media3 pin. Этот вариант сохраняет текущую модель сервиса лучше, чем перенос MIDI на отдельный системный backend.

---

## 5. Предлагаемый подход

Решение должно разделить три понятия: «файл относится к аудио», «файл поддержан текущим playback backend» и «файл может участвовать в очереди вместе с остальной музыкой». MID/MIDI должен оставаться аудио-элементом и идти через тот же аудиосервис, но сервис должен получить MIDI-capable Media3 backend вместо запуска через базовый extractor set.

### 5.1 Основные столпы / модули

#### Форматная политика

Единая политика должна явно описывать, какие аудиоформаты распознаются, какие реально воспроизводятся основным движком, а какие требуют отдельного отказа. MID/MIDI не должен попадать в Media3 configuration, где отсутствует MIDI extractor/renderer и запуск заранее обречён на `UnrecognizedInputFormatException`.

#### Playback routing

Маршрутизация запуска должна сохранять обычный audio-service path для MID/MIDI при наличии Media3 MIDI module. Системный `MediaPlayer` допустим только как fallback для локального foreground-сценария или для случая, где MIDI module недоступен, потому что отдельный backend сложнее связать с текущим `MediaSession` контрактом.

Для сетевых и облачных источников первая версия не требует прямого MIDI streaming. Достаточно существующей локальной подготовки: удалённый файл полностью кэшируется, затем воспроизводится как локальный URI через тот же MIDI-capable service backend.

#### Очередь и состояние

MID/MIDI должен корректно участвовать в next/previous, stop, pause, завершении трека, notification controls и сохранении позиции через общий `Player` контракт. Если fallback backend применяется в отдельном сценарии, он не должен притворяться полной заменой service playback: ограничения должны быть явными и не ломать соседние треки.

#### Ошибки и восстановление

Ошибка декодирования MID/MIDI должна быть отдельной от общих сетевых или файловых сбоев. После неудачной попытки плеер должен восстановить контролы, убрать неверное состояние «playing» и позволить перейти к следующему треку. Happy path после подключения MIDI backend не должен содержать `UnrecognizedInputFormatException`.

### 5.2 Потоки данных и событий

Пользователь выбирает музыкальный файл. Слой определения типа подтверждает, что это аудио. Перед запуском playback routing проверяет, нужен ли альтернативный MIDI-маршрут. Если маршрут доступен, файл запускается и отдаёт события состояния в общий аудио-интерфейс. Если маршрут недоступен, пользователь видит понятную ошибку, а очередь остаётся управляемой.

### 5.3 Точки расширяемости

- Добавление новых контейнеров, связанных с MIDI, должно происходить через форматную политику, а не через разрозненные проверки расширений.
- MIDI backend должен быть заменяемым, чтобы позже можно было перейти от Media3 MIDI extension к другому синтезатору без изменения пользовательского сценария.
- Ограничения по flavor и источникам должны быть выражены продуктово: поддержано, подготовлено локально или не поддержано с объяснением.

---

## 6. Открытые вопросы / Research items

1. **Поддержка системного MIDI backend**
   - **Вопрос:** достаточно ли системного воспроизведения MID/MIDI на целевых Android API и устройствах владельца.
   - **Результат исследования:** Android platform documented formats подтверждают системную поддержку MIDI Type 0/1, но системный `MediaPlayer` хуже соответствует текущему service/notification контракту. Media3 MIDI extension доступен в Google Maven для версии 1.2.1 и даёт более прямой путь к сохранению очереди, `MediaSession` и notification controls.
   - **Решение для тактики:** primary backend - Media3 MIDI extension с тем же Media3 version pin. Системный backend оставить как fallback candidate, а не как основной путь.
   - **Статус:** Resolved

2. **Удалённые источники**
   - **Вопрос:** должна ли первая версия играть MID/MIDI из SMB, FTP, SFTP и cloud так же, как локальные файлы.
   - **Результат исследования:** текущий audio-service маршрут уже готовит network/cloud audio через полное локальное кэширование перед передачей в service playback. Для MIDI это достаточно: после подготовки файл становится локальным URI для того же backend.
   - **Решение для тактики:** поддерживать local и уже существующие cached remote/cloud audio paths. Не добавлять прямой MIDI streaming в первой версии. Финальное product scope остаётся owner gate в §0.
   - **Статус:** Resolved

3. **Фоновое воспроизведение и notification controls**
   - **Вопрос:** должен ли MID/MIDI поддерживать тот же уровень фонового управления, что MP3/FLAC.
   - **Результат исследования:** текущие очередь, mini now playing, skip controls и notification controls завязаны на `Player`/`MediaSession` path. Отдельный `MediaPlayer` backend потребовал бы мост состояния и увеличил бы риск расхождения поведения.
   - **Решение для тактики:** требовать parity с обычным аудио через Media3 MIDI extension. Fallback с ограниченными контролами допустим только как явно задокументированный деградированный сценарий.
   - **Статус:** Resolved

4. **Форматный объём**
   - **Вопрос:** ограничиваемся `.mid` и `.midi` или добавляем родственные расширения.
   - **Результат исследования:** в проекте уже распознаются `.mid` и `.midi`; явных `.kar`/`.rmi` фикстур или требований не найдено. Android platform docs дополнительно упоминают `.xmf`, `.mxmf`, `.rtttl`, `.rtx`, `.ota`, `.imy`, но это расширение scope за пределы пользовательского запроса.
   - **Решение для тактики:** первая версия поддерживает только `.mid` и `.midi`. `.kar`, `.rmi` и ringtone/XMF семейство не добавлять без отдельного owner decision.
   - **Статус:** Resolved

5. **Тестовый набор**
   - **Вопрос:** какие MIDI-файлы считать эталонными для проверки.
   - **Результат исследования:** в репозитории нет постоянных MIDI fixture files. В пользовательском log evidence уже есть реальные файлы из `/storage/emulated/0/Ringtones/`: `AnnenPolka.mid`, `Canon.mid`, `HungarianDances.mid`, `RadetzkyMarch.mid`, `RumbleBee.mid`, `RussianDance.mid`, `TwinkleStar.mid`.
   - **Решение для тактики:** ручной device smoke использует этот набор. Автотестовый минимум должен включать `.mid`, `.midi`, короткий валидный файл и повреждённый файл для диагностики ошибки; добавление бинарных fixture files оформить отдельным тактическим шагом.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Media3 MIDI extension может иметь отличие поведения от обычных audio extractors | Средняя | MID/MIDI играет, но события завершения или seek отличаются от MP3/FLAC | Проверить service parity на локальном и cached remote файле |
| Версии Media3 modules могут разойтись | Низкая | Gradle conflict или runtime mismatch | Подключать `media3-exoplayer-midi` строго той же версии, что и остальные Media3 modules |
| Удалённые MID/MIDI требуют локальной подготовки | Высокая | Сетевые и cloud-файлы не стартуют напрямую | Решить scope первой версии в §6 до `/spec-tech` |
| Документация уже обещает MID/MIDI шире фактической поддержки | Средняя | Пользователь ожидает поддержку, которой нет | После реализации синхронизировать feature/docs или честно описать ограничения |
| Общий обработчик ошибок будет считать MIDI обычным source failure | Средняя | Плеер может показывать неверное состояние или пропускать треки слишком резко | Ввести отдельную диагностику для unsupported/extractor errors |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации добавить в аудиоплеер: MID/MIDI-файлы воспроизводятся из поддержанных музыкальных источников вместе с обычной аудиоочередью; ограничения по источникам указать явно, если они останутся.

---

## 9. Архитектурные решения (ADR)

**ADR-1: MID/MIDI остаётся аудио и использует Media3 MIDI extension как primary backend**

- **Решение:** подключить MIDI-capable Media3 path той же версии, что и текущий Media3 stack, и сохранить обычную audio-service модель.
- **Альтернативы:** убрать MID/MIDI из аудио-списков; оставить текущий Media3 set и показывать общую ошибку; сделать системный `MediaPlayer` основным backend; внедрить отдельный MIDI-синтезатор сразу.
- **Почему:** файл уже виден пользователю как музыка, а текущий сервисный путь даёт очередь, background playback и notification controls. Media3 MIDI extension закрывает root cause с меньшим архитектурным разрывом, чем отдельный backend.

**ADR-2: Remote/cloud MIDI поддерживается через существующую локальную подготовку**

- **Решение:** первая версия не строит прямой MIDI streaming path. Remote/cloud файл сначала готовится как локальный cache file, затем воспроизводится через общий MIDI-capable backend.
- **Альтернативы:** поддержать только локальные файлы; написать отдельный streaming route; запретить MIDI для удалённых источников.
- **Почему:** существующая модель audio pre-cache уже решает проблему seek/read semantics для service playback и не требует нового сетевого протокольного слоя.

**ADR-3: Форматный scope первой версии - `.mid` и `.midi`**

- **Решение:** не добавлять `.kar`, `.rmi`, XMF и ringtone-family расширения в S0305.
- **Альтернативы:** расширить весь Android MIDI/ringtone список сразу; добавить `.kar`/`.rmi` по аналогии с desktop players.
- **Почему:** текущий пользовательский запрос и кодовая классификация покрывают `.MID`/`.MIDI`; дополнительные контейнеры требуют отдельных реальных файлов, MIME/intent проверки и пользовательского подтверждения.

---

## 10. Связи с другими спеками

- Связано по области с S0172 - устойчивость аудиосервиса и сохранение позиции, статус Archived.
- Связано по области с S0105 - inline audio playback in Browse, статус Archived.
- Блокирующих активных спецификаций не найдено.

---

## 11. Критерии готовности (strategic-level)

1. Пользователь открывает локальный `.mid` из музыкального ресурса, слышит воспроизведение и не получает `Source error`.
2. Пользователь может перейти с MID/MIDI на следующий обычный аудиофайл и обратно без зависшего состояния плеера.
3. При неподдержанном источнике пользователь видит понятное сообщение, а очередь остаётся управляемой.
4. Логи тестового запуска не содержат `UnrecognizedInputFormatException` для MID/MIDI happy path.
5. Существующие аудиоформаты из текущего пользовательского списка продолжают открываться и играть.
6. Документация после реализации описывает MID/MIDI без обещаний шире фактической поддержки.

---

## 12. Ссылка на тактическую спецификацию

Тактический план создан: `PLAN/S0305_mid-audio-playback-support/INDEX.md`.

---

## Revision History

- **2026-05-30** - by `/spec-update` (`GitHub Copilot`, focus: completeness, consistency, verifiability)
   - Applied: 1. Proposed (DISCUSS): 0.
- **2026-05-30** - by `/spec-update` (`GitHub Copilot`, focus: owner-gate)
   - Applied: 1. Proposed (DISCUSS): 0.
- **2026-05-30** - by `/spec-tech` (`GitHub Copilot`)
   - Created tactical plan: 5 phases. Status moved to Tactical.

---

## Last Audit

**Date:** 2026-05-30
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 61 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 1

### Manual / on-device

- [ ] Device smoke: open a local `.mid` file and confirm audible playback through the regular audio controls.
- [ ] Device smoke: open a cached remote/cloud `.mid` file and confirm the same audio-service route.
- [ ] Optional logcat check: no `UnrecognizedInputFormatException` appears for a MID/MIDI happy path.

Focused S0305 unit tests, StandardDebug assembly, and the final static S0305 implementation audit passed after the single-file service playback MIME fallback was fixed. The broader `testStandardDebugUnitTest` suite still has unrelated pre-existing failures recorded in Phase 04 and was not used as the S0305 closure predicate.