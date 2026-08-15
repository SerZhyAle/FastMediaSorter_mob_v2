---
description: "Use when archiving one or more specs - move files to PLAN/archive/ and set status Archived. Triggers: 'archive spec Sxxxx', 'retire these tickets'."
---

# Spec Arc - Archive a Specification

Move spec files to `PLAN/archive/` (version-controlled) and mark journal records `Archived`. Accepts one or several ids. Works from any non-`Archived` status. No tactical folder required.

## Usage

```text
/spec-arc <Sxxxx>
/spec-arc <Sxxxx> <Syyyy> <Szzzz>
/spec-arc <Sxxxx> <Syyyy> --removes-functionality
```

- Ids space- or comma-separated. Each must match `S####`.
- `--removes-functionality`: every listed id corresponds to real removal of user-visible behaviour. Marks each id's capability record `removed` in `docs/ALL_FEATURES.jsonl` (step 4, via `close-and-log.ps1 -FuncOp DELETE` → `all_features/add.ps1 -Status removed`). Without it, archiving is bookkeeping (cancelled / superseded / never implemented) and inventory left untouched. Flag is all-or-nothing for the batch; for mix of "removes" and "bookkeeping" ids, run two separate invocations.

## When to use

- Specs Verified and no longer needed in active workspace.
- Specs cancelled, superseded, or never to be implemented.
- Decluttering `PLAN/` without losing history.

## Process

**1 - Resolve every id first (fail-fast on batch, not mid-loop).**

For each id, run `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json`. Classify:
- Not found -> report, drop from batch (do not abort others).
- Already `Archived` -> report `skipped (already archived)`, drop from batch.
- Otherwise -> keep for archiving.

If nothing remains after classification, print report and stop. Never infer status from filename - `select.ps1` authoritative.

**2 - Archive each surviving id.**

Per id:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/archive.ps1 -Id <Sxxxx>
```

`archive.ps1`:
- Creates `PLAN/archive/` if absent.
- Moves `PLAN/Sxxxx_<slug>.md` -> `PLAN/archive/Sxxxx_<slug>.md`.
- Moves `PLAN/Sxxxx_<slug>/` -> `PLAN/archive/Sxxxx_<slug>/` (if tactical folder exists).
- Sets journal `status = Archived`, `priority = 0`, and re-points `file` at the new path.

Unlike the working `PLAN/` workspace, the archive **is** under version control (S1620): it is
the only durable record of why a closed decision was made, so it must survive a `temp/` sweep
and reach other machines. Archiving therefore now produces a committable change.

Record per-id exit code. Non-zero exit drops that id to `failed` in summary; continue with rest.

**3 - Remove leftover debug tags.**

After all moves, `Grep` all `.kt` once for `Timber.d("(Sxxxx|Syyyy|..):` across archived ids and delete every matching line - archived spec must carry no debug tags (CLAUDE.md "Debug Verification Tags"). Normally a no-op: by invariant removed when each spec left `BlockNeedUserTest`. Note which `.kt` lost a tag for dev log.

**4 - Bookkeep per id (batched pwsh).**

`archive.ps1` already set status `Archived` and moved files. Touch the rest via `close-and-log.ps1 -StatusOnly` - one call per id:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
    -Id <Sxxxx> `
    -Status Archived `
    -StatusOnly `
    -DevLogs '[{"file":"PLAN/Sxxxx_<slug>.md","target":"spec-arc","desc":"Archive Sxxxx (<name>) -> PLAN/archive/"}]' `
    # -DevLogs is ONE JSON-array string - append one {file,target,desc} object per .kt that lost
    # a Timber.d("Sxxxx: ...") tag in step 3. Never a PowerShell array literal @('{..}','{..}'):
    # pwsh -File binds only its first element and close-and-log.ps1 rejects the leftovers (S1063).
    -FuncOp <DELETE|""> -FuncDesc "<english summary or omit>" `
    -FeatId "<area>.<feature> of the record being removed" `
    -FeatArea "<its area>" -FeatName "<its name>" -FeatFlavors "<its flavors>" `
    -SkipCatalogSync  # archived spec has no fresh .kt code
```

- Pass `-FuncOp DELETE` + `-FuncDesc` only when `--removes-functionality` given; otherwise omit both (or pass `-SkipFuncLog`). Cancelled / superseded / never-implemented specs produce no inventory entry.
- `-FuncOp` requires `-FeatArea`/`-FeatName`/`-FeatFlavors` (S1072) - the script no longer invents them. For `DELETE` also pass the existing record's `-FeatId`: without it the id is derived from the slug of `-FeatName`, so a name that does not reproduce the original id would add a *new* `status: removed` record instead of marking the real one. To only flip an existing record's status, `scripts/all_features/patch.ps1 -Id <id> -Status removed` is the direct tool.
- Pass `-SkipCatalogSync` unless a tag deletion in step 3 changed live `.kt` files (rare). If it did, drop `-SkipCatalogSync` on relevant id, or run `scripts/catalog_sync.ps1 -Module app_v2` once after batch.

Individual-call fallback: `scripts/all_features/add.ps1 -Id "<area>.<feature>" -Area .. -Name .. -Description ".." -Flavors .. -Spec <Sxxxx> -Status removed` (only on flag) + `add_to_dev_log.ps1 "PLAN/Sxxxx_<slug>.md" "spec-arc" "..."` + dev log line per `.kt` that lost a tag.

**5 - Chat output.**

One block, dry:

```
Archived N of M:
  Sxxxx -> PLAN/archive/Sxxxx_<slug>.md [+ Sxxxx_<slug>/]
  Syyyy -> PLAN/archive/Syyyy_<slug>.md
Skipped: Szzzz (already archived)
Not found: Swwww
Failed: Svvvv (archive.ps1 exit <code>)
Find later: PLAN/archive/Sxxxx_* or select.ps1 -Id Sxxxx -Format json (records stay in journal).
```

Omit empty lines (no skips -> no "Skipped:" line).

## Lifting an archived spec

Findable two ways:
- **File:** `PLAN/archive/Sxxxx_<slug>.md` (and `PLAN/archive/Sxxxx_<slug>/` for tactical).
- **Journal:** `select.ps1 -Id Sxxxx -Format json` - record remains, `status: Archived`.

To restore: move files back to `PLAN/`, then `update.ps1 -Id Sxxxx -Status Draft` (or appropriate status).

## Spec Catalog hooks

- **Status transition:** performed by `archive.ps1` (sets `Archived`, `priority = 0`) per id.
- **Debug tags:** deleted from `.kt` as part of archive (step 3). Normally a no-op.
- **Forbidden:** per CLAUDE.md Rule 12 (spec catalog is script-owned) - obey it as written. Additionally, never hard-delete a record.
