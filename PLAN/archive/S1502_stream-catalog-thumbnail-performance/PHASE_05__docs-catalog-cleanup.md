# Phase 05 - Docs, catalog and closure

**Strategic spec:** [`../S1502_stream-catalog-thumbnail-performance.md`](../S1502_stream-catalog-thumbnail-performance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the classes Phase 04 added, document the measurement entry points Phase 01 created where the project's operators look for them, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/DEV_OPS.md` | Modified | ≤ +25 |
| `dev/CHANGELOG.md` | Appended via script | n/a |

---

## Steps

### Step 05.1 - Regenerate the class catalog and classify the new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` for the classes Phase 04 introduced - `StreamPlayOutcomeEntity`, `StreamPlayOutcomeDao`, `ObserveStreamPlayOutcomesUseCase` - via `dev/CATALOG/scripts/set.ps1`. No `-NoFlavors` hint is needed: every one of them lives in `src/main` and ships in all six flavors.

**Why:**

CLAUDE.md "Catalog & Navigation" requires new classes to carry `role` and `status`, and an unclassified entity/DAO pair is exactly what makes the next ticket's catalog query miss the outcome table.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamPlayOutcome*"` returns both new classes with a non-empty `role`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification PASS. `catalog_sync.ps1 -Module app_v2` had already run on every step close through `post-change.ps1`, so the scan was current; classified with `set.ps1 -Status new`. **Five classes, not the three the step names** - the plan was written before Phase 04 grew: it missed `Migration47To48` (a public top-level `val` the catalog indexes like any other) and `GetStreamPlayOutcomeUseCase` (added in Step 04.6 for the player's read path). Leaving either unclassified would have defeated the point of the step, which is that the next ticket's catalog query finds the outcome table.

---

### Step 05.2 - Document the streams measurement checkpoints

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add the five checkpoints and `streams-perf-seed.ps1` from Phase 01 to the operations documentation beside the existing pre-release measurement entries, stating what each measures, that the seed step must run first, and that the two scroll checkpoints are advisory on an emulator.

**Why:**

Strategic §2 goal 5 asks for a way to see the next regression before release, and a harness whose entry points are not written down where operators look is one that will not be run by the person who needs it.

**Verification:**

- `Grep` - `streams-perf-seed` matches in `docs/DEV_OPS.md`.
- `Grep` - all five checkpoint names match in `docs/DEV_OPS.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification PASS. Added "Streams-catalog performance checkpoints (S1502)" to `docs/DEV_OPS.md` beside the Macrobenchmark section: all five checkpoint names, the seed-first requirement, and the two traps this ticket actually hit - `adb logcat -c` before opening the screen (or a previous launch's marker is read as this run's), and that `StreamsActivity` is not exported so the screen must be reached through the UI, which needs the `enable_streams` setting on. The advisory note now says what "advisory" is worth: a burst under 100 frames is reported `insufficient: true` and is not a number.
- 2026-08-08 - **Two findings outside the step's file list, both fixed.** `docs/DEV_OPS.md` claimed "Room schema version: 6" - stale by 42 versions, and this ticket made it staler. Corrected to 48, and pointed at `AppDatabase.kt` as the source of truth so the line cannot rot the same way again. Separately, the mandatory document-registry loop flagged `docs/ARCHITECTURE.md:253`, whose streams data-flow description named only `StreamSourceDao / StreamSourceEntity`; a reader would have had no way to learn the outcome side channel exists. Added it, with the per-table-invalidation reason that forced it. Sibling check: no other registered document restates either fact - `dev/TECH_REQUIREMENTS.md:127` already read 48 because it is a machine-synced pin, which is exactly why the unpinned prose line drifted unnoticed.

---

### Step 05.3 - Close through the mechanical facade

**Files:** repository-wide
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` naming the whole changed set with `-Files` and `-ScopeToFile`, `-ChangeType Mixed`, `-Module app_v2`. Read the verdict line: only a bare `post-change: PASS` is clean; `PASS WITH ADVISORIES (n)` names each advisory and each one must be read. Do not add a `docs/ALL_FEATURES.jsonl` record - strategic §8 states this ticket delivers no new user-facing capability.

**Why:**

CLAUDE.md §12 requires mechanical closure to route through the facade rather than hand-rolled steps, and §8 of the strategic spec explicitly rules out a feature-inventory record for a performance ticket.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` or `PASS WITH ADVISORIES (n)` with each advisory read.
- `Grep` - `S1502` returns zero hits in `docs/ALL_FEATURES.jsonl`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file, added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `docs/FEATURES*.md` untouched - strategic §8 reads "Без изменений".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. The ticket ends in `BlockNeedUserTest`: the S1502 probe tags from Step 04.8 stay in the source until a device run on floor-tier hardware closes strategic §6.1 and the owner confirms §11's thresholds, which §3.3 records as still his to set.

---

## Rollback Plan

Revert the phase commit - documentation and a regenerated local index only. `dev/CATALOG/*.jsonl` is gitignored and regenerated rather than reverted.
