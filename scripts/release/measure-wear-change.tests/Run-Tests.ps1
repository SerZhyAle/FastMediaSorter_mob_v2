# Run-Tests.ps1 (S4799) - contract suite for scripts/release/measure-wear-change.ps1.
#
# Subject: scripts/release/measure-wear-change.ps1
#
# Builds a throwaway git repository with dated commits and measures it against a stamped versionName,
# so the base-commit choice, the shipping/test split, untracked files and every exit code are checked
# against real git rather than a copy of the logic.
#
# Usage:  pwsh -NoProfile -File scripts/release/measure-wear-change.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the suite could not run (the subject or git is missing).

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$subject = Join-Path $repoRoot 'scripts/release/measure-wear-change.ps1'
if (-not (Test-Path -LiteralPath $subject)) {
    Write-Host "measure-wear-change.tests: CANNOT RUN - not found: $subject"
    exit 2
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host 'measure-wear-change.tests: CANNOT RUN - git is not on PATH'
    exit 2
}

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        $script:pass++
        Write-Host ("  PASS  {0}" -f $name)
    }
    else {
        $script:fail++
        Write-Host ("  FAIL  {0}`n          {1}" -f $name, $detail)
    }
}

$work = Join-Path ([IO.Path]::GetTempPath()) ("measure-wear-change-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null

function Invoke-Git([string[]] $gitArgs) {
    & git -C $work @gitArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git $($gitArgs -join ' ') failed" }
}

function Set-File([string] $relative, [string] $text) {
    $path = Join-Path $work $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    Set-Content -LiteralPath $path -Value $text
}

function Add-Commit([string] $date, [string] $message) {
    $env:GIT_AUTHOR_DATE = $date
    $env:GIT_COMMITTER_DATE = $date
    Invoke-Git @('add', '-A')
    Invoke-Git @('commit', '-q', '-m', $message)
}

function Measure-Case([string] $versionName) {
    $out = & pwsh -NoProfile -File $subject -SinceVersionName $versionName -RepoRoot $work -Json 2>&1
    $code = $LASTEXITCODE
    $obj = $null
    if ($code -ne 2) { $obj = ($out -join "`n") | ConvertFrom-Json }
    [pscustomobject]@{ Code = $code; Result = $obj; Text = ($out -join ' ') }
}

# 2026-11-12 17:38 local, the instant the watch release is stamped with.
$release = '2.61.1121.738'

try {
    Invoke-Git @('init', '-q')
    Invoke-Git @('config', 'user.email', 'suite@example.invalid')
    Invoke-Git @('config', 'user.name', 'suite')
    Invoke-Git @('config', 'core.autocrlf', 'false')

    Set-File 'wear/src/main/A.kt' 'one'
    Set-File 'wear/src/test/T.kt' 'one'
    Set-File 'app_v2/src/main/P.kt' 'one'
    Add-Commit '2026-11-10T10:00:00' 'c1'
    Set-File 'wear/src/main/A.kt' "one`ntwo"
    Add-Commit '2026-11-11T10:00:00' 'c2'
    $c2 = (& git -C $work rev-parse HEAD).Trim()

    $r = Measure-Case $release
    Assert-That 'a change made before the release is in the base, not counted' ($r.Code -eq 3 -and $r.Result.verdict -eq 'UNCHANGED') $r.Text
    Assert-That 'the base is the last commit before the release instant' ($r.Code -eq 3 -and $r.Result.baseCommit -eq $c2) $r.Text

    Set-File 'wear/src/test/T.kt' "one`ntwo"
    Set-File 'app_v2/src/main/P.kt' "one`ntwo"
    Add-Commit '2026-11-13T10:00:00' 'c3'
    $r = Measure-Case $release
    Assert-That 'test-only and phone-only changes are UNCHANGED, exit 3' ($r.Code -eq 3 -and @($r.Result.tests).Count -eq 1) $r.Text

    Set-File 'wear/src/main/B.kt' "new`nfile"
    $r = Measure-Case $release
    Assert-That 'an untracked shipping file is CHANGED, exit 0' ($r.Code -eq 0 -and @($r.Result.shipping).Path -contains 'wear/src/main/B.kt') $r.Text
    Remove-Item -LiteralPath (Join-Path $work 'wear/src/main/B.kt')

    Set-File 'wear/build.gradle.kts' 'android {}'
    Add-Commit '2026-11-14T10:00:00' 'c4'
    $r = Measure-Case $release
    Assert-That 'a committed build-script change is CHANGED, exit 0' ($r.Code -eq 0 -and @($r.Result.shipping).Path -contains 'wear/build.gradle.kts') $r.Text

    $r = Measure-Case '2.61.1010.000'
    Assert-That 'a release older than every commit is could-not-verify, exit 2' ($r.Code -eq 2) $r.Text

    $r = Measure-Case 'not-a-version'
    Assert-That 'a versionName with no instant is could-not-verify, exit 2' ($r.Code -eq 2) $r.Text
}
finally {
    Remove-Item Env:GIT_AUTHOR_DATE, Env:GIT_COMMITTER_DATE -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("measure-wear-change.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
