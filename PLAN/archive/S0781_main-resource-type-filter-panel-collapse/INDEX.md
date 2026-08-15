# Tactical Plan: S0781 - main-resource-type-filter-panel-collapse

**Strategic spec:** [`../S0781_main-resource-type-filter-panel-collapse.md`](../S0781_main-resource-type-filter-panel-collapse.md)
**Research inputs:** [`research/01__architecture-and-reference-design.md`](research/01__architecture-and-reference-design.md)
**Feature:** Resource-type filter panel collapse
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (implemented; awaiting on-device test)
**Phases:** 4 / 4 done
**Last updated:** 2026-07-01

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | persistence-flag | - | ✅ Done | 3/3 | [PHASE_01__persistence-flag.md](PHASE_01__persistence-flag.md) |
| 02 | strip-resources | - | ✅ Done | 4/4 | [PHASE_02__strip-resources.md](PHASE_02__strip-resources.md) |
| 03 | panel-manager | 01, 02 | ✅ Done | 4/4 | [PHASE_03__panel-manager.md](PHASE_03__panel-manager.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 has no open research items; all UI decisions resolved in §3.3.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - NOT touched here (owned by `/skill-release`); capability recorded in `docs/ALL_FEATURES.jsonl` instead (Phase 04).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager class).
- [ ] `/spec-check S0781` returns `Verified` (after device test of the `BlockNeedUserTest` gate).
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`; update `Phases: X/N done`.
2. During a phase: flip a step to `[~]` when started, `[x]` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a Blockers Log bullet; set journal status if the whole spec is blocked.
5. All phases done: code edits complete → debug tags inserted → status `BlockNeedUserTest` → device test → `/spec-check`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-01 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-01 - All 4 phases implemented via `/spec-dev`; standard-debug build + unit test + detekt/neuroslop/ticket-log gates PASS; status -> BlockNeedUserTest (no device attached - on-device test deferred to `/spec-sweep`).
