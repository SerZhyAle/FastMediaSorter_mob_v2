# Спецификация (compact bugfix): S0867 - SmbConnectionManager - RMW-гонка на @Volatile consecutiveTimeouts

**Ticket:** S0867
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED but DOWNGRADED to P2 (2026-07-02, dedicated skeptic). Race is real: @Volatile var (:122-123), unguarded ++ at :500/:568/:576/:661, resets at 9 sites, no Atomic/lock/Mutex anywhere on the three companion vars; all increment sites sit inside connectionSemaphore(16).withPermit on real Dispatchers.IO threads (scanners, Glide fetchers, file ops) - genuinely concurrent. Functional impact exists but is BOUNDED self-healing lag, not a stuck state: strike-3 pool eviction (:508-509) can fire late (stale connection retained past design point); lastAutoResetTime check-then-act (:735-744) can double-execute closeAllConnections+resetClients (wasted but bounded). Every timeout path still closes its own failing connection (:281/:287/:509/:583/:673/:855) -> system converges, only slower. Fix is proven in-repo: ConnectionThrottleManager :66-69 uses AtomicInteger with incrementAndGet (:434/:465) for the identical per-protocol counter - mirror that (AtomicInteger + compareAndSet for the cooldown).

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt:500** - consecutiveTimeouts++ is an unsynchronized read-modify-write on a @Volatile companion var from up to 16 concurrent SMB operations - lost increments delay degradation/auto-reset thresholds; lastAutoResetTime cooldown is check-then-act
  - Evidence: Companion lines 122-123: `@Volatile private var consecutiveTimeouts = 0` (also `lastSuccessfulOperation`:126, `lastAutoResetTime`:129). Unguarded increments at line 500 `consecutiveTimeouts++` (handleTimeout), 568 and 576 (handleFreshConnectionFailure/SMBRuntimeException branch), 661; threshold checks 503/508 (`>= 3` pool drop), 269 (`>= TIMEOUT_CRITICAL_THRESHOLD` full reset), plus cooldown check-then-act at 736-744 (`timeSinceLastReset = currentTime - lastAutoResetTime` .. `lastAutoResetTime = currentTime` with no lock). @Volatile makes reads/writes visible but `++` is still load-add-store, and this @Singleton manager admits up to `connectionSemaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS=16)` (line 135) concurrent operations from Dispatchers.IO scanners, Glide fetcher threads and Media3 loader threads. Runtime path: NAS becomes unreachable while a browse grid loads - a dozen thumbnail ops time out near-simultaneously, concurrent ++ loses counts, so the degradation warning (5), forced-reconnect (3) and critical full reset (20) fire late or not at all, and two threads can pass the auto-reset cooldown together and double-reset the pool.
  - Fix hint: Replace the three companion vars with AtomicInteger/AtomicLong (incrementAndGet, compareAndSet for the cooldown), matching what ConnectionThrottleManager.ProtocolState already does.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

SmbConnectionManager - RMW-гонка на @Volatile consecutiveTimeouts. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

Три companion-`@Volatile var` (`consecutiveTimeouts`, `lastSuccessfulOperation`, `lastAutoResetTime`) мутируются без синхронизации из до 16 конкурентных SMB-операций (`connectionSemaphore(16)`, Dispatchers.IO). `@Volatile` даёт видимость, но `++` остаётся load-add-store - потерянные инкременты откладывали пороги degradation(5)/forced-reconnect(3)/full-reset(20); cooldown auto-reset был check-then-act - два потока могли пройти проверку вместе и сделать двойной reset пула. Подтверждено скептиком: bounded self-healing lag (P2), не stuck state.

---

## 3. Исправление

- `consecutiveTimeouts` -> `AtomicInteger`, `lastSuccessfulOperation`/`lastAutoResetTime` -> `AtomicLong` - зеркалит проверенный в репо паттерн `ConnectionThrottleManager.ProtocolState`.
- Все три increment-сайта (`handleTimeout`, `handlePooledConnectionFailure` x2-ветки, `handleFreshConnectionFailure`) судят пороги по СВОЕМУ результату `incrementAndGet()` (локальная переменная), а не по повторному чтению - конкурентное обновление не даёт double-fire/пропуск порога.
- `autoResetIfNeeded`: cooldown через `compareAndSet(lastReset, currentTime)` - из N потоков, прошедших проверку, ровно один выполняет reset.
- Голая `3` из `handleTimeout` вынесена в `TIMEOUT_FORCED_RECONNECT_THRESHOLD` (detekt-clean-first).
- Сброс-сайты (`onSuccess`, idle/critical reset, manual reset, network reconnect) -> `.set(..)`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` - BUILD SUCCESSFUL (2026-07-02, exit 0).
- Grep-инвариант: прямых мутаций `consecutiveTimeouts =`/`++`/`lastSuccessfulOperation =`/`lastAutoResetTime =` не осталось (только val-декларации атомиков).
- Девайс-репро нецелесообразен: потерянный инкремент недетерминирован; статическое доказательство + зеркалирование проверенного паттерна (`ConnectionThrottleManager`).

---

## Last Audit

**Date:** 2026-07-03
**Verdict:** Verified

- Fix: `SmbConnectionManager.kt` - three companion `@Volatile var` replaced by `AtomicInteger`/`AtomicLong`; all increment sites judge thresholds on their own `incrementAndGet()` result; auto-reset cooldown is `compareAndSet` (single winner); bare strike literal extracted to `TIMEOUT_FORCED_RECONNECT_THRESHOLD`.
- Counters are companion-private - no external callers to migrate (grep confirmed).
- Validation: `.\a.ps1 fk` - expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (exit 0); post-edit grep - expected: 0 raw mutations | actual: 0 (3 val declarations only).

