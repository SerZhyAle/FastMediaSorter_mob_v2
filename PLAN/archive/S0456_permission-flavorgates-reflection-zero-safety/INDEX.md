# Tactical Plan: S0456 - permission-flavorgates-reflection-zero-safety

**Strategic spec:** [`../S0456_permission-flavorgates-reflection-zero-safety.md`](../S0456_permission-flavorgates-reflection-zero-safety.md)
**Research inputs:** [`research/01__gate-field-coverage-across-flavors.md`](research/01__gate-field-coverage-across-flavors.md)
**Feature:** Harden permission-registry flavor-gate resolution against silent field-name errors
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | harden-flavorgate-resolution | - | ✅ Done | 2/2 | [PHASE_01__harden-flavorgate-resolution.md](PHASE_01__harden-flavorgate-resolution.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 1/1 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The single strategic §6 item is Resolved (see Research inputs). Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` - no update (strategic §8 says "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0456` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0456`.

---

## Blockers Log

- 2026-06-16 - Phase 01 Phase-Done blocked: both code steps done and verified (grep), but the acceptance test cannot execute - `app_v2` test source set fails to compile due to pre-existing stale constructors in 5 unrelated test files (`ApplyEnableAllSettingsUseCaseTest`, `ProvisionDefaultResourcesUseCaseTest`, `ResolveOpenInFmsTargetUseCaseTest`, `ScanLocalFoldersUseCaseTest`, `CommandPanelLayoutPlannerTest`). Parked as S0457. Next: resolve S0457, then run `testStandardDebugUnitTest --tests *PermissionRegistryRepositoryImplTest*` and resume Phase 01 Done.

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
