#requires -Version 7.0
<#
.SYNOPSIS
    S1559 research probe: what surfaces if the listener-symmetry full scan covers every source set?

.DESCRIPTION
    The gate's full scan reads app_v2/src/main and wear/src/main only, while its delta mode (S1501)
    judges every non-test source set of both modules. This probe answers research item 1: how much
    imbalance lives in the source sets the full scan does not see, and in which files.

    The counting logic is copied verbatim from scripts/quality/assert-listener-symmetry.ps1 so the
    numbers are comparable with the gate's own. Read-only; writes nothing.

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

function Get-FileImbalance([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return 0 }
    $dObs = [Math]::Abs($regContentObserver.Matches($text).Count - $unregContentObserver.Matches($text).Count)
    $dRec = [Math]::Abs(($regReceiver.Matches($text).Count - $regReceiverNull.Matches($text).Count) - $unregReceiver.Matches($text).Count)
    $dList = [Math]::Abs($addListener.Matches($text).Count - $removeListener.Matches($text).Count)
    $dCb = [Math]::Abs(($addCallback.Matches($text).Count - $addCallbackLifecycle.Matches($text).Count) - $removeCallback.Matches($text).Count)
    $dObs2 = [Math]::Abs($addObserver.Matches($text).Count - $removeObserver.Matches($text).Count)
    return $dObs + $dRec + $dList + $dCb + $dObs2
}

function Get-DetailLine([string]$text) {
    $parts = @()
    $a = $regContentObserver.Matches($text).Count; $b = $unregContentObserver.Matches($text).Count
    if ($a -ne $b) { $parts += "ContentObserver: $a vs $b" }
    $a = $regReceiver.Matches($text).Count - $regReceiverNull.Matches($text).Count; $b = $unregReceiver.Matches($text).Count
    if ($a -ne $b) { $parts += "Receiver: $a vs $b" }
    $a = $addListener.Matches($text).Count; $b = $removeListener.Matches($text).Count
    if ($a -ne $b) { $parts += "Listener: $a vs $b" }
    $a = $addCallback.Matches($text).Count - $addCallbackLifecycle.Matches($text).Count; $b = $removeCallback.Matches($text).Count
    if ($a -ne $b) { $parts += "Callback: $a vs $b" }
    $a = $addObserver.Matches($text).Count; $b = $removeObserver.Matches($text).Count
    if ($a -ne $b) { $parts += "Observer: $a vs $b" }
    return ($parts -join ', ')
}

# Every source set of both modules except the test ones - the same scope delta mode already judges.
$sourceSets = @()
foreach ($module in @('app_v2', 'wear')) {
    $srcDir = Join-Path $repoRoot "$module/src"
    if (-not (Test-Path $srcDir)) { continue }
    foreach ($d in (Get-ChildItem -LiteralPath $srcDir -Directory)) {
        if ($d.Name -match '^(test|androidTest)') { continue }
        $sourceSets += [pscustomobject]@{ Module = $module; Name = $d.Name; Path = $d.FullName }
    }
}

$rows = @()
foreach ($set in $sourceSets) {
    $total = 0
    $files = @()
    Get-ChildItem -LiteralPath $set.Path -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' } |
        ForEach-Object {
            $text = Get-Content -LiteralPath $_.FullName -Raw
            $imb = Get-FileImbalance $text
            if ($imb -gt 0) {
                $total += $imb
                $rel = $_.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $files += [pscustomobject]@{ Path = $rel; Imbalance = $imb; Detail = (Get-DetailLine $text) }
            }
        }
    $rows += [pscustomobject]@{ Module = $set.Module; Name = $set.Name; Total = $total; Files = $files }
}

$baselineFile = Join-Path $repoRoot 'scripts/quality/listener-symmetry-baseline.txt'
$baseline = if (Test-Path $baselineFile) { [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim()) } else { -1 }

$mainTotal = ($rows | Where-Object { $_.Name -eq 'main' } | Measure-Object Total -Sum).Sum
$allTotal = ($rows | Measure-Object Total -Sum).Sum

Write-Host ''
Write-Host ("Committed baseline (src/main of both modules): {0}" -f $baseline)
Write-Host ("Recomputed src/main total                    : {0}" -f $mainTotal)
Write-Host ("Total across every non-test source set       : {0}" -f $allTotal)
Write-Host ("Increase a full-scope scan would report      : {0}" -f ($allTotal - $mainTotal))
Write-Host ''
Write-Host 'Per source set:'
foreach ($r in ($rows | Sort-Object -Property @{Expression = 'Module' }, @{Expression = 'Total'; Descending = $true })) {
    Write-Host ("  {0,-8} {1,-22} {2,4}  ({3} file(s))" -f $r.Module, $r.Name, $r.Total, $r.Files.Count)
}
Write-Host ''
Write-Host 'Files outside src/main carrying an imbalance:'
$outside = $rows | Where-Object { $_.Name -ne 'main' -and $_.Files.Count -gt 0 }
if (-not $outside) {
    Write-Host '  none'
} else {
    foreach ($r in $outside) {
        foreach ($f in ($r.Files | Sort-Object Imbalance -Descending)) {
            Write-Host ("  +{0,-3} {1}" -f $f.Imbalance, $f.Path)
            Write-Host ("        {0}" -f $f.Detail) -ForegroundColor DarkGray
        }
    }
}
Write-Host ''
exit 0
