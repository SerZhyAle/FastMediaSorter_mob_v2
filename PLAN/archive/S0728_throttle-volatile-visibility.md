# Спецификация (fix): S0728 - ConnectionThrottleManager: @Volatile для кросс-потоковых полей

**Ticket:** S0728
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0716 (Layer 2, P3 visibility)
**Umbrella:** S0714

> **Scope:** Две тривиальные правки видимости памяти. Найдено статически (S0716).

---

## 0. Источник

Две P3-находки аудита S0716 (`PLAN/S0716_concurrency-correctness-audit/AUDIT_FINDINGS.md`) измерения «data races»: JMM-visibility-гонки на `var`-полях `ConnectionThrottleManager`, читаемых из IO-корутин без read-side барьера. Доброкачественные (self-correcting), но автор уже применил `@Volatile` к `videoPlayerActive` - пропуск реален.

## 1. Находки и правки

1. **`data/network/ConnectionThrottleManager.kt:79` `userDefinedNetworkLimit`.** Plain `var` на process-wide object; пишется на Main (`setUserNetworkLimit`) и из фонового settings-collect, читается из каждой SMB/SFTP/FTP/CLOUD `withThrottle`-корутины. **Fix:** `@Volatile`.
2. **`data/network/ConnectionThrottleManager.kt:64,67` `ProtocolState.currentLimit`/`isDegraded`.** Plain `var` на singleton через ConcurrentHashMap (безопасная публикация ссылки, но не последующих мутаций var); пишутся под `synchronized(state)`, читаются без барьера на congestion/semaphore-путях. **Fix:** `@field:Volatile` на оба поля.

## 2. Статус

Реализовано в этом тикете (тривиально-безопасно, политика inline). `compileStandardDebugKotlin` - зелёный.

## 3. Критерии приёмки

- [x] `@Volatile`/`@field:Volatile` добавлены к трём полям.
- [x] Компиляция зелёная; поведение не меняется (только read-side memory barrier).

## 4. Связанные тикеты

- S0716 (аудит-источник), S0714 (зонтик).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 2 · WARN 0 · FAIL 0

Обе P3-правки видимости памяти (S0716 Layer 2) подтверждены в `ConnectionThrottleManager.kt`:

- #1 `userDefinedNetworkLimit` (`:81-82`) - `@Volatile` (пишется на Main + background settings-collect, читается из IO throttle-корутин).
- #2 `ProtocolState.currentLimit`/`isDegraded` (`:65`/`:68`) - `@field:Volatile` (пишутся под `synchronized(state)`, читаются без барьера на congestion/semaphore-путях).

Паритет с уже `@Volatile` `videoPlayerActive` (`:109`). `compileStandardDebugKotlin` зелёный (main собран в проходе S0732); поведение не меняется (только read-side memory barrier).
