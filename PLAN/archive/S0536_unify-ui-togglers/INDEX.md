# Tactical Plan: S0536 - unify-ui-togglers

**Strategic spec:** [`../S0536_unify-ui-togglers.md`](../S0536_unify-ui-togglers.md)
**Research inputs:** [`research/01__toggler-inventory.md`](research/01__toggler-inventory.md)
**Feature:** Unify on/off togglers and lock the recommended toggler form
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Verified
**Phases:** 5 / 5 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | component-switch-class | - | ✅ Done | 2/2 | [PHASE_01__component-switch-class.md](PHASE_01__component-switch-class.md) |
| 02 | theme-switch-style | - | ✅ Done | 1/1 | [PHASE_02__theme-switch-style.md](PHASE_02__theme-switch-style.md) |
| 03 | dialog-switch-migration | 01, 02 | ✅ Done | 3/3 | [PHASE_03__dialog-switch-migration.md](PHASE_03__dialog-switch-migration.md) |
| 04 | checkbox-to-switch-rows | 01 | ✅ Done | 4/4 | [PHASE_04__checkbox-to-switch-rows.md](PHASE_04__checkbox-to-switch-rows.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (quiz 2026-06-19). Phase 01 may start.

---

## Scope notes (from research/01)

- On/off togglers only. Selection / multiselect / consent checkboxes are out of scope (ticket S0537).
- `dialog_scheduled_operation.xml` is partially migrated: its two on/off switches are in scope; its file-type mask checkboxes stay for S0537.
- `debug/res/layout/activity_debug.xml` `switchLeakCanary` is excluded - developer-only debug source set, not a shipped user-facing surface.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - SKIP (strategic §8 is "Без изменений").
- [x] `dev/CHANGELOG.md` has an entry for every modified file (batched per phase) - 6 entries.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (component field-type change).
- [x] `/spec-check S0536` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, set journal status to a `Block*` value.
5. All done: flip `Status:` to `Done`, run `/spec-check S0536`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-tech`.
