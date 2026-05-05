# Tactical Plan: S0061 — bugfix-smb-stale-connection-invalidation

**Strategic spec:** [`../S0061_bugfix-smb-stale-connection-invalidation.md`](../S0061_bugfix-smb-stale-connection-invalidation.md)
**Feature:** SMB stale connection invalidation after server-side idle FIN
**Tier:** 3 — Moderate
**Priority:** 85
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | pool-extraction-and-health-probe | — | ✅ Done | 7/7 | [PHASE_01__pool-extraction-and-health-probe.md](PHASE_01__pool-extraction-and-health-probe.md) |
| 02 | pre-acquire-health-and-purge | 01 | ✅ Done | 6/6 | [PHASE_02__pre-acquire-health-and-purge.md](PHASE_02__pre-acquire-health-and-purge.md) |
| 03 | unified-retry-policy | 01, 02 | ✅ Done | 5/5 | [PHASE_03__unified-retry-policy.md](PHASE_03__unified-retry-policy.md) |
| 04 | background-lifecycle-close | 01 | ✅ Done | 6/6 | [PHASE_04__background-lifecycle-close.md](PHASE_04__background-lifecycle-close.md) |
| 05 | diagnostic-channel | 01, 02, 03 | ✅ Done | 5/5 | [PHASE_05__diagnostic-channel.md](PHASE_05__diagnostic-channel.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see strategic spec §6). No external blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 06).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0061` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Status Block...`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0061`.

---

## Blockers Log

- 2026-05-03 — Initial plan: no blockers.

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech` inside `/spec-all`.
