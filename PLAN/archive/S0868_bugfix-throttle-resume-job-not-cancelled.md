# Спецификация (compact bugfix): S0868 - ConnectionThrottleManager - незакрытый 300ms resume-job гасит videoPlayerActive

**Ticket:** S0868
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1 (2026-07-02, dedicated skeptic). TRIGGER IS THE ROUTINE FLOW, not an edge case: SmbPlaybackHelper.kt:44 releasePlayer() -> VideoPlayerLifecycleHelper.kt:44 -> deactivateVideoPlayerMode(oldKey) schedules videoPlayerResumeJob = managerScope.launch { delay(300); videoPlayerActive = false } (:238-242, no re-check of videoPlayerResources.isEmpty() in the job body); the SAME synchronous call stack reaches activateVideoPlayerMode(newKey) (SmbPlaybackHelper.kt:73) milliseconds later - and activateVideoPlayerMode (:206-212) never references videoPlayerResumeJob (cannot cancel it). Stale job fires at t=300ms and unconditionally zeroes the global flag mid-new-playback. Same pattern applies to FTP/SFTP helpers. Confirmed consumers: withThrottle :390 'if (!highPriority && videoPlayerActive)' (thumbnail suspension) and isVideoPlayerActiveForResource :259 early-return feeding S0060/S0066 transient-failure classification at NetworkVideoFrameDecoder.kt:116/:203 (citation fix vs original finding: consumer is NetworkVideoFrameDecoder, not NetworkFileModelLoader - mechanism identical). No compensating mechanism in the file (resetAllSmbStates/cancelAllForResource unrelated). Fix shape: cancel videoPlayerResumeJob at activate entry + re-check videoPlayerResources.isEmpty() inside the job before writing.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt:206** - activateVideoPlayerMode does not cancel the pending 300ms resume job, whose unsynchronized delayed write sets videoPlayerActive=false during active playback - thumbnail suspension and S0060 transient-failure classification silently defeated
  - Evidence: deactivateVideoPlayerMode lines 238-242 schedules `videoPlayerResumeJob = managerScope.launch { delay(300); videoPlayerActive = false; ... }` - the job neither re-checks `videoPlayerResources.isEmpty()` nor takes `synchronized(videoPlayerResources)` before writing the @Volatile flag. activateVideoPlayerMode lines 206-212 sets `videoPlayerActive = true` and adds the key but never cancels videoPlayerResumeJob. Runtime path (routine): next-file navigation or slideshow auto-advance between network videos - VideoPlayerLifecycleHelper.releasePlayer():44 calls deactivateVideoPlayerMode(key) (schedules the job), the next video activates within <300ms via SmbPlaybackHelper.kt:73 / FtpPlaybackHelper.kt:51 / SftpPlaybackHelper.kt:54, then the stale job fires and clears videoPlayerActive for the entire remaining playback (nothing re-sets it until the NEXT activate call). Consequences confirmed in-file: withThrottle line 390 `if (!highPriority && videoPlayerActive)` stops blocking low-priority thumbnail loads (defeating the FTP close-race protection described at lines 235-237, '(prevents SIGSEGV)'), and isVideoPlayerActiveForResource line 259 `if (!videoPlayerActive) return false` makes S0060 misclassify playback-induced SMB timeouts as permanent failures, which NetworkFileModelLoader.markVideoAsFailed persists to disk.
  - Fix hint: In activateVideoPlayerMode cancel videoPlayerResumeJob (and null it) inside the synchronized block; in the resume job re-check videoPlayerResources.isEmpty() under the same lock before clearing the flag.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

ConnectionThrottleManager - незакрытый 300ms resume-job гасит videoPlayerActive. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

- `deactivateVideoPlayerMode`: при опустошении `videoPlayerResources` планирует `videoPlayerResumeJob` (`delay(300)` -> `videoPlayerActive = false`); тело job не перепроверяет `videoPlayerResources.isEmpty()` и пишет `@Volatile`-флаг без лока.
- `activateVideoPlayerMode`: выставляет `videoPlayerActive = true` и добавляет ключ, но не отменяет `videoPlayerResumeJob`.
- Рутинный путь (не edge-case): навигация между сетевыми видео вызывает `deactivate(old)`, затем `activate(new)` за <300ms; устаревший job срабатывает на t=300ms и гасит глобальный флаг до конца новой сессии (ничто не восстанавливает его до следующего `activate`).
- Следствия в том же файле: `withThrottle` (:390) перестаёт блокировать низкоприоритетные thumbnail-загрузки (теряется защита от FTP close-race); `isVideoPlayerActiveForResource` (:259) заставляет S0060 классифицировать playback-таймауты как постоянные сбои, которые persist на диск.

---

## 3. Исправление

Both edits in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt`.

1. In `activateVideoPlayerMode`, inside `synchronized(videoPlayerResources)` and before `add(resourceKey)`, cancel and null the pending resume job (`videoPlayerResumeJob?.cancel(); videoPlayerResumeJob = null`).
   - Verification: routine `activate` after a sibling `deactivate` cannot leave a live 300ms job.
2. In the resume job body, move `videoPlayerActive = false` inside `synchronized(videoPlayerResources)` and guard it with `if (videoPlayerResources.isEmpty())`.
   - Verification: closes the TOCTOU window where `cancel()` cannot stop the write after `delay()` returns - a concurrent `activate` re-populates the set, so the guard keeps the flag set.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` (standard Kotlin compile) - PASS.
- Static gates `.\a.ps1 fg` (neuroslop, pm-flags, listener, flavor, ticket-log) - PASS.
- `ConnectionThrottleManagerTest` unaffected: `activate` still adds the key and sets the flag; job re-check on an empty set behaves as before. No timer test added - the file deliberately does not assert the resume-timer path (real Default-dispatcher scope + delay); a sleep-based regression test would be flaky. Fix is inspection-verified.
- Optional on-device regression (deferred, not a merge gate): play a network (SMB/FTP) video, navigate to the next network video within 300ms, confirm thumbnails stay suspended and the playing resource is not written to the permanent failed-cache (S0060).

---

## Last Audit

**Date:** 2026-07-02
**Verdict:** Verified
**Method:** static - `compileStandardDebugKotlin` + scoped gates + concurrency inspection (CODE_AUDIT_PROTOCOL synchronization/coroutine trigger). On-device network regression is optional, not a merge gate.

- Fix present in `ConnectionThrottleManager.kt`:
  - `activateVideoPlayerMode` cancels + nulls `videoPlayerResumeJob` inside `synchronized(videoPlayerResources)` before adding the key.
  - Resume job clears `videoPlayerActive` only inside `synchronized(videoPlayerResources)`, guarded by `if (videoPlayerResources.isEmpty())`.
- Concurrency reasoning:
  - Routine race `deactivate(old)` -> `activate(new)` within 300ms is closed by cancel-on-activate.
  - Residual TOCTOU (job already past `delay()` when a concurrent `activate` runs) is closed by the re-check: a cancelled coroutine still executes the non-suspending `synchronized` block, but `isEmpty()` is then false, so the flag is preserved.
  - Single monitor (`videoPlayerResources`), no new lock nesting, no deadlock.
- `ConnectionThrottleManagerTest` unchanged and still valid; timer path intentionally not asserted (real Default-dispatcher scope + delay).

