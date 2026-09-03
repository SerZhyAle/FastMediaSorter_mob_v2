#requires -Version 7.0
# Subject: scripts/quality/run-script-suites.ps1
<#
.SYNOPSIS
    Regression suite for assert-suite-tracked.ps1 and the list mode it reads (S2411).

.DESCRIPTION
    The gate refuses a contract-suite runner that exists on disk but not in the git index. Its whole
    value is in the refusal, so the refusal is what is asserted here - a gate whose red path nobody
    executed is the same unobserved green verdict the gate exists to prevent.

    Asserted, in both directions:
      * a fixture repository whose runner is staged passes,
      * the same repository with a second, unstaged runner fails, names that path, and prints the
        `git add` that clears it,
      * without -Gate the identical condition is reported and exits 0, because "report" and "refuse"
        are the two call sites' two readings of one measurement,
      * a directory that is not a git work tree exits 2, and an unusable discovery root exits 2 -
        "could not look" must never be spelled the same way as "looked and refused",
      * the runner's -ListOnly -Json writes parseable JSON with one record per selected suite, which
        is the contract the gate depends on and which silently did not exist before this ticket.

    The fixtures are their OWN git repositories under temp/scratch/, created with `git init` and
    removed afterwards. Never this repository's index: an assertion that staged or unstaged a real
    file would edit the very state it is judging.

    The Subject line names run-script-suites.ps1 on purpose. Path arithmetic already binds this
    suite to the gate beside it, but the gate reads the runner's list format, so a change to the
    runner must run this suite too.

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

$gatePs1 = Join-Path $repoRoot 'scripts/quality/assert-suite-tracked.ps1'
$runnerPs1 = Join-Path $repoRoot 'scripts/quality/run-script-suites.ps1'

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

function New-FixtureSuite([string]$Parent, [string]$Name) {
    $dir = Join-Path $Parent "$Name.tests"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    # The fixture is never executed - the gate only ever asks git about its path - but it is written
    # as a real runner so a stray full sweep aimed at the sandbox would still exit 0.
    Set-Content -LiteralPath (Join-Path $dir 'Run-Tests.ps1') -Value @('exit 0') -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $Parent "$Name.ps1") -Value @('exit 0') -Encoding utf8NoBOM
    return (Join-Path $dir 'Run-Tests.ps1')
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host 'git is not on PATH - the fixtures cannot be prepared.' -ForegroundColor Yellow
    exit 2
}

Write-Host 'assert-suite-tracked regression suite' -ForegroundColor Cyan

$sandbox = Join-Path $repoRoot ('temp/scratch/assert-suite-tracked-sandbox-{0}' -f $PID)
$noGitDir = Join-Path ([System.IO.Path]::GetTempPath()) ('fms-s2411-nogit-{0}' -f $PID)
$prepared = $false
try {
    New-Item -ItemType Directory -Force -Path $sandbox | Out-Null
    New-Item -ItemType Directory -Force -Path $noGitDir | Out-Null

    & git -C $sandbox init --quiet 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git init failed in $sandbox" }
    # A repository with no identity refuses to commit, but nothing here commits - the gate asks the
    # INDEX (strategic ADR-1), which `git add` alone fills.
    $stagedRunner = New-FixtureSuite -Parent $sandbox -Name 'alpha'
    & git -C $sandbox add -- $stagedRunner 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git add failed for $stagedRunner" }
    $prepared = $true

    # A. every discovered runner staged -> pass.
    $a = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'A. a fully staged fixture passes' ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"

    # B. a second runner exists and is not staged - the case the gate exists for.
    $looseRunner = New-FixtureSuite -Parent $sandbox -Name 'beta'
    $b = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'B. an unstaged runner fails' ($b.Code -eq 1) "exit $($b.Code): $($b.Text)"
    Assert-That 'B2. the refusal names the unstaged path' ($b.Text -match 'beta\.tests') $b.Text
    Assert-That 'B3. the refusal prints the staging command' ($b.Text -match 'git add --') $b.Text
    Assert-That 'B4. the staged runner is not reported' (-not ($b.Text -match 'alpha\.tests')) $b.Text

    # C. the same tree without -Gate reports and exits 0 - the two call sites read one measurement
    # two ways, exactly as run-script-suites.ps1 does with its own exit 2.
    $c = Invoke-Gate @('-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'C. without -Gate the same condition exits 0' ($c.Code -eq 0) "exit $($c.Code): $($c.Text)"
    Assert-That 'C2. and still reports the count' ($c.Text -match 'actual: 1 untracked') $c.Text

    # D. staging it clears the refusal - the fix the message names actually works.
    & git -C $sandbox add -- $looseRunner 2>&1 | Out-Null
    $d = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'D. staging the runner clears the refusal' ($d.Code -eq 0) "exit $($d.Code): $($d.Text)"

    # E. not a git work tree -> could not verify, not a defect.
    $e = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $noGitDir)
    Assert-That 'E. a non-git directory exits 2, not 1' ($e.Code -eq 2) "exit $($e.Code): $($e.Text)"
    Assert-That 'E2. and says it could not verify' ($e.Text -match 'CANNOT VERIFY') $e.Text

    # F. an unusable discovery root is also "could not look": the runner exits 2 and the gate must
    # not translate a missing list into a clean tree.
    $f = Invoke-Gate @('-Gate', '-Root', (Join-Path $sandbox 'no-such-root'), '-GitRoot', $sandbox)
    Assert-That 'F. an absent discovery root exits 2' ($f.Code -eq 2) "exit $($f.Code): $($f.Text)"

    # G. the list contract the gate stands on: -ListOnly -Json writes parseable JSON, one record per
    # selected suite. Before S2411 -Json was honoured in the run mode only and this file was never
    # written at all, silently.
    $listPath = Join-Path $sandbox 'suites.json'
    $listOut = & $pwshExe -NoProfile -File $runnerPs1 -ListOnly -Root $sandbox -Json $listPath 2>&1
    $listCode = [int]$LASTEXITCODE
    Assert-That 'G. -ListOnly -Json exits 0' ($listCode -eq 0) "exit ${listCode}: $(($listOut -join "`n"))"
    Assert-That 'G2. -ListOnly -Json writes the file' (Test-Path -LiteralPath $listPath) $listPath
    if (Test-Path -LiteralPath $listPath) {
        $records = @(Get-Content -LiteralPath $listPath -Raw | ConvertFrom-Json)
        $printed = [regex]::Match((($listOut | ForEach-Object { [string]$_ }) -join "`n"), '(\d+) suite\(s\) selected')
        Assert-That 'G3. one record per selected suite' (
            $printed.Success -and $records.Count -eq [int]$printed.Groups[1].Value
        ) ("records: {0}, printed: {1}" -f $records.Count, $printed.Value)
        Assert-That 'G4. each record carries Suite and Resolved' (
            @($records | Where-Object { $_.PSObject.Properties.Name -contains 'Suite' -and $_.PSObject.Properties.Name -contains 'Resolved' }).Count -eq $records.Count
        ) (($records | ConvertTo-Json -Depth 4 -Compress))
    }
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
    Write-Host 'assert-suite-tracked suite: FAIL' -ForegroundColor Red
    exit 1
}
exit 0
