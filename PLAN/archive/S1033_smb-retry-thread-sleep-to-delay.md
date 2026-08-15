# S1033 - Replace `Thread.sleep()` with coroutine `delay()` in SMB retry/backoff

**Status:** Archived
**Priority:** 45
**Date:** 2026-07-15
**Complexity:** Simple (documentation of deliberate exceptions; no behaviour change)

## Goal

`Thread.sleep()` в coroutine-коде блокирует поток пула вместо приостановки. Аудит S1030 нашёл 3 таких
места в SMB retry/backoff. Владелец задал минимальный объём (2026-07-14): менять на `delay()` только
там, где suspend-контекст уже есть; `handleFreshConnectionFailure` в suspend НЕ превращать - блокирующее
место задокументировать. Проверка показала, что suspend-шва нет ни у одного из трёх мест, поэтому все три
- задокументированные осознанные исключения.

## 0. Raw finding (auto-parked from S1030 audit, 2026-07-13)

Three `Thread.sleep()` sites, all in SMB retry/backoff:
- `data/network/SmbConnectionManager.kt` - `Thread.sleep(500)` in `handleFreshConnectionFailure`.
- `data/network/datasource/SmbDataSource.kt` - `Thread.sleep((100 * attempts))` (EOF retry).
- `data/network/datasource/SmbDataSource.kt` - `Thread.sleep((200 * attempts))` (protocol/share-closed retry).

## 1. Owner decision (2026-07-14) - approval gate resolved

- Minimal scope: convert `Thread.sleep()` -> `delay()` ONLY where a suspend context already exists.
- Do NOT make `handleFreshConnectionFailure` (or its chain) suspend - document/wrap the blocking site,
  leaving a documented `Thread.sleep` exception in place.
- No on-device reconnect/backoff verification required for this pass.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1030 (archived parent audit umbrella); S1027 (SMB logging trims, same files).

## 2. Investigation outcome - zero convertible sites

All three sites are already off the main thread and have **no suspend seam**, so none can move to
`delay()` under the owner's minimal-scope rule:

- `handleFreshConnectionFailure` returns `SmbResult<T>` synchronously. It is reached from a coroutine
  `withPermit` block, but the owner explicitly barred making it `suspend`. -> document.
- `SmbDataSource.readInternal` (both sites) runs on the dedicated `smbWatchdogExecutor`
  (`Executors.newCachedThreadPool`, a purpose-built blocking-SMB-I/O pool), submitted as a `Callable`
  and reached only from ExoPlayer's non-suspend `read()`/`open()` overrides. No shared IO-dispatcher
  thread is starved; the overrides' signatures are fixed and cannot be suspend. -> document.

The audit's "blocks a shared pool thread" premise does not hold for the `SmbDataSource` sites (dedicated
executor). The owner's incidental "one documented exception" becomes three, because the two data-source
sites are also seam-less blocking-read paths, not the shared coroutine pool.

## 3. Phases (all done)

### Phase 01 - document the three deliberate blocking sleeps `[x]`

- `SmbConnectionManager.kt`: WHY comment on `Thread.sleep(500)` - synchronous `SmbResult<T>`, owner-scoped
  non-suspend, SMB pool path, never main thread.
- `SmbDataSource.kt` (both sites): WHY comment - dedicated `smbWatchdogExecutor`, non-suspend ExoPlayer
  `read()` override, no coroutine seam / no shared-pool starvation.
  - Verification: `a.ps1 fk` compiles; neuroslop delta green (comments are substantive WHY, not trivial). **PASS.**

### Phase 02 - in-context hygiene `[x]`

- Replaced 4 pre-existing en-dashes with plain hyphens in `SmbConnectionManager.kt` comments/log string
  (touched-file cleanup, Rule 1/20).
  - Verification: em-dash delta 0. **PASS.**

## 4. Done criteria

1. Every `Thread.sleep` in the three SMB retry sites carries a WHY comment explaining the deliberate exception. `[x]`
2. No function was forced to `suspend`; no behaviour change. `[x]`
3. `a.ps1 fk` compiles; scoped detekt + neuroslop clean on the changed files. `[x]`
4. No on-device verification required (owner-waived). `[x]`

## 5. Notes

- If the SMB connection lifecycle is ever refactored to a fully suspend retry chain, these sites become
  convertible to `delay()`; the WHY comments name the exact seam that would have to open first.
- Parent audit: S1030 (archived umbrella).

## Last Audit

**Date:** 2026-07-15
**Mode:** full (Simple path S4)
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0

Investigation found zero convertible sites: all three `Thread.sleep` sites are off-main-thread with no
suspend seam under the owner's minimal-scope rule. `handleFreshConnectionFailure` returns `SmbResult<T>`
synchronously (owner-barred from suspend); `SmbDataSource.readInternal` (both sites) runs on the dedicated
`smbWatchdogExecutor` cached pool, reached only from ExoPlayer's non-suspend `read()`/`open()` overrides.
Delivered documentation WHY comments at all three; no function made suspend; no behaviour change. Cleaned 4
pre-existing en-dashes -> hyphen in the touched `SmbConnectionManager.kt` (Rule 1/20). `a.ps1 fk` passed;
scoped detekt clean on both files; neuroslop delta 0 across all dimensions. On-device verification
owner-waived. No new string keys.
