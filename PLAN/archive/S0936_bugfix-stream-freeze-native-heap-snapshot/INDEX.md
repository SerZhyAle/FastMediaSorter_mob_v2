# Tactical Plan: S0936 - stream-stall-watchdog

**Strategic spec:** [`../S0936_bugfix-stream-freeze-native-heap-snapshot.md`](../S0936_bugfix-stream-freeze-native-heap-snapshot.md)
**Research inputs:** none (root cause established inline in strategic §2)
**Feature:** Stream stall watchdog (detect + recover a silent no-error freeze)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-07-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | stall-detection-watchdog | - | ✅ Done | 4/4 | [PHASE_01__stall-detection-watchdog.md](PHASE_01__stall-detection-watchdog.md) |
| 02 | watchdog-recovery-trigger | 01 | ✅ Done | 3/3 | [PHASE_02__watchdog-recovery-trigger.md](PHASE_02__watchdog-recovery-trigger.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Device-repro confirmation** - CONFIRMED 2026-07-11 on emulator-5556 (throttled connection, per strategic status-note's sanctioned "spec-sweep on a throttled connection" path): live HLS (`1+1 International`) + cellular throttled to GSM + wifi kill -> `Stream state=BUFFERING` / `isPlaying=false` at 19:10:30, `Stream stall detected (buffering timeout)` at 19:10:45 (+15s, ratified threshold), **zero** `PlaybackException`/recovery logs - exactly the silent-stall shape gating Phase 02.
- [x] **Owner threshold decision (strategic §3.3)** - ratified 2026-07-04: Phase 01 defaults (3 polls x 3s, 15s buffering timeout, budget 3), same `RECONNECTING` label.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = internal resilience, not a FEATURES change).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `StreamStallWatchdog` symbol).
- [ ] `/spec-check S0936` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~]` when started, `[x]` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0936`.

---

## Blockers Log

- 2026-07-04 - Phase 02 blocked at authoring time: auto-recovery must not ship before the silent-stall shape is confirmed on-device (S0937 logs) and the owner ratifies watchdog thresholds. Next: run Phase 01, gather a real freeze harvest, then unblock Phase 02.
- 2026-07-11 - UNBLOCKED: silent stall reproduced on emulator-5556 via throttled connection (see Pre-Implementation Blockers); thresholds were ratified 2026-07-04. Phase 02 implemented and recovery verified on-device same day (stall -> 2 watchdog re-anchors while network dead -> network back -> READY without manual exit; budget exhaustion -> existing error dialog).

---

## Change Log

- 2026-07-04 - Initial tactical plan authored by `/spec-tech`. Phase 02 authored as `⛔ Blocked` pending device-repro + owner thresholds.
