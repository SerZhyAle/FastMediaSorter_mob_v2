---
name: spec-catalog-exit-code-contract
description: spec_catalog mutators (archive/update/insert/delete/close/complete/bulk-update) use trap { exit 1 } + explicit exit 0 because _lib.ps1 sets Stop preference globally
metadata:
  type: project
---

`scripts/spec_catalog/_lib.ps1` sets `$ErrorActionPreference = 'Stop'` at module scope. Every script that dot-sources it inherits Stop preference, which means `Write-Error` becomes a terminating error. Historical `Write-Error "..." ; exit 1` blocks in the mutators were therefore unreachable — the script aborted at `Write-Error`, leaving `$LASTEXITCODE` unset.

**Why:** discovered 2026-05-21 when a batch /spec-arc wrapper around `archive.ps1` saw `$LASTEXITCODE` empty after every successful archive (and after every error), so the wrapper's `if ($LASTEXITCODE -ne 0) { throw }` check fired on success and skipped the dev-log step for all 72 archived specs.

**How to apply:** when writing or auditing any script in `scripts/spec_catalog/` that mutates the journal, both halves of the CLI exit-code contract must be explicit:
- top-of-script `trap { Write-Host $_ -ForegroundColor Red; exit 1 }` (covers Write-Error, throw, provider errors)
- explicit `exit 0` on every success-path return point

Scripts currently following this contract: `archive.ps1`, `update.ps1`, `insert.ps1`, `delete.ps1`, `close.ps1`, `complete.ps1`, `bulk-update.ps1`. Wrappers chaining these scripts may safely check `$LASTEXITCODE` after each `& script.ps1` call.

Wrapper-side: `& ./script.ps1` in PowerShell propagates `$LASTEXITCODE` only if the inner script called `exit`. If a script body finishes naturally without `exit`, `$LASTEXITCODE` retains its previous value — same trap (pun intended) hits any new mutator added without these two patterns.

See also: [[pwsh-bash-dollar-escape-trap]], [[pwsh-efficiency]].
