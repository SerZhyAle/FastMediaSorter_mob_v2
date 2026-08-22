#requires -Version 7.0
<#
.SYNOPSIS
    Gate: a repository script says what it does and which codes it returns (S1872).

.DESCRIPTION
    The generated inventory is a parameter dump. Measured 2026-08-21 across 376 scripts: 290 carry
    no .SYNOPSIS at all, and of the 263 that declare an `exit N`, 158 document no exit codes. The
    cheatsheet still lists every one of them - name, path, parameters - so the document looks
    complete while explaining nothing, and a reader has to open the file to learn its purpose.

    CLAUDE.md Rule 7 already requires a header to list the codes it returns. Nothing enforced it:
    assert-exit-contract.ps1 judges whether a documented code is REACHABLE, whether a script is
    silent, and whether a non-zero exit prints a reason - all three pass on a file whose header
    describes nothing.

    TWO FINDINGS, TWO CEILINGS. They are counted apart because they decay at different speeds and a
    single number would let one hide behind the other:

      - UNDESCRIBED: no .SYNOPSIS in the comment-based help.
      - UNDOCUMENTED EXIT: the script declares `exit N` but its help carries no "Exit codes:" block.
        A script that never exits is not asked for one - a dot-sourced library returns, it does not
        exit, and demanding an exit contract from it would be noise.

    RATCHETED, NOT RETROACTIVE. Both ceilings start at the measured debt and may only fall. A new
    script must be described on the day it is written; the existing 290 are cleared by whichever
    ticket next touches them. A ceiling set by assertion rather than by measurement is the kind that
    gets disabled the first time it blocks someone else's ticket.

.PARAMETER Gate
    Accepted for the fast-gate batch's uniform call shape; judging is already the default.

.PARAMETER Report
    List every offending script and exit 0 regardless of the ceilings. Use when deciding.

.PARAMETER Quiet
    Print the verdict line only.

.PARAMETER RepoRoot
    Repository root. Defaults to the directory two levels above this script.

.NOTES
    Exit codes:
      0 - both counts at or below their ceilings, or -Report was given
      1 - either count rose above its ceiling
      2 - cannot verify: a script root or a baseline file is missing or unreadable
#>
[CmdletBinding()]
param(
    [switch] $Gate,
    [switch] $Report,
    [switch] $Quiet,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\utils\script-help-text.ps1')

$scriptRoots = @('scripts', 'dev/CATALOG/scripts', 'dev/ACTIVITY_CATALOG/scripts')
$rootScripts = @('a.ps1')
$baselinePath = Join-Path $PSScriptRoot 'script-described-baseline.txt'

$files = New-Object System.Collections.Generic.List[System.IO.FileInfo]
foreach ($root in $scriptRoots) {
    $full = Join-Path $RepoRoot ($root -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $full)) {
        Write-Host "assert-script-described: cannot verify - script root missing: $root" -ForegroundColor Yellow
        exit 2
    }
    foreach ($f in Get-ChildItem -LiteralPath $full -Recurse -File -Filter *.ps1) {
        if ($f.FullName -match '(^|[\\/])node_modules([\\/]|$)') { continue }
        $files.Add($f)
    }
}
foreach ($name in $rootScripts) {
    $full = Join-Path $RepoRoot $name
    if (Test-Path -LiteralPath $full) { $files.Add((Get-Item -LiteralPath $full)) }
}

$undescribed = New-Object System.Collections.Generic.List[string]
$undocumentedExit = New-Object System.Collections.Generic.List[string]

foreach ($f in $files) {
    $rel = $f.FullName.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')
    $text = [IO.File]::ReadAllText($f.FullName)
    $parseErrors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($f.FullName, [ref]$null, [ref]$parseErrors)

    # Both readers come from script-help-text.ps1, the same one the cheatsheet generator uses, so
    # this gate and the inventory can never disagree about whether a script is described.
    $synopsis = Get-ScriptSynopsis -Path $f.FullName -Ast $ast
    if ([string]::IsNullOrWhiteSpace($synopsis)) { $undescribed.Add($rel) }

    # A library returns; only a script that actually exits owes an exit contract.
    if ($text -match '(?m)^\s*exit\s+\d') {
        if (-not (Get-ScriptExitCodesDocumented -Path $f.FullName)) { $undocumentedExit.Add($rel) }
    }
}

if ($Report -and -not $Quiet) {
    foreach ($u in ($undescribed | Sort-Object)) { Write-Host "  no synopsis:      $u" -ForegroundColor Yellow }
    foreach ($u in ($undocumentedExit | Sort-Object)) { Write-Host "  exit undocumented: $u" -ForegroundColor Yellow }
}

if ($Report) {
    Write-Host ("assert-script-described: {0} without a synopsis, {1} exiting without a documented contract, {2} scripts - report mode." -f `
        $undescribed.Count, $undocumentedExit.Count, $files.Count)
    exit 0
}

if (-not (Test-Path -LiteralPath $baselinePath)) {
    Write-Host "assert-script-described: cannot verify - baseline file missing: $baselinePath" -ForegroundColor Yellow
    Write-Host "  Create it with two lines - undescribed count, then undocumented-exit count - from: assert-script-described.ps1 -Report"
    exit 2
}

$baselineLines = @(Get-Content -LiteralPath $baselinePath | Where-Object { $_.Trim() -ne '' })
if ($baselineLines.Count -lt 2) {
    Write-Host "assert-script-described: cannot verify - baseline needs two numbers, found $($baselineLines.Count)." -ForegroundColor Yellow
    Write-Host "  file: $baselinePath"
    exit 2
}
$maxUndescribed = [int]$baselineLines[0].Trim()
$maxUndocumented = [int]$baselineLines[1].Trim()

$failed = $false
if ($undescribed.Count -gt $maxUndescribed) {
    Write-Host ("assert-script-described: FAIL - {0} script(s) without a synopsis, ceiling {1}." -f $undescribed.Count, $maxUndescribed) -ForegroundColor Red
    $failed = $true
}
if ($undocumentedExit.Count -gt $maxUndocumented) {
    Write-Host ("assert-script-described: FAIL - {0} script(s) exit without a documented contract, ceiling {1}." -f $undocumentedExit.Count, $maxUndocumented) -ForegroundColor Red
    $failed = $true
}
if ($failed) {
    Write-Host "  Add a .SYNOPSIS line, and a .NOTES 'Exit codes:' block listing every code the script returns."
    Write-Host "  Run with -Report to see which files."
    exit 1
}

Write-Host ("assert-script-described: PASS - {0}/{1} undescribed, {2}/{3} undocumented exits, {4} scripts." -f `
    $undescribed.Count, $maxUndescribed, $undocumentedExit.Count, $maxUndocumented, $files.Count) -ForegroundColor Green
exit 0
