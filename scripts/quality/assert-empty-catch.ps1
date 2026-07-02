#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: the number of swallowing catch blocks in src/main must never grow.

.DESCRIPTION
    Part of S0383 neuroslop hygiene. Counts catch blocks that swallow an
    exception with no recovery: an empty `catch (..) {}` or a `catch (..) { .. }`
    whose body is exactly one comment (line `//` or block) and no executable
    statement. Each site is technical debt to be replaced with the project
    recovery standard (ADR-1: recovery / safe default / justified degradation
    logged at the correct level). The count may only go DOWN; growth fails.

    Baseline lives in scripts/quality/empty-catch-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-empty-catch.ps1
    pwsh -NoProfile -File scripts/quality/assert-empty-catch.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-empty-catch.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-empty-catch.ps1 -List
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    # S0850: when set, judge only the growth these files introduce (working vs HEAD)
    # instead of a full src/main scan. Preserves the "must not grow" guarantee for the change.
    [Parameter(ParameterSetName = 'Gate')][string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mainRoot = Join-Path $repoRoot 'app_v2/src/main'
$baselineFile = Join-Path $PSScriptRoot 'empty-catch-baseline.txt'

# catch (..) { }  OR  catch (..) { <single line/block comment only> }
$rx = [regex]'catch\s*\([^)]*\)\s*\{\s*(?:(?://[^\r\n]*)|(?:/\*[\s\S]*?\*/))?\s*\}'

# S0850: delta mode - same regex as the full scan, applied per changed file (working vs HEAD),
# mirroring the flavor-flags/deprecated-pm wiring from S0848 Phase 04.
if ($ChangedFiles) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    $scoped = @($ChangedFiles | Where-Object { ($_ -replace '\\', '/') -match 'app_v2/src/main/' })
    $countFn = { param($t) $rx.Matches($t).Count }
    $d = Measure-ChangedFileGrowth -ChangedFiles $scoped -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn
    Write-Host ("empty-catch [delta over changed files]: new occurrences {0}" -f $d.Growth)
    if ($Gate -and $d.Growth -gt 0) {
        foreach ($p in $d.PerFile) { if ($p.New -gt 0) { Write-Host ("  +{0} in {1}" -f $p.New, $p.Path) } }
        Write-Host "FAIL: new swallowing catch introduced in the changed file(s). Add recovery / safe default / justified degradation (ADR-1)."
        exit 1
    }
    exit 0
}

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
$files = Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
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
        Write-Host "empty-catch baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "empty-catch baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "empty-catch baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Swallowing catches grew - apply the ADR-1 recovery standard instead."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "empty-catch: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("empty-catch in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: swallowing catch count grew above baseline. Add recovery / safe default / justified degradation (ADR-1)."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
