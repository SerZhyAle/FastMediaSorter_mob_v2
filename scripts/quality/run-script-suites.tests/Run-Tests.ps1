#requires -Version 7.0
<#
.SYNOPSIS
    S2122 - regression suite for the script-suite runner itself.

.DESCRIPTION
    Subject: scripts/quality/run-script-suites.ps1

    This ticket exists because 37 suites sat in the tree with no run site, and shipping the run site
    without a suite of its own would leave exactly one script in the repository whose correctness
    nothing checks - the same defect in a new place. So the runner is asserted on the four things it
    can silently get wrong: discovery by convention, neighbour selection, the exit-2/exit-1
    distinction, and the aggregated verdict.

    HERMETIC. Every case points the runner at a throw-away fixture tree under temp/S2122/, never at
    scripts/, so this suite can never trigger a recursive sweep of the repository's real suites. The
    fixture is removed in a finally block whatever happens.

    The re-entry guard is cleared on purpose (see the assignment below): when this suite is executed
    BY the runner during a full sweep, the runner has set FMS_SCRIPT_SUITE_RUNNER=1 in the
    environment the child inherits, and every runner invocation below would skip and report 0. The
    guard protects against unbounded nesting, not against a test that deliberately drives the runner.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every case passed.
      1  at least one case failed.
      2  could not verify - the runner script is missing.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$runner = Join-Path $repoRoot 'scripts/quality/run-script-suites.ps1'
if (-not (Test-Path -LiteralPath $runner)) {
    Write-Error "run-script-suites tests: cannot verify - the runner is missing at $runner." -ErrorAction Continue
    exit 2
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$inheritedGuard = $env:FMS_SCRIPT_SUITE_RUNNER
$env:FMS_SCRIPT_SUITE_RUNNER = $null

$script:pass = 0
$script:fail = 0

function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $Name -> $Detail" -ForegroundColor Red
        $script:fail++
    }
}

$fixtureRoot = Join-Path $repoRoot ('temp/S2122/fixture-' + [guid]::NewGuid().ToString('N'))
$fixtureRel = 'temp/S2122/' + (Split-Path -Leaf $fixtureRoot)

function New-FixtureSuite([string]$Name, [int]$ExitCode) {
    $subject = Join-Path $fixtureRoot "$Name.ps1"
    Set-Content -LiteralPath $subject -Value "# fixture subject $Name" -Encoding utf8NoBOM
    $suiteDir = Join-Path $fixtureRoot "$Name.tests"
    New-Item -ItemType Directory -Path $suiteDir -Force | Out-Null
    $body = @(
        "Write-Host 'fixture suite $Name reporting'",
        "exit $ExitCode"
    )
    Set-Content -LiteralPath (Join-Path $suiteDir 'Run-Tests.ps1') -Value $body -Encoding utf8NoBOM
}

function Invoke-Runner([string[]]$RunnerArgs) {
    $out = @(& $pwshExe -NoProfile -File $runner @RunnerArgs *>&1 | ForEach-Object { [string]$_ })
    return [pscustomobject]@{ Code = [int]$LASTEXITCODE; Text = ($out -join "`n") }
}

try {
    New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
    New-FixtureSuite -Name 'alpha' -ExitCode 0
    New-FixtureSuite -Name 'beta' -ExitCode 1
    New-FixtureSuite -Name 'gamma' -ExitCode 2
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'delta.ps1') -Value '# fixture subject with no suite' -Encoding utf8NoBOM

    # A suite whose subject the path cannot reach, declaring it in its own header instead.
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'oddly-named.tests') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'lib') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'lib/produces-notices.ps1') -Value '# fixture subject reached only by declaration' -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'oddly-named.tests/Run-Tests.ps1') -Encoding utf8NoBOM -Value @(
        "# Subject: $fixtureRel/lib/produces-notices.ps1",
        "Write-Host 'fixture suite oddly-named reporting'",
        'exit 0'
    )

    Write-Host 'Discovery' -ForegroundColor Yellow

    $listed = Invoke-Runner @('-Root', $fixtureRoot, '-ListOnly')
    Assert-That 'D1 a suite placed by convention is discovered with no edit to the runner' `
        ($listed.Code -eq 0 -and $listed.Text -match '4 suite\(s\) selected of 4 discovered') `
        "exit $($listed.Code); text: $($listed.Text)"

    Assert-That 'D2 discovery never reaches the repository suites when -Root is a fixture' `
        ($listed.Text -notmatch 'scripts/post-change\.tests') `
        "fixture listing named a real repository suite: $($listed.Text)"

    Write-Host 'Neighbour selection' -ForegroundColor Yellow

    $alpha = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.ps1")
    Assert-That 'S1 a changed script selects its sibling suite and passes' `
        ($alpha.Code -eq 0 -and $alpha.Text -match 'alpha\.tests' -and $alpha.Text -notmatch 'beta\.tests') `
        "exit $($alpha.Code); text: $($alpha.Text)"

    $unrelated = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', 'app_v2/src/main/AndroidManifest.xml')
    Assert-That 'S2 an unrelated changed file selects nothing and exits 0' `
        ($unrelated.Code -eq 0 -and $unrelated.Text -match 'no suite has a subject in the changed set') `
        "exit $($unrelated.Code); text: $($unrelated.Text)"

    $selfEdit = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.tests/Run-Tests.ps1")
    Assert-That 'S3 editing a suite runs that suite' `
        ($selfEdit.Code -eq 0 -and $selfEdit.Text -match 'alpha\.tests') `
        "exit $($selfEdit.Code); text: $($selfEdit.Text)"

    $declared = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/lib/produces-notices.ps1")
    Assert-That 'S4 a suite whose subject only its own header names is still selected' `
        ($declared.Code -eq 0 -and $declared.Text -match 'oddly-named\.tests') `
        "exit $($declared.Code); text: $($declared.Text)"

    Write-Host 'Verdict classification' -ForegroundColor Yellow

    $gammaGate = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/gamma.ps1", '-Gate')
    Assert-That 'V1 a suite exiting 2 is "could not verify" and is fatal under -Gate' `
        ($gammaGate.Code -eq 2 -and $gammaGate.Text -match 'CANNOT-VERIFY') `
        "exit $($gammaGate.Code); text: $($gammaGate.Text)"

    $gammaAdvisory = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/gamma.ps1")
    Assert-That 'V2 the same suite is advisory without -Gate' `
        ($gammaAdvisory.Code -eq 0 -and $gammaAdvisory.Text -match 'advisory') `
        "exit $($gammaAdvisory.Code); text: $($gammaAdvisory.Text)"

    $betaRun = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/beta.ps1")
    Assert-That 'V3 a suite exiting 1 fails the run and is named' `
        ($betaRun.Code -eq 1 -and $betaRun.Text -match 'beta\.tests') `
        "exit $($betaRun.Code); text: $($betaRun.Text)"

    $sweep = Invoke-Runner @('-Root', $fixtureRoot, '-Gate')
    Assert-That 'V4 a failure outranks a cannot-verify in the aggregated verdict' `
        ($sweep.Code -eq 1) `
        "expected 1, got $($sweep.Code); text: $($sweep.Text)"

    Assert-That 'V5 the output of a failing suite is echoed so the caller need not re-run it' `
        ($betaRun.Text -match 'fixture suite beta reporting') `
        "output of the failing suite was not echoed: $($betaRun.Text)"
}
finally {
    $env:FMS_SCRIPT_SUITE_RUNNER = $inheritedGuard
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host "run-script-suites tests: $($script:pass) passed, $($script:fail) FAILED" -ForegroundColor ($script:fail -eq 0 ? 'Green' : 'Red')

if ($script:fail -gt 0) {
    Write-Error "run-script-suites tests: $($script:fail) case(s) failed." -ErrorAction Continue
    exit 1
}
exit 0
