# Стратегическая спецификация: S0195 — Перенос регистрации сетевых lifecycle-observer-ов на первое использование

**Ticket:** S0195
**Status:** BlockNeedUserTest
**Priority:** 55
**Date:** 2026-05-14
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — дочерняя спека S0193
**Parent spec:** [S0193](S0193_lazy-init-research.md)
**Tactical spec:** [`S0195_network-first-use-trigger/INDEX.md`](S0195_network-first-use-trigger/INDEX.md)

> **Scope:** STRATEGIC. Снять 4 синхронных process-start hooks сетевого стека с process entrypoint и перенести их регистрацию в момент первого реального использования удалённого ресурса.

---

## 1. Проблема

В process entrypoint есть четыре синхронных process-start действия, которые регистрируют OS-колбэки и lifecycle-observer-ы ещё до первого реального remote-use:

- регистрация системного network callback;
- установка SMB reset callback для UI-уведомлений;
- attach SMB background lifecycle observer;
- attach protocol-neutral lifecycle observer и его diagnostics collector.

Эта регистрация выполняется даже если в текущей сессии ни одного сетевого ресурса открыто не будет. Это противоречит принципу «не активируй неиспользуемое».

---

## 2. Цели

1. Перенести регистрацию OS-колбэков и lifecycle-observer-ов из process-start в момент первого реального входа в удалённый сетевой flow любого поддерживаемого протокола.
2. Гарантировать, что при сессии без сетевых ресурсов process-level network lifecycle subsystem остаётся неинициализированной и ни один callback / observer не зарегистрирован.
3. Сохранить семантику S0061 Phase 04 и S0067 Phase 06 — наблюдатели обязаны корректно закрывать соединения при backgrounding, после того как они зарегистрированы.

**Non-goals:**

- 13 «лёгких» Application-полей — выделены в S0194.
- Изменение поведения самих protocol gates (S0067) — меняется только момент регистрации lifecycle infrastructure.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Первое открытие сетевого ресурса в сессии может иметь короткую задержку (microfreeze) — это приемлемо.
2. Повторные открытия — без задержки, объекты уже в памяти.

### 3.2 Жёсткие ограничения

- **Flavor:** все.
- **API level:** не затрагивается.
- **Производительность:** не должно появиться регрессии при backgrounding/foregrounding (lifecycle observers продолжают работать корректно после их регистрации).
- **Совместимость данных:** не затрагивается.

---

## 4. Контекст текущей архитектуры

S0061 (SMB background lifecycle) и S0067 (protocol-neutral connection gates) сейчас опираются на предположение, что process-level lifecycle infrastructure регистрируется с самого начала жизни процесса. При этом protocol-neutral cleanup path перечисляет уже известные gates при backgrounding и должен оставаться side-effect-free. S0195 ослабляет только момент attach/init, но не меняет саму семантику cleanup: после регистрации наблюдатели работают как раньше, а до первого удалённого доступа никакая cleanup-ветка не должна сама инициировать bootstrap сетевого стека.

---

## 5. Предлагаемый подход

Ввести два отдельных архитектурных понятия:

1. **Consumer-side network entry boundary** — единая граница, через которую проходит первый реальный удалённый flow: remote browse, remote open, remote scan, player/use-case или иная операция, которая вот-вот запросит сетевой доступ.
2. **One-shot network lifecycle bootstrap** — идемпотентная инициализация process-level network lifecycle infrastructure, выполняемая синхронно на этой границе перед продолжением первого удалённого flow.

Ожидаемая последовательность:

1. При process start сетевой lifecycle bootstrap не выполняется.
2. Первый consumer-side remote flow входит в выделенную boundary и синхронно вызывает one-shot bootstrap entry point.
3. Bootstrap-компонент регистрирует системный network callback, оба lifecycle observer-а и SMB reset callback.
4. После успешного bootstrap существующий путь получения gate / connection / pool продолжает работу без изменения бизнес-семантики.
5. Повторные входы в remote flow упираются в no-op ветку того же one-shot bootstrap.

Cleanup / enumeration APIs не являются first-use trigger и не должны инициировать bootstrap сами по себе.

### 5.1 Основные блоки

- **Consumer-side network entry boundary** — единственная допустимая точка старта bootstrap перед первым удалённым действием.
- **One-shot bootstrapper** — идемпотентная инициализация process-level network lifecycle infrastructure.
- **Passive gate registry / cleanup view** — side-effect-free слой, который перечисляет уже созданные gates и не запускает bootstrap.

### 5.2 Потоки данных

Бизнес-потоки не меняются: после первого bootstrap текущие gate / pool / manager flows работают как раньше. Меняется только момент attach/init process-level lifecycle infrastructure и требование, что cleanup path остаётся без побочных эффектов до первого удалённого доступа.

---

## 6. Открытые вопросы / Research items

1. **Какая именно consumer-side boundary покрывает все удалённые flows?**
   - **Вопрос:** Где находится первый общий access point для remote browse / open / scan / player / operation flows, который вызывается до первой сетевой операции и до первого возможного backgrounding с уже открытым remote context?
   - **Варианты:** Граница на уровне network access orchestration / gate lookup / higher-level remote coordinator.
   - **Нужно выяснить:** Tactical spec должна доказать, что выбранная boundary покрывает все реальные remote-входы, а не только один browse path.

2. **Как убрать eager materialization protocol-gate слоя с cold start?**
   - **Вопрос:** Не останется ли process-start путь по-прежнему eager из-за DI-сборки registry / gates, даже если lifecycle bootstrap станет ленивым?
   - **Варианты:** Lazy providers, deferred registry population, другой deferred assembly pattern.
   - **Нужно выяснить:** Tactical spec должна зафиксировать механизм, при котором ленивость касается не только observer attach, но и process-start materialization protocol-gate graph.

3. **Как должен вести себя cleanup path до первого remote-use?**
   - **Вопрос:** Что происходит, если lifecycle cleanup path будет вызван до первого удалённого доступа?
   - **Статус:** Cleanup path должен быть no-op и не должен триггерить bootstrap сетевого стека.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Bootstrap привязан к cleanup / enumeration path вместо реального remote entry | Средняя | Lifecycle observer сам может инициировать сетевой bootstrap и сломать first-use semantics | Cleanup APIs обязаны оставаться side-effect-free; bootstrap разрешён только на consumer-side entry boundary |
| Eager materialization protocol-gate graph сохраняется на cold start | Высокая | Выигрыш окажется частичным: observers станут lazy, но сетевой граф всё равно материализуется слишком рано | Tactical spec обязана выбрать deferred assembly strategy для gates / registry |
| Циклическая зависимость между bootstrapper, observer и registry | Высокая | DI failure, recursion или runtime crash | Разделить ответственность: bootstrapper и passive registry не должны вызывать друг друга по кругу |
| Race condition между первым remote request и первым backgrounding | Низкая | Один пропущенный cleanup event в первой сессии | Синхронный `ensureInitialized()` до продолжения первого remote flow |
| Ошибка bootstrap оставляет процесс в half-initialized состоянии | Низкая | Degraded cleanup semantics или нестабильные remote session-ы | Идемпотентная инициализация, явный failure logging и отдельная tactical verification на partial-init scenario |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Внутренний рефакторинг.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Триггер только на consumer-side network entry boundary**

- **Решение:** Lifecycle bootstrap выполняется только на выделенной consumer-side boundary, через которую проходит первый реальный удалённый flow.
- **Альтернативы:**
   - Триггер в cleanup / enumeration API — отвергнут, так как это не semantic first use и такой путь может вызываться самим lifecycle observer-ом.
   - Триггер в DI registration path — отвергнут, так как он может сработать слишком рано и убить ленивость.
   - Триггер на уровне ViewModel — отвергнут, так как размазывает контракт по нескольким UI-путям.
- **Почему:** First-use semantics должна быть привязана к реальному входу в remote flow, а не к инфраструктурному перечислению уже существующих объектов.

**ADR-2: Bootstrapper и registry имеют разные обязанности**

- **Решение:** Выделить отдельный bootstrap-компонент для one-shot attach/init process-level lifecycle infrastructure. Registry остаётся passive lookup / cleanup view и не содержит скрытых side effects.
- **Альтернативы:**
   - Держать bootstrap в `Application` — отвергнуто, так как это сохраняет eager process-start wiring.
   - Встраивать bootstrap в registry — отвергнуто, так как это повышает риск рекурсии и смешивает first-use trigger с cleanup responsibility.
- **Почему:** Такое разделение проще верифицировать, тестировать и удерживать без циклической связности.

**ADR-3: Ленивость должна охватывать не только observer attach, но и protocol-gate assembly**

- **Решение:** Tactical spec обязана выбрать deferred assembly strategy для protocol-gate слоя, чтобы process-start путь не материализовал сетевой граф до первого удалённого доступа.
- **Альтернативы:** Ограничиться только lazy attach observers при сохранении eager gate materialization — отвергнуто.
- **Почему:** Иначе цель §2.2 не будет достигнута, а S0195 превратится в частичный cosmetic refactor.

---

## 10. Связи с другими спеками

- Parent: [S0193](S0193_lazy-init-research.md) — Phase 04 recommendation.
- Sibling: [S0194](S0194_lazy-hilt-singletons.md) — лёгкие 13 полей.
- Затрагивает поведение S0061 (SMB background lifecycle) и S0067 (protocol-neutral connection gates) — необходимо проверить совместимость на уровне acceptance criteria.

---

## 11. Критерии готовности

1. После запуска `standard`-сборки и работы только с локальными файлами process-level network lifecycle bootstrap не выполняется: нет регистрации network callback / lifecycle observer-ов и нет bootstrap trace.
2. Первый реальный удалённый flow любого поддерживаемого протокола синхронно выполняет bootstrap ровно один раз до продолжения сетевой операции.
3. Если приложение уходит в background до первого удалённого доступа, lifecycle cleanup path остаётся no-op и не инициализирует сетевой стек.
4. После первого удалённого доступа backgrounding/foregrounding сохраняет текущую семантику S0061 и S0067 для UI consumer-ов.
5. Tactical verification подтверждает, что protocol-gate graph не материализуется на cold start раньше первого удалённого доступа.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0195` — разобьёт работу на фазы (вероятно 3–4 фазы с осторожной миграцией).

## Revision History

- **2026-05-14** — by spec refinement (`GPT-5.4`, focus: consistency, completeness, verifiability)
   - Applied: 7. Proposed (DISCUSS): 0.

## Last Audit

**Date:** 2026-05-14
**Mode:** full (strategic + tactical, 4 phases)
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [x] First remote flow (SFTP) triggered bootstrap exactly once: log line `S0195: network lifecycle bootstrap complete` at 23:33:08 after ~13 s idle (session 2026-05-14 23:30–23:39)
- [x] Backgrounding after first use closed connections per S0061/S0067: `ConnectionThrottle: Cancelling active operations` at 23:37:06 on camera launch
- [ ] Local-files-only session (no remote access) — bootstrap stays absent: not covered in this session
