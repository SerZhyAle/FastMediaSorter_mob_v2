# Tactical Plan: S0468 - screenshot-clipboard

**Strategic spec:** [`../S0468_screenshot-clipboard.md`](../S0468_screenshot-clipboard.md)
**Research inputs:** [`research/01__clipboard-image-uri.md`](research/01__clipboard-image-uri.md), [`research/02__clipboard-write-from-service.md`](research/02__clipboard-write-from-service.md), [`research/03__clipboard-source-decoupling.md`](research/03__clipboard-source-decoupling.md)
**Feature:** Save gesture screenshots to clipboard
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done - awaiting on-device test (journal: BlockNeedUserTest)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-flag | - | ✅ Done | 3/3 | [PHASE_01__settings-flag.md](PHASE_01__settings-flag.md) |
| 02 | clipboard-writer | - | ✅ Done | 1/1 | [PHASE_02__clipboard-writer.md](PHASE_02__clipboard-writer.md) |
| 03 | capture-wiring | 01, 02 | ✅ Done | 2/2 | [PHASE_03__capture-wiring.md](PHASE_03__capture-wiring.md) |
| 04 | settings-ui | 01 | ✅ Done | 3/3 | [PHASE_04__settings-ui.md](PHASE_04__settings-ui.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 research items are Resolved (see Research inputs).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class added).
- [ ] `/spec-check S0468` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0468`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
