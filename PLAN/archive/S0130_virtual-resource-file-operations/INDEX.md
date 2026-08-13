# Tactical Plan: S0130 — virtual-resource-file-operations

**Strategic spec:** [`../S0130_virtual-resource-file-operations.md`](../S0130_virtual-resource-file-operations.md)
**Feature:** File operations (delete/rename/edit) in aggregate virtual resources
**Tier:** 2 — Easy
**Priority:** 60
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-09

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | util-and-provisioning | — | ✅ Done | 4/4 | [PHASE_01__util-and-provisioning.md](PHASE_01__util-and-provisioning.md) |
| 02 | startup-migration | 01 | ✅ Done | 3/3 | [PHASE_02__startup-migration.md](PHASE_02__startup-migration.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No blockers — strategic §6 lists no open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` not updated — strategic §8 explicitly says no change (behaviour fix, not new feature).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `VirtualPathUtils` changed).
- [ ] `/spec-check S0130` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0130`.

---

## Blockers Log

*(empty)*

---

## Change Log

- 2026-05-09 — Initial tactical plan authored by `/spec-tech`.
