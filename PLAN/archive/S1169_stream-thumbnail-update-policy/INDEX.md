# Tactical Plan: S1169 - stream-thumbnail-update-policy

**Strategic spec:** [`../S1169_stream-thumbnail-update-policy.md`](../S1169_stream-thumbnail-update-policy.md)
**Research inputs:** none (root-caused inline; anchors in strategic §1-§2)
**Feature:** Unified update policy for stream (video-broadcast) thumbnails
**Tier:** 3
**Priority:** 70
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-07-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | outcome-write-loop-cut | - | ✅ Done | 2/2 | [PHASE_01__outcome-write-loop-cut.md](PHASE_01__outcome-write-loop-cut.md) |
| 02 | capture-backoff | 01 | ✅ Done | 2/2 | [PHASE_02__capture-backoff.md](PHASE_02__capture-backoff.md) |
| 03 | partial-rebind-payload | 01 | ✅ Done | 2/2 | [PHASE_03__partial-rebind-payload.md](PHASE_03__partial-rebind-payload.md) |
| 04 | prewarm-and-panel-stability | 03 | ✅ Done | 2/2 | [PHASE_04__prewarm-and-panel-stability.md](PHASE_04__prewarm-and-panel-stability.md) |
| 05 | frame-cache-capacity | - | ✅ Done | 1/1 | [PHASE_05__frame-cache-capacity.md](PHASE_05__frame-cache-capacity.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Root cause and mechanism resolved in the strategic spec (§1-§4); no open research item.

---

## Deferred (follow-up ticket, out of this plan)

- **Two-pipeline consolidation** (main + pinned `StreamGridModeManager`/`StreamFrameSnapshotManager` into one section-parameterized instance): a large refactor that removes duplicated policy surface but does NOT itself fix flicker/redundancy (those are fixed by phases 01-05). Park as a separate ticket after this plan verifies, to keep the fix low-risk. Anchor: `StreamsActivity.kt:179-251`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 not a FEATURES-mandating change; capability recorded via `ALL_FEATURES` in Phase 06).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1169` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`; update `Phases: X/N done`.
2. During a phase: flip a step to `[~]` when started, `[x]` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add a Blockers Log bullet; set journal status if the whole spec is blocked.
5. All done: flip `Status:` to `Done`, run `/spec-check S1169`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-24 - Initial tactical plan authored by `/spec-tech`.
