# Tactical Plan: S0189 — browse-create-text-notes

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Feature:** Browse — create new text note + open in editor + Google Keep export
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 50
**Status:** In Progress (Phases 01..08 implemented; Phase 09 added 2026-05-17 to satisfy S0191 §6.1 п.5)
**Phases:** 8 / 9 done
**Last updated:** 2026-05-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | domain-create-file | — | Not started | 0/6 | [PHASE_01__domain-create-file.md](PHASE_01__domain-create-file.md) |
| 02 | browse-entry-points | 01 | Not started | 0/7 | [PHASE_02__browse-entry-points.md](PHASE_02__browse-entry-points.md) |
| 03 | network-staging | 01 | Not started | 0/4 | [PHASE_03__network-staging.md](PHASE_03__network-staging.md) |
| 04 | editor-auto-open | 02 | Not started | 0/3 | [PHASE_04__editor-auto-open.md](PHASE_04__editor-auto-open.md) |
| 05 | editor-action-icons | 04 | Not started | 0/6 | [PHASE_05__editor-action-icons.md](PHASE_05__editor-action-icons.md) |
| 06 | save-with-name | 03, 05 | Not started | 0/4 | [PHASE_06__save-with-name.md](PHASE_06__save-with-name.md) |
| 07 | font-auto-fit | 05 | Not started | 0/3 | [PHASE_07__font-auto-fit.md](PHASE_07__font-auto-fit.md) |
| 08 | docs-catalog-cleanup | 01..07 | Done | 5/5 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |
| 09 | shared-module-extraction | 01..08 | Not started | 0/10 | [PHASE_09__shared-module-extraction.md](PHASE_09__shared-module-extraction.md) |

Status legend: `Not started` · `In Progress` · `Done` · `Blocked` · `Skipped`

---

## Pre-Implementation Blockers

All §6.1 research items resolved in strategic spec (2026-05-16). All §6.2 customer questions answered in strategic spec. No open blockers.

- [x] §6.1 / §6.2 — all open items closed in strategic spec §6.3 "Locked decisions"

---

## Completion Gate

- [ ] All 8 phases show Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — bullet added per strategic §8.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`scan.ps1` + `render.ps1`).
- [ ] `dev/FUNCTIONALITY.log` has `ADD` entry for S0189.
- [ ] `/spec-check S0189` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `Done`, bump counter.
4. If blocked: flip to `Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Status Block...`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0189`.

---

## Blockers Log

(none yet)

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-17 — Phase 09 added (shared-module extraction for S0191). Triggered by S0191 §6.1 п.5 decision to make S0189 the owner of all 7 shared modules in S0191 §5.4. S0189 was already in `BlockNeedUserTest` with PHASE_01..08 implemented; this phase is a pure refactor with zero behaviour change for text notes.
