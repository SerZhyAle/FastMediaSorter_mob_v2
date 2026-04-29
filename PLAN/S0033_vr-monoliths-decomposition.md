# Стратегическая спецификация: S0033 — Декомпозиция VR-монолитов перед расширением ray-input/HUD

**Ticket:** S0033
**Status:** Tactical
**Date:** 2026-04-29
**Tier:** 3 — Moderate
**Roadmap entry:** Discovered by `/spec-all S0024` — Phase 02 ray-hud-intersection требует пред-обработки JNI-слоя и `VrPlayerActivity`, оба превышают жёсткий лимит CLAUDE.md rule 2 (1000 LOC).
**Tactical plan:** `PLAN/S0033_vr-monoliths-decomposition/INDEX.md`

<!-- discovered by /spec-all — 2026-04-29 -->

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Phase 02 спеки `S0024_vr-hud-ray-input` (Tactical, In Progress) предписывает:

- Step 02.2 — править `app_v2/src/vr/cpp/OpenXrNative.cpp` (добавить ray-vs-plane intersection и новый JNI callback).
- Step 02.3 — добавить поля и колбэк в `VrPlayerActivity.kt`.

Фактический размер файлов:

| Файл | LOC | Лимит CLAUDE.md rule 2 | Лимит фазы |
|------|----:|-----------------------:|-----------:|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | 3487 | 1000 | 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | 1956 | 1000 | — (правится напрямую) |

Phase 02 явно говорит: «If `OpenXrNative.cpp` exceeds 1000 lines pre-edit, refuse and split via Manager pattern first». До декомпозиции эти файлы любая правка дополнительно увеличивает разрыв с лимитом и нарушает правило проекта.

Дополнительно: монолитность `OpenXrNative.cpp` исторически копится (lifecycle XR-инстанса, swapchain, render-loop, ввод, hand-tracking, layer-композитор, JNI-обвязка — всё в одном файле); такая структура затрудняет любые дальнейшие VR-фичи (S0007 hand-tracking уже Partial из-за этой связности, S0019/S0024 натыкаются на тот же барьер).

---

## 2. Цели

1. `OpenXrNative.cpp` разбит на изолированные подсистемы по ответственности (lifecycle, swapchain/composition, render-loop, controllers/input, hand-tracking, layer/HUD-bridge, JNI-фасад). Главный файл остаётся как тонкий координатор, отвечает требованию ≤ 1000 LOC и читаем за один проход.
2. `VrPlayerActivity.kt` приведена к ≤ 1000 LOC переносом логики в `helpers/*Manager.kt` по существующему шаблону (см. CLAUDE.md rule 3). Никакой бизнес-логики в Activity не остаётся.
3. После декомпозиции компилируются оба билда: `assembleVrDebug` и `assembleStandardDebug`.
4. Ни одно поведение пользователя не меняется (рендер, контроллеры, hand-tracking, audio-routing, фотосфера, плеер). Декомпозиция чисто структурная.
5. После приземления — снять блокировку с `S0024` (статус `BlockByOtherTask` → `In Progress`) и продолжить ray-input работу.

**Non-goals:**

- Не вводятся новые user-facing фичи или JNI-API — только выделение существующего кода.
- Не меняется hand-tracking pose pipeline по существу (S0007 остаётся Partial по своим причинам, не блокируется этой спекой).
- Не пересобирается XR-runtime selection — он уже выбран и фиксирован.
- Не оптимизируется производительность — измеряется только отсутствие регресса.
- Не пересобирается gradle-флейвор `vr` (CMake-targets, ABI, etc.).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Декомпозиция cpp — по подсистемам OpenXR (как читается рантайм-цикл сверху вниз: instance → session → swapchain → frame → input → submit). Не по слоям абстракции (Manager/Repository/...) — это не Kotlin-код.
2. Декомпозиция Activity — следуя уже сложившемуся шаблону `vr/helpers/Vr*Manager.kt`, без выдумывания новых паттернов.
3. Использовать существующий механизм `// MARK: ===` (видно в коде) для маркеров — после декомпозиции каждая часть «вырастает» в отдельный файл, имена логично следуют существующим заголовкам.
4. Читаемость дороже компактности: предпочесть 7 файлов по 400 LOC одному файлу 2000+.

### 3.2 Жёсткие ограничения

- **Flavor:** правка только VR-флейвора. Standard/lite/photos/legacy не затрагиваются.
- **API level:** без новых API; `minSdk 26` сохраняется.
- **Wear OS:** не затрагивается.
- **Производительность:** ray-vs-plane / render-loop не должны просесть; оценить визуально на устройстве (Quest 3) после декомпозиции.
- **Совместимость данных:** не затрагивает.
- **Локализация:** не затрагивает.
- **Доступность:** не затрагивает.
- **Read-only zones:** `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/` не трогаются.

---

## 4. Контекст текущей архитектуры

`OpenXrNative.cpp` (3487 LOC) — единственная нативная единица VR-флейвора. Содержит:

- Утилиты логирования (`nativeLogBufferAppend`, `nativeLogEmit`).
- Перечисление `xrSessionStateName`, `xrEventTypeName` для диагностики.
- Глобальные структуры состояния XR (instance, system, session, spaces).
- Loaders/extensions detection (`XR_FB_*`, `XR_META_*`, `XR_KHR_*`).
- Swapchain creation/destruction, FBO ownership.
- Frame-loop: `xrWaitFrame` / `xrBeginFrame` / per-eye render через JNI callback / `xrEndFrame` + composition layers.
- Controllers: action sets, action spaces, aim-pose poll, button state, JNI callbacks.
- Hand-tracking: handTracker per hand, joints, aim-pose, pinch-events.
- HUD: composition layer (head-locked quad), swapchain image acquisition, Bitmap upload.
- Stereo video layer + photo-sphere fallback.
- JNI surface: `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_*` функции.

`VrPlayerActivity.kt` (1956 LOC) уже частично декомпозирована (см. `vr/helpers/`, `vr/ui/Vr*Manager.kt`), но всё ещё содержит логику жизненного цикла XR-сессии, диспетчеризации pose-событий, маршрутизации команд плеера, лайфсайкл-коллбэков OpenXR. Часть этой логики дублируется или близка к существующим Manager-классам.

---

## 5. Предлагаемый подход

Двухтрековая декомпозиция, треки независимы и могут идти параллельно, но обе должны завершиться до возобновления S0024 Phase 02.

### 5.1 Основные столпы / модули

**Трек A — Native-декомпозиция `OpenXrNative.cpp`.**

Семь логических подсистем (имена черновые — финализирует `/spec-tech`):

1. *Logging utilities* — буфер логов, JNI-bridge на Timber.
2. *Instance / system / session lifecycle* — создание/уничтожение, состояние сессии, обработка событий.
3. *Swapchain & composition* — color/depth swapchain, выбор формата, frame-buffers.
4. *Frame loop* — `xrWaitFrame` / `xrBeginFrame` / per-eye render dispatch / `xrEndFrame` + composition layers.
5. *Input / controllers* — action sets, aim-pose, кнопки, JNI callbacks.
6. *Hand-tracking* — joints, aim-pose, pinch и микрожесты.
7. *HUD layer & Bitmap upload* — head-locked quad, swapchain текстура, Bitmap → GL Texture.

Главный `OpenXrNative.cpp` остаётся как тонкий координатор + JNI-фасад, ≤ 1000 LOC. Подсистемы — отдельные `.cpp/.h` пары в той же папке. CMake-список обновляется явно (целевой граф простой; см. `app_v2/src/vr/cpp/CMakeLists.txt`).

**Трек B — Activity-декомпозиция `VrPlayerActivity.kt`.**

Существующие Manager-классы в `vr/helpers/` и `vr/ui/` — целевые получатели. Кандидаты на новые helper-классы (имена черновые — финализирует `/spec-tech`):

- `VrSessionLifecycleManager` — XR-сессия, события OpenXR, callbacks `onSessionStateChange`, suspend/resume.
- `VrPoseDispatchManager` — приём pose-событий из JNI, диспетчеризация подписчикам (controller ray, hand ray, в будущем — HUD ray из S0024).
- `VrPlayerCommandRouter` — маршрутизация трекинговых триггеров на команды плеера (часть логики уже в `VrControllerInputManager`, но финальный диспатч сейчас в Activity).

Activity после рефакторинга содержит только композицию: создание Manager-ов, проводку колбэков, делегирование Android-лайфсайклов. Логики ≤ 1000 LOC, в идеале ≤ 600.

### 5.2 Потоки данных и событий

Декомпозиция не меняет потоки. Каждый трек — изоморфное переименование владельцев (cpp: один файл → семь; kt: один файл → меньше один + N helpers). JNI-сигнатуры остаются прежними.

### 5.3 Точки расширяемости

После трека A добавить новый JNI callback (как требует S0024 Phase 02 Step 02.2 — `nativeOnHudPointerMove`) — это правка одного маленького `.cpp/.h` файла «HUD layer & Bitmap upload» + одного блока в кодинатор. Без рефакторинга такая правка тонет в 3500 LOC.

После трека B добавить поле и колбэк (как требует S0024 Phase 02 Step 02.3) — это правка одного нового или существующего `*Manager.kt`, не Activity.

---

## 6. Открытые вопросы / Research items

1. **CMake организация после трека A.**
   - **Вопрос:** оставить один CMake-target `openxr_native` со списком всех `.cpp` или раздробить на статическую библиотеку + фасад?
   - **Решение по умолчанию (best-practice):** один target — минимум изменения CMakeLists, никакой пользы от sub-libs при компактном проекте.
   - **Статус:** Resolved (default).

2. **VrPlayerActivity — какие именно блоки уезжают.**
   - **Вопрос:** уровень гранулярности (3 helper-а или 5)?
   - **Решение:** делегирует `/spec-tech S0033` — нужна ревизия текущей Activity по разделам (поиск группировок логики). Сейчас не блокер.
   - **Статус:** Open (для тактической фазы).

3. **Тестовое покрытие после декомпозиции.**
   - **Вопрос:** заводить ли unit-тесты на новые Manager-ы?
   - **Решение по умолчанию:** только если выделяется чистая Kotlin-логика без OpenXR-зависимостей. JNI-обёртки покрываются smoke-билдом + ручным запуском на Quest 3.
   - **Статус:** Resolved (default).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Регресс при декомпозиции (рендер не запускается, контроллеры не отвечают) | Средняя | Слом VR-сборки | Каждый PR сопровождается ручным smoke-тестом на Quest 3 (CLAUDE.md memory: VR testing is not a blocker — есть устройство) |
| Размытие ответственности между новыми файлами | Низкая | Деградация читаемости вместо улучшения | Тактическая фаза `/spec-tech` явно прорисовывает границы; review-gate перед merge |
| Race-conditions при реорганизации global state OpenXR | Низкая | Случайные крэши на cold-start | Сохранить существующую модель глобалов внутри одного `.cpp/.h` (не размазывать по файлам без mutex) |
| Скрытое дублирование логики Activity ↔ существующие Manager-ы | Средняя | Регресс поведения после рефакторинга | Пред-аудит существующих Manager-ов перед нарезкой |

---

## 8. Влияние на пользователя (docs/FEATURES)

Никакого. Чисто структурный рефакторинг. `docs/FEATURES.md` не обновляется.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Декомпозиция cpp по подсистемам OpenXR, не по абстрактным слоям.**

- **Решение:** разбиение следует естественной структуре OpenXR-приложения (lifecycle / swapchain / frame / input / hand / HUD), а не паттернам Kotlin-Clean (нет Repository / UseCase в нативном слое).
- **Альтернативы:** (а) разделение по «доменам» (типа `VrSession`, `VrInput` с собственными интерфейсами) — отвергнуто как over-engineering для ~3.5K LOC; (б) C++-классы вместо файлов — отвергнуто как избыточная абстракция, текущий код — функциональный, без классов.
- **Почему:** низкая стоимость миграции, сохраняется читаемость рантайм-цикла сверху вниз.

**ADR-2: Activity → Manager декомпозиция, не SOLID-расщепление.**

- **Решение:** следовать уже принятому проектному шаблону `helpers/*Manager.kt`. Не вводить интерфейсы под каждый Manager (никаких `interface VrSessionLifecycle`).
- **Альтернативы:** интерфейс-first архитектура — отвергнуто, не соответствует существующему коду.
- **Почему:** консистентность с проектом, минимум boilerplate, лёгкая навигация.

---

## 10. Связи с другими спеками

- **S0024** (In Progress, BlockByOtherTask после этого открытия) — главный потребитель. Phase 02 заблокирована до приземления S0033.
- **S0019** (Partial), **S0009** (Partial), **S0007** (Partial) — также упираются в монолитность `OpenXrNative.cpp` и `VrPlayerActivity.kt`. После S0033 эти спеки получат свободу для дальнейших фаз.
- **S0014** (vr-xr-cold-start) — измеряет cold-start performance VR; после S0033 точки инструментации станут стабильнее (меньше шанс merge-конфликтов).

---

## 11. Критерии готовности (strategic-level)

1. `OpenXrNative.cpp` ≤ 1000 LOC, остальные подсистемы — самостоятельные `.cpp/.h` пары, каждая ≤ 800 LOC.
2. `VrPlayerActivity.kt` ≤ 1000 LOC; новые helper-ы уложены в существующие подкаталоги `vr/helpers/` или `vr/ui/`.
3. `assembleVrDebug` и `assembleStandardDebug` собираются без ошибок и новых lint-warnings в затронутых файлах.
4. Smoke-тест на Quest 3: VR-плеер запускается, видео воспроизводится, контроллеры реагируют, hand-tracking работает (если включён в настройках), HUD рисуется. Регрессов нет.
5. После закрытия — `S0024` переключается обратно в `In Progress` через `update.ps1 -Status "In Progress"`, и `/spec-dev S0024` продолжает с Phase 02 Step 02.1.

---

## 12. Ссылка на тактическую спецификацию

Тактическая декомпозиция не создана автоматически (объём cpp-рефакторинга требует отдельного прохода с чтением всего файла). Следующий шаг — запустить `/spec-tech S0033`, который:

1. Прочитает `OpenXrNative.cpp` целиком, выделит границы подсистем (по существующим `// MARK:` или `===`), пропишет порядок миграции функций.
2. Прочитает `VrPlayerActivity.kt` целиком, найдёт логически связные блоки и подберёт подходящий Manager-класс.
3. Сформирует фазы (черновой план: Phase 01 cpp logging+lifecycle → Phase 02 cpp swapchain+frame → Phase 03 cpp input+hand → Phase 04 cpp HUD → Phase 05 Activity helpers → Phase 06 docs/catalog cleanup; уточнит `/spec-tech`).
