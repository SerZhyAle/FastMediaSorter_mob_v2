# Стратегическая спецификация: S1024 - Файлы медиа-захвата без расширения

**Ticket:** S1024
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-13
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-13
**Tactical spec:** inline (compact) - см. раздел «Фазы реализации». Аудит: `PLAN/S1024_media-capture-missing-extension/research/01__capture-filename-audit.md`

<!-- auto-approved by /spec-all - 2026-07-15 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-13

**Текст:**

При создании скриншот - редакция файлы изображений полчаются без расширения. Провести ещё раз адит чтобы все файлы скриншотов, кадров, адиозаписей, видеозаписей, фотокамеры и прочее - все генерировались с правильной маской имени и расширения

---

## 1. Проблема

При сценарии «скриншот → редакция → сохранить» итоговый файл изображения сохраняется без расширения. Проведён сплошной аудит всех точек записи медиа (см. артефакт). Все места **происхождения** захвата (скриншоты, камера, аудио, видео, кадры, кроп, PDF) именуют файлы корректно. Дефект - ниже по потоку, в naming-коде Draw-редактора «Save as..», который проверяет наличие расширения через `'.' in name`: имя с хвостовой точкой (`foo.`) проходит проверку, расширение не дописывается, файл (в т.ч. запись `MediaStore.DISPLAY_NAME`) остаётся без расширения.

---

## 2. Цели

1. Ни один путь сохранения из Draw-редактора не может создать файл, чьё имя оканчивается голой точкой или не имеет расширения.
2. Пустое/сорванное расширение (источник без расширения, либо пользователь стёр суффикс при переименовании) заменяется корректным по формату кодирования (jpg для JPEG, png для PNG).
3. Один и тот же анти-паттерн устранён во всех выявленных местах (UI-потребители + два dormant-места в domain), чтобы «все файлы генерировались с правильной маской и расширением».
4. Исправление детерминированно покрыто юнит-тестами.

**Non-goals:**

- Переименование или миграция уже сохранённых файлов без расширения.
- Изменение мест происхождения захвата (они корректны - не трогаем).
- Новый общий util-слой для capture-имён (не требуется этим тикетом).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Полный аудит - подтвердить корректность всех семейств захвата, не только чинить один симптом.

### 3.2 Жёсткие ограничения

- **Flavor:** все - дефектный код в `src/main`, достижим каждым flavor с Draw-редактором.
- **API level:** без API-специфики (баг воспроизводится одинаково на legacy/scoped-storage путях).
- **Wear OS:** не затрагивается.
- **Производительность:** без изменений.
- **Совместимость данных:** только новые сохранения; существующие файлы не трогаются.
- **Локализация:** не затрагивается - новых строк нет.
- **Доступность:** не затрагивается.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **Область фикса:** все места записи Draw-редактора (баг не в коде захвата, а в «Save as..» naming).
- **Оба триггера:** чинить и пустой ext в prefill, и потерю суффикса при пользовательском переименовании.
- **Данные:** уже сохранённые файлы не переименовываются; правило действует на новые сохранения.

---

## 4. Контекст текущей архитектуры

Скриншот именуется корректно (`screenshot_<ts>.png`) и пишется через общий `MediaStoreLocalDestinationWriter`. Если жест настроен на «открыть в редакторе», файл передаётся в Draw-редактор. Кнопка `[Save]` перезаписывает источник in-place (имя не трогается - безопасно). Пункт `⋮ → Save as..` строит имя через общий `ImageEditorFileNamer.buildName`, а затем три потребителя (`ImageDrawOverlayManager`, `PlayerDrawingSaveHelper`, `StandaloneDrawSaveHelper`) независимо решают, дописать ли расширение, проверкой `'.' in name`. Эта проверка ошибочно считает `foo.` уже имеющим расширение. Те же грабли - в domain (`SaveDrawingUseCase.normalizeName`, `CreateDrawingUseCase.ensureJpegExtension`), пока dormant.

---

## 5. Предлагаемый подход

Ввести единственную чистую функцию `ImageEditorFileNamer.ensureExtension(name, fallbackExt)`, которая определяет наличие расширения по `substringAfterLast('.', "")` (а не по `contains('.')`) и дописывает `fallbackExt` (по умолчанию jpg) при отсутствии, срезая любую хвостовую точку. Все UI-потребители маршрутизируются через неё (единый пакет - без новых импортов). `buildName` дополнительно защищается `ext.ifBlank`. Domain-usecase'ы чинятся inline тем же корректным предикатом (слой не может зависеть от ui). Чистая логика покрывается юнит-тестами.

### 5.1 Основные столпы / модули

- `ImageEditorFileNamer` - единый билдер+гард имён редактора (UI).
- Domain-usecase'ы Draw - локальный inline-фикс того же предиката.

### 5.2 Потоки данных и событий

- Draw «Save as..» → построение имени (`buildName`) → гард расширения (`ensureExtension`) → запись (файл / `MediaStore.DISPLAY_NAME`).

### 5.3 Точки расширяемости

- `ensureExtension` - единая точка для любых будущих потребителей вместо дублирования `contains('.')`.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. Полный аудит: **Артефакт:** `PLAN/S1024_media-capture-missing-extension/research/01__capture-filename-audit.md`.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Регрессия в редких именах (скрытые файлы `.name`, множественные точки `a.b.gz`) | Низкая | Неверное расширение | Юнит-тесты на эти кейсы; `substringAfterLast` сохраняет реальное расширение |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - исправление дефекта (файлы редактора теперь всегда с расширением).

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (эталон `endsWith`/`substringAfterLast` уже в camera-менеджерах).

---

## 10. Связи с другими спеками

Связей нет. Побочная находка (мёртвая обвязка `inPlaceSaveCallback`) вынесена в отдельный `/spec-draft`.

---

## 11. Критерии готовности (strategic-level)

1. «Save as..» из Draw-редактора всегда даёт имя с расширением - даже если источник без расширения или суффикс стёрт вручную.
2. Расширение соответствует фактическому формату кодирования (jpg/png).
3. Все выявленные места анти-паттерна `contains('.')` устранены (UI + domain).
4. Юнит-тесты фиксируют кейсы: нет точки, хвостовая точка, валидное расширение, множественные точки.

---

## Фазы реализации (compact tactical)

### Phase 1 - Единый гард расширения + UI-потребители

1. `ui/player/helpers/ImageEditorFileNamer.kt`: добавить `fun ensureExtension(name, fallbackExt)` (детект наличия через `substringAfterLast('.', "")`, дефолт `jpg`, срез хвостовой точки); в `buildName` защитить `ext` через `ifBlank`; убрать неиспользуемый импорт Timber.
   - Verify: `ensureExtension("foo.", "png") == "foo.png"`, `ensureExtension("foo.png","jpg") == "foo.png"`; `buildName("b","",DRAW)` оканчивается на `.jpg`.
2. `ui/player/helpers/ImageDrawOverlayManager.kt` `handleSaveRequest`: `extNoDot` с фолбэком `jpg` (пустое расширение источника не даёт хвостовую точку в prefill).
   - Verify: prefill для источника без расширения оканчивается на `.jpg`.
3. `ui/player/helpers/PlayerDrawingSaveHelper.kt` `shareDrawingBytes` (`:442`) и `setupDrawOverlaySaveCallback` (`:583-585`): маршрутизировать через `ImageEditorFileNamer.ensureExtension`.
   - Verify: `filename` с хвостовой точкой получает корректное расширение.
4. `ui/player/standalone/StandaloneDrawSaveHelper.kt` `save()` (`:123-124`): `finalName = ImageEditorFileNamer.ensureExtension(chosen, fallbackExt)`.
   - Verify: `MediaStore.DISPLAY_NAME` не оканчивается голой точкой.

### Phase 2 - Domain inline-фикс (dormant, тот же корень)

5. `domain/usecase/SaveDrawingUseCase.kt` `normalizeName`: заменить `'.' in trimmed` на проверку непустого `substringAfterLast('.', "")`.
   - Verify: `normalizeName("foo.", "x.jpg")` даёт `foo.jpg`.
6. `domain/usecase/CreateDrawingUseCase.kt` `ensureJpegExtension`: аналогично.
   - Verify: `ensureJpegExtension("foo.")` даёт `foo.jpg`.

### Phase 3 - Тесты + сборка

7. Новый `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/ImageEditorFileNamerTest.kt` - кейсы `ensureExtension` (нет точки / хвостовая точка / валидное / множественные точки / пустой fallback) и гард `buildName`.
   - Verify: `gradlew testStandardDebugUnitTest --tests *ImageEditorFileNamerTest*` PASS.
8. Сборка `standard debug` (`a.ps1 dq`).
   - Verify: BUILD SUCCESSFUL.

---

## Last Audit

**Date:** 2026-07-15
**Mode:** strategic (compact - inline phases, no tactical INDEX)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Все критерии готовности выполнены:

- `ImageEditorFileNamer.ensureExtension(name, fallbackExt = "jpg")` объявлена; детект наличия через `substringAfterLast('.', "")`, срез хвостовой точки; `buildName` защищён `ext.ifBlank { DEFAULT_EXT }`; неиспользуемый импорт Timber удалён (§11.1/§2.1).
- Анти-паттерн `contains('.')` / `'.' in` устранён во всех шести точках записи (`ImageDrawOverlayManager`, `PlayerDrawingSaveHelper` ×2, `StandaloneDrawSaveHelper`, `SaveDrawingUseCase`, `CreateDrawingUseCase`); оставшиеся вхождения - только в поясняющих комментариях (§11.3/§2.3).
- Fallback расширения соответствует формату кодирования (jpg для JPEG, png для PNG) во всех UI-потребителях (§11.2/§2.2).
- `ImageEditorFileNamerTest` покрывает нет-точки / хвостовая-точка / валидное / множественные-точки / кастомный-fallback + гард `buildName`; `testStandardDebugUnitTest` PASS (§11.4/§2.4).
- src/main, flavor-all, Wear не затронут, новых строк нет, только новые сохранения (§3.2). §8 FEATURES «Без изменений» -> EXEMPT.
- Сборка `standard debug` BUILD SUCCESSFUL; detekt по затронутым файлам чист (project-wide FAIL - чужой WIP-долг, не этот тикет).

### Manual / on-device

- [ ] (опционально) На устройстве: скриншот -> редактор -> «Save as..» с источником без расширения -> файл сохраняется с расширением. Логика детерминированно покрыта юнит-тестами; on-device не обязателен для Verified.
