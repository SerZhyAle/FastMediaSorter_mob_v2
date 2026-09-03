#requires -Version 7.0
<#
.SYNOPSIS
    S2453: a run time claimed in prose is judged against the telemetry journal that measures it.

.DESCRIPTION
    `docs/BUILD_TEST_FAST_PATH.md` states how long a gate batch takes, and CLAUDE.md Rule 6 turns
    that figure into a decision an agent makes on every call: foreground below 120 s, background
    above it. The figure is written by hand and never re-read. Measured 2026-09-03, the `a.ps1 fg`
    row had drifted about sevenfold - 18.9 s claimed against 142.8 s actual - and the divergence
    surfaced only because an unrelated investigation happened to time the target. The consequence
    is exactly what Rule 6 exists to prevent: the agent reads "about 20 s", runs the target in the
    foreground and is preempted by the tool's own timeout.

    The measurement already existed. `lib/gate-telemetry.ps1` journals every gate execution to
    temp/metrics/gate-executions.jsonl, and both batch runners call it. Nothing compared the two.

    JUDGES THE MEDIAN, NOT A RUN. A single execution says almost nothing: measured over 63
    fast-gate runs on 2026-09-03, `assert-no-ticket-logs.ps1` read p50 5701 ms, p90 9287 ms and a
    maximum of 25142 ms - a 4.4x spread on a gate with nothing wrong with it, produced by load
    from sibling sessions. So the statistic is the median over a 14-day window, the sample floor
    is 5 executions, and the failing ratio is 2.0 - above the observed p90/p50 of roughly 1.6 and
    far below the 7x drift this gate exists to catch.

    THE NUMBER STAYS IN THE PROSE. gate-timing-claims.json holds a document path and a regular
    expression, never a copy of the figure. Copying it would create a third source of truth and
    reproduce the same divergence one level down.

    AN ABSENT JOURNAL IS NOT A DEFECT. The journal lives under temp/, which CLAUDE.md Rule 1
    declares disposable, so a fresh clone and the release worktree legitimately have none. Those
    runs print `no telemetry` loudly and exit 0; a gate red on every clean checkout is a gate that
    gets switched off. A BROKEN ANCHOR is different and exits 2: the prose was reworded, so this
    check did not look, and "did not look" must never read as green.

.PARAMETER Gate
    Fail-closed: exit 1 when a claim has drifted. Without it the same finding is reported and the
    run exits 0. Exit 2 is unconditional - a check that could not look never answers green.

.PARAMETER Claims
    Claims map (default: scripts/quality/gate-timing-claims.json).

.PARAMETER TelemetryPath
    Gate telemetry journal (default: the path lib/gate-telemetry.ps1 writes).

.PARAMETER DocRoot
    Root the claims' `doc` paths resolve against (default: the repository root). Exists so the
    contract suite can point the gate at fixtures instead of this tree.

.PARAMETER WindowDays
    Age of the oldest execution considered (default: 14).

.PARAMETER MinSamples
    Executions required before a verdict is rendered at all (default: 5).

.PARAMETER Json
    Emit the per-claim result set as JSON instead of the human lines.

.PARAMETER Quiet
    Print the summary line only.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-gate-timing-claims.ps1 -Gate

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every claim agrees with its telemetry, or could not be judged for want of samples,
         or -Gate was absent.
      1  at least one claim has drifted past the tolerance, and -Gate was passed.
      2  cannot verify - the claims map is unreadable, a named document is absent, or a claim's
         anchor no longer matches exactly one line.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [string]$Claims,
    [string]$TelemetryPath,
    [string]$DocRoot,
    [ValidateRange(1, 365)]
    [int]$WindowDays = 14,
    [ValidateRange(1, 1000)]
    [int]$MinSamples = 5,
    [switch]$Json,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/gate-telemetry.ps1')

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

# Ratios, not free numbers: see .DESCRIPTION for the measurement each one comes from.
$failRatio = 2.0
$advisoryRatio = 0.5
$foregroundThresholdSeconds = 120.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$claimsPath = if ($Claims) { $Claims } else { Join-Path $PSScriptRoot 'gate-timing-claims.json' }
$journalPath = if ($TelemetryPath) { $TelemetryPath } else { Get-GateTelemetryPath }
$docRoot = if ($DocRoot) { $DocRoot } else { $repoRoot }

function Deny-Verify([string]$Message) {
    Write-Error "assert-gate-timing-claims: CANNOT VERIFY - $Message" -ErrorAction Continue
    exit 2
}

function Get-Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    $count = $sorted.Count
    if ($count -eq 0) { return 0.0 }
    if ($count % 2 -eq 1) { return [double]$sorted[[int](($count - 1) / 2)] }
    return ([double]$sorted[$count / 2 - 1] + [double]$sorted[$count / 2]) / 2.0
}

if (-not (Test-Path -LiteralPath $claimsPath -PathType Leaf)) {
    Deny-Verify "the claims map is absent: $claimsPath"
}
try {
    $claimSet = @((Get-Content -LiteralPath $claimsPath -Raw | ConvertFrom-Json).claims)
}
catch {
    Deny-Verify "the claims map is not readable JSON: $($_.Exception.Message)"
}
if ($claimSet.Count -eq 0) {
    Deny-Verify "the claims map names no claim: $claimsPath"
}

# One pass over the journal for every claim. Records outside the window are dropped here, so a
# journal that has grown for months costs one read rather than one per claim.
$cutoffUtc = [DateTime]::UtcNow.AddDays(-$WindowDays)
$byKey = @{}
if (Test-Path -LiteralPath $journalPath -PathType Leaf) {
    foreach ($line in [System.IO.File]::ReadLines($journalPath)) {
        if (-not $line) { continue }
        $record = try { $line | ConvertFrom-Json -ErrorAction Stop } catch { $null }
        if (-not $record) { continue }
        if ($record.status -eq 'SKIP') { continue }
        $stamp = try { [DateTime]::Parse($record.timestampUtc, $null, [System.Globalization.DateTimeStyles]::AdjustToUniversal) } catch { continue }
        if ($stamp -lt $cutoffUtc) { continue }
        $key = "$($record.runner)|$($record.gate)"
        if (-not $byKey.ContainsKey($key)) { $byKey[$key] = [System.Collections.Generic.List[double]]::new() }
        $byKey[$key].Add([double]$record.elapsedMs)
    }
}

$results = [System.Collections.Generic.List[object]]::new()

foreach ($claim in $claimSet) {
    $docPath = Join-Path $docRoot ([string]$claim.doc)
    if (-not (Test-Path -LiteralPath $docPath -PathType Leaf)) {
        Deny-Verify "claim '$($claim.id)' names a document that does not exist: $docPath"
    }

    $docText = Get-Content -LiteralPath $docPath -Raw
    $anchorMatches = [regex]::Matches($docText, [string]$claim.pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($anchorMatches.Count -ne 1) {
        Deny-Verify ("claim '$($claim.id)' matched $($anchorMatches.Count) line(s) in $($claim.doc), expected exactly 1. " +
            'The prose was reworded or duplicated: re-anchor the pattern in gate-timing-claims.json, ' +
            'or the figure it guards is unguarded from now on.')
    }
    if ($anchorMatches[0].Groups.Count -lt 2) {
        Deny-Verify "claim '$($claim.id)' has a pattern with no capture group for the seconds."
    }

    $claimedSeconds = [double]$anchorMatches[0].Groups[1].Value

    # The SECONDS always come out of the prose - that is the whole point. The foreground/background
    # verdict may be declared on the claim instead, because a sentence like Rule 6's states it as
    # the section it sits in rather than as a word next to the number. Declaring it copies a
    # binary, not a measurement, so it cannot drift the way a figure does.
    $promisedVerdict = ''
    if ($anchorMatches[0].Groups.Count -ge 3 -and $anchorMatches[0].Groups[2].Success) {
        $promisedVerdict = ([string]$anchorMatches[0].Groups[2].Value).ToLowerInvariant()
    }
    elseif ($claim.PSObject.Properties.Name -contains 'verdict') {
        $promisedVerdict = ([string]$claim.verdict).ToLowerInvariant()
    }
    else {
        Deny-Verify "claim '$($claim.id)' states no foreground/background verdict - neither a second capture group nor a 'verdict' field."
    }

    $key = "$($claim.runner)|$($claim.gate)"
    # Assigned in two statements, not through `if (..) { @() }`: an empty array returned from an
    # if-expression collapses to $null, and the next line would then read .Count off nothing.
    $samples = @()
    if ($byKey.ContainsKey($key)) { $samples = @($byKey[$key]) }

    if ($samples.Count -lt $MinSamples) {
        $results.Add([pscustomobject]@{
                Id             = [string]$claim.id
                Status         = 'NO-TELEMETRY'
                ClaimedSeconds = $claimedSeconds
                MedianSeconds  = $null
                Samples        = $samples.Count
                Reason         = "$($samples.Count) execution(s) of '$key' in the last $WindowDays day(s), below the floor of $MinSamples"
            })
        continue
    }

    $medianSeconds = [Math]::Round((Get-Median -Values $samples) / 1000.0, 1)
    $status = 'PASS'
    $reason = ''

    if ($promisedVerdict -eq 'foreground' -and $medianSeconds -gt $foregroundThresholdSeconds) {
        $status = 'FAIL'
        $reason = "the prose promises foreground, but the median is past the ${foregroundThresholdSeconds} s threshold - CLAUDE.md Rule 6 now sends the agent into a preempted call"
    }
    elseif ($medianSeconds -gt ($claimedSeconds * $failRatio)) {
        $status = 'FAIL'
        $reason = "the median is $([Math]::Round($medianSeconds / [Math]::Max($claimedSeconds, 0.1), 1))x the claimed figure (tolerance ${failRatio}x)"
    }
    elseif ($medianSeconds -lt ($claimedSeconds * $advisoryRatio)) {
        $status = 'ADVISORY'
        $reason = 'the prose is pessimistic - the target got faster and the document did not'
    }

    $results.Add([pscustomobject]@{
            Id             = [string]$claim.id
            Status         = $status
            ClaimedSeconds = $claimedSeconds
            MedianSeconds  = $medianSeconds
            Samples        = $samples.Count
            Reason         = $reason
        })
}

$failedResults = @($results | Where-Object { $_.Status -eq 'FAIL' })

if ($Json) {
    [ordered]@{
        status    = if ($failedResults.Count -gt 0) { 'fail' } else { 'pass' }
        source    = $journalPath
        window    = $WindowDays
        claims    = $results
    } | ConvertTo-Json -Depth 4
}
elseif (-not $Quiet) {
    foreach ($r in $results) {
        $actual = if ($null -eq $r.MedianSeconds) { 'not measured' } else { "$($r.MedianSeconds) s median of $($r.Samples) run(s)" }
        $color = switch ($r.Status) { 'FAIL' { 'Red' } 'ADVISORY' { 'Yellow' } 'NO-TELEMETRY' { 'Yellow' } default { 'Green' } }
        Write-Host ("  {0,-24} expected: {1} s | actual: {2}" -f $r.Id, $r.ClaimedSeconds, $actual) -ForegroundColor $color
        if ($r.Reason) { Write-Host ("      {0} - {1}" -f $r.Status, $r.Reason) -ForegroundColor $color }
    }
}

Write-Host ("assert-gate-timing-claims: expected: 0 | actual: {0} drifted claim(s) of {1}" -f $failedResults.Count, $results.Count)

if ($failedResults.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'assert-gate-timing-claims: PASS - every judged timing claim matches its telemetry.' -ForegroundColor Green
    }
    exit 0
}

if (-not $Quiet) {
    Write-Host ''
    Write-Host '  Fix: re-measure the target, write the fresh figure into the prose row, and date it.' -ForegroundColor Yellow
    Write-Host '  Do not widen the tolerance here - the number in the document is what the agent obeys.' -ForegroundColor Yellow
}

if ($Gate) {
    Write-Error ("assert-gate-timing-claims: FAIL - $($failedResults.Count) documented timing figure(s) no longer describe " +
        'this repository. An agent reads them to choose foreground or background, so a stale one costs a ' +
        'preempted call on every read until it is corrected.') -ErrorAction Continue
    exit 1
}

Write-Host 'assert-gate-timing-claims: reported without gating - pass -Gate to make a drifted claim fatal.' -ForegroundColor Yellow
exit 0
