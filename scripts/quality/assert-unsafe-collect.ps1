#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: unsafe (non-lifecycle-aware) Flow collections in src/main must never grow.

.DESCRIPTION
    Part of S0383 neuroslop hygiene. Counts `lifecycleScope.launch { .. .collect .. }`
    sites whose launch body reaches a `.collect` / `.collectLatest` WITHOUT first
    passing through `repeatOnLifecycle` / `flowWithLifecycle`. Such a collection
    keeps running on a destroyed View and leaks (ADR-2). The project already
    ships a safe helper `collectOnLifecycle` (utils/LifecycleExtensions.kt) -
    these residual raw sites should be routed through it. The count may only go
    DOWN; growth fails the gate.

    The negative lookahead `(?!repeatOnLifecycle|flowWithLifecycle|\})` ensures a
    safe `lifecycleScope.launch { repeatOnLifecycle(..) { flow.collect ... } }` is
    NOT flagged, and that a launch whose block closes before any collect is not
    counted.

    Baseline lives in scripts/quality/unsafe-collect-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1
    pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1 -List
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
$mainRoot = Join-Path $repoRoot 'app_v2/src/main'
$baselineFile = Join-Path $PSScriptRoot 'unsafe-collect-baseline.txt'

# Find each `lifecycleScope.launch {`, brace-match its body, and flag it when the
# body contains a `.collect` / `.collectLatest` but NO `repeatOnLifecycle` /
# `flowWithLifecycle`. Brace-matching (not a bounded regex) is required so that
# operator chains with their own lambda braces (`.filter { .. }.collect`) do not
# evade detection. Comments/strings with stray braces are a known approximation.
$launchRx = [regex]'lifecycleScope\.launch\s*\{'

function Test-UnsafeLaunchBody([string]$text, [int]$openBrace) {
    $depth = 0
    $end = -1
    for ($i = $openBrace; $i -lt $text.Length; $i++) {
        $c = $text[$i]
        if ($c -eq '{') { $depth++ }
        elseif ($c -eq '}') { $depth--; if ($depth -eq 0) { $end = $i; break } }
    }
    if ($end -lt 0) { return $false }
    $body = $text.Substring($openBrace + 1, $end - $openBrace - 1)
    if ($body -notmatch '\.collect') { return $false }
    if ($body -match '(repeatOnLifecycle|flowWithLifecycle)') { return $false }
    return $true
}

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
$files = Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    if ([string]::IsNullOrEmpty($text)) { continue }
    foreach ($m in $launchRx.Matches($text)) {
        $openBrace = $m.Index + $m.Length - 1
        if (Test-UnsafeLaunchBody $text $openBrace) {
            $current++
            if ($List) {
                $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $lineNo = ($text.Substring(0, $m.Index) -split "`n").Count
                $hits.Add(("{0}:{1}" -f $rel, $lineNo))
            }
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
        Write-Host "unsafe-collect baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "unsafe-collect baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "unsafe-collect baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Unsafe collections grew - route through collectOnLifecycle / repeatOnLifecycle."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "unsafe-collect: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("unsafe-collect in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: unsafe Flow-collection count grew above baseline. Use collectOnLifecycle (utils/LifecycleExtensions.kt)."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
