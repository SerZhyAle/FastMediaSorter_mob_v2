#requires -Version 7.0
<#
.SYNOPSIS
    Context bound for an unattended runner child (S4264): at a /spec-all stage boundary or a
    /spec-dev phase boundary, decide whether the child stops and hands the ticket back.

.DESCRIPTION
    Reads the carried context with `spec-next-session.ps1 -Verb CheckContext`. Under the threshold
    the child carries on. At or above it the script writes the handoff marker
    `temp/<Id>/context-handoff.json` and exits 3; the child then releases its lease, prints its one
    final line and ends, and the queue runner relaunches the same ticket in a fresh process.

    A `phase` boundary leaves the status unchanged, so a runner that does not know the marker would
    file the stop as an idle run, and two in a row take the ticket out of automatic ranking. The
    phase stop is therefore taken only when the RESOLVED runner script carries the `context-handoff`
    token; otherwise the script exits 0 with a note and the child carries on. A `stage` boundary
    follows a status move, which every runner already reads as progress.

    Called only from runner children - the rule lives in `.claude/runner/silent-mode.md`, which no
    interactive session loads.

.PARAMETER Id
    The ticket the child is running.

.PARAMETER Kind
    `stage` (a /spec-all stage just flipped the status) or `phase` (a /spec-dev phase just ended).

.PARAMETER Boundary
    Free text naming the boundary, stored in the marker for the run journal reader.

.PARAMETER Threshold
    Token threshold; omitted, CheckContext applies its own default (300000).

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/check-child-context.ps1 -Id S4264 -Kind phase -Boundary "phase 02 done"

Exit codes:
    0 - continue: under the threshold, or a phase crossing the resolved runner cannot relaunch.
    2 - cannot verify: bad arguments, or the context could not be read; the child continues.
    3 - stop: threshold crossed, marker written.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Id,
    [Parameter(Mandatory)][ValidateSet('stage', 'phase')][string]$Kind,
    [string]$Boundary = '',
    [int]$Threshold = 0
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if ($Id -notmatch '^S\d{4}$') {
    Write-Error "check-child-context: -Id must match S#### (got '$Id')" -ErrorAction Continue
    exit 2
}

$sessionScript = Join-Path $repoRoot 'scripts/spec_catalog/spec-next-session.ps1'
$checkArgs = @('-NoProfile', '-File', $sessionScript, '-Verb', 'CheckContext')
if ($Threshold -gt 0) { $checkArgs += @('-Threshold', $Threshold) }
$raw = & pwsh @checkArgs 2>&1
$checkExit = $LASTEXITCODE

if ($checkExit -notin 0, 3) {
    Write-Output "check-child-context: cannot verify ($(($raw | Out-String).Trim())) - continue"
    exit 2
}

$reading = $null
try {
    $reading = ($raw | Where-Object { "$_" -match '^\{' } | Select-Object -Last 1) | ConvertFrom-Json
} catch {
    Write-Output "check-child-context: unreadable CheckContext output - continue"
    exit 2
}
if ($null -eq $reading) {
    Write-Output "check-child-context: no CheckContext reading - continue"
    exit 2
}

if ($checkExit -eq 0) {
    Write-Output ("check-child-context: {0} of {1} tokens - continue" -f $reading.tokens, $reading.threshold)
    exit 0
}

if ($Kind -eq 'phase') {
    # The token is the runner's own outcome name; its absence means the deployed runner would file
    # this stop as an idle run.
    $runnerKnows = $false
    try {
        . (Join-Path $repoRoot 'scripts/spec_catalog/_status-sets.ps1')
        $runnerPath = Get-SzaHarnessScript 'batch/run-spec-queue.ps1'
        $runnerKnows = Select-String -LiteralPath $runnerPath -SimpleMatch 'context-handoff' -Quiet
    } catch {
        $runnerKnows = $false
    }
    if (-not $runnerKnows) {
        Write-Output ("check-child-context: {0} of {1} tokens, but the resolved runner cannot relaunch a phase stop - continue" -f `
                $reading.tokens, $reading.threshold)
        exit 0
    }
}

$markerDir = Join-Path $repoRoot (Join-Path 'temp' $Id)
New-Item -ItemType Directory -Force -Path $markerDir | Out-Null
$marker = Join-Path $markerDir 'context-handoff.json'
[pscustomobject]@{
    id        = $Id
    kind      = $Kind
    boundary  = $Boundary
    tokens    = $reading.tokens
    threshold = $reading.threshold
    at        = (Get-Date).ToString('s')
} | ConvertTo-Json -Compress | Set-Content -LiteralPath $marker -Encoding UTF8

Write-Output ("check-child-context: STOP - {0} of {1} tokens at {2} boundary '{3}'; marker {4}" -f `
        $reading.tokens, $reading.threshold, $Kind, $Boundary, $marker)
exit 3
