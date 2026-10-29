#requires -Version 7.0
<#
.SYNOPSIS
    S4488: the release pre-flight half of the closure battery - every per-ticket gate, at the release tier,
    over everything that changed since the last release.

.DESCRIPTION
    Between releases post-change.ps1 runs only the fast tier (checks of 5 s or less, scripts/quality/
    gate-tiers.json), so the slower per-ticket gates - detekt over the module, the script-suite
    regression, the string audits, the contract gates - no longer run once per ticket. They run here,
    once, over the union of the files the release carries, so nothing that ran before is gone from the
    release path. It is the release counterpart of `a.ps1 ff`.

    The changed set is `git diff --name-only <last v* release tag>..HEAD` plus the files the working tree
    still carries, de-duplicated, existing paths only. The gates run through post-change.ps1 -Tier Release
    -GatesOnly with no -ScopeToFile, which is the project-wide judgement CLAUDE.md section 12 asks of a
    release/CI closure; -GatesOnly writes no dev-log row and no catalog sync.

    Placement: release scope by Rule 33 (scripts/quality/gate-placement.jsonl); run by
    assert-release-scope-gates.ps1, step 0.4 of /spec-prerelease.

.PARAMETER Gate
    Accepted for the release-scope runner's calling convention; the verdict is the exit code either way.

.PARAMETER Quiet
    Accepted for the same reason; the child's own output is always streamed.

.PARAMETER BaseRef
    Compare against this ref instead of the newest v* release tag.

.NOTES
    Exit codes: 0 every release-tier gate that applies to the set passed; 1 a gate failed; 2 could not
    verify (no release tag to compare against, no files, or post-change.ps1 could not run).
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [string]$BaseRef
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:FMS_TIER = 'Release'
Write-CheckSubject ([ordered]@{ scope = 'release-diff'; tier = 'Release'; mode = 'closure-gates' })

Push-Location $root
try {
    if (-not $BaseRef) {
        $BaseRef = (& git tag --list 'v*' --sort=-creatordate 2>$null | Select-Object -First 1)
    }
    if (-not $BaseRef) {
        Write-Host 'assert-release-tier-closure: COULD NOT VERIFY (no v* release tag to compare against; pass -BaseRef)' -ForegroundColor Red
        exit 2
    }
    $committed = @(& git diff --name-only "$BaseRef..HEAD" 2>$null)
    $dirty = @(& git diff --name-only HEAD 2>$null)
    $set = @($committed + $dirty | ForEach-Object { "$_".Trim() } | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } | Sort-Object -Unique)
}
finally { Pop-Location }

if ($set.Count -eq 0) {
    Write-Host "assert-release-tier-closure: COULD NOT VERIFY (no changed file since $BaseRef)" -ForegroundColor Red
    exit 2
}

Write-Host "assert-release-tier-closure: $($set.Count) file(s) changed since $BaseRef, judged at the release tier"
# In-process, with the set as an array: a release diff can hold well over a thousand paths, and a comma-joined
# argument would meet the 32 767-character command-line ceiling of a child process.
& (Join-Path $root 'scripts/post-change.ps1') `
    -Files $set -Tier Release -GatesOnly -NoReuse `
    -Target 'release-tier' -Description "release pre-flight over $($set.Count) changed file(s) since $BaseRef" `
    -ChangeType Mixed -Module app_v2
exit $LASTEXITCODE
