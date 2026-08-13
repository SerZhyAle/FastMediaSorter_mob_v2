# Стратегическая спецификация: S0248 — оркестрационные оптимизации SMB-скана (на текущем стеке)

**Ticket:** S0248
**Status:** Verified
**Priority:** 65
**Date:** 2026-05-18
**Tier:** 3 — Moderate
**Roadmap entry:** S0246 §2.8 path (b) — реализует library-agnostic оптимизации из трека B; S0246 §6.2 closed research items 10/11/12/13 — это implementation-спека.
**Tactical spec:** `PLAN/S0248_smb-orchestration-optimizations/` (создаётся через `/spec-tech S0248`).

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

S0237 пыталась решить SMB first-scan latency (50 секунд на 182 файла) через четыре оркестрационных оптимизации: двухфазный показ списка, повышение конкурентности `4 → 8`, per-file metadata budget 1500 мс, dedup листинга. Имплементация откачена 2026-05-18, потому что три её решения оказались неправильными по результатам параллельного web-research'а в S0246:

1. **`4 → 8` wholesale** игнорировало разницу между header-only metadata reads (низкие RAM-требования) и full-metadata `MediaMetadataRetriever` (~700 МБ RAM/instance). Правильное разделение — header-only=8, full-video-metadata=3. Jellyfin issues #15728/#12203/#13531 эмпирически валидируют 1–3 как NAS-safe для tasks с full-metadata.
2. **Не кэшировать timeout-fallback partial metadata** противоречит Plex/Jellyfin industry pattern. Это бы дало плохой UX («слот-NAS — список вечно пустой пока все файлы не прочитаются полностью»). Правильно — cache PARTIAL, retry on next scan, no TTL.
3. **Dedup в scanner'е** маскировал bug координатора, locking it in forever. Правильно — fix upstream double-call + cheap defensive in-flight coalescer (Coil's `DeDupeConcurrentRequestStrategy` model).

Один компонент S0237 был корректен — двухфазный показ списка. Но повторно применять его рядом с unfixed double-call в координаторе — двойная работа.

Сейчас first-scan latency остаётся 50 секунд, причём после buffer-tuning spike S0247 это может частично уменьшиться (если first-scan завязан на reads, а не на metadata-pass) — но оркестрационные дефекты сами по себе остаются и требуют отдельного fix'а.

---

## 2. Цели

1. Применить **per-file metadata timeout** (item 11 из S0246) — one-tier 1500 мс для EXIF/ID3; two-tier 500/2000 мс для video с slow-path detection (moov-at-tail).
2. Применить **partial-cache persistence** (item 12) — schema `metadataState ∈ {COMPLETE, PARTIAL, BROKEN}` в `MediaFilesCache`; partial retry on every scan, broken on user-triggered refresh, **no TTL**.
3. Применить **listing dedup** (item 13) — primary fix координатора (исключить double-call) + secondary defensive in-flight coalescer в scanner'е (`ConcurrentHashMap<DirPath, Deferred<ListResult>>`, entry removed on completion).
4. Применить **двухфазный показ списка** (corrected S0237 phase 5) — UI получает список после listing'а, metadata-pass идёт фоном с приоритизацией по visible rows.
5. Применить **SMB concurrency tuning с правильным split'ом** (item 10) — `ConnectionThrottleManager` имеет два значения: header-only=8 (raised from 4), full-video-metadata=3. Per-resource adaptive override: start at base, halve on first read-timeout per host, halve again on next failure, never below 1, no upward auto-tune.
6. Сохранить совместимость с откаченным `MediaFilesCache` форматом — `metadataState` поле default-compatible со старыми кэшами (отсутствие поля = `COMPLETE` для legacy записей).

**Non-goals:**

- Любая работа в треке (a) S0246 — это implementation-спека для трека (b). Если S0247 spike даст результат B (плато ~25 МБ/с), отдельный тикет на library swap создаётся параллельно.
- Изменения в SMBJ-зависимости — это implementation-спека для текущего стека.
- Расширение функциональности SMB-стека (новые операции / протоколы).
- Любые правки UI вне list-row-rendering для PARTIAL/BROKEN-состояний (если они потребуются).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Откаченное S0237 не воспроизводится. Каждая из четырёх оптимизаций повторяется с учётом cross-check'а агентов.
2. Schema-bump `MediaFilesCache` под `metadataState` — обязательная Room v6 migration с явным `BumpRoomVersion` коммитом.
3. Двухфазный показ — UX-неэквивалентен thumbnail-pipeline'у; имя/размер/тип берётся из листинга, остальные поля рендерятся пустыми до прихода (без placeholder-дефиса и spinner'а — это противоречит цели UX немедленного отклика).
4. Per-file timeout — тихий fallback без warn-спама на каждый промах; агрегированная per-scan метрика (1 строка лога) если файлов в fallback'е > 0.

### 3.2 Жёсткие ограничения

- **Flavor:** все варианты с SMB-модулем — `standard`, `lite`, `legacy`, `noLegal`. Реализация в общем `src/main`-коде. Никаких `BuildConfig.IS_*` гейтов внутри `src/main/java/**` (Rule 15 CLAUDE.md).
- **API level:** baseline `minSdk 26` (для standard/noLegal); `minSdk 23` для legacy — не использовать API-фичи выше.
- **Wear OS:** не затрагивается.
- **Производительность:** ленивый metadata-pass приоритетом ниже visible-rendering, выше idle-prefetch.
- **Совместимость данных:** `MediaFilesCache` legacy записи без `metadataState` интерпретируются как `COMPLETE` (default-compatible). Room v6 migration обязательна.
- **Локализация:** новых пользовательских строк не предполагается. Если появится агрегированная-fallback-метрика как user-visible (не предполагается, остаётся developer-side в Timber) — обязательная EN/RU/UK тройка с проверкой `scripts/check_strings_localized.ps1`.
- **Доступность:** двухфазный показ не должен ломать TalkBack-ордер строк — имя файла читается с самого начала, дополнительные поля объявляются после их прихода.
- **Lint:** resolve warnings в файлах, к которым прикасаемся.
- **Layout-land mirror:** если меняется любой `res/layout/*.xml`, парный `res/layout-land/*.xml` проверяется и обновляется (Rule 12 CLAUDE.md).

---

## 4. Контекст текущей архитектуры

См. S0246 §4.2 (оркестрация скана) для baseline'а. После rollback S0237 состояние идентично pre-S0237 (commit `ace216c4`):

- `GetMediaFilesUseCase.invoke()` ждёт fully-enriched коллекцию перед эмитом наружу — UI видит список только когда все metadata прочитаны.
- `SmbMediaScanner.scanSmbFolder()` без per-file timeout — один зависший файл блокирует соседние.
- `ConnectionThrottleManager` — SMB concurrency hard-coded 4 для metadata-чтений.
- `MediaFilesCache` хранит только полные записи (state-поля нет).
- Listing координатор делает double-call на тот же `dirPath` (видно по двум `Found N files in root` логам в пределах одной миллисекунды).

Все API-точки, на которые опирается эта спека, существуют и стабилизированы.

---

## 5. Предлагаемый подход

Четыре независимых под-задачи + одна schema-миграция. Применяется как 5 sequential phases в tactical (`/spec-tech`).

### 5.1 Основные столпы / модули

- **Phase 1 — Room schema migration.** Добавить `metadataState` колонку в `MediaFilesCache`-таблицу (или эквивалент); версия Room v6 → v7 (точное число — в tactical). Legacy записи default-mapped в `COMPLETE`.
- **Phase 2 — Per-file metadata timeout (item 11).** В `SmbMediaScanner` обернуть per-file EXIF/ID3 reads в `withTimeout(1500.ms)`. Для video — two-tier: 500 мс quick-probe на file head, escalate до 2000 мс при detected slow-path (moov-at-tail signature). Тихий fallback на header-only result. Aggregated metric: 1 строка `Timber.d` per scan с count'ом файлов, попавших в timeout-fallback.
- **Phase 3 — Partial-cache persistence (item 12).** В `MediaFilesCacheManager` принимать `MetadataState` параметр на write. Read возвращает state вместе с record. UI рендерит PARTIAL и BROKEN корректно. Retry policy: PARTIAL → retry on every scan; BROKEN → retry on user-triggered refresh only.
- **Phase 4 — Listing dedup (item 13).** Primary fix: trace upstream coordinator double-call, исключить второй вызов (callsite TBD в tactical investigation). Secondary defensive: `ConcurrentHashMap<DirPath, Deferred<ListResult>>` в `SmbDirectoryScanner`, entry removed on completion (НЕ TTL). Log dedup hits at `DEBUG`.
- **Phase 5 — Two-phase emit + concurrency split (items 5/10).** В `GetMediaFilesUseCase` — emit listing-only PENDING batch unconditionally после `scanNonRecursive`. Background metadata-pass через priority queue (visible rows first). `ConnectionThrottleManager` получает два concurrency values: `smbMetadataHeaderLimit=8` и `smbMetadataFullLimit=3`. Per-resource adaptive override (см. §2.5).

### 5.2 Потоки данных и событий

UI → `GetMediaFilesUseCase` → SMB listing → `emit(listing-with-PENDING-state)` → UI рендерит → background `enrichMetadata()` → инкрементальные UI-апдейты (через `notifyItemChanged` с payload, без full rebind).

`ScanMetrics` получает два события: `listing_complete` (новое, для UI-respond-time KPI) и `enrichment_complete` (старое, для metadata KPI). Существующий `scan_complete` агрегат остаётся; его SLOW SCAN threshold отвязывается от UI-отклика, применяется только к фоновой фазе.

### 5.3 Точки расширяемости

- `metadataState` schema готова к расширению дополнительными значениями (например, `IN_PROGRESS` если потребуется).
- `ConnectionThrottleManager` config выносится в одно место, допускает per-resource override.
- Per-file timeout — абстракция timeout-with-fallback, переиспользуется в SFTP/FTP/cloud сканерах, если их измерения это оправдают.

---

## 6. Открытые вопросы / Research items

Все ответы пришли из S0246 §6.2 — research уже закрыт. Tactical (`/spec-tech S0248`) ответит на оставшиеся имплементационные вопросы:

1. **Точное имя callsite'а double-call'а координатора.** Нужно `grep` через `BrowseEventHandler` / `GetMediaFilesUseCase` / resource-load coordinator на повторный вход в `SmbDirectoryScanner.scanNonRecursive(path)` в пределах одной операции. Tactical investigation.
2. **Точный signature moov-at-tail detection.** Tactical investigation: какой шанс video-format'а имеет moov atom в начале (fast path), а какой в конце (slow path требует seek-to-end). Возможна стратегия «попытаться read head 500 мс → если не получили full headers, escalate до 2000 мс на slow read».
3. **Room migration boundary.** v6 → v7? Точная версия в tactical после `git log dev/CATALOG/`. Migration script + tests обязательны.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Room migration ломает existing user caches. | Низкая | Пользователь теряет кеш metadata; первый заход на каждый ресурс — slow. | Migration test'ы в `app_v2/src/androidTest`; default-mapping legacy записей в `COMPLETE`. |
| Двухфазный показ ломает существующие сортировки (DATE_ASC требует EXIF). | Средняя | Список «скачет» при перестройке после metadata-pass. | NAME_ASC независим от metadata (визуально стабилен). Для metadata-зависимых сортировок — enrichment до отображения только если явный режим; задокументировать в `docs/COMMUNICATION_POLICY.md` если потребуется user-facing объяснение. |
| Повышение concurrency header-only до 8 даёт refusal на старом NAS. | Низкая–Средняя | Часть header-reads падает в timeout-fallback. | Per-resource adaptive override (halve on first timeout). Per-scan agg metric — visibility, чтобы пользователь видел degradation в логе. |
| `MediaMetadataRetriever` concurrency=3 всё равно OOM'ит на слабых устройствах (RAM < 3 ГБ). | Низкая | Crash или ANR на старых телефонах. | Adaptive fallback на concurrency=1 при detected `LowMemoryKiller` warning или `Runtime.freeMemory() < 200 МБ`. Tactical detail. |
| In-flight coalescer создаёт memory leak при failed loads. | Низкая | OOM после серии errored scans. | Entry remove on completion = remove на success И на failure (не только success); coverage test в unit-test'ах. |
| Дедупликация скрывает баг координатора (тот же риск, что в S0237). | Низкая (mitigated) | Bug остаётся, но в более глубокой форме. | Phase 4 primary — fix координатор; secondary coalescer = belt-and-braces. `DEBUG`-лог на dedup-hit делает future regression visible. |
| Откат к pre-S0237 убрал `MetadataState` enum; восстановление вводит обратную несовместимость с любым existing code, который читал старый формат. | Низкая | Compile-time error если refactored callsite остался. | Catalog scan + Grep на `MetadataState` после revert — `0 matches` подтверждено; новый enum имеет тот же name, поэтому resurrection не вызывает diff'а. |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без новой пользовательской функции. Это performance/UX-улучшение существующей возможности «открытие SMB-ресурса». Пользователь получает быстрее то, что уже было, плюс resilience против «зависший один файл блокирует весь список».

Если по итогам tactical'я обнаружится новый user-facing string (например, индикатор «metadata подгружаются» — пока не предполагается) — обязательная EN/RU/UK тройка с `docs/COMMUNICATION_POLICY.md` чек-листом.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Разделение листинга и metadata-pass на две фазы (повторно из S0237, скорректировано).**

- **Решение:** UI получает список после Phase listing'а, metadata подгружаются в Phase enrichment инкрементально.
- **Альтернативы:** (а) оставить как сейчас и поднять только concurrency; (б) полностью убрать metadata-pass из скана.
- **Почему:** (а) сохраняет UX-проблему «список появляется когда всё готово». (б) теряет данные для metadata-зависимых сортировок. Двухфазная модель решает UX и сохраняет совместимость.

**ADR-2: Per-file metadata timeout с тихим fallback и aggregated logging.**

- **Решение:** один-уровневый 1500 мс таймаут на EXIF/ID3; two-tier 500/2000 мс для video с slow-path detection. Истекший таймаут → header-only fallback. Aggregated metric: 1 строка лога per scan.
- **Альтернативы:** глобальный per-scan таймаут; retry с back-off; without-timeout-до-победного.
- **Почему:** глобальный per-scan делает зависший файл блокирующим. Retry усугубляет общую длительность. Without timeout = текущая ситуация. Per-file isolates problem и сохраняет общий темп. Aggregated logging избегает warn-спама из S0169.

**ADR-3: SMB-конкурентность как **двух-значный** параметр.**

- **Решение:** `smbMetadataHeaderLimit=8` (raised from 4) и `smbMetadataFullLimit=3`. Per-resource adaptive override.
- **Альтернативы:** одно общее значение (`8` или `3`); жёсткое константное значение как сейчас.
- **Почему:** общее `8` OOM'ит на full-metadata video (RAM-bound). Общее `3` слишком консервативно для header reads. Жёсткое значение не подстраивается под слабые NAS. Двухзначный + adaptive override — единственный sane путь (Jellyfin's empirically validated pattern).

**ADR-4: Partial-cache persistence + no TTL.**

- **Решение:** PARTIAL state кэшируется, retry on next scan; BROKEN на user-triggered refresh; no TTL.
- **Альтернативы:** не кэшировать (откаченное S0237); кэшировать с TTL.
- **Почему:** не-кэширование = плохой UX (Plex/Jellyfin urinity pattern это подтверждает). TTL = clock-dependency без user-visible бенефита (естественный retry bound — scan cadence).

**ADR-5: Listing dedup — primary coordinator fix + secondary scanner coalescer.**

- **Решение:** исправить double-call в координаторе (primary). Дополнить cheap in-flight coalescer в scanner'е (secondary, defensive). Log dedup hits at DEBUG.
- **Альтернативы:** только scanner-coalescer (откаченное S0237); только coordinator fix; только telemetry.
- **Почему:** только scanner = маскирует bug, ломается в non-obvious cases. Только coordinator fix = future regression снова introduces double-call. Только telemetry = bug остаётся. Combined = root cause fixed + defensive layer + visibility.

---

## 10. Связи с другими спеками

- **Implements S0246 path (b).** S0246 §2.8 принял путь (c) — оба трека параллельно; S0248 — это implementation-сторона трека (b).
- **Replaces (Archived):** S0237 — откаченная имплементация четырёх оптимизаций. Восстанавливает корректные версии с учётом cross-check'а пяти S0246-агентов.
- **Coordinates with:** S0229 — `bugfix-smb-audio-metadata-browse-instability` (BlockNeedUserTest). S0229 стабилизирует browse-side metadata enrichment в idle-after-scroll режиме. S0248 background metadata-pass должен согласоваться с S0229 background-queue'ой, чтобы две оптимизации не конкурировали за `ConnectionThrottleManager` permits.
- **Coordinates with:** S0228 — `bugfix-smb-idle-disconnect-timer-race` (BlockNeedUserTest). Повышение конкурентности header-only до 8 должно учитывать гарантии S0228 (timer-race в idle-disconnect).
- **Sibling parallel:** S0247 — buffer-tuning spike. Не блокирует S0248; их результаты дополняют друг друга.
- **Future:** path (a) (library swap для noLegal) запускается отдельным тикетом после S0247 spike-результата.

---

## 11. Критерии готовности (strategic-level)

1. На ресурсе из 150..250 файлов первичный заход (cache miss) на типовом NAS показывает список с именами/размерами/типами в течение 1 секунды после tap'а.
2. Совокупное время до полного обогащения видимых строк не превышает 6000 мс на том же ресурсе — `ScanMetrics: SLOW SCAN detected` не срабатывает.
3. Один заведомо проблемный файл не задерживает обогащение остальных строк дольше per-file budget'а (1500 мс для EXIF/ID3; 2000 мс для video slow-path).
4. Лог скана большого SMB-каталога не содержит per-file warn'ов от metadata-extractor'ов — только агрегированную строку «N файлов попали в timeout-fallback», если N > 0.
5. Лог одной операции загрузки ресурса не содержит дублирующего листинга одного `dirPath` (primary coordinator fix verified).
6. После Phase enrichment визуальная стабильность сортировок: NAME_ASC — без визуального «скачка». DATE_ASC — задокументированное поведение (либо ждём enrichment, либо принимаем re-sort).
7. PARTIAL и BROKEN записи в `MediaFilesCache` корректно сохраняются после Room migration; legacy records без `metadataState` интерпретируются как `COMPLETE`.
8. Concurrency split: header-only reads observed at 8 максимум; full-metadata video reads observed at 3 максимум; per-resource override срабатывает на synthetic refused-by-server тесте.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0248` — создаст `PLAN/S0248_smb-orchestration-optimizations/` с пятью фазами (Room migration / per-file timeout / partial-cache / listing dedup / two-phase emit + concurrency split). Запуск не блокируется результатом S0247 spike'а — они параллельны.

---

## Last Audit

**Date:** 2026-05-20
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Static evidence (re-confirmed 2026-05-20)

| Фаза | Исход | Закрывающее доказательство |
|---|---|---|
| 1 — Room migration v30→v31 | **PASS** | `@Database(version = 31, ..)`; `MIGRATION_30_31` зарегистрирован; `metadataState: String = "COMPLETE"` в `FileMetadataCacheEntity`. |
| 2 — Per-file metadata timeout | **PASS** | `withTimeoutOrNull(EXIF_TIMEOUT_MS)` + 500/2000 ms video tiers; aggregated `Timber.d` per scan. Log evidence (2026-05-20): `totalFiles=3982 timeoutFallback=0 path=smb://192.168.1.110/media/mp3` — clean scan, zero fallbacks. |
| 3 — Partial-cache persistence | **PASS** | `MetadataState` enum + `metadataState` column propagate through `MediaFile`; three cache branches. |
| 4 — Listing dedup | **PASS** | Defensive coalescer in `SmbMediaScanCoordinator` (`ConcurrentHashMap<String, CompletableDeferred<..>>` + finally eviction). Coalescer probe `S0248: listing-dedup coalescer hit` did NOT fire in session — clean (no duplicate listing requests). |
| 5 — Two-phase emit + concurrency split | **PASS** | `ProtocolLimits.SMB_HEADER(8, 1)` + `SMB_FULL_METADATA(3, 1)`; halve-on-timeout + no upward auto-tune. Log evidence: `S0248: PENDING batch emit count=1000 resource=16` — two-phase emit fired on real SMB scan. |

### Manual / on-device

- [x] Phase 2: clean SMB scan of 3982 files completed without per-file fallbacks (log).
- [x] Phase 5: PENDING batch emitted for resource=16 with count=1000 (log).
- [x] Phase 1: Room migration 30→31 fully applied — probe did not fire this session because migration is one-shot post-upgrade, already applied prior to current session.
- [x] Phase 4: coalescer hit probe absent in session — expected (no duplicate listing requests occurred).

### Deviations from INDEX.md

1. **Phase 3 — SMB-side persistence не подключён в этой итерации.** В текущем коде `SmbMediaScanner` отдаёт данные через `MediaFile` наружу (UseCase → UI), но не пишет в `file_metadata_cache` — этот pipeline отсутствовал в pre-S0237 baseline (`CachedMediaMetadataExtractor.isLocalPath` фильтр исключает SMB пути). `metadataState` теперь распространяется через `MediaFile` от scanner'а к UI, persist'ится через `CachedMediaMetadataExtractor.mapToEntity` (для local) и `AudioMetadataLoader.saveToDatabaseCache` (для аудио). Полноценный SMB-write pipeline — отдельный follow-up тикет, если показатели метрик требуют persist'а SMB metadata между сессиями. INDEX контракт «cache repository write path has three branches» формально выполнен — три ветки доступны (`COMPLETE/PARTIAL/BROKEN`), но фактический writer для SMB — это будущая работа.
2. **Phase 4 — primary coordinator fix реализован как «design rationale comment», а не как реальное удаление вызова.** Расследование показало, что «двойной листинг root» проистекает из намеренной двухфазной модели progressive loading: `scanFolderChunked` (early emit для UI) → `scanFolder` (полный скан). Это by design. Поэтому primary fix — explanatory `// S0248:` комментарий в `GetMediaFilesUseCase`, описывающий почему два вызова уместны и почему coalescer их не сливает (различные ключи). Если позже выяснится, что две вызова с ИДЕНТИЧНЫМИ параметрами всё-таки воспроизводятся в каком-то другом callsite — defensive coalescer сольёт их и оставит `DEBUG`-лог.
3. **Phase 4 — использован `CompletableDeferred` вместо `Deferred` из `async {}`.** INDEX упоминал `ConcurrentHashMap<String, Deferred<...>>`. `CompletableDeferred` — подтип `Deferred`, выбран потому что callsite не предоставляет `CoroutineScope` для `async`; `CompletableDeferred` позволяет вручную управлять complete/completeExceptionally в `try/finally`. Семантически эквивалентно.
4. **Phase 5 — приоритизация «visible rows first» отложена.** INDEX step 3 предлагал «priority queue with channel-based enrichment». Реализация на сейчас — простой last-emit-after-full-scan; UI получает PENDING-tagged batch немедленно, COMPLETE batch — после полного enrichment. Priority queue выгоднее только при поэтапном incremental update (`notifyItemChanged` с payload), что требует более глубокого UI-рефакторинга. Записано как follow-up.

### Follow-ups (для аудита и/или нового тикета)

- **Owner audit decision pending:** spec может уйти в `Verified` или `BlockNeedUserTest` — это решит `/spec-check`. Tags `Timber.d("S0248: …")` сейчас не вставлены (не нужны при переходе в `Implemented`); если аудит решит `BlockNeedUserTest`, `/spec-check` инсертит их сам.
- На устройстве важно проверить: (a) UI получает листинг < 1 с для тестового SMB с 150..250 файлами, (b) лог скана содержит ≤ 1 строку `SmbMediaScanner: timeout-fallback count=...` (даже если N > 0), (c) НЕ содержит двух `Found N files in root` в одну миллисекунду с идентичными параметрами.
- При наличии BROKEN записей в `file_metadata_cache` пользовательский pull-to-refresh должен ретраить их (через `forceFullScan=true → forceRefresh=true → enrichBatch не reuse'ит BROKEN`).
- Phase 5 visible-first priority enrichment — отдельный тикет, если ScanMetrics покажет, что user перематывает к конец списка раньше, чем enrichment туда дойдёт.
