# Tactical Plan: S0276 - configuration-cache-recovery

**Strategic spec:** [`../S0276_configuration_cache_recovery.md`](../S0276_configuration_cache_recovery.md)
**Feature:** Configuration Cache Recovery
**Tier:** 2 - Moderate (build architecture)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | chaquopy-gating | - | ✅ Done | 2/2 | [PHASE_01__chaquopy-gating.md](PHASE_01__chaquopy-gating.md) |
| 02 | cc-rollout | 01 | ✅ Done | 3/3 | [PHASE_02__cc-rollout.md](PHASE_02__cc-rollout.md) |
| 03 | docs-catalog-cleanup | 01,02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Chaquopy incompatibility reproduced on `:app_v2:assembleNoLegalDebug --configuration-cache --dry-run`. See strategic §6.1.
- [x] **Research:** Gradle `notCompatibleWithConfigurationCache(..)` behavior reviewed and rejected as insufficient for the noLegal graph. See strategic §6.2.
- [x] **Research:** Current Chaquopy upstream docs checked for explicit CC support claims. See strategic §6.3.
- [x] **Research:** non-noLegal app tasks and `:wear:assembleDebug` verified to store/reuse configuration cache entries. See strategic §6.4 and §6.5.
- [x] **Research:** baseline dry-run timings captured for app flavors and wear. See strategic §6.6.
- [x] **Research:** IDE/sync risk localized to the `local.properties` fallback and included in the rollout scope. See strategic §6.7.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [x] `/spec-check <S0276>` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <S0276>`.

---

## Blockers Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-20 - Implementation completed; awaiting `/spec-check`.
- 2026-05-20 - Audit passed; strategic spec closed as `Verified`.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
