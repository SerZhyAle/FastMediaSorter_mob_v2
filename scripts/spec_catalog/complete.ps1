[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id,
    [ValidateSet('Verified','Archived')]
    [string] $Status = 'Verified',
    [switch] $SkipValidate
)

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }

$record = Find-Record -Id $Id
if (-not $record) {
    Write-Error "Record '$Id' not found."
    exit 1
}
if ($record.status -match '^Block') {
    Write-Error "Unblock first: update.ps1 -Id $Id -Status <previous>"
    exit 1
}
if ($record.status -in @('Verified','Archived')) {
    Write-Error "$Id is already $($record.status)."
    exit 1
}

Write-Host "=== complete.ps1 $Id ===" -ForegroundColor Cyan

# 1. Catalog validation
if (-not $SkipValidate) {
    Write-Host "[1/3] catalog validate..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot 'validate.ps1')
    $validateExit = $LASTEXITCODE
    if ($validateExit -ne 0) {
        Write-Warning "validate.ps1 exited $validateExit - check output above. Continue anyway (pre-existing failures are common). Use -SkipValidate to suppress this step."
    } else {
        Write-Host "      OK" -ForegroundColor Green
    }
} else {
    Write-Host "[1/3] catalog validate... SKIPPED" -ForegroundColor DarkGray
}

# 2. Dev-log check
Write-Host "[2/3] dev log check..." -ForegroundColor Cyan
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$changelogPath = Join-Path $repoRoot 'dev\CHANGELOG.md'
if (Test-Path $changelogPath) {
    $hits = (Select-String -LiteralPath $changelogPath -Pattern ([regex]::Escape($Id)) -SimpleMatch).Count
    if ($hits -eq 0) {
        Write-Warning "No dev/CHANGELOG.md entry found for $Id. Run add_to_dev_log.ps1 before closing."
    } else {
        Write-Host ("      {0} entr{1} found" -f $hits, $(if ($hits -eq 1) {'y'} else {'ies'})) -ForegroundColor Green
    }
} else {
    Write-Warning "dev/CHANGELOG.md not found - skipping dev-log check."
}

# 3. Close
Write-Host "[3/3] closing $Id -> $Status..." -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'close.ps1') -Id $Id -Status $Status
