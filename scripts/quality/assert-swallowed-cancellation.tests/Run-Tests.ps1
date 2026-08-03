# Run-Tests.ps1 (S1363) - regression suite proving the swallowed-cancellation gate actually fires.
#
# The predicate is multi-step (chain walk + coroutine-context walk + helper recognition), so it
# has three independent places it can lie, and a gate that has only ever been seen green proves
# nothing (S1244/S1070/S1347 lesson). Both cures are load-bearing: the first draft of this rule
# knew only the `catch (e: CancellationException)` arm and reported 24 false positives against
# files that cure the same defect with core/util/CoroutineExt.kt's helper instead.
#
# Unit level only: Measure-SwallowedCancellationText is called directly on synthetic strings.
# Hermetic, no file I/O, no CODE.LOCK needed. Plus a live regression asserting the real
# app_v2/src/main tree stays at or under the committed baseline.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-swallowed-cancellation.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   could not verify - the baseline file is missing.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/source-matchers.ps1')

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

Write-Host 'Unit level: Measure-SwallowedCancellationText branch coverage' -ForegroundColor Yellow

$suspendBroad = @'
class Foo {
    suspend fun load() {
        try {
            fetch()
        } catch (e: Exception) {
            Timber.e(e, "failed")
        }
    }
}
'@
Assert-That 'U1 broad catch in a suspend fun -> 1' `
    ((Measure-SwallowedCancellationText $suspendBroad) -eq 1) "expected 1, got $(Measure-SwallowedCancellationText $suspendBroad)"

$blockingBroad = @'
class Foo {
    fun parse() {
        try {
            decode()
        } catch (e: Exception) {
            Timber.e(e, "failed")
        }
    }
}
'@
Assert-That 'U2 broad catch in a blocking fun -> 0' `
    ((Measure-SwallowedCancellationText $blockingBroad) -eq 0) "expected 0, got $(Measure-SwallowedCancellationText $blockingBroad)"

$curedByArm = @'
class Foo {
    suspend fun load() {
        try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "failed")
        }
    }
}
'@
Assert-That 'U3 chain with a CancellationException arm -> 0' `
    ((Measure-SwallowedCancellationText $curedByArm) -eq 0) "expected 0, got $(Measure-SwallowedCancellationText $curedByArm)"

$curedByHelper = @'
class Foo {
    suspend fun load() {
        try {
            fetch()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "failed")
        }
    }
}
'@
Assert-That 'U4 block opening with rethrowIfCancellation() -> 0' `
    ((Measure-SwallowedCancellationText $curedByHelper) -eq 0) "expected 0, got $(Measure-SwallowedCancellationText $curedByHelper)"

# The helper's KDoc requires it to be the FIRST statement. Anything before it has already run
# error-path work (a log line, a toast, a failure result) on what was only a cancellation.
$helperTooLate = @'
class Foo {
    suspend fun load() {
        try {
            fetch()
        } catch (e: Exception) {
            Timber.e(e, "failed")
            e.rethrowIfCancellation()
        }
    }
}
'@
Assert-That 'U5 helper called after a statement -> 1' `
    ((Measure-SwallowedCancellationText $helperTooLate) -eq 1) "expected 1, got $(Measure-SwallowedCancellationText $helperTooLate)"

$builderLambda = @'
class Foo {
    fun start() {
        scope.launch {
            try {
                fetch()
            } catch (e: Exception) {
                Timber.e(e, "failed")
            }
        }
    }
}
'@
Assert-That 'U6 broad catch inside launch {} of a non-suspend fun -> 1' `
    ((Measure-SwallowedCancellationText $builderLambda) -eq 1) "expected 1, got $(Measure-SwallowedCancellationText $builderLambda)"

$withContextBody = @'
class Foo {
    override suspend fun listFiles(): Result {
        return withContext(Dispatchers.IO) {
            try {
                request()
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
}
'@
Assert-That 'U7 broad catch inside withContext {} -> 1' `
    ((Measure-SwallowedCancellationText $withContextBody) -eq 1) "expected 1, got $(Measure-SwallowedCancellationText $withContextBody)"

# One cure at the head of the chain covers every broad arm below it, so a chain that also
# catches Throwable must still count as a single fixed site, not two outstanding ones.
$throwableTail = @'
class Foo {
    suspend fun load() {
        try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "failed")
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            Timber.e(t, "escaped")
        }
    }
}
'@
Assert-That 'U8 Exception + Throwable arms under one cure -> 0' `
    ((Measure-SwallowedCancellationText $throwableTail) -eq 0) "expected 0, got $(Measure-SwallowedCancellationText $throwableTail)"

$uncuredThrowable = @'
class Foo {
    suspend fun load() {
        try {
            fetch()
        } catch (e: Exception) {
            Timber.e(e, "failed")
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            Timber.e(t, "escaped")
        }
    }
}
'@
Assert-That 'U9 uncured Exception + Throwable arms -> 2' `
    ((Measure-SwallowedCancellationText $uncuredThrowable) -eq 2) "expected 2, got $(Measure-SwallowedCancellationText $uncuredThrowable)"

$singleLine = @'
class Foo {
    suspend fun load() {
        try { fetch() }
        catch (e: Exception) { Timber.e(e, "failed") }
    }
}
'@
Assert-That 'U10 single-line try/catch in a suspend fun -> 1' `
    ((Measure-SwallowedCancellationText $singleLine) -eq 1) "expected 1, got $(Measure-SwallowedCancellationText $singleLine)"

$narrowCatch = @'
class Foo {
    suspend fun load() {
        try {
            fetch()
        } catch (e: IOException) {
            Timber.e(e, "failed")
        }
    }
}
'@
Assert-That 'U11 narrow catch -> 0' `
    ((Measure-SwallowedCancellationText $narrowCatch) -eq 0) "expected 0, got $(Measure-SwallowedCancellationText $narrowCatch)"

Assert-That 'U12 empty text -> 0' ((Measure-SwallowedCancellationText '') -eq 0) 'expected 0'

Write-Host ''
Write-Host 'Live regression: the real tree stays at or under the committed baseline' -ForegroundColor Yellow

$baselineFile = Join-Path $repoRoot 'scripts/quality/swallowed-cancellation-baseline.txt'
if (-not (Test-Path -LiteralPath $baselineFile)) {
    Write-Error "swallowed-cancellation baseline missing at $baselineFile - seed it with -UpdateBaseline." -ErrorAction Continue
    exit 2
}

$gateScript = Join-Path $repoRoot 'scripts/quality/assert-swallowed-cancellation.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }
& $pwshExe -NoProfile -File $gateScript -Gate | Out-Null
Assert-That 'L1 full-tree gate is green' ($LASTEXITCODE -eq 0) "gate exited $LASTEXITCODE"

Write-Host ''
Write-Host ("assert-swallowed-cancellation tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
