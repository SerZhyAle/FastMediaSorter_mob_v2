# Tactical plan: S0186 — pipeline cascade resilience

**Strategic spec:** [../S0186_nolegal-pipeline-cascade-resilience.md](../S0186_nolegal-pipeline-cascade-resilience.md)
**Status:** Tactical
**Phases:** 2

---

## Architectural anchor

`LinkAutoDownloadCoordinator.handleUrl` already wraps `strategy.probe(url)` in a try/catch (lines 169-175): catch `Throwable`, re-throw `CancellationException`, log `Timber.w`, `continue` to next strategy. The bug is that `strategy.open(url)` at line 186 is bare — any `PyException` / `RuntimeException` thrown by `YtDlpExtractionStrategy.open()` (line 138, `ydl.callAttr("extract_info", ...)`) escapes the `for`-loop, hits the outer `try`/`finally` (line 166 / 277), and propagates out of `handleUrl`. Strategies later in `CANONICAL_ORDER` (`site/NewPipe`, `direct`, `html`, `dynamic`) never get a probe.

Fix scope: one call site (`LinkAutoDownloadCoordinator.kt:186`). Symmetric to the existing probe wrapper. No changes to `UrlExtractionStrategy` contract — defensive coordinator-side catch is the contract change.

## Phases

- [Phase 01 — Safety-net unit tests for cascade fall-through](Phase01_cascade_tests.md)
- [Phase 02 — Wrap strategy.open() in try/catch](Phase02_wrap_open.md)

## Out of scope

- Per-strategy try/catch hardening (strategies remain responsible for their internal IO; coordinator wraps only the outermost call).
- Extraction of cascade iteration into a helper class to reduce `LinkAutoDownloadCoordinator` LOC (current 593 vs 450 budget) — separate refactor ticket.
- `LinkExtractionRegistry` test coverage gap — separate ticket if needed.
- YouTube-specific recovery (`youtube.com` in known-social, yt-dlp format-selector hardening) — covered by S0187.

## Risks

- Catch-all masking real bugs in strategy implementations. **Mitigation:** mandatory `Timber.w(throwable, ...)` on every caught throw — signal preserved.
- `CancellationException` accidentally swallowed → user-cancelled share never aborts. **Mitigation:** explicit `if (throwable is kotlinx.coroutines.CancellationException) throw throwable` mirror of probe wrapper.

## Verification predicates (rolled up from phases)

1. **Unit tests:** `LinkAutoDownloadCoordinatorTest.kt` includes cases proving cascade continues after `open()` throws, and `CancellationException` from `open()` propagates.
2. **Build:** `assembleStandardDebug` passes (shared coordinator code).
3. **noLegal build:** `assembleNoLegalDebug` passes.
4. **Manual / device:** YouTube share, after yt-dlp PyException, attempts NewPipe (or subsequent strategy). Logcat shows `Timber.w(... strategy=ytdlp ...)` and `S0186: open() threw for ytdlp, continuing` followed by `NewPipeSiteExtractionStrategy.probe` / similar evidence the chain advanced.

## Spec catalog sync

After each phase: `pwsh -File scripts/spec_catalog/update.ps1 -Id S0186 -Status <new>`. Phase 01 sets `Tactical`; Phase 02 sets `In Progress` then `Implemented` (then `BlockNeedUserTest` after tags inserted).
