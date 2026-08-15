# Стратегическая спецификация: S1002 - Расширение контракта компаньона до v2 (параметры ресурса при импорте)

**Ticket:** S1002
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-11
**Tier:** TBD
**Roadmap entry:** Ad-hoc - request 2026-07-11 18:10
**Tactical plan:** `PLAN/S1002_companion-config-v2-resource-params/INDEX.md`

<!-- auto-approved by /spec-all - 2026-07-11 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11 18:10

**Текст (владелец, дословно):**

> расшаривание папок копилотом работает
> нужно его усложнить
> ресурсы имеют имя, условия сканирования, тип медиа, настройка назначения, комментарий, пин код, интервал слайд шоу
> Всё это можно тоже импортировать от программы- копилота =- Нужно расширить версию передаваемого файла чтобы она была готова содержать эти параметры дляимпортируемых ресурсов, изменить импорт и дать мне спецификацию которую я должен буду выполнить на стороне программы-копилота чтобы там тоже можно было задать эти пареметры перед экспортом файла
> и штрихкода, как дополнительные вещи.
> В первую очередь речь о наитменовании ресурса и типе (аудиотека например)

**Трактовка:**

- Текущий импорт от компаньона (`.fmscfg`, контракт v1) создаёт «голый» SFTP-ресурс: имя = метка корня, фиксированный набор медиатипов, read-only, скан подпапок включён. Всё остальное - дефолты.
- Владелец хочет, чтобы обменный файл нёс полную конфигурацию ресурса и импортированный ресурс приезжал уже настроенным: имя, тип/профиль (например «аудиотека»), условия сканирования, настройки назначения, комментарий, пин-код, интервал слайд-шоу.
- Приоритет №1: наименование ресурса и тип (профиль «аудиотека» и т.п.). Остальные параметры - следом.
- «И штрихкода, как дополнительные вещи»: те же расширенные параметры должны проходить и через путь импорта по QR/штрихкоду (кнопка «Импорт по штрихкоду»), а не только через файл. QR - вторичный транспорт (ограничение по объёму), см. §6.
- Отдельный запрос: **дать спецификацию для стороны программы-копилота** - как там задать эти параметры перед экспортом файла. Это deliverable тикета.

**Вложения:** нет.

**Контекст на момент захвата:** отгружены S0421 (контракт `.fmscfg` v1 + импорт-пайплайн), S0984 (экспорт `.fmscfg` из живого ресурса + приём вложением), S0988 (импорт по QR), S0991 (кнопки «Импорт из файла / по штрихкоду» в Add resource). Windows-половина перенесена в «Fast Media Sorter for Windows» (репо `FastMediaSorter_Lite`); авторитетный контракт живёт в companion-репо `docs/CONFIG_FORMAT.md`, канонический вектор заморожен байт-в-байт на обоих концах.

---

## 1. Проблема

Обменный файл компаньона (`CompanionResourceConfig`, schemaVersion 1) описывает только транспорт: имя PC-шары, протокол, точки доступа, креды, отпечаток хоста и список корней (`virtualPath` + `label`). Каждый корень при импорте превращается в SFTP-ресурс с жёстко зашитыми дефолтами (`ImportCompanionConfigUseCase`):

- `supportedMediaTypes` = IMAGE+VIDEO+AUDIO+GIF (всегда),
- `scanSubdirectories = true`, `isReadOnly = true`,
- `comment = "Companion: <resourceName>"`,
- всё прочее (профиль, пин, интервал слайд-шоу, назначение, скан-флаги) - дефолты `MediaResource`.

Пользователь, настроивший на стороне компаньона «аудиотеку» с пином и своим интервалом слайд-шоу, при импорте теряет всю настройку и вынужден донастраивать ресурс вручную. `MediaResource`/`ResourceEntity` уже хранят все эти поля - не хватает только их переноса в контракте обмена и применения при импорте.

## 2. Цель

Расширить обменный контракт до **schemaVersion 2**: каждый корень (= один ресурс) может нести опциональные параметры ресурса. Импорт применяет их, отсутствие поля = поведение v1. Экспорт эмитит реальные параметры живого ресурса. Оба транспорта (файл `.fmscfg` и QR/штрихкод) идут через единый парсер - расширение DTO покрывает их автоматически. Отдельно - спека для стороны компаньона по заданию этих параметров перед экспортом.

## 3. Объём

**В объёме (Android):**

- Bump контракта: `schemaVersion` 1 -> 2; `SUPPORTED_SCHEMA_VERSION = 2`. v1-файлы принимаются как раньше (все новые поля опциональны, отсутствие = дефолт v1).
- Расширение `CompanionRootDto` опциональными полями (см. §5.1).
- `ImportCompanionConfigUseCase`: применить per-root параметры к `MediaResource`; профиль -> набор медиатипов/`allFiles`; назначение через существующий `addMultiple`.
- `ExportCompanionConfigUseCase`: эмитить реальные параметры ресурса в v2.
- Канонический вектор v2 (новый) + сохранить v1-вектор для теста обратной совместимости; тесты парсера/сериализатора.
- Deliverable: `COMPANION_EXPORT_SPEC.md` - контракт v2 + требования к UI компаньона для задания параметров и кодирования их в файл и QR.

**Вне объёма:**

- Room-миграция: не требуется - `ResourceEntity` уже хранит все целевые поля.
- Смена протокола (SFTP-only заморожен), мульти-хостовый файл, мульти-ресурсный экспорт из одного `ExportCompanionConfigUseCase` (сегодня один ресурс = один корень; модель v2 не запрещает мульти-корень при импорте).
- Реализация стороны компаньона (Go/VB) - её выполняет владелец по deliverable-спеке.
- Смена транспортного конверта `FMSCFG1:` (это версия конверта gzip+base64, не версия payload; остаётся заморожена).

### 3.3 Owner inputs (Approval gate)

- **Набор параметров v2:** имя (существующий `label`), профиль/тип, явные медиатипы (override), условия сканирования (`scanSubdirectories`, `showSubfoldersAsItems`, `showHiddenFiles`, `allFiles`), назначение (`isDestination` + опц. цвет), комментарий, пин-код, интервал слайд-шоу. Все опциональны, отсутствие = дефолт v1.
- **Приоритет:** имя + профиль («аудиотека») - обязательный минимум ценности; остальные - тем же механизмом.
- **Обратная совместимость:** старый app + v2-файл -> отказ «обновите приложение» (существующий guard `schemaVersion > SUPPORTED`); новый app + v1-файл -> принимается. Кросс-репный bump синхронный: companion `docs/CONFIG_FORMAT.md` + `schema_test.go` + канонический вектор обновляются в один шаг по deliverable-спеке.
- **Штрихкод (QR):** тот же v2-payload проходит через общий парсер (`importFromPayload`) - отдельной работы на Android нет; ограничение объёма QR - в §6, файл остаётся fallback.
- **Sensitive scope:** UI (диалог подтверждения импорта, путь экспорта), data/API (кросс-репный контракт schemaVersion). Строки пользовательские - при изменении диалога импорта пройти COMMUNICATION_POLICY + trilingual.
- **Related tickets:** S0421 (контракт `.fmscfg` v1 + импорт-пайплайн), S0984 (экспорт + приём вложением), S0988 (импорт по QR), S0991 (кнопки импорта в Add resource), S0046 (TOFU-пиннинг).

## 5. Предлагаемый подход

**Ключевая идея: не изобретать новую модель - `MediaResource`/`ResourceEntity` уже несут все поля; расширить только обменный DTO и маппинг импорта/экспорта, сохранив полную обратную совместимость с v1.**

### 5.1 Схема v2 (per-root)

Все поля добавляются в `CompanionRootDto` (каждый корень = один ресурс), все опциональны (nullable), отсутствие = дефолт v1:

- `profile` (string enum): тип/профиль ресурса. Токены: `none | audio_library | video_library | photo_storage | documents | all_files`. Маппинг 1:1 в `ResourceProfile`. Профиль задаёт пресет набора медиатипов и флагов (переиспользовать канонический пресет приложения, не дублировать).
- `mediaTypes` (string array, опц. override): явный набор медиатипов, если владелец хочет точнее профиля. Токены: `image | video | audio | gif | text | pdf | epub | office`. Присутствует -> перекрывает набор от профиля.
- `scanSubdirectories`, `showSubfoldersAsItems`, `showHiddenFiles`, `allFiles` (bool): условия сканирования.
- `isDestination` (bool) + `destinationColor` (int ARGB, опц.): настройка назначения.
- `comment` (string): комментарий (перекрывает дефолт `"Companion: <resourceName>"`).
- `accessPin` (string): пин-код доступа к ресурсу.
- `slideshowInterval` (int, секунды): интервал слайд-шоу.

Имя ресурса - существующий `label` (v1). Отдельного поля не вводим.

### 5.2 Импорт (расширение `ImportCompanionConfigUseCase`)

- Для каждого корня строить `MediaResource`, читая новые поля; при `null` - текущий дефолт v1 (набор медиатипов ALL, `scanSubdirectories=true`, `isReadOnly=true`, `comment="Companion: .."`).
- Профиль -> `supportedMediaTypes` + `allFiles` через общий пресет-маппер (домённый, переиспользуемый; извлечь, если сейчас логика только в UI-форме).
- `isDestination=true` -> ресурс должен быть записываемым: снять `isReadOnly` (назначение read-only бессмысленно; SFTP-шара должна допускать запись - предупредить в deliverable-спеке). Слоты назначения (макс. 10) и цвет по умолчанию уже раздаёт `addMultiple`.
- `accessPin`, `comment`, `slideshowInterval`, скан-флаги - применить напрямую.

### 5.3 Экспорт (расширение `ExportCompanionConfigUseCase`)

- В v2-DTO писать реальные значения ресурса: `profile`, `mediaTypes` (если не выводятся из профиля однозначно), скан-флаги, `isDestination`/`destinationColor`, `comment`, `accessPin`, `slideshowInterval`.
- `schemaVersion = 2`. Сегодня экспорт по-прежнему один ресурс = один корень.
- Пин/комментарий/пароль едут в файле - предупреждение при экспорте (уже есть для пароля S0984) распространить на пин.

### 5.4 Штрихкод / QR

- Путь QR (`CompanionQrScanActivity` -> `importFromPayload` -> тот же `CompanionConfigParser`) наследует v2 без изменений кода.
- Ограничение: QR (gzip+base64, конверт `FMSCFG1:`) имеет предел объёма (~2.9 КБ бинарных данных, версия/уровень коррекции). Многокорневой конфиг с полным набором параметров может не влезть - файл остаётся fallback. В deliverable-спеке для компаньона: предупреждать при переполнении QR и предлагать файл.

### 5.5 Совместимость и вектора

- `SUPPORTED_SCHEMA_VERSION = 2`. `validate()` принимает `schemaVersion in 1..2`; новые поля валидируются мягко (неизвестный токен профиля/медиатипа -> игнор с дефолтом, не INVALID_CONTENT, чтобы будущее расширение токенов не роняло импорт).
- Новый `canonical_vector_v2.json` (полный набор полей) + сохранить `canonical_vector.json` (v1) для теста «v1 всё ещё принимается». Оба замораживаются на обоих концах.

## 6. Открытые вопросы

1. **Объём QR при полном наборе параметров.** Многокорневой v2-конфиг может превысить ёмкость QR. Решение (дизайн): файл - основной транспорт полного конфига; QR несёт тот же payload, компаньон предупреждает при переполнении. Открыто: нужен ли на Android явный «слишком большой QR» UX или достаточно того, что компаньон просто не сгенерит QR - **вынести владельцу**, не блокирует Android-работу (импорт QR уже работает как есть).
2. **Пин-код в обменном файле.** Пин едет открытым в файле/QR (как пароль сегодня). Дизайн: зеркалить обработку пароля - предупреждение при экспорте, приватность файла на отправителе. Открыто: не отправлять ли пин по умолчанию (чекбокс, как «не включать пароль») - **вынести владельцу**.
3. **Назначение для read-only SFTP-шары.** `isDestination` подразумевает запись, а companion-шары в MVP отдаются read-only. Дизайн: `isDestination=true` снимает `isReadOnly`, ответственность за писабельность сервера - на владельце (нота в deliverable-спеке). Разрешено дизайном, не блокирует.

## 10. Связи

- S0421 - контракт `.fmscfg` v1 (заморожен), импорт-пайплайн. Изменение контракта требует синхронного bump companion-стороны (`docs/CONFIG_FORMAT.md`, `internal/config/schema_test.go`, канонический вектор) - покрыто deliverable-спекой.
- S0984 - экспорт `.fmscfg` из живого ресурса + приём вложением (расширяется до v2).
- S0988 - импорт по QR (наследует v2 без изменений).
- S0991 - кнопки «Импорт из файла / по штрихкоду» в Add resource.
- S0046 - TOFU-пиннинг host-key (переиспользуется как есть).

---

## Last Audit

**Date:** 2026-07-11 | **Verdict:** Verified (Android side) | **Method:** implementation + unit tests + scoped detekt.

### Delivered

- Contract v2: `CompanionConfigParser.SUPPORTED_SCHEMA_VERSION = 2`, `validate()` accepts `schemaVersion in 1..2`; v1 files still parse.
- `CompanionRootDto` extended with 11 optional per-root params (profile, mediaTypes, scan flags, isDestination, destinationColor, comment, accessPin, slideshowInterval). Positional order preserved (fields appended after `label`).
- `CompanionResourceTokens` - stable wire-token <-> enum maps (profile + mediaType), soft-null on unknown tokens.
- `ProfileMediaPreset` + `ResourceProfile.mediaPreset()` extracted in `Models.kt`; `ResourceFormData.applyProfile` refactored to delegate (single source of truth). Byte-identical behavior - `ResourceFormDataTest` green.
- Import (`ImportCompanionConfigUseCase.buildResource`): applies per-root params, v1 defaults on absence; profile -> media/flags; `isDestination` clears read-only.
- Export (`ExportCompanionConfigUseCase.buildRoot`): emits the resource's real params as v2; compact (nulls omitted).
- QR/barcode: inherits v2 via the shared parser (`importFromPayload`) - no code change.
- Vectors: new `canonical_vector_v2.json` (full param set) + retained v1 vector for the backward-compat test.
- Deliverable: `COMPANION_EXPORT_SPEC.md` (schema, canonical vector, import semantics, transports, UI requirements, security) for the companion side.

### Evidence

- `testStandardDebugUnitTest --tests "*CompanionConfig*" --tests "*ResourceFormData*"` -> BUILD SUCCESSFUL (parser v1+v2 parse, unknown-token soft-ignore, v2 serializer round-trip, applyProfile preserved).
- Scoped detekt gate over the 7 changed Kotlin files -> PASS (broad import/export catches use inline `@Suppress` matching the sibling companion files; parser now chains the original as `cause`).
- No Room schema change (all target fields already in `ResourceEntity`).

### Deferred / owner decisions (not defects)

- Export-time PIN warning + optional "exclude PIN" toggle (strategic §6 #2) - needs a portrait+landscape TextView, a trilingual string, and an owner call; deferred. Data-layer PIN round-trip already works.
- QR overflow UX on the Android side (§6 #1) - companion-side concern; import already works.
- Companion program implementation (Go/VB) - owner task, per `COMPANION_EXPORT_SPEC.md`.
- End-to-end device import of a v2 file is untestable until the companion emits v2; Android side is unit-verified.
- Minor: `ImportCompanionConfigUseCase.buildResource` mapping has no dedicated unit test (heavy DI deps) - covered by review + compile; token maps + parser exercised by tests.
