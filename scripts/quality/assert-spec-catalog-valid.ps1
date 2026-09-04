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

if (-not (Test-Path $validate)) {
    Write-Error "validate.ps1 not found at $validate" -ErrorAction Continue
    exit 2
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
