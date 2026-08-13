# Tactical Plan: S0529 - network-audio-always-continue

**Strategic spec:** [`../S0529_network-audio-always-continue.md`](../S0529_network-audio-always-continue.md)
**Research inputs:** [`research/01__service-network-streaming.md`](research/01__service-network-streaming.md) · [`research/02__startup-timeout-strategy.md`](research/02__startup-timeout-strategy.md) · [`research/03__next-track-prefetch-gap.md`](research/03__next-track-prefetch-gap.md)
**Feature:** «Всегда продолжать» для сетевого и облачного аудио
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 70
**Status:** Implemented - BlockNeedUserTest (device test pending)
**Phases:** 4 / 5 done (Phase 04 folded into 01+02)
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | startup-timeout-split | - | ✅ Done | 3/3 | [PHASE_01__startup-timeout-split.md](PHASE_01__startup-timeout-split.md) |
| 02 | service-network-streaming | 01 | ✅ Done | 4/4 | [PHASE_02__service-network-streaming.md](PHASE_02__service-network-streaming.md) |
| 03 | continuability-and-exit | 02 | ✅ Done | 4/4 | [PHASE_03__continuability-and-exit.md](PHASE_03__continuability-and-exit.md) |
| 04 | next-track-service-prefetch | 01, 02 | ⏭️ Skipped | - | [PHASE_04__next-track-service-prefetch.md](PHASE_04__next-track-service-prefetch.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

> **Phase 04 folded into 01+02.** The logged inter-track pause was caused by the next-track pre-cache aborting at the old 5 s SFTP timeout (fixed in Phase 01: prefetch now uses the adaptive transfer budget) and by the next track requiring a full download (removed in Phase 02: the next track streams immediately via the service). True gapless service-queue pre-buffering remains a possible future enhancement, out of scope for the reported issue.

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved - no open blockers.

- [x] Research 01 (service-side network streaming feasibility) - Resolved: protocol DataSource factories exist; service ExoPlayer needs a scheme-aware MediaSource.Factory that self-resolves credentials from URI/extras.
- [x] Research 02 (startup timeout strategy) - Resolved: connect/transfer split, adaptive transfer, no user setting.
- [x] Research 03 (next-track prefetch gap) - Resolved: queue next track to service streaming through the existing network throttle.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = «Без изменений»).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class introduced in Phase 02).
- [ ] `/spec-check S0529` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0529`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
