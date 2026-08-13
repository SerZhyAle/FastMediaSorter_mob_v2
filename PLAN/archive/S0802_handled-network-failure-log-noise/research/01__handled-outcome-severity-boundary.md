# 01 - Handled outcome severity boundary

**Status:** Resolved
**Date:** 2026-06-29
**Method:** code audit of `NetworkVideoFrameDecoder`, `NetworkMediaDataSource`, `NetworkReachabilityGate`, `NetworkFilesSyncWorker`, `SyncNetworkResourcesUseCase`, and `NetworkErrorClassifier`.

## Findings

1. The stream-preview path is already partially normalized.
   - `NetworkVideoFrameDecoder` logs handled preview outcomes as single-line `failureClass=...` messages without stack traces.
   - `NetworkMediaDataSource` does the same for stale-share / timeout / protocol-transient reads.
   - Only the explicit "unexpected" branch keeps `Timber.e(e, ..)`.

2. The SMB Wi-Fi gate already has a typed semantic boundary.
   - `NetworkReachabilityGate.requireWifi()` throws `WifiRequiredException` before any socket attempt.
   - Browse/UI surfaces already map that type to the user-facing Wi-Fi-required copy instead of a generic outage.

3. The remaining noise comes from catch-all sync paths.
   - `NetworkFilesSyncWorker` still logs every per-resource sync exception as `Timber.e(e, "Failed to sync ..")`.
   - `SyncNetworkResourcesUseCase` does the same for manual sync.
   - That erases the distinction between "policy gate fired before transport" and "real unexpected defect".

## Decision

Use a hybrid severity policy by handled-outcome type:

- `Timber.i` for expected policy/preflight skips where no remote endpoint was contacted.
  - Current concrete case: `WifiRequiredException` from the SMB Wi-Fi gate.
  - Same bucket also fits future "source disabled / no-network preflight" style outcomes.
- `Timber.w` for handled degraded outcomes where the app falls back or skips a best-effort enhancement.
  - Current concrete case: stream thumbnail/frame preview fallback (`timeout`, `null-frame`, `stale-share`, protocol transient).
  - These stay visible because they describe degraded media UX, but they must not carry throwable stack traces.
- `Timber.e` only for unexpected / unclassified transport or runtime defects.
  - Current concrete cases: uncaught classifier misses, cache-write failures, worker-level aborts that should never happen in the normal handled path.

## Why this boundary

- Wi-Fi-gate rejection is not an operational warning about the remote server - it is a local policy decision before any socket I/O, so `I` is enough.
- Preview fallback does represent degraded media quality/visibility, so keeping it at `W` preserves grep-visible signal without pretending the app itself is broken.
- The current problem is not "too many log lines" by itself; it is "handled control-flow logged with throwable/E-level semantics". Fixing that boundary is the highest-signal change.
