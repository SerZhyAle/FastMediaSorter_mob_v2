# Tactical Plan: S0396 - welcome-availability-contract

**Strategic spec:** [`../S0396_welcome-availability-contract.md`](../S0396_welcome-availability-contract.md)
**Research inputs:** [`../S0395_welcome-screens-redesign-research/research/06__page4-functionality-toggles.md`](../S0395_welcome-screens-redesign-research/research/06__page4-functionality-toggles.md), [`../S0395_welcome-screens-redesign-research/research/09__flavor-matrix.md`](../S0395_welcome-screens-redesign-research/research/09__flavor-matrix.md)
**Feature:** Runtime capability-availability contract for onboarding (OCR / translation / VR / extensions)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Design baseline (grounded in code)

- Capability axis already exists as source sets: `ocrEnabled`/`ocrDisabled`, `translationEnabled` (absent in lite/photos), `vrOnly`/`vrStub` (build.gradle.kts lines 522-570). Flavor→set mapping mirrors `ENABLE_TRANSLATION` exactly (true: standard/noLegal/legacy/vr; false: lite/photos).
- Contract reuses the proven multibinding pattern of `SettingsSearchAvailability` (`@SupportedMediaSection Set<String>` fed by per-source-set `@IntoSet`, empty-safe via `@Multibinds` in a main module).
- OCR has two layers: compiled-in (set membership) AND device-runtime (`DeviceCapabilities.ocrSupport(context)` - API≥26 + RAM≥3GB). The contract combines both; VR runtime XR detection stays in `XrDetectionFacade` and is out of scope.
- Migration is bounded to settings VISIBILITY reads (`OtherMediaSettingsFragment.applyFlavorRestrictions`, `PlaybackSettingsFragment`); functional gating in players/widgets is explicitly out of scope (strategic §11.2 as amended).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | capability-contract | - | ✅ Done | 2/2 | [PHASE_01__capability-contract.md](PHASE_01__capability-contract.md) |
| 02 | flavor-contributions | 01 | ✅ Done | 3/3 | [PHASE_02__flavor-contributions.md](PHASE_02__flavor-contributions.md) |
| 03 | settings-migration | 02 | ✅ Done | 2/2 | [PHASE_03__settings-migration.md](PHASE_03__settings-migration.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 items resolved by S0395. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API added).
- [ ] `/spec-check S0396` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0396`.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-06-10 - Initial tactical plan authored by `/spec-tech`.
