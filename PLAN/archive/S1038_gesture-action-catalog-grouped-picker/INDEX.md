# Tactical Plan: S1038 - gesture-action-catalog-grouped-picker

**Strategic spec:** [`../S1038_gesture-action-catalog-grouped-picker.md`](../S1038_gesture-action-catalog-grouped-picker.md)
**Research inputs:** [`research/01__gesture-action-architecture.md`](research/01__gesture-action-architecture.md)
**Feature:** Grouped action picker with per-option explanations + ~20 new edge-gesture actions (device/media/launch/accessibility) + per-slot URL payload.
**Tier:** 4 - Strategic (ad-hoc, epic)
**Priority:** 50
**Status:** BlockNeedUserTest (all 7 phases done; awaiting on-device verification)
**Phases:** 7 / 7 done
**Last updated:** 2026-07-14

> Scope: tactical, English, developer handoff. Every step has a verification predicate. Rationale in the strategic spec.
> Epic: phases are independently buildable. Phase 01 (grouped picker) + Phase 02 (per-slot payload) are the reusable foundation and ship before any new action batch. Action batches (03 device / 04 launch / 05 accessibility) are additive. Each new-action batch is device-verifiable only -> the ticket lands `BlockNeedUserTest` after the code phases build green.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | grouped-picker-infra | - | ✅ Done | 4 | [PHASE_01__grouped-picker-infra.md](PHASE_01__grouped-picker-infra.md) |
| 02 | per-slot-payload | - | ✅ Done | 3 | [PHASE_02__per-slot-payload.md](PHASE_02__per-slot-payload.md) |
| 03 | device-control-actions | 01 | ✅ Done | 4 | [PHASE_03__device-control-actions.md](PHASE_03__device-control-actions.md) |
| 04 | launch-intent-actions | 01, 02 | ✅ Done | 4 | [PHASE_04__launch-intent-actions.md](PHASE_04__launch-intent-actions.md) |
| 05 | accessibility-actions-nolegal | 01 | ✅ Done | 3 | [PHASE_05__accessibility-actions-nolegal.md](PHASE_05__accessibility-actions-nolegal.md) |
| 06 | permissions-degradation | 03, 04 | ✅ Done | 3 | [PHASE_06__permissions-degradation.md](PHASE_06__permissions-degradation.md) |
| 07 | docs-strings-catalog | all | ✅ Done | 3 | [PHASE_07__docs-strings-catalog.md](PHASE_07__docs-strings-catalog.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 items are owner-acknowledged and resolved from code/platform in research/01 (recommendations adopted). ADR forks resolved: grouped picker = dedicated sealed Header/Entry adapter (PermissionRowAdapter template); accessibility gating = noLegal source set + runtime holder degrade; dispatcher = per-class handlers up front.

---

## Coordination with S1036

Per-slot payload (Phase 02) is the shared mechanism from ADR-3. S1038 is not blocked and is being driven first, so Phase 02 introduces the generic per-slot string payload; S1036 (app-package selection) consumes it later. Keep the payload semantics value-agnostic (URL here, package in S1036).

---

## Completion Gate

- [ ] All phases ✅ Done.
- [ ] Grouped picker renders sections + per-item explanation; existing 18 actions still selectable (Phase 01 regression).
- [ ] All owner-selected new actions present, dispatched by class; accessibility subset only in noLegal.
- [ ] `secure`/permission actions degrade explicitly (WRITE_SETTINGS request; missing target app no-op with log).
- [ ] Per-slot URL payload persists across 12 slots; opens on trigger.
- [ ] standard debug + noLegal debug builds PASS.
- [ ] New strings EN/RU/UK; `check_strings_localized` green.
- [ ] Device verification (each action class, flavor gating, brightness permission) - deferred to `BlockNeedUserTest`.

---

## Change Log

- 2026-07-14 - Tactical plan authored by `/spec-tech` (F2). 7 phases; foundation (01-02) first, action batches additive.
