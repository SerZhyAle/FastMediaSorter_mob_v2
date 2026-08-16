# Phase 05 - Docs, catalog and inventory cleanup

**Strategic spec:** [`../S1511_stream-quality-rung-memory-and-probe-up.md`](../S1511_stream-quality-rung-memory-and-probe-up.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Record the changed capability and the refuted approaches, and regenerate the indexes the change invalidates.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 2 |
| `dev/REFUTED_APPROACHES.md` | Modified | ≤ 3 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 05.1 - Update the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the existing Streams record for adaptive quality step-down through `scripts/all_features/add.ps1`, so it also states that a channel keeps what it learned between sessions and climbs back on its own when the line improves. Take the flavor list from the `SUPPORT_STREAMS` row of `docs/FLAVOR_MATRIX.md`.

**Why:**

Strategic section 8 routes this as a `CHANGE` to the existing step-down record rather than a new capability, because the user-visible behaviour is the same feature getting a memory and a way back up.

**Verification:**

- `Grep` - `S1511` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Feature inventory and refuted approaches recorded. 05.1: the existing Streams step-down record was updated in place through add.ps1 (CHANGE, not a new capability, per strategic section 8) - it now states that a channel keeps its rung between sessions, that the record survives a catalog re-import because it is keyed by address, that the player retries the rung above and keeps the gain only on a survived window with the picture actually there, and that records age out and the store is capped. Flavors standard,noLegal,legacy,vr taken from the SUPPORT_STREAMS row of docs/FLAVOR_MATRIX.md. validate.ps1 PASS, 696 records. 05.2: two rows added to dev/REFUTED_APPROACHES.md - the seamless second-fetcher probe, refuted because it steals bandwidth from the line it measures, and an unmeasured probe cadence, refuted by 108.9 s of black screen at a 60 s base against 36.0 s at 5 min plus our own 3.0-3.5 s switch cost on a Galaxy S21+.

---

### Step 05.2 - Record the refuted approaches

**Files:** `dev/REFUTED_APPROACHES.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add two rows citing S1511 and the measurement in its section 0: a seamless probe that fetches a segment of the higher rung needs a second fetcher and steals bandwidth from the link it is measuring, so it can cause the stall it measures; and a probe cadence chosen without measurement, where a 60 s base produced 108.9 s of black screen against 36.0 s at a 5 min base with per-rung memory. Name what shipped instead in each case.

**Why:**

The file admits an entry only when a source ticket supplies a measurement, and this ticket carries one - recording it is what stops the seamless-probe idea from being re-proposed as an obvious improvement later.

**Verification:**

- `Grep` - `S1511` present in `dev/REFUTED_APPROACHES.md`, in two rows.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Feature inventory and refuted approaches recorded. 05.1: the existing Streams step-down record was updated in place through add.ps1 (CHANGE, not a new capability, per strategic section 8) - it now states that a channel keeps its rung between sessions, that the record survives a catalog re-import because it is keyed by address, that the player retries the rung above and keeps the gain only on a survived window with the picture actually there, and that records age out and the store is capped. Flavors standard,noLegal,legacy,vr taken from the SUPPORT_STREAMS row of docs/FLAVOR_MATRIX.md. validate.ps1 PASS, 696 records. 05.2: two rows added to dev/REFUTED_APPROACHES.md - the seamless second-fetcher probe, refuted because it steals bandwidth from the line it measures, and an unmeasured probe cadence, refuted by 108.9 s of black screen at a 60 s base against 36.0 s at 5 min plus our own 3.0-3.5 s switch cost on a Galaxy S21+.

---

### Step 05.3 - Regenerate the catalog and close

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the ticket, set role and status for the new entity, DAO, normalizer and use cases via `set.ps1`, then close through `scripts/post-change.ps1` naming the whole changed set with `-ScopeToFile`.

**Why:**

New public classes stay invisible to the catalog-first research path until the index carries them, and CLAUDE.md section 12 routes mechanical closure through the facade.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamQualityMemory*"` returns the new records with non-empty roles.
- `post-change.ps1` prints `post-change: PASS` and exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Catalog regenerated and closed. catalog_sync ran inside post-change (2833 records); set.ps1 gave role and status=new to all six new classes - StreamQualityMemoryEntity, StreamQualityMemoryDao, StreamUrlNormalizer, both use cases and StreamQualityMemoryEntryPoint - verified non-empty in dev/CATALOG/app_v2.jsonl. Closure: post-change PASS for the Kotlin set and PASS for the doc set, the second re-run with -RegistryAck 'feature-inventory,refuted-approaches' after the registry advisory named those two ids; ALL_FEATURES.schema.json was listed as a sibling and needs no edit, because the change updates one existing record's text and adds no field.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `/spec-check S1511` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - regenerated indexes are reproducible from their sources.
