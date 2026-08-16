# Tactical Plan: S1642 - launcher-section-header-grid-span

**Strategic spec:** [`../S1642_launcher-section-header-grid-span.md`](../S1642_launcher-section-header-grid-span.md)
**Research inputs:** none - every §6 item was resolved by owner ruling on 2026-08-15, no research artifact was written.
**Feature:** Launcher desktop - compact 2x1 section header
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-08-15

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | collapse-fold-membership | - | ✅ Done | 3/3 | [PHASE_01__collapse-fold-membership.md](PHASE_01__collapse-fold-membership.md) |
| 02 | seed-row-anchoring | - | ✅ Done | 2/2 | [PHASE_02__seed-row-anchoring.md](PHASE_02__seed-row-anchoring.md) |
| 03 | header-row-uniqueness | - | ✅ Done | 2/2 | [PHASE_03__header-row-uniqueness.md](PHASE_03__header-row-uniqueness.md) |
| 04 | compact-header-layout | - | ✅ Done | 2/2 | [PHASE_04__compact-header-layout.md](PHASE_04__compact-header-layout.md) |
| 05 | header-span-two | 01, 02, 03, 04 | ✅ Done | 5/5 | [PHASE_05__header-span-two.md](PHASE_05__header-span-two.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Ordering rationale

Phases 01-04 install the rules the 2x1 geometry needs and are inert while a header still spans its whole
row: no cell can share a header's row today, so the new fold branch, the seed anchor rule and the
one-header-per-row refusal all describe states the desktop cannot currently reach. Phase 05 is the single
behavioural flip, and it is last precisely so it never lands on a desktop that lacks one of those rules -
the failure mode strategic §7 rates highest is a fold that swallows a neighbouring section, which is
exactly what a narrowed header without phase 01 and phase 03 would produce.

Phases 01, 02 and 03 are mutually independent and may land in any order; only phase 05 consumes all three.

**Flavor placement.** Strategic §3.2 scopes the ticket to standard and noLegal, and no phase introduces a
flavor-specific class: the launcher's UI already lives in the `launcherEnabled` source set that exactly
those two flavors compile, and the model, repository and starter sets in `src/main` are compiled by every
flavor and gated by nothing. No `BuildConfig.IS_*` guard is planned or needed.

---

## Pre-Implementation Blockers

None. Every strategic §6 research item carries `Статус: Resolved` (owner rulings of 2026-08-15, §6.1-§6.4).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 forbids a new entry until separate user value is proven.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1642` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1642`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-15 - Initial tactical plan authored by `/spec-tech`.
