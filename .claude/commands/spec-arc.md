# Spec Arc - Archive a Specification

Move a spec's files to `temp/done/` (git-ignored) and mark the journal record as `Archived`. Works from any non-Archived status. No tactical folder is required.

## Usage

```text
/spec-arc <Sxxxx>
/spec-arc <Sxxxx> --removes-functionality
```

`--removes-functionality`: explicit signal that archiving this spec corresponds to a real removal of user-visible behaviour from the app. Triggers a `DELETE` line in `dev/FUNCTIONALITY.log` (Process step 3a). Without this flag, archiving is treated as bookkeeping (cancelled / superseded / never implemented) and the functionality log is left untouched.

## When to use

- Spec is Verified and no longer needed in the active workspace.
- Spec is cancelled, superseded, or will never be implemented.
- Decluttering `PLAN/` without losing the history.

## Process

**1 - Resolve id.**

Run `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json`. Abort if record not found or status is already `Archived`.

**2 - Run archive script.**

```powershell
pwsh -NoProfile -File scripts/spec_catalog/archive.ps1 -Id <Sxxxx>
```

The script:
- Creates `temp/done/` if absent (already git-ignored via `temp/`).
- Moves `PLAN/Sxxxx_<slug>.md` → `temp/done/Sxxxx_<slug>.md`.
- Moves `PLAN/Sxxxx_<slug>/` → `temp/done/Sxxxx_<slug>/` (if tactical folder exists).
- Sets journal `status = Archived`, `priority = 0`.

**3 - Remove leftover debug tags.**

`Grep` all `.kt` for `Timber.d("<Sxxxx>:` and delete every matching line - an archived spec must carry no debug tags (CLAUDE.md "Debug Verification Tags"). Idempotent no-op if none (the normal case - they should have been removed when the spec left `BlockNeedUserTest`). Run a dev log line per `.kt` file that lost a tag.

**3a / 4 - Functionality log + dev log (batched).**

`archive.ps1` already set status to `Archived` and moved files in Step 2. To bookkeep the rest (functionality log on `--removes-functionality`, dev log entry, debug-tag removal log lines, catalog touch) in one pwsh process, use `close-and-log.ps1` with `-StatusOnly` (status is already `Archived`, just touch `updated`):

```powershell
pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
    -Id <Sxxxx> `
    -Status Archived `
    -StatusOnly `
    -DevLogs @(
        '{"file":"PLAN/Sxxxx_<slug>.md","target":"spec-arc","desc":"Archive Sxxxx (<name>) -> temp/done/"}'
        # plus one entry per .kt that lost a Timber.d("Sxxxx: ...") tag in Step 3
      ) `
    -FuncOp <DELETE|""> -FuncDesc "<english summary or omit>" `
    -SkipCatalogSync  # archived spec has no fresh .kt code
```

Pass `-FuncOp DELETE` + `-FuncDesc` only when `--removes-functionality` was provided; without that flag, omit `-FuncOp`/`-FuncDesc` (or pass `-SkipFuncLog`). Cancelled / superseded / never-implemented specs produce no functionality-log entry - the catalogue only records lifecycle of behaviour that actually existed in shipped builds.

Pass `-SkipCatalogSync` unless a tag-deletion in Step 3 changed live `.kt` files (rare - by invariant they should have been removed earlier when the spec left `BlockNeedUserTest`).

Individual-call fallback: `add_to_functionality_log.ps1 -Op DELETE ...` (only on flag) + `add_to_dev_log.ps1 "PLAN/Sxxxx_<slug>.md" "spec-arc" "..."` + a dev log line per `.kt` that lost a tag.

**5 - Chat output.**

```
Sxxxx archived. Files: temp/done/Sxxxx_<slug>.md [+ Sxxxx_<slug>/].
To find later: temp/done/Sxxxx_* or select.ps1 -Id Sxxxx -Format json (record stays in journal).
```

## Lifting an archived spec

Archived specs are findable in two ways:

- **File:** `temp/done/Sxxxx_<slug>.md` (and `temp/done/Sxxxx_<slug>/` for tactical).
- **Journal:** `select.ps1 -Id Sxxxx -Format json` - record remains, `status: Archived`.

To restore to active: move files back to `PLAN/`, then `update.ps1 -Id Sxxxx -Status Draft` (or appropriate status).

## Spec Catalog hooks

- **Status transition:** performed by `archive.ps1` (sets `Archived`, `priority = 0`).
- **Debug tags:** archiving moves the spec out of any active status - delete every `Timber.d("<Sxxxx>:` line from `.kt` as part of the archive (Process step 3). Normally a no-op.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never hard-delete a record.
