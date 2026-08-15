# Tactical Plan: S0452 - share-commands-infrastructure

**Strategic spec:** [`../S0452_share-commands-infrastructure.md`](../S0452_share-commands-infrastructure.md)
**Research inputs:** [`research/01__architecture.md`](research/01__architecture.md)
**Feature:** Share-commands infrastructure (group "Команды отправить файл в.." + per-target registry + gating)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 4 / 5 done, 1 skipped (Phase 04 gating delegated to target tickets)
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | share-target-registry | - | ✅ Done | 4/4 | [PHASE_01__share-target-registry.md](PHASE_01__share-target-registry.md) |
| 02 | settings-flag-storage | 01 | ✅ Done | 3/3 | [PHASE_02__settings-flag-storage.md](PHASE_02__settings-flag-storage.md) |
| 03 | settings-group-ui | 01, 02 | ✅ Done | 4/4 | [PHASE_03__settings-group-ui.md](PHASE_03__settings-group-ui.md) |
| 04 | gating-existing-surfaces | 01, 02 | ⏭️ Skipped | 0/3 | [PHASE_04__gating-existing-surfaces.md](PHASE_04__gating-existing-surfaces.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (see research/01__architecture.md). Group expand-default is a tactical detail (default collapsed, mirroring existing sections).

---

## Completion Gate

- [x] All phases ✅ Done (Phase 04 ⏭️ Skipped - gating delegated to target tickets).
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states the foundation has no direct user-visible effect (target tickets own FEATURES entries).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes in core/share).
- [ ] `/spec-check S0452` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0452`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-16 - Phases 01-03 implemented (build-green). Phase 04 (consumer-gating) Skipped per owner decision: gating delegated to target tickets S0443-S0446 (foundation provides the `IsShareTargetEnabledUseCase` + resolver seam). Settings group hidden while registry empty.
