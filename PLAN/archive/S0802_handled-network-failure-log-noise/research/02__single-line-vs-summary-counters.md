# 02 - Single-line outcomes vs summary counters

**Status:** Resolved
**Date:** 2026-06-29
**Method:** code audit of the existing thumbnail diagnostics format plus implementation-cost review for worker/manual-sync paths.

## Findings

1. The project already has a workable compact format for repeated network outcomes.
   - Thumbnail diagnostics use structured one-line records such as `scope=thumbnail`, `protocol=...`, `resource=...`, `failureClass=...`.
   - Those lines are grep-friendly and preserve the exact reason without dumping a throwable block.

2. Repeated summary counters would add new mutable state and lifecycle questions.
   - Worker/manual-sync code would need shared accumulation keys, reset boundaries, and flush timing.
   - Preview loading runs across Glide/background threads, which raises ownership questions for any cross-event aggregator.

3. The current pain point is severity/stacktrace inflation, not lack of aggregation.
   - Once handled paths stop logging `Timber.e(e, ..)` and full throwable blocks, the remaining single-line records are already much cheaper to scan.

## Decision

Do not introduce summary counters in this ticket.

- Keep one structured log line per handled network outcome.
- Normalize those lines so they carry stable tags (`scope`, `resource`, `failureClass`, or equivalent) and no throwable for handled branches.
- Revisit counters only if the log remains noisy after severity normalization with real post-fix evidence.

## Why no counters now

- It keeps S0802 focused on semantics, not telemetry infrastructure.
- It avoids inventing shared state for a problem that existing structured one-line logs already solve well enough.
- It reduces regression risk in worker and thumbnail paths while still meeting the user goal: real defects stand out again.
