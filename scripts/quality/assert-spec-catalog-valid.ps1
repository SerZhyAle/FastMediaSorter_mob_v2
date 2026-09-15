#requires -Version 7.0
<#
.SYNOPSIS
    Guards spec catalog integrity and file mapping (S2549).

.DESCRIPTION
    Delegates to scripts/spec_catalog/validate.ps1:
      - Schema parsing
      - Id uniqueness & monotonicity
      - FS->Journal (every spec file on disk has a catalog record)
      - Journal->FS (every non-archived catalog record points at an existing file)
      - File naming and priority ranges

    Exit codes:
      0 - clean.
      1 - validation failure.
      2 - validate.ps1 missing.

.PARAMETER Gate
    Fail-closed: exit 1 on validation failure.

.PARAMETER Quiet
    Print only summary.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-spec-catalog-valid.ps1
    pwsh -NoProfile -File scripts/quality/assert-spec-catalog-valid.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$validate = Join-Path $repoRoot 'scripts/spec_catalog/validate.ps1'

. (Join-Path $PSScriptRoot 'lib/absent-input.ps1')

if (-not (Test-Path $validate)) {
    Write-Error "validate.ps1 not found at $validate" -ErrorAction Continue
    exit 2
}

# S3075: asked BEFORE the delegate runs. validate.ps1 reports a missing journal as a validation
# failure, which is the right answer for a workstation and the wrong one anywhere PLAN/ is simply
# not published - and by then the verdict is already 1 and indistinguishable from a real finding.
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'))) {
    Exit-InputAbsent -Gate 'assert-spec-catalog-valid' -Path 'PLAN/spec-catalog.jsonl' `
        -Reason 'PLAN/ is gitignored - present only on a workstation checkout'
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
& $pwshExe -NoProfile -File $validate
$validOk = ($LASTEXITCODE -eq 0)

if (-not $validOk) {
    if (-not $Quiet) { Write-Host "assert-spec-catalog-valid: spec catalog validation FAILED" -ForegroundColor Red }
    if ($Gate) { exit 1 }
} else {
    if (-not $Quiet) { Write-Host "assert-spec-catalog-valid: PASS (spec catalog structure and file mapping valid)." -ForegroundColor Green }
}

exit $(if ($validOk) { 0 } else { 1 })
