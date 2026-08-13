# Tactical Plan: S0470 - video-frame-clipboard

**Strategic spec:** [`../S0470_video-frame-clipboard.md`](../S0470_video-frame-clipboard.md)
**Feature:** Save extracted video frames to clipboard
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done - awaiting on-device test (journal: BlockNeedUserTest)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-flag | - | ✅ Done | 6/6 | [PHASE_01__settings-flag.md](PHASE_01__settings-flag.md) |
| 02 | frame-wiring | 01 | ✅ Done | 2/2 | [PHASE_02__frame-wiring.md](PHASE_02__frame-wiring.md) |
| 03 | settings-ui | 01 | ✅ Done | 4/4 | [PHASE_03__settings-ui.md](PHASE_03__settings-ui.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The reusable clipboard role `ImageClipboardWriter.copyImageFile(File)` already exists (delivered in S0469), so no clipboard-writer phase is needed. Strategic §6 Q1 (frame source) is Resolved: copy the saved `tempFile` before delete. §6 Q3 (image clip in text field) is an inherited device-test note, not a code blocker.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `docs/ALL_FEATURES.jsonl` has a record for the delivered capability.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0470` returns `Verified` (after device sign-off).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All code done: device-test gate -> `BlockNeedUserTest` -> `/spec-test-device S0470` -> `/spec-check S0470`.

---

## Blockers Log

- (none yet)
