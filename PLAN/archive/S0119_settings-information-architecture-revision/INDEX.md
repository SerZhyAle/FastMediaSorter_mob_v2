# Tactical Plan: S0119 — settings-information-architecture-revision

**Strategic spec:** [`../S0119_settings-information-architecture-revision.md`](../S0119_settings-information-architecture-revision.md)
**Feature:** Settings Information Architecture Revision
**Tier:** 4 — Large / cross-cutting
**Priority:** 60
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-inventory | — | ✅ Done | 4/4 | [PHASE_01__settings-inventory.md](PHASE_01__settings-inventory.md) |
| 02 | ia-model | 01 | ✅ Done | 5/5 | [PHASE_02__ia-model.md](PHASE_02__ia-model.md) |
| 03 | migration-map | 02 | ✅ Done | 4/4 | [PHASE_03__migration-map.md](PHASE_03__migration-map.md) |
| 04 | multilingual-search | 03 | ✅ Done | 5/5 | [PHASE_04__multilingual-search.md](PHASE_04__multilingual-search.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

> All 12 research items from strategic §6 are **internal** — resolved by executing Phases 01–03.
> No external dependency blocks Phase 01 start.
> Items are listed for traceability; check them off when the corresponding phase produces its output document.

- [x] **§6.1 — Casual user perspective:** which 5–10 tasks require no search? — Resolved in Phase 01 inventory + Phase 02 IA model.
- [x] **§6.2 — Power user perspective:** boundary between "advanced but findable" and "rare service action" — Resolved in Phase 02 placement checklist.
- [x] **§6.3 — Feature developer perspective:** placement checklist and contextual-vs-global decision — Resolved in Phase 02 ia-model.
- [x] **§6.4 — IA / UX perspective:** adequacy of current top-level tabs; service-actions vs preferences separation — Resolved in Phase 01 + Phase 02.
- [x] **§6.5 — Flavor owners perspective:** which differences stay leaf-level, which break the mental model — Resolved in Phase 02 ia-model.
- [x] **§6.6 — Support / docs / localization perspective:** section names to sync with help content; search synonym sources — Resolved in Phase 03 migration-map + Phase 04 multilingual-search.
- [x] **§6.7 — Accessibility / non-touch perspective:** depth constraints for keyboard / D-pad; navigation affordance rules — Resolved in Phase 02 ia-model.
- [x] **§6.8 — Current state inventory:** full list of all user-facing settings, service actions, hidden/debug controls with current placement — **Resolved by Phase 01 output document.**
- [x] **§6.9 — Behavior preservation perspective:** which elements carry hidden behavioral load — Resolved in Phase 01 inventory (behavior column).
- [x] **§6.10 — Responsive surface perspective:** spatial rules per window mode — Resolved in Phase 02 ia-model.
- [x] **§6.11 — Multilingual discoverability perspective:** cross-locale search behavior, alias sources — Resolved in Phase 04.
- [x] **§6.12 — Non-regression contract:** mandatory preserved affordances — Resolved in Phase 01 behavior inventory + Phase 03 migration-map.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `PLAN/S0119_.../docs/settings-inventory.md` exists with all tabs catalogued.
- [x] `PLAN/S0119_.../docs/ia-model.md` exists with placement checklist and surface hierarchy.
- [x] `PLAN/S0119_.../docs/migration-map.md` exists with per-item canonical placement decisions.
- [x] `SettingsSearchIndex` data class carries `localizedKeywords` field.
- [x] `SettingsSearchRegistry.search()` matches EN/RU/UK aliases.
- [x] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`).
- [x] `/spec-check S0119` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0119`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-08 — Initial tactical plan authored by `/spec-tech`.
