#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: the number of trivial verb-noun comments in src/main must never grow.

.DESCRIPTION
    Part of S0383 neuroslop hygiene. AI-generated code accrues trivial line
    comments that merely restate the next call (e.g. `// Get user` above
    `getUser()`). This detector counts `//` comments whose text begins with a
    trivial verb-noun keyword. It is a COARSE proposal generator, not an
    auto-deleter: removal is manual and per-site (a comment may legitimately
    start with such a verb). The count may only go DOWN; growth fails the gate.

    Heuristic: a line whose first non-space content is `//` followed by one of
    the trivial verbs and a word boundary. Excludes `//noinspection`, `// TODO`,
    `// FIXME`, and `//`-style URLs. KDoc (`*` / `/**`) never starts with `//`.

    Baseline lives in scripts/quality/trivial-comments-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1
    pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1 -List
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
$baselineFile = Join-Path $PSScriptRoot 'trivial-comments-baseline.txt'

$verbs = 'Get|Set|Initialize|Init|Create|Update|Check|Handle|Setup|Set up|Show|Hide|Load|Save|Return|Add|Remove|Clear|Start|Stop|Reset|Apply|Configure|Build|Bind|Observe|Enable|Disable|Register|Unregister|Notify|Refresh|Toggle|Cancel'
$rx = [regex]"^\s*//\s*($verbs)\b"

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
$files = Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
foreach ($file in $files) {
    $lineNo = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName) {
        $lineNo++
        if ($line -match '//\s*(noinspection|TODO|FIXME)' ) { continue }
        if ($line -match '//\s*https?:') { continue }
        if ($rx.IsMatch($line)) {
            $current++
            if ($List) {
                $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $hits.Add(("{0}:{1}  {2}" -f $rel, $lineNo, $line.Trim()))
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
        Write-Host "trivial-comments baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "trivial-comments baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "trivial-comments baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Trivial comments grew - remove them instead of widening the cap."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "trivial-comments: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("trivial-comments in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: trivial comment count grew above baseline. Remove the new trivial comments (or justify and refactor)."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
