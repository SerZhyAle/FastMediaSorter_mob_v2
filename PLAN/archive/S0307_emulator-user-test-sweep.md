---
ticket: S0307
status: BlockNeedUserTest
priority: 50
date: 2026-05-30
tier: 4
---

# Стратегическая спецификация: S0307 - Агентная проверка non-VR тикетов на эмуляторе

**Ticket:** S0307
**Status:** BlockNeedUserTest
**Priority:** 50
**Date:** 2026-05-30
**Tier:** 4 - Strategic
**Roadmap entry:** Ad-hoc - запрос 2026-05-30
**Tactical plan:** `PLAN/S0307_emulator-user-test-sweep/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, детальных команд, файлов реализации и low-level wiring.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec для последующего агентного execution workflow.
- **Goal / expected outcome:** Provided by user - найти все текущие non-VR / non-3D тикеты, зависшие в `BlockNeedUserTest`, и проверить их на подключённом эмуляторе с файлами и тестовыми данными, которые можно скопировать или подготовить локально.
- **Local anchor:** Provided by user - текущий spec catalog, статус `BlockNeedUserTest`, подключённый Android emulator, локально копируемые fixture-файлы.
- **Scope boundaries / forbidden areas:** Provided by user - исключить VR, 3D, headset-only, OpenXR, Quest-only и immersive задачи; не требовать физический VR-девайс; не чинить найденные дефекты внутри этой спецификации; не использовать реальные внешние аккаунты или секреты, если они не подготовлены владельцем отдельно.
- **Done / success signal:** Provided by user - для каждого eligible тикета есть evidence bundle с логами, скриншотами и expected/actual выводом; тикет переведён в `Verified`, `Broken`, `Partial`, `BlockExternal` или `BlockQuestions` по доказательствам, либо явно оставлен в `BlockNeedUserTest` только с обоснованным blocker.
- **Autonomy rule:** Provided by user - agent may decide with explicit assumptions: исполнитель может устанавливать сборку, копировать fixture-файлы, управлять эмулятором, собирать логи и скриншоты, выбирать проверочный маршрут и обновлять статусы через spec catalog scripts.
- **UI decisions / delegation:** N/A - новая пользовательская UI-поверхность не создаётся; проверки используют существующие экраны и уже утверждённое поведение проверяемых тикетов.

`Approved` is blocked while any mandatory line in this section contains `MISSING - requires owner input`.

---

## 1. Проблема

В backlog накопились тикеты в `BlockNeedUserTest`, где реализация уже завершена, но переход в `Verified` зависит от ручной пользовательской проверки. Для задач, не связанных с VR/3D, часть этой проверки можно выполнить агентно на Android emulator: установить нужный flavor, скопировать локальные fixture-файлы, пройти сценарий, снять логи и скриншоты.

Без единого sweep-процесса эти тикеты остаются в подвешенном состоянии, а ошибки обнаруживаются поздно и неравномерно. Нужна спецификация, которая разрешает исполнителю системно сканировать каталог, тестировать eligible задачи и менять статус по evidence, а не по предположению.

## 2. Цели

1. Каждый запуск sweep начинается с актуального scan всех активных тикетов в `BlockNeedUserTest`.
2. VR/3D/headset-only задачи исключаются из sweep и фиксируются отдельным списком excluded.
3. Каждый non-VR тикет классифицируется как direct emulator, emulator with local service, emulator with external dependency, или not testable by copied fixtures.
4. Для direct emulator задач исполнитель подготавливает локальные fixture-файлы, копирует их на эмулятор, запускает сценарии и собирает доказательства.
5. Для задач с локальными network/service зависимостями исполнитель поднимает безопасные тестовые сервисы или фиксирует blocker, если это невозможно без внешних секретов.
6. Для cloud/auth/third-party задач исполнитель не подставляет реальные секреты и не имитирует успешный OAuth без доказательств; такие тикеты получают explicit blocker или partial verdict.
7. По каждому тикету формируется evidence bundle: build/install context, fixture manifest, screenshots, logs, expected/actual checks, verdict и next action.
8. По итогам проверки eligible тикеты переводятся из `BlockNeedUserTest` в корректный статус: `Verified`, `Broken`, `Partial`, `BlockExternal` или `BlockQuestions`.
9. Итоговый sweep report показывает, какие тикеты закрыты, какие переоткрыты, какие требуют внешнего действия и какие исключены как VR/3D.

**Non-goals:**

- Проверка VR, 3D, OpenXR, Quest-only, headset-only и immersive задач.
- Исправление найденных дефектов в рамках этого sweep.
- Release-signing, публикация, store validation или проверка production OAuth credentials.
- Полная матрица устройств, API levels и физических Android TV/head-unit устройств.
- Генерация новых пользовательских функций или изменение UX.
- Использование copyrighted media fixtures.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Снять зависимость от ручного user-test для задач, которые можно проверить на эмуляторе.
2. Использовать файлы, которые можно скопировать на эмулятор или сгенерировать локально.
3. Выполнять проверки от имени агента-исполнителя, включая установку приложения, подготовку storage, управление UI, сбор логов и скриншотов.
4. Не смешивать non-VR sweep с VR/3D verification pipeline.
5. При ошибке не закрывать тикет, а переоткрывать его evidence-based verdict.

### 3.2 Жёсткие ограничения

- **Flavor:** target flavor выбирается per ticket. Baseline - non-VR debug flavor; noLegal используется только для noLegal тикетов, если сценарий воспроизводим на emulator ABI.
- **API level:** emulator должен соответствовать минимальному уровню проверяемого flavor; exact API фиксируется в evidence bundle.
- **Wear OS:** не входит в baseline sweep, кроме тикетов, где Wear явно указан как проверяемая область.
- **Производительность:** sweep не должен оставлять долгоживущие сервисы, большие fixture-файлы или активные background jobs после завершения проверки.
- **Совместимость данных:** reset app data допустим только если тикет не требует проверки migration/resume state; otherwise state policy фиксируется per ticket.
- **Локализация:** новая локализация не добавляется. Если тикет проверяет user-visible text, screenshots должны подтверждать EN/RU/UK только в рамках исходного acceptance.
- **Доступность:** если тикет касается keyboard, D-pad, TV remote или focus behavior, проверка должна включать соответствующий input path на эмуляторе.
- **Secrets:** реальные tokens, passwords, account credentials и приватные тестовые ключи не запрашиваются у модели и не записываются в evidence.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** текущие active `BlockNeedUserTest` тикеты из spec catalog; seed non-VR candidates перечислены в §4 и §10; current VR/3D exclusions: `S0249`, `S0291`.
- **Proceed signal:** owner requested `spec-all` continuation on 2026-05-30 after strategic draft creation.
- **Delegated execution latitude:** исполнитель может выбирать target flavor, копировать local fixtures на emulator storage, управлять UI, собирать screenshots/logcat, классифицировать тикеты и обновлять их status через catalog scripts по evidence.
- **Blocking conditions:** emulator offline, missing acceptance criteria, external accounts/secrets, unavailable third-party app, hardware-only behavior, or any ticket whose observable criteria cannot be reproduced on emulator with local/copied fixtures.
- **Forbidden scope:** no VR/3D/headset-only verification, no secret collection, no speculative success for OAuth/cloud flows, no defect fixes inside S0307 sweep.

## 4. Контекст текущего состояния

На момент создания спецификации catalog показывает 28 активных тикетов в `BlockNeedUserTest`. Из них первично исключены как VR/3D: `S0249`, `S0291`. Первичный non-VR seed list для sweep: `S0054`, `S0165`, `S0186`, `S0206`, `S0207`, `S0211`, `S0213`, `S0229`, `S0233`, `S0235`, `S0236`, `S0239`, `S0243`, `S0252`, `S0253`, `S0254`, `S0257`, `S0260`, `S0265`, `S0266`, `S0274`, `S0281`, `S0284`, `S0288`, `S0289`, `S0303`.

Этот список является seed snapshot, а не source of truth. Во время исполнения sweep обязан заново прочитать catalog, потому что статусы могут измениться между созданием S0307 и запуском тактики.

## 5. Предлагаемый подход

Sweep строится как evidence-first verification pipeline поверх существующего spec lifecycle. Исполнитель не принимает статус по названию тикета: он читает актуальный catalog, классифицирует scope, подготавливает emulator route, запускает сценарий, собирает evidence и только после этого меняет статус проверяемого тикета.

### 5.1 Ticket Discovery And Classification

Discovery phase каждый раз строит свежий список `BlockNeedUserTest`. Фильтр исключения срабатывает на VR/3D/immersive/headset-only признаки в названии, тексте спеки, roadmap link или acceptance criteria. Неочевидные случаи попадают в review bucket, а не в pass/fail.

Каждый non-VR тикет получает один из классов:

- **Direct emulator:** проверяется локальными файлами, системными intents, app settings и UI действиями.
- **Emulator with local service:** требует FTP, SFTP, SMB, HTTP, локального webhook или другого сервиса, который можно безопасно поднять локально.
- **External dependency:** требует реального OAuth, cloud provider, installed third-party app, account state, payment/license surface или hardware outside emulator.
- **Not testable by copied fixtures:** acceptance зависит от условий, которые нельзя честно воспроизвести на emulator без расширения scope.

### 5.2 Fixture Preparation

Fixture set должен быть минимальным, reproducible и свободным от copyright risk. Он включает синтетические изображения, короткие видео, аудио, текстовые документы, архивы и специальные containers только если конкретный тикет требует такой формат. Каждый файл получает имя, назначение, размер и expected behavior в fixture manifest.

Скопированные файлы размещаются в emulator storage так, чтобы app видел их тем же способом, что и пользовательский локальный ресурс. После sweep исполнитель удаляет временные данные или фиксирует, что emulator state сохранён намеренно для повторной проверки.

### 5.3 Execution Flow

Для каждого тикета исполнитель выбирает target flavor, устанавливает свежую debug build, готовит app state, копирует fixtures, выполняет сценарий и собирает evidence. UI-driven проверка предпочтительна, когда acceptance является пользовательским поведением. Log-only проверка допустима только для internal resilience тикетов, где UI не несёт наблюдаемого результата.

Скриншоты должны покрывать ключевые состояния: до действия, результат действия, ошибку или recovery state. Логи должны покрывать app-only signal, crash/ANR absence и специфичные flow markers без раскрытия секретов.

### 5.4 Verdict And Status Transition

`Verified` допускается только когда все observable criteria тикета подтверждены evidence. `Broken` используется для воспроизводимой ошибки, которая нарушает основную acceptance. `Partial` используется, когда часть acceptance подтверждена, но остаётся ограниченный gap. `BlockExternal` используется для честной внешней зависимости. `BlockQuestions` используется, когда acceptance невозможно интерпретировать без owner decision.

Статус обновляется только через spec catalog tooling. Если проверяемая спека поддерживает inline audit block, sweep добавляет или обновляет audit summary с ссылкой на evidence bundle и expected/actual результатами.

### 5.5 Reporting

Итоговый report группирует тикеты по verdict. Для каждого тикета указывается initial status, classification, tested flavor, emulator identity, fixture set, evidence location, final status и next action. Report не заменяет individual spec audit, но служит batch-level навигацией.

## 6. Открытые вопросы / Research items

1. **Connected emulator identity**
   - **Вопрос:** какой emulator сейчас подключён, какой API level, ABI, storage state и input capabilities доступны?
   - **Варианты:** использовать текущий emulator; пересоздать clean emulator; сохранить state между тикетами.
   - **Нужно выяснить:** device fingerprint, API level, installed package state, available storage, screen size, D-pad/keyboard support.
   - **Статус:** Resolved for tactical planning - Phase 01 records emulator identity and blocks Phase 04 if the device is offline.

2. **Per-ticket acceptance source**
   - **Вопрос:** у каждого seed тикета есть достаточно конкретный `Last Audit` или acceptance criteria для agent verification?
   - **Варианты:** использовать существующий audit; прочитать tactical notes; запросить owner input для неясных тикетов.
   - **Нужно выяснить:** какие тикеты имеют machine-verifiable критерии, а какие требуют человеческого решения.
   - **Статус:** Resolved for tactical planning - Phase 02 extracts each ticket's current acceptance source before classification.

3. **External dependency boundary**
   - **Вопрос:** какие cloud/auth/third-party тикеты можно честно проверить без реальных секретов?
   - **Варианты:** local fake service, installed test app, credentials already present, `BlockExternal`.
   - **Нужно выяснить:** наличие безопасных test accounts, local server routes and already installed third-party packages.
   - **Статус:** Resolved for tactical planning - Phase 02 isolates external dependencies and forbids secret capture.

4. **Fixture corpus**
   - **Вопрос:** какие форматы нужны для текущего seed list?
   - **Варианты:** generated local fixtures only; repo-provided test assets; user-provided media pack.
   - **Нужно выяснить:** минимальный набор файлов для M2TS/video OOM/audio metadata/OCR/Telegram/download/file operation scenarios.
   - **Статус:** Resolved for tactical planning - Phase 03 creates the fixture manifest and defers unavailable formats explicitly.

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Тикет ошибочно классифицирован как non-VR | Средняя | Sweep тронет задачу, которую нужно проверять на headset | Двойной фильтр по имени, тексту спеки и acceptance; uncertain bucket вместо автоматического запуска |
| Emulator не воспроизводит device-specific дефект | Средняя | `Verified` будет ложноположительным | Evidence фиксирует emulator identity; hardware-specific критерии остаются `BlockExternal` или `Partial` |
| Cloud/auth тикет требует секреты | Высокая | Проверка остановится или станет небезопасной | Не запрашивать секреты у модели; использовать только уже настроенные тестовые окружения; otherwise `BlockExternal` |
| Fixture files слишком большие | Средняя | Sweep станет медленным, emulator storage загрязнится | Минимальные synthetic fixtures; cleanup policy; size в manifest |
| Статус изменён без достаточного evidence | Низкая | Каталог потеряет доверие | Status transition только после expected/actual, screenshots/logs и per-ticket report |
| Batch слишком широк для одного запуска | Средняя | Проверка оборвётся без понятного состояния | Обязательный progress report, per-ticket idempotency и возможность resume |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES`. S0307 не добавляет пользовательскую функцию; это внутренний verification workflow для закрытия уже реализованных задач.

## 9. Архитектурные решения (ADR)

**ADR-1: Batch sweep вместо ручной проверки каждого тикета**

- **Решение:** один umbrella workflow отвечает за discovery, classification, execution, evidence и status transitions для текущего non-VR user-test backlog.
- **Альтернативы:** проверять каждый `BlockNeedUserTest` тикет вручную через отдельный запрос.
- **Почему:** текущий backlog содержит много зависших тикетов; единый процесс уменьшает потерю контекста и делает evidence формат одинаковым.

**ADR-2: Dynamic catalog scan вместо статического списка**

- **Решение:** seed list из §4 используется только как snapshot; реальное исполнение всегда сканирует текущий catalog.
- **Альтернативы:** зафиксировать список тикетов в S0307 и тестировать только его.
- **Почему:** статусы меняются быстро, а batch verification должен быть idempotent и безопасен к повторному запуску.

**ADR-3: Evidence-first status transition**

- **Решение:** статус каждого тикета меняется только после логов, скриншотов и expected/actual результата.
- **Альтернативы:** переводить тикеты в `Verified` после успешного smoke без полного evidence.
- **Почему:** `BlockNeedUserTest` означает ожидание ручного доверия; агент должен заменить его проверяемыми артефактами, а не утверждением.

**ADR-4: Emulator-only baseline with external blockers**

- **Решение:** baseline ограничен эмулятором и копируемыми/local fixtures; внешние зависимости фиксируются как blockers.
- **Альтернативы:** пытаться покрыть cloud providers, third-party apps and hardware in the same run.
- **Почему:** безопасная и воспроизводимая проверка важнее ложного покрытия, особенно для секретов и внешних сервисов.

## 10. Связи с другими спеками

- **Seed candidates:** `S0054`, `S0165`, `S0186`, `S0206`, `S0207`, `S0211`, `S0213`, `S0229`, `S0233`, `S0235`, `S0236`, `S0239`, `S0243`, `S0252`, `S0253`, `S0254`, `S0257`, `S0260`, `S0265`, `S0266`, `S0274`, `S0281`, `S0284`, `S0288`, `S0289`, `S0303`.
- **Excluded current VR/3D tickets:** `S0249`, `S0291`.
- **Related verification skills:** `/spec-check`, `/spec-fix`, `/spec-test-device`, `/log-reader`, `/build`.

## 11. Критерии готовности (strategic-level)

1. Sweep report содержит актуальное число `BlockNeedUserTest` тикетов на момент запуска, список excluded VR/3D и список eligible non-VR.
2. Каждый eligible тикет имеет classification и явно выбранный route: direct emulator, local service, external dependency или not testable by copied fixtures.
3. Для каждого direct emulator тикета есть evidence bundle с fixture manifest, screenshots, logs, emulator identity, tested flavor и expected/actual результатами.
4. Для каждого local service тикета есть evidence bundle или explicit blocker с причиной, почему local service route невозможен.
5. Для каждого external dependency тикета есть безопасный verdict без раскрытия секретов и без фиктивного successful auth.
6. Ни один eligible тикет не остаётся в `BlockNeedUserTest` без нового audit note и next action.
7. Тикеты, прошедшие acceptance, переведены в `Verified`; тикеты с воспроизводимыми ошибками переведены в `Broken` или `Partial` с кратким defect summary.
8. Все артефакты sweep лежат в scratch/evidence области, а project root не загрязнён.
9. VR/3D/headset-only тикеты не изменены этим sweep.
10. Итоговый report позволяет владельцу увидеть, какие задачи закрыты, какие переоткрыты и какие требуют внешнего действия.

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0307_emulator-user-test-sweep/INDEX.md`.

## Last Audit

**Date:** 2026-05-30
**Mode:** full
**Flags:** spec-all
**Outcome:** PartialExecution
**Counts:** PASS 18 · WARN 4 · FAIL 0 · MANUAL 8 · EXEMPT 3

### Action items

1. **[WARN S0253]** Fresh standardDebug evidence supports the fresh-install branch, but upgrade-path preservation remains untested; no target mutation was applied.
2. **[PASS S0254]** Runtime UI and source evidence show moved grid/file-overflow controls still present under Playback and absent from General -> Interface; status changed `BlockNeedUserTest` -> `Broken`.
3. **[PASS S0165]** Resource-specific `showSubfoldersAsItems=true` was enabled, Browse showed `btnCreateFolder`, the dialog opened, and `BetaFolder` was created under `/sdcard/Download`; status changed `BlockNeedUserTest` -> `Verified`.
4. **[WARN S0284]** Search finds `3D`, `VR`, `SBS`, and `OU`, but query `single eye` produced no visible result; candidate status is `Partial_candidate`.
5. **[WARN S0289]** D-pad Settings smoke passed and 5 `S0289:` probes appeared in logcat, but noLegal finger-tap, mouse and gamepad requirements remain untested.
6. **[WARN S0281]** ACTION_SEND route was attempted, but share aliases are disabled after onboarding and `shell` cannot enable them; `run-as` did not produce S0281 runtime markers.
7. **[MANUAL remaining routes]** noLegal, local-service, external dependency, memory-pressure, notification tap, link-share, audio race and static-audit routes still require separate focused passes.

### Evidence

- `temp/s0307/05_sweep_report.md`
- `temp/s0307/05_status_transitions.tsv`
- `temp/s0307/05_target_audit_notes.md`
- `temp/s0307/04_execution_log.md`
- `temp/s0307/04_logcat_summary.txt`
- `temp/s0307/02_route_matrix.md`
- `temp/s0307/03_fixture_manifest.md`

### Status policy

- Target ticket status mutations: 2 (`S0254` -> `Broken`; `S0165` -> `Verified`).
- Excluded VR/3D ticket mutations: 0.
- S0307 remains `In Progress`; remaining routes require focused noLegal, local-service, external-provider or specialised fixture passes.