<#
.SYNOPSIS
    S1706 - generated splash brand drawables vs their source.

.DESCRIPTION
    `ic_splash_app_brand.xml` is generated, never authored: the splash centre slot is a picture,
    so the wordmark and the slogan exist only as contours baked in from the strings and the
    template. A hand edit to one variant is therefore invisible - it compiles, it renders, and it
    silently diverges from every other locale, which strategic S1706 section 7 names as the live
    risk of the per-locale approach.

    This gate runs the generator in -Check mode for each module and fails when what is on disk is
    not what the current strings and template would produce. It writes nothing itself; the repair
    is always `scripts/utils/generate-splash-brand.ps1 -Module <m>`, which is the only writer.

    The two modules deliberately generate different compositions: the phone carries arrows,
    wordmark and slogan with one variant per locale, and the watch carries the arrows alone,
    because at watch size the text measured below the legible floor (S1706 step 05.4).

.PARAMETER Module
    Restrict the check to one module. Default: every module the generator supports.

.PARAMETER Gate
    Accepted for the fast-gate batch, which passes it to every gate. It changes nothing here:
    this gate has no advisory mode, because a divergence is always a defect - the file on disk
    is either what the generator produces or it was hand-edited.

.PARAMETER Quiet
    Suppress the per-module pass lines; findings are still printed.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-splash-brand-sync.ps1
    pwsh -NoProfile -File scripts/quality/assert-splash-brand-sync.ps1 -Module wear

.NOTES
    Exit codes:
      0  every generated drawable matches its source
      1  at least one drawable diverges, or a slogan no longer fits the mask circle
      2  could not verify: the generator is missing, or python/fontTools is unavailable
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,

    [switch]$Gate,

    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$generator = Join-Path $repoRoot 'scripts/utils/generate-splash-brand.ps1'

if (-not (Test-Path -LiteralPath $generator)) {
    Write-Error "assert-splash-brand-sync: generator not found: $generator" -ErrorAction Continue
    exit 2
}

$modules = if ($Module) { @($Module) } else { @('app_v2', 'wear') }
$diverged = @()
$unverifiable = @()

foreach ($m in $modules) {
    $output = & pwsh -NoProfile -File $generator -Module $m -Check 2>&1
    $code = $LASTEXITCODE
    switch ($code) {
        0 { if (-not $Quiet) { Write-Host "assert-splash-brand-sync: $m in sync" } }
        1 { $diverged += [pscustomobject]@{ Module = $m; Detail = ($output -join "`n") } }
        default { $unverifiable += [pscustomobject]@{ Module = $m; Detail = ($output -join "`n") } }
    }
}

foreach ($u in $unverifiable) {
    Write-Host "assert-splash-brand-sync: could not verify $($u.Module)" -ForegroundColor Yellow
    Write-Host $u.Detail
}
foreach ($d in $diverged) {
    Write-Host "assert-splash-brand-sync: $($d.Module) diverges from its source" -ForegroundColor Red
    Write-Host $d.Detail
}

# Divergence outranks an unverifiable module: a proven mismatch is a finding even when a second
# module could not be checked at all.
if ($diverged.Count -gt 0) {
    $names = ($diverged | ForEach-Object { $_.Module }) -join ', '
    Write-Error "assert-splash-brand-sync: hand-edited or stale splash drawables in $names - regenerate with scripts/utils/generate-splash-brand.ps1 -Module <m>" -ErrorAction Continue
    exit 1
}
if ($unverifiable.Count -gt 0) {
    $names = ($unverifiable | ForEach-Object { $_.Module }) -join ', '
    Write-Error "assert-splash-brand-sync: could not verify $names - python with fontTools is required" -ErrorAction Continue
    exit 2
}

if (-not $Quiet) { Write-Host 'assert-splash-brand-sync: PASS (every generated splash drawable matches its source).' }
exit 0
