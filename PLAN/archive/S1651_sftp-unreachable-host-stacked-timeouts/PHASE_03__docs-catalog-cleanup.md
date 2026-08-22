# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S1651_sftp-unreachable-host-stacked-timeouts.md`](../S1651_sftp-unreachable-host-stacked-timeouts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Close the implementation evidence, catalog, and capability records without adding a new user-facing surface.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Generated | n/a |
| `dev/CATALOG/app_v2.jsonl` | Generated | n/a |
| `docs/ALL_FEATURES.jsonl` | Generated via catalog CLI when applicable | n/a |

---

## Steps

### Step 03.1 - Synchronize Kotlin catalog and change log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the repository catalog synchronization for `app_v2` and the standard post-change facade with file-scoped validation for every S1651 Kotlin and test file. Resolve touched-file warnings before continuing and confirm the generated catalog reflects the new SFTP cache type.

**Why:**

The feature changes a shared connection-layer component, so its discoverability and per-file validation evidence must be regenerated with the implementation.

**Verification:**

- `Grep` - `SftpConnectionFailureCache` appears in `dev/CATALOG/app_v2.jsonl` after synchronization.
- `Grep` - an S1651 row appears in `dev/CHANGELOG.md`.

**Result:** `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` -> `[catalog_sync] OK (app_v2)`, exit 0. `SftpConnectionFailureCache` is present in `dev/CATALOG/app_v2.jsonl` (`layer=data`, `sector=network`, `hasTests=true`), with `role` and `status=tested` filled via `dev/CATALOG/scripts/set.ps1`. `dev/CHANGELOG.md` already carries 7 S1651 rows.

Scoped validation was run in place of the post-change facade, because closure belongs to the owning session:

- `pwsh -NoProfile -File ./a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0.
- `pwsh -NoProfile -File ./a.ps1 fu` -> `BUILD SUCCESSFUL in 3m 8s`, exit 0; `assert-test-suite-complete: PASS`.
- `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles "<4 S1651 files>"` -> `assert-detekt: PASS [scoped] - 68 file(s) with new findings project-wide, none among changed files.`, exit 0.
- `pwsh -NoProfile -File ./a.ps1 fg` -> `assert-fast-gates: PASS (all fast gates green).`, exit 0.

**[DEFERRED]** `scripts/post-change.ps1` itself - the implementing sub-agent is barred from the closure facade and the dev-log writer. The owning session must run it with `-ChangeType Kotlin -ScopeToFile` over the four files listed in the INDEX.

**Status:** `[x]` done

---

### Step 03.2 - Record capability impact and run final spec check

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Use the standard ticket closure flow to record the SFTP reliability improvement if it is user-visible, then run `/spec-check S1651`. Do not add feature-marketing text unless the closure workflow identifies a required public documentation delta.

**Why:**

The user-visible benefit is faster failure feedback, while the strategic scope deliberately avoids a new screen, setting, or localized text.

**Verification:**

- `Grep` - `S1651` appears in `docs/ALL_FEATURES.jsonl` if the closure workflow classifies the improvement as user-visible.
- `Grep` - `S1651` appears in `dev/CHANGELOG.md`.
- `/spec-check S1651` returns a documented verdict.

**Result:** `dev/CHANGELOG.md` carries S1651 rows (7). `docs/ALL_FEATURES.jsonl` carries no S1651 record yet: at 2026-08-14 the count is 0.

**[DEFERRED] - both remaining actions are closure-owned and barred to the implementing sub-agent:**

1. `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1`. Recommended classification: **user-visible**. The behaviour the user meets changes - an unreachable SFTP folder reports its error once instead of stacking four full connect timeouts (~31 s measured in `logs/fastmediasorter_20260813_005708.log`, now one ~10 s wait). No new screen, setting or string, so strategic §8 still needs no `docs/FEATURES*` text.
2. `/spec-check S1651` - the audit verdict and any `Status:` advance to `Verified`.

**Status:** `[x]` developer scope done - closure actions `[DEFERRED]` to the owning session

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done` within developer scope; the two closure actions are marked `[DEFERRED]` in Step 03.2.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1` - **deferred to the owning session's closure run**.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
