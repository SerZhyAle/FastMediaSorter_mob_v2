# Run-Tests.ps1 (S2563) - regression suite for scripts/builders/build-failure-digest.contract.ps1.
#
# The invariant being guarded: RANK BEATS LINE ORDER. S2377 put the install refusal between the
# compiler error and the failed-task token, and the three anchors do not arrive in rank order - the
# installer prints INSTALL_FAILED_* long before gradle prints the task line it outranks - so "the first
# line wins" and "the best line wins" are different answers and only the second one is the contract.
# Getting that wrong does not break a build and fails no gate: it silently names the wrong cause in the
# thing the operator reads INSTEAD of the log, which is exactly how a refused install read as a failed
# Room migration for two days (S2377).
#
# Within one rank the first line in log order still wins - that tiebreak is deliberately order
# dependent, so the reversal cases below assert the winning RANK is stable, never that the winning
# line is.
#
# Fixtures. The three logs in scripts/builders/testdata/ are read from disk; they existed unread since
# May 2026 and this suite is what makes them live. The two S2377 ranking logs are inlined verbatim
# instead: their home is PLAN/S2377_.../evidence/, PLAN/ is archived at the release, and a suite whose
# input is an archived path goes green having observed nothing (the failure class of S2411).
#
# Hermetic: the subject is a dot-source-only pure parser, so nothing here builds, installs or reads a
# real build log.
#
# Usage:  pwsh -NoProfile -File scripts/builders/build-failure-digest.contract.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed, or a required fixture is missing from scripts/builders/testdata/.
#       A missing fixture is a FAIL and not a "could not verify": the file is tracked repository
#       content, so its absence is a broken tree, not the incomplete environment exit 2 stands for.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/builders/build-failure-digest.contract.ps1')

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

$script:testdata = Join-Path $repoRoot 'scripts/builders/testdata'

function Get-Fixture([string]$name) {
    $path = Join-Path $script:testdata $name
    if (-not (Test-Path -LiteralPath $path)) {
        Assert-That "fixture $name is present" $false "not found at $path"
        return @()
    }
    return @(Get-Content -LiteralPath $path)
}

# Reversal is how rank-beats-order is observed: the same lines, the opposite arrival sequence.
function Get-Reversed([string[]]$lines) {
    $copy = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $lines) { $copy.Add($line) }
    $copy.Reverse()
    return $copy.ToArray()
}

# Verbatim from PLAN/S2377_bugfix-fam-install-downgrade-reads-as-migration-failure/evidence/
# build_rank_install_vs_task.log. The task line is printed FIRST and the refusal that outranks it
# second - the whole point of the case.
$installVsTask = @(
    '> Task :app_v2:connectedStandardDebugAndroidTest FAILED',
    '04:53:24 E/SplitApkInstallerBase: Error: INSTALL_FAILED_VERSION_DOWNGRADE: Downgrade detected: Update version code 111 is older than current 222',
    'FAILURE: Build failed with an exception.',
    'BUILD FAILED in 1s'
)

# Verbatim from the same folder, build_rank_compiler_wins.log. Both lower ranks are printed ahead of
# the compiler error that outranks them.
$compilerWins = @(
    '> Task :app_v2:connectedStandardDebugAndroidTest FAILED',
    '04:53:24 E/SplitApkInstallerBase: Error: INSTALL_FAILED_VERSION_DOWNGRADE: Downgrade detected: Update version code 111 is older than current 222',
    'e: file:///P:/x/Demo.kt:42:5: Unresolved reference: missingSymbol',
    'FAILURE: Build failed with an exception.',
    'BUILD FAILED in 1s'
)

Write-Host "build-failure-digest.contract regression suite" -ForegroundColor Cyan

# --- digest shape ------------------------------------------------------------------------------
# The field order is contract, not cosmetics: the strategic spec asserts these tokens verbatim.
$empty = New-BuildFailureDigest

Assert-That 'empty digest keeps the contract field order' `
(($empty.Keys -join ',') -eq 'command,exitCode,firstActionableFailure,rawLogPath,verdict') `
"got '$($empty.Keys -join ',')'"

Assert-That 'firstActionableFailure keeps the contract field order' `
(($empty.firstActionableFailure.Keys -join ',') -eq 'module,flavor,file,line,message') `
"got '$($empty.firstActionableFailure.Keys -join ',')'"

Assert-That 'empty digest defaults to blocked with the blocked exit code' `
($empty.verdict -eq 'blocked' -and $empty.exitCode -eq 20) `
"verdict=$($empty.verdict) exitCode=$($empty.exitCode)"

# --- no input ----------------------------------------------------------------------------------
# Anti-stale-success guard (strategic S0312 sections 2.4 and 11.3): nothing to read must never read
# as success.
$noLines = ConvertFrom-BuildFailureLog -LogLines @()
Assert-That 'an empty log stays blocked' `
($noLines.verdict -eq 'blocked' -and $null -eq $noLines.firstActionableFailure.message) `
"verdict=$($noLines.verdict)"

$absent = ConvertFrom-BuildFailureLog
Assert-That 'an absent -LogLines stays blocked' `
($absent.verdict -eq 'blocked') "verdict=$($absent.verdict)"

$withNulls = ConvertFrom-BuildFailureLog -LogLines @($null, 'BUILD SUCCESSFUL in 1s', $null)
Assert-That 'null lines are skipped without throwing' `
($withNulls.verdict -eq 'success') "verdict=$($withNulls.verdict)"

# --- fixture: a real compile failure ------------------------------------------------------------
$middleLines = Get-Fixture 'build-failure-middle.log'
$middle = ConvertFrom-BuildFailureLog -LogLines $middleLines

Assert-That 'compiler error outranks the failed-task token' `
($middle.firstActionableFailure.message -eq 'Unresolved reference: missingSymbol') `
"message='$($middle.firstActionableFailure.message)'"

# Two 'e:' lines are present; the second must not overwrite the first.
Assert-That 'the FIRST compiler error wins, not the last' `
($middle.firstActionableFailure.line -eq 42) "line=$($middle.firstActionableFailure.line)"

# The fixture opens with a 'w:' warning on the same file - a warning is not an actionable failure.
Assert-That 'a w: warning line is never actionable' `
($middle.firstActionableFailure.message -notmatch 'warning') `
"message='$($middle.firstActionableFailure.message)'"

Assert-That 'file comes from the e: line' `
($middle.firstActionableFailure.file -eq 'P:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/Demo.kt') `
"file='$($middle.firstActionableFailure.file)'"

Assert-That 'module is derived from the failed-task line' `
($middle.firstActionableFailure.module -eq 'app_v2') `
"module='$($middle.firstActionableFailure.module)'"

Assert-That 'flavor is derived from the failed-task line' `
($middle.firstActionableFailure.flavor -eq 'standard') `
"flavor='$($middle.firstActionableFailure.flavor)'"

Assert-That 'a failure log reads as failure' `
($middle.verdict -eq 'failure') "verdict=$($middle.verdict)"

# The parser never sets exitCode - build-failure-digest.ps1 maps verdict to exit code and owns it.
# Pinned so a future edit cannot quietly move that ownership into the pure parser.
Assert-That 'the parser leaves exitCode to its caller' `
($middle.exitCode -eq 20) "exitCode=$($middle.exitCode)"

# Reversed, the winning RANK must not move. The winning LINE does - within one rank the first line in
# log order wins, so the reversed run picks the other compiler error, which is correct, not a defect.
$middleReversed = ConvertFrom-BuildFailureLog -LogLines (Get-Reversed $middleLines)
Assert-That 'reversed: a compiler error still outranks the task token' `
($middleReversed.firstActionableFailure.message -eq 'Type mismatch: inferred type is Int but String was expected') `
"message='$($middleReversed.firstActionableFailure.message)'"

# --- fixture: success and truncation ------------------------------------------------------------
$success = ConvertFrom-BuildFailureLog -LogLines (Get-Fixture 'build-success.log')
Assert-That 'a successful log reads as success' `
($success.verdict -eq 'success') "verdict=$($success.verdict)"

Assert-That 'a successful log names no actionable failure' `
($null -eq $success.firstActionableFailure.message -and $null -eq $success.firstActionableFailure.module) `
"message='$($success.firstActionableFailure.message)'"

# Truncated: a started task, no FAILED marker, no FAILURE: block, no BUILD SUCCESSFUL. Neither
# outcome can be asserted, so it must stay blocked rather than fall through to either.
$truncated = ConvertFrom-BuildFailureLog -LogLines (Get-Fixture 'build-truncated.log')
Assert-That 'a truncated log stays blocked' `
($truncated.verdict -eq 'blocked' -and $truncated.exitCode -eq 20) `
"verdict=$($truncated.verdict) exitCode=$($truncated.exitCode)"

Assert-That 'a truncated log names no actionable failure' `
($null -eq $truncated.firstActionableFailure.message) `
"message='$($truncated.firstActionableFailure.message)'"

# --- S2377 rank 2: install refusal over failed task ----------------------------------------------
$rankInstall = ConvertFrom-BuildFailureLog -LogLines $installVsTask

Assert-That 'install refusal outranks a task line printed before it' `
($rankInstall.firstActionableFailure.message -like 'INSTALL_FAILED_VERSION_DOWNGRADE*') `
"message='$($rankInstall.firstActionableFailure.message)'"

# The detail behind the token is what turns "it failed" into "it refused, and here is the pair that
# made it refuse" - both version codes must survive into the message.
Assert-That 'the refusal message keeps both version codes' `
($rankInstall.firstActionableFailure.message -match '111' -and
$rankInstall.firstActionableFailure.message -match '222') `
"message='$($rankInstall.firstActionableFailure.message)'"

# The log tag and session time ahead of the token are noise and must not reach the operator.
Assert-That 'the refusal message drops the log tag ahead of the token' `
($rankInstall.firstActionableFailure.message -notmatch 'SplitApkInstallerBase') `
"message='$($rankInstall.firstActionableFailure.message)'"

Assert-That 'module still comes from the failed-task line the refusal outranked' `
($rankInstall.firstActionableFailure.module -eq 'app_v2') `
"module='$($rankInstall.firstActionableFailure.module)'"

# connectedStandardDebugAndroidTest carries no assemble/compile/lint/test prefix, so the variant
# regex does not reach it and flavor stays null. Pinned deliberately: widening that regex is a
# decision, and this line makes it a loud one rather than a silent change of the digest's contents.
Assert-That 'a connected* task yields no flavor' `
($null -eq $rankInstall.firstActionableFailure.flavor) `
"flavor='$($rankInstall.firstActionableFailure.flavor)'"

$rankInstallReversed = ConvertFrom-BuildFailureLog -LogLines (Get-Reversed $installVsTask)
Assert-That 'reversed: the refusal still outranks the task token' `
($rankInstallReversed.firstActionableFailure.message -eq $rankInstall.firstActionableFailure.message) `
"reversed='$($rankInstallReversed.firstActionableFailure.message)'"

# --- S2377 rank 1: compiler error over install refusal --------------------------------------------
# The two cannot honestly compete: a build that failed to compile produced no APK to refuse.
$rankCompiler = ConvertFrom-BuildFailureLog -LogLines $compilerWins

Assert-That 'compiler error outranks an install refusal printed before it' `
($rankCompiler.firstActionableFailure.message -eq 'Unresolved reference: missingSymbol') `
"message='$($rankCompiler.firstActionableFailure.message)'"

Assert-That 'the winning compiler error brings its file and line' `
($rankCompiler.firstActionableFailure.file -eq 'P:/x/Demo.kt' -and
$rankCompiler.firstActionableFailure.line -eq 42) `
"file='$($rankCompiler.firstActionableFailure.file)' line=$($rankCompiler.firstActionableFailure.line)"

$rankCompilerReversed = ConvertFrom-BuildFailureLog -LogLines (Get-Reversed $compilerWins)
Assert-That 'reversed: the compiler error still wins over both lower ranks' `
($rankCompilerReversed.firstActionableFailure.message -eq $rankCompiler.firstActionableFailure.message) `
"reversed='$($rankCompilerReversed.firstActionableFailure.message)'"

# A refusal with no compiler error and no task line is still actionable on its own.
$refusalOnly = ConvertFrom-BuildFailureLog -LogLines @(
    '04:53:24 E/SplitApkInstallerBase: Error: INSTALL_FAILED_INSUFFICIENT_STORAGE',
    'BUILD FAILED in 1s'
)
Assert-That 'a lone install refusal is actionable and reads as failure' `
($refusalOnly.firstActionableFailure.message -eq 'INSTALL_FAILED_INSUFFICIENT_STORAGE' -and
$refusalOnly.verdict -eq 'failure') `
"message='$($refusalOnly.firstActionableFailure.message)' verdict=$($refusalOnly.verdict)"

# --- S2377 rank 3: the failed-task token as last resort --------------------------------------------
$taskOnly = ConvertFrom-BuildFailureLog -LogLines @(
    '> Task :wear:testNoLegalDebugUnitTest FAILED',
    'BUILD FAILED in 4s'
)
Assert-That 'the task token is the message when nothing outranks it' `
($taskOnly.firstActionableFailure.message -eq ':wear:testNoLegalDebugUnitTest') `
"message='$($taskOnly.firstActionableFailure.message)'"

Assert-That 'module and flavor come off the task token' `
($taskOnly.firstActionableFailure.module -eq 'wear' -and
$taskOnly.firstActionableFailure.flavor -eq 'noLegal') `
"module='$($taskOnly.firstActionableFailure.module)' flavor='$($taskOnly.firstActionableFailure.flavor)'"

# A FAILURE: block with no anchor at all is still a failure - the verdict does not need an
# actionable line, only the actionable line needs a rank.
$bareFailure = ConvertFrom-BuildFailureLog -LogLines @('FAILURE: Build failed with an exception.')
Assert-That 'a bare FAILURE: block reads as failure with no actionable line' `
($bareFailure.verdict -eq 'failure' -and $null -eq $bareFailure.firstActionableFailure.message) `
"verdict=$($bareFailure.verdict) message='$($bareFailure.firstActionableFailure.message)'"

# --- summary ---------------------------------------------------------------------------------------
Write-Host ""
Write-Host "build-failure-digest.contract: $($script:pass) passed, $($script:fail) failed" `
    -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })

if ($script:fail -gt 0) { exit 1 }
exit 0
