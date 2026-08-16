# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S0494_send-to-cloud-http-materialization.md`](../S0494_send-to-cloud-http-materialization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Record the delivered capability and refresh the generated indexes.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 04.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` stating that «Send to..» now materializes cloud and direct web files with download progress. Do not edit the file by hand and do not touch `docs/FEATURES*.md`.

**Why:**

CLAUDE.md section 11 makes `ALL_FEATURES.jsonl` the inventory each spec writes its delivered capability into, and it is the diff `/skill-release` reads when it builds the public showcase.

**Verification:**

- `Grep` - `Send to` with `materializ` matches the new record in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate the class catalog and close the ticket mechanically

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` for the two new classes via `set.ps1`, then run the closure facade `scripts/post-change.ps1` naming the whole changed set with `-ScopeToFile` and `-ChangeType Kotlin`.

**Why:**

CLAUDE.md section 12 routes mechanical closure through `post-change.ps1`, and new classes carry no `role`/`status` until they are set explicitly.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "CloudDownloadUseCase"` returns one record with a non-empty `role`.
- `post-change.ps1` prints `post-change: PASS` and exits 0.

**Status:** `[x]` done

---

## Deviations recorded during implementation

- Both detekt blockers in the Blockers Log are cleared, neither by re-freezing debt.
  - `LongParameterList` was removed rather than re-baselined. `CloudDownloadUseCase` needs seven collaborators the handler already holds, so Phase 01's 17th constructor parameter was unnecessary: the handler now builds the use case in a property initializer, exactly as it already builds `CloudToCloudTransferHelper` and `CloudFileOperationPathUtils`. The constructor returns to its baselined 16-parameter signature, so the accepted entry matches again and no new finding exists. The class is stateless, so the hand-built instance and the one Hilt injects into share materialization are interchangeable.
  - `detekt-baseline-absorption` was a signature re-key, not new debt. The absorbed id and the pruned id are the same rule, class and function - `ReturnCount` on `CastMediaManagerImpl.resolveAndSend` - differing only by the `stereoCrop` parameter S1558 added. The snapshot was re-seeded with that reason and the accepted id is named in this ticket's dev-log row.
- Changing the handler's constructor also required dropping the matching argument from `CloudFileOperationHandlerTest`, which `a.ps1 fk` never compiles.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Dev log entry added for the ticket via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit - generated indexes are rebuilt from source by their own scripts.
