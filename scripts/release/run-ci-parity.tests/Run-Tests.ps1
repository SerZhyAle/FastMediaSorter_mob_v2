#requires -Version 7.0
# Subject: scripts/release/run-ci-parity.ps1
<#
.SYNOPSIS
    Contract suite for run-ci-parity.ps1: step selection, every step running after a red, exit mapping.

.DESCRIPTION
    The script is an orchestrator, so the suite drives it with a plan of trivial `pwsh -Command`
    steps through -PlanPath and a sandbox repository root. No gradle, no gate and no device runs.

.NOTES
    Exit codes:
      0 - every assertion passed
      1 - an assertion failed
      2 - the script under test is missing
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot '..\run-ci-parity.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Host "run-ci-parity tests: script under test missing: $script" -ForegroundColor Yellow
    exit 2
}

$sandbox = Join-Path ([IO.Path]::GetTempPath()) ("run-ci-parity-tests-{0}" -f $PID)
New-Item -ItemType Directory -Force -Path $sandbox | Out-Null

$failures = New-Object System.Collections.Generic.List[string]
function Assert-Case([string] $Name, [bool] $Condition, [string] $Detail = '') {
    if ($Condition) { Write-Host "  ok   $Name" } else { Write-Host "  FAIL $Name $Detail" -ForegroundColor Red; $failures.Add($Name) }
}

function Invoke-Parity([object[]] $Steps, [string[]] $Extra = @()) {
    $planPath = Join-Path $sandbox ('plan-{0}.json' -f [guid]::NewGuid().ToString('N'))
    ConvertTo-Json -InputObject @($Steps) -Depth 4 | Set-Content -LiteralPath $planPath -Encoding utf8
    $text = (& pwsh -NoProfile -File $script -RepoRoot $sandbox -PlanPath $planPath @Extra 2>&1 | Out-String)
    return @{ Code = $LASTEXITCODE; Text = $text }
}

function New-Step([string] $Name, [int] $Code) {
    return @{ name = $Name; ci = "job of $Name"; args = @('-Command', "exit $Code") }
}

function Get-RunLogText {
    $dir = Join-Path $sandbox 'temp/metrics/ci-parity-runs'
    $latest = Get-ChildItem -LiteralPath $dir -Filter '*.log' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
    if ($latest) { return Get-Content -LiteralPath $latest.FullName -Raw }
    return ''
}

try {
    $list = & pwsh -NoProfile -File $script -RepoRoot $sandbox -List 2>&1 | Out-String
    $listExit = $LASTEXITCODE
    Assert-Case 'list exits 0' ($listExit -eq 0) "exit $listExit"
    $names = 'static-gates', 'lint-rules', 'unit-wear', 'lint-wear', 'unit-phone', 'lint-phone'
    Assert-Case 'list names all six steps' (@($names | Where-Object { $list -notmatch [regex]::Escape($_) }).Count -eq 0) $list
    Assert-Case 'list runs nothing' (-not (Test-Path -LiteralPath (Join-Path $sandbox 'temp/metrics/ci-parity-runs')))

    $green = Invoke-Parity @((New-Step 'a' 0), (New-Step 'b' 0))
    Assert-Case 'all green exits 0' ($green.Code -eq 0) "exit $($green.Code)"
    Assert-Case 'all green says PASS 2 of 2' ($green.Text -match 'PASS \(2 of 2 steps\)') $green.Text

    $red = Invoke-Parity @((New-Step 'a' 1), (New-Step 'b' 0))
    Assert-Case 'a red step exits 1' ($red.Code -eq 1) "exit $($red.Code)"
    Assert-Case 'the step after a red still ran' ((Get-RunLogText) -match '===== b exit 0') (Get-RunLogText)
    Assert-Case 'the red is named in the verdict' ($red.Text -match 'step\(s\) red: a') $red.Text

    $blind = Invoke-Parity @((New-Step 'a' 2), (New-Step 'b' 0))
    Assert-Case 'a step that exits 2 alone gives 2' ($blind.Code -eq 2) "exit $($blind.Code)"

    $both = Invoke-Parity @((New-Step 'a' 2), (New-Step 'b' 3))
    Assert-Case 'a red wins over could-not-verify' ($both.Code -eq 1) "exit $($both.Code)"

    $subset = Invoke-Parity @((New-Step 'a' 1), (New-Step 'b' 0)) @('-Only', 'b')
    Assert-Case '-Only runs just the named step' ($subset.Code -eq 0 -and (Get-RunLogText) -notmatch '===== a ') "exit $($subset.Code)"

    $unknown = Invoke-Parity @((New-Step 'a' 0)) @('-Only', 'nosuch')
    Assert-Case 'an unknown step name gives 2' ($unknown.Code -eq 2) "exit $($unknown.Code)"

    $missingPlan = (& pwsh -NoProfile -File $script -RepoRoot $sandbox -PlanPath (Join-Path $sandbox 'absent.json') 2>&1 | Out-String)
    Assert-Case 'an unreadable plan gives 2' ($LASTEXITCODE -eq 2) $missingPlan
} finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures.Count -gt 0) {
    Write-Host ("run-ci-parity tests: FAIL - {0} case(s): {1}" -f $failures.Count, ($failures -join '; ')) -ForegroundColor Red
    exit 1
}
Write-Host 'run-ci-parity tests: PASS' -ForegroundColor Green
exit 0
