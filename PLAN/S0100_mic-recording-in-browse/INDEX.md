# Tactical Plan: S0100 — mic-recording-in-browse

**Strategic spec:** [`../S0100_mic-recording-in-browse.md`](../S0100_mic-recording-in-browse.md)
**Feature:** Microphone recording from Browse command bar
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 6 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | — | ⬜ Not started | 0/2 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | recording-engine | 01 | ⬜ Not started | 0/3 | [PHASE_02__recording-engine.md](PHASE_02__recording-engine.md) |
| 03 | manifest-strings | 01 | ⬜ Not started | 0/4 | [PHASE_03__manifest-strings.md](PHASE_03__manifest-strings.md) |
| 04 | settings-ui | 01, 03 | ⬜ Not started | 0/3 | [PHASE_04__settings-ui.md](PHASE_04__settings-ui.md) |
| 05 | browse-integration | 02, 03, 04 | ⬜ Not started | 0/5 | [PHASE_05__browse-integration.md](PHASE_05__browse-integration.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open blockers — all §6 research items resolved before tactical was authored.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 06).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 06).
- [ ] `/spec-check S0100` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Status Block...`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0100`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
