#requires -Version 7.0
# Subject: scripts/quality/assert-invoked-tracked.ps1
<#
.SYNOPSIS
    Regression suite for assert-invoked-tracked.ps1 (S2654).

.DESCRIPTION
    The gate refuses an invoked script that exists on disk but not in the git index.

    Asserted:
      * a fully staged fixture passes under -Gate,
      * an unstaged target fails under -Gate, names the path plus its git add,
      * without -Gate the identical condition is reported and exits 0,
      * staging the target clears the refusal,
      * a comment mentioning an untracked script produces no finding,
      * a non-git directory exits 2 with CANNOT VERIFY,
      * an absent discovery root exits 2.

.NOTES
    Exit codes:
      0   all cases pass.
      1   at least one case failed.
      2   fixtures could not be prepared.
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

$gatePs1 = Join-Path $repoRoot 'scripts/quality/assert-invoked-tracked.ps1'

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

function New-FixtureInvocation([string]$Parent, [string]$ConsumerName, [string]$TargetName) {
    $targetPath = Join-Path $Parent "$TargetName.ps1"
    $consumerPath = Join-Path $Parent "$ConsumerName.ps1"
    Set-Content -LiteralPath $targetPath -Value @('#requires -Version 7.0', 'exit 0') -Encoding utf8NoBOM
    Set-Content -LiteralPath $consumerPath -Value @(
        '#requires -Version 7.0',
        ('& "$PSScriptRoot/{0}.ps1"' -f $TargetName)
    ) -Encoding utf8NoBOM
    return @{ Consumer = $consumerPath; Target = $targetPath }
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host 'git is not on PATH - the fixtures cannot be prepared.' -ForegroundColor Yellow
    exit 2
}

Write-Host 'assert-invoked-tracked regression suite' -ForegroundColor Cyan

$sandbox = Join-Path $repoRoot ('temp/S2654/assert-invoked-tracked-sandbox-{0}' -f $PID)
$noGitDir = Join-Path ([System.IO.Path]::GetTempPath()) ('fms-s2654-nogit-{0}' -f $PID)
$prepared = $false

try {
    New-Item -ItemType Directory -Force -Path $sandbox | Out-Null
    New-Item -ItemType Directory -Force -Path $noGitDir | Out-Null

    & git -C $sandbox init --quiet 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git init failed in $sandbox" }

    $pairAlpha = New-FixtureInvocation -Parent $sandbox -ConsumerName 'caller_alpha' -TargetName 'helper_alpha'
    & git -C $sandbox add -- $pairAlpha.Consumer $pairAlpha.Target 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git add failed for alpha pair" }
    $prepared = $true

    # A. every discovered target staged -> pass.
    $a = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'A. a fully staged fixture passes' ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"

    # B. a second target exists and is not staged - fails under -Gate.
    $pairBeta = New-FixtureInvocation -Parent $sandbox -ConsumerName 'caller_beta' -TargetName 'helper_beta'
    & git -C $sandbox add -- $pairBeta.Consumer 2>&1 | Out-Null
    $b = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'B. an unstaged invoked script fails' ($b.Code -eq 1) "exit $($b.Code): $($b.Text)"
    Assert-That 'B2. the refusal names the unstaged path' ($b.Text -match 'helper_beta\.ps1') $b.Text
    Assert-That 'B3. the refusal prints the staging command' ($b.Text -match 'git add --') $b.Text
    Assert-That 'B4. the staged target is not reported' (-not ($b.Text -match 'helper_alpha\.ps1')) $b.Text

    # C. without -Gate the same condition reports and exits 0.
    $c = Invoke-Gate @('-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'C. without -Gate the same condition exits 0' ($c.Code -eq 0) "exit $($c.Code): $($c.Text)"
    Assert-That 'C2. and still reports the count' ($c.Text -match 'actual: 1 untracked') $c.Text

    # D. staging it clears the refusal.
    & git -C $sandbox add -- $pairBeta.Target 2>&1 | Out-Null
    $d = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'D. staging the target clears the refusal' ($d.Code -eq 0) "exit $($d.Code): $($d.Text)"

    # E. a comment mentioning an untracked script produces no finding.
    $commentOnlyConsumer = Join-Path $sandbox 'comment_caller.ps1'
    $untrackedCommentScript = Join-Path $sandbox 'comment_target.ps1'
    Set-Content -LiteralPath $untrackedCommentScript -Value @('exit 0') -Encoding utf8NoBOM
    Set-Content -LiteralPath $commentOnlyConsumer -Value @('# This calls comment_target.ps1 in prose only') -Encoding utf8NoBOM
    & git -C $sandbox add -- $commentOnlyConsumer 2>&1 | Out-Null

    $e = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $sandbox)
    Assert-That 'E. comment mentioning untracked script produces no finding' ($e.Code -eq 0) "exit $($e.Code): $($e.Text)"

    # F. not a git work tree -> CANNOT VERIFY, exit 2.
    $f = Invoke-Gate @('-Gate', '-Root', $sandbox, '-GitRoot', $noGitDir)
    Assert-That 'F. a non-git directory exits 2, not 1' ($f.Code -eq 2) "exit $($f.Code): $($f.Text)"
    Assert-That 'F2. and says it could not verify' ($f.Text -match 'CANNOT VERIFY') $f.Text

    # G. absent discovery root -> exit 2.
    $g = Invoke-Gate @('-Gate', '-Root', (Join-Path $sandbox 'no-such-root'), '-GitRoot', $sandbox)
    Assert-That 'G. an absent discovery root exits 2' ($g.Code -eq 2) "exit $($g.Code): $($g.Text)"
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
    Write-Host 'assert-invoked-tracked suite: FAIL' -ForegroundColor Red
    exit 1
}
exit 0
