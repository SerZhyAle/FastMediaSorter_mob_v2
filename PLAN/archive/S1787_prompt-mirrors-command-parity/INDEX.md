# Tactical Plan: S1787 - prompt-mirrors-command-parity

**Strategic spec:** [`../S1787_prompt-mirrors-command-parity.md`](../S1787_prompt-mirrors-command-parity.md)
**Research inputs:** none
**Feature:** Retire Stale Copilot Prompt Mirrors (.github/prompts/*.prompt.md)
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-08-18

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | retire-prompt-mirrors | - | ✅ Done | 2/2 | [PHASE_01__retire-prompt-mirrors.md](PHASE_01__retire-prompt-mirrors.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 1/1 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

none

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped ("Без изменений").
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `/spec-check S1787` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1787`.

---

## Blockers Log

none

---

## Change Log

- 2026-08-18 - Initial tactical plan authored by `/spec-tech`.
