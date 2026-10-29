#requires -Version 7.0
<#
.SYNOPSIS
    S4488: the one between-release check - the fast tier (checks of 5 s or less) over the files you changed.

.DESCRIPTION
    Between releases an agent runs no full build and no check over 5 s (canon TESTING_AND_QA section 2). This
    is "run the fast checks for this area" as a single command: it hands the changed set to
    post-change.ps1 at -Tier Fast with -GatesOnly, so every check that applies to the set and that
    scripts/quality/gate-tiers.json records at 5 s or less runs, nothing slower runs, and nothing is written
    (no dev-log row, no catalog sync). The verdict line names the release-tier checks it left for the
    release. The closure that DOES write the dev-log row is post-change.ps1 itself, which is the fast tier
    by default in an agent shell.

    What this does not prove: a compile, the unit suite, lint, a build. Those are the release run's
    (`FMS_TIER=Release`), and the report says "not built, not tested until the release".

.PARAMETER Files
    The changed paths, repo-relative; a comma-joined list is accepted (`pwsh -File` binds one element).

.PARAMETER ChangeType
    Doc | Script | Config | Tooling | Kotlin | Xml | Mixed. Derived from the extensions when omitted.

.PARAMETER Module
    app_v2 | wear. Derived from the paths when omitted (wear only when every path is under wear/).

.EXAMPLE
    pwsh -NoProfile -File ./a.ps1 ff -Files "app_v2/src/main/java/com/sza/fastmediasorter/Foo.kt"

.NOTES
    Exit codes: the verdict of post-change.ps1 unchanged - 0 every fast check that applies passed (the line
    names what was left for the release), 1 a fast check failed, 2 could not verify (no files, or a check
    could not run).
#>
[CmdletBinding()]
param(
    [Alias('File', 'ChangedFiles')]
    [string[]]$Files,
    [ValidateSet('Doc', 'Script', 'Config', 'Tooling', 'Kotlin', 'Xml', 'Mixed')]
    [string]$ChangeType,
    [ValidateSet('app_v2', 'wear')]
    [string]$Module
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$set = @($Files | ForEach-Object { ([string]$_) -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($set.Count -eq 0) {
    Write-Host 'fast-tier: COULD NOT VERIFY (no -Files given; name the paths you changed)' -ForegroundColor Red
    exit 2
}

if (-not $ChangeType) {
    $norm = @($set | ForEach-Object { $_ -replace '\\', '/' })
    $hasCode = @($norm | Where-Object { $_ -match '\.(kt|java)$' }).Count -gt 0
    $hasXml = @($norm | Where-Object { $_ -match '(^|/)src/.*\.xml$' }).Count -gt 0
    $hasScript = @($norm | Where-Object { $_ -match '\.(ps1|psm1|cjs|js|sh)$' }).Count -gt 0
    $hasOther = @($norm | Where-Object { $_ -notmatch '\.(kt|java|md|html?)$' -and $_ -notmatch '(^|/)src/.*\.xml$' }).Count -gt 0
    $ChangeType = if ($hasCode -and ($hasXml -or $hasScript -or $hasOther)) { 'Mixed' }
    elseif ($hasCode) { 'Kotlin' }
    elseif ($hasXml -and -not $hasScript -and -not $hasOther) { 'Xml' }
    elseif ($hasXml) { 'Mixed' }
    elseif ($hasScript -or $hasOther) { 'Tooling' }
    else { 'Doc' }
}
if (-not $Module) {
    $inWear = @($set | Where-Object { ($_ -replace '\\', '/') -match '^wear/' }).Count
    $Module = if ($inWear -gt 0 -and $inWear -eq $set.Count) { 'wear' } else { 'app_v2' }
}

# In-process: a nested interpreter would add its own start-up to a call whose whole budget is 5 s. post-change.ps1 ends with `exit`, which ends the script and leaves this process running.
& (Join-Path $root 'scripts/post-change.ps1') `
    -Files ($set -join ',') -ScopeToFile -Tier Fast -GatesOnly `
    -Target 'fast-tier' -Description 'fast tier over the changed set' `
    -ChangeType $ChangeType -Module $Module
exit $LASTEXITCODE
