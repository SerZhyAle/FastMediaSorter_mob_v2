# S0168 — Tactical Plan: ExoPlayer errorCode=1004 without user feedback

**Strategic spec:** `PLAN/S0168_bugfix-video-playback-stuck-buffering-no-feedback.md`
**Status:** In Progress
**Generated:** 2026-05-12

## Summary

Two remaining goals from strategic spec:
- **Goal 3** (§5.3): Pre-check native heap before ExoPlayer creation — Glide eviction + GC when free < 30 MB; warn user if still low after GC.
- **Goal 4** (§5.4): Elevate `PrefetchLoadControl: fallback standard defaults` log from D → W.

Goals 1 (Toast on errorCode=1004) and 2 (session cache) already implemented in previous iteration.

## Phases

| # | File | Scope | Status |
|---|---|---|---|
| 1 | [P1_heap-precheck.md](P1_heap-precheck.md) | `VideoPlayerManager.kt`: add Glide GC pre-check before playback | ☐ |
| 2 | [P2_prefetch-log-level.md](P2_prefetch-log-level.md) | `PrefetchLoadControlFactory.kt`: D → W for fallback standard defaults | ☐ |
