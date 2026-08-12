#requires -Version 7.0
<#
.SYNOPSIS
    S1595: how often has each given file been touched, according to the dev log?

.DESCRIPTION
    Counts rows in dev/CHANGELOG.md naming each path. The dev log records one row per logical
    change with the file it touched, so it is the repository's own record of what gets worked on -
    and unlike git history it is available without leaving the working tree, which this project
    treats as the source of truth for current state.

    Intended use: ranking detekt-debt tickets by how often their file is actually opened, so the
    order comes from an observable quantity rather than a feeling. It reports only. Work order
    belongs to PLAN/RELEASE_QUEUE.md and to the owner; catalog priority is a tiebreak for tickets
    the queue does not list, so nothing here writes a priority anywhere.

    Matching is on the file NAME plus, when the given path has directories, its parent segment -
    dev-log rows record repo-relative paths, but the same file is sometimes logged under a
    different source set (a flavor move) and a bare full-path match would silently score zero.

.PARAMETER Paths
    Repo-relative paths. Pass ONE comma-joined argument (`pwsh -File` binds a [string[]] to its
    first element only).

.PARAMETER SinceDays
    Only count rows newer than this many days. 0 counts the whole log. Default 90.

.NOTES
    Exit codes:
      0 - measured; one row printed per path.
      2 - cannot verify: dev/CHANGELOG.md is missing, or no path was given.
#>
[CmdletBinding()]
param(
    [string[]] $Paths,
    [int]      $SinceDays = 90,
    [string]   $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch]   $Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$log = Join-Path $RepoRoot 'dev/CHANGELOG.md'
if (-not (Test-Path -LiteralPath $log)) {
    Write-Error "measure-file-touch-frequency: CANNOT VERIFY - dev/CHANGELOG.md not found at $log." -ErrorAction Continue
    exit 2
}

$expanded = @()
foreach ($entry in ($Paths | Where-Object { $_ })) {
    $expanded += ($entry -split ',') | ForEach-Object { $_.Trim() }
}
$expanded = @($expanded | Where-Object { $_ })
if ($expanded.Count -eq 0) {
    Write-Error 'measure-file-touch-frequency: CANNOT VERIFY - no path given.' -ErrorAction Continue
    exit 2
}

$cutoff = if ($SinceDays -gt 0) { (Get-Date).AddDays(-$SinceDays) } else { [datetime]::MinValue }

# Rows look like: | 2026-08-12 11:18:12 | `PLAN/Sxxxx.md` | `target` | description |
# The path column is backtick-wrapped, so it is unwrapped below - matching the raw capture scores
# every path zero, which reads as "never touched" rather than as a broken matcher.
$rowRx = [regex]'^\s*\|\s*(?<when>\d{4}-\d{2}-\d{2}[^|]*)\|\s*(?<file>[^|]+?)\s*\|'
$rows = [System.Collections.Generic.List[object]]::new()
foreach ($line in [System.IO.File]::ReadLines($log)) {
    $m = $rowRx.Match($line)
    if (-not $m.Success) { continue }
    $when = [datetime]::MinValue
    if (-not [datetime]::TryParse($m.Groups['when'].Value.Trim(), [ref]$when)) { continue }
    if ($when -lt $cutoff) { continue }
    $file = $m.Groups['file'].Value.Trim().Trim('`').Trim()
    $rows.Add([pscustomobject]@{ When = $when; File = ($file -replace '\\', '/').ToLower() })
}

$results = foreach ($p in $expanded) {
    $norm = ($p -replace '\\', '/').ToLower()
    $leaf = [System.IO.Path]::GetFileName($norm)
    $parent = [System.IO.Path]::GetFileName([System.IO.Path]::GetDirectoryName($norm))
    $hits = @($rows | Where-Object {
            $_.File.EndsWith("/$leaf") -or $_.File -eq $leaf -or
            ($parent -and $_.File -like "*/$parent/$leaf")
        })
    [pscustomobject]@{
        Touches = $hits.Count
        First   = if ($hits.Count) { (($hits | Measure-Object When -Minimum).Minimum).ToString('yyyy-MM-dd') } else { '-' }
        Last    = if ($hits.Count) { (($hits | Measure-Object When -Maximum).Maximum).ToString('yyyy-MM-dd') } else { '-' }
        Path    = $p
    }
}

$ranked = @($results | Sort-Object Touches -Descending)
if ($Json) { $ranked | ConvertTo-Json -Depth 3; exit 0 }

$window = if ($SinceDays -gt 0) { "last $SinceDays days" } else { 'whole log' }
Write-Host ("File touch frequency from dev/CHANGELOG.md ({0}, {1} rows in window):" -f $window, $rows.Count)
Write-Host ("{0,7}  {1,10}  {2,10}  {3}" -f 'TOUCHES', 'FIRST', 'LAST', 'PATH')
foreach ($r in $ranked) {
    Write-Host ("{0,7}  {1,10}  {2,10}  {3}" -f $r.Touches, $r.First, $r.Last, $r.Path)
}
exit 0
