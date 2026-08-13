# Phase 09 - Diagnostic Lifecycle Fix (re-enter resilience)

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md) §1.1, §5.1.D.1, ADR-7
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress (code complete; on-device verification pending in BlockNeedUserTest)
**Depends on:** -
**Blocks:** Phase 02, Phase 10, Phase 11, Phase 01 (any on-device validation requires re-enter to be safe)
**Steps done:** 5 / 5
**Started:** 2026-05-22
**Completed:** - (awaiting on-device test loop)

---

## Objective

Сделать повторный вход в `DiagnosticXrActivity` в одном процессе безопасным: устранить JNI crash `NewGlobalRef on invalid jobject` при втором `nativeInitSession` и каскадный OOM на повторной декодировке bundled 8K. После фазы пользователь может последовательно `enter → exit → enter` минимум 5 раз без падений и без существенного heap-bloat.

---

## Prerequisites

- [ ] Read strategic §1.1, §5.1.D.1, ADR-7.
- [ ] Логи с 2026-05-22 02:20:42-46 как воспроизводимый baseline (`logs/current.log`).
- [ ] Working tree clean / feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp` | Modified | ≤ 200 |
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1350 |
| `app_v2/src/vr/cpp/xr_session.h` | Modified | ≤ 90 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt` | Modified | ≤ 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/DiagnosticXrRuntime.kt` | Modified | ≤ 130 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 740 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrRenderThread.kt` | Modified | ≤ 130 |

---

## Steps

### Step 09.1 - Снять «intentionally leaks» комментарий и привести native init/shutdown к paired-семантике

**Files:** `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp`

**Prompt for developer:**

> Удалить KDoc-блок `// Note: the global ref intentionally leaks for the session's lifetime…` (lines 47-49). Заменить его на: `// Paired with xr_session_shutdown (xr_session.cpp): DeleteGlobalRef(g.activity) выполняется там; init никогда не должен быть вызван, пока предыдущий shutdown не завершился.` Добавить guard в `nativeInitSession`: если `fms::xr::xr_session_is_initialized()` возвращает true — логировать ERROR «init called while previous session alive», возвращать `NativeResult::UnexpectedRuntimeError` без `NewGlobalRef`. Этим предотвращается JNI crash из лога.

**Verification:**

- `Grep` - `intentionally leaks` matches 0 times in `app_v2/src/vr/cpp/`.
- `Grep` - `xr_session_is_initialized` is declared in `xr_session.h` and defined in `xr_session.cpp`.
- `Grep` - `nativeInitSession` body contains an `if` early-return на `is_initialized()`.

**Status:** `[x] done`

---

### Step 09.2 - Полный paired shutdown native-стороны

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`, `app_v2/src/vr/cpp/xr_session.h`

**Prompt for developer:**

> В `xr_session_shutdown` явно освободить все pixel-буферы текстур (фото-path и video-path), сбросить `g.surfaceTexture`, `g.videoSurface`, `g.activity` в nullptr строго после `DeleteGlobalRef`. Добавить `xr_session_is_initialized()` (возвращает `g.instance != XR_NULL_HANDLE || g.vm != nullptr`). Убедиться, что `g.running.store(false)` срабатывает раньше освобождения GLES — иначе render thread читает дeallocated state. Логировать на DEBUG: `shutdown begin`, `shutdown DeleteGlobalRef done`, `shutdown complete` — для трассировки в логе.

**Verification:**

- `Grep` - `xr_session_is_initialized` defined once in `xr_session.cpp`.
- Manual: пройти по `xr_session_shutdown` глазами — все 4 `g.*` GlobalRefs (activity, videoSurface, videoSurfaceTexture, surfaceTextureClass, surfaceClass) очищаются до возврата из функции.
- Build: `.\a.ps1 nd` — компилируется.

**Status:** `[x] done`

---

### Step 09.3 - Java-сторона: shutdown гарантированно вызывается до возврата из onPause/onDestroy

**Files:** `DiagnosticXrActivity.kt`, `DiagnosticXrRenderThread.kt`, `NativeDiagnosticXrRuntime.kt`, `DiagnosticXrRuntime.kt`

**Prompt for developer:**

> В `DiagnosticXrActivity.onPause()` и `onDestroy()` вызывать `nativeDiagnosticXrRuntime.shutdown()` синхронно (с timeout 2 с на дождаться завершения render thread'а); только потом возвращаться. `DiagnosticXrRenderThread`: после `run`-loop'а гарантированно вызывать `nativeShutdown()` в `finally`-блоке, чтобы исключение не оставило native side в открытом состоянии. `NativeDiagnosticXrRuntime.shutdown()`: idempotent — повторный вызов не падает, просто no-op после `_shutdownCalled`.

**Verification:**

- `Grep` - `nativeDiagnosticXrRuntime.shutdown\(\)` matches inside `onPause` и `onDestroy` блоков `DiagnosticXrActivity.kt`.
- `Grep` - `finally\s*\{` followed by `nativeShutdown` matches at least once in `DiagnosticXrRenderThread.kt`.
- `Grep` - `_shutdownCalled` (или эквивалент idempotent-флага) в `NativeDiagnosticXrRuntime.kt`.

**Status:** `[x] done`

---

### Step 09.4 - Добавить on-device test loop в /spec-test-device скрипт

**Files:** `scripts/utils/setup_test_vr.ps1` (опционально дополнить test-loop вызовом) **или** временный markdown с инструкцией для ручной проверки

**Prompt for developer:**

> В разделе on-device verification в spec-test-device описать обязательный test-loop: «5 раз последовательно нажать Test Immersive → Exit, без перезапуска приложения. Лог не должен содержать `JNI ERROR`, `OutOfMemoryError`, `Runtime aborting`, `FATAL EXCEPTION`. После каждого Exit `adb shell dumpsys meminfo com.sza.fastmediasorter.debug | grep TOTAL` показывает delta ≤ 50 МБ к baseline.»

**Verification:**

- Manual: после Phase 09 deploy — пройти test-loop, собрать `logs/current.log` после 5 циклов; убедиться 0 крашей.

**Status:** `[x] done`

---

### Step 09.5 - Timber tag для подтверждения исправления (BlockNeedUserTest)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt`

**Prompt for developer:**

> При переводе спеки в `BlockNeedUserTest` добавить **строго один** `Timber.d("S0290: re-enter path - shutdown then init")` в `DiagnosticXrActivity.onPause()` и **строго один** `Timber.d("S0290: native session init guarded")` в `NativeDiagnosticXrRuntime.initSession()`. Это probe-теги — удаляются `/spec-check` при переходе в Verified (см. CLAUDE.md «Debug Verification Tags»).

**Verification:**

- `Grep` - `Timber\.d\("S0290: re-enter path` matches exactly once in VR source set.
- `Grep` - `Timber\.d\("S0290: native session init guarded` matches exactly once.
- Statuses: вставить только при `update.ps1 -Id S0290 -Status BlockNeedUserTest`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every Step 09.* is `[x] done`.
- [x] Build `.\a.ps1 nd` passes. (2026-05-22 13:14 + 13:25; both PASS exit 0; APK at DOWNLOADS/FastMediaSorter_nolegal_debug.apk)
- [ ] **MANUAL-REQUIRED** Test-loop 5×(enter→exit) на устройстве: 0 крашей, 0 JNI ERROR, 0 OutOfMemoryError в `logs/current.log`. — verified by owner after BlockNeedUserTest deploy.
- [ ] **MANUAL-REQUIRED** pss-heap после 5-го exit укладывается в baseline ± 50 МБ. — verified by owner after BlockNeedUserTest deploy.
- [x] Dev log entry для каждого файла из «Files Touched». (6 entries: xr_session.h, xr_session.cpp x2, diagnostic_xr_runtime.cpp, DiagnosticXrActivity.kt, NativeDiagnosticXrRuntime.kt, PHASE_09 doc)

---

## Manual On-Device Verification (Phase 09)

Run this loop on Quest 3 after the noLegalDebug APK is deployed and the Timber `S0290:`
probes (Step 09.5) are inserted. The loop is the authoritative test for the lifecycle fix —
the unit-style verifications above only confirm code structure, not runtime behaviour.

1. **Baseline.** Launch the app. Open the VR diagnostic mode entry point. Before the first
   immersive launch, capture baseline heap:
   ```bash
   adb shell dumpsys meminfo com.sza.fastmediasorter.debug | findstr TOTAL
   ```
   Record the `TOTAL PSS` value.
2. **Loop 5 times.** Each iteration:
   - Tap **Test Immersive** (or equivalent VR entry button).
   - Wait for the OpenXR session to render at least 2 s of content.
   - Use the controller / pinch / system back to **Exit** the immersive session and return
     to the 2D activity.
   - **Do not** kill the app between iterations.
3. **Post-loop heap.** After the 5th Exit, capture heap again with the same `dumpsys` line.
   `TOTAL PSS` delta vs baseline must be ≤ 50 MB.
4. **Logcat.** Pull `logs/current.log` or filter `adb logcat` for the session. Must contain:
   - Zero `JNI ERROR` lines.
   - Zero `OutOfMemoryError` lines.
   - Zero `Runtime aborting` lines.
   - Zero `FATAL EXCEPTION` lines.
   - Exactly 5 occurrences of `S0290: re-enter path - shutdown then init` (one per Exit).
   - Exactly 5 occurrences of `S0290: native session init guarded` (one per Enter).
5. **Sanity.** Each iteration's `xr_session_shutdown: begin` must be followed (eventually) by
   `xr_session_shutdown: DeleteGlobalRef(activity) done` and `xr_session_shutdown: complete`
   in that order, with no `xr_session_shutdown: begin` interleaving (i.e. shutdown is not
   re-entered while a previous shutdown is mid-flight).

PASS = all 5 loop iterations succeed, heap delta ≤ 50 MB, all 4 zero-occurrence conditions
hold, both `S0290:` probes appear the expected number of times, shutdown ordering invariant
holds.

---

## Handoff Notes to Next Phase

После Phase 09 повторный launch безопасен — это open's gate для всех остальных on-device тестов. Phase 11 (bitmap pipeline) сразу уменьшит даже первый launch heap pressure и снимет main-thread freeze.

---

## Rollback Plan

Revert phase commits — старая semantics «intentionally leaks» восстанавливается, повторный launch снова падает; первый запуск работает как раньше. Изменения чисто additive в части shutdown.
