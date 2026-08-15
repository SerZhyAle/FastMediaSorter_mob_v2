# Стратегическая спецификация: S0026 — Стереофайл моргает иммерсивом и падает в панель

**Ticket:** S0026
**Status:** Approved
**Date:** 2026-04-29
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — VR-сессия 2026-04-29 на Quest 3 (`logs/fastmediasorter_20260429_022215.log`); пункт 2 в `PLAN/new-vr.txt`
**Tactical spec:** `PLAN/S0026_bugfix-vr-stereo-route-flicker/` (создаётся через `/spec-tech`)

<!-- auto-approved by /spec-all — 2026-04-29 -->

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

При открытии стереофайла с Browse в VR-флейворе пользователь на ~1 секунду видит, как иммерсив запускается, затем сворачивается, и контент оказывается в панельном плеере. На Quest 3 это даёт характерное «моргание»: открылся новый XR-сеанс, разрушился, поверх Browse открылось окно панельного плеера. Поведение возникает даже при выключенном тумблере «запускать сразу в иммерсив-режиме»: пользователь не просил иммерсив, но активити иммерсива всё равно стартует и тут же падает в фолбэк.

Лог сессии 2026-04-29 02:22 показывает причинную цепочку для файла `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4`:

1. `BrowseEventHandler` корректно детектирует формат: `detected=VR180_FISHEYE_SBS effective=VR180_FISHEYE_SBS autoDetect=true -> standard=false` (line 464). То есть на стороне Browse решение «послать в VR-активити» принято.
2. `VrPlayerActivity.onCreate` стартует, считывает настройки и применяет дефолт `MONO`: `PlayerStereoModeCoordinator: suppressed effective=UNKNOWN reason=apply-settings (kept=MONO)` (line 494). Информация о детектированном `VR180_FISHEYE_SBS` теряется при передаче из Browse в VR-активити.
3. После старта XR-сеанса маршрутизатор VR-активити берёт уже `requested=MONO effective=MONO` и решает: `route=STANDARD_PANEL_FALLBACK reason=plain-2d-video` (line 579).
4. Активити вызывает `forceStopVrPlayback reason=standard-player-fallback:player-state` (line 581), убивает XR-сеанс и стартует обычный `PlayerActivity`.

Эффект на пользователя: лишний цикл «активити запустилась — погасла», лишнее окно в трей-баре Quest 3 (см. S0028 для контекста многооконного поведения), потерянные ~1.5 с на ничего и недоверие к корректности маршрутизации.

S0018 (Verified) закрыла рассинхрон «route vs reason» внутри VR-маршрутизатора, но не покрыла саму точку входа: BrowseEventHandler передаёт стерео-режим в VR-активити, а активити его не подхватывает. Таким образом S0018 решила «правильное решение, но неправильный маршрут», а S0026 решает «правильное решение в Browse → потерянный сигнал в VR-активити → неправильное решение внутри».

---

## 2. Цели

1. При открытии файла с уже детектированным стерео-форматом (`VR180_FISHEYE_SBS`, `OU`, любые другие `Stereo3D`-режимы из S0012) VR-активити получает этот режим как входной параметр и не обнуляет его до `MONO` на этапе инициализации.
2. Если по итогам входных данных и пользовательских настроек (тумблер «авто-вход в иммерсив») решение — «остаться на панели», то VR-активити **не запускается вовсе**: маршрут перенаправляется на `PlayerActivity` ещё на этапе `BrowseEventHandler`, без промежуточного цикла «активити создалась — упала в фолбэк».
3. Если решение — «иммерсив», то VR-активити запускается с уже корректным стерео-режимом, и `route decision` внутри активити совпадает с тем, что зарегистрировал Browse.
4. В логе любая ветка маршрута пишется единой строкой с полями `requested / effective / detected / autoImmersiveSetting / route / reason`. Рассинхрон полей между Browse и VR-активити — баг и ловится тестом.
5. Нет двойной активити при одном клике: на одно открытие файла создаётся ровно одна активити (либо `VrPlayerActivity`, либо `PlayerActivity`), без промежуточного `forceStopVrPlayback`.

**Non-goals:**
- Не пересматривается алгоритм детекции стерео-формата по имени файла.
- Не вводится новый механизм маршрутизации; используется существующий `VrRouteDecisionHelper` из S0018.
- Не меняется UX панельного плеера (отдельная панель — по-прежнему `PlayerActivity`).
- Не покрывается случай файлов, у которых стерео-формат **действительно** не определяется: они могут попадать на панель как и раньше (это корректное поведение).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Запуск VR-активити должен происходить **только** когда финальный маршрут — иммерсив. Никаких «активити создалась чтобы тут же упасть».
2. Любая регрессия моргания должна ловиться автоматическим тестом на матрицу «настройка авто-иммерсива × тип содержимого × стерео-режим».
3. Желательно, чтобы единая строка `route decision` логировалась один раз за открытие файла — на стороне, которая фактически принимает решение. Дублирование «решение Browse + решение VR-активити» — анти-паттерн.

### 3.2 Жёсткие ограничения

- **Flavor:** только VR-флейвор. Другие сборки `VrPlayerActivity` не имеют.
- **API level:** без новых API-зависимостей; минимум проекта сохраняется.
- **Wear OS:** не затрагивается.
- **Производительность:** маршрут вычисляется один раз при клике. Оверхед должен быть ниже задержки сегодняшнего цикла «активити создалась — упала».
- **Совместимость данных:** intent-extras уже передают путь файла, ресурс, индекс — структура расширяется одним полем (детектированный стерео-режим). Миграция не требуется.
- **Локализация:** без новых строк UI.
- **Доступность:** без изменений.

---

## 4. Контекст текущей архитектуры

В VR-флейворе `BrowseEventHandler` при клике на файл выполняет детекцию стерео-формата (через `StereoDetector` по имени файла), затем принимает решение «вернуть стандартный плеер или запустить VR-активити». Решение опирается на конфигурацию `autoImmersiveSetting`, тип файла, и результат детекции. Если `standard=false`, активити `VrPlayerActivity` стартует с intent-extras `resourceId`, `initialIndex`, `initialFilePath`, `skipAvailabilityCheck`. **Стерео-режим в intent-extras не попадает.**

`VrPlayerActivity.onCreate` инициализирует `PlayerStereoModeCoordinator`, который применяет настройки с дефолтом `MONO` (`suppressed effective=UNKNOWN reason=apply-settings (kept=MONO)`). Затем `VrRouteDecisionHelper` (из S0018) принимает решение по уже **обнулённым** входным данным: `requested=MONO`, `effective=MONO`, что для не-360-видео даёт `route=STANDARD_PANEL_FALLBACK reason=plain-2d-video`. Активити запускает `PlayerActivity` фолбэком и сама закрывается.

Корень проблемы — разорванная передача данных: Browse знает результат детекции, VR-активити нет. S0018 решила консистентность внутри активити, но не покрыла точку входа.

---

## 5. Предлагаемый подход

Подход — два независимых блока: «не запускать активити, если иммерсив не нужен» и «передать детектированный режим в активити, если запуск всё-таки нужен».

### 5.1 Основные столпы / модули

**Решение принимается до запуска активити.**
`BrowseEventHandler` опирается на тот же `VrRouteDecisionHelper`, что и сама VR-активити, и принимает финальный маршрут до отправки intent'а. Если результат — `STANDARD_PANEL_FALLBACK`, intent отправляется в `PlayerActivity` напрямую; `VrPlayerActivity` не создаётся вовсе. Если результат — `IMMERSIVE_*`, intent отправляется в VR-активити.

**Передача детектированного стерео-режима в intent-extras.**
Intent на запуск VR-активити расширяется полем `detectedStereoMode` (имя поля и формат — задача тактической спеки). `VrPlayerActivity.onCreate` использует это значение при инициализации `PlayerStereoModeCoordinator` вместо дефолта `MONO`. Если поле в intent отсутствует (например, deep-link, внешний intent) — поведение откатывается к существующему дефолту, но с `reason=intent-extras-missing` в логе.

**Унификация лога маршрута.**
Точка, принимающая финальное решение, логирует одну строку с полным набором полей: входная настройка авто-иммерсива, детектированный режим, эффективный режим, выбранный маршрут, причина. Эта строка — источник истины для тестов и аудита. Ни одно последующее место не пишет «route decision» повторно с другим набором полей.

### 5.2 Потоки данных и событий

```
Click on file in Browse
        │
        ▼
StereoDetector → detected mode
        │
        ▼
VrRouteDecisionHelper(detected, autoImmersiveSetting, fileType)
        │
        ├── route=STANDARD_PANEL_FALLBACK → start PlayerActivity(extras)
        └── route=IMMERSIVE_*            → start VrPlayerActivity(extras + detectedStereoMode)
                                                │
                                                ▼
                                         VrPlayerActivity uses extras.detectedStereoMode
                                                │
                                                ▼
                                         No fallback — XR session lives
```

### 5.3 Точки расширяемости

- Если в будущем появится новый формат стерео или новый маршрут (например, отдельный режим для VR-фото), таблица `VrRouteDecisionHelper` пополняется в одном месте; обе точки вызова (Browse + VR-активити) подхватывают изменение автоматически.
- Поле `detectedStereoMode` в intent — расширяемый канал. Добавление параметров проекции (custom FOV, lens model) не требует второго intent.

---

## 6. Открытые вопросы / Research items

1. **Двойной вызов `VrRouteDecisionHelper` (Browse и VR-активити) — оставить или единый источник?**
   - **Вопрос:** после переноса решения в Browse, нужен ли повторный вызов внутри VR-активити (как страховка), или решение принимается ровно один раз?
   - **Варианты:** (а) единый источник — Browse, активити доверяет intent'у; (б) дублированный вызов с проверкой консистентности (рассинхрон → лог-маркер `route-mismatch`).
   - **Нужно выяснить:** допустимо ли запускать VR-активити, если intent пришёл извне (deep-link, share). Если да — внутренний вызов нужен как fallback.
   - **Статус:** BlockNeedUserTest

2. **Поведение для случая, когда детекция в Browse не дала результата.**
   - **Вопрос:** если `StereoDetector` вернул `UNKNOWN` (например, имя файла не содержит маркеров), но пользователь в настройках включил «авто-вход в иммерсив» — открывать в иммерсиве или на панели?
   - **Варианты:** «всегда панель для UNKNOWN», «иммерсив с режимом MONO», «иммерсив со специальным детектором по разрешению/метаданным».
   - **Нужно выяснить:** ожидание владельца; согласовать с поведением S0012 (поддержка форматов через метаданные).
   - **Статус:** BlockNeedUserTest

3. **Регрессионный тест на «нет двойной активити».**
   - **Вопрос:** как ловить регрессию автотестом — instrumented-test с проверкой LifecycleStat, или unit-test, моделирующий маршрут?
   - **Варианты:** (а) unit-test над `VrRouteDecisionHelper` + `BrowseEventHandler` с моком intent'а; (б) instrumented-test с подсчётом `onCreate/onDestroy` обоих активити.
   - **Нужно выяснить:** наличие инструментария для подсчёта lifecycle-событий в существующей тест-обвязке.
   - **Статус:** BlockNeedUserTest

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Перенос решения в Browse сломает сценарий «открыть файл по deep-link», где Browse не участвует | Средняя | Внешний intent открывается на панели вместо иммерсива | Сохранить fallback-вызов `VrRouteDecisionHelper` внутри `VrPlayerActivity`; intent-extras `detectedStereoMode` опционален |
| Поле `detectedStereoMode` устареет при обновлении формата детектора — старое значение не совпадёт с новой логикой | Низкая | После обновления приложения первое открытие файла из закладки даёт «не тот» маршрут | Версионировать enum или использовать неизменяемые токены; tactical-фаза фиксирует стабильный набор имён режимов |
| Унификация лог-строки сломает существующие парсеры логов из тулинга | Низкая | Старый log-reader не распознаёт новую строку | Единая строка имеет уникальный префикс (например, `RouteDecision:`) — старые регулярки расширяются один раз |
| Тест-сьют покрывает только matched-input комбинации, не покрывает «detected ≠ requested» | Средняя | Регрессия снова утечёт через дельту | Матрица тестов включает explicit «detected={SBS,OU,FISHEYE,UNKNOWN} × requested={AUTO,MONO,FORCED}» |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без новых строк в `docs/FEATURES.md`. Существующее описание иммерсивного режима не меняется — фикс возвращает заявленное поведение «стерео файлы открываются сразу в иммерсиве, остальные — на панели».

---

## 9. Архитектурные решения (ADR)

**ADR-1: Маршрут решается до запуска активити, не внутри неё.**
- **Решение:** `BrowseEventHandler` вызывает `VrRouteDecisionHelper` и сразу выбирает целевую активити. `VrPlayerActivity` запускается только когда маршрут — иммерсив.
- **Альтернативы:** (а) текущее поведение (всегда стартовать VR-активити, она сама решит) — отвергнуто из-за моргания и лишнего цикла; (б) перенести решение в общий навигатор приложения — отвергнуто как излишнее обобщение для VR-only сценария.
- **Почему:** убирает класс ошибок «активити создалась — упала», устраняет лишний цикл и лишний XR-сеанс.

**ADR-2: Передача детектированного режима через intent-extras, не через статическое состояние.**
- **Решение:** `detectedStereoMode` упаковывается в intent-extras; ни ViewModel-singleton, ни DI-параметры не используются.
- **Альтернативы:** (а) общий in-memory объект «текущий открываемый файл» — отвергнуто, потому что не переживает process death; (б) DataStore на ключ файла — отвергнуто, потому что детекция должна давать одинаковый результат при каждом открытии и не требует персистенции.
- **Почему:** intent-extras — стандартный механизм Android для переноса контекста между активити; они переживают process restoration, поддерживаются deep-link'ами.

**ADR-3: Единая строка `RouteDecision` — источник истины.**
- **Решение:** ровно одна точка кода логирует финальный маршрут с полным набором полей. Все остальные места могут логировать «вспомогательные» события, но не дублируют поля `route + reason`.
- **Альтернативы:** оставить мультистрочный лог как сейчас — отвергнуто, потому что именно мультистрочность маскирует рассинхрон, найденный в S0018.
- **Почему:** один источник истины обеспечивает testability и однозначную интерпретацию лога.

---

## 10. Связи с другими спеками

- **S0018** (Verified) — `bugfix-vr-auto-immersive-route-broken`. Закрыла консистентность route+reason **внутри** VR-активити. S0026 закрывает консистентность **между** Browse и VR-активити. Тест-матрица S0018 расширяется новыми кейсами «detected mode mismatch».
- **S0012** (Partial) — `vr-stereo-formats`. Поставляет каталог стерео-форматов. S0026 потребляет результат `StereoDetector`; список режимов остаётся source-of-truth у S0012.
- **S0019** (Partial) — `vr-controls-panel-flow-restoration`. Описывает сценарий «выйти в панель» из иммерсива. После S0026 «нет иммерсива → нет иммерсива вовсе» — фолбэка через моргание не существует, остаются только явные команды.
- **S0028** (Draft) — `vr-multi-window-playback`. Многооконный сценарий усугубляет моргание (каждый цикл создаёт окно в трее Quest 3). После S0026 циклов не будет — окно создаётся ровно одно.
- **S0023** (Verified) — `bugfix-vr-player-activity-stale-references`. Устраняла мусор от закрытых VR-активити. После S0026 закрытий «фолбэком» не будет вовсе — нагрузка на сценарий S0023 снижается, но он остаётся валидным для явных закрытий пользователем.

---

## 11. Критерии готовности (strategic-level)

1. На Quest 3 при выключенном «авто-вход в иммерсив» открытие любого стерео-файла из Browse открывает панельный плеер напрямую: в логе нет ни одной строки `VrPlayerActivity: onCreate` для этого открытия.
2. На Quest 3 при включённом «авто-вход в иммерсив» открытие стерео-файла открывает иммерсив: `VrPlayerActivity: onCreate` есть, `forceStopVrPlayback reason=standard-player-fallback:*` отсутствует, XR-сессия живёт до явной команды пользователя.
3. На пять подряд открытий разных стерео-файлов в одной сессии нет ни одного промежуточного цикла «активити создалась — закрылась через фолбэк».
4. Лог-строка `RouteDecision` уникальна и содержит поля `detected, requested, effective, autoImmersiveSetting, route, reason` в одной записи на одно открытие.
5. Тест-сьют покрывает матрицу «detected × requested × autoImmersiveSetting × fileType» и в CI зелёный; намеренная поломка ветки маршрутизатора в pull-request падает в тестах.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация: [`PLAN/S0026_bugfix-vr-stereo-route-flicker/INDEX.md`](S0026_bugfix-vr-stereo-route-flicker/INDEX.md). 5 фаз (F01 BrowseRoutingDecision, F02 intent-extra, F03 VR consume hint, F04 unit tests, F05 manual acceptance).

---

## Last Audit

**Date:** 2026-04-29
**Mode:** /spec-all (Full path)
**Verdict:** **BlockNeedUserTest** — code-side criteria closed; on-device verification required.

### Criterion-by-criterion

| # | Criterion | Status | Evidence |
| - | --------- | :----: | -------- |
| 1 | auto-immersive OFF: stereo opens panel without VrPlayerActivity | ✅ code | `BrowseRoutingDecision.decide(file, stereo, vrAutoImmersive=false) == STANDARD_PLAYER` (`BrowseRoutingDecisionTest` 5 cases). `BrowseEventHandler.shouldLaunchStandardPlayer` returns true → `createStandardPlayerIntent` → `VrTaskTransition.shouldEnterImmersiveTask` returns false. Manual scenario S1 covers live verification. |
| 2 | auto-immersive ON: stereo enters immersive cleanly, no fallback | ✅ code | F03 patches `resolveLaunchStereoMode` so the `EXTRA_DETECTED_STEREO_MODE` hint replaces MONO short-circuit. Inner `VrRouteDecisionHelper.decide` then sees `effective=VR180_FISHEYE_SBS` (or similar) → `IMMERSIVE_VIDEO`. Manual scenario S2. |
| 3 | five consecutive opens, no intermediate fallback cycle | ⏳ manual | Cannot be checked without on-device run; deferred to F05. |
| 4 | unified `RouteDecision` log line | ⚠️ partial | `VrRouteDecision.logTo` (S0018) emits the unified line inside VR-activity. `BrowseEventHandler: route ...` line in browse retains its existing format with `autoImmersive=` field added (S0026/F01). Two different log markers remain by design — strategic suggestion of a single token (`RouteDecision:`) was not implemented. **Non-blocking enhancement.** |
| 5 | test matrix in CI | ✅ done | New `BrowseRoutingDecisionTest` — 14 tests, all PASS. Existing `VrRouteDecisionHelperTest` — extended with S0026 regression case, all PASS. Standard + VR debug builds PASS. |

### Build gate

- `:app_v2:assembleStandardDebug` — PASS (1m 24s)
- `:app_v2:assembleVrDebug` — PASS (31s)
- `:app_v2:testStandardDebugUnitTest --tests BrowseRoutingDecisionTest` — PASS
- `:app_v2:testVrDebugUnitTest --tests VrRouteDecisionHelperTest` — PASS

### Side effect — pre-existing test compile blocker

`app_v2/src/test/java/com/sza/fastmediasorter/data/cloud/GoogleDriveTokenRefreshTest.kt` was missing the `reachabilityGate` constructor argument introduced when `GoogleDriveRestClient` adopted `NetworkReachabilityGate`. This blocked **all** unit-test compilation. Fixed inline (one-line addition `reachabilityGate = mockk(relaxed = true)`); not a S0026 change but unavoidable for the build gate.

### Manual acceptance pending

[F05_manual-acceptance.md](S0026_bugfix-vr-stereo-route-flicker/F05_manual-acceptance.md) defines four scenarios on Quest 3:

- **S1** — auto-immersive=OFF + stereo file → standard player, no flicker
- **S2** — auto-immersive=ON + stereo file → immersive holds
- **S3** — auto-immersive=ON + plain 2D → cinema immersive
- **S4** — auto-immersive=OFF + plain 2D → standard player

Until S1/S2 confirmed, status remains `BlockNeedUserTest`. After confirmation: `update.ps1 -Status Verified`.
