# Phase 1 — Pass/Fail Contract and Scenario Matrix

**Status:** [x] Done

## Scenario matrix

### Image surface

| Scenario | Mode | Transitions | Min duration |
|----------|------|-------------|-------------|
| IMG-1 | Manual next/previous | 50+ | 10 min |
| IMG-2 | Auto slideshow (sequential) | 50+ | 10 min |
| IMG-3 | Auto slideshow (random order) | 50+ | 10 min |

Warmup: first 5 transitions excluded from trend analysis.

### Audio surface

| Scenario | Mode | Transitions | Min duration |
|----------|------|-------------|-------------|
| AUD-1 | Manual track changes | 50+ | 10 min |
| AUD-2 | Continuous playback, sequential | 50+ | 10 min |
| AUD-3 | Continuous playback, shuffle | 50+ | 10 min |

### Video surface

| Scenario | Mode | Transitions | Min duration |
|----------|------|-------------|-------------|
| VID-1 | Sequential list transitions | 50+ | 10 min |
| VID-2 | Repeated back/next | 50+ | 10 min |
| VID-3 | Shuffle mode (where supported) | 50+ | 10 min |

### Large folder browse/sort surface

| Scenario | Mode | Cycles | Min duration |
|----------|------|--------|-------------|
| BRW-1 | Open heavy folder → sort change × N | 20+ sort changes | 10 min |
| BRW-2 | Repeated folder enter/exit | 30+ enter/exit | 10 min |
| BRW-3 | Filter type changes on large folder | 30+ filter changes | 10 min |

"Large folder" = ≥ 500 mixed media files.

### Mixed surface (audio + image simultaneous)

The app supports two modes where audio and image surfaces are active at the same time:

1. **Audio-background slideshow** (`AudioSlideshowPhotoModeManager`): music plays in the background while slideshow auto-advances images.
2. **Photo browse during audio playback**: audio playlist is running via `AudioPlaybackService` / background service while the user navigates the image viewer or browse list.

Both modes exert simultaneous pressure on audio decoder, image decoder, thumbnail cache, and playback state. They are higher-risk than single-surface scenarios.

| Scenario | Mode | Transitions | Min duration |
|----------|------|-------------|-------------|
| MIX-1 | Audio slideshow (audio running + slideshow auto-advance images) | 50+ image transitions | 10 min |
| MIX-2 | Audio background + manual image browse (audio service running, user navigates image viewer) | 50+ image transitions | 10 min |

MIX-1 and MIX-2 apply to `standard` and `legacy` flavors only (require both AUDIO + IMAGES). Skip for `lite` (no AUDIO) and `photos` (no AUDIO).

## Flavor coverage

| Surface | standard | lite | photos | legacy |
|---------|:--------:|:----:|:------:|:------:|
| IMG | ✓ | ✓ | ✓ | ✓ |
| AUD | ✓ | — | — | ✓ |
| VID | ✓ | ✓ | — | ✓ |
| BRW | ✓ | ✓ | ✓ | ✓ |
| MIX | ✓ | — | — | ✓ |

## Checkpoint structure

Each scenario emits checkpoints at:

1. **BASELINE** — before any transitions (after app warm, before scenario start).
2. **WARMUP_END** — after transition #5.
3. **CYCLE_END** — at transitions 10, 25, 50 (and every 25 thereafter).
4. **SCENARIO_END** — after the last transition.
5. **COOLDOWN_END** — 30 seconds after SCENARIO_END.

Measurements per checkpoint:
- Java heap used (MB): `Runtime.getRuntime().totalMemory() - freeMemory()`
- Java heap max (MB): `Runtime.getRuntime().maxMemory()`
- Native heap allocated (MB): `Debug.getNativeHeapAllocatedSize()`

## Pass/fail rule

Applied to CYCLE_END and COOLDOWN_END checkpoints:

```
delta(cycle N) = (retained_N - retained_{N-1}) / retained_{N-1}
```

- **PLATEAU**: all cycle deltas < 15%.
- **SUSPICIOUS**: any delta 15–40%, AND the value stabilises (delta ≤ 15% for last 3 cycles).
- **FAIL**: any delta > 40%, OR monotonic positive delta for 5+ consecutive cycles.

If COOLDOWN_END heap is within 20% of BASELINE heap → always PLATEAU regardless of peak.

## Device matrix

- **Primary**: any device with MemoryTier.STANDARD or STANDARD/HIGH (≥ 4 GB RAM).
- **Constrained**: emulator with heap limit ≤ 256 MB OR physical low-RAM device (MemoryTier.LOW).
- **Legacy (optional, not blocking)**: API 23 device for legacy flavor.

## Data scope

First wave: local media only. Remote (SMB/SFTP/Cloud) is a follow-up expansion.

## Verification predicate

- [ ] Scenario matrix table is signed off in this file.
- [ ] Pass/fail rule is recorded in this file (see above).
- [ ] Checkpoint structure is recorded in this file (see above).
