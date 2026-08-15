# Стратегическая спецификация: S1518 - Видимость и покрытие ticket-lease

**Ticket:** S1518
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-08
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-08-08
**Tactical spec:** `PLAN/S1518_ticket-lease-coverage-visibility/` (будет создан через `/spec-tech`)

**Tactical plan:** `PLAN/S1518_ticket-lease-coverage-visibility/INDEX.md`

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Текст:**

Ticket ownership is invisible while a session is driving a ticket, so a sibling can take a Draft ticket that is already being worked.

Owner's report (verbatim): "нужен ещё какой то статус, когда задача уже взята разработчиком, но еще не готова. А то другой разработчик может взять draft"

Owner's decision after discussion (2026-08-08): do NOT add a lifecycle status. Close the gap in the existing ticket-lease store instead - status stays the resume point, busy-ness stays ephemeral and self-expiring.

Evidence gathered while diagnosing why S1506 sat at Draft for 15 minutes under run-spec-all-queue.ps1:
- The lease store already exists and works: scripts/spec_catalog/ticket-lease.ps1 (S1437, liveness widened by S1448), documented at docs/DEV_OPS.md:246. Observed live during the incident: "S1506 queue-driver-17536 (held 20.5 min) run-spec-all-queue.ps1" and "S1433 10d2dd82-.. (last seen 2.1 min ago, held 38 min) /spec-do".
- Consumers that respect it: spec-next-preflight.ps1 drops leased ids (lines 138-159), run-spec-all-queue.ps1 passes over a leased ticket (line 277).
- Gap 1 - coverage: only /spec-next and /spec-do claim a lease, plus run-spec-all-queue.ps1 on behalf of the child session it starts. A hand-launched /spec-all Sxxxx or /spec-dev Sxxxx claims nothing. Its only cover is the fallback busy signal Get-TicketBusySignal (run-spec-all-queue.ps1:120-150), which reads the ticket id out of a held or queued CODE.LOCK/BUILD.LOCK reason - and during Stage 0 research no lock is held at all, so the ticket looks free for the whole research window (15 minutes in the observed S1506 run).
- Gap 2 - human visibility: PLAN/RELEASE_QUEUE.md shows only the catalog status, so the owner reading it sees "Draft" with nothing indicating a live session is on it. The machine knows; the human does not.
- Gap 3 - heartbeat display: the queue driver's own lease printed "last seen unknown" while a /spec-do lease printed "last seen 2.1 min ago". Worth checking whether the driver's Claim path refreshes lastSeenAt.

**Second observation, 2026-08-09 (during S1453), same store, opposite direction - a lease lost by a session that never stopped:**

- A `/spec-do` session claimed S1453 at 10:47 and worked it continuously to phase 05. At 11:41 the lease was held by a DIFFERENT session (`4b3183cb`, held 4.1 min), and a re-Claim returned exit 3. The first session had not stopped, crashed or gone idle - it was alive the whole time.
- What it was doing instead of touching the lease: waiting for `CODE.LOCK`. Every step of the ticket edited a repository script, so it queued for the lock five separate times, several minutes each, per Rule 23's contract of "wait in the background and do lock-free work". Nothing on that path refreshes a ticket lease.
- Cost of the collision, observed: both sessions wrote `PLAN/S1453_gate-shared-test-flavor-scope/PHASE_05__docs-catalog-cleanup.md`. The second session's Step Log records a closure the first session did not run (`post-change: PASS (Mixed, 56796 ms)`, nine files, `-RegistryAck "architecture,developer-operations,script-cheatsheet"`) while the first session's own closure of the same phase reported a different file set and a different pair of registry ids. Two step logs for one step, each true about a different run.
- Why this belongs to the lease store rather than to Rule 23: the queue behaviour is correct - a blocked session SHOULD yield the lock. What is wrong is that yielding a lock silently yields the ticket. Liveness is measured from the session transcript, and a session parked on a lock keeps producing transcript activity, so whichever signal expired here needs identifying before the fix.

Sketch of the wanted change (not a plan, just direction):
- /spec-all and /spec-dev claim the ticket lease at Stage 0 / start and release it at exit, mirroring what spec-next.md already does at its Stages 3.5 and 5.
- Surface ownership to the human - either a column/marker rendered into RELEASE_QUEUE.md by release-queue.ps1 -Reconcile from the lease store, or a cheap read command the owner runs. Prefer whichever avoids churning a git-tracked file on every claim/release.

Why a status was rejected (record so it is not re-proposed):
- A status lives in a git-tracked journal and cannot expire; a crashed session would leave InWork forever with no owner identity and no heartbeat to judge it by.
- Status is the resume point: /spec-all routes stages off it (Draft -> F1, Approved -> F2, Tactical -> F3, spec-all.md:77). Overwriting it with a busy marker destroys where to continue, and the whole driver is built on relaunching the same ticket.
- Every claim/release would write spec-catalog.jsonl and reconcile both RELEASE files - noise across parallel sessions.
- "In Progress" already exists and means something else: work under way per the plan, not "a session holds this ticket".

---

## 1. Проблема

Эфемерное владение тикетом надёжно хранится отдельно от жизненного статуса, но не все
точки входа в работу получают lease. Поэтому сессия, начатая напрямую через `/spec-all`
или `/spec-dev`, видна другому исполнителю только после захвата CODE.LOCK или BUILD.LOCK.
Во время исследования, планирования и ожидания очереди этот сигнал отсутствует или может
устареть, а владелец не видит занятый тикет в обычном представлении release queue.

---

## 2. Цели

1. Каждый верхнеуровневый запуск `/spec-all` и отдельный запуск `/spec-dev` атомарно
   резервирует тикет до начала работы и освобождает резервирование при выходе.
2. Вложенный `/spec-dev`, запущенный из `/spec-all`, не может преждевременно освободить
   lease владельца верхнего уровня.
3. Оператор может получить release-queue вместе с живыми lease без изменения самого
   файла очереди и без шума в git-tracked планировании.
4. Сессия, ожидающая CODE.LOCK или BUILD.LOCK по штатному контракту, сохраняет lease,
   пока действует существующая heartbeat/liveness-модель.

**Non-goals:**

- Новый lifecycle status и запись busy-состояния в spec catalog.
- Изменение порядка, пакета или строк PLAN/RELEASE_QUEUE.md.
- Изменение Android-кода, flavor-конфигурации или сборочного поведения.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Человекочитаемый список показывает ticket id, owner session, возраст и причину lease.

### 3.2 Жёсткие ограничения

- **Flavor:** без влияния на product flavors.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** только один короткий read lease-store для явного операторского вывода.
- **Совместимость данных:** не затрагивается.
- **Локализация:** UI-строки не добавляются.
- **Доступность:** UI не меняется.

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S1437, S1448, S1453, S1518.

---

## 4. Контекст текущей архитектуры

Инструменты спецификаций используют файловый lease-store с атомарным claim и сессией как
владельцем. Его liveness сначала читает heartbeat, затем transcript, затем время claim.
Очередной драйвер уже обновляет свой lease во время долгого дочернего запуска, но
инструкции самостоятельных entry point-ов не устанавливают тот же контракт.

Release queue намеренно хранит только owner-managed порядок и catalog status. Записывать
в неё частые claim/release запрещено: это породило бы шум и могло бы изменить план.
Поэтому visibility должна быть opt-in read projection из существующего lease-store.

---

## 5. Предлагаемый подход

Ввести единый lifecycle lease для верхнеуровневых исполнителей: claim после разрешения
тикета и до работы, idempotent refresh на длительных границах, release в гарантированном
выходе. Вложенный исполнитель получает явный признак владельца верхнего уровня и только
обновляет его lease.

Операторская команда получает необязательный режим, который объединяет уже отформатированные
строки очереди с read-only снимком активных lease. Базовый вывод и файл очереди не меняются.

### 5.1 Основные столпы / модули

### 5.1 Lease ownership contract

Документировать, кто владеет lease для прямого и вложенного запуска, включая claim-lost и
гарантированное освобождение.

### 5.2 Queue visibility projection

Добавить явный операторский режим, который показывает активное владение рядом с release queue
без записи в очередь.

### 5.2 Потоки данных и событий

Entry point → atomic lease claim → specification work → periodic/idempotent heartbeat →
guaranteed release. Operator request → queue read + lease-store read → rendered projection.

### 5.3 Точки расширяемости

Формат lease-store остаётся единственным источником ephemeral ownership. Будущие entry point-ы
подключаются к тому же контракту, не создавая второй registry или status.

---

## 6. Открытые вопросы / Research items

1. **Граница владения nested executor**
   - **Вопрос:** Как не дать вложенному `/spec-dev` снять lease `/spec-all`?
   - **Нужно выяснить:** Проверить idempotent claim и существующий driver lifecycle.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S1518_ticket-lease-coverage-visibility/research/01__lease-lifecycle.md`

2. **Видимость без churn очереди**
   - **Вопрос:** Как показать busy-состояние человеку, не переписывая release queue?
   - **Нужно выяснить:** Проверить существующий read-only queue CLI и lease status payload.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S1518_ticket-lease-coverage-visibility/research/01__lease-lifecycle.md`

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Вложенный executor освобождает lease родителя | Средняя | Сестринская сессия повторно берёт тикет | Явный флаг owner и release только верхним уровнем |
| Visibility меняет owner-managed очередь | Низкая | Планирование шумит или меняется порядок | Только opt-in read projection |
| Долгая очередь за lock выглядит stale | Средняя | Lease перехватывается во время работы | Использовать существующий heartbeat precedence и проверку ожидания |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Lease остаётся эфемерным, а не catalog status**

- **Решение:** Расширить покрытие и read projection существующего lease-store.
- **Альтернативы:** Новый status `InWork`; запись owner в release queue.
- **Почему:** Status является resume point и не истекает, а очередь принадлежит владельцу и
  не должна меняться на каждом claim/release.

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

Связи: S1437 ввёл lease-store; S1448 ввёл heartbeat precedence; S1453 зафиксировал потерю
lease во время ожидания CODE.LOCK. Блокирующих связей нет.

---

## 11. Критерии готовности (strategic-level)

1. Прямой `/spec-all` и standalone `/spec-dev` не начинают работу, если lease уже принадлежит
   живой другой сессии.
2. Вложенный `/spec-dev` не освобождает lease, пока `/spec-all` продолжает работу.
3. Операторский вызов queue может отобразить живые lease с идентификатором тикета и владельцем,
   а обычный вызов и файл очереди сохраняют прежний формат.
4. Скриптовая проверка покрывает свободный, занятый и отсутствующий lease в projection.

## Last Audit

**Date:** 2026-08-14
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- EXEMPT - internal PowerShell tooling only; no Android device flow or user-visible feature is changed.
