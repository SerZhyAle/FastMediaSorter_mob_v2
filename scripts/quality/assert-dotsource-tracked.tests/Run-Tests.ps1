#requires -Version 7.0
# Subject: scripts/quality/assert-dotsource-tracked.ps1
<#
.SYNOPSIS
    Regression suite for assert-dotsource-tracked.ps1 and the index helper it shares (S2616).

.DESCRIPTION
    The gate refuses a dot-sourced script that exists on disk but not in the git index. Its whole
    value is in the refusal, so the refusal is what is asserted here - a gate whose red path nobody
    executed is the same unobserved green verdict the gate exists to prevent.

    Asserted:
      * a fixture whose helpers are all staged passes, through both spellings the repository uses
        ("$PSScriptRoot\x.ps1" and (Join-Path $PSScriptRoot 'x.ps1')),
      * an unstaged helper fails, is named, and the printed `git add` is what clears it,
      * without -Gate the identical condition is reported and exits 0,
      * a target addressed through a runtime variable is NOT judged and is counted as unresolved -
        the gate must never invent a path it cannot know,
      * a `.` in a comment and a decimal literal produce no dot-source at all, which is the whole
        reason the selection comes from the parser instead of a regular expression,
      * a target resolving outside the judged work tree is counted, not judged,
      * a directory that is not a git work tree exits 2, and an absent discovery root exits 2 -
        "could not look" must never be spelled the same way as "looked and refused".

    The fixtures are their OWN git repositories under temp/scratch/, created with `git init` and
    removed afterwards. Never this repository's index: an assertion that staged or unstaged a real
    file would edit the very state it is judging.

.NOTES
    Exit codes:
      0   all cases pass.
      1   at least one case failed.
      2   the fixtures could not be prepared (git absent, or the sandbox could not be built).
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else { 'pwsh' }

$gatePs1 = Join-Path $repoRoot 'scripts/quality/assert-dotsource-tracked.ps1'

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

function Invoke-Gate([string[]]$Arguments) {
    $out = & $pwshExe -NoProfile -File $gatePs1 @Arguments 2>&1
    return [pscustomobject]@{
        Code = [int]$LASTEXITCODE
        Text = (($out | ForEach-Object { [string]$_ }) -join "`n")
    }
}

function Write-Script([string]$Path, [string[]]$Lines) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    Set-Content -LiteralPath $Path -Value $Lines -Encoding utf8NoBOM
}

# The coverage line is a contract too: a gate that silently narrowed its own selection would print
# the same green as one that judged everything.
function Get-Coverage([string]$Text, [string]$Field) {
    $match = [regex]::Match($Text, "(\d+) $Field")
    if (-not $match.Success) { return -1 }
    return [int]$match.Groups[1].Value
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host 'git is not on PATH - the fixtures cannot be prepared.' -ForegroundColor Yellow
    exit 2
}

Write-Host 'assert-dotsource-tracked regression suite' -ForegroundColor Cyan

$sandbox = Join-Path $repoRoot ('temp/scratch/assert-dotsource-tracked-sandbox-{0}' -f $PID)
$noGitDir = Join-Path ([System.IO.Path]::GetTempPath()) ('fms-s2616-nogit-{0}' -f $PID)
$prepared = $false
try {
    New-Item -ItemType Directory -Force -Path $sandbox | Out-Null
    New-Item -ItemType Directory -Force -Path $noGitDir | Out-Null

    & git -C $sandbox init --quiet 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git init failed in $sandbox" }

    # Both spellings the repository actually writes, plus the three shapes that must NOT become
    # findings: a runtime variable, a decimal literal and a dot inside a comment.
    Write-Script (Join-Path $sandbox 'consumer.ps1') @(
        '. "$PSScriptRoot\helper-expandable.ps1"',
        ". (Join-Path `$PSScriptRoot 'helper-joined.ps1')",
        '$runtime = "$PSScriptRoot\helper-unknowable.ps1"',
        '. $runtime',
        '$ratio = 1.5',
        '# A sentence ending in a version 1. "quoted words" follow it.',
        'exit 0'
    )
    Write-Script (Join-Path $sandbox 'helper-expandable.ps1') @('function Test-Alpha { }')
    Write-Script (Join-Path $sandbox 'helper-joined.ps1') @('function Test-Beta { }')
    Write-Script (Join-Path $sandbox 'helper-unknowable.ps1') @('function Test-Gamma { }')

    & git -C $sandbox add -- (Join-Path $sandbox 'consumer.ps1') `
        (Join-Path $sandbox 'helper-expandable.ps1') `
        (Join-Path $sandbox 'helper-joined.ps1') 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'git add failed for the staged fixture' }
    $prepared = $true

    # A. every judged target staged -> pass. helper-unknowable.ps1 exists and is NOT staged, so this
    # case also proves the runtime-variable target is genuinely not judged.
    $a = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'A. a fully staged fixture passes' ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"
    Assert-That 'A2. both resolvable spellings are judged' (
        (Get-Coverage $a.Text 'untracked target\(s\) of') -eq 0 -and $a.Text -match 'of 2 judged'
    ) $a.Text
    Assert-That 'A3. the runtime-variable target is counted unresolved, not judged' (
        (Get-Coverage $a.Text 'unresolved') -eq 1
    ) $a.Text
    Assert-That 'A4. a decimal and a dot in a comment are not dot-sources' (
        (Get-Coverage $a.Text 'dot-source site\(s\)') -eq 3
    ) $a.Text

    # B. a helper exists and is not staged - the case the gate exists for.
    Write-Script (Join-Path $sandbox 'loose-consumer.ps1') @(
        '. "$PSScriptRoot\loose-helper.ps1"',
        'exit 0'
    )
    Write-Script (Join-Path $sandbox 'loose-helper.ps1') @('function Test-Delta { }')
    & git -C $sandbox add -- (Join-Path $sandbox 'loose-consumer.ps1') 2>&1 | Out-Null

    $b = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'B. an unstaged dot-source target fails' ($b.Code -eq 1) "exit $($b.Code): $($b.Text)"
    Assert-That 'B2. the refusal names the unstaged target' ($b.Text -match 'loose-helper\.ps1') $b.Text
    Assert-That 'B3. the refusal names the consumer and its line' ($b.Text -match 'loose-consumer\.ps1:1') $b.Text
    Assert-That 'B4. the refusal prints the staging command' ($b.Text -match 'git add --') $b.Text
    Assert-That 'B5. the staged helpers are not reported' (
        -not ($b.Text -match 'helper-expandable\.ps1\r?\n') -and $b.Text -match 'actual: 1 untracked'
    ) $b.Text

    # C. the same tree without -Gate reports and exits 0 - two call sites, one measurement.
    $c = Invoke-Gate @('-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'C. without -Gate the same condition exits 0' ($c.Code -eq 0) "exit $($c.Code): $($c.Text)"
    Assert-That 'C2. and still reports the count' ($c.Text -match 'actual: 1 untracked') $c.Text

    # D. staging it clears the refusal - the fix the message names actually works.
    & git -C $sandbox add -- (Join-Path $sandbox 'loose-helper.ps1') 2>&1 | Out-Null
    $d = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'D. staging the target clears the refusal' ($d.Code -eq 0) "exit $($d.Code): $($d.Text)"

    # E. a target above the work tree is not this repository's file to track. The canon forwarders
    # are the live case; here it is a plain climb out of the sandbox.
    Write-Script (Join-Path $sandbox 'outsider.ps1') @(
        '. "$PSScriptRoot\..\..\..\outside-target.ps1"',
        'exit 0'
    )
    & git -C $sandbox add -- (Join-Path $sandbox 'outsider.ps1') 2>&1 | Out-Null
    $e = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'E. a target outside the work tree is counted, not judged' (
        $e.Code -eq 0 -and (Get-Coverage $e.Text 'outside the work tree') -eq 1
    ) "exit $($e.Code): $($e.Text)"

    # F. -ChangedFiles narrows the CONSUMERS: naming only the clean consumer must not surface the
    # loose one's target, which is what makes the per-ticket half refuse its own author and nobody
    # else's work in flight.
    Write-Script (Join-Path $sandbox 'second-loose-helper.ps1') @('function Test-Epsilon { }')
    Write-Script (Join-Path $sandbox 'second-loose-consumer.ps1') @(
        '. "$PSScriptRoot\second-loose-helper.ps1"',
        'exit 0'
    )
    & git -C $sandbox add -- (Join-Path $sandbox 'second-loose-consumer.ps1') 2>&1 | Out-Null
    $f = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox, '-ChangedFiles', 'consumer.ps1')
    Assert-That 'F. a changed set naming a clean consumer passes' ($f.Code -eq 0) "exit $($f.Code): $($f.Text)"
    Assert-That 'F2. and judges only that consumer' ($f.Text -match 'in 1 script\(s\)') $f.Text
    $f2 = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox, '-ChangedFiles', 'second-loose-consumer.ps1')
    Assert-That 'F3. a changed set naming the dirty consumer refuses' ($f2.Code -eq 1) "exit $($f2.Code): $($f2.Text)"

    # F4. the SPELLING of a -ChangedFiles entry must not decide the verdict, and the component that
    # matters is a DIRECTORY. The gate used to lower-case the changed set for de-duplication and then
    # walk those lower-cased strings; Get-Item echoes the casing it was handed rather than the casing
    # on disk, so the lower-cased directory travelled into the `git ls-files` pathspec, which git
    # matches case-sensitively. git printed nothing, and this gate reads silence as "untracked" - so a
    # file staged seconds earlier was refused, with the printed fix being the `git add` already run
    # (S2837, against the real `dev/CATALOG/scripts/`). The consumer lives under a mixed-case
    # directory because a bare file name cannot reproduce it: the directory half of the path comes
    # from -Root and keeps its casing whatever the caller typed.
    Write-Script (Join-Path $sandbox 'MixedCase/mixed-helper.ps1') @('function Test-Zeta { }')
    Write-Script (Join-Path $sandbox 'MixedCase/mixed-consumer.ps1') @(
        '. "$PSScriptRoot\mixed-helper.ps1"',
        'exit 0'
    )
    & git -C $sandbox add -- (Join-Path $sandbox 'MixedCase') 2>&1 | Out-Null
    $f4 = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox, '-ChangedFiles', 'MixedCase/mixed-consumer.ps1')
    Assert-That 'F4. a mixed-case changed path does not fake an untracked target' (
        $f4.Code -eq 0 -and $f4.Text -match 'actual: 0 untracked'
    ) "exit $($f4.Code): $($f4.Text)"

    # G. not a git work tree -> could not verify, not a defect.
    $g = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $noGitDir)
    Assert-That 'G. a non-git directory exits 2, not 1' ($g.Code -eq 2) "exit $($g.Code): $($g.Text)"
    Assert-That 'G2. and says it could not verify' ($g.Text -match 'CANNOT VERIFY') $g.Text

    # H. an absent discovery root is also "could not look".
    $h = Invoke-Gate @('-Gate', '-Root', (Join-Path $sandbox 'no-such-root'), '-GitRoot', $sandbox)
    Assert-That 'H. an absent discovery root exits 2' ($h.Code -eq 2) "exit $($h.Code): $($h.Text)"
}
catch {
    Write-Host "  fixture error: $($_.Exception.Message)" -ForegroundColor Yellow
}
finally {
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $noGitDir) { Remove-Item -LiteralPath $noGitDir -Recurse -Force -ErrorAction SilentlyContinue }
}

if (-not $prepared) {
    Write-Host 'Fixtures could not be prepared.' -ForegroundColor Yellow
    exit 2
}

Write-Host ''
Write-Host ("passed: {0}  failed: {1}" -f $script:pass, $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) {
    Write-Host 'assert-dotsource-tracked suite: FAIL' -ForegroundColor Red
    exit 1
}
exit 0
