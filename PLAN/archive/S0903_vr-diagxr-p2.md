# S0903 - DiagnosticXrActivity: stale focus gate, dead HUD buffers, abandoned render thread (P2 cluster)

**Ticket:** S0903
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->
<!-- auto-approved by /spec-all - 2026-07-11 -->

## Goal

Три P2-находки статик-аудита в `DiagnosticXrActivity` (flavor `src/vr`, noLegal sideload) верифицированы против живого кода. Одна из них - реальный баг корректности VR re-entry: gate оконного фокуса (`windowFocusedDeferred`) - одноразовый, поэтому повторный запуск render-потока в рамках одного Activity стартует XR-сессию до перерегистрации волюметрического окна HzOS (риск чёрного экрана на re-entry). Комментарий рядом с полем ложно утверждает, что gate пересоздаётся. Вторая находка (мёртвые HUD-буферы) уже устранена в S0964 - фиксируем как resolved без правок. Третья (брошенный живой render-поток при таймауте join) реальна, но у зависшего нативного frame-loop нет безопасного синхронного kill - корректная мера уровня P2 - поднять лог до error с точной формулировкой утечки.

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Flavor src/vr (noLegal sideload family).

- app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt:112 - False load-bearing comment on windowFocusedDeferred: claims a new instance is created in maybeStartRenderThread, but no recreation exists - the focus gate is permanently open for any second render-thread start within one Activity instance
- app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt:286 - Dead HUD canvas buffers: ~4 MB (hudBitmap 1024x512 ARGB_8888 + hudRgbaBytes) allocated per activity with zero consumers since the S0290 ray-tick HUD path was removed
- app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt:1124 - shutdownRenderThreadSync abandons a still-alive render thread that strongly references the Activity and the live native EGL/OpenXR session on join timeout

### 0.1 Verification against live code (2026-07-11)

- **Finding 1 - CONFIRMED (real).** `maybeStartRenderThread` never reassigns `windowFocusedDeferred`; it is a one-shot field initializer. After `onPause` -> `shutdownRenderThreadSync` nulls `renderThread`, a re-resume starts a fresh render thread against the already-completed deferred, so `windowFocused.await()` returns instantly and the render thread skips the window-registration wait it was designed to enforce (see `DiagnosticXrRenderThread` KDoc, "On re-entry sessions the XrInstance is reused .. before HzOS registers the volumetric window"). Comment at the field is false.
- **Finding 2 - STALE (already fixed).** `hudRgbaBytes` was removed by S0964 (field note at the `hudBitmap`/`hudCanvas` declaration confirms "was dead and is gone"). `hudBitmap`/`hudCanvas` now have live consumers (`queueHud` HUD path). No code change - document only.
- **Finding 3 - CONFIRMED (real, bounded remedy).** On `join` timeout the thread stays alive holding a strong `activity` ref plus the live native session, and nulling `renderThread` abandons it. A wedged native `runFrameLoop` cannot be killed safely (no `Thread.stop`), so the correct P2 remedy is accurate error-level logging, not an unsafe termination.

## Phases

### Phase 01 - Re-arm the window-focus gate on render-thread teardown (Finding 1)

- Re-arm `windowFocusedDeferred` with a fresh `CompletableDeferred()` at the single teardown choke point `shutdownRenderThreadSync`, immediately after `renderThread = null`, so the next render-thread start awaits a fresh window-focus signal instead of an already-completed one. This is the only path that nulls `renderThread`, hence the only path that permits a restart, so it is the correct single re-arm site (cold start remains covered by the field initializer).
- Rewrite the false field comment on `windowFocusedDeferred` to state the truth: the deferred is created once at field init and re-armed on teardown in `shutdownRenderThreadSync`; it is NOT recreated in `maybeStartRenderThread`.
- Verification:
  - `grep -n "windowFocusedDeferred = CompletableDeferred" DiagnosticXrActivity.kt` shows two sites: the field initializer and inside `shutdownRenderThreadSync`.
  - The `windowFocusedDeferred` field comment no longer mentions `maybeStartRenderThread` as the recreation site.
  - `.\a.ps1 fkn` (noLegal Kotlin compile - VR is a noLegal-family flavor) passes.

### Phase 02 - Elevate abandoned-render-thread log; document Finding 2 as resolved (Findings 3 + 2)

- In `shutdownRenderThreadSync`, change the `thread.isAlive` branch log from `Timber.w` to `Timber.e`, wording it to name the concrete leak: the render thread is abandoned still-alive, retaining the Activity and the live native EGL/OpenXR session; a wedged native frame loop has no safe synchronous kill. Keep the line `<=120` chars (S0826) - wrap the interpolated timeout if needed.
- Do NOT attempt any thread termination or ref-nulling gymnastics - the native session leaks regardless while the loop is wedged; the honest remedy is correct-severity observability.
- No code change for Finding 2: the S0964 field note already records the dead `hudRgbaBytes` removal and the live `hudBitmap`/`hudCanvas` consumers. Leave as-is.
- Verification:
  - `grep -n "did not exit within" DiagnosticXrActivity.kt` shows the message emitted via `Timber.e`.
  - No `hudRgbaBytes` symbol remains in the file (`grep -c hudRgbaBytes` counts only the historical comment note, not a field).
  - `.\a.ps1 fkn` passes.

## 3. Owner inputs (Approval gate)

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878 (audit tail container - triage source), S0964 (removed the dead HUD buffers - Finding 2), S0290 (removed the ray-tick HUD path).
- **Flavor scope:** src/vr only (noLegal sideload family). No standard/lite/photos/legacy impact.

## Related

- S0878 (audit tail container - triage source); VR immersive re-entry hotspot (recreate XrInstance per entry).

## Last Audit

**Audited:** 2026-07-11 (static, via /spec-all). **Status: Verified.**

- **Phase 01 (Finding 1) - done.** `DiagnosticXrActivity.kt`: `windowFocusedDeferred` is now re-armed with a fresh `CompletableDeferred()` in `shutdownRenderThreadSync` after `renderThread = null` (line 1327), so every post-teardown restart awaits a fresh window-focus signal - closing the stale-focus-gate re-entry race. Field comment (line ~114-118) rewritten to state the real cold-start + re-arm locations; no longer claims recreation in `maybeStartRenderThread`. Cold start unchanged (field initializer). Only behavioral edge - a same-Activity re-resume where focus does not re-fire - resolves through the pre-existing graceful 5s `withTimeoutOrNull` path in `DiagnosticXrRenderThread` (logged, proceeds).
- **Phase 02 (Finding 3) - done.** Abandoned-render-thread branch now logs via `Timber.e` naming the concrete leak (Activity + native EGL/OpenXR session) and the absence of a safe synchronous kill for a wedged native `runFrameLoop`. No unsafe termination attempted - correct P2 remedy is observability.
- **Phase 02 (Finding 2) - no change.** Confirmed already resolved by S0964: `hudRgbaBytes` removed, `hudBitmap`/`hudCanvas` have live `queueHud` consumers.
- **Build:** `:app_v2:compileVrDebugKotlin` BUILD SUCCESSFUL (vr flavor is the source set that mounts `src/vr/java`).
- **Deferred (non-blocking):** on-Quest immersive re-entry smoke (pause -> resume -> confirm no black screen / session re-registers) - VR hardware not attached; not gating.
