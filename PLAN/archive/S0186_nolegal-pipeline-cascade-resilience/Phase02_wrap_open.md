# Phase 02 — Wrap strategy.open() in try/catch

**Goal:** make `LinkAutoDownloadCoordinator.handleUrl` resilient to any `Throwable` from `strategy.open(url, onProgress)`. Mirror the existing probe wrapper at lines 169-175. Insert `Timber.d("S0186: …")` debug tag at the catch site for on-device verification.

## Touch points

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` — change at line 186-188 (the `val opened = strategy.open(url) { … }` call).

## Steps

1. Locate the call site at line 186-188:
   ```kotlin
   val opened = strategy.open(url) { read, total ->
       callbacks.onProgress(ProgressState.Downloading(read, total))
   }
   ```

2. Replace with the same shape as the probe wrapper at lines 169-175. New code:
   ```kotlin
   val opened = try {
       strategy.open(url) { read, total ->
           callbacks.onProgress(ProgressState.Downloading(read, total))
       }
   } catch (throwable: Throwable) {
       if (throwable is kotlinx.coroutines.CancellationException) throw throwable
       Timber.w(throwable, "LinkAutoDownloadCoordinator: open threw for %s", strategy.id)
       Timber.d("S0186: open() threw for %s, continuing cascade", strategy.id)
       continue
   }
   ```
   - **Rationale for two log lines:** `Timber.w` is the permanent diagnostic (with stack), `Timber.d("S0186: …")` is the debug verification tag tied to `BlockNeedUserTest` per CLAUDE.md. The tag will be removed when status leaves `BlockNeedUserTest`.

3. No other lines change. The `when (opened)` block stays as-is — the `continue` short-circuits past it. Coordinator LOC delta: +7.

4. Build gate (skill orchestrator handles this automatically):
   - `pwsh -File a.ps1 standard debug` → must PASS.
   - `pwsh -File a.ps1 nolegal debug` → must PASS (shared code; safety check).

5. Re-run unit tests added in Phase 01 — all three must PASS now:
   - `open_throwing_strategy_does_not_abort_cascade`
   - `cancellation_in_open_propagates_immediately`
   - `probe_throwing_already_handled_does_not_regress`

## Verification predicates

- `LinkAutoDownloadCoordinator.kt:186-` matches the wrapped shape above.
- `Timber.d("S0186: open() threw for %s, continuing cascade", strategy.id)` line exists exactly once in the file.
- Both builds pass.
- Phase 01 unit tests are green.

## Post-implementation

1. `pwsh -File scripts/spec_catalog/update.ps1 -Id S0186 -Status Implemented`.
2. /spec-check S0186 (audit).
3. If audit Verified — flip status to `BlockNeedUserTest` for device verification (Timber.d tag stays until owner confirms in logcat that YouTube share, after yt-dlp PyException, advances to NewPipe; then `/spec-check` flips to `Verified` and strips the tag).

## Spec catalog sync

`pwsh -File scripts/spec_catalog/update.ps1 -Id S0186 -Status "In Progress"` at start, then `-Status Implemented` at end.
