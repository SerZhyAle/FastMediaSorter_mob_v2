#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: em-dash / en-dash characters in src/main *.kt must never grow.

.DESCRIPTION
    Part of the neuroslop hygiene family (CLAUDE.md Rule 19). The project writing
    standard is the plain ASCII hyphen-minus `-`, never the typographic long
    dashes. AI-generated text routinely emits the em-dash `—` (U+2014), en-dash
    `–` (U+2013) and horizontal bar `―` (U+2015) it was trained on. In code,
    comments and KDoc these are pure stylistic residue that must be replaced with
    a hyphen `-`.

    Scope is `app_v2/src/main/**/*.kt` only. Localised UI text in `res/values*`
    strings.xml is intentionally OUT of scope: RU/UK typography legitimately uses
    the long dash, and COMMUNICATION_POLICY governs that copy, not this gate.

    Matches three code points as one count:
      - U+2013 en-dash  `–`
      - U+2014 em-dash  `—`
      - U+2015 horizontal bar `―`

    Baseline lives in scripts/quality/em-dash-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-em-dash.ps1
    pwsh -NoProfile -File scripts/quality/assert-em-dash.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-em-dash.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-em-dash.ps1 -List
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
$baselineFile = Join-Path $PSScriptRoot 'em-dash-baseline.txt'

# Long-dash code points that must be a plain hyphen-minus in code/comments.
$rx = [regex]'[–—―]'

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
            $hits.Add(("{0}:{1}  {2}" -f $rel, $lineNo, $m.Value))
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
        Write-Host "em-dash baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "em-dash baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "em-dash baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Long dashes grew - use a plain hyphen '-' instead."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "em-dash: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("em-dash in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: long dashes grew above baseline. Replace em-dash/en-dash with a plain hyphen '-'."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
