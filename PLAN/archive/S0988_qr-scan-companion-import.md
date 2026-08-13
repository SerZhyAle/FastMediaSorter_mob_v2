# Стратегическая спецификация: S0988 - QR-скан импорта companion `.fmscfg`

**Ticket:** S0988
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-11
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-11
**Tactical spec:** `PLAN/S0988_qr-scan-companion-import/` (будет создан через `/spec-tech`)

<!-- approved 2026-07-11 - owner resolved ADR-1 (ZXing) via /spec-all gate -->
<!-- auto-approved by /spec-all - 2026-07-11 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11

**Текст:**

Владелец указал на контракт `P:\WINDOWS\FastMediaSorter_Lite\SPECIFICATION_QR_IMPORT_ANDROID.md` (frozen schemaVersion 1, живёт в Lite-репо - это handoff-пакет, упомянутый в заметке статуса S0421) и выбрал "завести Draft на QR-скан".

Захваченная эвиденс-сверка приложения против контракта (2026-07-11):

- Android-половина `.fmscfg`-импорта уже реализована и соответствует контракту: S0421 (companion import, Android-часть DONE, тикет `BlockExternal`), S0984 (share / import вложения, `BlockNeedUserTest`), S0046 (host-key TOFU pinning SHA256).
- `CompanionConfigParser` уже декодирует ОБА транспорта: plain JSON (`{`) и compressed `FMSCFG1:` = base64(std) + gzip - именно compressed-вариант LITE делает для плотных QR (>900 байт). Валидация схемы v1 полная (reject schemaVersion>1, protocol!=sftp, пустых accessPaths/roots, порт 1..65535, virtualPath начинается с `/`). KDoc парсера прямо говорит "QR string or `.fmscfg` file content".
- Точки входа импорта - ТОЛЬКО файловые: SAF-пик в add-resource (`AddResourceCompanionCoordinator.importFromUri`) и вложение из Telegram/email через ACTION_VIEW/SEND (`CompanionConfigImportActivity`). Оба вызывают `CompanionConfigParser.parse(...)`.
- Единственный gap vs контракта §0/§1: **нет QR-сканера камерой**. Поиск по каталогу спеков по "QR" даёт 0 тикетов.
- Осознанное расхождение (не конфликт): контракт §2 помечает `password` и `hostKeyFingerprintSha256` как required, а Android ослабил оба до опциональных (S0984: passwordless-share + no-pin TOFU). Контракт всё равно всегда шлёт оба.

Объём (по запросу владельца): экран сканирования камерой → сырая строка QR → существующий `CompanionConfigParser.parse(payload)` → существующий confirm-диалог / `ImportCompanionConfigUseCase`. Весь downstream уже готов; добавляется только сам скан. Переиспользовать существующий CameraX + ML Kit пайплайн (Camera OCR); ML Kit Barcode Scanning. Флейворы - как у companion-импорта: standard / photos / legacy / vr / noLegal.

**Вложения:**
- Замороженный контракт QR/`.fmscfg` импорта (Android-сторона), schemaVersion 1, копия из Lite-репо на 2026-07-11 - `PLAN/S0988_qr-scan-companion-import/attachments/01__qr-import-android-contract.md`

**Захвачено во время:** сверки контракта, инициированной владельцем (связано с S0421)

---

## 1. Проблема

Companion-конфиг `.fmscfg` сейчас можно импортировать только как файл: выбор через SAF на экране добавления ресурса либо открытие вложения из Telegram/email. Замороженный кросс-репо контракт делает QR равноправным способом приёма - LITE рисует QR прямо в своём окне. Пользователь, у которого QR на экране ПК, не может отсканировать его в приложении и вынужден обходным путём сохранять или пересылать файл. Область - добавление ресурса / companion-импорт (Network & Cloud).

---

## 2. Цели

1. Сканирование companion-QR камерой в приложении создаёт SFTP-ресурс(ы) без единого файлового шага.
2. Отсканированная строка проходит через существующие парсер, валидацию, confirm-диалог и use-case импорта без изменений.
3. Оба транспорта - plain JSON и `FMSCFG1:`-compressed - принимаются из скана (плотный QR приходит сжатым).
4. Точка входа обнаружима рядом с существующим действием файлового companion-импорта.

**Non-goals:**

- Новые поля wire-контракта не вводятся; schemaVersion остаётся 1.
- Файловый и attachment-пути импорта не трогаются.
- Универсальное сканирование штрих-кодов вне companion-QR не добавляется.
- Генерация QR на Android не делается - экспорт остаётся за LITE.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Тумблер фонарика на экране скана (тёмные помещения).
2. Тактильный/звуковой отклик при успешном декодировании.
3. Резервная вставка строки из буфера обмена, если камера недоступна.

### 3.2 Жёсткие ограничения

- **Flavor:** standard, photos, legacy, noLegal. Не lite (вне companion-импорта). VR - см. §9 ADR-2.
- **API level:** minSdk 26 (standard) / 23 (legacy); CameraX 1.5.3 уже подключён во всех флейворах.
- **Wear OS:** не затрагивается.
- **Производительность:** камера - один владелец; превью и анализатор кадров освобождаются немедленно при уходе/паузе (Rule 18). Скан ограничен по времени, без утечки сессии.
- **Совместимость данных:** нет - персистентная схема не меняется, переиспользуется путь импорта.
- **Локализация:** EN/RU/UK - обязательно.
- **Доступность:** при отказе в разрешении камеры - rationale; точка входа focusable, D-pad/мышь; отличие не только цветом.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0421 (родитель, downstream парсер/use-case), S0984 (share/import вложения), S0046 (host-key TOFU pinning)
- **Dependency choice (ADR-1):** РЕШЕНО (владелец, 2026-07-11) - ZXing (`com.google.zxing:core`). Pure-JVM, без native/GMS, единообразно на всех флейворах.
- **Flavor scope (ADR-2):** VR исключён из точки входа скана (камера Quest недоступна CameraX); файловый/attachment-импорт для VR сохраняется.

### 3.3.1 Sensitive-scope owner inputs

- **UI placement:** новая кнопка "Сканировать QR" рядом с существующей `btnSftpImportCompanion` на экране добавления ресурса.
- **Flavor gating:** точка входа скана присутствует на standard/photos/legacy/noLegal, отсутствует на lite и vr.
- **New dependency:** одна decode-библиотека (ZXing либо ML Kit barcode) - см. ADR-1.
- **Camera permission:** новое разрешение НЕ добавляется - `CAMERA` уже объявлен (S0359) и является обязательным для всех камер-путей приложения.

---

## 4. Контекст текущей архитектуры

Companion-импорт сегодня состоит из парсера с полной валидацией, use-case импорта (маппит конфиг в SFTP-ресурсы с шифрованием пароля и TOFU-пином) и confirm-диалога. Приём конфига идёт двумя файловыми путями: SAF-пик на экране добавления ресурса и приём вложения через системный intent. Оба сводятся к одному вызову парсера строкой/байтами.

In-app камера уже есть: S0359 сделал CameraX единственным путём захвата, `CAMERA` объявлен в манифесте и обязателен. Существуют паттерны сессии CameraX и анализа кадров (Camera OCR использует ImageAnalysis). Чего нет - компонента, превращающего живой кадр камеры в строку companion-payload; поэтому QR со схемой ПК сейчас в приложение попасть не может.

---

## 5. Предлагаемый подход

Тонкий слой скана поверх готового downstream: экран с превью камеры и анализатором кадров декодирует QR в строку, которую отдаёт в существующую точку companion-импорта. Разрешение, валидация, confirm-диалог и импорт переиспользуются как есть.

### 5.1 Основные столпы / модули

1. Поверхность скана - превью камеры плюс анализатор кадров, эмитит декодированную текстовую строку и завершается на первом успехе.
2. Мост приёма - принимает строку и передаёт в существующий вход companion-импорта (тот же, что у файлового пути после чтения байт).
3. Переиспользование - существующий поток разрешения камеры, confirm-диалог и use-case импорта без модификаций.

### 5.2 Потоки данных и событий

Экран добавления ресурса → поверхность скана → анализатор кадров декодирует QR → строка payload → существующий парсер/валидация → существующий confirm-диалог → существующий use-case импорта → ресурс(ы) добавлены. Compressed-транспорт и все проверки уже покрыты downstream - слой скана транспортно-нейтрален.

### 5.3 Точки расширяемости

- Анализатор развязан с транспортом (транспорт решается ниже) - будущие форматы payload не трогают скан.
- Поверхность скана переиспользуема под любой будущий приём строки (не только companion).
- Точка входа аддитивна - файловый и attachment-пути не меняются.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. (Выбор decode-библиотеки закрыт: ZXing - владелец, 2026-07-11, см. ADR-1.)

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Отказ в разрешении камеры | Средняя | Скан недоступен | Переиспользовать rationale-поток; резервная вставка из буфера (§3.1) |
| Камера VR недоступна | Высокая | Скан не работает на Quest | Исключить точку входа скана на VR (ADR-2); оставить файловый импорт |
| Плотный QR не читается | Низкая | Импорт не проходит | LITE уже отдаёт compressed для >900 байт; библиотека декодирует строку, downstream распаковывает |
| Повторное срабатывание декодера | Средняя | Двойной импорт | Debounce - завершать по первому валидному результату |
| ML Kit barcode тянет native-модель (если выбран) | Средняя | Риск класса S0386 на API36 | Предпочесть ZXing (pure-JVM, без native/GMS) - ADR-1 |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая пользовательская возможность. Черновой текст для docs/FEATURES + _RU + _UK:

- EN: Scan a companion QR code with the camera to add a shared PC folder as an SFTP resource in one step - no file needed.
- RU: Отсканируйте QR companion-приложения камерой, чтобы добавить общую папку ПК как SFTP-ресурс в один шаг - без файла.
- UK: Відскануйте QR companion-застосунку камерою, щоб додати спільну теку ПК як SFTP-ресурс за один крок - без файлу.

---

## 9. Архитектурные решения (ADR)

**ADR-1 - библиотека декодирования QR.** РЕШЕНО (владелец, 2026-07-11) - **ZXing** (`com.google.zxing:core`) плюс анализатор кадров CameraX (без старого camera1-стека `zxing-android-embedded`). Обоснование: pure-JVM, без native-модели и без GMS, одинаково работает на всех флейворах, включая photos (где ML Kit сейчас нет вовсе) и любые сборки без Google-сервисов; обходит класс отказов S0386 (native-attach de-bundled ML Kit падает на реальном arm64/API36). Отклонённая альтернатива - ML Kit barcode-scanning (bundled): +~2.5 МБ на флейвор и первое появление ML Kit в photos.

**ADR-1a - интеграция ZXing.** Использовать только `com.google.zxing:core` (декодер) + собственный CameraX `ImageAnalysis`-анализатор: кадр YUV → `PlanarYUVLuminanceSource` → `BinaryBitmap` → `MultiFormatReader` с hint `QR_CODE`. НЕ подключать `journeyapps:zxing-android-embedded` - он тянет собственный camera1-стек и конфликтует с CameraX. Зависимость - общий `implementation` (pure-JVM ~0.5 МБ), чтобы `src/main` компилировался на всех флейворах (Rule 14); видимость точки входа гейтится на UI-слое, не на уровне зависимости.

**ADR-2 - VR исключён.** Quest не отдаёт passthrough-камеры стандартным CameraX/camera2 (приватность платформы), поэтому сканер камерой на VR технически не функционирует. VR сохраняет файловый и attachment-импорт; точка входа скана на VR отсутствует.

**ADR-3 - точка входа.** Кнопка скана рядом с `btnSftpImportCompanion` на экране добавления ресурса - переиспользует существующий раздел companion-импорта, аддитивно.

---

## 10. Связи с другими спеками

- S0421 - родитель (companion-импорт, downstream парсер/use-case, тикет `BlockExternal`).
- S0984 - share/import вложения (`BlockNeedUserTest`), общий парсер и confirm-диалог.
- S0046 - host-key TOFU pinning, применяется к созданному ресурсу.
- S0359 - in-app CameraX и обязательный `CAMERA` (снимает вопрос нового разрешения).

---

## 11. Критерии готовности (strategic-level)

1. С экрана добавления ресурса скан companion-QR (plain и compressed) создаёт SFTP-ресурс(ы) без файлового шага.
2. Невалидный или чужой QR отклоняется существующей ошибкой парсера, без краша.
3. Камера освобождается при уходе с экрана; отказ в разрешении показывает rationale и не сканирует.
4. Точка входа скана отсутствует на lite и vr, присутствует на standard/photos/legacy/noLegal.
5. Сборка standard debug (и затронутых флейворов) проходит; строки трёхъязычны EN/RU/UK.

---

## Last Audit

**Дата:** 2026-07-11
**Статус:** Implemented -> BlockNeedUserTest (нужен device-тест: скан реального QR)
**Метод:** self-review по CODE_AUDIT_PROTOCOL + сборка + статические гейты + diff-scoped detekt.

**Код-ревью по слоям:**
- Lifecycle / listener symmetry: камера через `LifecycleCameraController.bindToLifecycle(activity)` - авто-стоп ниже STARTED, полный release в `onDestroy` (`unbind()` + shutdown executor). `add*Listener`-токенов в изменённых файлах нет (гейт listener-symmetry PASS).
- Concurrency: декодирование на выделенном single-thread executor, `@Volatile decoded` single-hit, callback -> main через `runOnUiThread`. Нет main-thread decode, нет гонки за `MultiFormatReader`.
- Resource ownership (Rule 18): каждый `ImageProxy` закрывается (finally), `STRATEGY_KEEP_ONLY_LATEST`, один владелец камеры.
- Permission: нового manifest-разрешения нет (`CAMERA` уже объявлен S0359); рантайм-запрос, при отказе - rationale + файловый fallback.
- Correctness: транспорт-нейтральный скан -> общий `CompanionConfigParser.parse` (plain + `FMSCFG1:`); чужой QR -> ошибка парсера -> toast, без краша; double-import защищён дважды (анализатор + activity).
- Flavor / Rule 14: ZXing - общий `implementation`, код в `src/main` компилируется на всех флейворах; VR-исключение реализовано рантайм-гейтом `FEATURE_CAMERA_ANY`, а не флейвор-гардом (скрывает кнопку на Quest и любом camera-less устройстве).
- UI/a11y: кнопки focusable/clickable (D-pad/мышь), инсеты через fitsSystemWindows, без hex (Rule 19).

**Evidence (гейты/сборка):**
- `assembleStandardDebug`: BUILD SUCCESSFUL (APK v2.60.7101.516).
- `fk` финального дерева: BUILD SUCCESSFUL.
- Статические гейты (neuroslop, flavor-flags, deprecated-pm, ticket-log, orientation, listener-symmetry): PASS.
- detekt diff-scoped на 8 изменённых файлов: CLEAN. Два pre-existing generic-catch в companion-import (`invoke(uri)`, `readConfig`) подавлены `@Suppress("TooGenericExceptionCaught")` с обоснованием - это import-boundary guard'ы, поверхностились diff-scoped гейтом.
- Строки EN/RU/UK: parity OK (4 ключа).
- Полный `:app_v2:detekt` остаётся красным из-за параллельной сессии S0962 (VR Cinema) + wear + прочего pre-existing долга - это не файлы S0988.

**Остаточное (device-gated):**
- Реальный скан QR на устройстве (эмулятор не читает реальный QR с экрана ПК) - гейт BlockNeedUserTest.
- Release/minified proof отложен до релизной сборки (нового reflection нет, риск низкий).

**Parity note:** QR-импорт идёт напрямую -> toast, как соседний файловый путь add-resource (там confirm-диалога не было); confirm-on-QR - возможный follow-up, не блокер.
