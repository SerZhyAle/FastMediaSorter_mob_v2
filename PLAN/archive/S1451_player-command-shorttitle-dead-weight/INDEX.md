# Tactical Plan: S1451 - player-command-shorttitle-dead-weight

**Strategic spec:** [`../S1451_player-command-shorttitle-dead-weight.md`](../S1451_player-command-shorttitle-dead-weight.md)
**Research inputs:** none - strategic §6 carries no open research item.
**Feature:** Player command panel
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 1 / 1 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shorttitle-removal | - | ✅ Done | 6/6 | [PHASE_01__shorttitle-removal.md](PHASE_01__shorttitle-removal.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 research items are Resolved as of 2026-08-08.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1451` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1451`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-08 - Initial tactical plan authored by `/spec-all` Stage F2.
