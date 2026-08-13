# Tactical Plan: S1456 - untracked-dialogs-outside-settings-helpers

**Strategic spec:** [`../S1456_untracked-dialogs-outside-settings-helpers.md`](../S1456_untracked-dialogs-outside-settings-helpers.md)
**Research inputs:** none
**Feature:** Untracked-dialog ratchet gate plus the sweep onto `showBoundTo`
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | gate-and-baseline | - | ✅ Done | 6/6 | [PHASE_01__gate-and-baseline.md](PHASE_01__gate-and-baseline.md) |
| 02 | player-surfaces | 01 | ✅ Done | 5/5 | [PHASE_02__player-surfaces.md](PHASE_02__player-surfaces.md) |
| 03 | browse-managers | 01 | ✅ Done | 3/3 | [PHASE_03__browse-managers.md](PHASE_03__browse-managers.md) |
| 04 | main-and-add-resource | 01 | ✅ Done | 3/3 | [PHASE_04__main-and-add-resource.md](PHASE_04__main-and-add-resource.md) |
| 05 | settings-streams-share | 01 | ✅ Done | 4/4 | [PHASE_05__settings-streams-share.md](PHASE_05__settings-streams-share.md) |
| 06 | remaining-surfaces | 01 | ✅ Done | 4/4 | [PHASE_06__remaining-surfaces.md](PHASE_06__remaining-surfaces.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none. Strategic §6 carries no open research item: the predicate, the wrapper name and the sweep order are fixed by the 2026-08-09 measurement and by the existing gate infrastructure.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 asks for an `docs/ALL_FEATURES.jsonl` FIX record instead (Phase 07).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `scripts/quality/untracked-dialog-baseline.txt` reads `0`.
- [ ] `/spec-check S1456` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1456`.

---

## Cross-cutting invariants

These bind every phase and are not repeated per step.

- The gate, not this plan, is the authoritative list of remaining sites. The file lists below were measured on 2026-08-09 and are a planning aid; every sweep step re-reads `assert-untracked-dialogs.ps1 -List` before editing and after.
- A sweep changes the binding and nothing else: same builder, same title, same buttons, same listener bodies. A step that wants to change wording or behaviour belongs to another ticket.
- The lifecycle owner is taken from what the site already holds - `this` inside an Activity, the fragment field a manager was constructed with, the fragment a helper already receives. Widening a constructor is allowed only when the site holds neither.
- `AlertDialog.Builder.showBoundTo` returns a nullable dialog, so a site that used the return value of `show()` keeps working only if it handles null; a site that ignored the return value ignores it still.
- `util/LifecycleDialogExt.kt` gains the created-dialog overload in Phase 01 and is not touched again. Its `Timber.d("S1447: ..")` probes belong to S1447, which is in `BlockNeedUserTest`, and removing them there would break that ticket's gate.
- Take `CODE.LOCK` immediately before each sweep step and release it right after; a sweep phase must never hold the lock across a build.

---

## Blockers Log

- none.

---

## Change Log

- 2026-08-09 - Initial tactical plan authored by `/spec-tech`, phases sized from the 146-site measurement.
