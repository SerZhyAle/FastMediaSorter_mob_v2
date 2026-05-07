# Spec Arc — Archive a Specification

Move a spec's files to `temp/done/` (git-ignored) and mark the journal record as `Archived`. Works from any non-Archived status. No tactical folder is required.

## Usage

```text
/spec-arc <Sxxxx>
```

## When to use

- Spec is Verified and no longer needed in the active workspace.
- Spec is cancelled, superseded, or will never be implemented.
- Decluttering `PLAN/` without losing the history.

## Process

**1 — Resolve id.**

Run `pwsh -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json`. Abort if record not found or status is already `Archived`.

**2 — Run archive script.**

```powershell
pwsh -File scripts/spec_catalog/archive.ps1 -Id <Sxxxx>
```

The script:
- Creates `temp/done/` if absent (already git-ignored via `temp/`).
- Moves `PLAN/Sxxxx_<slug>.md` → `temp/done/Sxxxx_<slug>.md`.
- Moves `PLAN/Sxxxx_<slug>/` → `temp/done/Sxxxx_<slug>/` (if tactical folder exists).
- Sets journal `status = Archived`, `priority = 0`.

**4 — Run dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<slug>.md" "spec-arc" "Archive Sxxxx (<name>) -> temp/done/"
```

**5 — Chat output.**

```
Sxxxx archived. Files: temp/done/Sxxxx_<slug>.md [+ Sxxxx_<slug>/].
To find later: temp/done/Sxxxx_* or select.ps1 -Id Sxxxx -Format json (record stays in journal).
```

## Lifting an archived spec

Archived specs are findable in two ways:

- **File:** `temp/done/Sxxxx_<slug>.md` (and `temp/done/Sxxxx_<slug>/` for tactical).
- **Journal:** `select.ps1 -Id Sxxxx -Format json` — record remains, `status: Archived`.

To restore to active: move files back to `PLAN/`, then `update.ps1 -Id Sxxxx -Status Draft` (or appropriate status).

## Spec Catalog hooks

- **Status transition:** performed by `archive.ps1` (sets `Archived`, `priority = 0`).
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never hard-delete a record.
