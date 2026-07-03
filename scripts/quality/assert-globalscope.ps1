#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: GlobalScope usage in src/main must never grow (target: stay at 0).

.DESCRIPTION
    Part of S0383 neuroslop hygiene (Tier 1). AI-generated coroutine code reaches
    for `GlobalScope.launch { }` / `GlobalScope.async { }` because it is the
    shortest way to "just run something". Such a coroutine is unscoped: it
    outlives the screen/ViewModel that started it, cannot be cancelled, and leaks
    (violates Rule 18 lifecycle/resource discipline). The correct scope is
    `viewModelScope`, a lifecycle `*Scope`, or an injected `CoroutineScope`.

    This detector counts `GlobalScope.` usage sites (the trailing dot excludes the
    bare `import ..GlobalScope` line). The codebase is currently at 0, so this is a
    KEEP-AT-ZERO gate: the baseline floor is 0 and any new site fails the gate.

    Baseline lives in scripts/quality/globalscope-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-globalscope.ps1
    pwsh -NoProfile -File scripts/quality/assert-globalscope.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-globalscope.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-globalscope.ps1 -List
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
$baselineFile = Join-Path $PSScriptRoot 'globalscope-baseline.txt'

# `GlobalScope.` (usage). The trailing dot means a bare `import ..GlobalScope`
# line is NOT counted - only call/property access on the singleton.
$rx = [regex]'\bGlobalScope\s*\.'

# S0850: delta mode - same regex as the full scan, applied per changed file (working vs HEAD),
# mirroring the flavor-flags/deprecated-pm wiring from S0848 Phase 04.
if ($ChangedFiles) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    $scoped = @($ChangedFiles | Where-Object { ($_ -replace '\\', '/') -match 'app_v2/src/main/' })
    $countFn = { param($t) $rx.Matches($t).Count }
    $d = Measure-ChangedFileGrowth -ChangedFiles $scoped -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn
    Write-Host ("globalscope [delta over changed files]: new occurrences {0}" -f $d.Growth)
    if ($Gate -and $d.Growth -gt 0) {
        foreach ($p in $d.PerFile) { if ($p.New -gt 0) { Write-Host ("  +{0} in {1}" -f $p.New, $p.Path) } }
        Write-Host "FAIL: new GlobalScope usage introduced in the changed file(s). Use viewModelScope / a lifecycle scope / an injected CoroutineScope."
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
        Write-Host "globalscope baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "globalscope baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "globalscope baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). GlobalScope crept in - use viewModelScope / a lifecycle scope / an injected CoroutineScope instead."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "globalscope: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("globalscope in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: GlobalScope usage grew above baseline. Replace with viewModelScope / lifecycle scope / injected CoroutineScope."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
