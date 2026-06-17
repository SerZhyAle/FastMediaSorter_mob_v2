---
model: sonnet
---

# Spec Arc - Archive a Specification

Move spec files to `temp/done/` (git-ignored) and mark journal records `Archived`. Accepts one or several ids. Works from any non-`Archived` status. No tactical folder required.

## Usage

```text
/spec-arc <Sxxxx>
/spec-arc <Sxxxx> <Syyyy> <Szzzz>
/spec-arc <Sxxxx> <Syyyy> --removes-functionality
```

- Ids may be space- or comma-separated. Each must match `S####`.
- `--removes-functionality`: every listed id corresponds to a real removal of user-visible behaviour. Emits one `DELETE` line per id in `dev/FUNCTIONALITY.log` (step 4). Without it, archiving is bookkeeping (cancelled / superseded / never implemented) and the functionality log is left untouched. The flag is all-or-nothing for the batch; for a mix of "removes" and "bookkeeping" ids, run two separate invocations.

## When to use

- Specs Verified and no longer needed in the active workspace.
- Specs cancelled, superseded, or will never be implemented.
- Decluttering `PLAN/` without losing history.

## Process

**1 - Resolve every id first (fail-fast on the batch, not mid-loop).**

For each id, run `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json`. Classify:
- Not found -> report, drop from batch (do not abort the others).
- Already `Archived` -> report `skipped (already archived)`, drop from batch.
- Otherwise -> keep for archiving.

If nothing remains after classification, print the report and stop. Never infer status from the filename - `select.ps1` is authoritative.

**2 - Archive each surviving id.**

Per id:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/archive.ps1 -Id <Sxxxx>
```

`archive.ps1`:
- Creates `temp/done/` if absent.
- Moves `PLAN/Sxxxx_<slug>.md` -> `temp/done/Sxxxx_<slug>.md`.
- Moves `PLAN/Sxxxx_<slug>/` -> `temp/done/Sxxxx_<slug>/` (if tactical folder exists).
- Sets journal `status = Archived`, `priority = 0`.

Record the per-id exit code. A non-zero exit drops that id to `failed` in the summary; continue with the rest.

**3 - Remove leftover debug tags.**

After all moves, `Grep` all `.kt` once for `Timber.d("(Sxxxx|Syyyy|..):` across the archived ids and delete every matching line - an archived spec must carry no debug tags (CLAUDE.md "Debug Verification Tags"). Normally a no-op: by invariant they were removed when each spec left `BlockNeedUserTest`. Note which `.kt` lost a tag for the dev log.

**4 - Bookkeep per id (batched pwsh).**

`archive.ps1` already set status `Archived` and moved files. Touch the rest via `close-and-log.ps1 -StatusOnly` - one call per id:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
    -Id <Sxxxx> `
    -Status Archived `
    -StatusOnly `
    -DevLogs @(
        '{"file":"PLAN/Sxxxx_<slug>.md","target":"spec-arc","desc":"Archive Sxxxx (<name>) -> temp/done/"}'
        # plus one entry per .kt that lost a Timber.d("Sxxxx: ...") tag in step 3
      ) `
    -FuncOp <DELETE|""> -FuncDesc "<english summary or omit>" `
    -SkipCatalogSync  # archived spec has no fresh .kt code
```

- Pass `-FuncOp DELETE` + `-FuncDesc` only when `--removes-functionality` was given; otherwise omit both (or pass `-SkipFuncLog`). Cancelled / superseded / never-implemented specs produce no functionality-log entry.
- Pass `-SkipCatalogSync` unless a tag deletion in step 3 changed live `.kt` files (rare). If it did, drop `-SkipCatalogSync` on the relevant id, or run `scripts/catalog_sync.ps1 -Module app_v2` once after the batch.

Individual-call fallback: `add_to_functionality_log.ps1 -Op DELETE ...` (only on flag) + `add_to_dev_log.ps1 "PLAN/Sxxxx_<slug>.md" "spec-arc" "..."` + a dev log line per `.kt` that lost a tag.

**5 - Chat output.**

One block, dry:

```
Archived N of M:
  Sxxxx -> temp/done/Sxxxx_<slug>.md [+ Sxxxx_<slug>/]
  Syyyy -> temp/done/Syyyy_<slug>.md
Skipped: Szzzz (already archived)
Not found: Swwww
Failed: Svvvv (archive.ps1 exit <code>)
Find later: temp/done/Sxxxx_* or select.ps1 -Id Sxxxx -Format json (records stay in journal).
```

Omit empty lines (no skips -> no "Skipped:" line).

## Lifting an archived spec

Findable two ways:
- **File:** `temp/done/Sxxxx_<slug>.md` (and `temp/done/Sxxxx_<slug>/` for tactical).
- **Journal:** `select.ps1 -Id Sxxxx -Format json` - record remains, `status: Archived`.

To restore: move files back to `PLAN/`, then `update.ps1 -Id Sxxxx -Status Draft` (or appropriate status).

## Spec Catalog hooks

- **Status transition:** performed by `archive.ps1` (sets `Archived`, `priority = 0`) per id.
- **Debug tags:** deleted from `.kt` as part of the archive (step 3). Normally a no-op.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never hard-delete a record.
