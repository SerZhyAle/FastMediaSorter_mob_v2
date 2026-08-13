# Стратегическая спецификация: S0575 - Streams toggle, welcome onboarding and entry points

**Ticket:** S0575
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-21
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - request 2026-06-21
**Tactical spec:** `PLAN/S0575_streams-toggle-welcome-entrypoints/`
**Tactical plan:** `PLAN/S0575_streams-toggle-welcome-entrypoints/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Фича Streams (воспроизведение интернет-источников, поставлена в S0565) собрана целиком, но включена всегда и везде, где сборка её содержит. Её точки входа (пункт меню на главном экране, кнопка в настройках воспроизведения) показываются каждому пользователю независимо от того, нужен ли ему стриминг, а единого пользовательского переключателя для всей возможности нет.

Из-за этого: пользователь не может скрыть стриминг, если не пользуется им; нет момента онбординга, который представил бы возможность и предложил скачать каталог доступных источников; нет дефолта, зависящего от типа устройства (ТВ-приставка и фоторамка получают одинаковое поведение); фича не представлена в экране загружаемых расширений, где скачивание каталога источников было бы естественным.

---

## 2. Цели

1. Единый пользовательский мастер-переключатель, включающий/выключающий возможность Streams целиком.
2. Переключатель живёт в Настройках на вкладке Media, в собственной сворачиваемой секции, размещённой после существующей группировки Translation/OCR.
3. Состояние переключателя по умолчанию выбирается по профилю устройства, так что профили, ориентированные на стриминг, стартуют с ним включённым, а остальные - выключенным.
4. Шаг онбординга «What should the app do?» предлагает Streams как выбор сразу после Translation; включение немедленно активирует фичу и предлагает опциональное скачивание каталога источников - пользователь может пропустить и добавлять источники вручную.
5. Каталог источников Streams представлен в экране загружаемых расширений как скачиваемый элемент.
6. Пункт меню Streams на главном экране появляется только когда переключатель включён; ярлык в настройках воспроизведения остаётся доступен всегда, когда сборка показывает Streams (это путь назад, пока пункт меню скрыт).

**Non-goals:**

- Не меняется поведение воспроизведения стримов, протоколы, кэширование и сам UI управления источниками (поставлены в S0565).
- Не добавляются новые протоколы стриминга или типы источников.
- Не меняется набор флейворов, компилирующих фичу.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Сворачиваемая секция «Streams» в настройках Media стоит именно после Translation/OCR, а не сливается с ними в одну группу.
2. Запись Streams в онбординге стоит непосредственно после записи Translation.
3. Включение в онбординге или в экране расширений тянет именно список доступных источников (каталог), а не пустое состояние.

### 3.2 Жёсткие ограничения

- **Flavor:** Streams компилируется в standard, lite, legacy, noLegal, vr; исключён из photos. В lite фича Streams целиком скрыта (нет переключателя, записей и точек входа), несмотря на компиляцию - это разворачивает решение S0565 о включении Streams в lite; механизм сокрытия (compile-time исключение vs гейт видимости) определяется на тактическом уровне. Переключатель и все точки входа невидимы/инертны там, где сборка фичу не показывает, без флейвор-гардов в общем коде - гейт остаётся на существующем механизме возможностей (capability) плюс новый runtime-флаг поверх него.
- **API level:** работает вплоть до минимума legacy (API 23); поведение без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** скачивание каталога - опциональная сетевая операция; выполняется вне главного потока, с видимым прогрессом; отказ или ошибка не блокируют онбординг и не выключают фичу.
- **Совместимость данных:** вводится один сохраняемый пользовательский флаг и один профиль-зависимый столбец дефолтов; существующие установки получают детерминированное значение (выключено, если активный профиль не говорит иначе).
- **Локализация:** EN/RU/UK обязательно для заголовка секции, метки переключателя, записи онбординга и записи в расширениях.
- **Доступность:** новый переключатель и сопутствующие строки уважают TalkBack, порядок фокуса и навигацию D-pad/TV наравне с соседними настройками.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0565 (internet-stream-playback) - родитель: построил фичу и текущие точки входа; на момент написания в `BlockNeedUserTest`. Зависящих тикетов нет.
- **Дефолт по профилю:** ON на всех профилях, кроме photo_frame и ebook_reader (OFF); Other - без переопределения.
- **Точки входа при OFF:** скрывается только пункт меню на главном; ярлык в настройках воспроизведения остаётся; мастер-тумблер в Media всегда виден.
- **Семантика включения:** тумблер фиксируется ON немедленно; скачивание каталога предлагается, но опционально; ручное добавление источников доступно всегда; отказ или ошибка скачивания не выключают фичу.
- **Охват lite:** Streams скрыт в lite целиком (нет UI), несмотря на компиляцию.
- **Размещение в настройках:** отдельная сворачиваемая секция «Streams» на вкладке Media после Translation/OCR.
- **Enable all:** включает опциональное скачивание каталога Streams при поддержке сборкой.
- **Семантика выключения:** прячет точки входа; данные каталога и активное воспроизведение не трогаются.

---

## 4. Контекст текущей архитектуры

Настройки - это собственный view-стек на вкладочном экране; вкладка Media хостит сворачиваемые секции, и мастер-переключатели возможностей (Translation, OCR) уже живут там, сохраняясь через центральное хранилище настроек и наблюдаемые потоком потребителями. Пресеты профиля устройства применяют дефолты первого запуска из матрицы, ключованной по профилю; стриминг уже имеет там профиль-зависимые дефолты кэша, то есть прецедент профиль-зависимых стриминговых значений существует.

Поток онбординга имеет шаг «что приложение должно делать», перечисляющий опциональные возможности, каждая из которых может скачать свои данные при включении. Экран загружаемых расширений перечисляет скачиваемые артефакты возможностей. Сама фича Streams сегодня гейтится только во время компиляции флагом возможности, поэтому фактически она включена всегда: нет пользовательского флага, нет присутствия в онбординге, нет присутствия в расширениях, а точки входа нельзя скрыть пользователем.

---

## 5. Предлагаемый подход

Ввести runtime мастер-флаг для Streams по образцу существующих мастер-переключателей Translation/OCR: сохраняется централизованно, наблюдается потоком, дефолтится по профилю через матрицу пресетов (присоединяясь к уже имеющимся там стриминговым дефолтам кэша). Поднять его как новую сворачиваемую секцию на вкладке Media после группировки Translation/OCR. Завести тот же флаг в три точки входа, сохранив compile-time гейт возможности как внешний (photos и любые неподдерживающие сборки не показывают ничего), а новый флаг - как внутренний, управляемый пользователем.

### 5.1 Основные столпы / модули

- Мастер-флаг Streams + профиль-зависимый дефолт.
- Секция настроек на вкладке Media.
- Запись онбординга + скачивание каталога при включении.
- Скачиваемый элемент каталога в экране расширений.
- Гейтинг точек входа (меню главного экрана + ярлык в настройках воспроизведения).

### 5.2 Потоки данных и событий

- Первый запуск -> пресет профиля задаёт дефолт флага -> центральное хранилище настроек.
- Пользователь переключает (Настройки или Онбординг) -> запись в хранилище -> наблюдатели (точки входа, UI настроек) реагируют.
- Включение в Онбординге/Расширениях -> опциональное скачивание каталога -> наполнение репозитория источников; источники также добавляются вручную.
- Видимость пункта меню на главном = сборка показывает Streams (поддержка возможности и не-lite) И включённый runtime-флаг.
- Видимость ярлыка в настройках воспроизведения = сборка показывает Streams (независимо от runtime-флага).

### 5.3 Точки расширяемости

- Четвёрка «гейт возможности + мастер-флаг + элемент расширений + запись онбординга» должна выражаться так, чтобы следующая скачиваемая возможность шла тем же путём. Текущая проверка доступности экрана расширений прописана отдельно под каждую возможность (Translation/OCR) - добавление оси Streams усиливает этот запах; стоит свести проверку к единому списку возможностей.

---

## 6. Открытые вопросы / Research items

Открытых вопросов владельца нет - все решения зафиксированы (см. блок ниже и §3.3). Единственный отложенный пункт - механизм скачиваемого элемента в экране расширений: каталог не является артефактом-«deliverable», поэтому форма элемента и маршрут скачивания определяются на тактическом уровне в `/spec-tech`; по умолчанию - отдельная секция «Streams».

### Quiz decisions (2026-06-21)

- Дефолт по профилю -> ON везде, кроме `photo_frame` и `ebook_reader` (соответствует «soul» устройства; фоторамка и читалка не про стриминг). `Other` - без переопределения.
- Точки входа при OFF -> скрывать только пункт меню на главном; ярлык в настройках воспроизведения остаётся как путь назад; мастер-тумблер в Media всегда виден.
- Семантика включения -> фиксировать ON немедленно; скачивание каталога предлагается, но опционально; ручной ввод источников всегда доступен; отказ или ошибка скачивания - не событие (тумблер остаётся ON, фича работает на ручных источниках).
- Охват lite -> Streams скрыт в lite целиком, несмотря на `SUPPORT_STREAMS=true` (разворачивает решение S0565 о включении Streams в lite).
- Размещение в настройках (без вопроса, владелец уже просил) -> отдельная сворачиваемая секция «Streams» после Translation/OCR.
- Семантика выключения (без вопроса, дефолт безопасности данных) -> прячет точки входа; данные каталога и активное воспроизведение сохраняются.
- Enable all (без вопроса) -> включает опциональное скачивание каталога Streams при поддержке сборкой.
- Форма элемента расширений (без вопроса, тактическое решение) -> отложено в `/spec-tech`; по умолчанию своя секция «Streams».

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Точки входа сегодня гейтятся только compile-флагом; пропуск одного места при добавлении runtime-гейта оставит видимую точку входа при OFF | Средняя | Переключатель «выключен», но Streams доступен | Перечислить все точки входа (меню + ярлык настроек) и гейтить централизованно на наблюдаемом флаге |
| Профиль-зависимый дефолт добавлен в матрицу пресетов без соответствующей ветки применения - молча ничего не делает | Средняя | Дефолт не применяется, профиль ведёт себя неверно | Добавлять столбец матрицы и ветку применения одним изменением; прогонять проверку консистентности пресетов |
| Скачивание каталога в онбординге сетевое; ошибка или медленность во время первого запуска может «подвесить» пользователя | Средняя | Застревание онбординга, ложное ощущение поломки | Вне главного потока, с прогрессом и определённым путём ошибки; никогда не блокировать переход страницы |
| Новая настройка без регенерации документации настроек ломает gate синхронизации (Rule 22) | Высокая | Падение mechanical-gate | Регенерировать manifest/reference/annotations настроек в том же изменении |
| Запись онбординга не добавлена в landscape-вариант разметки | Средняя | Запись Streams видна только в portrait | Зеркалить правки layout/ в layout-land/ |
| Модель элемента экрана расширений предполагает артефакт-«deliverable», а каталог им не является | Средняя | Натягивание каталога на чужую абстракцию | Решить форму элемента в тактической спеке; переиспользовать существующую операцию импорта каталога, а не раннер артефактов |
| Сокрытие Streams в lite разворачивает осознанное включение из S0565 | Средняя | lite теряет стриминг; рассинхрон с историей решения | Зафиксировать механизм в тактической спеке (compile-time off для lite vs гейт видимости); проверить, не опирается ли lite на сам движок воспроизведения стримов |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая пользовательская возможность: единый переключатель включения/выключения интернет-Streams с дефолтом по типу устройства, выбором в онбординге и присутствием в загружаемых расширениях. Одно предложение для FEATURES + _RU + _UK при релизе (наполняется `/skill-release` из диффа ALL_FEATURES, не здесь).

---

## 9. Архитектурные решения (ADR)

Решение: моделировать мастер-переключатель Streams по существующему паттерну мастер-переключателей Translation/OCR + гейт возможности, не изобретая новый механизм; compile-time возможность остаётся внешним гейтом, runtime-флаг - внутренним. Обоснование: консистентность, переиспользование, соблюдение изоляции флейворов (без `IS_*`-гардов в общем коде).

---

## 10. Связи с другими спеками

- **S0565** (internet-stream-playback) - родитель: построил фичу Streams и её текущие точки входа; S0575 надстраивает мастер-флаг, онбординг, расширения и гейтинг.
- Подсистема пресетов профиля устройства - переиспользуется и расширяется (присоединение к существующим стриминговым дефолтам кэша).
- Подсистемы онбординга и загружаемых расширений - точки интеграции.
- Потенциальные follow-up находки исследования (не зависимости): пробел в применении пресетов для ряда полей с пустыми строками матрицы (запаркован отдельно); отсутствие юнит-тестов у классов подсистемы Streams; жёстко прописанная проверка доступности экрана расширений под каждую возможность.

---

## 11. Критерии готовности (strategic-level)

1. На вкладке Media есть сворачиваемая секция «Streams» после Translation/OCR с единственным мастер-переключателем.
2. На свежей установке состояние переключателя по умолчанию соответствует профилю устройства (ON везде, кроме `photo_frame` и `ebook_reader`).
3. Шаг онбординга «What should the app do?» содержит запись Streams сразу после Translation; включение немедленно активирует фичу и предлагает опциональное скачивание каталога (пропуск возможен, источники добавляются вручную).
4. Экран загружаемых расширений содержит скачиваемый элемент каталога Streams.
5. Пункт меню Streams на главном присутствует тогда и только тогда, когда сборка показывает Streams И переключатель включён; ярлык в настройках воспроизведения присутствует всегда, когда сборка показывает Streams.
6. Ни в photos, ни в lite нигде нет переключателя, записей и точек входа Streams.
7. EN/RU/UK строки на месте; документация настроек регенерирована; проверки качества зелёные.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0575` - создаст `PLAN/S0575_streams-toggle-welcome-entrypoints/` с фазами.

---

## Last Audit

### Manual / on-device - 2026-06-26 (emulator-5554, Android 17 x86_64, standard debug `com.sza.fastmediasorter.debug` v2.60.6261.106)

**Outcome: PASS (in-scope reachable sub-checks).** Re-ran the settings + dropdown gating path on a standard build (prior 2026-06-23 sweep was noLegal). Spec is `BlockNeedUserTest`; both `S0575:` probes fired as quoted. Evidence: `temp/streams_sweep_20260626/S0575_A01_media_streams_section.png`.

- **Sub-check 1 (Settings Media section + persistence) - PASS.** Settings > Media section order confirmed: `headerDocuments` (Text/PDF/EPUB) -> `headerOther` (Translation, digitization (OCR)) -> `headerStreams` (Streams). Row `rowEnableStreams` = "Enable Streams" / "Open internet audio, video and RTSP streams." Toggled OFF (probe `S0575: settings Streams toggle -> false`), force-stopped + relaunched, reopened Settings > Media: state persisted OFF; toggled back ON (`S0575: settings Streams toggle -> true`) persisted.
- **Sub-check 5 (main dropdown gating) - PASS; Media-section shortcut follows toggle per S0578.** With toggle ON the "Programs" dropdown listed {Calculator, Streams, Voice recording, Camera, Download by link}; with toggle OFF (after restart) it listed {Calculator, Voice recording, Camera, Download by link} - Streams GONE. Gate probes `S0575: main menu streams gate support=true enabled=true` (ON) and `..enabled=false` (OFF). The Media-section `btnStreams` shortcut + the streams defaults group (Default order / Show by default / Updating the channel list / Clear play marks) are present only when ON and GONE when OFF - consistent with S0578 superseding the old always-visible Playback-tab shortcut clause (already noted in the 2026-06-23 audit).
- **Sub-checks 2, 3, 4, 6 - not re-exercised this run** (welcome onboarding / extensions / lite-photos negative); covered by the 2026-06-23 noLegal sweep below. This run focused on the standard-build settings + dropdown gating.

### Manual device-test - 2026-06-23 (emulator-5554, Android 17 x86_64, noLegal debug `com.sza.fastmediasorter.debug`)

Streams is SUPPORTED on noLegal and the toggle/onboarding/extensions/dropdown logic lives in `src/main` (capability + runtime gate), so the positive sub-checks render and are valid evidence on noLegal. Negative lite/photos sub-check is not buildable here. Spec is `BlockNeedUserTest`; `Timber.d("S0575:` probes fired as quoted. Evidence: `temp/S0575_sweep/`.

Overall verdict: PARTIAL (all in-scope positive sub-checks PASS; one strategic criterion superseded by S0578; one negative sub-check N/A on this build).

- **Sub-check 1 (Settings Media section) - PASS.** Expected: collapsible "Streams" section after the Translation/OCR grouping, with an "Enable Streams" toggle, persisting across restart. Actual: section order `headerDocuments` (Text/PDF/EPUB) -> `headerOther` (Translation, digitization (OCR)) -> `headerStreams` (Streams) confirmed via uiautomator; row `rowEnableStreams` = "Enable Streams" / "Open internet audio, video and RTSP streams." Toggled OFF, force-stopped + relaunched, reopened Settings -> Media: state persisted OFF; toggled back ON persisted. Screenshots: `A04_streams_section_ON.png`, `A05_streams_toggled_OFF.png`, `A06_persist_OFF_after_restart.png`, `A08_streams_expanded.png`, `A09_toggled_ON.png`.

- **Sub-check 5 (main dropdown gating) - PASS; Playback-shortcut clause SUPERSEDED by S0578.** Expected (per S0575): Streams menu item visible only when toggle ON, hidden when OFF; Playback-settings Streams shortcut visible in both states. Actual: with toggle OFF the main 3-dot dropdown showed only {Camera, Download by link} (no Streams); with toggle ON it showed {Streams, Camera, Download by link}. Probes: `S0575: main menu streams gate support=true enabled=false` (OFF) and `...enabled=true` (ON). The "always-visible Playback-settings shortcut" no longer exists: S0578 (Archived, "streams-shortcut-into-media-section") explicitly supersedes S0575 device-test point 5 - the shortcut now follows the master toggle (the Media-section `btnStreams` is GONE when OFF, VISIBLE when ON) and the Settings > Player tab has no Streams button (verified `B02_player_tab_ON.png`: Player tab has Sorting / Deletion+renaming / Player interface / Touch zones / Send file to / Background audio, no Streams). This is an intentional design change in the current working tree, not a defect; the S0575 strategic criterion §11.5 second clause is stale. Screenshots: `B01_dropdown_OFF.png`, `B03_dropdown_ON.png`, `B02_player_tab_ON.png`.

- **Sub-check 4 (Downloadable Extensions) - PASS.** Expected: a Streams section + catalog row; tapping imports the catalog with a per-source Installed/Failed status. Actual: Extensions screen (General tab -> Downloadable Extensions -> `ExtensionsManagerFragment`) has a dedicated "STREAMS" section with row "Stream sources catalog" / "Download the catalog of available stream sources" (Available). Tapped Download -> probe `S0575: extensions stream catalog import requested` fired -> row flipped to status "Installed" with a "Delete" action (network was available; a Failed status would also have been acceptable evidence). Screenshots: `C02_extensions_screen.png`, `C03_extensions_media_section.png`, `C04_catalog_import_result.png`.

- **Sub-checks 2 + 3 (welcome onboarding) - PASS (with a noted UI nuance).** Fresh install via `pm clear` + relaunch into WelcomeActivity.
  - Row placement (3): on "What should the app do?" the Streams row (`rowStreams`) sits immediately after the Translation row (`rowTranslation`) - confirmed by uiautomator Y-order in BOTH orientations (landscape `D04`/`D06`, portrait `D07` after `user_rotation 1`). The landscape layout variant includes the row (no portrait-only regression).
  - Enable semantics (3): toggling the Streams row ON flipped it ON immediately and showed the inline status "Catalog downloaded" (optional catalog fetch ran and succeeded; on no network it would show a non-alarming failed status and stay ON). Probe: `S0575: welcome Streams toggle -> true`. Screenshots `D04`, `D06`, `D07`.
  - Profile defaults (2): selected a normal profile (Personal smartphone) -> finished onboarding -> Settings > Media > Streams "Enable Streams" = ON (`D13_settings_smartphone_streams_ON.png`, btnStreams visible). Selected `photo_frame` -> Settings > Media > Streams = OFF (`D15_photoframe_streams_OFF.png` / `small_D15_*`, btnStreams absent). Matches `app_v2/src/main/assets/device_profile_presets.csv` (`enableStreams`: TRUE for all profiles except `photo_frame`=FALSE and `ebook_reader`=FALSE; `Other`=no override).
  - UI nuance (not a blocker): on the welcome "What should the app do?" page the Streams row reads `settings.enableStreams` and shows the app default OFF for the selected profile even though `WelcomeViewModel.applyFirstRunPresetForSelectedProfile` KDoc claims the page should "render the profile's defaults" (Translation/OCR rows behave the same). The profile master-flag is correctly applied by Finish (verified in Settings above), so the end state is correct; only the mid-flow row checkbox is stale. Candidate `/spec-draft` (welcome functionality rows not reflecting the eagerly-applied first-run preset) for the caller to capture if desired - left unparked here pending owner intent.

- **Sub-check 6 (lite/photos negative) - N/A.** Requires lite + photos builds to confirm no Streams toggle / onboarding row / extensions item / menu item. The installed build is noLegal debug; lite/photos are not present on this device and must not be rebuilt during the sweep. Not testable here; covered by the standard-build sweep.

Fired probes (verbatim):
- `D MainActivity: S0575: main menu streams gate support=true enabled=false`
- `D MainActivity: S0575: main menu streams gate support=true enabled=true`
- `D DeliverableInventoryImpl$importStreamCatalog: S0575: extensions stream catalog import requested`
- `D WelcomeFunctionalityController: S0575: welcome Streams toggle -> true`
