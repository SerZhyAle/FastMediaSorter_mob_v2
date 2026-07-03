#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: raw-int PackageManager flag overloads in src/main must never grow (target: 0).

.DESCRIPTION
    Part of S0467. `PackageManager.getPackageInfo(String, Int)`,
    `getApplicationInfo(String, Int)`, `queryIntentActivities(Intent, Int)` and
    `resolveActivity(Intent, Int)` were deprecated in API 33 (Tiramisu) in favor of the
    type-safe `*Flags.of(Long)` overloads. New code keeps copying the deprecated raw-int
    pattern because it is shorter. The project routes every call through the compat seam
    `util/PackageManagerCompat.kt` (`getPackageInfoCompat` / `getApplicationInfoCompat` /
    `queryIntentActivitiesCompat` / `resolveActivityCompat`), which holds the single
    `Build.VERSION` branch.

    This detector flags `<method>(.., <flag>)` raw-int call sites (two args, comma). The
    `*Compat` wrappers are NOT matched (the trailing `(` cannot follow the `Compat` suffix),
    and the compat seam file itself is allow-listed. The `Intent.resolveActivity(PackageManager)`
    overload (single arg, not deprecated) is not matched.

    Baseline lives in scripts/quality/deprecated-pm-flags-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    # S0848 Phase 04: judge only the growth these files introduce (working vs HEAD) instead of a
    # full src/main scan. Preserves the "must not grow" (target 0) guarantee for the change.
    [Parameter(ParameterSetName = 'Gate')][string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mainRoot = Join-Path $repoRoot 'app_v2/src/main'
$baselineFile = Join-Path $PSScriptRoot 'deprecated-pm-flags-baseline.txt'

# <method>( <no nested parens / no newline> , ...  -> raw-int two-arg overloads.
# `*Compat(` cannot match (suffix breaks the `\s*\(` anchor). Single-arg Intent.resolveActivity unmatched.
$rx = [regex]'\b(getPackageInfo|getApplicationInfo|queryIntentActivities|resolveActivity)\s*\([^()\r\n]*,'

# S0848 Phase 04: delta mode - judge only growth introduced by the changed files (working vs
# HEAD), so post-change -ScopeToFile gets a real verdict instead of an advisory full scan. Same
# regex as the full scan; the compat seam (PackageManagerCompat.kt) stays allow-listed.
if ($ChangedFiles) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    $scoped = @($ChangedFiles | Where-Object { ($_ -replace '\\', '/') -match 'app_v2/src/main/' })
    $countFn = { param($t) $rx.Matches($t).Count }
    $d = Measure-ChangedFileGrowth -ChangedFiles $scoped -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn -ExcludeNames @('PackageManagerCompat.kt')
    Write-Host ("deprecated-pm-flags [delta over changed files]: new occurrences {0}" -f $d.Growth)
    if ($Gate -and $d.Growth -gt 0) {
        foreach ($p in $d.PerFile) { if ($p.New -gt 0) { Write-Host ("  +{0} in {1}" -f $p.New, $p.Path) } }
        Write-Host "FAIL: new raw-int PackageManager flag overloads introduced in the changed file(s). Use the *Compat helpers in util/PackageManagerCompat.kt."
        exit 1
    }
    exit 0
}

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
$files = Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
    Where-Object {
        $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' -and
        $_.Name -ne 'PackageManagerCompat.kt'
    }
foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    if ([string]::IsNullOrEmpty($text)) { continue }
    $matches = $rx.Matches($text)
    $current += $matches.Count
    if ($List -and $matches.Count -gt 0) {
        $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        foreach ($m in $matches) {
            $lineNo = ($text.Substring(0, $m.Index) -split "`n").Count
            $hits.Add(("{0}:{1}" -f $rel, $lineNo))
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
        Write-Host "deprecated-pm-flags baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "deprecated-pm-flags baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "deprecated-pm-flags baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Route the call through util/PackageManagerCompat.kt (*Compat helpers) instead of the raw-int overload."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "deprecated-pm-flags: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("deprecated-pm-flags in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: raw-int PackageManager flag overloads grew above baseline. Use the *Compat helpers in util/PackageManagerCompat.kt."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
