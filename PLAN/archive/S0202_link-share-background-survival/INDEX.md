# Tactical Plan: S0202 — link-share-background-survival

**Strategic spec:** [`../S0202_link-share-background-survival.md`](../S0202_link-share-background-survival.md)
**Feature:** Link share download must survive backgrounding
**Tier:** 2 — significant
**Priority:** 80
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | worker-single-mode-foreground | — | ✅ Done | 7/7 | [PHASE_01__worker-single-mode-foreground.md](PHASE_01__worker-single-mode-foreground.md) |
| 02 | activity-enqueue-observe-watchdog | 01 | ✅ Done | 6/6 | [PHASE_02__activity-enqueue-observe-watchdog.md](PHASE_02__activity-enqueue-observe-watchdog.md) |
| 03 | cancel-routing-dedup | 02 | ✅ Done | 4/4 | [PHASE_03__cancel-routing-dedup.md](PHASE_03__cancel-routing-dedup.md) |
| 04 | result-return-on-resume | 02 | ✅ Done | 4/4 | [PHASE_04__result-return-on-resume.md](PHASE_04__result-return-on-resume.md) |
| 05 | docs-catalog-cleanup | 01,02,03,04 | ✅ Done | 5/5 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All §6 research items in the strategic spec are resolved with explicit recommendations:

- §6.1 watchdog timing — Variant B with 4-second watchdog (resolved).
- §6.2 progress sub-stages — deferred as stretch goal; not gating Phase 01.
- §6.3 cancel atomicity — addressed in Phase 03.
- §6.4 dedup policy — `enqueueUniqueWork` with `KEEP` keyed on canonicalized URL (resolved).
- §6.5 flavor scope — main sourceSet, not flavor-gated (resolved).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skip (strategic §8 explicitly says "Без изменений" for the public catalogue; this is a FIX, not a new capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/FUNCTIONALITY.log` has a `FIX S0202 share download survives backgrounding` line.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0202` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0202`.

---

## Blockers Log

- 2026-05-14 — none.

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech`.
