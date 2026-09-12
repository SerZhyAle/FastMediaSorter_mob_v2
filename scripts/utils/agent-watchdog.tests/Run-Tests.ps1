#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/utils/agent-watchdog.ps1.

.DESCRIPTION
    The watchdog kills processes on the owner's personal workstation, so the property worth testing
    is not that it reaps correctly - that needs a real jam - but that it CANNOT act when told not to,
    and that it refuses arguments which would make it trigger-happy. A dry run that killed something
    would be the one failure nobody could undo.

Exit codes: 0 every test passed, 1 at least one failed.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Three levels up, not two: this file sits in scripts/utils/agent-watchdog.tests/.
$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$script:watchdog = Join-Path $repoRoot 'scripts/utils/agent-watchdog.ps1'
$script:failures = 0

function Assert-That {
    param([string] $Name, [bool] $Condition, [string] $Detail = '')
    if ($Condition) {
        Write-Output "  PASS  $Name"
    } else {
        Write-Output "  FAIL  $Name$(if ($Detail) { " - $Detail" })"
        $script:failures++
    }
}

Write-Output 'agent-watchdog contract'

# 1 - the file exists and documents its exit codes, per the reachable-exit-code contract.
$body = Get-Content -LiteralPath $script:watchdog -Raw
Assert-That 'script exists' (Test-Path -LiteralPath $script:watchdog)
# (?s) so the match spans the wrapped header line - without it the assertion fails on formatting
# rather than on the contract it means to check.
Assert-That 'header declares its exit codes' ($body -match '(?s)Exit codes:\s*0 .*\b2\b.*\b3\b')

# 2 - out-of-range arguments are refused rather than silently clamped. An interval of one second
#     would sample every process on the box continuously; a stall window of zero would call a build
#     that just started a stall and kill it.
foreach ($case in @(
        @{ Args = @('-IntervalSeconds', '1'); Name = 'refuses a 1s interval' },
        @{ Args = @('-SampleSeconds', '0'); Name = 'refuses a 0s sample' },
        @{ Args = @('-StallMinutes', '0'); Name = 'refuses a 0m stall window' }
    )) {
    $null = & pwsh -NoProfile -File $script:watchdog @($case.Args + @('-MaxPasses', '1', '-WhatIf')) 2>&1
    Assert-That $case.Name ($LASTEXITCODE -eq 3) "exit was $LASTEXITCODE, expected 3"
}

# 3 - a dry run completes, reports itself as a dry run, and kills nothing. The log is the evidence:
#     a real kill writes KILLED, a dry run writes WOULD KILL, and the two are never both correct.
$logFile = Join-Path $repoRoot 'temp/scratch/watchdog/watchdog.log'
$before = if (Test-Path -LiteralPath $logFile) { (Get-Item -LiteralPath $logFile).Length } else { 0 }

$output = & pwsh -NoProfile -File $script:watchdog -MaxPasses 1 -WhatIf 2>&1
Assert-That 'dry run exits 0' ($LASTEXITCODE -eq 0) "exit was $LASTEXITCODE"
Assert-That 'dry run announces itself' (($output -join "`n") -match 'whatIf=True')

$newText = if (Test-Path -LiteralPath $logFile) {
    $all = Get-Content -LiteralPath $logFile -Raw
    if ($before -gt 0 -and $all.Length -gt $before) { $all.Substring($before) } else { $all }
} else { '' }
Assert-That 'dry run killed nothing' ($newText -notmatch '^\S+ \S+\s+KILLED ')
Assert-That 'dry run dropped no ticket' ($newText -notmatch '^\S+ \S+\s+DROPPED ')
Assert-That 'dry run started no runner' ($newText -notmatch '^\S+ \S+\s+STARTED runner')

# 4 - the machinery predicate must exclude the three process families that look like targets and are
#     not: the IDE's language server, a Claude session, and the watchdog itself. This is asserted
#     against the source because the function is script-scoped by design - the script must not be
#     dot-sourceable, or importing it would start the supervision loop.
Assert-That 'excludes claude, language-server and itself' ($body -match "agent-watchdog\|claude\\.exe\|language-server")
Assert-That 'gradle daemons are exempt from the orphan rule' ($body -match 'isDaemon.*continue|if \(\$isDaemon\) \{ continue \}')

# 5 - the correction that cost 24 live builds. A gradle daemon detaches, so it is never inside the
#     lock holder's descendant tree; judging the tree alone reads a healthy build as a stall. The
#     reaper must consult daemon activity before it kills anything, and the stall window must not be
#     tight enough to fire during daemon startup.
Assert-That 'stall reaper consults gradle daemon activity' ($body -match 'Test-AnyGradleDaemonBusy')
Assert-That 'stall reaper bails out when a daemon is busy' ($body -match 'NOT A STALL')
$stallDefault = if ($body -match '\[int\]\s*\$StallMinutes\s*=\s*(\d+)') { [int]$Matches[1] } else { 0 }
Assert-That 'stall window leaves room for daemon startup' ($stallDefault -ge 20) "default is $stallDefault"

Write-Output ''
if ($script:failures -gt 0) {
    Write-Output "agent-watchdog contract: expected: 0 | actual: $($script:failures) failure(s)"
    exit 1
}
Write-Output 'agent-watchdog contract: expected: 0 | actual: 0 failure(s)'
exit 0
