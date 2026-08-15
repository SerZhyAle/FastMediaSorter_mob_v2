# Tactical Plan: S0272 - repo-knowledge-search-expansion

**Strategic spec:** [../S0272_repo_knowledge_search_expansion.md](../S0272_repo_knowledge_search_expansion.md)
**Feature:** Repo Knowledge Search Expansion
**Tier:** 2 - Moderate (agent tooling)
**Priority:** 55
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | search-server | - | ✅ Done | 3/3 | [PHASE_01__search-server.md](PHASE_01__search-server.md) |
| 02 | registration-smoke | 01 | ✅ Done | 2/2 | [PHASE_02__registration-smoke.md](PHASE_02__registration-smoke.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Strategic §6 items resolved inline on 2026-05-20. No open blockers remain for implementation.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0272` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0272`.

---

## Blockers Log

- 2026-05-20 - No blockers at tactical authoring time.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-all` during the `/spec-tech` stage.