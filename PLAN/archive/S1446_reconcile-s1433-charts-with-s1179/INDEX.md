# Tactical Plan: S1446 - reconcile-s1433-charts-with-s1179

**Strategic spec:** [`../S1446_reconcile-s1433-charts-with-s1179.md`](../S1446_reconcile-s1433-charts-with-s1179.md)
**Research inputs:** [`research/01__landed-s1179-vs-planned-s1433.md`](research/01__landed-s1179-vs-planned-s1433.md)
**Feature:** One shared time-series chart for both the gadget tile and the network-monitor section
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented
**Phases:** 2 / 2 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shared-chart-extension | - | ✅ Done | 3/3 | [PHASE_01__shared-chart-extension.md](PHASE_01__shared-chart-extension.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 carries no open item: the single research question is resolved by `research/01__landed-s1179-vs-planned-s1433.md`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched; strategic §8 records no user-facing capability.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2580 records.
- [ ] `/spec-check S1446` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Cross-cutting invariants

- Both new capabilities are off unless the host asks for them, so the landed S1179 gadget tiles render byte-identically. S1179 is in `BlockNeedUserTest` awaiting a real-device pass; a default-on change would silently alter what the owner is about to verify.
- The chart view returns numbers, never formatted text. The wording of any summary belongs to the consumer, which owns the strings and the locale.
- No step edits `PLAN/**` outside the final cleanup phase.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1446`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-08 - Both phases executed by `/spec-dev`. No on-device gate - strategic §11 is fully checkable statically, so the ticket goes to `Implemented` with no debug probes.
