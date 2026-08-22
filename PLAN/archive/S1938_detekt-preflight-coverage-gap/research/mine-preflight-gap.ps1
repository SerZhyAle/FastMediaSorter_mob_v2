#requires -Version 7.0
<#
.SYNOPSIS
    S1938: classify the post-change runs where detekt-preflight passed and the gradle gate failed.
.NOTES
    Exit codes:
      0 - the corpus was read and the table printed.
      2 - CANNOT VERIFY: the transcript directory is absent.
#>
[CmdletBinding()]
param(
    [string] $ProjectDir = (Join-Path $env:USERPROFILE '.claude/projects/p--ANDROID-FastMediaSorter-mob-v2')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ProjectDir)) {
    Write-Error "mine-preflight-gap: CANNOT VERIFY - no transcript directory at $ProjectDir" -ErrorAction Continue
    exit 2
}

$stats = [ordered]@{
    preflightPassGateFail = 0
    preflightFailGateSkip = 0
    degradedSeen          = 0
    gateFailScoped        = 0
    gateFailProjectWide   = 0
    gateFailStaleReport   = 0
}
$degradedReasons = @{}
$seen = [System.Collections.Generic.HashSet[string]]::new()

foreach ($file in (Get-ChildItem -LiteralPath $ProjectDir -Filter *.jsonl)) {
    foreach ($line in [System.IO.File]::ReadLines($file.FullName)) {
        if ($line -notmatch 'detekt-preflight' -and $line -notmatch 'detekt-gate') { continue }
        $uuid = if ($line -match '"uuid":"([^"]+)"') { $Matches[1] } else { $null }
        if ($uuid -and -not $seen.Add($uuid)) { continue }

        $pf = [regex]::Match($line, '\[detekt-preflight\]\s+(PASS|FAIL|SKIP)')
        $gt = [regex]::Match($line, '\[detekt-gate\]\s+(PASS|FAIL|SKIP)')
        if (-not $pf.Success -or -not $gt.Success) { continue }

        if ($pf.Groups[1].Value -eq 'PASS' -and $gt.Groups[1].Value -eq 'FAIL') {
            $stats.preflightPassGateFail++
            if ($line -match 'FAIL \[scoped\]') { $stats.gateFailScoped++ }
            elseif ($line -match 'FAIL \[project-wide\]') { $stats.gateFailProjectWide++ }
            if ($line -match 'detekt FAILED without refreshing the report') { $stats.gateFailStaleReport++ }
            $deg = [regex]::Match($line, 'degraded to the lexical scan[^"\]*')
            if ($deg.Success) {
                $stats.degradedSeen++
                $key = $deg.Value.Substring(0, [Math]::Min(160, $deg.Value.Length))
                $degradedReasons[$key] = 1 + ($degradedReasons[$key] ?? 0)
            }
        }
        elseif ($pf.Groups[1].Value -eq 'FAIL' -and $gt.Groups[1].Value -eq 'SKIP') {
            $stats.preflightFailGateSkip++
        }
    }
}

$stats.GetEnumerator() | ForEach-Object { '{0,-24} {1}' -f $_.Key, $_.Value }
if ($degradedReasons.Count -gt 0) {
    ''
    'Degraded reasons:'
    $degradedReasons.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object { '  {0} x {1}' -f $_.Value, $_.Key }
}
exit 0
