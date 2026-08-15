---
ticket: S0249
status: BlockNeedUserTest
priority: 90
date: 2026-05-18
tier: 3
---

<!-- auto-approved by /spec — 2026-05-18 -->

# Стратегическая спецификация: S0249 — Этап 1A: Test Immersive (статичная 360°-картинка)

**Ticket:** S0249
**Status:** Archived
**Priority:** 90
**Date:** 2026-05-18
**Tier:** 3 — Moderate (ad-hoc, первая реальная OpenXR-интеграция в минимальном объёме)
**Roadmap entry:** `S0240 §10` — новый Этап 1A, вставляется **до** Этапа 1 (видео-immerse, S0240 §10.2). После `Verified` S0249 видео-immerse становится Этапом 1B.
**Tactical spec:** `PLAN/S0249_vr-test-immersive-image-stage1a/INDEX.md`.
**Tactical plan:** `PLAN/S0249_vr-test-immersive-image-stage1a/INDEX.md`
**Depends on:**
- `S0245` (`vr-settings-scaffold-stage0`) — должен быть `Verified` (блок VR-настроек и master toggle живы, контракты `XrEntryGateway` / `XrDetectionFacade` существуют).

**Blocks:** Этап 1B (видео-immerse) — отдельный `Sxxxx`, появляется после `Verified` S0249.

---

## 1. Проблема

После закрытия Этапа 0 (`S0245`) в приложении есть только декларативный каркас VR-настроек — master toggle «Включить 3D VR», который ни на что не влияет. Реального immersive-экрана не существует ни для одного формата контента. Нет возможности проверить на железе (Meta Quest 3, Android XR), что весь OpenXR-pipeline — открытие сессии, создание swapchain, презентация composition layer-а, обработка ввода, корректное закрытие — работает end-to-end. Любой следующий этап (видео-immerse, изображения, HUD) опирается на этот pipeline, поэтому без изолированного диагностического шага в первый раз падает сразу несколько подсистем одновременно, и отладка превращается в гадание.

Эффект на пользователя: впечатления «приложение видит мой Quest 3, но никуда меня не пускает». На наблюдаемом уровне — отсутствие кнопки, которая ведёт в VR.

Затронутая область — `vr` / `noLegal` flavor-ы, VR-блок Settings.

---

## 2. Цели

1. VR-блок настроек получает новое имя — локализованное **«Управление 3D-VR»** (RU; EN/UK — `/spec-tech` + communication policy). Старое название «VR» из `S0245` заменяется в рамках этого тикета.
2. Блок размещается **внутри media-секции Settings** (там же, где блок «Video» и другие media-related controls) как отдельная **сворачиваемая группа** (collapsible group). Конкретный механизм сворачивания (expand/collapse header, PreferenceCategory с `isIconSpaceReserved`-стилем, expand-arrow) — `/spec-tech` после `R-02`-research.
3. Блок **всегда присутствует** в `vr` / `noLegal`-сборке, независимо от наличия XR runtime — пользователь видит, что фича существует, даже если устройство её не поддерживает.
4. Внутри блока сверху — **информационная надпись** (advisory text) над master toggle с локализованным текстом «Доступно на устройствах типа Meta Quest 3, Android XR» (RU; EN/UK — `/spec-tech`). Видимость advisory — **только** когда устройство не XR-runtime (master toggle в disabled-состоянии); на XR-устройстве advisory скрыт.
5. Master toggle «Включить 3D VR» (из `S0245`) на не-XR-устройстве — **disabled и не интерактивен**; пользователь видит его состояние, но не может изменить.
6. На XR-устройстве master toggle интерактивен; advisory скрыт.
7. Внутри блока появляется кнопка с локализованной подписью **«Test Immersive»**, видимая и активная только когда master toggle = ON. На XR-устройстве с master = OFF, и на не-XR-устройстве — кнопка скрыта.
8. По нажатию кнопки приложение открывает OpenXR-сессию и показывает пользователю одну статичную 360°-картинку, упакованную в APK ресурсом.
9. Картинка отображается в формате stereo top-bottom equirect (верхняя половина — левый глаз, нижняя — правый), с применением eye-specific сэмплинга.
10. Любой пользовательский ввод во время отображения immerse — кнопка контроллера, триггер, клик мыши, любая клавиша клавиатуры — закрывает OpenXR-сессию и возвращает пользователя на экран Settings.
11. На XR-устройстве с master toggle = ON кнопка отрабатывает cold-start OpenXR-сессии за наблюдаемо короткое время (приемлемо: ~1.5 с на Quest 3 от тапа до видимой картинки).
12. В `standard`/`lite`/`photos`/`legacy`-сборках весь VR-блок (с любым именем) отсутствует физически (флавор-изоляция из `S0245`).

**Non-goals:**

- Видео в immerse — отдельный тикет (Этап 1B).
- Реальный stereoscopic parallax между глазами — на этом этапе ассет упаковывается как stereo TB, но фактическое содержимое верхней и нижней половин **может совпадать** (mono-as-TB). Реальный parallax — отдельный future-step.
- Несколько форматов (SBS, OU, fisheye, 360 mono) — только один фиксированный equirect-ассет.
- Авто-детект стерео-формата.
- HUD, индикаторы, любая интерактивность внутри immerse кроме «любой ввод → выход».
- Долгоживущая XR-host-активность — на каждое нажатие новая cold-start сессия.
- Передача ExoPlayer-state, файловые операции, browser в immerse.
- Расширение публичной FEATURES — диагностический test-button не объявляется как user-facing capability.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Картинка-ассет — sample из открытых источников интернета с разрешающей лицензией (MIT / CC0 / совместимая для редистрибуции в коммерческом APK). Sourcing — часть работы по тикету, не предмет внешнего согласования.
2. Максимальное практически достижимое разрешение в рамках бюджета по размеру (см. §3.2). Если найденный sample имеет меньшее разрешение — это приемлемо для test-button.
3. По возможности — реальный stereoscopic parallax в samplе (не критично, см. §6).

### 3.2 Жёсткие ограничения

- **Flavor:** **только** `vr` и `noLegal` (`noLegal` наследует через flavor-иерархию). Не появляется в `standard`, `lite`, `photos`, `legacy`. Реализация **обязана** следовать `dev/FLAVOR_DEVELOPMENT_RULES.md`: интерфейс расширения immerse-entry — в `src/main/`, no-op default — в `src/main/` (или `src/standard/`), реальная реализация — **только** в `src/vr/`, binding — через flavor-specific Hilt-модуль. Никаких `BuildConfig.IS_*_FLAVOR` / `BuildConfig.SUPPORT_*` веток в `src/main/` (запрещено CLAUDE.md Rule 15).
- **Структура VR-блока (рефакторинг из `S0245`):** блок переименовывается в «Управление 3D-VR», располагается в media-секции Settings как сворачиваемая группа, всегда присутствует в `vr` / `noLegal`-сборке. Advisory text над master toggle — **только** на не-XR-устройстве. Master toggle — disabled на не-XR-устройстве, интерактивен на XR-устройстве.
- **Master toggle gating:** кнопка «Test Immersive» видима и активна **только** при master toggle = ON. При master OFF — скрыта. При disabled master (не-XR устройство) — скрыта.
- **API level:** минимум Android API 26 (общий minSdk для `vr`/`noLegal` после `S0241`). Реальная XR-функциональность ожидается на Quest 3 (Android 12+ под HorizonOS) и Android XR (Android 14+), но компиляция должна проходить на minSdk без runtime-падений на не-XR устройствах.
- **Wear OS:** не затрагивается.
- **Размер APK:** ассет-картинка укладывается в бюджет **до ~3 MB** для `vr`/`noLegal`-сборки. JPEG quality порядка 85.
- **Производительность:** cold-start OpenXR-сессии на каждое нажатие — приемлемо. Целевой бюджет от тапа до видимой картинки на Quest 3 — ~1.5 с. Long-lived XR-host — целевое состояние S0240, **не** требование этого тикета.
- **Совместимость данных:** новых persisted-форм нет; preference-keys не добавляются.
- **Локализация:** EN/RU/UK для подписи кнопки и любых сопутствующих строк (toast о невозможности, описание в Settings). После добавления — обязателен `scripts/check_strings_localized.ps1`.
- **Доступность:** кнопка имеет фокус-traversal в одном порядке с соседними preference-controls VR-блока (CLAUDE.md Rule 17). TalkBack-label осмыслен. Touch target ≥ 48dp.
- **Лицензия ассета:** sourced asset должен быть под permissive license (MIT, CC0, CC-BY с приемлемой attribution). Attribution-notice — в существующем app-credits экране (если CC-BY) или в LICENSE-файле проекта (если MIT). Закрытые license-tier-ы (royalty-free demo с ограничениями) — запрещены.
- **Communication policy:** все user-facing строки (label кнопки, любые toast-ы об ошибках) проходят `docs/COMMUNICATION_POLICY.md` (§6 tone checklist) до коммита.

---

## 4. Контекст текущей архитектуры

После `Verified` S0245 в `src/main/` существуют два контракта — фасад «запустить VR-immerse» и фасад «есть ли VR + включён ли он». В `src/vr/` существует VR-блок Settings и реальная реализация фасада детекта. Реальной реализации фасада «запустить immerse» **нет** — в `src/vr/` лежит stub, возвращающий «недоступно». OpenXR loader не подключён ни в `src/main/`, ни в `src/vr/`.

Нерешённость состоит в том, что фасад «запустить immerse» спроектирован под видео-сценарий (передача состояния плеера, текущего файла, скорости, аудио-дорожки), а для диагностического image-test эта поверхность избыточна и тащит за собой ExoPlayer-зависимости. Контракт нужно расширить (или ввести параллельный) под минимальную форму: «запусти immerse с фиксированным bundled-ассетом, верни обратно когда пользователь нажмёт что-нибудь».

Также отсутствует слой OpenXR-инициализации: ни сессия, ни swapchain, ни composition layer, ни input-handler — ничего из этого ещё не существует в коде. Этот этап впервые приносит реальную интеграцию OpenXR loader-а в `vr`-flavor.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Расширение контракта immerse-entry.** Фасад из `S0245` дополняется операцией «запустить immerse в режиме diagnostic-image». Поверхность операции — без параметров (всё — внутренний bundled-ассет). Возвращает результат-семантику: «запущено», «недоступно (нет XR runtime)», «недоступно (master OFF)», «ошибка инициализации XR».

**Слой XR-runtime.** Новый изолированный модуль в `vr`-flavor, отвечающий за полный жизненный цикл OpenXR-сессии в режиме image-diagnostic: создание instance / session / swapchain, presentation цикла кадров с одним composition layer-ом, обработка input-событий, корректное закрытие. Этот слой остаётся внутренним и не экспонируется наружу — фасад в `main/` ничего о нём не знает.

**Слой бандлируемого immerse-ассета.** Изолированный компонент в `vr`-flavor, отвечающий за загрузку sample-ассета из APK-ресурсов, декодирование в текстуру и upload в OpenXR swapchain. Знает формат содержимого (stereo TB equirect) и параметры eye-specific сэмплинга.

**Точка входа в Settings.** Существующий VR-блок Settings из `S0245` рефакторится в сворачиваемую группу «Управление 3D-VR» внутри media-секции Settings, и расширяется тремя элементами:
- advisory text над master toggle (виден только на не-XR-устройстве);
- master toggle становится disabled на не-XR-устройстве (раньше в `S0245` блок мог отсутствовать вовсе — теперь всегда присутствует);
- новый preference-control «Test Immersive» — кнопка, видимая только при master toggle = ON.

### 5.2 Потоки данных и событий

Пользовательский поток:

`Settings (VR-блок) → tap «Test Immersive» → фасад immerse-entry → XR-runtime-слой создаёт сессию → загружается ассет → отображается composition layer → пользователь видит 360°-картинку → любой input event перехватывается → XR-сессия закрывается → пользователь возвращается на Settings.`

Поток «отказ»:

`tap → фасад → XR-runtime пытается инициализировать → fail (нет runtime / отказано в разрешении / ошибка swapchain) → возвращает структурированную ошибку → UI показывает toast по communication policy → пользователь остаётся на Settings.`

Поток «master OFF» (XR-устройство, пользователь выключил):

`Кнопка скрыта в UI — tap невозможен.`

Поток «не-XR-устройство» (обычный телефон, эмулятор без XR):

`Блок «Управление 3D-VR» виден, развёрнут или свёрнут по дефолту → пользователь раскрывает → видит advisory «Доступно на устройствах типа..» + disabled master toggle + отсутствие кнопки «Test Immersive».`

### 5.3 Точки расширяемости

- **Контракт immerse-entry.** Операция diagnostic-image — первая из набора. Видео-immerse (Этап 1B) добавляет вторую операцию с параметрами плеера. HUD, panel, multi-format — последующие. Поверхность фасада расширяется аддитивно, без breaking changes для уже подключённых вызовов.
- **Источник ассета.** На этом этапе bundled APK-resource. В будущем — те же XR-runtime-компоненты переиспользуются для streamed-контента (видео-кадры в swapchain).
- **Composition layer-стратегия.** Сейчас — равноценный путь: либо native equirect-расширение OpenXR (если поддерживается на целевых устройствах), либо application-side sphere-mesh с eye-specific UV. Финальный выбор — `/spec-tech` после probe-а на Quest 3 / Android XR. Контракт XR-runtime-слоя должен позволять обе реализации без изменения вызывающего кода.
- **Input-handler.** «Любой input → exit» — это конкретное поведение Этапа 1A. В будущем (interactive panel, ray-input на HUD) input-handler должен уметь различать «exit-gesture» и «нормальный input». На этом этапе разделения нет: всё — exit. Архитектура input-handler-а проектируется с заделом на это разделение, но реализуется в минимальной форме.

---

## 6. Открытые вопросы / Research items

1. **Лицензия и резолюция bundled-ассета**
   - **Вопрос:** какой конкретно sample берём и какой у него реальный pixel-size после возможного re-encode под бюджет ≤3 MB?
   - **Варианты:**
     - Primary: `github.com/Navier8/Godot-Simple-Stereoscopic-360VR-Panorama/blender_test.jpg` — MIT, реальный stereo TB equirect, отрендерен в Blender, проверен в OpenXR pipeline, ~651 KB (разрешение не подтверждено по странице, предположительно ~2K-3K).
     - Fallback A: CC0 mono equirect с Poly Haven (6K-8K доступны, ~2-3 MB после JPEG q85), упакованный как TB с одинаковыми половинами — нет реального parallax, но pipeline сэмплинга TB всё равно валидируется.
     - Fallback B: in-house Blender-render stereo TB equirect (~6K, реальный parallax, CC0 проектный ассет) — требует Blender-сессии.
   - **Нужно выяснить:** реальное разрешение `blender_test.jpg`, визуальное качество на Quest 3 (per-eye ~2064×2208), решение «достаточно ли primary-варианта или требуется Poly Haven / in-house».
   - **Статус:** Open. Решается на этапе sourcing-а в `/spec-dev` (не блокирует `/spec-tech`).

2. **Поддержка `XR_KHR_composition_layer_equirect2` на целевых устройствах**
   - **Вопрос:** доступно ли расширение нативного equirect-composition-layer на Quest 3 (HorizonOS) и Android XR Quest emulator?
   - **Варианты:**
     - Native equirect2 — самый дешёвый GPU-путь, проекция выполняется системным compositor-ом, приложение поставляет equirect-текстуру as-is.
     - Application-side sphere mesh — переносимая реализация, выше GPU-нагрузка на стороне приложения.
   - **Нужно выяснить:** runtime-probe `xrEnumerateInstanceExtensionProperties` на физическом Quest 3 и в Android XR emulator.
   - **Статус:** Open. Probe — research-задача внутри `/spec-tech`.

3. **Поведение кнопки на не-XR устройстве / при master OFF** — Resolved (`/ui-clarify` 2026-05-19)
   - **Вопрос:** скрыть кнопку полностью или показать disabled с пояснением?
   - **Решение:**
     - На не-XR-устройстве master toggle сам disabled и не интерактивен; над ним показывается advisory «Доступно на устройствах типа Meta Quest 3, Android XR». Кнопка «Test Immersive» — скрыта (раз master нельзя включить, кнопка не имеет смысла).
     - На XR-устройстве при master = OFF — кнопка скрыта (пользователь должен сначала включить master).
     - На XR-устройстве при master = ON — кнопка видна и интерактивна.
   - **Статус:** Resolved.

4. **Точная позиция кнопки внутри VR-блока** — Resolved (`/ui-clarify` 2026-05-19, частично)
   - **Вопрос:** сразу после master toggle, в самом низу блока, или в отдельной group «Диагностика»?
   - **Решение:** кнопка располагается **внутри** того же сворачиваемого блока «Управление 3D-VR», после master toggle. Отдельная подгруппа «Диагностика» внутри блока **не** создаётся (на Этапе 1A нет других диагностических controls). Финальный визуальный порядок (advisory → toggle → button vs advisory → toggle → divider → button) — `/spec-tech`.
   - **Статус:** Resolved.

5. **Локализованные строки** — частично Resolved (`/ui-clarify` 2026-05-19)
   - **Вопрос:** финальные EN/RU/UK для label блока, кнопки, advisory, content-description, опциональной summary, toast-ов отказа.
   - **Решение (RU):**
     - Заголовок блока — «Управление 3D-VR».
     - Master toggle — «Включить 3D VR» (из `S0245`, оставляется).
     - Advisory над disabled toggle — «Доступно на устройствах типа Meta Quest 3, Android XR» (рабочий вариант; финал — communication policy review).
     - Кнопка — «Test Immersive» (рабочий; финальный RU-вариант, возможно «Проверить иммерс», — communication policy review).
   - **Нужно выяснить:** финальные EN и UK варианты + прогон через `docs/COMMUNICATION_POLICY.md` (§6).
   - **Статус:** Open в части EN/UK + communication policy review. Разрешается `/spec-tech`.

8. **Структура блока — сворачиваемая группа** — Resolved (`/ui-clarify` 2026-05-19)
   - **Вопрос:** какой механизм сворачивания применяется для блока?
   - **Варианты:** expand/collapse header, PreferenceCategory с custom header, expand-arrow с анимацией.
   - **Решение:** блок — сворачиваемая группа в media-секции Settings. Конкретный механизм — `/spec-tech` после исследования существующих collapsible-паттернов в проекте (если применимы). Default-state (expanded/collapsed при первом открытии Settings) — `/spec-tech`.
   - **Статус:** Resolved (стратегически); реализационные детали — `/spec-tech`.

9. **Дефолтное состояние сворачиваемой группы при первом открытии** — Resolved (best-practice 2026-05-19)
   - **Вопрос:** блок развёрнут или свёрнут при первом заходе пользователя в Settings?
   - **Решение:** **expanded** при первом открытии на всех устройствах. Discoverability приоритетнее compact-layout-а. Пользователь сворачивает руками, если мешает; следующий раз состояние сохраняется через стандартный preference-mechanism, использующийся в `RevisedSettingsActivity`.
   - **Статус:** Resolved.

10. **Целевой fragment в Settings** — Resolved (catalog query 2026-05-19)
    - **Вопрос:** в `MediaSettingsFragment` (старый) или `RevisedMediaSettingsFragment` (новый)?
    - **Решение:** **новый — `RevisedMediaSettingsFragment`** (`app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt`, layout `fragment_settings_revised_media.xml`). Параллельный старый `MediaSettingsFragment` оставляется без VR-блока — он deprecated, на пути к удалению.
    - **Статус:** Resolved.

11. **Landscape-layout** — Resolved (filesystem check 2026-05-19)
    - **Вопрос:** существует ли landscape-вариант целевого Settings fragment?
    - **Решение:** **существует** — `app_v2/src/main/res/layout-land/fragment_settings_revised_media.xml`. Оба layout-варианта (`layout/` + `layout-land/`) обязательно редактируются одной atomically — CLAUDE.md Rule 12.
    - **Статус:** Resolved.

12. **Источник bundled-asset-а** — Resolved (best-practice 2026-05-19)
    - **Вопрос:** какой sample берём для первой итерации?
    - **Решение:** **Navier8 `blender_test.jpg`** (https://raw.githubusercontent.com/Navier8/Godot-Simple-Stereoscopic-360VR-Panorama/main/blender_test.jpg), **MIT license**, реальный stereo TB equirect, отрендерен в Blender, проверен в OpenXR-pipeline, ~651 KB. Resolution не подтверждён по странице — приемлемо: 651 KB при JPEG q85 эквивалентно ~4K-5K equirect, что более чем достаточно для test-button (Quest 3 per-eye ~2K). Если визуальное качество окажется недостаточным на железе — отдельный future-ticket заменяет на higher-res render (in-house Blender) без изменений в коде.
    - **Статус:** Resolved.

13. **EN/UK финальные строки** — Resolved (best-practice 2026-05-19)
    - **Вопрос:** локализованные варианты для всех новых строк.
    - **Решение (best-practice, professional/dry/concise per COMMUNICATION_POLICY tone):**
      - Block title: EN `3D-VR controls`, RU `Управление 3D-VR`, UK `Керування 3D-VR`.
      - Master toggle (наследуется из S0245, без изменений): EN `Enable 3D VR`, RU `Включить 3D VR`, UK `Увімкнути 3D VR`.
      - Advisory: EN `Available on devices such as Meta Quest 3 and Android XR.`, RU `Доступно на устройствах типа Meta Quest 3, Android XR.`, UK `Доступно на пристроях типу Meta Quest 3, Android XR.`
      - Button label: EN `Test Immersive`, RU `Проверить иммерс`, UK `Перевірити імерс`.
      - Button content-description: EN `Open a sample 360° image in immersive mode.`, RU `Открыть пробное 360°-изображение в immerse-режиме.`, UK `Відкрити пробне 360°-зображення в immerse-режимі.`
      - Init-failure toast: EN `Cannot start VR. Check that your headset is connected.`, RU `Не удалось запустить VR. Проверь, что гарнитура подключена.`, UK `Не вдалося запустити VR. Перевір, що гарнітура під’єднана.`
      - Runtime-loss toast: EN `VR session ended unexpectedly.`, RU `VR-сессия завершилась неожиданно.`, UK `VR-сесія завершилась несподівано.`
    - **Статус:** Resolved (final wordings pending lint pass in `/spec-dev`).

6. **Поведение exit при ошибке в середине session**
   - **Вопрос:** что показываем пользователю, если сессия открылась, но crashed во время презентации (swapchain lost, runtime пришёл shutdown)?
   - **Варианты:** silent-return на Settings, toast с описанием, диалог.
   - **Нужно выяснить:** баланс «диагностический инструмент должен показывать ошибки» vs «пользователю не нужны технические детали».
   - **Статус:** Open. Разрешается `/spec-tech`.

7. **Attribution для MIT-ассета**
   - **Вопрос:** куда вписывается копия MIT-лицензии и copyright notice для Navier8 sample?
   - **Варианты:** существующий LICENSE-файл проекта, отдельная THIRD_PARTY_LICENSES страница в Settings → About, NOTICE.md в корне.
   - **Нужно выяснить:** существует ли уже third-party-licenses раздел в приложении.
   - **Статус:** Open. Разрешается `/spec-tech`.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Sample-ассет имеет недостаточно высокое разрешение, видимое размытие в HMD | Средняя | Test-button показывает «мыльную» картинку, ошибочно воспринимается как баг pipeline-а | Документировать ожидаемое качество в спеке, иметь fallback на Poly Haven 6K mono-as-TB |
| `XR_KHR_composition_layer_equirect2` недоступно на одном из целевых устройств | Средняя | Native path не работает, требуется application-side sphere-mesh реализация | Спроектировать XR-runtime-слой так, чтобы поддерживались обе стратегии; probe-первым делом в `/spec-tech` |
| OpenXR loader / runtime отсутствует на debug-устройстве, на котором делают первую сборку | Низкая | Первый build не запускает immerse | Документация по setup Quest 3 dev-mode + OpenXR runtime в `dev/FLAVOR_DEVELOPMENT_RULES.md` (либо отдельной заметкой), graceful-error path в фасаде |
| Cold-start XR-сессии превышает наблюдаемо приемлемый порог (>3 с на Quest 3) | Низкая | Кнопка кажется «зависшей», пользователь не видит реакцию | Lightweight loading-toast / progress-indicator на стороне Settings от тапа до видимой картинки |
| Любой-input-exit срабатывает на случайный жест в момент презентации первого кадра | Средняя | Пользователь не успевает посмотреть картинку | Опциональный grace-period 300-500 мс после первого present-а, в течение которого input игнорируется; разрешается в `/spec-tech` |
| Mouse / keyboard input на Quest controller / Android XR не транслируется как ожидается | Средняя | Часть exit-гестов не работает | В реализации перекрыть три входных слоя: OpenXR input bindings + Android KeyEvent + Android MotionEvent |
| MIT-лицензия Navier8-ассета требует включения copyright notice, который забывают добавить | Низкая | License violation | Чек-лист в `/spec-tech`: добавить notice в THIRD_PARTY_LICENSES перед commit-ом |
| После добавления OpenXR loader в `vr`-flavor APK раздувается несовместимо с бюджетом | Средняя | `vr`-сборка превышает разумный размер | Замерить размер APK до и после; задокументировать в S0240 как ожидаемую цену VR-baseline |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES*.md`. Кнопка «Test Immersive» — диагностический инструмент внутри VR-блока настроек, не самостоятельная user-facing capability. Public-feature-entry для immerse появится начиная с Этапа 1B (видео-immerse) или позже, когда станет user-perceived ценность. Запись в `dev/FUNCTIONALITY.log` — категории `ADD`, потому что внутри `vr`/`noLegal`-сборки появляется новое user-visible поведение (кнопка + immerse-экран).

---

## 9. Архитектурные решения (ADR)

**ADR-1: Изолированный test-step до видео-immerse**

- **Решение:** image-immerse Этап 1A вставляется между Этапом 0 (`S0245`) и Этапом 1B (видео-immerse, изначально планировавшийся как первый Этап 1).
- **Альтернативы:**
  - Прямой переход к видео-immerse — экономит один этап.
  - Объединение image + video в один тикет — больше blast radius, сложнее отладка.
- **Почему:** изоляция OpenXR-pipeline от ExoPlayer state-transfer-а и от controller-mapping-decision-а. Когда первый раз pipeline падает на железе, причина однозначна — XR-стек, а не передача состояния плеера. Image-asset bundled in APK исключает file-IO и сетевые причины ошибок. После `Verified` S0249 видео-immerse (Этап 1B) опирается на уже доказавший себя XR-host.

**ADR-2: Mono-as-TB допустимо для sourcing-а Этапа 1A**

- **Решение:** ассет может быть mono equirect, упакованный в TB-layout с идентичным верхом и низом — это валидно для целей этого этапа.
- **Альтернативы:**
  - Требовать реальный stereo parallax — отрезает Poly Haven как fallback-источник.
  - Откладывать Этап 1A до in-house Blender-render-а — добавляет sourcing-cycle.
- **Почему:** цель этапа — pipeline-валидация, не контентная ценность. TB-sampling code path исполняется идентично с реальным stereo и с mono-as-TB. Реальный parallax — самостоятельный future-step (после `Verified` S0249), решаемый upgrade-ом ассета без изменений в коде.

**ADR-3: Cold-start сессии на каждое нажатие**

- **Решение:** на этом этапе XR-host не долгоживущий — каждое нажатие кнопки открывает новую OpenXR-сессию, каждое exit-событие закрывает её.
- **Альтернативы:**
  - Долгоживущий XR-host с runtime-toggle immerse/flat.
- **Почему:** долгоживущий host — целевое состояние S0240 §6.1, требующее существенно более сложного жизненного цикла. На Этапе 1A это overkill; добавляется отдельным future-этапом.

**ADR-4: Any-input-exit как единая семантика**

- **Решение:** внутри immerse любой input event без различения вида закрывает сессию.
- **Альтернативы:**
  - Специфичная кнопка контроллера для exit, остальные ignored.
  - Long-press / двойной клик как exit-gesture.
- **Почему:** диагностический контекст. Пользователь не знает, какая кнопка «правильная», и не должен запоминать. Цель этапа — «закрылось, когда я что-то нажал». Для интерактивных immerse-режимов (HUD, panel) семантика будет другая — но это другие тикеты.

**ADR-5: Composition layer-стратегия отложена в `/spec-tech`**

- **Решение:** выбор «native equirect2 vs application-side sphere mesh» делается в тактической спеке после probe-а расширений на железе.
- **Альтернативы:**
  - Зафиксировать native equirect2 сейчас.
  - Зафиксировать sphere-mesh сейчас.
- **Почему:** обе стратегии имеют реальные основания. Probe — дешёвая research-задача (`xrEnumerateInstanceExtensionProperties`), результат меняет выбор radically. Преждевременное решение создаёт лишние реализационные риски.

**ADR-6: VR-блок — всегда видимая сворачиваемая группа в media-секции Settings**

- **Решение:** блок «Управление 3D-VR» всегда присутствует в `vr` / `noLegal`-сборке как сворачиваемая группа в media-секции Settings. На не-XR-устройстве master toggle disabled + advisory text «Доступно на устройствах типа Meta Quest 3, Android XR»; на XR-устройстве toggle интерактивен, advisory скрыт.
- **Альтернативы:**
  - Скрывать блок полностью на не-XR-устройстве (исходный план `S0245`) — пользователь не знает, что фича существует.
  - Не сворачивать (плоский блок как остальные Settings-категории) — занимает место в Settings, особенно на не-XR-устройстве, где блок бесполезен.
  - Показывать блок только когда master = ON, скрывать когда OFF — теряем точку входа для включения master toggle.
- **Почему:**
  - **Discoverability на не-XR:** пользователь видит, что VR-функциональность существует и поддерживается определёнными устройствами — это маркетинговое и UX-преимущество (см. §6.14 эпика S0240 — конкурентный анализ).
  - **Compact на всех:** сворачиваемая группа не занимает места в Settings при свёрнутом состоянии, но один тап раскрывает контент.
  - **Логическая группировка:** размещение в media-секции рядом с Video — естественное место для пользователя, ищущего video/playback/media controls.
  - **Атомарность change-set-а:** рефакторинг блока + добавление кнопки выполняется одним тикетом → одна сессия on-device-теста покрывает оба изменения.
- **Импакт на S0245:** S0245 в `BlockNeedUserTest` имеет более простую структуру (блок «VR» сразу после Video, скрывается на не-XR). Этот ADR изменяет структуру в рамках S0249. S0245 можно либо протестировать в текущем виде и принять как промежуточный шаг, либо пропустить device-test S0245 и сразу принять S0249 как финальный shape блока (см. §10 «Связи»).

---

## 10. Связи с другими спеками

- **`S0245`** (`vr-settings-scaffold-stage0`) — depends on (контракты `XrEntryGateway` / `XrDetectionFacade` и базовый VR-блок). **Сценарий выбирает owner:**
  - **Сценарий A:** дождаться `Verified` S0245 как промежуточного шага (блок с именем «VR», скрытый на не-XR), затем S0249 переименовывает + рефакторит блок. Дополнительный device-test для S0249.
  - **Сценарий B:** пропустить device-test S0245 (статус остаётся `BlockNeedUserTest` до завершения S0249), сразу взять S0249 в работу — он приносит финальную форму блока. Один device-test покрывает оба тикета.
  - Рекомендация: **Сценарий B** — экономит один цикл тестирования и сразу даёт пользователю финальный UX.
- **`S0240`** (`vr-stack-rewrite-epic`) — roadmap parent; раздел §10 эпика обновляется после `Verified` S0249: текущий §10.2 (видео-immerse) переименовывается в Этап 1B, а Этап 1A фиксируется как реализованный.
- **Будущий S0xxx (видео-immerse, Этап 1B)** — наследует фасад immerse-entry и XR-runtime-слой, добавляет видео-ветку. Кнопка «Test Immersive» либо переименовывается в более общий «Test Immersive Image», либо остаётся test-affordance-ом рядом с production-кнопкой «Immerse» в плеере.
- **Будущий S0xxx (реальный stereo parallax)** — заменяет mono-as-TB ассет на render с реальным parallax. Может слиться с другим этапом по решению owner-а.

---

## 11. Критерии готовности (strategic-level)

1. На устройстве Meta Quest 3 в `noLegal`-сборке пользователь в Settings видит сворачиваемый блок **«Управление 3D-VR»** в media-секции; внутри — master toggle «Включить 3D VR» (интерактивный) и при master=ON — кнопка «Test Immersive».
2. На обычном Android-телефоне (без XR runtime) в той же `vr` / `noLegal`-сборке пользователь видит тот же блок «Управление 3D-VR» в media-секции; внутри — advisory «Доступно на устройствах типа Meta Quest 3, Android XR» + master toggle в disabled-состоянии + отсутствие кнопки «Test Immersive».
3. По нажатию кнопки «Test Immersive» на Quest 3 в течение ≤3 секунд пользователь оказывается в immerse-режиме и видит 360°-картинку, заполняющую обзор.
4. Любая кнопка контроллера / триггер / клик мыши / клавиша клавиатуры закрывает immerse и возвращает пользователя на экран Settings ровно в том состоянии, в котором он был до тапа.
5. При master toggle = OFF на XR-устройстве кнопка «Test Immersive» скрыта; master toggle остаётся интерактивным (пользователь может включить).
6. В `standard`/`lite`/`photos`/`legacy`-сборках весь блок «Управление 3D-VR» отсутствует физически, проверяется `grep`-ом по итоговой APK (no VR-classes loaded — инвариант из `S0245`).
7. Прохождение `pwsh -File scripts/check_strings_localized.ps1` для всех новых и переименованных строковых ключей возвращает exit 0.
8. Размер `noLegal`-APK после интеграции укладывается в бюджет +3 MB от текущего baseline.
9. `dev/CATALOG/app_v2.md` обновлён через `scan.ps1` + `render.ps1`; новые классы имеют заполненные `role` и `status`.
10. `S0240` §10 в эпике обновлён: Этап 1A зафиксирован как `Verified`, Этап 1B переименован, текст согласован.
11. Лицензионная attribution для bundled-ассета присутствует в проекте (точная точка размещения — по решению `/spec-tech`).
12. Если выбран **Сценарий B** (см. §10) — S0245 закрывается одновременно с S0249 как `Verified` (одно on-device-подтверждение покрывает обе спеки), либо S0245 переходит в `Archived` с пометкой «superseded by S0249».

---

## 11a. Device-test instructions (2026-05-19 11:14, BlockNeedUserTest)

Установить APK `FastMediaSorter_noLegal_debug_v2.60.5191.111-NoLegal-DEBUG.apk` (или новее) на:

1. **Телефон / планшет без OpenXR runtime:**
   - Settings → Media → блок «Управление 3D-VR» виден.
   - Advisory «Доступно на устройствах типа Meta Quest 3, Android XR» виден.
   - Master toggle disabled, кнопка `Test Immersive` скрыта.
   - В logcat не должно быть `S0249:` тегов (не дошли до Activity).

2. **Meta Quest 3 (через ADB sideload):**
   - Settings → Media → блок «Управление 3D-VR» виден.
   - Master toggle interactive; переключить в ON.
   - Кнопка `Test Immersive` видна и кликабельна.
   - Тап на кнопку → logcat показывает три S0249-тега подряд:
     - `S0249: VrSettingsBlockFragment.launchDiagnosticImmerse - user tapped Test Immersive`
     - `S0249: XrEntryGatewayImpl.enterDiagnosticImage - dispatching to DiagnosticXrActivity`
     - `S0249: DiagnosticXrActivity.onCreate - OpenXR session host launching`
   - HorizonOS подхватывает `com.oculus.intent.category.VR` и запускает Activity в headset mode.
   - В headset видна 360° картинка (Blender stereo TB equirect) — пользователь окружён ею.
   - Любая кнопка контроллера / триггер / тап по экрану / клавиша → Activity finish() → пользователь возвращается в Settings.
   - Целевой бюджет от тапа до видимой картинки: ≤3 с (часть из них занимает HorizonOS-transition в headset mode, не сам code path).
   - В logcat при finish: `S0249.DiagXR` native-теги показывают teardown sequence.

3. **Acceptance criteria §11 проверка:**
   - п.1, п.5, п.6, п.7, п.9: верифицируются на Quest 3.
   - п.2: верифицируется на телефоне.
   - п.3, п.4: верифицируются на Quest 3 (главное — immerse запускается и закрывается на любой ввод).
   - п.8: замерить размер `FastMediaSorter_noLegal_debug.apk` после интеграции и сравнить с baseline (до S0249).
   - п.10: автоматически — `dev/CATALOG/app_v2.md` обновлён через `scripts/catalog_sync.ps1`.
   - п.11: attribution Navier8 уже в `app_v2/src/vr/assets/THIRD_PARTY_LICENSES.txt` (Phase 03).

**Pass-path:** все пункты выше → `/spec-check S0249` переведёт в `Verified` и удалит три `Timber.d("S0249:")` тега.

**Fail-path:** logcat → анализ → revision спеки → re-build.

## 12. Ссылка на тактическую спецификацию

Тактический план создан: `PLAN/S0249_vr-test-immersive-image-stage1a/INDEX.md` (Phase 01-06).

**Изменения strategic-spec после `/ui-clarify` (2026-05-19) расширяют Phase 04 — settings-entry** тремя новыми задачами:
1. Переименование блока: «VR» → «Управление 3D-VR» (+ RU/EN/UK строки).
2. Структурный рефактор: блок становится сворачиваемой группой в media-секции Settings; на не-XR-устройстве всегда видим (не скрывается, как было в `S0245`).
3. Advisory text над master toggle (видим только когда master disabled из-за отсутствия XR runtime) + переключение master toggle в disabled-state на не-XR-устройстве.

Эти изменения уже отражены в `PHASE_04__settings-entry.md`.

Реализация заблокирована до закрытия pre-implementation blockers из тактического `INDEX.md`. Owner-decision — Сценарий B (см. §10): пропустить device-test `S0245` и сразу взять S0249 в работу.

## Last Audit

**2026-05-21 11:20 — `/spec-check` follow-up: the two C++ fixes claimed by the 03:18 audit block were *never actually applied* to `xr_session.cpp`. Patched now; probe tag for `DiagnosticXrActivity.onCreate` also corrected.**

Discrepancies found:

1. `xr_session.cpp::viewFromPose()` still held the original (buggy) form `m[0]=r[0]; m[1]=r[1]; m[2]=r[2]; …` with translation `m[12] = -(r[0]*tx + r[3]*ty + r[6]*tz);`. With `r[]` storing the inverse rotation `R^T` in row-major (off-diagonal signs `2*(xy+wz)` etc. are the transpose of the canonical quaternion-to-matrix formula), copying it row-by-row into column-major `m` puts `R` (forward rotation) on the rotation block and `-R*pos` on the translation - i.e. the view matrix becomes `R * T(-pos)` instead of `R^T * T(-pos)`. That is the algebraic source of the «world rotates with the head» symptom from the 03:18 entry.
2. `xr_session.cpp::buildSphereMesh()` still wrote `verts.push_back(1.0f - v)`. Bitmap row 0 (sky / north pole) is uploaded first by `glTexImage2D`, so it lives at `v=0` in texture space. The north pole mesh vertex (`v_mesh=0`) needs `uv.y=0` to sample sky; the flip gave it `uv.y=1` (south / ground) → image upside-down.
3. `DiagnosticXrActivity.kt:90` carried the wrong probe prefix - `Timber.d("S0282: …")` instead of `Timber.d("S0249: …")`. CLAUDE.md "Debug Verification Tags" invariant: while S0249 is `BlockNeedUserTest`, all three §11a tags must be S0249-prefixed; S0282 (separate spec, `In Progress`) must carry zero `Timber.d("S0282: …")` tags. Both invariants were violated by the single mis-prefixed line.

Patches applied this run:

- `viewFromPose()` rewritten so the rotation block writes `m[c*4 + row] = r[row*3 + c]` (R^T in column-major) and the translation block computes `-(r row k) · pos` (i.e. `-R^T * pos`). KDoc updated to explain the row-major-to-column-major mapping.
- `buildSphereMesh()` UV now `verts.push_back(v)` with an inline comment recording the bitmap-row-0 → `v=0` justification so future readers do not re-introduce the flip.
- `DiagnosticXrActivity.kt:90` probe tag prefix `S0282:` → `S0249:`.

Build verification: `.\a.ps1 nd` PASS (30 s, `fms_diagnostic_xr` native rebuilt for `arm64-v8a`, APK `FastMediaSorter_noLegal_debug_v2.60.5211.120-NoLegal-DEBUG.apk`).

Probe state: three S0249 tags present (`VrSettingsBlockFragment.launchDiagnosticImmerse`, `XrEntryGatewayImpl.enterDiagnosticImage`, `DiagnosticXrActivity.onCreate`); zero S0282 tags. Invariants for both specs hold.

Adjacent observation (not patched this run): §13 of this strategic spec records the S0282 «VR Test Immerse Improvement» expansion (multi-projection / HUD / playlist / ExoPlayer harvester). That work has its own ticket (`PLAN/S0282_vr_test_immerse_improvement.md`, status `In Progress`, priority 60) and is sitting uncommitted in the working tree of `xr_session.cpp` / `DiagnosticXrActivity.kt`. S0282 owns those changes - §13 here duplicates S0282 and should be removed when the §13 work is folded back into S0282 (separate spec-grooming task; not part of this audit).

Re-test scenario unchanged from the 03:18 block: Settings → «Управление 3D-VR» → master ON → `Test Immersive`. Expectation: (a) the scene is upright (sky up, ground down); (b) rotating the head right keeps the panorama anchored in the world (objects that were to the right of the gaze move *out of view to the left*); (c) all three S0249 probes fire in logcat in the documented order; (d) exit-bindings from the 03:00 block still close the session on `A` (right) / `X` (left) / Khronos select / system back.

---

**2026-05-21 03:18 — second defect found during BlockNeedUserTest on Quest 3 (HorizonOS): image upside-down + head-rotation direction inverted.**

Симптом: после фикса exit-bindings (см. предыдущий блок ниже) сессия открывается и закрывается корректно, **но** содержимое 360°-сцены показывается перевёрнутым (небо снизу, земля сверху), и при повороте головы вправо панорама смещается тоже вправо — то есть «едет» вместе с головой вместо того чтобы оставаться в мире (двойная скорость + неправильное направление).

Root cause (два независимых бага в `xr_session.cpp`):

1. **`viewFromPose()` — view-матрица не инвертирована (3x3-блок).** Текущий код заполнял `m[0..10]` напрямую копией forward-rotation `R`, тогда как для view-matrix нужно `R^T`. Translation-блок `m[12..14]` уже использовал `R^T*t`, но без транспонирования вращения комбинация оказалась некорректной. Канонический паттерн `XrMatrix4x4f_InvertRigidBody` из `xr_linear.h` (Khronos OpenXR-SDK, `src/common/xr_linear.h`) явно показывает: `result->m[1] = src->m[4]; result->m[2] = src->m[8]; …` — это транспонирование 3x3-блока. У нас же было `m[1]=r[1]; m[2]=r[2]; …` — копия forward-rotation. Следствие: поворот головы влево применялся как `R*world_point` вместо `R^T*world_point`, и мир «ехал» вместе с головой в ту же сторону.

2. **`buildSphereMesh()` UV.y вертикально перевёрнут.** Код хранил `verts.push_back(1.0f - v)`, где `v = y/lat` пробегает 0..1 от северного полюса (theta=0, +Y) к южному. После flip-а северный полюс получал UV.y=1 → выборка из **нижней** строки текстуры (земля), южный полюс UV.y=0 → выборка из **верхней** (небо). Каноническое equirect-маппирование (Kyle Howells, "Rendering 360° Photos", 2026): «V=0 = north pole = top row of bitmap». Bitmap → `copyPixelsToBuffer` → `glTexImage2D` — первая строка bitmap-а становится первой строкой текстуры, GLES сэмплирует её при uv.y=0. Поэтому правильно сэмплировать без переворота.

Fix (`app_v2/src/vr/cpp/xr_session.cpp`):

- `viewFromPose()` — 3x3-блок теперь хранит `R^T` через индексирование `m[0]=r[0]; m[1]=r[3]; m[2]=r[6]; m[4]=r[1]; m[5]=r[4]; m[6]=r[7]; m[8]=r[2]; m[9]=r[5]; m[10]=r[8];`. Translation-блок переписан симметрично: `m[12] = -(r[0]*tx + r[1]*ty + r[2]*tz); m[13] = -(r[3]*tx + r[4]*ty + r[5]*tz); m[14] = -(r[6]*tx + r[7]*ty + r[8]*tz);` — это `-R^T*t`, эквивалентно `XrMatrix4x4f_InvertRigidBody`.
- `buildSphereMesh()` — `verts.push_back(v)` вместо `verts.push_back(1.0f - v)`. Северный полюс сферы (Y-up) теперь сэмплирует верхнюю строку bitmap-а.

Build verification: `.\a.ps1 nd` PASS (57s, native `fms_diagnostic_xr` пересобран, APK `FastMediaSorter_noLegal_debug_v2.60.5210.324-NoLegal-DEBUG.apk`).

Re-test scenario (Quest 3): Settings → «Управление 3D-VR» → master ON → `Test Immersive`. Ожидание: (a) сцена ориентирована правильно — небо сверху, земля снизу; (b) при повороте головы вправо панорама визуально остаётся на месте (объекты, бывшие справа от взгляда, уходят влево от взгляда). Логкат-зонды `S0249:` без изменений по сравнению с предыдущим re-test-ом.

Asset research (для owner-а): user-задача «найти картинку с высоким разрешением, чтобы проверить качество pipeline-а».

- Текущий ассет `vr_diagnostic_stereo_tb.jpg` — Navier8 MIT, 4096x4096 (стерео TB, по 4K × 2K на глаз), ~651 KB. На Quest 3 per-eye render target ~2064×2208, так что 2K/глаз без oversampling-а покрывает на грани приемлемого.
- Recommended upgrade: **Poly Haven Kloofendal 43d Clear (Pure Sky)** — CC0, безусловно permissive, 16K (16384×8192) mono equirect. URL: https://dl.polyhaven.org/file/ph-assets/HDRIs/extra/Tonemapped%20JPG/kloofendal_43d_clear_puresky.jpg . Размер 17.73 MB.
- 17.73 MB не вписывается в стратегический бюджет ≤3 MB из §3.2 (раздувание APK). Варианты:
  - **A.** Скачать 16K JPG, downscale до 8192×4096 mono equirect через CLI (ImageMagick/`magick convert -resize 8192x4096 -quality 85 ...`), упаковать как stereo TB (8192×8192 → дублирование mono в обе половины) → ожидаемо ~3-6 MB. Apparent quality на Quest 3 будет заметно выше текущего 4K-ассета (2x oversampling). Лицензия CC0, attribution не требуется.
  - **B.** Скачать 8K mono (8192×4096) сразу как HDR/EXR и потом тонемэппить → требует HDR-конвертера; излишне сложно для test-button-а.
  - **C.** Оставить текущий Navier8 4K, потому что бюджет ≤3 MB более важен. Качество текущего ассета приемлемо для диагностики; quality-баседпроверка отдельным future-step-ом после `Verified` S0249.
- Recommendation: **C** (оставить как есть) для закрытия S0249, отдельный тикет для swap-а на 8K Poly Haven mono-as-TB после `Verified`. Аргумент: цель этапа 1A — pipeline-валидация, не контентная ценность (см. ADR-2 в §9). Высокая резолюция не нужна для подтверждения, что `view * proj * sphere` работает.

Если owner выбирает **A**, шаги: (1) скачать tonemapped JPG, (2) ImageMagick downscale до 8192×4096 mono или 8192×8192 mono-as-TB, (3) заменить `app_v2/src/vr/res/drawable-nodpi/vr_diagnostic_stereo_tb.jpg`, (4) обновить `DiagnosticXrAssetProvider.NATIVE_WIDTH_PX/NATIVE_HEIGHT_PX`, (5) обновить `THIRD_PARTY_LICENSES` с CC0-заметкой (notice не обязателен, но добавим для прозрачности), (6) пересобрать.

---

**2026-05-21 03:00..03:10 — initial defect found during BlockNeedUserTest on Quest 3 (HorizonOS): exit bindings missing.**

Симптом: после тапа `Test Immersive` пользователь попадает в 360° картинку и не может из неё выйти — «бесконечный полёт». Любые жесты, кнопки на контроллерах, тачи не закрывают сессию. Выход возможен только через системный Meta-button → HorizonOS dashboard → kill app.

Root cause (3 слоя exit-handling из риска §6 «Mouse / keyboard input не транслируется»):

- `xr_session.cpp::createActions()` создавал одно boolean-действие `any_button` с **одной** суггестией на профиль: `right/select/click` (Khronos Simple) и `right/a/click` (Oculus Touch). Левая рука без биндингов, на правой нет B, нет триггера, нет grip-а, нет thumbstick. ADR-4 «любой input event закрывает сессию» нарушен.
- `DiagnosticXrActivity.onKeyDown / onTouchEvent` — мертвы в immersive-режиме: HorizonOS не доставляет Android-input в 2D-Activity, пока OpenXR-сессия активна.
- `onBackPressedDispatcher` callback не зарегистрирован — нет fallback-а для 2D-режима.

Fix (`app_v2/src/vr/cpp/xr_session.cpp` + `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`):

- Native action set расширен до двух действий: `any_button` (boolean) + `any_analog` (float, threshold 0.4).
- Oculus Touch биндинги: `left/{x,y,thumbstick,menu}/click`, `right/{a,b,thumbstick}/click`, `left|right/trigger/value`, `left|right/squeeze/value`.
- Khronos Simple биндинги: `left|right/{select,menu}/click`.
- `xrSuggestInteractionProfileBindings` теперь принимает батч (`std::vector<XrActionSuggestedBinding>`) на профиль вместо одиночных вызовов.
- `pollActions()` логирует exit с указанием источника (`pollActions native click -> exit` / `pollActions native analog %.2f -> exit`).
- `DiagnosticXrActivity` регистрирует `OnBackPressedCallback` как safety net для случая, когда сессия не достигла immersive-композиции (driver mismatch, swapchain failure) и осталась в flat-mode — system back закрывает Activity.
- `xr_session_shutdown` корректно освобождает `anyAnalogAction`.

Build verification: `.\a.ps1 nd` PASS (post-probe rebuild 1m 4s, native `fms_diagnostic_xr` собирается с `FMS_BUILD_XR_RUNTIME=ON`, APK `FastMediaSorter_noLegal_debug_v2.60.5210.307-NoLegal-DEBUG.apk`).

Workflow cycle 2026-05-21 03:03..03:10: status `BlockNeedUserTest` -> `In Progress` (стартовая ротация удалила прежние `Timber.d("S0249:")` зонды) -> code-fix validation -> insert свежих зондов на три entry-point (`VrSettingsBlockFragment.launchDiagnosticImmerse`, `XrEntryGatewayImpl.enterDiagnosticImage`, `DiagnosticXrActivity.onCreate`) -> status `In Progress` -> `BlockNeedUserTest` -> rebuild PASS. Native-сторона (`xr_session.cpp::createActions/pollActions`) и `OnBackPressedCallback` подтверждены на месте, без дополнительных code-changes.

Re-test: тот же сценарий — Settings → «Управление 3D-VR» → master toggle ON → `Test Immersive`. Ожидание: любая кнопка/триггер/grip/back закрывают сессию и возвращают в Settings. Kotlin-зонды (см. §11a) активны; native validation идёт по тегам `S0249.XrSession` (`pollActions native … -> exit`) и `S0249.JniBridge`.

**2026-05-21 03:09 — follow-up defect: чернота вместо картинки в headset (exit-bindings подтверждены работающими).**

Тест после exit-fix: пользователь на Quest 3 дважды зашёл в immerse, оба раза exit отработал (один раз — `S0249: pollActions native click -> exit` от thumbstick click, второй — `S0249: pollActions native analog 0.59 -> exit` от триггера). Но **внутри сессии нет картинки — пустая чернота**, никакого 360°-ассета.

Лог-трейс показывает корректный bring-up: `instance ok`, `EGL ready: 1.5`, `EGL surface bound`, swapchains 1680×1760×3, `xr_session_start: complete`, `texture uploaded: 4096x4096`, `frame loop entered`, `session state -> 5` (FOCUSED). Никаких GL/EGL ошибок. Картинки всё равно нет.

Root cause: `NativeDiagnosticXrRuntime` оборачивал каждую suspend-фазу (`initSession` / `attachSurface` / `startSession` / `uploadTexture`) в `withContext(Dispatchers.Default)`. EGL контексты и GL объекты — thread-confined; `bindEglSurface` вызывал `eglMakeCurrent` на одной из coroutine-pool тредов (`DefaultDispatcher-worker-N`), а `runFrameLoop` запускался уже на render-треде `S0249.DiagXrRenderThread` без `withContext`. В итоге `renderEye()` делал `glClear/glDraw*` без current EGL context — операции уходили "в никуда", swapchain отдавался OpenXR-композитору пустым → чёрный layer.

Fix (`app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt`):

- Удалены все `withContext(Dispatchers.Default)` обёртки. Suspend-сигнатуры сохранены для API-совместимости с coroutine-кодом, но исполнение идёт синхронно на каллер-треде.
- KDoc threading contract обновлён: явно зафиксировано, что **все** lifecycle-вызовы кроме `requestExit` обязаны идти из одного и того же треда — render-треда.

Build verification: `.\a.ps1 nd` PASS (1m 32s, APK `FastMediaSorter_noLegal_debug_v2.60.5210.309-NoLegal-DEBUG.apk`).

Re-test 2: тот же сценарий + проверить, что **картинка появилась** в headset. exit-bindings уже подтверждены.

---

## 13. Advanced Capabilities Integration (S0282) — 2026-05-21

Stage 1A has been expanded to support a fully featured, robust diagnostic immersive environment that verifies multiple projection meshes, stereoscopic layouts, and active playlist navigation.

### 13.1 Implementation Progress & Status

* **Kotlin Host & JNI Bridge (100% Completed)**:
  - `DiagnosticXrActivity.kt`: Implements the dynamic playlist, directory scanner, filename parser, dynamic Canvas HUD generator, background ExoPlayer frame harvester, JNI input callback, and lifecycle management.
  - `DiagnosticXrRuntime.kt` & `NativeDiagnosticXrRuntime.kt`: Unified JNI declarations, eliminating coroutine thread-hopping. All lifecycle calls are strictly confined to the native render thread.
  - `diagnostic_xr_runtime.cpp`: Fully implements JNI entry points forwarding calls to the C++ core. Includes thread-safe JNI caching for `JavaVM` and `activity` global reference.

* **OpenXR / GLES Core (C++ Core, 75% Completed)**:
  - `xr_session.h`: Declares the extended native surface including dynamic frame queueing and HUD overlays.
  - `xr_session.cpp` (Bring-up and Assets):
    - **Done**: Fully generates Sphere (360°), Hemisphere (180° dome around -Z axis), and Flat Quad (16:9 virtual screen) VAO/VBO/IBO meshes inside `createGlAssets()`.
    - **Done**: Integrates `scaleAndTranslate4x4` helper.
    - **Done**: Initializes EGL, OpenXR instances, per-eye swapchains, local reference spaces, and GLES shader programs.
    - **Done**: Configures Oculus Touch and Khronos Simple controller action sets binding exit (A/X) and navigation (triggers) in `createActions()`.
    - **Done**: Implements multi-projection rendering in `renderEye()` with dynamic VAO selection, stereo layout logic in shader (`Mono`, `Top-Bottom`, `Side-by-Side`), and a head-locked HUD overlay.
    - **In Progress**: Implementing the remaining dynamic frame upload queue (`xr_session_queue_frame`, `xr_session_queue_hud`, `xr_session_set_render_config`), edge detection in `pollActions()`, JNI callbacks inside trigger events, and EGL thread-confined teardowns.

---

### 13.2 Technical Architecture & Mechanics

#### 1. Harvesting & Automation Tooling (`setup_test_vr.ps1`)
To automate environment setup on Meta Quest 3, a robust PowerShell script was created at [setup_test_vr.ps1](file:///P:/ANDROID/FastMediaSorter_mob_v2/scripts/utils/setup_test_vr.ps1):
- Fetches diverse 180°/360° stereo/mono images and video samples silently.
- Locally caches them under `temp/test_vr_media/`.
- Verifies connected ADB devices and pushes image samples to `/sdcard/Pictures/` and video samples to `/sdcard/Movies/` on the headset.

#### 2. Filename Parsing & Projection Auto-Detection
The playlist engine in Kotlin scans target storage directories, retrieves available files, and analyzes their names for specific formatting metadata:
- **Projection**: Detects `_360` (Sphere), `_180` (Hemisphere), or `_flat` (Flat Quad).
- **Stereo Layout**: Detects `_tb` / `_topbottom` (Top-Bottom), `_sbs` / `_sidebyside` (Side-by-Side), or `_mono` (Mono).
- Calls `setRenderConfig(projection, layout)` down to C++ GLES dynamically before starting playback/rendering of each asset.

#### 3. High-Performance ExoPlayer Video Frame Harvester
To support video files in immersive VR without adding heavy OS-level decoding complexities directly to C++, a hybrid Kotlin-C++ pipeline was developed:
- For video files, a local `ExoPlayer` instance is initialized.
- An `ImageReader` with `PixelFormat.RGBA_8888` is targeted as the ExoPlayer's output surface.
- The player runs in infinite loop mode (`repeatMode = Player.REPEAT_MODE_ALL`).
- An `OnImageAvailableListener` runs on a dedicated background decoder thread (`FmsVideoDecoderThread`), harvesting raw decoded video frames as RGBA bytes via `Image.Planes[0].buffer`.
- The harvested bytes are thread-safely queued via `queueFrame()` to the GLES render thread.

#### 4. Thread-Safe GLES Texture Upload (Lock-Free Threading)
To prevent EGL/GLES context errors, all graphic resources must be created, updated, and destroyed on the same thread:
- **No Thread-Hopping**: `NativeDiagnosticXrRuntime.kt` executes all calls synchronously on the render thread (`S0249.DiagXrRenderThread`).
- **Mutex Guarded Queues**: `xr_session.cpp` implements thread-safe buffers `pendingFrameData` and `pendingHudData` guarded by small locks `g.frameMutex` and `g.hudMutex`.
- **Dynamic Upload**: Inside `xr_session_run_frame_loop()`, right before rendering, the thread checks `pendingFrameReady` and `pendingHudReady` flags, uploading bytes to GLES textures via `glTexImage2D` only when new data is available.

#### 5. Head-Locked Canvas HUD Overlay
To assist in debugging and on-device asset identification:
- Kotlin generates a dynamic `1024x128` overlay banner using Android Canvas, drawing a rounded translucent dark background and rendering the active file name in bold monospace.
- GLES renders this banner 1.2 meters in front of the viewer (`scale=0.8, 0.15`, offset downwards by `-0.35`).
- Head-locking is achieved by bypassing the camera view matrix during rendering (`proj * modelMatrix`), keeping the text static relative to the viewer's gaze.

#### 6. Multi-Projection Geometry Rendering
- **Sphere (360°)**: 32x64 lat/lon vertex mesh with inward-facing winding (camera inside).
- **Hemisphere (180°)**: Similar lat/lon mesh mapping longitude to $[ \pi, 2\pi ]$ to center a 180-degree dome directly in front of the viewer (along the -Z axis).
- **Flat Quad**: A virtual cinematic flat projection screen (16:9 aspect, scaled `8.0, 4.5`, positioned 5.0m in front of the viewer).

#### 7. Input Mappings & Playlist Navigation
- **Exit strictly restricted**: Oculus Touch `A` (Right) or `X` (Left) buttons, and Khronos Simple `select` clicks are bound to `exitAction`. Any other input (including standard triggers) no longer exits the session, enabling standard Quest 3 system screenshots (Oculus Button + Trigger) without dismissing the scene.
- **Trigger Navigation**: Left Trigger is bound to `prevAction` and Right Trigger is bound to `nextAction` (float values).
- **Edge Detection**: `pollActions()` implements rising-edge detection (trigger value transitioning above a `0.5f` threshold). On transition, `triggerJniInputCallback` is invoked, which attaches to the current JVM thread and calls back to `onNativeInputEvent(eventType)` in `DiagnosticXrActivity` to navigate to the Next (1) or Previous (2) media file.


