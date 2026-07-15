#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: `!!` non-null-assertion operators in src/main *.kt must never grow.

.DESCRIPTION
    S1032. `!!` (the not-null assertion operator) is a canonical Kotlin antipattern:
    it converts a nullable into a guaranteed NullPointerException site with no
    recovery. `docs/CODE_AUDIT_PROTOCOL.md` lists `!!` as a P3 review item; this gate
    is the mechanical enforcement (CLAUDE.md Rule 19/20, Audit-Protocol Layer 8:
    a recurring finding becomes a gate).

    Why a custom counting gate and NOT detekt's `UnsafeCallOnNullableType`: that
    detekt rule requires type resolution, but this project's detekt gate runs
    LEXICALLY (config/detekt/detekt.yml: "Do NOT enable rules that require type
    resolution"; build.gradle.kts applies the plain `detekt` task, no classpath).
    A lexical `!!` counter matches the existing ratchet family (assert-em-dash.ps1)
    and the project's static-gate philosophy.

    Scope is `app_v2/src/main/**/*.kt` only. Each `!!` occurrence counts once, so
    `a!!.b!!` is two. `!=` is never matched (it is not two consecutive bangs).
    Per-site remediation is a judgement call - safe-call `?.`, `requireNotNull(x)
    { "msg" }`, an early-return/elvis, or a captured local val when a nullable var
    is guarded then used (the canonical redundant-guard fix). The gate does not
    prescribe which; it only forbids the count from growing.

    Baseline lives in scripts/quality/non-null-assertion-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-non-null-assertion.ps1
    pwsh -NoProfile -File scripts/quality/assert-non-null-assertion.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-non-null-assertion.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-non-null-assertion.ps1 -List
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    # S0850 pattern: judge only the growth these files introduce (working vs HEAD)
    # instead of a full src/main scan. Preserves the "must not grow" guarantee for the change.
    [Parameter(ParameterSetName = 'Gate')][string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mainRoot = Join-Path $repoRoot 'app_v2/src/main'
$baselineFile = Join-Path $PSScriptRoot 'non-null-assertion-baseline.txt'

# Two consecutive bangs = the not-null assertion operator. `!=` never matches
# (bang-then-equals), so no inequality false positives.
$rx = [regex]'!!'

# Delta mode - same regex as the full scan, applied per changed file (working vs HEAD),
# mirroring the em-dash/flavor-flags/deprecated-pm wiring.
if ($ChangedFiles) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    $scoped = @($ChangedFiles | Where-Object { ($_ -replace '\\', '/') -match 'app_v2/src/main/' })
    $countFn = { param($t) $rx.Matches($t).Count }
    $d = Measure-ChangedFileGrowth -ChangedFiles $scoped -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn
    Write-Host ("non-null-assertion [delta over changed files]: new occurrences {0}" -f $d.Growth)
    if ($Gate -and $d.Growth -gt 0) {
        foreach ($p in $d.PerFile) { if ($p.New -gt 0) { Write-Host ("  +{0} in {1}" -f $p.New, $p.Path) } }
        Write-Host "FAIL: new '!!' non-null assertion introduced. Use ?., requireNotNull(x){}, an elvis early-return, or a captured local val."
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
        Write-Host "non-null-assertion baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "non-null-assertion baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "non-null-assertion baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). '!!' grew - remove a non-null assertion instead."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "non-null-assertion: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("non-null-assertion in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: '!!' grew above baseline. Use ?., requireNotNull(x){}, an elvis early-return, or a captured local val."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
