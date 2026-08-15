#requires -Version 7.0
<#
.SYNOPSIS
    S1559 research probe: what does the listener-symmetry count become once three benign forms
    are discounted, and what genuinely remains outside src/main?

.DESCRIPTION
    Probe 01 measured the gap as-is: 115 in src/main, 8 more across the flavor source sets.
    Inspecting those eight showed half are not registrations at all. This probe quantifies three
    candidate discounts, each the same shape as the two the gate already applies
    (registerReceiver(null, ..) and onBackPressedDispatcher.addCallback):

      A  an `import` line - importing androidx.activity.addCallback is not a registration;
      B  lifecycle.addObserver(..) - the Lifecycle drops its observers when it is destroyed,
         exactly the reasoning S1433 recorded for onBackPressedDispatcher.addCallback;
      C  addEventListener - a DOM call living inside an HTML/JS string literal, never Kotlin.

    Prints the per-source-set count before and after, and lists what is still unbalanced outside
    src/main, so the re-seed covers only what a human decided to accept. Read-only.

.NOTES
    Exit codes:
      0 - the report was produced.
      2 - the repository roots could not be resolved.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
if (-not (Test-Path (Join-Path $repoRoot 'app_v2/src'))) {
    Write-Error "Cannot resolve the repository root from $PSScriptRoot" -ErrorAction Continue
    exit 2
}

$regContentObserver = [regex]'\bregisterContentObserver\b'
$unregContentObserver = [regex]'\bunregisterContentObserver\b'
$regReceiver = [regex]'\bregisterReceiver\b'
$unregReceiver = [regex]'\bunregisterReceiver\b'
$regReceiverNull = [regex]'\bregisterReceiver\s*\(\s*null\s*,'
$addListener = [regex]'\badd[A-Za-z0-9_]*Listener\b'
$removeListener = [regex]'\bremove[A-Za-z0-9_]*Listener\b'
$addCallback = [regex]'\badd[A-Za-z0-9_]*Callback\b'
$removeCallback = [regex]'\bremove[A-Za-z0-9_]*Callback\b'
$addCallbackLifecycle = [regex]'\bonBackPressedDispatcher\s*(?:\r?\n\s*)?\.\s*addCallback\b'
$addObserver = [regex]'\badd[A-Za-z0-9_]*Observer\b'
$removeObserver = [regex]'\bremove[A-Za-z0-9_]*Observer\b'

# Candidate discounts.
$addObserverLifecycle = [regex]'\blifecycle\s*(?:\r?\n\s*)?\.\s*addObserver\b'
$addListenerDom = [regex]'\baddEventListener\b'

# A discount must only shrink an imbalance in the leak direction. Subtracting from the add count
# and then taking the absolute difference does the opposite when the file DOES pair its call: the
# add drops to zero, the remove is still there, and a balanced file reports an imbalance of one.
# Measured on LauncherStatusStripManager.kt, which S1501 had already fixed.
function Get-Delta([int]$add, [int]$remove, [int]$discount) {
    $raw = $add - $remove
    if ($raw -le 0) { return [Math]::Abs($raw) }
    return [Math]::Max(0, $raw - $discount)
}

function Get-Imbalance([string]$text, [string]$mode) {
    if ([string]::IsNullOrEmpty($text)) { return 0 }
    $discounted = ($mode -ne 'current')
    $safe = ($mode -eq 'safe')
    if ($discounted) {
        # Discount A is a line filter, not a subtraction - an import line contributes nothing at all.
        $text = (($text -split "`n") | Where-Object { $_ -notmatch '^\s*import\s' }) -join "`n"
    }

    $dObs = [Math]::Abs($regContentObserver.Matches($text).Count - $unregContentObserver.Matches($text).Count)

    $aRec = $regReceiver.Matches($text).Count
    $dRec = if ($safe) { Get-Delta $aRec $unregReceiver.Matches($text).Count $regReceiverNull.Matches($text).Count }
            else { [Math]::Abs(($aRec - $regReceiverNull.Matches($text).Count) - $unregReceiver.Matches($text).Count) }

    $aList = $addListener.Matches($text).Count
    $domCount = if ($discounted) { $addListenerDom.Matches($text).Count } else { 0 }
    $dList = if ($safe) { Get-Delta $aList $removeListener.Matches($text).Count $domCount }
             else { [Math]::Abs(($aList - $domCount) - $removeListener.Matches($text).Count) }

    $aCb = $addCallback.Matches($text).Count
    $cbDiscount = $addCallbackLifecycle.Matches($text).Count
    $dCb = if ($safe) { Get-Delta $aCb $removeCallback.Matches($text).Count $cbDiscount }
           else { [Math]::Abs(($aCb - $cbDiscount) - $removeCallback.Matches($text).Count) }

    $aObs = $addObserver.Matches($text).Count
    $obsDiscount = if ($discounted) { $addObserverLifecycle.Matches($text).Count } else { 0 }
    $dObs2 = if ($safe) { Get-Delta $aObs $removeObserver.Matches($text).Count $obsDiscount }
             else { [Math]::Abs(($aObs - $obsDiscount) - $removeObserver.Matches($text).Count) }

    return $dObs + $dRec + $dList + $dCb + $dObs2
}

$sets = @()
foreach ($module in @('app_v2', 'wear')) {
    $srcDir = Join-Path $repoRoot "$module/src"
    if (-not (Test-Path $srcDir)) { continue }
    foreach ($d in (Get-ChildItem -LiteralPath $srcDir -Directory)) {
        if ($d.Name -match '^(test|androidTest)') { continue }
        $sets += [pscustomobject]@{ Module = $module; Name = $d.Name; Path = $d.FullName }
    }
}

$rows = @()
$remaining = @()
foreach ($set in $sets) {
    $cur = 0
    $naive = 0
    $safeTotal = 0
    Get-ChildItem -LiteralPath $set.Path -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' } |
        ForEach-Object {
            $text = Get-Content -LiteralPath $_.FullName -Raw
            $cur += Get-Imbalance $text 'current'
            $naive += Get-Imbalance $text 'naive'
            $s = Get-Imbalance $text 'safe'
            $safeTotal += $s
            if ($s -gt 0 -and $set.Name -ne 'main') {
                $rel = $_.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $remaining += [pscustomobject]@{ Path = $rel; Imbalance = $s }
            }
        }
    if ($cur -gt 0 -or $safeTotal -gt 0) {
        $rows += [pscustomobject]@{ Module = $set.Module; Name = $set.Name; Current = $cur; Naive = $naive; Safe = $safeTotal }
    }
}

$mainRows = @($rows | Where-Object { $_.Name -eq 'main' })

Write-Host ''
Write-Host 'Counts: current heuristic | naive subtraction | discount that cannot manufacture an imbalance'
Write-Host ("  src/main only            : {0,4} | {1,4} | {2,4}" -f ($mainRows | Measure-Object Current -Sum).Sum, ($mainRows | Measure-Object Naive -Sum).Sum, ($mainRows | Measure-Object Safe -Sum).Sum)
Write-Host ("  every non-test source set: {0,4} | {1,4} | {2,4}" -f ($rows | Measure-Object Current -Sum).Sum, ($rows | Measure-Object Naive -Sum).Sum, ($rows | Measure-Object Safe -Sum).Sum)
Write-Host ''
Write-Host 'Per source set carrying anything:'
foreach ($r in ($rows | Sort-Object Current -Descending)) {
    Write-Host ("  {0,-8} {1,-18} {2,4} | {3,4} | {4,4}" -f $r.Module, $r.Name, $r.Current, $r.Naive, $r.Safe)
}
Write-Host ''
Write-Host 'Still unbalanced outside src/main under the safe discount:'
if ($remaining.Count -eq 0) {
    Write-Host '  none'
} else {
    foreach ($f in ($remaining | Sort-Object Imbalance -Descending)) {
        Write-Host ("  +{0,-3} {1}" -f $f.Imbalance, $f.Path)
    }
}
Write-Host ''
exit 0
