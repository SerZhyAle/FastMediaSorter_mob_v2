# Tactical Plan: S0407 - audio-player-extra-visualizations

**Strategic spec:** [`../S0407_audio-player-extra-visualizations.md`](../S0407_audio-player-extra-visualizations.md)
**Research inputs:** [`research/04__asset-source.md`](research/04__asset-source.md), [`research/05__generation-prompts.md`](research/05__generation-prompts.md)
**Feature:** Audio-player extra background visualizations + resilient delivery/playback
**Tier:** 2 - Easy (ad-hoc) (code workstream raises effort; asset workstream external)
**Priority:** 50
**Status:** BlockNeedUserTest (code+docs done; mirror upload + on-device test pending)
**Phases:** 4 / 4 done (6 new clips this batch -> 11 total, below 8-10 target, extensible)
**Last updated:** 2026-06-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | audio-empty-state-resilience | - | ✅ Done | 3/3 | [PHASE_01__audio-empty-state-resilience.md](PHASE_01__audio-empty-state-resilience.md) |
| 02 | settings-download-gate | - | ✅ Done | 2/2 | [PHASE_02__settings-download-gate.md](PHASE_02__settings-download-gate.md) |
| 03 | register-visualization-assets | - (assets produced) | ✅ Done | 2/2 | [PHASE_03__register-visualization-assets.md](PHASE_03__register-visualization-assets.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phases 01 and 02 are the code workstream and have NO blockers - start immediately. The blocker below gates Phase 03 only (asset registration); it does NOT gate Phases 01, 02.

- [x] **Research:** Asset production - 6 clips produced + boomerang-processed + SHA-256/size captured (2026-06-14). NOTE: mirror publish still pending (owner re-uploads all 11 under BlockNeedUserTest); only the hashes were needed to unblock code registration.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0407` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0407`.

---

## Blockers Log

- 2026-06-13 - Phase 03 gated: new clip assets not yet produced/published (external/manual). Next: owner generates clips, publishes to mirror, captures SHA-256 + size; then Phase 03 registers them.

---

## Change Log

- 2026-06-13 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-14 - Phases 03-04 done: 6 new clips produced; ALL 11 re-encoded to H.264 Constrained Baseline / 1024x576 / 24fps / ~800k (low-power car & cheap-device decoders) - old 5 re-encoded too (§6.9 reversed); descriptor + bg list + INVENTORY updated with new hashes; FEATURES EN/RU/UK added; `a.ps1 fk` green; debug tags inserted; status -> BlockNeedUserTest. Pending: owner re-uploads all 11 to mirror (`gh release upload --clobber`), then on-device A/B/C test.
- 2026-06-13 - Phases 01-02 implemented + compiled (`a.ps1 fk`, full `a.ps1 d` APK). On-device smoke (emulator-5554): app launches no-crash; audio settings open (Hilt inject OK); Visualization with set installed applies immediately (no spurious prompt); with payload dir removed, selecting Visualization shows the "Audio Visualizations ~6.4 MB" download prompt; Cancel auto-reverts the dropdown to the prior mode. No crashes. Playback fallback (req B) + memory-over-queue (req C) not driven on-device (emulator has no audio files) - left for owner manual test. Phase 03 still external-blocked.
