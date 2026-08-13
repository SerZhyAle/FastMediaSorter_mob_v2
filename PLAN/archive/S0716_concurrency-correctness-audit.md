# Стратегическая спецификация: S0716 - Аудит корректности конкурентности

**Ticket:** S0716
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-26
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - дочерний тикет S0714 (принятие Code Audit Protocol)
**Umbrella:** S0714

> **Scope:** STRATEGIC. Цели и объём аудит-прохода. Конкретные классы/пути - на этапе `/spec-tech` и в fix-тикетах находок.

---

## 0. Источник

Прохождение Layer 2 (Lifecycle, coroutine, and concurrency) протокола `docs/CODE_AUDIT_PROTOCOL.md` в части **корректности конкурентности** - сверх существующих гейтов `assert-globalscope.ps1` и `assert-unsafe-collect.ps1`, которые покрывают только утечки.

## 1. Проблема

Существующие гейты ловят `GlobalScope` и lifecycle-небезопасный сбор Flow, но не ловят **гонки данных** и нарушения **main-safety**. Общее изменяемое состояние без синхронизации, `withContext(IO)`, закопанный глубоко в бизнес-логику или вынесенный на вызывающего, и горячие Flow без `WhileSubscribed`/`distinctUntilChanged` - это и корректностные баги (P1), и лишняя работа (P2). Системно эта ось не проверялась.

## 2. Цели

1. Найти **гонки**: общее изменяемое состояние, не сведённое к одному потоку и не защищённое `Mutex`/`synchronized`/`@Volatile`; read-modify-write без атомарности.
2. Проверить **main-safety**: `withContext(Dispatchers.IO)` стоит на границе Repository/DataSource, а не глубже и не на вызывающем; suspend-функции main-safe.
3. Проверить **гигиену Flow**: горячие/общие потоки через `stateIn`/`shareIn` с `SharingStarted.WhileSubscribed(5_000)`; эмиссии подрезаны `distinctUntilChanged`/`conflate`/`buffer`, где коллектор не успевает.
4. Найти **блокирующие вызовы** (`runBlocking`, блокирующий I/O, `Thread.sleep`) на UI/однопоточном диспетчере.

**Non-goals:** утечки lifecycle/корутин (покрыто S0715 и существующими гейтами); исправления - fix-тикетами.

## 3. Объём и ограничения

- Модули `app_v2/` и `wear/`.
- Аудит-проход не меняет поведение; каждое исправление гонки/main-safety - отдельный fix-тикет.
- Опираться на `audit-shared-state-writers.ps1` для инвентаризации писателей общего состояния.

## 4. Критерии приёмки

- [x] Инвентаризовано общее изменяемое состояние; владелец-поток/механизм защиты определён; незащищённое запарковано (S0728 `@Volatile`, S0729 кэш-гонки).
- [x] Границы `withContext` проверены на main-safety; нарушения - находки (кластер блокировок Main → S0727).
- [x] Горячие/общие Flow проверены на `WhileSubscribed`/дедупликацию (лишние пересборки → S0730; `asSharedFlow` на шинах - чисто).
- [x] Блокирующие вызовы на UI/однопоточном диспетчере - находки (3×P1+2×P2 → S0727).
- [x] Находки классифицированы P0-P3 (13: 0/3/5/5); запаркованы в S0727/S0728/S0729/S0730; отчёт в `## Last Audit` + `AUDIT_FINDINGS.md`.

## 5. Связанные тикеты

- S0714 (зонтик).
- S0715 (владение памятью - смежные владельцы корутин).
- S0717 (Room - main-safety запросов к БД).
- S0727, S0728, S0729, S0730 (fix-тикеты по находкам этого аудита).

## Last Audit

**2026-06-26 - статический проход Layer 2 (корректность конкурентности), workflow, 5 измерений, adversarial-верификация.**

Отчёт: `PLAN/S0716_concurrency-correctness-audit/AUDIT_FINDINGS.md`.

Итог: 18 кандидатов проверено, 5 опровергнуто, **13 подтверждено - 0 P0, 3 P1, 5 P2, 5 P3** (включая один дубль MainActivity:733 по двум измерениям). Код в целом дисциплинирован: пулы SFTP/FTP кладут `withContext(IO)` на границе datasource; диалоговые scope отменяются в `onDetachedFromWindow`; `catch(CancellationException)` ре-бросают; нет ни одного `callbackFlow`; process-шины используют `asSharedFlow`. GlobalScope и unsafe-collect исключены (под гейтами).

Подтверждённые сведены в 4 fix-тикета:

- **S0727** (3×P1 + 2×P2) - блокировка Main на disk/network IO: ScreenGestureOverlayController, SftpConnectionGate.closeFor (ON_STOP), ScheduledTasksWidgetProvider.onUpdate, MainActivity:733, ImportSettingsUseCase.
- **S0728** (2×P3) - `@Volatile` для кросс-потоковых полей `ConnectionThrottleManager` - **исправлено в этом проходе** (тривиально-безопасно).
- **S0729** (2×P2) - гонки на разделяемых кэшах MediaFilesCacheManager (CME с widget-refresher на IO) и TranslationCacheManager (Main-запись vs IO-чтение/очистка).
- **S0730** (1×P2 + 2×P3) - лишняя работа: BrowseObserverManager (4× пересборка AppSettings), ResourceEditorUseCase (неотменяемый scope), CloudOperationStrategy (корутина на 64KB-тик).

Статус прохода: все критерии аудита (инвентаризация, withContext, Flow, блокировки, классификация) закрыты. Находки запаркованы.

**F5-аудит (2026-06-26, /spec-next):** все 5 критериев §4 met (PASS); fix-тикеты существуют (S0727 Verified, S0728 Implemented, S0729 Draft, S0730 Draft); deliverable аудита полон. Verdict: **Verified**. Counts: PASS 5 · WARN 0 · FAIL 0.
