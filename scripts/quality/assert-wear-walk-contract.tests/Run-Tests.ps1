#requires -Version 7.0
<#
.SYNOPSIS
    S2547 regression suite for the watch walk contract gate.

# Subject: scripts/quality/assert-wear-walk-contract.ps1

.DESCRIPTION
    Hermetic: drives scripts/quality/assert-wear-walk-contract.ps1 against fixture trees through its
    path overrides. No adb call, no device, no network, no writes outside this folder, and no shipped
    string is renamed to find out whether the gate notices.

    The three cases that matter are the three faults measured on 2026-09-04, each of which a naive
    "does the token appear anywhere in the module" check would have missed:

      renamed        - the resource resolves, its value no longer carries the token. This is
                       `enable_audio`, which became "Audio" while the walk still expected
                       "Enable Audio".
      manifest-only  - the value matches but nothing under wear/src/main/java references it, so no
                       composable can render it. This is `app_name` = "FastMedia Wear", named only by
                       AndroidManifest.xml, which is why the `home` entry could never match and why
                       blaming the 192 dp screen for it was wrong.
      missing-screen - the entry names a composable that does not exist.

    The clean case is not decoration: a gate that fires on everything is as useless as one that fires
    on nothing, and it pins the ratchet's "at or below baseline passes" arithmetic.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-wear-walk-contract.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$gate = Join-Path $repoRoot 'scripts/quality/assert-wear-walk-contract.ps1'
$fixtures = Join-Path $PSScriptRoot 'fixtures'

$script:passed = 0
$script:failed = 0

function Invoke-Gate {
    param([string]$ScreenListName, [switch]$AsGate, [string]$SourceDir = 'src', [string]$ChangedFiles,
        [string]$ScreenListBaseline)
    $callArgs = @(
        '-NoProfile', '-File', $gate,
        '-ScreenList', (Join-Path $fixtures $ScreenListName),
        '-StringsFile', (Join-Path $fixtures 'strings.xml'),
        '-WearSource', (Join-Path $fixtures $SourceDir),
        '-BaselineFile', (Join-Path $fixtures 'baseline-zero.txt')
    )
    if ($AsGate) { $callArgs += '-Gate' }
    if ($ChangedFiles) { $callArgs += @('-ChangedFiles', $ChangedFiles) }
    if ($ScreenListBaseline) { $callArgs += @('-ScreenListBaseline', (Join-Path $fixtures $ScreenListBaseline)) }
    $output = & pwsh @callArgs 2>&1
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = ($output -join "`n") }
}

function Assert-Case {
    param([string]$Label, [int]$ExpectedExit, [string]$ExpectedPattern, $Result)
    $exitOk = ($Result.Exit -eq $ExpectedExit)
    $textOk = [string]::IsNullOrEmpty($ExpectedPattern) -or ($Result.Output -match $ExpectedPattern)
    if ($exitOk -and $textOk) {
        Write-Host "PASS | $Label"
        $script:passed++
        return
    }
    Write-Host "FAIL | $Label" -ForegroundColor Red
    Write-Host "     | expected exit $ExpectedExit, actual $($Result.Exit)" -ForegroundColor Red
    if (-not $textOk) { Write-Host "     | expected output to match: $ExpectedPattern" -ForegroundColor Red }
    Write-Host "     | output: $($Result.Output)" -ForegroundColor DarkGray
    $script:failed++
}

if (-not (Test-Path -LiteralPath $gate)) {
    Write-Host "FAIL | gate script not found: $gate" -ForegroundColor Red
    exit 1
}

Assert-Case -Label 'clean tree passes the gate' `
    -ExpectedExit 0 -ExpectedPattern 'PASS' `
    -Result (Invoke-Gate -ScreenListName 'screens-clean.json' -AsGate)

Assert-Case -Label 'renamed string value is reported' `
    -ExpectedExit 1 -ExpectedPattern "is not contained in R\.string\.good_marker" `
    -Result (Invoke-Gate -ScreenListName 'screens-renamed.json' -AsGate)

Assert-Case -Label 'resource no composable references is reported' `
    -ExpectedExit 1 -ExpectedPattern 'never referenced under' `
    -Result (Invoke-Gate -ScreenListName 'screens-manifest-only.json' -AsGate)

Assert-Case -Label 'entry naming a composable that does not exist is reported' `
    -ExpectedExit 1 -ExpectedPattern "screen 'GammaScreen' is not a composable" `
    -Result (Invoke-Gate -ScreenListName 'screens-missing-screen.json' -AsGate)

Assert-Case -Label 'expectRes that resolves to nothing is reported' `
    -ExpectedExit 1 -ExpectedPattern 'resolves to no string' `
    -Result (Invoke-Gate -ScreenListName 'screens-unresolved-res.json' -AsGate)

Assert-Case -Label 'without -Gate a divergence reports but does not fail the caller' `
    -ExpectedExit 0 -ExpectedPattern 'is not contained in' `
    -Result (Invoke-Gate -ScreenListName 'screens-renamed.json')

Assert-Case -Label 'a screen in neither list is reported' `
    -ExpectedExit 1 -ExpectedPattern 'neither walked nor excluded' `
    -Result (Invoke-Gate -ScreenListName 'screens-unclassified.json' -AsGate)

Assert-Case -Label 'a screen both walked and excluded is reported' `
    -ExpectedExit 1 -ExpectedPattern 'both walked and excluded' `
    -Result (Invoke-Gate -ScreenListName 'screens-both-lists.json' -AsGate)

Assert-Case -Label 'a reason outside the closed set is reported' `
    -ExpectedExit 1 -ExpectedPattern "reason 'because-i-said-so' is not one of" `
    -Result (Invoke-Gate -ScreenListName 'screens-bad-reason.json' -AsGate)

Assert-Case -Label 'a missing screen list is could-not-verify, not a defect' `
    -ExpectedExit 2 -ExpectedPattern 'could not verify' `
    -Result (Invoke-Gate -ScreenListName 'screens-does-not-exist.json' -AsGate)

# S2621 - the scoped mode. It exists to NOT report things, which is indistinguishable from a broken
# gate unless both halves are pinned: what it still catches, and what it deliberately lets past.
Assert-Case -Label 'scoped: the unscoped run over the same fixtures still reports the divergence' `
    -ExpectedExit 1 -ExpectedPattern 'BetaScreen : neither walked nor excluded' `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped.json' -SourceDir 'src-scoped' -AsGate)

Assert-Case -Label 'scoped: a divergence in a file the set does not name is not reported' `
    -ExpectedExit 0 -ExpectedPattern 'PASS' `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped.json' -SourceDir 'src-scoped' -AsGate `
        -ChangedFiles 'src-scoped/Alpha.kt')

Assert-Case -Label 'scoped: a divergence in a file the set names is reported' `
    -ExpectedExit 1 -ExpectedPattern 'BetaScreen : neither walked nor excluded' `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped.json' -SourceDir 'src-scoped' -AsGate `
        -ChangedFiles 'src-scoped/Beta.kt')

# S2723 - naming the screen list used to restore the full project-wide judgement, which refused a
# closure over a neighbour's unclassified screen every time a ticket added one of its own. Ownership
# is now per RECORD, so all four outcomes below are pinned with the screen list in the presented set.
Assert-Case -Label 'scoped: naming the screen list does not claim a record this edit left alone' `
    -ExpectedExit 0 -ExpectedPattern 'PASS' `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped.json' -SourceDir 'src-scoped' -AsGate `
        -ChangedFiles 'screens-scoped.json' -ScreenListBaseline 'screens-scoped-head.json')

Assert-Case -Label 'scoped: a record this edit added is still judged' `
    -ExpectedExit 1 -ExpectedPattern "screen 'GammaScreen' is not a composable" `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped-missing.json' -SourceDir 'src-scoped' -AsGate `
        -ChangedFiles 'screens-scoped-missing.json' -ScreenListBaseline 'screens-scoped-head.json')

Assert-Case -Label 'scoped: a record this edit deleted is still judged' `
    -ExpectedExit 1 -ExpectedPattern 'BetaScreen : neither walked nor excluded' `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped.json' -SourceDir 'src-scoped' -AsGate `
        -ChangedFiles 'screens-scoped.json' -ScreenListBaseline 'screens-scoped-head-excluded.json')

Assert-Case -Label 'scoped: an unobtainable pre-edit copy owns the whole contract' `
    -ExpectedExit 1 -ExpectedPattern 'BetaScreen : neither walked nor excluded' `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped.json' -SourceDir 'src-scoped' -AsGate `
        -ChangedFiles 'screens-scoped.json' -ScreenListBaseline 'screens-there-is-no-such-baseline.json')

# The rename channel: the stale entry names a composable that exists in no file, so it is
# attributable to no file - only the presence of a changed *Screen.kt in the set can claim it.
Assert-Case -Label 'scoped: a stale entry is claimed by a changed *Screen.kt in the set' `
    -ExpectedExit 1 -ExpectedPattern "screen 'GammaScreen' is not a composable" `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped-missing.json' -SourceDir 'src-scoped' -AsGate `
        -ChangedFiles 'wear/src/main/java/com/example/RenamedScreen.kt')

Assert-Case -Label 'scoped: a stale entry is not claimed by a set carrying no screen source' `
    -ExpectedExit 0 -ExpectedPattern 'PASS' `
    -Result (Invoke-Gate -ScreenListName 'screens-scoped-missing.json' -SourceDir 'src-scoped' -AsGate `
        -ChangedFiles 'src-scoped/Alpha.kt')

Write-Host ""
Write-Host "assert-wear-walk-contract.tests: $script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
exit 0
