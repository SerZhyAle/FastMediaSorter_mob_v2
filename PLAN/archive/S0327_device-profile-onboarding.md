# Стратегическая спецификация: S0327 - Device profile onboarding

**Ticket:** S0327
**Status:** BlockNeedUserTest
**Priority:** 50
**Date:** 2026-06-01
**Tier:** 4 - Strategic
**Roadmap entry:** Ad-hoc - запрос 2026-06-01
**Tactical spec:** [`PLAN/S0327_device-profile-onboarding/INDEX.md`](PLAN/S0327_device-profile-onboarding/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** spec / research.
- **Goal / expected outcome:** исследовать выбор профиля устройства при первой установке, первым тактическим этапом описать существующие default-настройки и подготовить основу для технической спецификации реализации.
- **Local anchor:** первый запуск после выбора языка; выбор профиля расположен под выбором языка.
- **Scope boundaries / forbidden areas:** профиль влияет на стартовые настройки, приоритеты команд, интерфейс и поведение; явных forbidden areas нет.
- **Done / success signal:** по результатам этой спецификации можно готовить техническую спецификацию реализации.
- **Autonomy rule:** агент получает свободу на первом research/tactical этапе: фиксирует явные технические допущения, готовит русскоязычный research-результат по существующим настройкам, а владелец уточняет этот результат перед реализацией preset-ов.
- **UI decisions / delegation:** утверждено: v1 включает `TV / media box`; Skip = `auto-profile`; first-run selector расположен под выбором языка и применяет выбранный профиль сразу без отдельного подтверждения; отдельный selector в настройках интерфейса расположен после языка и применяет профиль только после предупреждения и явного подтверждения; существующие установки после перехода на версию S0327 получают профиль `Other / Другой` без применения preset-а.

---

## 1. Проблема

Первый запуск сейчас настраивает язык и базовые разрешения, но не спрашивает, как устройство будет реально использоваться. Один и тот же набор дефолтов подходит плохо для личного телефона, домашнего планшета, автомагнитолы, медиапроигрывателя, фоторамки, отдельного видео- или аудиопроигрывателя, электронной книги, TV-приставки и VR-шлема.

Часть похожей логики уже существует как отдельные эвристики для больших экранов, TV/DPAD, multi-window и VR. Из-за этого новые стартовые настройки легко разнести по разным местам без единой причины, почему конкретный пользователь получил конкретный интерфейс.

---

## 2. Цели

1. Добавить на первый запуск ручной выбор профиля устройства после выбора языка.
2. Предвыбирать профиль автоматически, когда системные сигналы достаточно сильные.
3. Применять профиль как одноразовый стартовый preset при первой установке; позже повторное применение возможно только явным действием пользователя в Settings с предупреждением.
4. Сделать профиль наблюдаемым и изменяемым позже в Settings.
5. Использовать профиль для стартовых настроек, приоритетов команд и поведения экранов.
6. Сохранить flavor-изоляцию VR / noLegal и не вносить flavor-specific проверки в общий runtime-код.

**Non-goals:**

- Не делать отдельную реализацию каждого экрана в рамках этой стратегической спеки.
- Не менять существующие настройки у пользователей после обновления без явного действия пользователя.
- Не заменять resource profiles для папок устройственным профилем.
- Не объявлять фоторамку надёжно автоопределяемым Android form factor.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исследовать, какие профили стоит предложить пользователю.
2. Исследовать, как программа может угадать профиль сама.
3. Поддержать сценарий Skip: если пользователь не выбирает профиль, приложение применяет автоопределённый профиль.
4. Сделать стартовые настройки зависимыми от профиля.
5. Дать возможность профилю влиять на приоритет кнопок-команд, интерфейс и поведение.

### 3.2 Жёсткие ограничения

- **Flavor:** standard / lite / photos / legacy / noLegal / vr; VR-поведение физически изолируется по flavor-правилам.
- **API level:** standard API 26+, legacy API 23+; автоопределение должно деградировать без новых runtime-разрешений.
- **Wear OS:** не затрагивается в первой итерации; синхронизация профиля на часы остаётся отдельным вопросом.
- **Производительность:** детект выполняется синхронно и дёшево на первом запуске; тяжёлые проверки, сеть и сканирование файлов исключены.
- **Совместимость данных:** существующие установки при обновлении получают выбранный профиль `Other / Другой` с migration source, без batch preset-а и без изменения текущих настроек; новая запись должна иметь версию preset-а и источник выбора.
- **Локализация:** EN/RU/UK обязательны для всех новых пользовательских строк.
- **Доступность:** экран выбора профиля обязан работать с touch, keyboard, DPAD/TV remote, mouse и TalkBack.
- **Communication policy:** новые тексты проходят `docs/COMMUNICATION_POLICY.md` §6 до интеграции строк.

### 3.3 Owner inputs (Approval gate)

- **Profile list approved:** 11 selectable profiles (Personal smartphone, Home tablet, TV/media box, Car head unit, Media player, Photo frame, Video player, Audio player, E-book reader, VR headset, Other) - research complete, ready for implementation.
- **First-run vs Settings UX approved:** Welcome selector under language picker applies preset immediately; Settings selector requires warning + explicit confirmation before apply.
- **Existing install migration approved:** Existing installs receive profile `Other / Другой` with no preset applied; new installs apply profile preset on welcome or skip; Settings reapply requires explicit user action.
- **Detector confidence handling approved:** High/Medium/Low confidence signals; low-confidence detector falls back to `Personal smartphone` with reason logged.
- **Related tickets:** S0245 (VR settings scaffold), S0249 (VR runtime availability), S0292 (VR content launch UI), S0293 (multi-window capability defaults), S0326 (global 3D/VR settings), S0230/S0289 (TV/keyboard/DPAD input coverage).

---

## 4. Контекст текущей архитектуры

В приложении уже есть первый запуск с языком, карточками возможностей, разрешениями и переходом к Settings после завершения welcome-flow. Настройки хранятся централизованно, поддерживают fresh-install fallback-и, экспорт/импорт, reset по секциям и feature-gated значения.

Сейчас поведение по типу устройства задаётся точечно: TV/DPAD влияет на фокус и навигацию, XR/VR влияет на видимость и defaults VR-поверхности, multi-window defaults зависят от отдельных capability-сигналов. Нет единой сущности «профиль устройства», нет сохранённого источника выбора и нет матрицы preset-ов, которую можно проверить продуктово.

---

## 5. Предлагаемый подход

Ввести единый профиль устройства как стартовый intent пользователя, а не как жёсткую классификацию hardware. Профиль состоит из значения, источника и confidence: пользовательский выбор всегда сильнее автоопределения, автоопределение сильнее generic fallback-а.

### 5.1 Selectable-профили v1

**Personal smartphone**

- Базовый профиль fallback-а.
- Приоритет: быстрый доступ к личным папкам, share/default-player сценариям, безопасные операции с подтверждениями.
- Поведение: стандартный touch-first UI, обычные размеры controls, без агрессивного фонового сканирования.

**Home tablet**

- Для большого touch-экрана, дивана, кухни, общего семейного устройства.
- Приоритет: grid/list обзор, крупные thumbnails, slideshow, multi-window там, где устройство реально это поддерживает.
- Поведение: больше экранного пространства используется для контента и параллельных панелей, но destructive-действия остаются защищёнными.

**TV / media box**

- Утверждён как отдельный selectable-профиль v1.
- Приоритет: DPAD-first, удалённое управление, playback/slideshow/cast, минимум мелких inline-кнопок.
- Поведение: фокус всегда предсказуемый, команды чаще уходят в overflow, touch-only affordance не является единственным путём.

**Car head unit**

- Для автомагнитол и автомобильных Android head units.
- Приоритет: большие кнопки, минимальные отвлечения, быстрый playback, screen-on, безопасные подтверждения.
- Поведение: опасные и мелкие операции понижены в приоритете, команды управления медиа выше файлового администрирования.

**Media player**

- Для устройства, которое используется как общий медиапроигрыватель без document-first сценариев.
- Приоритет: фото, видео, музыка, slideshow/playback, keep-screen-on, fullscreen, низкое вмешательство пользователя.
- Поведение: документы и файловое администрирование понижены, медиаконтент остаётся широким.

**Photo frame**

- Для стационарного планшета или панели, которая большую часть времени показывает фото/слайдшоу.
- Приоритет: изображения/GIF, slideshow, keep-screen-on, fullscreen, низкое вмешательство пользователя.
- Поведение: документы, видео, музыка и режим “другие файлы” выключены; destructive-команды спрятаны или понижены.

**Video player**

- Для устройства, которое используется почти только для видео.
- Приоритет: video playback, thumbnails, keep-screen-on, fullscreen, predictable controls.
- Поведение: документы, фото, музыка и режим “другие файлы” выключены; видео остаётся основным типом контента.

**Audio player**

- Для устройства, которое используется почти только для музыки/аудио.
- Приоритет: audio playback, background audio, now-playing surface, low visual noise.
- Поведение: документы, фото, видео и режим “другие файлы” выключены; аудио остаётся основным типом контента.

**E-book reader / Электронная книга**

- Для устройства, которое используется как электронная книга или document reader.
- Приоритет: текст, PDF, EPUB, Office documents, статичные изображения, комфортное чтение.
- Поведение: видео, музыка, GIF-анимация и режим “другие файлы” выключены; документы и изображения остаются доступными.

**VR headset**

- Для Quest / Android XR / OpenXR-сценариев в поддерживающих flavor-ах.
- Приоритет: immersive playback, 3D/VR detection, headset-friendly navigation, отдельная логика доступности VR controls.
- Поведение: VR-возможности включаются только при runtime-доступности; на обычном устройстве профиль не должен открывать недоступные действия.

**Other / Другой**

- Для пользователя, который не хочет менять defaults через device profile.
- Приоритет: сохранить текущее поведение приложения без batch preset-а.
- Поведение: выбор профиля сохраняется, но настройки не перезаписываются preset matrix.

### 5.2 Автоопределение

Детектор должен возвращать не только профиль, но и confidence и список сработавших сигналов.

**High confidence**

- VR headset: XR/OpenXR features, VR headtracking, VR UI mode, известный headset manufacturer.
- Car head unit: automotive feature или car UI mode.
- TV / media box: leanback / television feature или television UI mode.

**Medium confidence**

- Home tablet: smallest width от 600dp, touch screen, нет car/TV/VR сигналов.
- Personal smartphone: smallest width ниже 600dp, touch screen, telephony или обычный handheld набор признаков.
- Desktop/ChromeOS: PC/ARC/freeform-сигналы; в v1 это modifier для tablet-like defaults, а не отдельный пользовательский профиль.

**Low confidence**

- Media player / Photo frame / Video player / Audio player / E-book reader: нет надёжного стандартного Android-признака. Детектор может предлагать их только как low-confidence/manual-first сценарии; точный выбор должен оставаться за пользователем.
- Unknown / conflicting: несколько сильных сигналов конфликтуют, например большой touch-экран с car mode или TV box с touchscreen.

### 5.3 Применение preset-а

На fresh install профиль применяется один раз после выбора или skip-а как batch preset стартовых настроек. В welcome-flow отдельный диалог подтверждения не показывается: выбор профиля под выбором языка считается явным действием первого запуска. Приложение сохраняет value, source, confidence, preset version и факт применения профиля, но не ведёт per-setting source и не отслеживает, изменил ли пользователь конкретную настройку позже.

В Settings повторное применение профиля разрешено только через явное действие пользователя после предупреждения о смене настроек и явного подтверждения. Это тоже batch preset: после применения приложение не следит за последующими ручными изменениями отдельных настроек.

Existing installs после обновления получают сохранённый профиль `Other / Другой` с source `migration-existing`, confidence `none` и признаком, что preset не применялся. Их текущие настройки не меняются, пока пользователь явно не применит другой профиль в Settings.

Профиль влияет на категории, а не на разрозненные флаги:

- Content defaults: включённые типы медиа, thumbnails, cache/preload, slideshow.
- Interaction defaults: big buttons, compact/expanded controls, command panel, DPAD focus, touch hints.
- Operation safety: delete/move confirmations, trash, overflow для файловых операций.
- Device behavior: prevent sleep, fullscreen/system bars, background audio, resume on launch.
- Command priorities: какие команды прямые, какие уходят в overflow, какие скрыты до enable-а.
- Feature discoverability: какие onboarding cards и Settings blocks получают priority/highlight.

### 5.4 Потоки данных и событий

Порядок шагов первого запуска:

1. Шаг выбора языка.
2. Детектор профиля подготавливает recommended profile.
3. UI выбора профиля отображается под выбором языка.
4. Применение preset-а профиля сразу без отдельного подтверждения на Welcome.
5. Поток разрешений и выбора плеера по умолчанию.
6. Переход в Settings или на главный экран.

Если пользователь выбирает профиль вручную, сохраняется manual source. Если пользователь нажимает Skip, применяется авто-профиль с source `auto-skipped`; при низкой уверенности применяется safe fallback и сохраняется reason. Если пользователь выбирает `Other / Другой`, сохраняется выбор без применения batch preset-а.

В настройках интерфейса пользователь видит текущий профиль, источник и может сменить профиль в отдельном блоке после настройки языка. Смена профиля после первого запуска не пытается вычислить ручные overrides: перед применением показывается предупреждение, что выбранный preset может изменить настройки, и пользователь подтверждает применение явно.

Для пользователей, обновившихся с предыдущей версии, текущий профиль в Settings должен отображаться как `Other / Другой`; это объясняет, почему приложение сохранило прежние настройки и не применило новый device preset.

### 5.5 Точки расширяемости

- Новые профили добавляются через матрицу preset-ов без изменения экранной логики.
- Новые signals добавляются в detector без изменения пользовательской модели.
- Командные панели читают профиль как input для priority policy, но сохраняют существующие per-resource и ручные настройки пользователя.
- Flavor-specific VR capabilities поставляются через существующую flavor boundary.

---

## 6. Открытые вопросы / Research items

1. **Финальный список профилей**
   - **Вопрос:** включать ли `TV / media box` и content-only профили в v1 рядом с исходными профилями из запроса?
   - **Варианты:** добавить как selectable-профили; оставить как hidden modifiers; перенести часть в future ticket.
   - **Нужно выяснить:** нет.
   - **Статус:** Resolved - v1 включает `Personal smartphone`, `Home tablet`, `TV / media box`, `Car head unit`, `Media player`, `Photo frame`, `Video player`, `Audio player`, `E-book reader`, `VR headset` и `Other / Другой`.

2. **Фоторамка**
   - **Вопрос:** фоторамка должна быть полноценным selectable profile или preset внутри tablet/slideshow?
   - **Варианты:** отдельный профиль; под-профиль Home tablet; только быстрый шаблон в Settings.
   - **Нужно выяснить:** нет.
   - **Статус:** Resolved - `Photo frame` остаётся отдельным selectable-профилем v1, но старый широкий photo/audio/video сценарий переименован в `Media player`.

3. **Content-only профили**
   - **Вопрос:** нужны ли отдельные узкие профили для фото, видео, аудио и чтения?
   - **Варианты:** один общий media player; отдельные режимы по типам контента; оставить только Settings templates.
   - **Нужно выяснить:** нет.
   - **Статус:** Resolved - v1 включает `Photo frame`, `Video player`, `Audio player` и `E-book reader` как отдельные selectable-профили; каждый отключает нецелевые content types по preset matrix.

4. **Skip behavior**
   - **Вопрос:** Skip применяет авто-профиль, safe fallback, или оставляет текущие defaults без profile source?
   - **Варианты:** apply detected profile; apply personal smartphone fallback; не применять preset, только сохранить `unknown`.
   - **Нужно выяснить:** нет.
   - **Статус:** Resolved - Skip применяет `auto-profile` с source `auto-skipped`; low-confidence detector падает в safe fallback с сохранённым reason.

5. **Preset matrix**
   - **Вопрос:** какие существующие настройки уже имеют default-значения, для чего они предназначены, и какие из них можно включить в profile preset v1?
   - **Варианты:** минимальный набор interaction/safety; широкий набор media/cache/playback; двухуровневая матрица `core + optional`.
   - **Нужно выяснить:** Phase 01 должен подготовить русскоязычный research-результат по существующим default-настройкам и их назначению; владелец уточнит итоговую matrix перед реализацией preset-ов.
   - **Статус:** Resolved - процесс утверждён: research-first, затем owner refinement.

6. **User override rules**
   - **Вопрос:** как отличать значение, заданное профилем, от значения, изменённого пользователем позже?
   - **Варианты:** per-setting source; profile version plus changed-keys set; coarse rule «profile only on absent key».
   - **Нужно выяснить:** нет.
   - **Статус:** Resolved - не отслеживать per-setting overrides; preset применяется batch-операцией при первой установке или явном применении в Settings, а последующие ручные изменения отдельных настроек не мониторятся.

7. **UI form**
   - **Вопрос:** экран выбора профиля должен быть отдельной welcome-страницей, bottom sheet после language picker, или частью первой welcome-страницы?
   - **Варианты:** отдельная страница; inline block под language picker; modal/bottom sheet.
   - **Нужно выяснить:** нет.
   - **Статус:** Resolved - first-run selector расположен под выбором языка и применяет профиль без отдельного подтверждения; Settings selector находится в настройках интерфейса после языка и требует предупреждение плюс явное подтверждение перед применением.

8. **External Android signals**
   - **Вопрос:** какие платформенные сигналы считать supported baseline?
   - **Варианты:** только public `PackageManager` / `Configuration`; добавить manufacturer allowlist для headset; добавить runtime power/dock signals только после первого запуска.
   - **Нужно выяснить:** границу между надёжным detector-ом и speculative heuristics.
   - **Статус:** Resolved - baseline: public features + UI mode + screen width; manufacturer fallback только для headset, если уже используется текущей VR-логикой.

9. **External references used**
   - **Вопрос:** какие источники подтверждают классификацию?
   - **Варианты:** Android `PackageManager`, `UiModeManager`, responsive/adaptive layout docs, Android XR docs.
   - **Нужно выяснить:** нет.
   - **Статус:** Resolved - sources:
     - https://developer.android.com/reference/android/content/pm/PackageManager
     - https://developer.android.com/reference/android/app/UiModeManager
     - https://developer.android.google.cn/develop/ui/views/layout/responsive-adaptive-design-with-views
     - https://developer.android.com/develop/xr/jetpack-xr-sdk/build-immersive

### 6.1 Блокеры перед `/spec-tech`

Перед `/spec-tech` обязательных owner-блокеров нет. Первый тактический этап должен быть research-only:

1. Найти существующие настройки с default-значениями.
2. Описать на русском, для чего каждая настройка сейчас предопределена.
3. Предложить draft preset matrix v1 без реализации.
4. Остановиться на owner refinement перед фазами, которые меняют runtime-настройки.

Дополнительные обязательные tactical tasks после owner refinement:

1. Первым документационным шагом объяснить, почему выбор профиля устройства важен: профиль меняет стартовые defaults, видимость/приоритет команд, контентные типы и поведение экранов; `Other / Другой` оставляет текущие defaults без batch preset-а.
2. Подобрать визуальные изображения для каждого selectable-профиля: `Personal smartphone`, `Home tablet`, `TV / media box`, `Car head unit`, `Media player`, `Photo frame`, `Video player`, `Audio player`, `E-book reader`, `VR headset`, `Other / Другой`.
3. Один и тот же approved asset set должен использоваться в приложении и документации; для каждого изображения нужны source/license note, stable asset id/filename, EN/RU/UK alt/caption text и проверка читаемости в Welcome и Settings.
4. Создать проектную техническую документацию по принятой profile preset matrix: список профилей, список управляемых настроек, принятые значения, значение пустых ячеек, `Other / Другой` semantics, версию preset-а и правила изменения матрицы для следующих разработчиков.
5. Подготовить для каждого selectable-профиля короткий локализованный EN/RU/UK paragraph, который описывает группу устройств и перечисляет примеры; этот текст должен использоваться единообразно в Welcome, Settings и документации, проходить communication policy и проверку размещения на 360dp.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Неверный авто-профиль | Средняя | Пользователь получает неудобные defaults и думает, что приложение «сломано» | Показывать manual choice, confidence, и не применять low-confidence guess без safe fallback |
| Профиль перезаписывает ручные настройки | Средняя | Потеря доверия к Settings | Применять preset только на первом запуске или через explicit preview/apply |
| Existing installs получают скрытый авто-профиль | Средняя | После обновления настройки могут измениться без согласия пользователя | На migration выставлять `Other / Другой`, сохранять прежние настройки и не применять batch preset |
| Content-only профили выглядят автоопределяемыми, хотя Android не даёт такого сигнала | Высокая | False positives на планшетах, TV boxes и head units | Считать Media player / Photo frame / Video player / Audio player / E-book reader manual-first profiles; detector только предлагает их с низкой уверенностью или после явного выбора пользователя |
| Flavor leakage VR/noLegal | Средняя | Стандартные сборки получают чужие элементы или зависимости | Использовать flavor boundary и общие интерфейсы без новых BuildConfig-веток в общем коде |
| Слишком много опций в welcome-flow | Средняя | Пользователь устаёт до первого контента | Ограничить v1 коротким выбором профиля, Recommended badge и Skip |
| Командные панели получают новую скрытую сложность | Средняя | Регрессии в player/browse actions | Вынести priority policy в отдельный слой и покрыть матрицу unit-тестами |

---

## 8. Влияние на пользователя (docs/FEATURES)

Первый документационный шаг S0327: объяснить, почему выбор профиля устройства важен. Пользователь должен понять до implementation details, что профиль меняет стартовые defaults, видимость и приоритет команд, контентные типы, fullscreen/screen-on/background behavior и safety defaults.

После реализации добавить в `docs/FEATURES.md` + `_RU` + `_UK`: First-run device profile setup selects a phone, tablet, TV/media box, car head unit, media player, photo frame, video player, audio player, e-book reader, VR headset, or Other preset and applies safer defaults for that device style.

Для existing installs документация должна явно сказать: после обновления приложение показывает профиль `Other / Другой`, потому что прежние настройки сохранены и новый preset не применён автоматически.

Для документации и приложения нужен единый approved image set для всех selectable-профилей. Изображения должны быть reusable между Welcome/Settings UI и docs, иметь стабильные asset ids, license/source notes и локализуемые captions/alt text.

Для каждого selectable-профиля нужен короткий локализованный EN/RU/UK paragraph: что это за группа устройств, когда её выбирать и примеры устройств. Текст должен быть одинаковым по смыслу в приложении и документации; черновик owner-edit copy хранится в `temp/S0327/` до переноса в production strings/docs.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Профиль - это пользовательский intent, не hardware truth**

- **Решение:** профиль хранит value, source, confidence и applied preset version.
- **Альтернативы:** вычислять профиль на каждом старте из hardware signals.
- **Почему:** пользователь может использовать планшет как фоторамку или медиапроигрыватель, телефон как head unit или аудиоплеер, а Quest/noLegal APK может запускаться на обычном телефоне.

**ADR-2: Manual choice всегда сильнее detector-а**

- **Решение:** ручной выбор не меняется автоматически после обновления, смены устройства или импорта.
- **Альтернативы:** пересчитывать профиль при каждом запуске.
- **Почему:** профиль управляет настройками и приоритетами команд; silent пересчёт может изменить поведение без согласия.

**ADR-3: Detector возвращает confidence**

- **Решение:** автоопределение использует high/medium/low confidence, а UI показывает recommended profile только когда это не выглядит как обещание точности.
- **Альтернативы:** возвращать один enum без объяснения.
- **Почему:** Android даёт сильные сигналы для car/TV/VR, но не для content-only сценариев и не всегда для tablet-vs-desktop.

**ADR-4: TV / media box входит в selectable-профили v1**

- **Решение:** включить TV / media box как отдельный selectable-профиль v1.
- **Альтернативы:** спрятать TV под car/tablet или оставить только как DPAD modifier.
- **Почему:** приложение уже поддерживает Android TV boxes и DPAD; без отдельного профиля TV defaults смешаются с car defaults, хотя сценарии отличаются.

**ADR-5: Профиль применяется через batch preset matrix**

- **Решение:** profile preset описывает категории настроек и command priority, а реализация применяет batch-набор значений при первой установке или явном применении в Settings.
- **Альтернативы:** зашить дефолты рядом с каждым экраном.
- **Почему:** централизованная матрица проверяема, версионируема и не требует отслеживать per-setting overrides после ручных изменений пользователя.

**ADR-6: Existing installs мигрируют в Other / Другой**

- **Решение:** при обновлении с версии без device profile существующие установки получают сохранённый профиль `Other / Другой` с migration source и без применения batch preset-а.
- **Альтернативы:** применить auto-profile после обновления; оставить профиль пустым до первого открытия Settings.
- **Почему:** `Other / Другой` даёт наблюдаемое состояние в Settings и сохраняет прежнее поведение без silent changes.

---

## 10. Связи с другими спеками

- S0245 / S0249 - VR settings scaffold и runtime VR availability shape.
- S0292 - VR content launch UI и visual language VR controls.
- S0293 - capability-detected multi-window defaults.
- S0326 - global 3D/VR default settings.
- S0230 / S0289 - TV/keyboard/DPAD input coverage для first-run и основных поверхностей.

---

## 11. Критерии готовности (strategic-level)

1. На fresh install после выбора языка пользователь видит выбор профиля устройства или явно делегированный auto/skip путь.
2. Экран показывает recommended profile, если detector имеет high или medium confidence.
3. Пользователь может выбрать personal smartphone, home tablet, TV / media box, car head unit, media player, photo frame, video player, audio player, e-book reader, VR headset и Other / Другой.
4. Skip применяет `auto-profile`; при low-confidence detector применяется safe fallback с сохранённым reason.
5. После применения профиля стартовые настройки отличаются наблюдаемо для минимум трёх профилей.
6. Профиль не отслеживает последующие ручные изменения отдельных настроек пользователя.
7. В Settings пользователь видит текущий профиль и может явно применить другой preset через предупреждающий UX.
8. VR-профиль не показывает недоступные VR-действия в flavor-ах и устройствах без runtime-поддержки.
9. Car/TV профили работают с DPAD/keyboard/mouse без touch-only тупиков.
10. EN/RU/UK строки присутствуют и проходят parity check.
11. Existing installs после обновления показывают профиль `Other / Другой`, сохраняют старое поведение и не получают batch preset, пока пользователь явно не выберет и не применит другой профиль.
12. Источник и уверенность профиля наблюдаемы в состоянии приложения и доступны для диагностики в debug-логах без утечки персональных данных.
13. Documentation-first step объясняет важность выбора профиля устройства до описания implementation details.
14. Для всех selectable-профилей подобран единый image asset set, пригодный для приложения и документации.
15. Принятая profile preset matrix описана в проектной технической документации так, чтобы следующие разработчики могли дополнять и менять значения профилей без восстановления контекста из research-файлов.
16. Для каждого selectable-профиля есть approved EN/RU/UK description paragraph с примерами устройств; тексты проходят communication policy, localization parity и проверку читаемости в Welcome/Settings.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0327` - создаст `PLAN/S0327_device-profile-onboarding/` с фазами.

## Proposed Structural Changes

### Proposal P-1 - Profile resolution clarity (proposed 2026-06-02 by Claude)

**Status:** Proposed
**Affected:** §3.1 pkt 1, §6 Q1
**Rationale:** §3.1 pkt 1 asks owner to "исследовать, какие профили стоит предложить", but §5.1 already lists 11 selectable profiles and §6 Q1 is marked Resolved with the same 11 profiles. This creates ambiguity: is the research already done, or is it a future task? Clarify whether §3.1 reflects a past research phase (already resolved) or a future research step under `/spec-tech` Phase 01.
**Suggested edit:**
> §3.1 pkt 1: "Исследовать, какие профили стоит предложить пользователю." → "*Resolved: выбраны 11 selectable-профилей (см. §5.1, §6 Q1) как базовый набор v1.*"

---

### Proposal P-2 - Detector signals table (proposed 2026-06-02 by Claude)

**Status:** Proposed
**Affected:** §5.2 Автоопределение
**Rationale:** §5.2 describes confidence levels (High/Medium/Low) and mentions signals (`XR features`, `leanback`, `smallest width`, etc.), but does not provide a **single explicit table** mapping each signal to its profile(s) and confidence. This makes it harder to verify a detector implementation later. Recommend adding a structured table: `Signal | Profile(s) | Confidence`.
**Suggested edit:**
> Insert after "Low confidence" paragraph in §5.2:
> ```
> | Signal | Profile(s) | Confidence |
> |--------|-----------|------------|
> | XR/OpenXR features | VR headset | High |
> | VR headtracking, headset manufacturer | VR headset | High |
> | Automotive feature, car UI mode | Car head unit | High |
> | Leanback / television feature | TV / media box | High |
> | Smallest width ≥ 600dp, no car/TV/VR | Home tablet | Medium |
> | Smallest width < 600dp, touch, telephony | Personal smartphone | Medium |
> | PC/ARC/freeform signals | Desktop (modifier) | Medium |
> ```
> This table makes the high/medium distinction concrete and verifiable.

---

### Proposal P-3 - Per-setting override rationale (proposed 2026-06-02 by Claude)

**Status:** Proposed
**Affected:** §5.3 Применение preset-а, ADRs
**Rationale:** §5.3 states "приложение не ведёт per-setting source и не отслеживает, изменил ли пользователь конкретную настройку позже", but the WHY is not explained in the body. This is an important architectural choice: users cannot distinguish which settings came from the profile vs. which they changed manually. Consider adding an ADR-7 or extending ADR-5 to justify: "per-setting tracking adds persistence overhead and complicates undo/rollback; batch-apply-and-forget is simpler and sufficient for v1 because users are expected to explicitly choose Settings → Device Profile → Apply if they want a new profile applied."
**Suggested edit:**
> Add or extend ADR (after ADR-6):
> ```
> **ADR-7: No per-setting override tracking**
> 
> - **Decision:** Profile preset applies as a batch operation; app does not track which settings came from the profile or which were manually changed afterward.
> - **Alternatives:** track source per setting; store changed-keys set on each apply.
> - **Why:** per-setting source persists overhead for a feature users do not see; batch apply is sufficient because users control when reapply happens (explicit Settings action + warning + confirmation).
> ```

---

## Revision History

- **2026-06-02** - by `/spec-update` (Claude, focus: language, structure, verifiability, completeness)
   - Applied: 3 (removed catalog technical notation from §0; clarified research-first process in §6.1; cleaned revision history). Proposed (DISCUSS): 3.
   - Proposed: (P-1) clarity on §3.1-§6 profile resolution discrepancy; (P-2) explicit signals table for §5.2 detector; (P-3) per-setting override rationale in §5.3 or ADR.

---

## Last Audit

**Date:** 2026-06-02 (readiness pass ~17:10)
**Mode:** full
**Flags:** -
**Outcome:** BlockNeedUserTest - code + docs ready; on-device verification round. Full `Verified` is additionally gated on owner approval of the provisional matrix values.
**Counts:** PASS 24 · WARN 1 · FAIL 0 · MANUAL (device plan below) · EXEMPT 0

Feature is real and compiles (`standardDebug` BUILD SUCCESSFUL). Unit tests pass: `RealDeviceProfileDetectorTest` 7/7, `RealDeviceProfileRepositoryTest` 3/3, `ApplyProfilePresetUseCaseTest`. This readiness pass closed the prior FAILs:

- §11.13 - rationale documented in `dev/DEVICE_PROFILE_PRESET_MATRIX.md`, plus user-facing first-launch docs in QUICK_START / README / howto (EN/RU/UK).
- §11.15 - preset matrix design + field mapping + change rules documented in `dev/DEVICE_PROFILE_PRESET_MATRIX.md`.
- §11.16 - per-profile EN/RU/UK descriptions already shipped and wired into Welcome + Settings (confirmed correct, incl. `360°`); the earlier "missing" finding was wrong.
- §11.14 - unified vector profile icon set `ic_profile_*` (11) added with an asset registry in the matrix doc; now wired into a shared full-screen tile picker (`DeviceProfilePickerDialogFragment`, Welcome + Settings) that replaced the dropdown.
- Code WARN resolved - `RealDeviceProfileRepository` first-run bootstrap moved fully onto `Dispatchers.IO` (no constructor-thread disk reads).

### Remaining

1. **§6.1 owner-gate RESOLVED.** The preset matrix is now the owner-authored CSV asset
   `app_v2/src/main/assets/device_profile_presets.csv` (~90 settings × 11 profiles), parsed by
   `DeviceProfilePresetCsvDataSource` and applied by `DeviceProfilePresetApplier`. Verified on device:
   selecting TV / media box applied 83 overrides and persisted (grid mode, subfolders, etc.). The old
   provisional 5-field matrix is removed. Data caveats to fix in the CSV: `defaultIconSize=156` is not a
   valid slider step (use 152/160); 4 profiles (media/video/audio/ebook player) are seeded copies.
2. On-device pass of the remaining §11 criteria (§11.5 settings differ ≥3 profiles - now demonstrable;
   §11.9 Car/TV DPAD/keyboard/mouse). §11.8 done: VR profile is flavor-gated (hidden in non-VR builds,
   shown in vr/noLegal) via `DeviceProfileAvailability`; detector strengthened (UiModeManager + Chromebook).
3. 3 `/spec-update` structural proposals (P-1, P-2, P-3) remain `Proposed` (out of scope).

### Device Test Plan (BlockNeedUserTest)

Build/install `standardDebug`. Watch logcat for the `S0327:` probe tags - each firing proves its flow ran. Provisional preset effects are documented in `dev/DEVICE_PROFILE_PRESET_MATRIX.md`.

Fresh install:
- [x] First run: the auto-detected profile is highlighted (bright frame + "(Recommended)") AND pre-selected in the picker. Probe `S0327: welcome device-profile auto-detection`. Verified on device. (§11.1-11.3)
- [ ] Pick a profile, finish welcome. Probe `S0327: welcome device-profile save (isSkipped=false)` then `S0327: preset matrix apply (...)`. (§11.1)
- [ ] Fresh again, press Skip: auto profile applied. Probe `S0327: welcome device-profile save (isSkipped=true)`. (§11.4)
- [ ] Compare defaults across phone / TV / car / photo-frame: thumbnails / fullscreen / keep-awake / confirmations differ per the matrix. (§11.5)
- [ ] Pick `Other / Custom`: no settings overwritten. (§11 Other semantics)

Settings:
- [ ] Settings -> Interface -> Device profile shows current profile + source; changing shows the overwrite warning and requires confirm. Probe `S0327: settings device-profile change (...)` + `S0327: preset matrix apply (...)`. (§11.7)
- [ ] Car / TV profile screens fully usable via DPAD / keyboard / mouse, no touch-only dead ends. (§11.9)
- [x] §11.8 - VR profile hidden entirely in non-VR flavors (standard/lite/photos/legacy = 10 tiles; vr/noLegal = 11 incl VR). Flavor-isolated via `DeviceProfileAvailability`; verified on device.

Upgrade install (over a pre-S0327 build):
- [ ] Settings shows profile `Other / Другой`, previous settings preserved, no preset auto-applied. Probe `S0327: existing-install migration to OTHER profile`. (§11.11)

Trilingual:
- [ ] Profile titles + descriptions render correctly in EN/RU/UK. (§11.10, §11.16)
