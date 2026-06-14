#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: runtime stub markers (TODO() / NotImplementedError) in src/main must never grow.

.DESCRIPTION
    Part of S0383 neuroslop hygiene (Tier 1). When an AI agent does not know how
    to finish a branch it leaves the Kotlin runtime stub `TODO(..)` or
    `throw NotImplementedError(..)`. Unlike a `// TODO` tracking comment, these
    COMPILE and then CRASH at runtime the moment the path is hit - an abandoned
    code path shipped as a landmine. Every site is unfinished work that must be
    implemented or removed before merge.

    Counts the `TODO(` function call (the comment form `// TODO` is NOT matched -
    only `TODO` immediately followed by `(`) and any `NotImplementedError`
    reference.

    Comments and string literals containing these tokens are a known
    approximation (same caveat as the sibling detectors).

    Baseline lives in scripts/quality/stub-todo-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every matching file:line (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-stub-todo.ps1
    pwsh -NoProfile -File scripts/quality/assert-stub-todo.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-stub-todo.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-stub-todo.ps1 -List
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
$baselineFile = Join-Path $PSScriptRoot 'stub-todo-baseline.txt'

# `TODO(` is the kotlin.TODO() call (a `// TODO` comment has no `(` and is NOT
# matched). `NotImplementedError` covers `throw NotImplementedError(..)`.
$rx = [regex]'\bTODO\s*\(|\bNotImplementedError\b'

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
        Write-Host "stub-todo baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "stub-todo baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "stub-todo baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Runtime stubs grew - implement the path or remove it before merge."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "stub-todo: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("stub-todo in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: runtime stub count grew above baseline. Implement the branch or remove it (do not ship TODO()/NotImplementedError)."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
