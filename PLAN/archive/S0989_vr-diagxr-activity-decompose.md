# Стратегическая спецификация: S0989 - Декомпозиция DiagnosticXrActivity (>1500 LOC)

**Ticket:** S0989
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-11
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено при чистке S0903 (2026-07-11)
**Tactical plan:** [`PLAN/S0989_vr-diagxr-activity-decompose/INDEX.md`](S0989_vr-diagxr-activity-decompose/INDEX.md)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11

**Захвачено во время:** S0903 (P2-чистка VR DiagnosticXrActivity)

**Текст:**

DiagnosticXrActivity.kt exceeds the 1500 LOC ceiling (~1577 LOC, Rule 2). It is a monolithic VR immersive Activity mixing lifecycle, render-thread orchestration, HUD canvas rendering, ExoPlayer wiring, buffer management, and exit/return handoff. Decompose into helpers/*Manager.kt (e.g. render-thread lifecycle, HUD canvas, playback, exit dispatch) without disturbing the tight EGL/OpenXR threading and native-session coupling. Flavor: src/vr (noLegal sideload family). Discovered during S0903 P2 cleanup. Dedup-checked: no existing ticket for DiagnosticXrActivity decomposition.

---

## 1. Проблема

`DiagnosticXrActivity` в src/vr превышает потолок 1500 LOC (Rule 2, ~1577 строк) и совмещает несколько ответственностей: жизненный цикл Activity, оркестрацию render-потока, отрисовку HUD-канваса, проводку ExoPlayer, управление reusable-буферами и обработку exit/return handoff. Монолит затрудняет ревью и точечные правки (см. кластер находок S0903). Область - flavor src/vr (noLegal sideload family).

---

## 2. Цели

1. `DiagnosticXrActivity` уходит под потолок 1500 LOC (сейчас ~1624) за счёт вынесения самодостаточных блоков в `ui/xr/helpers/*Manager.kt`.
2. Activity остаётся владельцем только жизненного цикла, оркестрации render-потока, surface-колбэков и проводки интерактивного слушателя HUD.
3. Декодирование текстур, генерация HUD-баннера, диспетчеризация exit/return, видеовоспроизведение и резолв stereo-конфигурации живут в отдельных, тестируемых по отдельности хелперах.
4. Поведение VR-сессии, порядок teardown (ExoPlayer -> native), threading и нативный контракт неизменны - проверяется on-Quest smoke.

**Non-goals:**

- Изменение поведения VR-сессии, EGL/OpenXR threading или нативного контракта - декомпозиция строго behaviour-preserving.
- Смена алгоритмов декодирования, budget-семплинга, парсинга stereo-раскладки или return-плейбука - только перенос без правки логики.
- Объединение с `ImmersiveBrowseActivity` (RESOURCE_BROWSE) - отдельный host, вне области.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Каждый хелпер - один связный столп ответственности, имя по паттерну `NounVerbManager` / `NounRenderer` / `NounController`.
2. Порядок извлечения от самого развязанного (return-диспетчер, HUD-баннер) к более связанному (декодер, playback), чтобы каждая фаза собиралась независимо.

### 3.2 Жёсткие ограничения

- **Flavor:** vr (noLegal sideload family) - только src/vr.
- **API level:** без API-специфики (наследует flavor).
- **Wear OS:** не затрагивается.
- **Производительность:** без регрессий в render-loop; извлечение не должно добавлять аллокаций на кадр.
- **Совместимость данных:** нет.
- **Локализация:** EN/RU/UK - без новых строк ожидается.
- **Доступность:** без изменений (внутренний рефактор).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0903 (источник находки), S0964, S0290 (VR HUD/render-loop история).
- **Flavor scope:** vr (noLegal sideload family) - изменения только в `app_v2/src/vr`; standard/lite/photos/legacy не затрагиваются, `BuildConfig`-гейтов не добавляется (flavor source set сам изолирует).
- **UI scope:** без изменений видимого UI - внутренний рефактор, ни строк, ни разметки, ни визуального поведения HUD/loading-overlay не меняется.
- **Data scope:** без изменений - нет Room/persist/schema.
- **API scope:** без изменений - нативный контракт `DiagnosticXrRuntime` и сигнатуры render-потока неизменны.

---

## 4. Контекст текущей архитектуры

`DiagnosticXrActivity` - host OpenXR-сессии в `src/vr`. Часть ответственностей уже вынесена в `core/xr/*` (runtime, asset-provider, input-exit-handler, launch-args) и в `ui/xr/helpers/*` (HudCanvasRenderer, HudInteractionDispatcher, HudPlaybackController, HudTrackController, SubtitleCueController, HudHapticBridge). Однако сам Activity по-прежнему держит внутри себя пять крупных самодостаточных блоков: декодирование изображений с budget-семплингом и переиспользуемыми direct-буферами, генерацию RGBA-байтов HUD-баннера через Canvas, конструирование ExoPlayer с listener'ом и teardown, резолв stereo-конфигурации из имени файла и весь плейбук exit/return (Home + PendingIntent, ACTIVITY_RESULT, построение return-intent).

Проблему из §1 нельзя закрыть точечно, потому что эти блоки переплетены общими полями Activity (`reusableDirectBuffer`, `reusableHudBuffer`, `reusablePanelHudBuffer`, `textureBytes/Width/Height`, `exoPlayer`, `mediaPlaylist`, `returnTarget`) и вызовами `runtime.*`. Вынесение требует аккуратного проброса зависимостей (runtime, contentResolver/resources, коллбэки навигации), не перенося вызовы между потоками.

---

## 5. Предлагаемый подход

Извлечь самодостаточные блоки в `ui/xr/helpers/*` как обычные (не-Hilt) классы, которым Activity передаёт нужные зависимости в конструкторе (runtime, лямбды доступа к `Context`/`ExoPlayer`, коллбэки). Activity остаётся тонким координатором: держит render-поток, surface-колбэки, lifecycle и проводку интерактивного слушателя. Каждый хелпер владеет своими переиспользуемыми буферами, снимая соответствующие поля с Activity.

### 5.1 Основные столпы / модули

1. **VrPanelReturnDispatcher** - весь exit/return-плейбук: `deliverReturnAndFinish`, ветвление ACTIVITY_RESULT / LEGACY_PANEL_RETURN, Home+PendingIntent, построение return-intent, идемпотентность `panelReturnDispatched`. Читает `returnTarget`/`launchInput`/`exoPlayer`-снимок, пишет через `startActivity`/`setResult`/`finish` (проброшены как коллбэки). Самый развязанный блок - извлекается первым.
2. **VrHudBannerRenderer** - генерация RGBA-байтов баннера (filename/error) через Canvas, метки projection/layout, владение `reusableHudBuffer`, вызовы `runtime.queueHud`. Чистый рендеринг без состояния плейлиста.
3. **VrTextureDecoder** - budget-семплинг (`pickSampleSizeForBudget`), pooled-декод bundled/файлов, копия bitmap->RGBA через `reusableDirectBuffer`, возврат в Glide-пул. Возвращает результат-структуру (bytes + width + height) на `Dispatchers.IO`; владеет direct-буфером.
4. **VrDiagnosticPlaybackController** - конструирование ExoPlayer с `PrefetchLoadControlFactory`, `Player.Listener` (ошибки, tracks, cues), teardown в правильном порядке, seed панели из snapshot. Не путать с существующим `HudPlaybackController` (обёртка транспортных кнопок).
5. **VrStereoConfigResolver** - `parseFilenameConfig` + маппинг `StereoMode -> RenderConfig`, метки; обёртка над общим `StereoDetector`. Держит enum'ы `ProjectionType`/`StereoLayout`/`RenderConfig`.

Дополнительно (если бюджет LOC требует) - **VrDiagnosticPlaylistSource** (`prepareLaunchMedia`, resolve file/content-uri, `scanMediaFiles`), но навигация (`navigateToNext/Prev`, `loadCurrentMediaItem`) остаётся в Activity как оркестрация.

### 5.2 Потоки данных и событий

- Launch: Activity -> `DiagnosticXrLaunchArgs.parse` -> (playlist source) -> **VrTextureDecoder** (IO) -> `textureBytes` -> старт render-потока.
- Slide change: navigation (Activity) -> **VrStereoConfigResolver** -> `runtime.setRenderConfig` -> **VrTextureDecoder**/**VrDiagnosticPlaybackController** -> **VrHudBannerRenderer**/panel HUD -> `runtime.queueFrame`/`queueHud`.
- Exit: back/native-exit/surfaceDestroyed -> render-thread exit -> Activity -> **VrPanelReturnDispatcher** -> Home/PendingIntent или ACTIVITY_RESULT.

### 5.3 Точки расширяемости

- Новые режимы доставки return (`VrLaunchDeliveryMode`) добавляются в **VrPanelReturnDispatcher** без касания Activity.
- Новые типы HUD-баннера - в **VrHudBannerRenderer**.
- Новые source-режимы плейлиста локализуются в playlist-source.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Нарушение EGL/OpenXR threading при извлечении | Средняя | Чёрный экран / краш VR-сессии | Извлекать без переноса вызовов между потоками; on-Quest smoke |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (helpers/*Manager.kt).

---

## 10. Связи с другими спеками

- S0903 - источник находки (P2-чистка того же файла).

---

## 11. Критерии готовности (strategic-level)

1. `DiagnosticXrActivity.kt` < 1500 LOC.
2. Извлечённые хелперы лежат в `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/`, каждый - один связный столп.
3. `standard debug` и `vr debug` собираются без ошибок.
4. Ни одной правки вне `src/vr`; никаких новых строк, разметки или `BuildConfig`-гейтов.
5. On-Quest smoke: cold launch (изображение и видео), навигация вперёд/назад, интерактивная панель (FILE_URI), exit->возврат к панели - поведение неотличимо от текущего.

<!-- auto-approved by /spec-all - 2026-07-21 -->
<!-- Complexity: Full (VR decomposition, >4 helper extractions across render/decode/playback/return seams) -->
