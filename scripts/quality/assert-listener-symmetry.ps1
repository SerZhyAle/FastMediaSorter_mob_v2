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
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    # S0850: when set, judge only the imbalance growth these files introduce (working vs HEAD)
    # instead of a full scan. A balance gate ratchets per file, so per-file growth preserves
    # the "must not degrade" guarantee for the change.
    [Parameter(ParameterSetName = 'Gate')][string[]]$ChangedFiles
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

# S0850: the per-file imbalance is this gate's unit of count. Both the full scan and the
# delta mode call this, so they judge byte-for-byte the same logic.
function Get-FileImbalance([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return 0 }
    $dObs = [Math]::Abs($regContentObserver.Matches($text).Count - $unregContentObserver.Matches($text).Count)
    $dRec = [Math]::Abs(($regReceiver.Matches($text).Count - $regReceiverNull.Matches($text).Count) - $unregReceiver.Matches($text).Count)
    $dList = [Math]::Abs($addListener.Matches($text).Count - $removeListener.Matches($text).Count)
    $dCb = [Math]::Abs($addCallback.Matches($text).Count - $removeCallback.Matches($text).Count)
    $dObs2 = [Math]::Abs($addObserver.Matches($text).Count - $removeObserver.Matches($text).Count)
    return $dObs + $dRec + $dList + $dCb + $dObs2
}

# S0850: delta mode - per-file imbalance growth vs HEAD over the changed files only. An edit
# that raises a file's imbalance fails; keeping or reducing it passes; other files' pre-existing
# imbalance is ignored (it is already in HEAD / the committed baseline).
if ($ChangedFiles) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    $scoped = @($ChangedFiles | Where-Object { ($_ -replace '\\', '/') -match '(app_v2|wear)/src/main/' })
    $countFn = { param($t) Get-FileImbalance $t }
    $d = Measure-ChangedFileGrowth -ChangedFiles $scoped -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn
    Write-Host ("listener-symmetry [delta over changed files]: new imbalance {0}" -f $d.Growth)
    if ($Gate -and $d.Growth -gt 0) {
        foreach ($p in $d.PerFile) { if ($p.New -gt 0) { Write-Host ("  +{0} in {1}" -f $p.New, $p.Path) } }
        Write-Host "FAIL: listener/observer/receiver/callback imbalance grew in the changed file(s). Pair every add/register with its remove/unregister on the symmetric lifecycle edge."
        exit 1
    }
    exit 0
}

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

    # S0850: the imbalance itself comes from the shared function; the per-category counts
    # below are recomputed only for -List display detail.
    $fileImbalance = Get-FileImbalance $text
    if ($fileImbalance -gt 0) {
        $current += $fileImbalance
        if ($List) {
            $cRegObs = $regContentObserver.Matches($text).Count
            $cUnregObs = $unregContentObserver.Matches($text).Count
            $cRegRec = $regReceiver.Matches($text).Count - $regReceiverNull.Matches($text).Count
            $cUnregRec = $unregReceiver.Matches($text).Count
            $cAddList = $addListener.Matches($text).Count
            $cRemoveList = $removeListener.Matches($text).Count
            $cAddCb = $addCallback.Matches($text).Count
            $cRemoveCb = $removeCallback.Matches($text).Count
            $cAddObs = $addObserver.Matches($text).Count
            $cRemoveObs = $removeObserver.Matches($text).Count
            $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
            $details = [System.Collections.Generic.List[string]]::new()
            if ($cRegObs -ne $cUnregObs) { $details.Add("ContentObserver: $cRegObs vs $cUnregObs") }
            if ($cRegRec -ne $cUnregRec) { $details.Add("Receiver: $cRegRec vs $cUnregRec") }
            if ($cAddList -ne $cRemoveList) { $details.Add("Listener: $cAddList vs $cRemoveList") }
            if ($cAddCb -ne $cRemoveCb) { $details.Add("Callback: $cAddCb vs $cRemoveCb") }
            if ($cAddObs -ne $cRemoveObs) { $details.Add("Observer: $cAddObs vs $cRemoveObs") }
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
