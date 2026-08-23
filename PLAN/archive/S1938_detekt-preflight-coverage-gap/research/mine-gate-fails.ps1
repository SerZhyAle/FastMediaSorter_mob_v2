#requires -Version 7.0
<# .SYNOPSIS S1938: raw counts of detekt verdict lines in the transcripts. .NOTES Exit codes: 0 ok; 2 no corpus. #>
param([string] $ProjectDir = (Join-Path $env:USERPROFILE '.claude/projects/p--ANDROID-FastMediaSorter-mob-v2'))
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ProjectDir)) { Write-Error 'CANNOT VERIFY' -ErrorAction Continue; exit 2 }
$c = [ordered]@{ gateFail = 0; gateFailWithPreflightPass = 0; gateFailWithPreflightAny = 0; assertDetektFail = 0; preflightDegraded = 0 }
$seen = [System.Collections.Generic.HashSet[string]]::new()
foreach ($file in (Get-ChildItem -LiteralPath $ProjectDir -Filter *.jsonl)) {
    foreach ($line in [System.IO.File]::ReadLines($file.FullName)) {
        if ($line -notmatch 'detekt') { continue }
        $uuid = if ($line -match '"uuid":"([^"]+)"') { $Matches[1] } else { $null }
        if ($uuid -and -not $seen.Add($uuid)) { continue }
        if ($line -match '\[detekt-gate\]\s+FAIL') {
            $c.gateFail++
            if ($line -match '\[detekt-preflight\]') { $c.gateFailWithPreflightAny++ }
            if ($line -match '\[detekt-preflight\]\s+PASS') { $c.gateFailWithPreflightPass++ }
        }
        if ($line -match 'assert-detekt: FAIL') { $c.assertDetektFail++ }
        if ($line -match 'degraded to the lexical scan') { $c.preflightDegraded++ }
    }
}
$c.GetEnumerator() | ForEach-Object { '{0,-28} {1}' -f $_.Key, $_.Value }
exit 0
