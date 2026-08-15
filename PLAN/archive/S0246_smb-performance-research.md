# Стратегическая спецификация: S0246 - ресёрч ускорения SMB-операций: библиотека и оркестрация скана

**Ticket:** S0246
**Status:** Verified
**Priority:** 50
**Date:** 2026-05-18
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-05-18; объединяет два ранее самостоятельных направления (S0246 library-research и S0237 first-scan-speedup, откачен 2026-05-18).
**Tactical spec:** `PLAN/S0246_smb-performance-research/` (будет создан через `/spec-tech` после явного решения владельца, что собранный материал по обоим направлениям достаточен для перехода к POC).

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

SMB-стек проекта проседает по производительности сразу на двух уровнях, и до проведения сравнительного ресёрча неясно, где находится более выгодная точка приложения усилий.

**Уровень A - пропускная способность.** Текущая клиентская SMB-библиотека показывает throughput ниже 2 МБ/с при копировании файлов с устройства на сетевую шару. Среда владельца не является узким местом: телефон работает в Wi-Fi 7, ПК подключён к роутеру по 2.5 Gbps, share-хост держит конкурентные операции от других клиентов на порядок быстрее. Узкое место - клиентская SMB-библиотека и её модель ввода-вывода, а не транспорт.

**Уровень B - первичный заход на большой каталог.** Метрика `ScanMetrics: SLOW SCAN detected - 50082ms threshold=6000ms file_count=182` фиксирует, что открытие SMB-каталога среднего размера (182 файла) занимает 50 секунд, в то время как сам листинг отрабатывает за миллисекунды. Все 50 секунд - это per-file metadata pass под жёстко зашитым низким пределом конкурентности (4 одновременных metadata-чтения на одну SMB-цель). На втором заходе (cache hit) задержки нет - пользователь платит весь штраф в первую сессию.

Эти два уровня взаимосвязаны, но имеют разные потенциальные решения. Throughput-узкое-место преимущественно лежит в самой библиотеке (pure-Java I/O без нативных оптимизаций, отсутствие multi-channel, отсутствие large MTU). First-scan-узкое-место преимущественно лежит в нашей оркестрации (синхронный metadata-pass перед эмитом, низкая параллельность, отсутствие per-file timeout). До измерений на альтернативной библиотеке неизвестно, какая часть first-scan-проблемы исчезнет сама собой при смене стека, а какая останется и потребует отдельной оркестрационной работы.

Текущая интеграция была выбрана исходя из совместимости с Google Play (открытая лицензия, отсутствие нативных бинарей с проблемной редистрибьюцией). Это ограничение продолжает действовать для market-flavors, но для sideload-only `noLegal`-канала есть дополнительная свобода: можно рассматривать платные SDK, коммерческие лицензии, библиотеки с менее благоприятной для Play лицензией и proprietary-решения. Возможен сценарий, где более производительная open-source библиотека окажется совместимой и с market-flavors - тогда замена будет повышена до уровня всего продукта.

Сейчас систематической карты кандидатов нет, и нет ответа, нужны ли оркестрационные изменения сверху от смены библиотеки. Нужна единая research-спека, которая инвентаризирует доступные SMB/CIFS-стеки для Android, классифицирует их по дистрибутивной пригодности, измеряет реальный прирост производительности и отдельно проверяет, какие оркестрационные оптимизации (двухфазный показ списка, конфигурируемая конкурентность, per-file metadata budget, dedup листинга) остаются осмысленными после возможной смены библиотеки. Весь объём ресёрча выполняется параллельными агентами в интернете (`WebSearch` + `WebFetch` + Maven/GitHub/vendor-сайты + публичные бенчмарки), а не в режиме ручного человеческого поиска.

---

## 2. Цели

1. Составить полный реестр кандидатов SMB-клиентских библиотек, технически пригодных для Android (Java/Kotlin/JNI/native bridges), с фактическим уровнем поддержки SMB2 и SMB3.
2. Для каждого кандидата зафиксировать тип лицензии, статус совместимости с Google Play в `standard`-сборке и пригодность для sideload-only `noLegal`-сборки.
3. Определить измеримые бенчмарк-критерии, по которым кандидаты будут сравниваться в follow-up POC: пропускная способность крупных файлов в обе стороны, скорость листинга больших директорий, latency на мелких файлах, профиль CPU и памяти, поведение при нестабильной сети.
4. Сформировать shortlist кандидатов, которые имеет смысл проверять в POC: с приоритетом «открытая лицензия + market-safe» → «открытая лицензия + только sideload» → «коммерческая лицензия + только sideload».
5. Зафиксировать, какие архитектурные требования к текущему сетевому слою должны быть выполнены для безопасной подмены реализации без слома существующих фич (плейбек, batch-операции, listing, watchdog, mutation coordination, error-classification).
6. Определить, какие фичи кандидата дают наибольший потенциал прироста throughput: pipelined I/O, multi-channel, large MTU, async/non-blocking сокеты, нативный SMB-стек, оптимизированный listing, persistent handles, server-side copy.
7. Параллельно отдельным research-треком оценить, какие оркестрационные оптимизации поверх текущей библиотеки остаются осмысленными до смены стека и какие потеряют смысл после: двухфазный показ списка с инкрементальной подгрузкой metadata, конфигурируемая SMB-конкурентность, per-file metadata budget с тихим fallback, дедупликация листингов внутри одной операции загрузки ресурса.
8. По итогам обоих треков получить от владельца явное решение: (а) идём на POC замены библиотеки, (б) идём на оркестрационные оптимизации поверх текущей библиотеки, (в) оба трека параллельно, (г) ни один из треков (текущий стек оптимален).

**Non-goals:**

- Любая интеграция кандидата в кодовую базу в рамках этой спеки.
- Любая реализация оркестрационных оптимизаций (двухфазный эмит, concurrency, per-file timeout, listing dedup) в рамках этой спеки. Это исследовательский trade-off-анализ, а не implementation.
- Замер текущего baseline-стека на устройстве: baseline-числа фиксируются уже в POC-спеке, не здесь.
- Расширение функциональности SMB-стека (новые операции, новые протоколы транспорта) - спека только про производительность существующих операций.
- Любые правки UI и user-visible strings.
- Окончательное архитектурное решение о замене текущей библиотеки или о принятии оркестрационных оптимизаций - оно принимается на основе POC-результатов или отдельной импликативной спеки.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Идеальный исход - найти библиотеку с лицензией, безопасной для Google Play, и выкатить её сразу во все флейворы, включая `standard`.
2. Если такая библиотека не найдена - приемлемо ограничить замену только sideload-only `noLegal`-сборкой, а market-flavors оставить на текущем стеке.
3. Платные коммерческие SDK допустимы как кандидаты, если они легитимно приобретаются или предусматривают модель личного использования.
4. Если кандидат показывает прирост, но требует нативного кода с проблемной редистрибьюцией для Play, это всё ещё валидный кандидат для `noLegal`-канала.
5. Замена ради замены не нужна: если ни один кандидат не даёт измеримого прироста и оркестрационные оптимизации сами по себе не решают first-scan-проблему, ресёрч закрывается выводом «текущий стек оптимален».
6. Оркестрационные оптимизации не должны быть применены «вслепую» до решения по библиотеке: некоторые из них (например, повышение SMB-конкурентности до 8) могут стать избыточными или вредными после смены стека.
7. Исследование должно сразу учитывать стабильность кандидата: нерегулярные релизы, открытые CVE, неактивные maintainer-команды - стоп-флаг даже при потенциально высокой скорости.
8. По оркестрационной части - двухфазный показ должен ощущаться как немедленный отклик, аналогичный `setSkipInitialThumbnailLoad` для миниатюр. Per-file metadata timeout - короткий, тихий, без warn-спама. Параллельность SMB-чтений должна быть настраиваемой величиной, а не зашитой константой.

### 3.2 Жёсткие ограничения

- **Flavor:** первичный целевой флейвор для возможной замены библиотеки - `noLegal` (sideload-only). Повышение замены до `standard` рассматривается только если итоговая лицензия и условия редистрибьюции совместимы с Google Play. Оркестрационные оптимизации, если будут признаны полезными, лежат в общем коде (`src/main`) и применяются ко всем флейворам, где включён SMB-модуль (`standard`, `lite`, `legacy`, `noLegal`). Любая реализация после POC обязана следовать `dev/FLAVOR_DEVELOPMENT_RULES.md`: интерфейс SMB-клиента в `src/main`, конкретные реализации в `src/<flavor>/java/`, биндинг через flavor-specific Hilt-модули. Никаких `BuildConfig.IS_NO_LEGAL_FLAVOR`-гейтов внутри `src/main/java/**` (Rule 15 CLAUDE.md).
- **Distribution:** `noLegal` остаётся sideload-only сборкой владельца, не предназначенной для публичных магазинов. Никакие `noLegal`-зависимости, class names или artifact references не должны утекать в market-flavors.
- **Лицензионная граница:** обязательная классификация лицензии каждого кандидата (Apache 2.0, MIT, BSD, LGPL, GPL, AGPL, proprietary commercial, custom EULA). Лицензии с copyleft-условиями, требующими открытия исходников всего приложения, автоматически исключаются для `standard` и помечаются как «only legal under sideload» для `noLegal`. Коммерческие SDK обязаны иметь явно описанные условия покупки/использования.
- **Security/privacy:** ресёрч обязан помечать кандидатов с открытыми CVE, проблемами supply-chain, закрытыми бинарными blob'ами с непрозрачным сетевым поведением, телеметрией или интеграциями с внешними сервисами.
- **API level:** baseline остаётся прежним (`minSdk 26` для `noLegal`/`standard`, `minSdk 23` для `legacy`). Кандидаты, требующие более высокого `minSdk` или специфической libc / NDK-ABI, фиксируются с явным указанием стоимости и совместимости.
- **Compatibility surface:** замена клиента не должна сломать существующие фичи - плейбек, batch-копирование/перемещение, листинг с фильтрацией, watchdog-стек, mutation coordination, error classification, lifecycle-управление пулом соединений. Эти точки фиксируются в §5 как обязательные ограничения для будущего POC.
- **Оркестрационная совместимость данных:** оркестрационные оптимизации, если будут признаны полезными, должны сохранять формат уже сохранённых `MediaFilesCache`-записей. Любое введение state-полей (например, `metadataState` со значениями `pending`/`partial`/`complete`) обязано быть default-совместимым со старыми кэшами.
- **Доступность:** двухфазный показ списка не должен ломать TalkBack-ордер строк - имя файла читается с самого начала, дополнительные поля объявляются после их прихода.
- **Производительность:** ленивый metadata-pass не должен конкурировать с playback и общим UI-потоком - приоритет ниже visible-rendering, выше idle-prefetch.
- **Локализация:** сама research-спека не вводит user-visible strings. Любые будущие user-facing сообщения (например, новая ошибка от другого клиента или индикатор «metadata подгружаются») обязаны проходить через `docs/COMMUNICATION_POLICY.md` и EN/RU/UK parity через `scripts/check_strings_localized.ps1`.
- **Без правок кода:** эта спека не приводит к изменениям в исходниках, ресурсах, Gradle-конфигурации или скриптах. Любые такие правки появляются только в POC-спеке (или в отдельной implementation-спеке по оркестрации) после явного решения владельца.

---

## 4. Контекст текущей архитектуры

### 4.1 SMB-клиентский стек

Сетевой слой работы с SMB построен на одной open-source клиентской библиотеке с pure-Java стеком, без нативной части. Поверх неё в проекте уже выстроен значительный слой инфраструктуры: пул соединений, lifecycle-менеджер, health-probe, ошибочный классификатор, координатор file-mutation, отдельный directory-scanner, media-scan-coordinator, отдельный hasher, форматтер ошибок и набор data-source адаптеров. Этот слой стабилизирован, подтверждён закрытыми багами и инкрементальными исправлениями за последние месяцы и не должен переписываться вместе со сменой нижележащей библиотеки.

Текущая библиотека ограничена в части производительности по нескольким причинам: pure-Java I/O без нативных оптимизаций, отсутствие multi-channel поддержки, отсутствие конфигурируемого large-MTU, синхронная модель чтения/записи в большинстве операций, ограниченная гибкость в настройке размеров transaction-буферов и read/write-windows. Сторонняя экспертиза и публичные обсуждения других проектов на Android подтверждают, что этот стек уступает по throughput более новым реализациям на нативной основе или на async-стеке. Эти утверждения для S0246 - гипотезы, требующие проверки на бенчмарках в POC, а не аксиомы.

### 4.2 Оркестрация скана

В сценарии загрузки ресурса слой `data` сначала вызывает `SmbDirectoryScanner` для получения списка имён, размеров и типов файлов, а затем `SmbMediaScanner` обогащает каждый элемент метаданными (EXIF, video header, ID3) до того, как результат уйдёт в UI. Параллельность чтений на одну SMB-цель ограничена коэффициентом `ConnectionThrottleManager` (текущий лимит 4 одновременных metadata-чтений), что для домашнего NAS чрезмерно консервативно. UI-слой (browse list) уже имеет паттерн отложенной загрузки через `setSkipInitialThumbnailLoad`, но он применяется только к миниатюрам, а не к остальным metadata-полям.

Сейчас экран не способен показать список сразу после листинга, потому что доменная цепочка ждёт полностью обогащённую коллекцию перед эмитом наружу. Дополнительно зафиксирована избыточная работа: листинг одного и того же `dirPath` выполняется дважды подряд (видно как два `Found N files in root` в пределах одной миллисекунды) - следствие двойного входа из вышестоящего координатора.

Часть оркестрационной нагрузки потенциально снимается за счёт смены библиотеки (если новый стек делает листинг и metadata-чтения принципиально быстрее на уровне протокола), часть остаётся независимо от стека (UI-эмит после полного обогащения, дублирующий вызов листинга, отсутствие per-file timeout). Эта спека обязана разделить эти два класса до решения о реализации.

---

## 5. Предлагаемый подход

Ресёрч строится как два независимых трека с общей точкой принятия решения в конце.

### 5.1 Трек A - библиотечный ресёрч

- **Discovery.** Систематический сбор кандидатов параллельными агентами через `WebSearch`/`WebFetch`: Java/Kotlin SMB-клиенты, JNI-обёртки над нативными стеками (включая обёртки над libsmb2, samba-client, libsmbclient, проприетарные SMB SDK), wrappers над сторонними нативными бинарями. Источники - Maven Central, GitHub, AndroidX-каталоги, коммерческие SDK-вендоры, форумы Android-разработчиков, релизы крупных file-manager проектов с открытым кодом, публичные benchmark-репорты. Фиксируется минимум 8–12 кандидатов, чтобы фильтры дали статистически осмысленный shortlist.
- **License classification.** Для каждого кандидата определяется тип лицензии, наличие copyleft-вирусности на уровень всего приложения, наличие коммерческой опции, явные условия редистрибьюции в Google Play, дополнительные patent-clauses. Кандидат, лицензию которого нельзя однозначно классифицировать, помечается как `Unclear` и не попадает в shortlist без юридического подтверждения.
- **Capability matrix.** Поддержка SMB2/SMB3, multi-channel, large MTU, persistent handles, server-side copy, async I/O, encrypted transport, signing, kerberos/NTLM/guest. Фиксируются минимум как «есть/нет», а где возможно - версия и зрелость реализации.
- **Health signals.** Дата последнего релиза, частота релизов за 12 месяцев, число открытых CVE, статус maintainer-команды (active/inactive/unknown), наличие production-usage в крупных open-source проектах.
- **Integration cost.** Соответствие текущей абстракции client/connection/pool, требуемый объём адаптации mutation-coordinator/error-classifier/connection-pool, новые API-surface для UI-слоя, новые требования к Android `minSdk`, ABI-набор, размер APK-дельты.
- **Benchmark plan.** Документ, по которому POC будет измерять кандидатов на реальной сети владельца: профиль крупных файлов 100 МБ / 1 ГБ / 4 ГБ; листинг директорий с 100/1000/10000 файлов; latency для batch-операций с мелкими файлами; CPU/heap-профиль; устойчивость при умышленной деградации Wi-Fi; baseline текущего стека снимается одновременно с замером кандидатов.
- **Risk assessment.** Telemetry-каналы внутри библиотеки, поведение при некорректных сертификатах подписи, отношение к серверам с устаревшими версиями SMB1/SMB2, наличие закрытых нативных blob'ов.

Pipeline трека A: discovery → license-filter → capability-filter → health-filter → integration-cost-estimate → benchmark-readiness-rating → shortlist. Каждый фильтр работает в режиме hard-cut: кандидат, не прошедший фильтр, исключается с явным указанием причины. Итоговый shortlist делится на три bucket'а: `market-safe`, `nolegal-only-open-source`, `nolegal-only-commercial`.

### 5.2 Трек B - оркестрационный ресёрч

- **Двухфазный показ списка.** Trade-off между сложностью UI-контракта и UX-выгодой. Анализируется, какие сортировки и фильтры browse-экрана зависят от metadata (NAME_ASC независим, DATE_ASC требует EXIF) и как двухфазный показ влияет на их визуальную стабильность.
- **Конфигурируемая SMB-конкурентность.** Trade-off между скоростью первого скана и риском серверного throttle/refusal на слабых NAS. Анализируется, как изменение базовой конкурентности (текущие 4) повлияет на гарантии `IdleDisconnect` и `SmbConnectionManager`, и нужно ли вводить per-resource override. Фиксируется ожидаемый эффект на разных кандидатах из трека A (если кандидат сам делает pipelined I/O, наша конкурентность поверх него может стать избыточной).
- **Per-file metadata budget.** Trade-off между полнотой metadata и риском зависшего файла, который блокирует соседние. Анализируется типичное время EXIF/ID3/video-header чтения на SMB и какое значение бюджета изолирует проблему без потери валидных medata-чтений. Фиксируется, как тихий fallback сочетается с COMMUNICATION_POLICY (никаких новых user-facing строк), и какие телеметрические данные нужно собирать на агрегированном уровне.
- **Дедупликация листинга.** Trade-off между свёрткой дублирующих вызовов и риском маскировки бага во внешнем координаторе. Анализируется, на каком уровне (scanner vs. coordinator) свёртка корректна, и какой signal-уровень логирования сохранить, чтобы root-cause не исчез.
- **Совместимость треков.** Главный вопрос трека B - какие из его выводов остаются осмысленными после возможной смены библиотеки в треке A. Например: per-file metadata budget остаётся полезным независимо от стека (защита от зависших файлов - свойство SMB-сервера, не клиента). Повышение базовой конкурентности может стать избыточным, если новый клиент сам параллелит чтения через multi-channel. Двухфазный показ списка остаётся полезным независимо от стека.

### 5.3 Точка принятия решения

После завершения обоих треков фиксируется матрица «оптимизация vs. ожидаемый эффект» для трёх сценариев: (a) остаёмся на текущей библиотеке, (b) меняем библиотеку на market-safe-кандидата, (c) меняем библиотеку на nolegal-only-кандидата. Для каждого сценария указывается, какие выводы трека B остаются актуальными. Владелец выбирает один из четырёх итоговых путей (Цель §2.8).

### 5.4 Точки расширяемости

- Будущая POC-спека замены библиотеки обязана опираться на интерфейс SMB-клиента, который должен жить в общем `src/main`-коде и иметь как минимум две реализации: существующая (на текущей библиотеке) и кандидатская. Этот интерфейс является обязательной предпосылкой для безопасной A/B-проверки и для соблюдения flavor-isolation.
- Точка расширения для multi-channel/large-MTU/persistent-handles: будущий клиент должен описывать свои возможности декларативно, чтобы вышестоящие операции могли использовать оптимальный путь там, где он есть, и fallback там, где его нет.
- Точка расширения для benchmark-режима: будущий клиент должен поддерживать запуск через тестовый harness без UI, чтобы измерение и регрессии можно было воспроизводить независимо от полного приложения.
- Conditional dependency wiring: новый SMB-клиент, если он попадёт в `noLegal`-only сборку, заносится через flavor-specific binding; конкретные Hilt/file-уровни оформляются в `/spec-tech`.
- Конфигурация конкурентности metadata-чтений, если по итогам трека B будет признана нужной, выносится в одно место и допускает override на уровне ресурса (для нестабильных серверов).
- Per-file budget, если по итогам трека B будет признан нужным, оформляется как абстракция таймаута + fallback-провайдер, чтобы тот же паттерн позже применить к SFTP / FTP, если измерения это оправдают.

---

## 6. Открытые вопросы / Research items

Все 13 items закрыты параллельными агентами 2026-05-18 (`A_DISCOVERY`, `A_LICENSE`, `A_INTEGRATION`, `A_SERVER`, `B_ORCHESTRATION`).

**Сводка ключевых выводов:**

- **Library swap для `standard`-флейвора market-safe-вариантов нет.** Единственные Apache/MIT/BSD SMB-клиенты - SMBJ (incumbent) и Apache-2.0-wrappers поверх него. Все остальные актуально-поддерживаемые кандидаты - LGPL-2.1 (jcifs-ng, codelibs/jcifs 3.0, libsmb2) или enterprise-only коммерческие без self-serve покупки (Visuality jNQ/YNQ, Tuxera).
- **Library swap для `noLegal`-флейвора имеет два реальных пути.** Низкий риск, высокая совместимость: codelibs/jcifs 3.0 (LGPL-2.1, active, SMB3.1.1 + AES-GCM, multi-channel scaffolding present, drop-in API ancestry с jcifs-ng). Высокая отдача, высокая инженерная цена: libsmb2 + DIY JNI (LGPL-2.1, Kodi-proven >100 МБ/с, 1–2 недели на JNI + 4-ABI build + AAR pipeline, нет upstream Android-сборки).
- **Текущая throughput-проблема может быть НЕ библиотечной.** Issue `jcifs-ng#106` показывает: SMBJ обгоняет jcifs-ng на 1 ГБ upload (~25 с vs ~36 с). Наш замер «<2 МБ/с» вероятно вызван app-side wiring (буферы, BufferedInputStream chunk), не самой SMBJ. До любой миграции - обязательный 30-минутный spike с `SmbConfig.withReadBufferSize(1_048_576).withWriteBufferSize(1_048_576)` + 64 KiB BufferedInputStream chunk; если throughput подскочит до ≥20 МБ/с - мигрировать незачем.
- **Три из четырёх оркестрационных оптимизаций library-agnostic.** Per-file timeout, partial-cache persistence, listing-dedup полезны независимо от выбора SMB-клиента и могут быть реализованы немедленно. Только base-concurrency tuning (`4 → 8`) теряет смысл после swap на multi-channel/sidecar клиент.
- **Откаченное значение S0237 `4 → 8` для всех metadata-чтений было слишком общим.** Правильное разделение: header-only EXIF/ID3 reads - 8; full-metadata video через `MediaMetadataRetriever` - 3 (RAM-bound, не throughput-bound; Jellyfin's empirically validated NAS-safe range - issues #15728, #12203, #13531). Откаченное решение «не кэшировать partial» противоречит Plex/Jellyfin industry pattern. Откаченное решение «dedup в scanner» маскировало root-cause координатора.
- **SMBJ 0.14.0 имеет single-maintainer-risk.** 202 открытых issues, последний merged PR = январь 2025. Не блокер сейчас, но фактор долгосрочной устойчивости.

### 6.1 Трек A - библиотека

1. **Полный список кандидатов на Android.** *(Resolved by A_DISCOVERY, 2026-05-18)*
   - Найдено 14 кандидатов; 8 живых и техноспособных:
     - **Pure-Java OSS:** SMBJ 0.14.0 (Apache-2.0, current); jcifs-ng 2.1.10 (LGPL-2.1, recent commit Nov 2025); codelibs/jcifs 3.0.2 (LGPL-2.1, **Java 17 source** - нужен desugaring spike для API 26); codelibs/jcifs 2.1.40 (LGPL-2.1, Java 8 source, без SMB3.1.1).
     - **Native+JNI OSS:** libsmb2 (LGPL-2.1; нет upstream Android-сборки - issue #93 открыт с 2019; ~300 KB per ABI; Kodi production usage).
     - **Closed-source commercial:** Visuality jNQ (Java SMB3.1.1, Microsoft patent indemnification, NDA-gate); Visuality YNQ (C SMB3.1.1, embedded-focus); Tuxera Fusion SMB (C, server-focus, client SDK on contract).
   - Дисквалифицированы: libdsm (SMB1-only по собственному README); original jcifs ioplex (SMB1-only, abandoned 2011); google/samba-documents-provider (GPL-3, archived 2020, vendored Samba 4.5.1); Rust-кандидаты smb-rs/pavao (требуют Rust+JNI toolchain - out-of-scope); форки SMBJ pepijnve/kilokahn (dormant 2017/2020).
   - CVEs: 0 direct на каждом из 8 живых кандидатов (NIST NVD 2024–2026). Малая user-база Java SMB-клиентов означает «отсутствие assigned CVE», а не «отсутствие багов».
   - Источники: [SMBJ](https://github.com/hierynomus/smbj) · [jcifs-ng](https://github.com/AgNO3/jcifs-ng) · [codelibs/jcifs](https://github.com/codelibs/jcifs) · [codelibs/jcifs SMB3 multi-channel design](https://github.com/codelibs/jcifs/blob/master/docs/smb3-features/03-multi-channel-design.md) · [libsmb2](https://github.com/sahlberg/libsmb2) · [libsmb2 issue #93](https://github.com/sahlberg/libsmb2/issues/93) · [Visuality jNQ](https://visualitynq.com/products/jnq/) · [Tuxera Fusion SMB](https://www.tuxera.com/products/tuxera-fusion-smb/)

2. **Лицензионная пригодность для Google Play.** *(Resolved by A_LICENSE, 2026-05-18)*
   - **MARKET-SAFE** (можно в `standard`/`lite`/`legacy`/`photos`/`vr`): SMBJ (Apache-2.0) + Apache-wrappers поверх него. Других OSS-кандидатов с market-safe-лицензией не существует.
   - **NOLEGAL-ONLY** (только sideload): все LGPL-2.1/LGPL-3 кандидаты (jcifs-ng, codelibs/jcifs, libsmb2, libsmbclient). Юридически - на Android LGPL ≈ GPL: APK подписан одним ключом, юзер не может перелинковать. Conservative reading (Xebia "LGPL on Android", LWN, собственная политика Google'а в `samba-documents-provider`).
   - **REJECT-bucket пуст:** AGPL-лицензированных SMB-клиентов в индустрии нет; худший случай - GPL-3 (libsmbclient через Samba upstream), технически юзаемо в одной личной sideload-сборке, но риск cross-source-set contamination при merge'е в market-флейворы - strong recommendation: не вносить GPL-3 код в `app_v2` ни под каким flavor-guard'ом.
   - Patent risk: Apache-2.0 имеет explicit patent grant от контрибьюторов, но не передаёт SMB-патенты Microsoft. jNQ Visuality - единственный кандидат с Microsoft SMB patent sublicense; реальный enforcement risk для small-volume приложений = низкий, но non-zero asymmetry.
   - Источники: [SMBJ LICENSE_HEADER](https://github.com/hierynomus/smbj/blob/master/LICENSE_HEADER) · [Xebia: LGPL on Android](https://xebia.com/blog/the-lgpl-on-android/) · [LWN: LGPL and app store](https://lwn.net/Articles/526355/) · [FSF licence list](https://www.gnu.org/licenses/license-list.html) · [VLC LGPL relicensing](https://www.videolan.org/press/lgpl-libvlc.html) · [google/samba-documents-provider](https://github.com/google/samba-documents-provider)

3. **Коммерческие SDK с моделью личной/sideload-only лицензии.** *(Resolved by A_LICENSE, 2026-05-18)*
   - **Доступных через self-serve покупку для индивидуального dev'а: НЕТ.** Все известные коммерческие Android SMB SDK - enterprise-only с NDA-gate перед eval kit, без публичного прайс-листа и self-serve cart'а.
     - Visuality jNQ - pharma-focus enterprise; NDA → eval kit; ongoing use требует contract.
     - Visuality YNQ - embedded firmware market; custom JNI integration не из коробки.
     - Tuxera Fusion SMB - primarily NAS server vendors; OEM-royalty pricing.
   - Единственная теоретически accessible commercial-дверь - Videolabs libdsm proprietary dual-license (LGPL/proprietary). Отсекается по техническому критерию (SMB1-only).
   - Verdict: `nolegal-only-commercial-accessible` bucket = пуст. `nolegal-only-commercial-enterprise-only` = jNQ/YNQ/Tuxera, но в текущей модели работы владельца не достижимы.
   - Источники: [Visuality jNQ](https://visualitynq.com/products/jnq/) · [Visuality YNQ](https://visualitynq.com/products/ynq/) · [Tuxera Fusion SMB](https://www.tuxera.com/products/tuxera-fusion-smb/) · [Visuality jNQ white paper](https://visualitynq.com/app/uploads/jNQ-White-Papers.pdf) · [libdsm dual-license repo](https://github.com/videolabs/libdsm)

4. **Реальный прирост производительности.** *(Resolved by A_DISCOVERY, 2026-05-18)*
   - Capability matrix по throughput-факторам (Y=stated&implemented, P=partial, N=missing, D=design-stage):
     - SMBJ 0.14: pure-Java; compound limited; futures-based partial async; AES-CCM=Y, AES-GCM=P (open issues).
     - jcifs-ng 2.1.10: pure-Java; no async; no SMB3 encryption ratified; multi-channel=N.
     - codelibs/jcifs 3.0: pure-Java; SMB3.1.1 + AES-GCM=Y; multi-channel=**D** (design-stage only, не shipped).
     - libsmb2: native C; full async event loop; zero-copy READ/WRITE compounded; AES-CCM/GCM=Y; multi-channel=N (не в upstream).
     - jNQ/YNQ/Tuxera (commercial): все throughput-фичи stated vendor side; independent benchmarks отсутствуют.
   - Сторонние подтверждения: `jcifs-ng#106` (SMBJ ~25 с vs jcifs-ng ~36–39 с на 1 ГБ upload); SNIA 2019 paper Sahlberg (libsmb2 zero-copy); Kodi production (libsmb2 >100 МБ/с на equivalent hardware).
   - **Critical caveat:** наш baseline «<2 МБ/с» противоречит `jcifs-ng#106` (SMBJ должен делать ≥25 МБ/с). Прирост на любой замене может оказаться ниже разочарования от настройки текущего стека - поэтому buffer-tuning spike обязателен перед любым POC (см. §6.3).
   - Server-side prerequisites: multi-channel требует server-side support (multiple NICs); AES-GCM требует SMB3.0.2+; persistent handles требуют SoFS/CA-share. Домашний NAS вряд ли даст multi-channel-gain, но encryption-throughput и async-pipeline даст.
   - Источники: [jcifs-ng#106 benchmark](https://github.com/AgNO3/jcifs-ng/issues/106) · [SMBJ#253](https://github.com/hierynomus/smbj/issues/253) · [SNIA libsmb2 paper (Sahlberg 2019)](https://www.snia.org/sites/default/files/SDC/2019/presentations/SMB/Sahlberg_Ronnie_Libsmb2_a_Userspace_SMB2_Client_for_all_Platforms.pdf) · [codelibs/jcifs multi-channel design](https://github.com/codelibs/jcifs/blob/master/docs/smb3-features/03-multi-channel-design.md)

5. **Совместимость с текущей абстракцией клиента.** *(Resolved by A_INTEGRATION, 2026-05-18)*
   - **Единственный OSS-кандидат, где wrapping-слой (connection pool, lifecycle manager, mutation coordinator, error classifier, scanner) сохраняется без переписывания: jcifs-ng.** Структурно ближайший к SMBJ Java-API: `SmbFile.listFiles(filter)`, `SmbFile.getInputStream/getOutputStream`, `renameTo`/`copyTo` - блокирующие; `CIFSContext` идеально мэппится туда, где сегодня `SMBClient`.
   - codelibs/jcifs (legacy 2.1 + active 3.0): API совместим с jcifs-ng-классикой (та же фамилия), та же low-cost интеграция; 3.0 имеет Java-17-bytecode-issue, требующий desugaring spike.
   - libsmbclient через Samba-DP паттерн: medium-to-high cost. Handle-based POSIX-like API мэппится терпимо в Java-обёртку, но теряется server-side wildcard на `query-directory` (scanner регрессирует на client-side filter); single-callback credential model клешется с per-share credential injection; +3–8 МБ per ABI зависимости (krb5, gnutls, talloc, tdb, tevent).
   - libsmb2: high cost везде. Sync API handle-based с ручным lifecycle несовместим с current wrapper invariant'ами. Async API требует introduction'а event-loop-thread'а - структурное изменение wrapper'а, прямо запрещённое NON-goal'ом спеки.
   - jNQ: engineering cost низкий (vendor шипит JCIFS-migration shim), но commercial cost - недоступен индивидуально.
   - Per-operation cost detail (listFiles / openInputStream / openOutputStream / delete / rename / copy / getAttrs / mkdir / pool): см. отчёт A_INTEGRATION для построчного breakdown'а.
   - Источники: [jcifs-ng SmbFile source](https://github.com/AgNO3/jcifs-ng/blob/master/src/main/java/jcifs/smb/SmbFile.java) · [libsmb2 header](https://github.com/sahlberg/libsmb2/blob/master/include/smb2/libsmb2.h) · [Samba libsmbclient.h](https://github.com/samba-team/samba/blob/master/source3/include/libsmbclient.h) · [google/samba-documents-provider SambaClient.h](https://github.com/google/samba-documents-provider/blob/master/app/src/main/cpp/samba_client/SambaClient.h)

6. **Совместимость с серверной стороной владельца.** *(Resolved by A_SERVER, 2026-05-18)*
   - SMBJ 0.14.0 уже выставляет ~80% capability-битов как named getters на `ConnectionContext`: `supportsMultiChannel()`, `supportsMultiCredit()` (= `SMB2_GLOBAL_CAP_LARGE_MTU`), `supportsEncryption()`, `supportsDFS()`, `supportsFileLeasing()`, `supportsDirectoryLeasing()`, `cipherId`, `preauthIntegrityHashId`, `compressionIds`, `isServerSigningEnabled/Required`, `getServerGuid/Name/NetBiosName`, `windowsVersion`, `timeOffsetMillis`, `maxRead/Write/TransactSize`. Reflection не нужна.
   - Не выставлены как named-предикаты: `SMB2_GLOBAL_CAP_PERSISTENT_HANDLES` (0x10), `SMB2_GLOBAL_CAP_NOTIFICATIONS` (0x80). Требуют чтения `ctx.server.serverCapabilities` (поле unverified) или апгрейда SMBJ.
   - Активное обнаружение multi-channel в SMBJ не обёрнуто: после успешного TREE_CONNECT и при `supportsMultiChannel()=true` руками построить `SMB2IoctlRequest(CtlCode=FSCTL_QUERY_NETWORK_INTERFACE_INFO=0x001401FC)`, распарсить `NETWORK_INTERFACE_INFO` linked-list (MS-SMB2 §2.2.32.5): `IfIndex`, `Capability(RSS/RDMA)`, `LinkSpeed`, `SockAddr_Storage`.
   - Cross-check tools для workstation: `nmap --script smb-protocols,smb2-capabilities,smb2-security-mode -p445 host` + `smbclient -m SMB3 -L //host -d 5`.
   - Move-the-needle Samba knobs на стороне NAS: `server multi channel support=yes` (по умолчанию OFF), `aio read/write size=1`, `socket options=TCP_NODELAY IPTOS_LOWDELAY`, `use sendfile=yes`, `min receivefile size=16384`, `smb encrypt=desired`. Synology DSM 7.2+: *Control Panel → File Services → SMB → Advanced → Others → Enable SMB3 Multichannel*. QNAP QTS 5.x: *Microsoft Networking → Advanced Settings*.
   - Ready-to-drop pseudocode `dumpSmbServerCapabilities(connection): SmbServerCapabilitiesSnapshot` - один диагностический вызов после `authenticate()`, не на hot-path; полная сигнатура и data-class в отчёте A_SERVER.
   - Источники: [MS-SMB2 §2.2.4 NEGOTIATE Response](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-smb2/63abf97c-0d09-47e2-88d6-6bfa552949a5) · [MS-SMB2 §2.2.32.5 NETWORK_INTERFACE_INFO](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-smb2/fcd862d1-1b85-42df-92b1-e103199f531f) · [SMBJ ConnectionContext.java v0.12.1](https://github.com/hierynomus/smbj/blob/v0.12.1/src/main/java/com/hierynomus/smbj/connection/ConnectionContext.java) · [nmap smb2-capabilities](https://nmap.org/nsedoc/scripts/smb2-capabilities.html) · [Synology SMB3 Multichannel KB](https://kb.synology.com/en-us/DSM/tutorial/smb3_multichannel_link_aggregation) · [smb.conf manual](https://www.samba.org/samba/docs/current/man-html/smb.conf.5.html)

7. **Покрытие текущего функционального surface.** *(Resolved by A_INTEGRATION, 2026-05-18)*
   - Regressions относительно SMBJ baseline:
     - libsmbclient (google/samba-documents-provider build): теряется server-side wildcard на `query-directory` - клиент должен фильтровать после полного listing'а.
     - libsmb2 и Samba-DP libsmbclient build (по умолчанию): SMB1 отключен - регрессия для legacy NAS (Windows pre-10, старые Samba, embedded firmware <2018). SMBJ тоже SMB2-only, поэтому это нейтрально vs baseline для libsmb2, но хуже для libsmbclient если non-Samba-DP build re-enabled бы SMB1.
     - Cross-share rename: не поддерживается никем - это протокольное ограничение SMB.
   - **Bonus capability:** jcifs-ng (и codelibs/jcifs 2.x+3.x) умеют SMB1-fallback через `jcifs.smb.client.minVersion=SMB1` - это снимает технический долг в `NetworkErrorClassifier` (текущий "SMB1 not supported" branch).
   - Batch / SMB2 compound: только jNQ официально позиционируется с first-class batch-delete; SMBJ/jcifs-ng/codelibs имеют compounding plumbing внутри, но публичных batch-API нет - file-mutation-coordinator's compound-delete оптимизация остаётся не реализованной для OSS-кандидатов.
   - Auth surface: все 8 живых кандидатов поддерживают anonymous + guest + NTLMv1 + NTLMv2; Kerberos/SPNEGO - у SMBJ/jcifs-ng/jNQ/libsmb2 (последний build-time optional), у codelibs/jcifs partial.
   - Источники: [SMBJ DiskShare.list issue #163](https://github.com/hierynomus/smbj/issues/163) · [jcifs-ng issue #259 - SMB1 interop](https://github.com/AgNO3/jcifs-ng/issues/259) · [Microsoft SMB1 deprecation](https://learn.microsoft.com/en-us/windows-server/storage/file-server/troubleshoot/smbv1-not-installed-by-default-in-windows)

8. **Внешняя зависимость от native binaries.** *(Resolved by A_INTEGRATION, 2026-05-18)*
   - **Production-grade SMB sidecar для Android не существует.** Все обнаруженные «sidecar» паттерны - теоретические.
   - Hypothetical: Samba `smbclient` CLI repackaged. 3–8 МБ per ABI stripped (Samba dependency-heavy: krb5, gnutls, talloc, tdb, tevent) → ~10–20 МБ per ABI realistically, ~40–80 МБ APK growth для 4 ABI. ProcessBuilder/Runtime.exec, stdin/stdout pipe или AF_UNIX socket. Lifecycle проблематичный: lowmemorykiller убьёт child первым; Doze suppress'нёт wake-locks. Native crash debugging - tombstones в `/data/tombstones/` (root-only на prod-устройстве).
   - Hypothetical: custom AIDL service с libsmb2 в `:smbd` процессе. Bound service с `android:process=":smbd"`, AIDL+Binder, stream operations через ParcelFileDescriptor - extra hop per buffer. Размер APK ~250–500 KB per ABI (только libsmb2). Failure: service crash → все open connections die → `DeadObjectException` на in-flight Binder calls; connection-pool wrapper должен detect-and-rebuild. Binder transactions имеют 1 МБ limit - streams обязаны через PFD.
   - Verdict: sidecar даёт isolation, но за счёт IPC overhead на каждый stream-call. Для single-app архитектуры FastMediaSorter isolation benefit не нужен - sidecar отсечён из кандидатов.
   - Источники: [google/samba-documents-provider SambaClient.cpp](https://github.com/google/samba-documents-provider/blob/master/app/src/main/cpp/samba_client/SambaClient.cpp) · [Samba GPL page](https://www.samba.org/samba/docs/GPL.html) · [Android Binder TransactionTooLargeException](https://developer.android.com/reference/android/os/TransactionTooLargeException)

### 6.2 Трек B - оркестрация

9. **Какие выводы трека B остаются осмысленными после смены библиотеки.** *(Resolved by B_ORCHESTRATION, 2026-05-18)*
   - **Три из четырёх оркестрационных оптимизаций library-agnostic:** per-file timeout (item 11), partial-cache persistence (item 12), listing dedup (item 13) - полезны независимо от выбора SMB-клиента; должны ship'аться unconditionally.
   - **Один оптимизатор теряет смысл после swap:** base-concurrency tuning (item 10a, `4 → 8`) - на multi-channel pipelined клиенте или sidecar native клиенте библиотека сама мультиплексирует requests; client-side concurrency knob превращается из «throttle network» в «throttle downstream parser/RAM».
   - Per-resource override (item 10b) переживает swap, но смысл меняется: «throttle network parallelism» → «throttle downstream parser/RAM parallelism».
   - Implementation order: items 11/12/13 - немедленно (library-agnostic); item 10 - quick-win для SMBJ-era с дизайном knob'а под expectation of reinterpretation, не removal, после swap.

10. **Оптимальное значение базовой SMB-конкурентности.** *(Resolved by B_ORCHESTRATION, 2026-05-18)*
    - **Raise base concurrency 4 → 8 для header-only metadata reads на SMBJ.** Server-side budget trivially покрывает: SMB2 credit window default = 8192 grant pool / client cap = 128 (Microsoft default, MS-SMB2 §3.3.1.2); Samba `max smbd processes=0 (unlimited)`; QNAP cap = 256; Synology DSM = до 10000 SMB connections. Текущая 4 - overly conservative.
    - **CRITICAL nuance:** для full-metadata extraction (`MediaMetadataRetriever`, ~700 МБ RAM/instance) держать cap = **3**, не 8. Это Jellyfin's empirically validated NAS-safe range (issues #15728, #12203, #13531). Failure mode на over-asking - НЕ TCP-reset, а cascading read-timeouts → Skia decoder fails → recursive stack overflow → OOM. Откаченное значение S0237 `4→8` wholesale было слишком общим; правильное разделение - header-only=8, full-video-metadata=3.
    - Per-resource adaptive override: start at 8, halve on first observed read-timeout per host, halve again on next failure, never below 1, no upward auto-tune (риск oscillation).
    - Сценарийная применимость: Stay-SMBJ = high value; Multi-channel OSS swap = low value; Sidecar native swap = low value.
    - Источники: [MS-SMB2 §3.3.1.2 credit window](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-smb2/46256e72-b361-4d73-ac7d-d47c04b32e4b) · [Jellyfin#15728](https://github.com/jellyfin/jellyfin/issues/15728) · [Jellyfin#12203](https://github.com/jellyfin/jellyfin/issues/12203) · [Samba max smbd processes](https://www.golinuxcloud.com/linux-smbd-process-limit/) · [QNAP forum cap=256](https://forum.qnap.com/viewtopic.php?t=129047) · [Synology DSM SMB spec](https://www.synology.com/en-uk/dsm/7.3/software_spec/dsm)

11. **Per-file metadata timeout - значение и форма.** *(Resolved by B_ORCHESTRATION, 2026-05-18)*
    - **One-tier 1500 мс - корректное значение для EXIF / ID3 / video-header reads.** Покрывает 99%+ healthy LAN-NAS reads (P50 ≈ 30–75 мс на основе ~10–15 мс per 4–8 KiB capacity-class NAS latency) с ~x30 margin.
    - **Для video - two-tier 500/2000:** 500 мс quick-probe на file head; escalate до 2000 мс только если detected slow-path (moov-at-tail mp4 seek). Отличает «slow but correct video» от «network stall» - нельзя сделать одним порогом.
    - EXIF и ID3 - one-tier 1500 мс достаточно (всегда header-resident).
    - `MediaMetadataRetriever` особенность: нет public timeout; `finalize()` сам может зависнуть 10–60 с во время GC (coil#651, AntennaPod#2113). Wrapping в `withTimeout {}` или `Future.get(timeout, …)` - единственный safe kill switch.
    - SMBJ socket-level timeout (`withSoTimeout`) недостаточен: если hung read блокирует socket - все in-flight calls на этом socket погибают вместе. App-level coroutine `withTimeout` обязателен поверх library-level socket timeout.
    - Сценарийная применимость: essential во всех 3 сценариях; меняется только форма (coroutine `withTimeout` для SMBJ, future deadline для async OSS, per-RPC deadline для sidecar).
    - Источники: [androidx.exifinterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface) · [coil#651 - MediaMetadataRetriever finalize timeout](https://github.com/coil-kt/coil/issues/651) · [AntennaPod#2113](https://github.com/AntennaPod/AntennaPod/issues/2113) · [Glide network timeout default 10s](https://futurestud.io/tutorials/glide-4-customize-network-timeouts) · [Alibaba NAS read latency baseline](https://www.alibabacloud.com/help/en/nas/user-guide/nas-performance-overview) · [SMBJ#281 - socket timeout config](https://github.com/hierynomus/smbj/issues/281)

12. **Persistence stratagem для partial metadata.** *(Resolved by B_ORCHESTRATION, 2026-05-18)*
    - **Cache as `partial`, surface to UI, retry on next scan.** Это industry pattern (Plex, Jellyfin). Откаченное решение S0237 (НЕ кэшировать timeout-fallback) противоречит этому паттерну и приведёт к худшему UX («слот-NAS - список вечно пустой пока все файлы не прочитаются полностью»).
    - Schema: `metadataState ∈ {COMPLETE, PARTIAL, BROKEN}`. PARTIAL → retry on every scan (cheap O(1) status check). BROKEN → retry every N scans или user-triggered refresh.
    - **NO TTL** на partial: user-driven scan cadence - естественный retry bound; clock-based TTL добавляет dependency без решения underlying UX issue.
    - Storage cost: ~200 байт per partial row × 10000 файлов = 2 МБ. Negligible.
    - Сценарийная применимость: essential во всех 3 сценариях. Library swap reduces frequency партиальных hit'ов, но не eliminates the code path (network stalls, NAS sleep, `MediaMetadataRetriever` quirks survive any swap).
    - Источники: [Plex: Scan vs Refresh](https://support.plex.tv/articles/200289306-scanning-vs-refreshing-a-library/) · [Plex matching process](https://support.plex.tv/articles/200889878-matching-process/) · [Jellyfin#11923 - manual refresh-metadata replace-all](https://github.com/jellyfin/jellyfin/issues/11923) · [Jellyfin#12269 - outdated metadata displayed](https://github.com/jellyfin/jellyfin/issues/12269) · [Glide caching (partial-cache served)](https://bumptech.github.io/glide/doc/caching.html)

13. **Уровень dedup листинга и его границы.** *(Resolved by B_ORCHESTRATION, 2026-05-18)*
    - **Primary fix - устранить double-call upstream в координаторе.** Memoization-at-scanner маскирует баг, locks it in forever, и breaks в non-obvious случаях (after refresh, after navigation, after process death). Откаченное S0237-решение (свёртка в scanner'е) было неправильным - оно прятало root cause.
    - **Secondary belt-and-braces - cheap in-flight coalescer в scanner:** `ConcurrentHashMap<DirPath, Deferred<ListResult>>`, entry-removed-on-completion (НЕ TTL). Это in-flight coalescing (Coil's `DeDupeConcurrentRequestStrategy` model), не result-caching. Zero staleness risk.
    - Log dedup hits at `DEBUG` - следующие регрессии становятся visible сразу, а не растворяются.
    - Сценарийная применимость: upstream fix - mandatory во всех 3 сценариях (каждый duplicate listDir = wasted bytes на wire для SMBJ / wasted async ops для multi-channel / wasted IPC round-trip для sidecar). In-flight coalescer - useful во всех 3 (cheap, library-agnostic).
    - Источники: [Coil DeDupeConcurrentRequestStrategy](https://coil-kt.github.io/coil/changelog/) · [Coil#527](https://github.com/coil-kt/coil/issues/527) · [Glide caching](https://bumptech.github.io/glide/doc/caching.html) · [Anti-pattern: debounce as bug-mask](https://dev.to/trademark18/debounce-in-event-driven-serverless-nio) · [Inngest: debouncing in queue systems](https://www.inngest.com/blog/debouncing-in-queuing-systems-optimizing-efficiency-in-async-workflows) · [JuiceFS directory entry cache](https://juicefs.com/docs/community/guide/cache/)

### 6.3 Pre-decision practical spike

До любого commitment'а к §2.8 - **обязательный 30-минутный buffer-tuning spike на SMBJ**: установить `SmbConfig.builder().withReadBufferSize(1_048_576).withWriteBufferSize(1_048_576).build()`, поднять consumer-side `BufferedInputStream` chunk до 64 KiB, перезамерить throughput на типовом ресурсе.

Логика: `jcifs-ng#106` показывает SMBJ ≥25 МБ/с на 1 ГБ upload - наш «<2 МБ/с» либо artifact app-side wiring, либо специфики маленьких/смешанных файлов.

- **Если throughput подскочит до ≥20 МБ/с** - library replacement излишен, оставляем SMBJ + items 10/11/12/13 из трека B.
- **Если плато на ~25 МБ/с** - реальный library-ceiling, переход на codelibs/jcifs 3.0 (low-risk Java) или libsmb2+JNI (high-ceiling native) становится экономически обоснованным.
- **Если throughput остаётся «<5 МБ/с»** - проблема ни в библиотеке, ни в буферах; искать в `ConnectionThrottleManager`, в media-scan flow, в IdleDisconnect-race S0228, либо в среде владельца (AP throttling, Defender lock на хосте).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Ни один кандидат из трека A не даёт измеримого прироста relative to baseline. | Средняя | Замена библиотеки исключается; трек B становится единственным источником улучшений. | Принять как валидный исход; решение в §5.3 учитывает этот сценарий явно. |
| Лучший по скорости кандидат имеет лицензию, несовместимую с Google Play. | Высокая | Замена возможна только в `noLegal`-канале; market-flavors остаются на текущем стеке; раздваивается поддержка. | Зафиксировать архитектуру с интерфейсом и flavor-specific биндингом, чтобы поддержка двух реализаций оставалась дешёвой. |
| Лучший кандидат - коммерческий SDK с дорогой/жёсткой EULA, несовместимой даже с личной sideload-сборкой. | Низкая–Средняя | Кандидат исключается; shortlist становится короче. | На этапе license-classification сразу проверять EULA; не вкладываться в технический анализ кандидата до подтверждения license-fit. |
| Кандидат с native-частью увеличивает размер APK сверх допустимого для `standard`. | Средняя | Невозможно использовать его в market-канале; вынужденный fallback на `noLegal`-only. | Учитывать размер APK как явный фильтр; для `noLegal` ограничение мягче. |
| Кандидат имеет открытые CVE или неактивный maintainership. | Средняя | Замена приведёт к security/supply-chain regression вместо улучшения. | На этапе health-filter жёстко исключать таких кандидатов вне зависимости от их производительности. |
| Бенчмарк-методология приведёт к ложно-положительному выводу из-за неверной среды измерения (warm cache, лимиты антивируса/Defender на хосте, AP throttling). | Средняя | POC покажет нереальный прирост, который потом не воспроизведётся у конечных пользователей. | Спецификация бенчмарка в §5.1 обязана требовать warm-up runs, multiple iterations, явный baseline текущего стека на той же сети, контроль внешних факторов. |
| API кандидата требует переписывания значительной части обвязки (mutation-coordinator, error-classifier, connection-pool). | Средняя | Стоимость интеграции делает кандидата нецелесообразным даже при хорошей производительности. | На этапе integration-cost фиксировать интеграционную стоимость в качестве отдельной метрики, сравнимой с приростом производительности. |
| Оркестрационная оптимизация из трека B (например, повышение конкурентности до 8) реализуется до завершения трека A и оказывается избыточной после смены библиотеки. | Высокая | Двойная работа, путаница в конфигурации, ненужный риск refusal на слабых NAS. | Эта спека прямо запрещает реализацию оркестрационных оптимизаций до принятия решения по §5.3. |
| Per-file metadata budget с тихим fallback маскирует реальные проблемы транспорта. | Низкая | Скрытое снижение качества metadata для целого ресурса. | На этапе бенчмарк-плана зафиксировать требование агрегированной per-scan метрики «N файлов попали в fallback» и порог warn-сигнала. |
| Дедупликация листинга маскирует баг во внешнем координаторе, который вообще не должен вызывать его дважды. | Средняя | Скрываем root cause. | Перед свёрткой залогировать факт дублирующего вызова один раз, затем чинить координатор отдельным фиксом. |
| Двухфазный показ списка ломает текущие сортировки, зависимые от metadata (DATE_ASC требует EXIF). | Средняя | Пользователь видит «не до конца отсортированный» список несколько секунд. | На этапе ресёрча трека B зафиксировать поведение и решить, нужно ли для metadata-зависимых сортировок выполнять enrichment до отображения. |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Эта спека не вводит и не меняет видимое поведение для пользователя. Если по итогам POC прирост окажется значимым и стабильным, соответствующая follow-up-спека отдельно решит, попадает ли упоминание ускорения в публичный `docs/FEATURES.md` (для `standard`-канала) или в локальный `docs/FEATURES_noLegal.md` (для `noLegal`-канала) - это решение не принимается на уровне S0246.

---

## 9. Архитектурные решения (ADR)

ADR нет - все архитектурные решения откладываются до POC-спеки и/или до отдельной implementation-спеки по оркестрации. Эта спека только фиксирует методологию ресёрча, два независимых трека и список открытых вопросов.

---

## 10. Связи с другими спеками

- **Parent epic:** `S0156` (nolegal-capability-surface-audit) - S0246 является конкретным кластером ресёрча в рамках общего аудита `noLegal`-capability surface. S0246 покрывает один из явно перечисленных в S0156 §3.1 классов улучшений: «замена оправдана только при реальном приросте функциональности, покрытия форматов или качества», в данном случае - прирост throughput SMB-клиента и потенциальный прирост latency первого скана. Принципы изоляции и distribution-граничения, заданные в S0156, применяются к S0246 без изменений.
- **Absorbed (Archived):** `S0237` (smb-first-scan-speedup) - был самостоятельной тактической спекой по двухфазному показу списка, повышению конкурентности до 8, per-file budget 1500 мс и dedup листинга. Имплементация откачена 2026-05-18, тикет архивирован. Все цели и research-items S0237 переплавлены в трек B этой спеки (§5.2, §6.2). Причина merge'а - нельзя принимать решения по оркестрации до знания, какие из них переживут смену библиотеки.
- **Related (исторические):** `S0056` (Archived) - `smb-scan-slowness-investigation` - исследование тех же симптомов first-scan latency на более раннем этапе; артефакты archived, выводы переплавляются в трек B. `S0169` (Archived) - `bugfix-audio-metadata-retriever-smb-excessive-warnings` - устраняла warn-спам; per-file timeout в треке B должен сохранять тот же тихий профиль.
- **Related (активные):** `S0229` - `bugfix-smb-audio-metadata-browse-instability` (BlockNeedUserTest). Стабилизирует browse-side metadata enrichment в idle-after-scroll режиме. Любая реализация трека B обязана согласовать background-pipeline с S0229, чтобы две оптимизации не конкурировали за `ConnectionThrottleManager`. `S0228` - `bugfix-smb-idle-disconnect-timer-race` (BlockNeedUserTest). Стабилизирует timer-race в idle-disconnect; повышение конкурентности из трека B должно учитывать гарантии S0228.
- **Spawns (active children):**
  - **S0247** - `smb-buffer-tuning-spike` (Approved, Priority 80, Tier 1). Реализует §6.3 spike. Блокирует трек (a) - POC замены библиотеки нельзя запускать до того, как spike подтвердит, что текущий стек упирается в library-ceiling.
  - **S0248** - `smb-orchestration-optimizations` (Approved, Priority 65, Tier 3). Реализует трек (b) - corrected версии items 10/11/12/13 (per-file timeout, partial-cache, listing dedup, concurrency split с header=8/full-video=3). Не зависит от S0247 - запускается параллельно.
- **Не блокирует и не блокируется:** ни одна другая активная спека не блокирует S0246 и не блокируется S0246 напрямую. Любая follow-up POC-спека замены библиотеки (трек a) будет создана как отдельный тикет после S0247 spike-результата.

---

## 11. Критерии готовности (strategic-level)

1. **CLOSED.** Реестр кандидатов SMB-библиотек для Android собран: 14 кандидатов всего, 8 живых и техноспособных (см. §6.1.1).
2. **CLOSED.** Capability-матрица по throughput-факторам и health-метрикам зафиксирована (см. §6.1.4).
3. **CLOSED.** Спецификация бенчмарк-методологии готова: §5.1 (Benchmark plan), §6.3 (pre-spike процедура на SMBJ).
4. **CLOSED.** Shortlist:
   - `market-safe`: SMBJ 0.14.0 (incumbent); Apache-2.0-wrappers без независимой ценности.
   - `nolegal-only-open-source`: codelibs/jcifs 3.0 (low-risk Java, требует Java-17-bytecode-spike); libsmb2+JNI (high-ceiling native, 1–2 недели DIY); jcifs-ng 2.1.10 (backup).
   - `nolegal-only-commercial`: пуст - accessible self-serve дверь не существует; enterprise-only (jNQ/YNQ/Tuxera) недостижимы в текущей модели работы владельца.
5. **CLOSED.** Integration-cost оценён (см. §6.1.5): jcifs-ng / codelibs/jcifs = low; libsmbclient JNI = medium; libsmb2 = high; sidecar = unjustified.
6. **CLOSED.** Все 8 items §6.1 (трек A) закрыты как `Resolved` с явными ссылками на источники.
7. **CLOSED.** Все 5 items §6.2 (трек B) закрыты как `Resolved` - для каждой из четырёх оркестрационных оптимизаций зафиксирован 3-сценарный applicability matrix (Stay-SMBJ / Multi-channel-OSS-swap / Sidecar-native-swap).
8. **CLOSED (2026-05-18).** Owner выбрал **путь (c) - оба трека параллельно**:
   - Трек (b) запущен немедленно как **S0248** (`smb-orchestration-optimizations`, Approved, Priority 65, Tier 3) - реализует corrected версии items 10/11/12/13.
   - **S0247** (`smb-buffer-tuning-spike`, Approved, Priority 80, Tier 1) - параллельный pre-decision spike по §6.3, блокирует только трек (a).
   - Трек (a) для `noLegal` (POC замены библиотеки на codelibs/jcifs 3.0 или libsmb2+JNI) запускается отдельным Sxxxx-тикетом после spike-результата S0247: если spike даёт «≥ 20 МБ/с» - трек (a) отменяется; если плато ~25 МБ/с или throughput остаётся `< 5 МБ/с` после неfound app-side bottleneck'а - отдельный POC-тикет создаётся с приоритетом по §6.1.4 capability matrix.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: **buffer-tuning spike на SMBJ** (§6.3) - обязательная пред-решение проверка, занимает ~30 минут на устройстве владельца. Только после spike - owner принимает один из четырёх путей §2.8.

В зависимости от пути:

- **(a) POC замены библиотеки** → новый Sxxxx-тикет с приоритетом по §6.1.4 capability matrix; кандидаты - codelibs/jcifs 3.0 (low-risk Java) или libsmb2+JNI (high-ceiling native). Реализация - строго в `noLegal`-флейворе через flavor-specific Hilt-binding (`src/noLegal/java/`), интерфейс SMB-клиента в `src/main` (§5.4).
- **(b) оркестрационные оптимизации поверх текущего стека** → новый Sxxxx-тикет, который implement'ит items 11/12/13 (library-agnostic) + item 10 (SMBJ-era only); явно с **header-only-vs-full-metadata split** для конкурентности (item 10) - не общая `4→8`. Этот тикет может быть запущен параллельно с любым другим путём.
- **(c) оба трека параллельно** → два Sxxxx-тикета; оркестрационный (b) запускается немедленно, library-POC (a) - после spike-результата.
- **(d) ни один из треков** → spec закрывается как «текущий стек оптимален»; CHANGELOG-запись фиксирует архитектурное решение.

`/spec-tech S0246` создаст `PLAN/S0246_smb-performance-research/` с фазами POC, но только после явного выбора (a) или (c) - для путей (b) и (d) новые тикеты выпускаются отдельно, не как тактика S0246.

**Final decision (2026-05-18, owner):** путь (c) - оба трека параллельно.

- Трек (b) - `S0248` (`smb-orchestration-optimizations`, Approved, Priority 65, Tier 3).
- Трек (a) pre-decision spike - `S0247` (`smb-buffer-tuning-spike`, Approved, Priority 80, Tier 1).
- Трек (a) follow-up (POC замены библиотеки в `noLegal`) - отдельный тикет, создаётся только если spike S0247 даёт результат B или C (см. S0247 §2.3).

Тактическая спецификация для самой S0246 не пишется: реализация делегирована S0247 + S0248, S0246 остаётся research deliverable'ом.

---

## Last Audit

**Date:** 2026-05-18  
**Verdict:** Verified (research-only, no code in scope).

**Checked:**

- §3.2 hard constraint «Без правок кода» соблюдён - никаких изменений в `app_v2/`, `wear/`, `build.gradle.kts`, `res/`, скриптах. Spec - чистый research deliverable.
- §6 - все 13 research items закрыты как `Resolved` (8 в треке A: §6.1.1..§6.1.8; 5 в треке B: §6.2.9..§6.2.13). Каждый ответ снабжён ссылками на первичные источники (MS-SMB2, GitHub issues, vendor docs, Jellyfin issues).
- §11 - все 8 strategic-criteria помечены `CLOSED` со ссылками на §6.* подразделы.
- §2.8 (Goal 8: owner decision) - путь (c) явно зафиксирован в §11.8 и §12 (Final decision block).
- Follow-up specs существуют и в статусе `Approved`: `S0247` (spike), `S0248` (orchestration impl). Library-swap POC отложен до результата S0247.

**Action items:** нет. Research deliverable полностью выполнен; user-visible изменений не вводилось; функциональный лог не требуется (нет пользовательского behaviour change'а).
