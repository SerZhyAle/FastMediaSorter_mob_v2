# Run-Tests.ps1 (S1070) - regression suite for scripts/quality/assert-exit-contract.ps1.
#
# The gate finds sites where a bare Write-Error under $ErrorActionPreference = 'Stop' raises a
# terminating error, so the `exit N` written after it never runs and the process reports 1 instead.
# Origin: the class regrew three times - S0082-era mutators, close-and-log.ps1 (S1063), and 18 sites
# across 9 scripts (S1070).
#
# Two things get asserted, because a gate that only ever goes green proves nothing:
#   * it FLAGS the defect (fixture 'bad'),
#   * it SPARES every legitimate shape it must not touch (cured / no-Stop / exit-1 / comment).
# Plus a live regression: the real scripts/ tree must stay clean.
#
# Hermetic: fixtures are written to a temp dir and removed in a finally block. The repo-scan case is
# read-only. Nothing under scripts/ is mutated.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-exit-contract.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gate = Join-Path $repoRoot 'scripts/quality/assert-exit-contract.ps1'
$sandbox = Join-Path $env:TEMP "assert-exit-contract.tests.$PID"

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function New-Fixture([string]$name, [string]$body) {
    $p = Join-Path $sandbox $name
    [System.IO.File]::WriteAllText($p, $body)
    return $p
}

function Invoke-Gate([string]$path) {
    & $pwshExe -NoProfile -File $gate -Gate -Path $path *> $null
    return $LASTEXITCODE
}

New-Item -ItemType Directory -Path $sandbox -Force | Out-Null
try {
    # --- A: the defect itself must be flagged. ---
    Write-Host 'A: a bare Write-Error before exit 2 under Stop is flagged' -ForegroundColor Yellow
    $bad = New-Fixture 'bad.ps1' @'
$ErrorActionPreference = 'Stop'
Write-Error "bad argument"
exit 2
'@
    Assert-That 'A1 gate exits 1 on the defect' ((Invoke-Gate $bad) -eq 1) 'expected 1'

    # --- B: the cured shape must be spared - otherwise the fix cannot pass its own gate. ---
    Write-Host 'B: -ErrorAction Continue is the cure and must not be flagged' -ForegroundColor Yellow
    $good = New-Fixture 'good.ps1' @'
$ErrorActionPreference = 'Stop'
Write-Error "bad argument" -ErrorAction Continue
exit 2
'@
    Assert-That 'B1 gate exits 0 on the cured shape' ((Invoke-Gate $good) -eq 0) 'expected 0'

    # --- C: without EAP=Stop, Write-Error never terminates, so the exit line is already reached. ---
    Write-Host 'C: a file not running under Stop is spared' -ForegroundColor Yellow
    $noStop = New-Fixture 'nostop.ps1' @'
Write-Error "bad argument"
exit 2
'@
    Assert-That 'C1 gate exits 0 without EAP=Stop' ((Invoke-Gate $noStop) -eq 0) 'expected 0'

    # --- D: exit 1 is excluded on purpose - the collapse also yields 1, so the site already
    # delivers its intended code and rewriting it would be pure noise. ---
    Write-Host 'D: an exit 1 site is not flagged (collapse delivers 1 anyway)' -ForegroundColor Yellow
    $exit1 = New-Fixture 'exit1.ps1' @'
$ErrorActionPreference = 'Stop'
Write-Error "substantive failure"
exit 1
'@
    Assert-That 'D1 gate exits 0 for an exit-1 site' ((Invoke-Gate $exit1) -eq 0) 'expected 0'

    # --- E: a header explaining this rule must not trip the gate that enforces it. ---
    Write-Host 'E: a comment describing the pattern is not a finding' -ForegroundColor Yellow
    $comment = New-Fixture 'comment.ps1' @'
$ErrorActionPreference = 'Stop'
# Never write: Write-Error "msg"
# followed by: exit 2
exit 0
'@
    Assert-That 'E1 gate exits 0 for a comment-only mention' ((Invoke-Gate $comment) -eq 0) 'expected 0'

    # --- F: distance - an exit far below an unrelated Write-Error is not the same statement. ---
    Write-Host 'F: an exit well past the lookahead is not attributed to the Write-Error' -ForegroundColor Yellow
    $far = New-Fixture 'far.ps1' @'
$ErrorActionPreference = 'Stop'
Write-Error "unrelated warning"
$a = 1
$b = 2
$c = 3
$d = 4
exit 2
'@
    Assert-That 'F1 gate exits 0 when the exit is out of reach' ((Invoke-Gate $far) -eq 0) 'expected 0'

    # --- G: the gate must not commit the sin it audits - its own refusal reaches exit 2. ---
    Write-Host 'G: the gate own contract - a missing scan root is exit 2, not 1' -ForegroundColor Yellow
    $missingExit = Invoke-Gate (Join-Path $sandbox 'no-such-file.ps1')
    Assert-That 'G1 gate exits 2 on a missing scan root' ($missingExit -eq 2) "expected 2, got $missingExit"

    # --- H: live regression - the real tree stays clean (all 18 S1070 sites cured). ---
    Write-Host 'H: the repository scripts/ tree has no unreachable exit site' -ForegroundColor Yellow
    & $pwshExe -NoProfile -File $gate -Gate -Quiet *> $null
    Assert-That 'H1 repo scan exits 0' ($LASTEXITCODE -eq 0) "expected 0, got $LASTEXITCODE"
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host 'sandbox removed' -ForegroundColor DarkGray
}

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "assert-exit-contract tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "assert-exit-contract tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
