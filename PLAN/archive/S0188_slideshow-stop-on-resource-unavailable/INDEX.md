# Tactical Plan: S0188 — slideshow-stop-on-resource-unavailable

**Strategic spec:** [../S0188_slideshow-stop-on-resource-unavailable.md](../S0188_slideshow-stop-on-resource-unavailable.md)
**Feature:** Stop slideshow when the backing resource becomes unavailable
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-and-stop-path | — | ✅ Done | 2/2 | [PHASE_01__foundation-and-stop-path.md](PHASE_01__foundation-and-stop-path.md) |
| 02 | failure-signal-wiring | 01 | ✅ Done | 3/3 | [PHASE_02__failure-signal-wiring.md](PHASE_02__failure-signal-wiring.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** image transport-failure classification resolved. See strategic §6.1.
- [x] **Research:** audio quick-end heuristic confirmed. See strategic §6.2.
- [x] **Research:** video failure hook resolved to playback errors/timeouts. See strategic §6.3.
- [x] **Research:** one resource-name message template is sufficient. See strategic §6.4.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0188` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0188`.

---

## Blockers Log

- 2026-05-14 — No open blockers after research consolidation.

---

## Change Log

- 2026-05-14 — Initial tactical plan authored after strategic research closeout.