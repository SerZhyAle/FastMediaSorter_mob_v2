# Tactical Plan: S1142 - audio-stream-metadata-display

**Strategic spec:** [`../S1142_audio-stream-metadata-display.md`](../S1142_audio-stream-metadata-display.md)
**Research inputs:** [`research/01__audio-stream-nowplaying-surfaces.md`](research/01__audio-stream-nowplaying-surfaces.md), [`research/02__mediasession-live-metadata-ownership.md`](research/02__mediasession-live-metadata-ownership.md)
**Feature:** Audio-stream now-playing metadata (artist/title) in notification, lock screen, inline control, grid card
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-07-23

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | nowplaying-parse-format | - | ✅ Done | 3/3 | [PHASE_01__nowplaying-parse-format.md](PHASE_01__nowplaying-parse-format.md) |
| 02 | service-live-metadata | 01 | ✅ Done | 2/2 | [PHASE_02__service-live-metadata.md](PHASE_02__service-live-metadata.md) |
| 03 | inline-grid-surfaces | 01, 02 | ✅ Done | 3/3 | [PHASE_03__inline-grid-surfaces.md](PHASE_03__inline-grid-surfaces.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 Q1/Q2 resolved by quiz 2026-07-23; research 01 + 02 Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic §8 mandates showcase only via `/skill-release`, not per-spec.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `NowPlayingMetadata` public class).
- [ ] `/spec-check S1142` returns `Verified` (device-verify of notification headline gated via `BlockNeedUserTest`).
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1142`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-23 - Initial tactical plan authored by `/spec-tech` (service-side ownership per research 02).
