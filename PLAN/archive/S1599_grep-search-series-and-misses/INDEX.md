# Tactical Plan: S1599 - grep-search-series-and-misses

**Strategic spec:** [`../S1599_grep-search-series-and-misses.md`](../S1599_grep-search-series-and-misses.md)
**Research inputs:** [`research/01__zero-hit-anatomy.md`](research/01__zero-hit-anatomy.md)
**Feature:** Agent tooling - search feedback
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** In Progress - awaiting a fresh-session confirmation (Step 02.3)
**Phases:** 2 / 3 done
**Last updated:** 2026-08-12

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | widen-observer-hook | - | ✅ Done | 4/4 | [PHASE_01__widen-observer-hook.md](PHASE_01__widen-observer-hook.md) |
| 02 | registration-and-reachability | 01 | 🚧 In Progress | 2/3 | [PHASE_02__registration-and-reachability.md](PHASE_02__registration-and-reachability.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Fate of absence checks - strategic §6.1, Resolved 2026-08-12 (suppress entirely).
- [ ] **Research:** Upper bound on benefit - strategic §6.2, Open. **Non-blocking**: it refines the benefit estimate only and gates no phase.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped, strategic §8 says "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for the ticket.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated - not applicable, no Kotlin touched.
- [ ] `/spec-check S1599` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1599`.

---

## Blockers Log

- 2026-08-12 - Step 02.3 blocked: `.claude/settings.json` is read at session start, so the `PostToolUse` entry written in this session is not registered in it and the hook cannot be observed firing from here. Next: re-issue the two Greps recorded in PHASE_02 "Handoff Notes" from a fresh session; that alone closes the ticket.

---

## Change Log

- 2026-08-12 - Initial tactical plan authored by `/spec-all` Stage F2.
