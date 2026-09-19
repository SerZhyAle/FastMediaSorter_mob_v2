#requires -Version 7.0
# Subject: scripts/quality/explain-last-failure.ps1
<#
.SYNOPSIS
    Regression suite for the failure explainer (S3288).

.DESCRIPTION
    This script is the one command a hook tells an agent to run, so its failure mode is not a crash -
    it is a plausible answer about the wrong row, which nothing contradicts. Every case here pins one
    property that could go wrong silently: which row is explained, that a repeat is called a repeat,
    that a coinciding gate verdict is named, and above all that the script cannot return 1. A 1 would
    be caught by the very hook that names it, and the agent would be told to explain the explanation.

    Both journals are fixtures. A suite reading the live ones would pass or fail by whatever ran on
    the host that hour, which is the opposite of a contract.

.NOTES
    Exit codes:
      0   all cases pass.
      1   at least one case failed.
      2   the fixtures could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$subject = Join-Path $repoRoot 'scripts/quality/explain-last-failure.ps1'
$scratch = Join-Path $repoRoot 'temp/scratch/S3288-explain-last-failure'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else { 'pwsh' }

$script:pass = 0
$script:fail = 0

function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
        $script:fail++
    }
}

function New-FailureRow([string]$Command, [int]$ExitCode, [int]$MinuteOffset, [string]$Tail = 'output', [string]$SessionId = 'session-a') {
    return ([ordered]@{
            timestampUtc = ([datetime]'2026-09-18T10:00:00Z').AddMinutes($MinuteOffset).ToString('o')
            sessionId    = $SessionId
            tool         = 'Bash'
            command      = $Command
            exitCode     = $ExitCode
            outputTail   = $Tail
            cwd          = 'P:/repo'
        } | ConvertTo-Json -Compress)
}

function New-GateRow([string]$Gate, [string]$Status, [int]$MinuteOffset) {
    return ([ordered]@{
            timestampUtc = ([datetime]'2026-09-18T10:00:00Z').AddMinutes($MinuteOffset).ToString('o')
            runner       = 'post-change'
            runId        = 'abc123def456'
            gate         = $Gate
            status       = $Status
            exitCode     = ($Status -eq 'FAIL' ? 1 : 0)
            elapsedMs    = 120
            findingPaths = @('scripts/quality/thing.ps1')
        } | ConvertTo-Json -Compress)
}

function Invoke-Subject([string[]]$Arguments) {
    $output = & $pwshExe -NoProfile -File $subject @Arguments 2>&1
    return [pscustomobject]@{
        Code = $LASTEXITCODE
        Text = (($output | ForEach-Object { [string]$_ }) -join "`n")
    }
}

try {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
    New-Item -ItemType Directory -Path $scratch -Force | Out-Null
}
catch {
    Write-Error "could not prepare the scratch directory: $_" -ErrorAction Continue
    exit 2
}

Write-Host "explain-last-failure.tests" -ForegroundColor Cyan

# --- An absent journal is an answer, not a failure -----------------------------------------------
$absent = Invoke-Subject @('-Journal', (Join-Path $scratch 'no-such.jsonl'))
Assert-That 'an absent journal exits 0' ($absent.Code -eq 0) "exit $($absent.Code)"
Assert-That 'an absent journal says there is nothing to explain' ($absent.Text -match 'nothing to explain') $absent.Text

# --- An empty journal is the same answer ---------------------------------------------------------
$emptyJournal = Join-Path $scratch 'empty.jsonl'
Set-Content -LiteralPath $emptyJournal -Value '' -Encoding utf8
$empty = Invoke-Subject @('-Journal', $emptyJournal)
Assert-That 'an empty journal exits 0' ($empty.Code -eq 0) "exit $($empty.Code)"

# --- One row is explained with its command and its code ------------------------------------------
$oneJournal = Join-Path $scratch 'one.jsonl'
Set-Content -LiteralPath $oneJournal -Encoding utf8 -Value @(
    (New-FailureRow 'pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action set' 2 5 'set-android-string: key not found')
)
$one = Invoke-Subject @('-Journal', $oneJournal)
Assert-That 'a single row exits 0' ($one.Code -eq 0) "exit $($one.Code)"
Assert-That 'the command is printed' ($one.Text -match 'set-android-string\.ps1') $one.Text
Assert-That 'the exit code is printed' ($one.Text -match 'exit 2') $one.Text
Assert-That 'what it printed last is shown' ($one.Text -match 'key not found') $one.Text
Assert-That 'a non-gate command gets no gate section' ($one.Text -notmatch 'which gate went red') $one.Text

# --- -Last 2 explains two rows -------------------------------------------------------------------
$twoJournal = Join-Path $scratch 'two.jsonl'
Set-Content -LiteralPath $twoJournal -Encoding utf8 -Value @(
    (New-FailureRow 'pwsh -NoProfile -File scripts/quality/assert-detekt.ps1' 1 1),
    (New-FailureRow 'pwsh -NoProfile -File scripts/utils/help.ps1' 1 2)
)
$two = Invoke-Subject @('-Journal', $twoJournal, '-Last', '2')
Assert-That '-Last 2 explains both rows' (
    $two.Text -match 'assert-detekt\.ps1' -and $two.Text -match 'help\.ps1'
) $two.Text

# --- A repeated command head is named as a repeat, with its count --------------------------------
$repeatJournal = Join-Path $scratch 'repeat.jsonl'
Set-Content -LiteralPath $repeatJournal -Encoding utf8 -Value @(
    (New-FailureRow 'pwsh -NoProfile -File scripts/quality/assert-detekt.ps1' 1 1),
    (New-FailureRow 'pwsh -NoProfile -File scripts/quality/assert-detekt.ps1' 1 2),
    (New-FailureRow 'pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Fix' 1 3)
)
$repeat = Invoke-Subject @('-Journal', $repeatJournal)
Assert-That 'a repeated command is reported as a repeat' ($repeat.Text -match 'REPEAT: 2 earlier failure') $repeat.Text

# --- A coinciding gate FAIL row is named ---------------------------------------------------------
$gateFailJournal = Join-Path $scratch 'gate-runner.jsonl'
Set-Content -LiteralPath $gateFailJournal -Encoding utf8 -Value @(
    (New-FailureRow 'pwsh -NoProfile -File scripts/post-change.ps1 -Files x.ps1' 1 10)
)
$gateTelemetry = Join-Path $scratch 'gate-executions.jsonl'
Set-Content -LiteralPath $gateTelemetry -Encoding utf8 -Value @(
    (New-GateRow 'suite-tracked' 'FAIL' 9),
    (New-GateRow 'neuroslop-gate' 'PASS' 9),
    (New-GateRow 'ancient-gate' 'FAIL' -600)
)
$gate = Invoke-Subject @('-Journal', $gateFailJournal, '-GateJournal', $gateTelemetry)
Assert-That 'a coinciding gate FAIL is named' ($gate.Text -match 'suite-tracked') $gate.Text
Assert-That 'a coinciding PASS is not reported as a failure' ($gate.Text -notmatch 'neuroslop-gate') $gate.Text
Assert-That 'a FAIL outside the window is not attributed' ($gate.Text -notmatch 'ancient-gate') $gate.Text
Assert-That 'the finding paths are carried through' ($gate.Text -match 'scripts/quality/thing\.ps1') $gate.Text

# --- An unreadable journal is could-not-verify, never a finding -----------------------------------
# A directory sitting on the journal's path passes Test-Path and fails every read.
$unreadable = Join-Path $scratch 'unreadable.jsonl'
New-Item -ItemType Directory -Path $unreadable -Force | Out-Null
$broken = Invoke-Subject @('-Journal', $unreadable)
Assert-That 'an unreadable journal exits 2' ($broken.Code -eq 2) "exit $($broken.Code)"

# --- The one code this script may never return ----------------------------------------------------
Assert-That 'no case returned exit 1' (
    @($absent, $empty, $one, $two, $repeat, $gate, $broken | Where-Object { $_.Code -eq 1 }).Count -eq 0
) 'a case returned 1, which the hook would catch and re-explain'

if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }

Write-Host ""
Write-Host ("explain-last-failure.tests: {0} passed, {1} failed." -f $script:pass, $script:fail) `
    -ForegroundColor ($script:fail -gt 0 ? 'Red' : 'Green')
exit ($script:fail -gt 0 ? 1 : 0)
