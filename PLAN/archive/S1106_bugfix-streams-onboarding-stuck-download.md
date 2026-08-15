# Спецификация (compact bugfix): S1106 - Онбординг: Streams виснет на «Downloading.. 0%»

**Ticket:** S1106
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-18
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-18

**Текст:**

ПРограмма на попытке включить стримы при инсталяции (вечная загрузка 0)
это происходит сейчас на подглюченном эмуляторе - собери оттуда логи и выясни причин

**Вложения:**
- Скриншот онбординга: тумблер Streams включён, статус «Downloading.. 0%», indeterminate-бар (снят 04:00) - `PLAN/S1106_bugfix-streams-onboarding-stuck-download/attachments/01__streams-stuck-0pct-04-00.png`
- Скриншот после свежего пере-дёргивания тумблера + 35с ожидания: всё ещё «Downloading.. 0%», бар анимируется (корутина жива) - `PLAN/S1106_bugfix-streams-onboarding-stuck-download/attachments/02__streams-stuck-0pct-after-retoggle-35s.png`

---

## 1. Проблема / симптом

На экране онбординга (WelcomeActivity, страница «What should the app do?») включение тумблера **Streams** оставляет статус на «Downloading.. 0%» неопределённо долго - импорт встроенного каталога стримов не завершается ни успехом, ни ошибкой.

**Где наблюдалось:**
- Устройство: эмулятор `emulator-5554`, Android 17 (SDK 37, preview), «подглюченный» (по словам владельца).
- Экран: `com.sza.fastmediasorter.debug/…ui.welcome.WelcomeActivity`, страница функциональности.
- Flavor: standard-debug.

**Эвиденс (собран 2026-07-18, 03:54-04:03):**
- Скриншоты в 03:53, 04:00, 04:02 - статус неизменно «Downloading.. 0%». Между первым и третьим кадром прошло ~9 минут; при 30-секундном `callTimeout` импорт давно должен был упасть в «failed».
- Свежий тест: очищен logcat, тумблер Streams пере-дёрнут OFF→ON, ожидание 35 с (за пределом `callTimeout`). Результат: бар всё ещё анимируется на «0%», статус не сменился на done/failed.
- Отклонённый фактор (реплика владельца): владелец после включения стримов нажал «Enable gestures» и предположил связь. Изолированный ре-тест это исключает - тумблер gestures оставался ВЫКЛ (видно на обоих скриншотах), пере-дёрнут только Streams, зависание всё равно воспроизвелось. Виснет сам импорт каталога, независимо от gestures.
- Логи: за всё время наблюдения процесс приложения (PID 23040) не выдал **ни одной** строки об импорте - ни сети, ни ошибки, ни таймаута. В logcat только Skia-варнинги системного UI (pid 527, `AGTM parsing failed`), к делу не относятся.
- Сеть эмулятора исправна: `ping 8.8.8.8` 0% loss; DNS резолвит `github.com`, `codeload.github.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com`; латентность высокая (~500 мс/пакет).
- Целевой asset существует и доступен: `https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip` → HTTP 302 → `release-assets.githubusercontent.com`, HTTP 200, `Content-Length: 2561714` (~2.5 МБ).

**Первичные наблюдения по коду (не финальный диагноз):**
- `welcome_func_downloading, 0` - процент **захардкожен нулём** для стримов ([WelcomeFunctionalityController.kt:322](../app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFunctionalityController.kt#L322)). У стримов нет реального прогресса (в отличие от OCR/Translation, у которых `DownloadProgress.Running(percent)`), бар `isIndeterminate = true`. То есть «0%» - это не залипший счётчик, а константа на весь период загрузки; сам по себе слабый UX.
- Импорт запускается разовым suspend-вызовом `importStreamCatalogUseCase()` на `owner.lifecycleScope`; статус меняется только по возврату результата ([WelcomeFunctionalityController.kt:320-333](../app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFunctionalityController.kt#L320-L333)). Пока вызов не вернулся - UI навсегда на «0%».
- `ImportStreamCatalogUseCase` ставит `callTimeout = 30 c` на производном OkHttp-клиенте ([ImportStreamCatalogUseCase.kt:106-108](../app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamCatalogUseCase.kt#L106-L108)), но визуально этот таймаут зависание не ограничивает.
- `reset()` отменяет `streamsCatalogJob` на каждом ре-байнде страницы ([WelcomeFunctionalityController.kt:83](../app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFunctionalityController.kt#L83)) - на «глючном» эмуляторе с частыми пересозданиями Activity импорт может отменяться и рестартовать с нуля, не успевая завершиться (гипотеза, требует проверки).

---

## 2. Корневая причина

**Наиболее вероятно (по чтению кода; окончательно - по новым логам на устройстве):** зависание в пост-обработке импорта, не покрытой `callTimeout`. `callTimeout` (30 с) стоит только на OkHttp-вызове внутри `downloadCatalog()`; шаги ПОСЛЕ его возврата - `parser.parse`, `faviconAtlasStore.write`, `repository.mergeCatalog` (ImportStreamCatalogUseCase.kt:43-85) - без дедлайна. Зависание в любом из них даёт бесконечную «0%», анимирующийся бар (корутина жива), ни таймаута, ни ошибки; согласуется с «35 с без срабатывания 30-с таймаута».

**Усиливающий фактор (делает баг невидимым):** `invoke()` не логирует ни старт, ни успех - только `Timber.w` на ошибке. Отсюда ноль строк об импорте в logcat.

**Вторичная гипотеза:** `bind()` отменяет `streamsCatalogJob` на каждом байнде (WelcomeFunctionalityController.kt:83); на глючном эмуляторе с частыми пересозданиями импорт мог отменяться до завершения. Отделяется от «зависшего пост-шага» по логам (повторные `starting` без `done`). Вклад preview-среды не исключён.

Исходные кандидаты (сохранены как контекст):
1. `callTimeout` не ограничивает реальную длительность - проверить, покрывает ли он чтение тела через `ZipInputStream` внутри `response.use{}` на данной версии OkHttp/Android 17, и не теряется ли дедлайн при пере-запуске корутины.
2. Зависание после `.execute()` - в пост-обработке: чтение тела, `faviconAtlasStore.write()` или `repository.mergeCatalog()` (DB/IO), где нет таймаута.
3. Повторная отмена/рестарт `streamsCatalogJob` при пересозданиях WelcomeActivity на глючном эмуляторе - импорт никогда не доходит до конца.
4. Вклад среды: preview-эмулятор Android 17/SDK 37 + высокая латентность. Нужно воспроизвести на реальном устройстве / чистом эмуляторе, чтобы отделить баг приложения от глюка среды.

---

## 3. Исправление

**Безопасное упрочнение (корректно при любой гипотезе §2); точный диагноз даст логирование:**

1. Логирование в `ImportStreamCatalogUseCase.invoke()`: `Timber.i` на старте («Stream catalog import: starting») и на успехе (счётчики added/updated/removed). Повторные `starting` без завершения = диагностика причины (§4).
2. Наблюдаемый дедлайн на уровне контроллера: обернуть `importStreamCatalogUseCase()` в `withTimeout(<деадлайн>)` в `startStreamCatalogImport`; по таймауту - `Failure`, UI уходит в «failed». Ограничивает ВЕСЬ импорт (сеть + parse + atlas + merge), а не только сетевой шаг.
3. Честный статус: убрать захардкоженный «0%» (`welcome_func_downloading, 0`) - у стримов нет реального прогресса, бар indeterminate; новый ключ `welcome_streams_catalog_downloading` («Загрузка..», EN/RU/UK).

Не входит в объём (следствие, не подтверждённая причина): вынос импорта из страница-скоупа в `@ApplicationScope` (доступен в контроллере) с наблюдением результата - отдельный follow-up, если логи подтвердят отмену пересозданием.

Исходные направления (сохранены как контекст):
- Ограничить импорт жёстким наблюдаемым дедлайном на уровне use case/UI (`withTimeout`), чтобы онбординг гарантированно уходил в «failed» с понятным сообщением, а не висел бесконечно.
- Добавить реальный прогресс или честный indeterminate-статус (не захардкоженный «0%») для загрузки каталога стримов.
- Логировать старт/успех импорта (сейчас логируется только `Timber.w` на ошибке) - иначе зависание невидимо в диагностике.
- Устойчивость к пересозданию Activity: не перезапускать импорт с нуля на каждом ре-байнде; вынести его из `lifecycleScope` привязанной страницы туда, где он переживёт recreation (например `appScope`/worker), с корректной привязкой прогресса.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0570 (импорт каталога стримов), S0668 (favicon-atlas в том же zip), S0925 (гварды доставки favicon). Проверить актуальность при переходе Draft → Approved.

---

## 4. Проверка

**On-device (после фикса):** чистая установка -> онбординг -> включить Streams. Ожидаемо: в logcat `Stream catalog import: starting`, затем в разумный срок `done added=..` либо `failed`; UI уходит из «Загрузка..» в done/failed, не залипает на бесконечной «0%».

**Диагностика причины по логам:** повторные `starting` без `done`/`failed` -> follow-up на вынос импорта из страница-скоупа; одиночный `starting` без завершения до дедлайна -> зависал пост-шаг (parse/atlas/merge), теперь ограничен `withTimeout`.

Исходные наметки (сохранены как контекст):
- Повтор on-device: чистая установка → онбординг → включить Streams → импорт завершается (done) или падает в «failed» с сообщением в разумный срок; UI не залипает на «0%».
- Симуляция медленной/оборванной сети → гарантированный «failed» по дедлайну.
- Проверка на реальном устройстве и на чистом (не-preview) эмуляторе для отделения от глюка среды.

---

## Last Audit

### Manual / on-device
- [x] Clean install -> onboarding -> "What should the app do?" -> Streams ON -> import resolves (done/failed) within ~90s, never stuck on an infinite "Downloading.. 0%" bar - verified on-device 2026-07-18

**Device run (2026-07-18, `/spec-test-device`):** emulator-5554 (Pixel 4, Android 17 preview - the exact repro env from §1), standard-debug 2.60.7181.800.

- **Verdict: PASS.**
- Expected: honest indeterminate "Downloading.." (no hardcoded 0%); import resolves to done/failed within the 90s UI deadline; UI never hangs on an infinite bar.
- Actual: status resolved to "Catalog downloaded" (`welcome_streams_catalog_done`) in ~4.08s; the "Downloading.. 0%" infinite-hang regression is gone.
- Log pattern = `starting` then `done` (import completes now; the original hang was environment/flaky preview, not a deterministic app defect):
  - `D/WelcomeFunctionalityController: S1106: streams onboarding catalog import started` (probe, valid D-level - build is current)
  - `I/ImportStreamCatalogUseCase$invoke: Stream catalog import: starting` @ 18:05:10.488
  - `I/ImportStreamCatalogUseCase$invoke: Stream catalog import done: +2691 ~0 -0` @ 18:05:14.563
- Exactly one `starting` -> one `done`; no repeated `starting` (recreation-cancellation hypothesis did NOT manifest), no `UI deadline exceeded`, no failure/timeout. Import too fast (~4s) to snapshot the intermediate "Downloading.." a11y state; honest-string path confirmed via code + resource.
- Evidence: `temp/S1106/mobile_test_scenario_20260718_1801.md`, `temp/S1106/run_20260718_1801.log` (lines 3082/3085/3155), `temp/S1106/screens/step_streams_done.png`.
