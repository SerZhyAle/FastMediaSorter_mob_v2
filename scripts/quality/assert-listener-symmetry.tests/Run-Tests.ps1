# Run-Tests.ps1 (S1559) - regression suite for the listener-symmetry gate.
#
# The gate sums, per Kotlin file, how far register/add calls outrun their unregister/remove
# counterparts, and ratchets that sum against a committed baseline. Two properties are pinned here
# because both were defects found by measurement rather than by a case:
#   * a DISCOUNT (a form that registers nothing, or registers something the framework releases on
#     its own) must only shrink an imbalance - subtracting it from the add count and then taking the
#     absolute difference reported a correctly paired file as imbalanced (LauncherStatusStripManager,
#     the file S1501 had just fixed);
#   * the three forms measured to be benign - an `import` line, lifecycle.addObserver, and a DOM
#     addEventListener inside an HTML string literal - must not count as registrations.
#
# The counting core is exercised through scripts/quality/lib/listener-symmetry-count.ps1 rather than
# through the gate: the gate runs a repository scan on load, and its delta mode needs git plus a path
# inside the repository, so a sandbox fixture can never reach either. The live repository scan is
# asserted separately at the end.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

. (Join-Path $repoRoot 'scripts/quality/lib/listener-symmetry-count.ps1')

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

function Assert-Imbalance([string]$name, [string]$text, [int]$expected) {
    $actual = Get-FileImbalance $text
    Assert-That $name ($actual -eq $expected) "expected $expected, got $actual"
}

# --- A: the defect the gate exists for. ---
Write-Host 'A: an unpaired registration is an imbalance, a paired one is not' -ForegroundColor Yellow
Assert-Imbalance 'A1 unpaired listener counts 1' @'
class Screen {
    fun onStart() { view.addScrollListener(cb) }
}
'@ 1
Assert-Imbalance 'A2 paired listener counts 0' @'
class Screen {
    fun onStart() { view.addScrollListener(cb) }
    fun onStop() { view.removeScrollListener(cb) }
}
'@ 0

# --- B: a discount must not manufacture an imbalance on a file that pairs the call. This is the
# shape that broke: subtracting the discounted add left "0 adds vs 1 remove" and reported 1. ---
Write-Host 'B: a discounted form that IS paired stays balanced' -ForegroundColor Yellow
Assert-Imbalance 'B1 paired lifecycle observer counts 0' @'
class StripManager(private val lifecycleOwner: LifecycleOwner) : DefaultLifecycleObserver {
    init { lifecycleOwner.lifecycle.addObserver(this) }
    fun detach() { lifecycleOwner.lifecycle.removeObserver(this) }
}
'@ 0
Assert-Imbalance 'B2 paired back-pressed callback stays balanced' @'
class Screen {
    fun bind() { onBackPressedDispatcher.addCallback(this) { } }
    fun unbind() { holder.removeCallback(this) }
}
'@ 0

# --- C: the three forms measured to be benign (S1559 strategic section 4). ---
Write-Host 'C: a benign form alone is not a registration' -ForegroundColor Yellow
Assert-Imbalance 'C1 an import line counts 0' @'
import androidx.activity.addCallback

class Screen
'@ 0
Assert-Imbalance 'C2 an unpaired lifecycle observer counts 0' @'
class TrayManager(private val lifecycleOwner: LifecycleOwner) : DefaultLifecycleObserver {
    init { lifecycleOwner.lifecycle.addObserver(this) }
}
'@ 0
Assert-Imbalance 'C3 a DOM addEventListener inside a string literal counts 0' @'
class Bridge {
    private val page = """
        <script>
          document.addEventListener('selectionchange', function() { })
        </script>
    """
}
'@ 0
Assert-Imbalance 'C4 an unpaired back-pressed callback counts 0' @'
class Screen {
    fun bind() { onBackPressedDispatcher.addCallback(this) { } }
}
'@ 0

# --- D: a discount must not hide a real leak that sits next to it. ---
Write-Host 'D: a benign form does not cover an unpaired registration beside it' -ForegroundColor Yellow
Assert-Imbalance 'D1 lifecycle observer plus a leaked observer counts 1' @'
class Manager(private val lifecycleOwner: LifecycleOwner) : DefaultLifecycleObserver {
    init {
        lifecycleOwner.lifecycle.addObserver(this)
        registry.addChangeObserver(other)
    }
}
'@ 1

# --- E: more removes than adds is not a leak and must not be inflated by a discount. ---
Write-Host 'E: an over-removed file is reported by distance, not by discount' -ForegroundColor Yellow
Assert-Imbalance 'E1 two removes against one add counts 1' @'
class Screen {
    fun onStart() { view.addScrollListener(cb) }
    fun onStop() { view.removeScrollListener(cb); view.removeScrollListener(other) }
}
'@ 1

# --- F: the scope rule both modes read. A flavor source set is in; a test source set is out. ---
Write-Host 'F: the scope rule admits flavor source sets and refuses test ones' -ForegroundColor Yellow
$gate = Join-Path $repoRoot 'scripts/quality/assert-listener-symmetry.ps1'
$gateText = Get-Content -LiteralPath $gate -Raw
Assert-That 'F1 the gate derives its roots instead of listing src/main' `
    ($gateText -notmatch "Join-Path \`$repoRoot 'app_v2/src/main'") 'src/main is still a literal scan root'
Assert-That 'F2 both modes call the one scope predicate' `
    (([regex]'Test-InSymmetryScope').Matches($gateText).Count -ge 3) 'the predicate is not read by both modes'

# --- G: live regression - the repository scan stays at or below its baseline. ---
Write-Host 'G: the repository scan is at or below the committed baseline' -ForegroundColor Yellow
& $pwshExe -NoProfile -File $gate -Gate *> $null
$gateExit = $LASTEXITCODE
Assert-That 'G1 repo scan exits 0' ($gateExit -eq 0) "expected 0, got $gateExit"

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "assert-listener-symmetry tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "assert-listener-symmetry tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
