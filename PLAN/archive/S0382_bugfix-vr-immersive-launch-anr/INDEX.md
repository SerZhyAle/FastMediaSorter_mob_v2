# Tactical Plan: S0382 - bugfix-vr-immersive-launch-anr

**Strategic spec:** [`../S0382_bugfix-vr-immersive-launch-anr.md`](../S0382_bugfix-vr-immersive-launch-anr.md)
**Feature:** VR immersive launch cold-start ANR - residual root-causes (transport + main-thread decode)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 85
**Status:** Done (awaiting on-device verification)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | payload-holder-foundation | - | ✅ Done | 2/2 | [PHASE_01__payload-holder-foundation.md](PHASE_01__payload-holder-foundation.md) |
| 02 | token-transport-launch | 01 | ✅ Done | 4/4 | [PHASE_02__token-transport-launch.md](PHASE_02__token-transport-launch.md) |
| 03 | token-transport-return | 01, 02 | ✅ Done | 4/4 | [PHASE_03__token-transport-return.md](PHASE_03__token-transport-return.md) |
| 04 | offthread-initial-decode | - | ✅ Done | 4/4 | [PHASE_04__offthread-initial-decode.md](PHASE_04__offthread-initial-decode.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Transport phases (01-03) are fully unblocked - the transport decision is Resolved (strategic §6.1 / ADR-2). Phase 04 (decode) is independent of transport but gated by the two blockers below.

---

## Pre-Implementation Blockers

- [x] **UI-clarify:** loading-state presentation during off-main-thread decode - RESOLVED 2026-06-08: 2D indicator overlay on the immersive-host window (owner choice via `/ui-clarify`), dismissed on session-ready. Strategic §6.2 Resolved.
- [x] **Research:** texture-buffer readiness ordering - RESOLVED 2026-06-08: gate `maybeStartRenderThread` on an `initialDecodeComplete` flag plus an explicit re-trigger from the decode-completion callback; the render thread receives the buffer by value at construction, so a gated start cannot reach first render with an empty buffer. Strategic §6.3 Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done (Phase 04 may stay ⏭️ Skipped only on explicit owner deferral).
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 is "Без изменений" (bugfix).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0382` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0382`.

---

## Blockers Log

- 2026-06-08 - Phase 04 blocked: loading-state presentation needs `/ui-clarify` (§6.2); buffer-readiness ordering needs research (§6.3). Next: resolve both before starting Phase 04. Transport phases 01-03 may proceed in parallel.
- 2026-06-08 - Both Phase 04 blockers resolved. §6.2 -> 2D indicator overlay (owner via `/ui-clarify`). §6.3 -> render-thread start gated on `initialDecodeComplete`. Phase 04 unblocked; `/spec-dev` resumed.

---

## Change Log

- 2026-06-08 - Initial tactical plan authored by `/spec-tech`. Transport direction pre-resolved to key + process-scoped holder (strategic ADR-2).
- 2026-06-08 - Phases 01-03 implemented by `/spec-dev` (transport fully tokenized, noLegal debug green). Spec stays `In Progress`: Phase 04 (decode) blocked on `/ui-clarify` + research; Phase 05 (cleanup) deferred until Phase 04. Dead `IntentSerializationCompat` helper removed.
