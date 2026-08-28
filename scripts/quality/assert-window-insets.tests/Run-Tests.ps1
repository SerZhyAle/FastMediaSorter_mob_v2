# Run-Tests.ps1 (S1347) - regression suite proving Rule 17's window-insets gate actually fires.
#
# S1338 package I built scripts/quality/assert-window-insets.ps1 and baselined it at 28
# pre-existing sites. S1340's audit found no test anywhere asserting the gate's exit code flips
# to FAIL against a deliberately-broken case - only that the gate exists and currently reports
# clean. A gate that has only ever been seen green proves nothing (same lesson as S1244/S1070).
#
# Two levels, because a lexical rule has two places it can lie:
#   * unit level   - Measure-WindowInsetsText (scripts/quality/lib/source-matchers.ps1) is called
#                    directly on synthetic strings. Hermetic, no file I/O, covers every branch of
#                    the predicate including the documented comment-loophole.
#   * end-to-end   - the real scripts/quality/assert-window-insets.ps1 script is invoked with
#                    -Gate -ChangedFiles against a fixture file that must physically exist under
#                    app_v2/src/main/ for the rule's PathFilter to even consider it (S1338's
#                    -ChangedFiles delta is git-diff-based: working-copy count minus HEAD count).
#                    The fixture is written under the Code.Phone lock and removed in a finally block
#                    regardless of outcome, so a crash mid-test cannot leave stray source behind.
# Plus a live regression: the real app_v2/src/main tree must stay at/under the committed baseline.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-window-insets.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   could not acquire the Code.Phone domain for the end-to-end case - re-run once the lock is free.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

. (Join-Path $repoRoot 'scripts/quality/lib/source-matchers.ps1')
. (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1')

$gateScript = Join-Path $repoRoot 'scripts/quality/assert-window-insets.ps1'

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

# --- unit level: the predicate itself, no file I/O ---------------------------------------------
Write-Host 'Unit level: Measure-WindowInsetsText branch coverage' -ForegroundColor Yellow

$badListener = @'
class Foo {
    fun onCreate() {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets -> insets }
    }
}
'@
Assert-That 'U1 bare listener, no cutout/helper -> count > 0' `
    ((Measure-WindowInsetsText $badListener) -gt 0) 'expected > 0'

$badEdgeToEdge = @'
class Foo {
    fun onCreate() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}
'@
Assert-That 'U2 edge-to-edge with no listener, no cutout/helper -> count > 0' `
    ((Measure-WindowInsetsText $badEdgeToEdge) -gt 0) 'expected > 0'

$curedByCutout = @'
class Foo {
    fun onCreate() {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val cutout = insets.displayCutout()
            insets
        }
    }
}
'@
Assert-That 'U3 listener that names displayCutout() -> count 0' `
    ((Measure-WindowInsetsText $curedByCutout) -eq 0) 'expected 0'

$curedByHelper = @'
class Foo {
    fun onCreate() {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets -> insets }
        binding.root.applySystemBarInsetPadding()
    }
}
'@
Assert-That 'U4 listener that delegates to applySystemBarInsetPadding() -> count 0' `
    ((Measure-WindowInsetsText $curedByHelper) -eq 0) 'expected 0'

$notASurface = @'
class Foo {
    fun onCreate() {
        binding.root.setPadding(0, 0, 0, 0)
    }
}
'@
Assert-That 'U5 no listener and no edge-to-edge call -> count 0 (exempt)' `
    ((Measure-WindowInsetsText $notASurface) -eq 0) 'expected 0'

$commentLoophole = @'
class Foo {
    // never reads displayCutout() - TODO fix
    fun onCreate() {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets -> insets }
    }
}
'@
Assert-That 'U6 displayCutout() named only in a comment still clears the file (documented limitation)' `
    ((Measure-WindowInsetsText $commentLoophole) -eq 0) 'expected 0 - script header documents this as accepted, not a bug'

# --- end-to-end: the real gate script, against a real (temporary) fixture ----------------------
Write-Host "`nEnd-to-end level: assert-window-insets.ps1 against a live fixture" -ForegroundColor Yellow

$fixtureRel = 'app_v2/src/main/java/com/sza/fastmediasorter/S1347WindowInsetsGateFixtureDoNotCommit.kt'
$fixtureAbs = Join-Path $repoRoot ($fixtureRel -replace '/', [System.IO.Path]::DirectorySeparatorChar)

function Invoke-Gate([string]$changedFile) {
    & $pwshExe -NoProfile -File $gateScript -Gate -ChangedFiles $changedFile *> $null
    return $LASTEXITCODE
}

$badFixtureBody = @'
package com.sza.fastmediasorter

// S1347: scratch fixture for assert-window-insets.tests - deleted by the harness in a finally
// block. If you are reading this in a committed file, the test crashed mid-run; delete it.
private fun s1347WindowInsetsFixture(view: android.view.View) {
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets -> insets }
}
'@

$curedFixtureBody = @'
package com.sza.fastmediasorter

// S1347: scratch fixture for assert-window-insets.tests - deleted by the harness in a finally
// block. If you are reading this in a committed file, the test crashed mid-run; delete it.
private fun s1347WindowInsetsFixture(view: android.view.View) {
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
        val cutout = insets.displayCutout()
        insets
    }
}
'@

# S2170: Code.Phone only - the fixture is one app_v2 source file, so holding the wear and scripts
# domains too would stall a watch or tooling edit for a test that cannot touch either.
$lock = Enter-AgentLock -Name 'Code' -Domains @('Code.Phone') -Reason 'S1347 assert-window-insets.tests end-to-end fixture'
if (-not $lock.Acquired) {
    Write-Error 'assert-window-insets.tests: could not acquire the Code.Phone domain - another session is editing. Re-run once free.' -ErrorAction Continue
    exit 2
}
try {
    [System.IO.File]::WriteAllText($fixtureAbs, $badFixtureBody)
    $badExit = Invoke-Gate $fixtureRel
    Assert-That 'E1 gate exits 1 on a new uncovered listener (untracked fixture, full count is new)' `
        ($badExit -eq 1) "expected 1, got $badExit"

    [System.IO.File]::WriteAllText($fixtureAbs, $curedFixtureBody)
    $curedExit = Invoke-Gate $fixtureRel
    Assert-That 'E2 gate exits 0 once the fixture names displayCutout()' `
        ($curedExit -eq 0) "expected 0, got $curedExit"
}
finally {
    Remove-Item -LiteralPath $fixtureAbs -Force -ErrorAction SilentlyContinue
    Exit-AgentLock -Name 'Code' -Domains @('Code.Phone')
    $stillThere = Test-Path -LiteralPath $fixtureAbs
    Assert-That 'E3 fixture removed - no stray source left behind' (-not $stillThere) 'fixture file still exists after cleanup'
}

# --- live regression: the real tree stays within its committed baseline -------------------------
Write-Host "`nLive regression: the real app_v2/src/main tree" -ForegroundColor Yellow
& $pwshExe -NoProfile -File $gateScript -Gate *> $null
$liveExit = $LASTEXITCODE
Assert-That 'L1 full-scan gate exits 0 against the real tree' ($liveExit -eq 0) "expected 0, got $liveExit"

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "assert-window-insets tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "assert-window-insets tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
