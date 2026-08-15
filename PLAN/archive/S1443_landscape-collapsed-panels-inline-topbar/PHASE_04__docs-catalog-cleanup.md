# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1443_landscape-collapsed-panels-inline-topbar.md`](../S1443_landscape-collapsed-panels-inline-topbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Register the two new classes in the class catalog, record the delivered capability change in the feature inventory, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |

---

## Steps

### Step 04.1 - Register the new classes in the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`

**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set the role and status of both new classes with `dev/CATALOG/scripts/set.ps1`: `MainCollapsedChipPlacementPlanner` as the pure decision function and `MainCollapsedChipsPlacementManager` as the view-level executor. Neither class is flavor-specific, so pass no `-NoFlavors`.

**Why:**

Strategic §5.1 introduces two new roles whose whole point is that a later reader finds them instead of re-deriving the placement logic, and an unregistered class is invisible to the catalog-first research order every task in this repository starts with.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*CollapsedChip*" -Module app_v2` - returns both classes.
- Both returned records carry a non-empty `role` and `status`.

**Status:** `[x]` done

---

### Step 04.2 - Record the capability change in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`

**Depends on:** Step 04.1

**Prompt for developer:**

> Add a CHANGE record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing, in English, that collapsed main-screen panel chips relocate into the free tail of the command bar in wide layout and free the row they used to occupy. Reference spec `S1443`. Do not pass `-NoLegal` - the behaviour ships in every flavor. Do not touch `docs/FEATURES*.md`.

**Why:**

Strategic §8 states the change is user-perceivable but not a new capability, so it belongs in the developer inventory as a CHANGE and explicitly not in the curated public showcase, which only `/skill-release` writes.

**Verification:**

- `Grep` - `S1443` matches at least once in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit code 0.
- `Grep` - `S1443` returns zero hits in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

**Status:** `[x]` done

---

### Step 04.3 - Close through the mechanical facade

**Files:** `dev/CHANGELOG.md`

**Depends on:** Step 04.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` once for the whole changed set with `-Files` naming every Kotlin, test and resource file this ticket touched, `-ChangeType Mixed`, `-Module app_v2` and `-ScopeToFile`, so every scoped gate judges this ticket's files rather than the whole dirty tree. Read the printed verdict: only a bare `post-change: PASS` is clean, and any advisory it names must be read before the ticket moves on.

**Why:**

Strategic §11 makes the ticket's completion an observable set of outcomes rather than an assertion, and the facade is the only path that chains the dev log, the catalog sync and the quality gates in one verdict-bearing run.

**Verification:**

- `scripts/post-change.ps1` - exit code 0 and the printed verdict is `post-change: PASS` or `PASS WITH ADVISORIES (n)` with every advisory named and read.
- `Grep` - `S1443` matches at least once in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-08 - Step 04.1 done. `set.ps1` gave both new classes a role and `status: new`; `query.ps1 -ClassMatches "*CollapsedChip*"` returns both under layer `ui`.
- 2026-08-08 - Step 04.2 done. `main-screen.collapsed-panel-chips-inline-command-bar` added to `docs/ALL_FEATURES.jsonl` with flavors `standard,noLegal,lite,photos,legacy,vr` - the behaviour lives in `src/main` behind no BuildConfig gate, so the flavor list is read off the absence of a gate, not off a sibling record. `validate.ps1` PASS, 672 records. `docs/FEATURES*.md` untouched: zero `S1443` hits across all three locales.
- 2026-08-08 - Step 04.3 done. Closure through `post-change.ps1` per phase rather than one run at the end, because each phase had to prove its own gates before the next one built on it. The first phase-04 run returned `PASS WITH ADVISORIES (1)` for the `document-registry` acknowledgement: the changed file is the registered `feature-inventory` document, whose sibling `docs/ALL_FEATURES.schema.json` needs no edit - the new record uses only existing fields, which `validate.ps1` proves. Re-run with `-RegistryAck feature-inventory` returned a bare `post-change: PASS`.
- 2026-08-08 - Phase-boundary audit skipped: `Files Touched` is documentation and generated catalog only.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - no source changed in this phase; the phase 03 `.\a.ps1 dq` build stands.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - chained by `post-change.ps1` and by `set.ps1`'s render.
- [x] Phase-boundary audit - not applicable, documentation-only phase.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The relocation is device-verifiable only: strategic §11 criteria 1 to 8 all describe on-screen outcomes in wide layout, so the ticket goes to `BlockNeedUserTest` with probe tags before it can reach `Verified`.

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog only; the catalog indexes are regenerated rather than hand-maintained, so a revert is followed by one `catalog_sync.ps1` run.
