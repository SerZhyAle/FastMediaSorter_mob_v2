#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: hardcoded hex colors in layout XML must never grow.

.DESCRIPTION
    Part of S0383 neuroslop hygiene. Counts attribute values of the form
    `="#<hex>"` (3-8 hex digits) inside app_v2/src/main/res/layout and
    /res/layout-land only. Hardcoded colors there should reference a theme
    attribute (`?attr/..`) or a named `@color/..` so dark/light and flavor
    themes stay consistent. Vector drawables / color selectors / mipmaps are
    NOT scanned - hex in those assets is legitimate. The count may only go
    DOWN; growth fails the gate.

    Baseline lives in scripts/quality/layout-hardcoded-colors-baseline.txt.

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-layout-hardcoded-colors.ps1
    pwsh -NoProfile -File scripts/quality/assert-layout-hardcoded-colors.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-layout-hardcoded-colors.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-layout-hardcoded-colors.ps1 -List
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
$resRoot = Join-Path $repoRoot 'app_v2/src/main/res'
$baselineFile = Join-Path $PSScriptRoot 'layout-hardcoded-colors-baseline.txt'

$rx = [regex]'="#[0-9a-fA-F]{3,8}"'

$layoutDirs = @('layout', 'layout-land') | ForEach-Object { Join-Path $resRoot $_ } | Where-Object { Test-Path $_ }

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
foreach ($dir in $layoutDirs) {
    $files = Get-ChildItem -LiteralPath $dir -Recurse -File -Filter '*.xml' -ErrorAction SilentlyContinue
    foreach ($file in $files) {
        $lineNo = 0
        foreach ($line in Get-Content -LiteralPath $file.FullName) {
            $lineNo++
            $c = $rx.Matches($line).Count
            if ($c -gt 0) {
                $current += $c
                if ($List) {
                    $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                    $hits.Add(("{0}:{1}  {2}" -f $rel, $lineNo, $line.Trim()))
                }
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
        Write-Host "layout-hardcoded-colors baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "layout-hardcoded-colors baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "layout-hardcoded-colors baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Hardcoded layout colors grew - use ?attr/.. or @color/.. instead."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "layout-hardcoded-colors: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("layout-hardcoded-colors in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: hardcoded layout color count grew above baseline. Reference a theme attr or named color."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
