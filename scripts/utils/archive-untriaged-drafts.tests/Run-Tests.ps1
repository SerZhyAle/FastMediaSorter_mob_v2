#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for scripts/utils/archive-untriaged-drafts.ps1 (S4190).

.DESCRIPTION
    Each case builds a throwaway root with a PLAN/spec-catalog.jsonl and runs the script against a stub
    archive entry point that records the ids it was handed, so no case touches the real catalog.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'archive-untriaged-drafts.ps1'
$failures = 0
$roots = @()

function New-Root {
    param([string[]] $Rows, [int] $StubExit = 0)
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('untriaged-tests-' + [guid]::NewGuid().ToString('n').Substring(0, 8))
    New-Item -ItemType Directory -Path (Join-Path $root 'PLAN') -Force | Out-Null
    if ($null -ne $Rows) { Set-Content -LiteralPath (Join-Path $root 'PLAN/spec-catalog.jsonl') -Value $Rows -Encoding UTF8 }
    $stub = Join-Path $root 'stub-archive.ps1'
    "param([string]`$Id) Set-Content -LiteralPath '$root/archived.txt' -Value `$Id; exit $StubExit" | Set-Content -LiteralPath $stub -Encoding UTF8
    $script:roots += $root
    return $root
}

function Invoke-Script([string] $Root, [string[]] $Extra = @()) {
    $out = & pwsh -NoProfile -File $script -RepoRoot $Root -ArchiveScript (Join-Path $Root 'stub-archive.ps1') @Extra 2>&1 | Out-String
    return @{ Exit = $LASTEXITCODE; Out = $out }
}

function Get-Archived([string] $Root) {
    $path = Join-Path $Root 'archived.txt'
    if (Test-Path -LiteralPath $path) { return (Get-Content -LiteralPath $path -Raw).Trim() }
    return ''
}

function Assert-Case([string] $Name, [scriptblock] $Body) {
    try {
        $message = & $Body
        if ($message) { Write-Host "FAIL  $Name - $message" -ForegroundColor Red; $script:failures++ }
        else { Write-Host "PASS  $Name" -ForegroundColor Green }
    }
    catch { Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red; $script:failures++ }
}

$old = (Get-Date).AddDays(-20).ToString('yy-MM-dd')
$fresh = (Get-Date).AddDays(-2).ToString('yy-MM-dd')
$rows = @(
    ('{"id":"S0001","name":"old-parked","status":"BlockQuestions","statusNote":"Parked finding, untriaged (S4190). parked: ' + $old + '"}'),
    ('{"id":"S0002","name":"fresh-parked","status":"BlockQuestions","statusNote":"Parked finding, untriaged (S4190). parked: ' + $fresh + '"}'),
    '{"id":"S0003","name":"owner-question","status":"BlockQuestions","statusNote":"Which codec? - owner","created":"2026-01-01"}',
    '{"id":"S0004","name":"plain-draft","status":"Draft","created":"2026-01-01"}'
)

try {
    Assert-Case 'T1 only an untriaged parked ticket past the deadline is archived' {
        $root = New-Root $rows
        $r = Invoke-Script $root
        if ($r.Exit -ne 0) { return "expected exit 0, got $($r.Exit): $($r.Out)" }
        if ((Get-Archived $root) -ne 'S0001') { return "archived '$(Get-Archived $root)', expected S0001" }
    }

    Assert-Case 'T2 -List archives nothing and flags the expired one' {
        $root = New-Root $rows
        $r = Invoke-Script $root @('-List')
        if ($r.Exit -ne 0) { return "expected exit 0, got $($r.Exit)" }
        if (Get-Archived $root) { return '-List archived something' }
        if ($r.Out -notmatch 'S0001 old-parked - parked \d+ day\(s\) - EXPIRED') { return "no EXPIRED line: $($r.Out)" }
        if ($r.Out -match 'S0003') { return 'an owner question was listed as parked' }
    }

    Assert-Case 'T3 nothing expired archives nothing' {
        $root = New-Root @($rows[1], $rows[2])
        $r = Invoke-Script $root
        if ($r.Exit -ne 0 -or (Get-Archived $root)) { return "expected a no-op, got exit $($r.Exit) and '$(Get-Archived $root)'" }
    }

    Assert-Case 'T4 a refusing archive.ps1 exits 1' {
        $root = New-Root $rows 1
        $r = Invoke-Script $root
        if ($r.Exit -ne 1) { return "expected exit 1, got $($r.Exit)" }
    }

    Assert-Case 'T5 an absent catalog cannot verify' {
        $root = New-Root $null
        $r = Invoke-Script $root
        if ($r.Exit -ne 2) { return "expected exit 2, got $($r.Exit)" }
    }

    Assert-Case 'T6 -Days moves the deadline' {
        $root = New-Root $rows
        $r = Invoke-Script $root @('-Days', '1')
        if ($r.Exit -ne 0) { return "expected exit 0, got $($r.Exit)" }
        if ((Get-Archived $root) -ne 'S0001,S0002') { return "archived '$(Get-Archived $root)', expected S0001,S0002" }
    }
}
finally {
    foreach ($root in $roots) { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host ''
if ($failures -gt 0) { Write-Host "archive-untriaged-drafts tests: $failures case(s) failed" -ForegroundColor Red; exit 1 }
Write-Host 'archive-untriaged-drafts tests: all cases passed' -ForegroundColor Green
exit 0
