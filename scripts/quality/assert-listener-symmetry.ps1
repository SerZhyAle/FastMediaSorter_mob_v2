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

# S1559: ONE scope rule, read by both modes. The full scan used to list app_v2/src/main and
# wear/src/main while delta mode (S1501) already judged every non-test source set - so an unbalanced
# registration was refused on its way into a flavor set and then invisible to the integer baseline for
# good, which is a gate that reports PASS on what it declined to look at. Two lists drift apart; one
# predicate cannot, and a source set added later joins both modes at once.
$scopedModules = @('app_v2', 'wear')
function Test-InSymmetryScope([string]$path) {
    $p = $path -replace '\\', '/'
    return ($p -match '(app_v2|wear)/src/') -and ($p -notmatch '(app_v2|wear)/src/(test|androidTest)')
}

$scanRoots = @(foreach ($m in $scopedModules) {
    $srcDir = Join-Path $repoRoot "$m/src"
    if (Test-Path $srcDir) {
        Get-ChildItem -LiteralPath $srcDir -Directory |
            Where-Object { Test-InSymmetryScope ("$m/src/" + $_.Name) } |
            ForEach-Object { $_.FullName }
    }
})
$baselineFile = Join-Path $PSScriptRoot 'listener-symmetry-baseline.txt'

# S1559: every counting decision - patterns, discounts, per-file imbalance and the detail line - lives
# in this library so the regression suite can exercise it. The gate itself cannot be loaded for
# testing (it runs a repository scan on load) and delta mode needs git plus a path inside the
# repository, so a sandbox fixture can never reach either.
. (Join-Path $PSScriptRoot 'lib/listener-symmetry-count.ps1')

# S0850: delta mode - per-file imbalance growth vs HEAD over the changed files only. An edit
# that raises a file's imbalance fails; keeping or reducing it passes; other files' pre-existing
# imbalance is ignored (it is already in HEAD / the committed baseline).
if ($ChangedFiles) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    # S1501: expand BEFORE filtering. The filter used to run over the raw -ChangedFiles elements, and
    # pwsh -File binds a comma-joined list as ONE element - so a whole CSV was kept or dropped by
    # whether ANY path inside it matched. Same file, two answers: alone it was dropped and reported
    # clean, riding next to a src/main path it was measured and failed. A real leak
    # (LauncherStatusStripManager, an observer and an insets listener never removed) survived six
    # per-step runs that way, and only the ticket-wide run caught it - a false clean, which is the
    # dangerous direction.
    #
    # S1501: the scope is every source set of the two modules, not src/main alone. Delta mode asks
    # "did THIS change add an imbalance", which is a question about the edit and not about the
    # integer baseline below - and the file that leaked lived in a flavor source set.
    # S1559: the full scan now derives its roots from this same predicate, so the two modes cannot
    # disagree about what is in scope.
    $expanded = @(Expand-ChangedFiles -ChangedFiles $ChangedFiles)
    $scoped = @($expanded | Where-Object { Test-InSymmetryScope $_ })
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

    # S0850: the imbalance itself comes from the shared function; S1559 moved the -List detail there
    # too, so the printed numbers and the printed imbalance can no longer disagree.
    $fileImbalance = Get-FileImbalance $text
    if ($fileImbalance -gt 0) {
        $current += $fileImbalance
        if ($List) {
            $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
            $hits.Add(("{0} (imbalance: {1} | {2})" -f $rel, $fileImbalance, (Get-FileImbalanceDetail $text)))
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
