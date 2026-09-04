#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for assert-always-loaded-budget.ps1 (S2517 ratchet, S2521 rules checks).

.DESCRIPTION
    Builds a throwaway tree under temp/scratch and points the gate at it with -RepoRoot, so the
    failure paths run without touching the live rules files every concurrent session reads.

    Cases:
      C1 a tree within budget passes
      C2 a file over its ceiling fails under -Gate and names the destination
      C3 HTML comments are not counted - a file whose bytes exceed the ceiling only inside a
         comment passes
      C4 a .claude/rules file WITHOUT paths: that the baseline does not list fails
      C5 a .claude/rules file whose paths prefix does not exist fails
      C6 a .claude/rules file with a valid paths: list is accepted
      C7 -UpdateBaseline refuses to raise a ceiling
      C8 -UpdateBaseline lowers a ceiling the tree beats

    Exit codes (CLAUDE.md Rule 7):
      0  every case passed
      1  at least one case failed
      2  could not run - gate script missing
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$gate = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-always-loaded-budget.ps1'
if (-not (Test-Path -LiteralPath $gate)) {
    Write-Error "gate not found: $gate" -ErrorAction Continue
    exit 2
}
$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$sandbox = Join-Path $repoRoot ('temp/scratch/always-loaded-budget-tests-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$pwshExe = (Get-Process -Id $PID).Path
$script:pass = 0
$script:fail = 0

function Reset-Sandbox {
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $sandbox '.claude/rules') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $sandbox 'PLAN') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $sandbox 'PLAN/x.md') -Value 'x'
}

function Write-Page([string]$rel, [int]$bytes, [string]$comment = '') {
    $body = ('a' * $bytes) + $comment
    [System.IO.File]::WriteAllText((Join-Path $sandbox $rel), $body, [System.Text.UTF8Encoding]::new($false))
}

function Write-Baseline([string]$lines) {
    $p = Join-Path $sandbox 'baseline.txt'
    [System.IO.File]::WriteAllText($p, $lines, [System.Text.UTF8Encoding]::new($false))
    return $p
}

function Invoke-Gate([string[]]$extra) {
    $out = & $pwshExe -NoProfile -File $gate -RepoRoot $sandbox -BaselineFile (Join-Path $sandbox 'baseline.txt') @extra 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Out = (($out | ForEach-Object { [string]$_ }) -join "`n") }
}

function Assert([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { Write-Host "  PASS  $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL  $name - $detail" -ForegroundColor Red; $script:fail++ }
}

Write-Host "assert-always-loaded-budget contract (sandbox: $sandbox)"

# C1
Reset-Sandbox
Write-Page 'CLAUDE.md' 100
[void](Write-Baseline "CLAUDE.md|200|200|somewhere`n")
$r = Invoke-Gate @('-Gate')
Assert 'C1 within budget passes' ($r.Code -eq 0) "exit $($r.Code): $($r.Out)"

# C2
Reset-Sandbox
Write-Page 'CLAUDE.md' 300
[void](Write-Baseline "CLAUDE.md|200|200|the refusal text of the gate`n")
$r = Invoke-Gate @('-Gate')
Assert 'C2 over ceiling fails and names the destination' ($r.Code -eq 1 -and $r.Out -match 'the refusal text of the gate' -and $r.Out -match 'paths:') "exit $($r.Code): $($r.Out)"

# C3
Reset-Sandbox
Write-Page 'CLAUDE.md' 150 ("<!-- " + ('c' * 500) + " -->")
[void](Write-Baseline "CLAUDE.md|200|200|somewhere`n")
$r = Invoke-Gate @('-Gate')
Assert 'C3 HTML comment bytes are not counted' ($r.Code -eq 0) "exit $($r.Code): $($r.Out)"

# C4
Reset-Sandbox
Write-Page 'CLAUDE.md' 100
[void](Write-Baseline "CLAUDE.md|200|200|somewhere`n")
Set-Content -LiteralPath (Join-Path $sandbox '.claude/rules/unscoped.md') -Value "# always loaded`nbody"
$r = Invoke-Gate @('-Gate')
Assert 'C4 rules file without paths: not in baseline fails' ($r.Code -eq 1 -and $r.Out -match 'unscoped\.md' -and $r.Out -match "no 'paths:'") "exit $($r.Code): $($r.Out)"

# C5
Reset-Sandbox
Write-Page 'CLAUDE.md' 100
[void](Write-Baseline "CLAUDE.md|200|200|somewhere`n")
Set-Content -LiteralPath (Join-Path $sandbox '.claude/rules/dead.md') -Value "---`npaths:`n  - `"PLAM/**`"`n---`nbody"
$r = Invoke-Gate @('-Gate')
Assert 'C5 rules file with a dead paths prefix fails' ($r.Code -eq 1 -and $r.Out -match 'PLAM' -and $r.Out -match 'never load') "exit $($r.Code): $($r.Out)"

# C6
Reset-Sandbox
Write-Page 'CLAUDE.md' 100
[void](Write-Baseline "CLAUDE.md|200|200|somewhere`n")
Set-Content -LiteralPath (Join-Path $sandbox '.claude/rules/good.md') -Value "---`npaths:`n  - `"PLAN/**`"`n  - `"CLAUDE.md`"`n---`nbody"
$r = Invoke-Gate @('-Gate')
Assert 'C6 rules file with valid paths: is accepted' ($r.Code -eq 0) "exit $($r.Code): $($r.Out)"

# C7
Reset-Sandbox
Write-Page 'CLAUDE.md' 300
[void](Write-Baseline "CLAUDE.md|200|200|somewhere`n")
$r = Invoke-Gate @('-UpdateBaseline')
Assert 'C7 -UpdateBaseline refuses to raise' ($r.Code -eq 1 -and $r.Out -match 'refusing to RAISE') "exit $($r.Code): $($r.Out)"

# C8
Reset-Sandbox
Write-Page 'CLAUDE.md' 100
$bl = Write-Baseline "CLAUDE.md|200|200|somewhere`n"
$r = Invoke-Gate @('-UpdateBaseline')
$after = [System.IO.File]::ReadAllText($bl)
Assert 'C8 -UpdateBaseline lowers a beaten ceiling' ($r.Code -eq 0 -and $after -match 'CLAUDE\.md\|100\|') "exit $($r.Code): $after"

if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
Write-Host ("assert-always-loaded-budget tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
