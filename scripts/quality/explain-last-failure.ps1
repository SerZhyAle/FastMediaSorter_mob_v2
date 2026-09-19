#requires -Version 7.0
<#
.SYNOPSIS
    Explains the red verdict you just got - the one command the hook names (S3288).

.DESCRIPTION
    `.claude/hooks/observe-red-script-exit.ps1` fires when a .ps1 returns 1 or 2 and tells the agent
    that caused it to run this script before doing anything else. That is the whole design: the check
    belongs to the moment of the failure and to the agent standing in front of it, never to a digest
    somebody reads later - a report with no owner is read by nobody, which is the failure S3288 was
    written to end rather than to reproduce one level up.

    What it answers, in one screen:
      - what the failing invocation was, when it ran, and what it printed at the end;
      - whether the same command already failed earlier in this session, and how often;
      - for the three gate runners, WHICH gate went red and over which files, read from the gate
        telemetry journal that has carried that answer since long before anyone read it.

    This script never returns 1. It is itself a .ps1, so a 1 would be caught by the very hook that
    names it, and the agent would be told to explain the explanation. "Nothing to explain" is an
    answer, not a failure, and it exits 0.

.PARAMETER Last
    Explain the N most recent rows instead of one.

.PARAMETER Session
    Restrict to rows written by the current session.

.PARAMETER Journal
    Failure journal to read. Defaults to the one the writer library declares. For tests.

.PARAMETER GateJournal
    Gate telemetry journal to cross-reference. Defaults to the one the telemetry library declares.

.NOTES
    Exit codes:
      0   the question was answered - including "no failures recorded", which is an answer.
      2   a journal exists and could not be read.
#>
[CmdletBinding()]
param(
    [int]$Last = 1,
    [switch]$Session,
    [string]$Journal,
    [string]$GateJournal
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/tool-failure-journal.ps1')
. (Join-Path $PSScriptRoot 'lib/gate-telemetry.ps1')

# How far back a gate row may sit and still belong to the invocation that recorded the failure. The
# failure row is stamped when the command finished; its gates ran during the seconds or minutes
# before that, and post-change's own batch has measured up to 48.8 s.
$GateWindowMinutes = 15

# Only these three runners write to the gate journal, so only for these is a cross-reference an
# answer rather than an empty section.
$GateRunnerMarkers = @('post-change.ps1', 'assert-fast-gates.ps1', 'assert-release-scope-gates.ps1', 'a.ps1 fg')

function Read-TailLines([string]$Path, [int]$Count) {
    # Streamed rather than slurped: the gate journal measured 30 MB, and the answer lives at its end.
    $buffer = [System.Collections.Generic.Queue[string]]::new()
    foreach ($line in [System.IO.File]::ReadLines($Path)) {
        if ($line) {
            $buffer.Enqueue($line)
            if ($buffer.Count -gt $Count) { [void]$buffer.Dequeue() }
        }
    }
    return @($buffer.ToArray())
}

function Get-CommandHead([string]$Command) {
    if (-not $Command) { return '' }
    $token = ($Command -split '\s+') | Where-Object { $_ -like '*.ps1' } | Select-Object -First 1
    if ($token) { return ($token -replace '\\', '/') }
    return (($Command -split '\s+') | Select-Object -First 1)
}

$journalPath = if ($Journal) { $Journal } else { Get-ToolFailureJournalPath }

if (-not (Test-Path -LiteralPath $journalPath)) {
    Write-Host "explain-last-failure: no failures recorded yet - nothing to explain." -ForegroundColor Green
    exit 0
}

try {
    $rawRows = Read-TailLines $journalPath 500
}
catch {
    Write-Error "explain-last-failure: the failure journal exists and could not be read: $_" -ErrorAction Continue
    exit 2
}

$rows = @()
foreach ($raw in $rawRows) {
    try { $rows += ($raw | ConvertFrom-Json) } catch { continue }
}

if ($Session) {
    $current = [string]$env:CLAUDE_CODE_SESSION_ID
    $rows = @($rows | Where-Object { [string]$_.sessionId -eq $current })
}

if ($rows.Count -eq 0) {
    Write-Host "explain-last-failure: no failures recorded yet - nothing to explain." -ForegroundColor Green
    exit 0
}

$selected = @($rows | Select-Object -Last ([Math]::Max(1, $Last)))

$gateJournalPath = if ($GateJournal) { $GateJournal } else { Get-GateTelemetryPath }
$gateRows = $null

foreach ($row in $selected) {
    $head = Get-CommandHead ([string]$row.command)

    Write-Host ""
    Write-Host ("  {0}   exit {1}   [{2}]" -f $row.timestampUtc, $row.exitCode, $row.tool) -ForegroundColor Yellow
    Write-Host ("  {0}" -f $row.command) -ForegroundColor White
    if ($row.cwd) { Write-Host ("  in {0}" -f $row.cwd) -ForegroundColor DarkGray }

    if ($row.outputTail) {
        Write-Host "  --- what it printed last ---" -ForegroundColor DarkGray
        foreach ($line in ([string]$row.outputTail -split "`r?`n")) {
            if ($line) { Write-Host "  $line" -ForegroundColor Gray }
        }
    }

    # A repeat is the finding, not the row: the same command failing three times in one session means
    # the previous two answers were walked past.
    $earlier = @($rows | Where-Object {
            $_.timestampUtc -lt $row.timestampUtc -and
            (Get-CommandHead ([string]$_.command)) -eq $head -and
            [string]$_.sessionId -eq [string]$row.sessionId
        })
    if ($earlier.Count -gt 0) {
        Write-Host ("  REPEAT: {0} earlier failure(s) of {1} in this session." -f $earlier.Count, $head) `
            -ForegroundColor Red
    }

    $isGateRunner = $false
    foreach ($marker in $GateRunnerMarkers) {
        if ([string]$row.command -like "*$marker*") { $isGateRunner = $true; break }
    }

    if ($isGateRunner -and (Test-Path -LiteralPath $gateJournalPath)) {
        if ($null -eq $gateRows) {
            try { $gateRows = @(Read-TailLines $gateJournalPath 4000) } catch { $gateRows = @() }
        }

        $until = [datetime]::Parse($row.timestampUtc).ToUniversalTime()
        $since = $until.AddMinutes(-$GateWindowMinutes)
        $failures = @()

        foreach ($raw in $gateRows) {
            try { $gate = $raw | ConvertFrom-Json } catch { continue }
            if ([string]$gate.status -ne 'FAIL') { continue }
            $stamp = [datetime]::Parse([string]$gate.timestampUtc).ToUniversalTime()
            if ($stamp -ge $since -and $stamp -le $until.AddMinutes(1)) { $failures += $gate }
        }

        if ($failures.Count -gt 0) {
            Write-Host "  --- which gate went red ---" -ForegroundColor DarkGray
            foreach ($gate in $failures) {
                $paths = if ($gate.PSObject.Properties.Name -contains 'findingPaths' -and $gate.findingPaths) {
                    ' :: ' + (@($gate.findingPaths) -join ', ')
                }
                else { '' }
                Write-Host ("  {0}  exit {1}  run {2}{3}" -f $gate.gate, $gate.exitCode, $gate.runId, $paths) `
                    -ForegroundColor Red
            }
        }
    }
}

Write-Host ""
Write-Host "explain-last-failure: judge the verdict above before the next step." -ForegroundColor Cyan
exit 0
