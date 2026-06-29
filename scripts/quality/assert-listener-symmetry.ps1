#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: listener and callback registration symmetry must never degrade.

.DESCRIPTION
    Checks balance of register/unregister and add/remove listener methods in Kotlin files.
    Calculates total imbalance as the sum of absolute differences of:
      - registerContentObserver vs unregisterContentObserver
      - registerReceiver (excluding null receiver) vs unregisterReceiver
      - add*Listener vs remove*Listener
      - add*Callback vs remove*Callback
      - add*Observer vs remove*Observer

    Baseline lives in scripts/quality/listener-symmetry-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every unbalanced file with details.
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Report')][switch]$List
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scanRoots = @(
    Join-Path $repoRoot 'app_v2/src/main'
    Join-Path $repoRoot 'wear/src/main'
)
$baselineFile = Join-Path $PSScriptRoot 'listener-symmetry-baseline.txt'

$regContentObserver = [regex]'\bregisterContentObserver\b'
$unregContentObserver = [regex]'\bunregisterContentObserver\b'

$regReceiver = [regex]'\bregisterReceiver\b'
$unregReceiver = [regex]'\bunregisterReceiver\b'
$regReceiverNull = [regex]'\bregisterReceiver\s*\(\s*null\s*,'

$addListener = [regex]'\badd[A-Za-z0-9_]*Listener\b'
$removeListener = [regex]'\bremove[A-Za-z0-9_]*Listener\b'

$addCallback = [regex]'\badd[A-Za-z0-9_]*Callback\b'
$removeCallback = [regex]'\bremove[A-Za-z0-9_]*Callback\b'

$addObserver = [regex]'\badd[A-Za-z0-9_]*Observer\b'
$removeObserver = [regex]'\bremove[A-Za-z0-9_]*Observer\b'

$files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
foreach ($root in $scanRoots) {
    if (Test-Path $root) {
        Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' } |
            ForEach-Object { $files.Add($_) }
    }
}

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()

foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    if ([string]::IsNullOrEmpty($text)) { continue }

    $cRegObs = $regContentObserver.Matches($text).Count
    $cUnregObs = $unregContentObserver.Matches($text).Count
    $dObs = [Math]::Abs($cRegObs - $cUnregObs)

    $cRegRec = $regReceiver.Matches($text).Count - $regReceiverNull.Matches($text).Count
    $cUnregRec = $unregReceiver.Matches($text).Count
    $dRec = [Math]::Abs($cRegRec - $cUnregRec)

    $cAddList = $addListener.Matches($text).Count
    $cRemoveList = $removeListener.Matches($text).Count
    $dList = [Math]::Abs($cAddList - $cRemoveList)

    $cAddCb = $addCallback.Matches($text).Count
    $cRemoveCb = $removeCallback.Matches($text).Count
    $dCb = [Math]::Abs($cAddCb - $cRemoveCb)

    $cAddObs = $addObserver.Matches($text).Count
    $cRemoveObs = $removeObserver.Matches($text).Count
    $dObs2 = [Math]::Abs($cAddObs - $cRemoveObs)

    $fileImbalance = $dObs + $dRec + $dList + $dCb + $dObs2
    if ($fileImbalance -gt 0) {
        $current += $fileImbalance
        if ($List) {
            $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
            $details = [System.Collections.Generic.List[string]]::new()
            if ($dObs -gt 0) { $details.Add("ContentObserver: $cRegObs vs $cUnregObs") }
            if ($dRec -gt 0) { $details.Add("Receiver: $cRegRec vs $cUnregRec") }
            if ($dList -gt 0) { $details.Add("Listener: $cAddList vs $cRemoveList") }
            if ($dCb -gt 0) { $details.Add("Callback: $cAddCb vs $cRemoveCb") }
            if ($dObs2 -gt 0) { $details.Add("Observer: $cAddObs vs $cRemoveObs") }
            $hits.Add(("{0} (imbalance: {1} | {2})" -f $rel, $fileImbalance, ($details -join ", ")))
        }
    }
}

if ($List) {
    foreach ($h in $hits) { Write-Host $h }
    Write-Host ''
}

if ($PSCmdlet.ParameterSetName -eq 'Update') {
    if (-not (Test-Path $baselineFile)) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "listener-symmetry baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "listener-symmetry baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "listener-symmetry baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Mismatched listeners grew - ensure every add/register has a matching remove/unregister."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "listener-symmetry: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("listener-symmetry: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: Listener/observer/receiver/callback symmetry grew above baseline. Balance all registrations."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
