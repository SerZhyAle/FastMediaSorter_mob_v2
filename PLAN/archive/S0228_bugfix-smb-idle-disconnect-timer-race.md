# Стратегическая спецификация: S0228 — Устранение гонки idle-disconnect таймера в SMB-цепочке

**Ticket:** S0228
**Status:** Verified
**Implemented date:** 2026-05-16
**Priority:** 90
**Date:** 2026-05-16
**Tier:** 2 — Easy (ad-hoc, bugfix)
**Roadmap entry:** Ad-hoc — анализ `logs/current.log` 2026-05-16 (повторные `IdleDisconnect: timeout fired` по одному SMB transport в одном idle-окне)
**Tactical spec:** `PLAN/S0228_bugfix-smb-idle-disconnect-timer-race/` (создан через `/spec-tech`)
**Tactical plan:** [`PLAN/S0228_bugfix-smb-idle-disconnect-timer-race/INDEX.md`](S0228_bugfix-smb-idle-disconnect-timer-race/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

В `logs/current.log` от 2026-05-16 один и тот же SMB transport (`smb@192.168.1.110:445:media:sza:`) получает серию `IdleDisconnect: timeout fired` в окне `14:14:31..14:14:38`. Плотность событий несовместима с идеей «один transport -> один live timeout job -> один callback на одно idle-окно». В той же сессии рядом видны `DiskShare closed during read, reconnecting`, успешные `reopenConnection`, а также browse-side audio metadata burst. Это указывает на вероятную гонку в слое idle-disconnect, а не просто на обычное истечение таймера.

Пользовательская поверхность выглядит как избыточный reconnect churn, лишнее закрытие активного транспорта, шумные предупреждения в playback path и вклад в jank на экране браузера/плеера. Даже если SMB-путь частично самовосстанавливается, повторные timeout callback'и на одном ключе размывают диагностику и мешают отделить transport-race от реальной нагрузки по памяти.

---

## 2. Цели

1. Для одного transport-key в каждый момент времени существует не более одного live timeout job и не более одного timeout callback на одно idle-окно.
2. Конкурентные `arm`/`touch`/`disarm` по одному SMB transport не могут оставить в системе stale callback, который позже закроет уже переиспользуемое соединение.
3. После периода idle SMB browse и SMB playback не производят burst из нескольких `timeout fired` по одному и тому же transport.
4. Исправление не добавляет лишних сетевых ping/probe в горячий путь checkout соединения.

**Non-goals:**

- Полный редизайн всей idle-политики для всех протоколов одним тикетом.
- Изменение пользовательских текстов или UI-логики при потере соединения.
- Переписывание playback reconnect path в целом — здесь фиксируется именно invariant таймера.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Желательно сохранить текущую идею best-effort idle disconnect без дополнительных сетевых round-trip в момент обычного checkout.
2. Желательно сделать timeout path идемпотентным: stale callback должен уметь тихо завершиться, не ломая transport, если ownership уже ушёл к новому таймеру.
3. Желательно получить диагностический признак exact-once поведения в логах, чтобы повторные всплески ловились без ручного diff больших logcat-файлов.

### 3.2 Жёсткие ограничения

- **Flavor:** все source set'ы, где присутствует SMB stack (`standard`, `noLegal`, `legacy`, VR-варианты). `lite` и `photos` вне scope, если соответствующий SMB path в них не собирается.
- **API level:** без новой Android API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** hot path SMB checkout не должен получать дополнительный сетевой ping. Допустимы только локальные синхронизация, generation-token и in-memory проверки.
- **Совместимость данных:** без изменения хранимых форматов и схем.
- **Локализация:** пользовательские строки не меняются.

---

## 4. Контекст текущей архитектуры

SMB-цепочка уже использует общий idle-disconnect слой как singleton-политику. Один и тот же transport-key трогают как обычные операции браузера, так и ExoPlayer-путь, а browse-side metadata enrichment может добавлять параллельные SMB-чтения на том же ресурсе. В такой архитектуре простого «последний `touch` победил» недостаточно: таймеру нужен сильный ownership-инвариант, иначе старый callback способен дожить до firing и закрыть transport уже после того, как новый путь посчитал его валидным.

Текущее наблюдение по логу не доказывает конкретную строку кода, но даёт чёткую локальную гипотезу: состояние «какой timeout job сейчас владеет transport-key» не защищено достаточно жёстко, и stale callback не умеет доказуемо самоотменяться перед закрытием транспорта.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп A — строгий ownership timeout job по transport-key.** Для каждого transport-key вводится явная модель ownership: generation/token/version или эквивалентный invariant, позволяющий любому callback понять, что он всё ещё latest-owner перед выполнением close/invalidate.

**Столп B — stale callback suppression перед закрытием транспорта.** Даже если старый timeout job физически добежал до firing, его callback должен завершиться без side effect, если transport уже re-armed более свежим событием.

**Столп C — concurrency coverage и диагностические критерии.** Fix не считается закрытым без тестов/лог-предикатов на concurrent `touch`/`arm`/`disarm` и без подтверждения exact-once поведения timeout path.

### 5.2 Потоки данных и событий

Обычная последовательность остаётся прежней: browse/playback path работает с SMB transport, idle-политика переармливается на активности, timeout callback по истечении окна просит transport cleanup. Меняется только invariant между `rearm` и `fire`: callback больше не может действовать по одному имени transport-key без проверки, что он владеет актуальным поколением этого ключа.

### 5.3 Точки расширяемости

- Shared idle-layer invariant должен быть переиспользуемым и для других протоколов, но acceptance этой спеки остаётся SMB-specific.
- Если в дальнейшем понадобится общий helper для transport-generation ownership, он должен жить в shared слое, а не дублироваться в каждом протоколе.

---

## 6. Открытые вопросы / Research items

1. **Generation-token или per-key mutex?**
   - **Вопрос:** Что даёт более дешёвый и проверяемый invariant — номер поколения у timeout job или отдельная сериализация по ключу?
   - **Нужно выяснить:** что проще покрыть unit-тестом без искусственных `sleep` и без расширения scope на другие протоколы.
   - **Решение:** использовать generation-token в shared idle-layer.
   - **Почему:** generation-token не держит lock на сетевом или callback-path, локализует правку в одном shared компоненте и естественно покрывается coroutine test scheduler'ом через deterministic `arm -> touch -> old-deadline -> new-deadline` сценарии.
   - **Статус:** Resolved

2. **Где должен лежать stale-callback guard?**
   - **Вопрос:** Достаточно ли защитить только shared idle-layer, или SMB cleanup path тоже должен перепроверять ownership перед close/invalidate?
   - **Нужно выяснить:** какой минимальный слой правки гарантирует exact-once поведение без дублирования условий в двух местах.
   - **Решение:** stale-callback guard живёт только в `IdleDisconnectPolicyImpl`; SMB callback entrypoint остаётся single-purpose cleanup helper без дублирующей ownership-проверки.
   - **Почему:** ownership state уже принадлежит shared idle-layer. Повторная проверка в SMB cleanup path дублировала бы invariant, но не давала бы новой корректности, если старое поколение вообще не допускается до callback.
   - **Статус:** Resolved

3. **Как отделить race от обычного reconnect churn?**
   - **Вопрос:** Какой log predicate считать признаком именно этой гонки, а не штатного idle timeout + reconnect?
   - **Нужно выяснить:** зафиксировать машинно-проверяемый критерий вида «не более одного `timeout fired` по ключу за одно idle-окно».
   - **Решение:** manual acceptance predicate для одного `transport=` в рамках одного 30 s idle-окна таков: не более одного `IdleDisconnect: timeout fired`, допускается `IdleDisconnect: stale timeout dropped` для superseded generation, и до следующего `touch`/`arm` не должно появляться второй `timeout fired` по тому же transport-key.
   - **Почему:** такой predicate отделяет штатный single timeout + reconnect от duplicate-firing race и прямо отражает стратегическую цель exact-once semantics.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Fix в shared idle-layer заденет SFTP/FTP поведение | Средняя | Непредвиденная регрессия в соседних протоколах | Держать SMB acceptance отдельным, а shared invariant — минимальным и покрытым тестами |
| Слишком грубая сериализация по ключу начнёт тормозить hot path | Низкая | Лишние задержки на активном browse/playback | Не держать глобальный lock на сетевом IO, сериализовать только ownership state |
| Stale callback suppression скроет реальные timeout'ы из лога | Низкая | Сложнее диагностировать настоящие idle disconnect | Добавить отдельный debug-маркер «stale timeout dropped» без повышения шумности |
| Scope начнёт разрастаться до «починить все протоколы» | Средняя | Tactical plan потеряет фокус | Формально ограничить acceptance этой спеки SMB-path'ом, а cross-protocol follow-up делать отдельно |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Это bugfix transport-слоя: пользователь не получает новую возможность, он перестаёт сталкиваться с лишним reconnect churn и нестабильным поведением после idle.

---

## 9. Архитектурные решения (ADR)

**ADR-1: exact-once timeout — это invariant, а не best-effort пожелание.**

- **Решение:** для одного transport-key timeout callback допускается ровно один раз на одно idle-окно.
- **Альтернативы:** терпеть duplicate firing и полагаться на идемпотентность close path.
- **Почему:** даже идемпотентный close path не убирает reconnect churn, шум лога и размывание acceptance-критериев.

**ADR-2: shared-layer fix допустим, но acceptance остаётся SMB-specific.**

- **Решение:** если правка лежит в shared idle-layer, критерии готовности всё равно формулируются через SMB evidence.
- **Альтернативы:** расширить тикет сразу на все протоколы.
- **Почему:** текущий воспроизводимый сигнал получен именно в SMB-сценарии; раздувать scope без отдельного протокольного acceptance нецелесообразно.

**ADR-3: никаких сетевых health-probe в hot path.**

- **Решение:** fix достигается локальным ownership state, а не дополнительными сетевыми проверками.
- **Альтернативы:** ping/probe перед каждым checkout или перед каждым timeout close.
- **Почему:** проблема выглядит как локальная гонка состояния; сетевой probe только поднимет latency и не гарантирует исчезновение stale callback.

---

## 10. Связи с другими спеками

- **S0219** (`In Progress`) — аналогичный idle/retry тикет для SFTP. S0228 не подменяет его, а фиксирует SMB-проявление и shared invariant.
- **S0207** (`In Progress`) — memory-reduction spec, в рамках которой этот burst впервые всплыл как мешающий фактор для чистого memory acceptance.
- **S0213** (`BlockNeedUserTest`) — defensive playback hardening, поверх которой SMB reconnect churn всё ещё может добавлять шум и косвенную нагрузку.
- **S0229** (`Draft`) — sibling ticket для SMB browse-side audio metadata instability, идущей рядом в той же лог-сессии.

---

## 11. Критерии готовности (strategic-level)

1. В сценарии SMB browse/playback после idle не наблюдается серии из нескольких `IdleDisconnect: timeout fired` по одному и тому же transport в пределах одного idle-окна.
2. Timeout callback для stale поколения transport-key не приводит к close/invalidate активного транспорта.
3. Исправление не добавляет сетевой probe в обычный SMB checkout path.
4. Появляется воспроизводимый тест или лог-предикат exact-once timeout semantics для concurrent `touch`/`arm`.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0228` — создать tactical breakdown с проверкой ownership/generation invariant и SMB-specific acceptance.

## Revision History

- **2026-05-16** — by `/spec-tech` continuation with delegated engineering defaults
   - Applied: resolved §6.1/§6.2/§6.3 research items directly in the strategic spec (generation-token ownership, shared-layer-only stale guard, exact manual log predicate).

---

## Last Audit

**Date:** 2026-05-20
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [x] SMB transport `smb@192.168.1.110:445:media:sza:` — 4 idle-windows in session log, each producing exactly one `latest idle timeout accepted` + one cleanup. No duplicate firing.
- [x] SFTP transport `sftp@46.54.0.135:22022:sza` — 2 idle-windows, same exact-once behaviour.
- [x] `stale timeout dropped` marker present in `IdleDisconnectPolicyImpl.kt:84` per §6.3 predicate.