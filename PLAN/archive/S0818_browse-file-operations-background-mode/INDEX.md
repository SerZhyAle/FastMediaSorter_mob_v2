# Tactical Plan: S0818 - browse-file-operations-background-mode

**Strategic spec:** [`../S0818_browse-file-operations-background-mode.md`](../S0818_browse-file-operations-background-mode.md)
**Feature:** Background mode for browse copy and move operations
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (code complete; awaiting on-device verification)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | browse-transfer-contract | - | ✅ Done | 3/3 | [PHASE_01__browse-transfer-contract.md](PHASE_01__browse-transfer-contract.md) |
| 02 | browse-progress-reattach-ui | 01 | ✅ Done | 3/3 | [PHASE_02__browse-progress-reattach-ui.md](PHASE_02__browse-progress-reattach-ui.md) |
| 03 | result-return-and-copy | 01, 02 | ✅ Done | 3/3 | [PHASE_03__result-return-and-copy.md](PHASE_03__result-return-and-copy.md) |
| 04 | docs-catalog-cleanup | 01-03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are already resolved.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check <S0818>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <S0818>`.

---

## Blockers Log

- On-device verification still pending. No emulator/device was attached during implementation, so the ticket is parked in `BlockNeedUserTest`.

---

## Change Log

- 2026-06-29 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-29 - Phases 01-04 implemented, docs/catalog synced, `a.ps1 fc` PASS, ticket advanced to `BlockNeedUserTest`.
