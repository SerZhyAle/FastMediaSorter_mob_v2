#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: non-Timber logging in src/main must never grow.

.DESCRIPTION
    Part of S0383 neuroslop hygiene (Tier 1). The project logging standard is
    Timber ONLY (CLAUDE.md Sec 8). AI-generated code routinely emits the logging
    it was trained on instead: `android.util.Log.d/v/i/w/e/wtf(..)` and
    `System.out` / `System.err`. These bypass the Timber tree (no tag policy, no
    release stripping, no ticket-log gate) and are pure debug residue. They must
    be replaced with the matching Timber level.

    Counts two families as one number:
      - `Log.<level>(`     android.util.Log calls (Timber.* is NOT matched: there
                           is no `Log.` token inside `Timber.`)
      - `System.out`/`System.err`

    Bare `println(` / `print(` are deliberately NOT flagged: this codebase uses
    `PrintWriter.println(..)` inside `apply { }` for crash-log file writes, and
    owns a document-printing domain (`fun print(): Boolean`, Android PrintManager)
    - so those tokens are legitimate I/O here, not stdout debug residue. A
    text-scan cannot separate the two without scope/type info, and including them
    would both pollute the baseline and block legitimate domain code. Catch stray
    `println` debug in review instead.

    Comments and string literals containing these tokens are a known
    approximation (same caveat as the sibling detectors).

    Baseline lives in scripts/quality/nontimber-log-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-nontimber-log.ps1
    pwsh -NoProfile -File scripts/quality/assert-nontimber-log.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-nontimber-log.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-nontimber-log.ps1 -List
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
$baselineFile = Join-Path $PSScriptRoot 'nontimber-log-baseline.txt'

# Logging-framework-bypass tokens only. See the .DESCRIPTION note for why
# println/print are intentionally excluded in this codebase.
$rx = [regex]'\bLog\.(?:d|v|i|w|e|wtf)\s*\(|\bSystem\.(?:out|err)\b'

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
            $hits.Add(("{0}:{1}  {2}" -f $rel, $lineNo, $m.Value.Trim()))
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
        Write-Host "nontimber-log baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "nontimber-log baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "nontimber-log baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Non-Timber logging grew - use Timber.<level>(..) instead."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "nontimber-log: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("nontimber-log in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: non-Timber logging grew above baseline. Replace Log.*/System.out with Timber.<level>(..)."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
