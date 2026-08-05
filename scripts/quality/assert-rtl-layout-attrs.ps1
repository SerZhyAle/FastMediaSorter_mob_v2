#requires -Version 7.0
<#
.SYNOPSIS
    S1190 ratchet gate: absolute Left/Right layout attributes must never come back.

.DESCRIPTION
    An absolute directional attribute does not mirror in a right-to-left locale, so a layout
    carrying one looks correct in English and wrong in Arabic or Urdu - a defect that no
    English-language test can see. Phase 04 of S1190 removed the last of them; this gate is what
    keeps the count from rising again.

    Counted, across app_v2/src/*/res/layout*/**.xml:
      layout_marginLeft/Right, layout_alignParentLeft/Right, layout_toLeftOf/RightOf,
      paddingLeft/Right, and gravity="left"/"right".

    Their start/end counterparts are the fix; where a physical direction is genuinely meant - a
    rotation, a drawable edge tied to a fixed diagram - keep the attribute and record the reason
    in the layout, then raise the baseline deliberately with -UpdateBaseline.

    Baseline lives in scripts/quality/rtl-layout-attrs-baseline.txt and holds a single number.

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 when the count is above the baseline.
      -ChangedFiles    Judge only the named files - any occurrence in one of them fails under
                       -Gate, because the project-wide baseline is what says how many may exist
                       at all and a changed file may not be the one to raise it.
      -UpdateBaseline  Write the current count (ratchets DOWN only, and seeds a missing file).
      -List            Print every occurrence as file:line - the cleanup worklist.

.NOTES
    Exit codes:
      0 - pass (count at or below baseline, or a non-gate mode).
      1 - fail: the count rose above the baseline, or a changed file carries an occurrence.
      2 - cannot verify: the scan root is missing, or a named changed file does not exist.
#>

[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [string[]]$ChangedFiles
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$baselinePath = Join-Path $PSScriptRoot 'rtl-layout-attrs-baseline.txt'
$pattern = 'android:(layout_marginLeft|layout_marginRight|layout_alignParentLeft|' +
    'layout_alignParentRight|layout_toLeftOf|layout_toRightOf|paddingLeft|paddingRight)=|' +
    'android:gravity="(left|right)"'

# pwsh -File does not re-split a quoted CSV into array elements, so split each bound element too.
$scoped = @($ChangedFiles | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } |
    Where-Object { $_ } | Where-Object { $_ -match '\.xml$' -and $_ -match '[\\/]res[\\/]layout' })

if ($scoped.Count -gt 0) {
    $targets = @()
    foreach ($rel in $scoped) {
        $full = if ([System.IO.Path]::IsPathRooted($rel)) { $rel } else { Join-Path $root $rel }
        if (-not (Test-Path -LiteralPath $full)) {
            Write-Error "assert-rtl-layout-attrs: changed file not found - $rel" -ErrorAction Continue
            exit 2
        }
        $targets += Get-Item -LiteralPath $full
    }
}
else {
    $scanRoot = Join-Path $root 'app_v2/src'
    if (-not (Test-Path -LiteralPath $scanRoot)) {
        Write-Error "assert-rtl-layout-attrs: scan root not found - $scanRoot" -ErrorAction Continue
        exit 2
    }
    $targets = @(Get-ChildItem -Path $scanRoot -Recurse -Filter '*.xml' -File |
        Where-Object { $_.DirectoryName -match '[\\/]res[\\/]layout' })
}

$hits = @(Select-String -Path $targets.FullName -Pattern $pattern -AllMatches -ErrorAction SilentlyContinue)
$count = @($hits | ForEach-Object { $_.Matches }).Count

if ($List) {
    foreach ($hit in $hits) {
        Write-Output ("  {0}:{1}" -f ($hit.Path.Replace($root, '').TrimStart('\', '/')), $hit.LineNumber)
    }
}

if ($scoped.Count -gt 0) {
    Write-Output "rtl-layout-attrs [scoped to $($scoped.Count) changed layout(s)]: occurrences $count"
    if ($Gate -and $count -gt 0) {
        Write-Error ("assert-rtl-layout-attrs: FAIL - a changed layout carries an absolute " +
            "directional attribute. Use the start/end counterpart.") -ErrorAction Continue
        exit 1
    }
    Write-Output 'assert-rtl-layout-attrs: PASS (no absolute directional attribute in the changed layouts).'
    exit 0
}

$baseline = if (Test-Path -LiteralPath $baselinePath) { [int](Get-Content -LiteralPath $baselinePath -Raw).Trim() } else { $count }

if ($UpdateBaseline) {
    if ($count -lt $baseline -or -not (Test-Path -LiteralPath $baselinePath)) {
        Set-Content -LiteralPath $baselinePath -Value $count -Encoding utf8NoBOM
        Write-Output "assert-rtl-layout-attrs: baseline set to $count (was $baseline)."
    }
    else {
        Write-Output "assert-rtl-layout-attrs: baseline stays $baseline - it ratchets down only (current $count)."
    }
    exit 0
}

Write-Output "rtl-layout-attrs: baseline $baseline | actual $count"
if ($Gate -and $count -gt $baseline) {
    Write-Error ("assert-rtl-layout-attrs: FAIL - $count occurrence(s) against a baseline of " +
        "$baseline. Replace the absolute attribute with its start/end counterpart.") -ErrorAction Continue
    exit 1
}
Write-Output 'assert-rtl-layout-attrs: PASS (at or below baseline).'
exit 0
