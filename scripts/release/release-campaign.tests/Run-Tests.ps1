#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/release/release-campaign.ps1 and its library (S4413).

.DESCRIPTION
    Every case runs against a per-PID fixture directory that FMS_RELEASE_CAMPAIGN_ROOT points the
    script at, so no case can touch the repository's own temp/metrics campaign files.

    The pinned property the strategic spec names: a new campaign always gets a new id, because the
    pass cache keys on it and a shared id would carry a pass from one campaign into the next.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$releaseDir = Split-Path -Parent $PSScriptRoot
$script = Join-Path $releaseDir 'release-campaign.ps1'
$lib = Join-Path $releaseDir 'lib\release-campaign.ps1'
foreach ($required in @($script, $lib)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "FAIL  not found: $required" -ForegroundColor Red
        exit 1
    }
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$root = Join-Path ([System.IO.Path]::GetTempPath()) "release-campaign-tests-$PID"
$emptyRoot = Join-Path $root 'empty'
$liveRoot = Join-Path $root 'live'
Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $emptyRoot, $liveRoot -Force | Out-Null

$previousRoot = $env:FMS_RELEASE_CAMPAIGN_ROOT
$env:FMS_RELEASE_CAMPAIGN_ROOT = $liveRoot
$recordPath = Join-Path $liveRoot 'release-campaign.json'
$journalPath = Join-Path $liveRoot 'release-campaign-phases.jsonl'

function Invoke-Campaign {
    param([string[]] $CampaignArgs)
    & $pwshExe -NoProfile -File $script @CampaignArgs *> $null
    return $LASTEXITCODE
}

function Get-Record { return ([System.IO.File]::ReadAllText($recordPath) | ConvertFrom-Json -DateKind String) }

function Get-JournalRows {
    if (-not (Test-Path -LiteralPath $journalPath)) { return @() }
    return @([System.IO.File]::ReadAllLines($journalPath) | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json -DateKind String })
}

$script:failures = 0
$script:caseCount = 0
function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    $script:caseCount++
    try {
        $message = & $Body
        if ($message) {
            Write-Host "FAIL  $Name - $message" -ForegroundColor Red
            $script:failures++
        }
        else {
            Write-Host "PASS  $Name" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

$script:firstId = ''

try {
    Assert-Case 'C1 -Help exits 0' {
        $code = Invoke-Campaign @('-Help')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        return $null
    }

    Assert-Case 'C2 Start without -Package exits 2 and writes nothing' {
        $code = Invoke-Campaign @('-Verb', 'Start')
        if ($code -ne 2) { return "expected exit 2, got $code" }
        if (Test-Path -LiteralPath $recordPath) { return 'a refused Start wrote a record' }
        return $null
    }

    Assert-Case 'C3 Start writes an open record with a fresh id' {
        $code = Invoke-Campaign @('-Verb', 'Start', '-Package', '47')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        $record = Get-Record
        if ([string]::IsNullOrWhiteSpace([string]$record.id)) { return 'the record carries no id' }
        if ($record.state -ne 'open') { return "expected state open, got '$($record.state)'" }
        if ([int]$record.package -ne 47) { return "expected package 47, got '$($record.package)'" }
        $script:firstId = [string]$record.id
        return $null
    }

    Assert-Case 'C4 a second Start exits 4 and keeps the open id' {
        $code = Invoke-Campaign @('-Verb', 'Start', '-Package', '47')
        if ($code -ne 4) { return "expected exit 4, got $code" }
        if ([string](Get-Record).id -ne $script:firstId) { return 'the refused Start replaced the open record' }
        return $null
    }

    Assert-Case 'C5 Status exits 0 while a campaign is open' {
        $code = Invoke-Campaign @('-Verb', 'Status')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        return $null
    }

    Assert-Case 'C6 Phase begin and end append two lines carrying the campaign id' {
        $code = Invoke-Campaign @('-Verb', 'Phase', '-Name', 'sweep', '-State', 'begin')
        if ($code -ne 0) { return "begin expected exit 0, got $code" }
        $code = Invoke-Campaign @('-Verb', 'Phase', '-Name', 'sweep', '-State', 'end', '-ExitCode', '0')
        if ($code -ne 0) { return "end expected exit 0, got $code" }
        $rows = Get-JournalRows
        if ($rows.Count -ne 2) { return "expected 2 journal lines, found $($rows.Count)" }
        foreach ($row in $rows) {
            if ([string]$row.campaignId -ne $script:firstId) { return "a line carries id '$($row.campaignId)', expected '$($script:firstId)'" }
        }
        if ($rows[0].state -ne 'begin' -or $rows[1].state -ne 'end') { return 'the two lines are not begin then end' }
        return $null
    }

    Assert-Case 'C7 Report computes the duration from begin and end' {
        $rows = Get-JournalRows
        $expected = [long]$rows[1].atMs - [long]$rows[0].atMs
        $out = & $pwshExe -NoProfile -File $script -Verb Report -Json 2>$null
        if ($LASTEXITCODE -ne 0) { return "expected exit 0, got $LASTEXITCODE" }
        $report = ($out | Out-String) | ConvertFrom-Json -DateKind String
        $sweep = @($report.phases | Where-Object { $_.name -eq 'sweep' })
        if ($sweep.Count -ne 1) { return "expected one sweep row, found $($sweep.Count)" }
        if ([long]$sweep[0].durationMs -ne $expected) { return "expected durationMs $expected, got '$($sweep[0].durationMs)'" }
        if ([int]$sweep[0].exitCode -ne 0) { return "expected exitCode 0, got '$($sweep[0].exitCode)'" }
        return $null
    }

    Assert-Case 'C8 Add-ReleaseCampaignPhase with no open campaign writes nothing and does not throw' {
        $env:FMS_RELEASE_CAMPAIGN_ROOT = $emptyRoot
        try {
            . $lib
            Add-ReleaseCampaignPhase -Name 'gate' -State 'point' -ElapsedMs 12 -ExitCode 0
            if ($null -ne (Get-ActiveReleaseCampaign)) { return 'an empty root reported an open campaign' }
            $written = @(Get-ChildItem -LiteralPath $emptyRoot -Force)
            if ($written.Count -ne 0) { return "expected no files, found $($written.Name -join ', ')" }
            $code = Invoke-Campaign @('-Verb', 'Phase', '-Name', 'gate', '-State', 'point')
            if ($code -ne 0) { return "the CLI Phase outside a campaign expected exit 0, got $code" }
            if (@(Get-ChildItem -LiteralPath $emptyRoot -Force).Count -ne 0) { return 'the CLI Phase outside a campaign wrote a file' }
            return $null
        }
        finally { $env:FMS_RELEASE_CAMPAIGN_ROOT = $liveRoot }
    }

    Assert-Case 'C9 End closes the record and Status then exits 3' {
        $code = Invoke-Campaign @('-Verb', 'End')
        if ($code -ne 0) { return "End expected exit 0, got $code" }
        $record = Get-Record
        if ($record.state -ne 'closed') { return "expected state closed, got '$($record.state)'" }
        if (-not $record.endedAt) { return 'the closed record carries no endedAt' }
        $code = Invoke-Campaign @('-Verb', 'Status')
        if ($code -ne 3) { return "Status expected exit 3, got $code" }
        $code = Invoke-Campaign @('-Verb', 'End')
        if ($code -ne 3) { return "a second End expected exit 3, got $code" }
        return $null
    }

    Assert-Case 'C10 a new Start after End gets a different id' {
        $code = Invoke-Campaign @('-Verb', 'Start', '-Package', '47')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        $id = [string](Get-Record).id
        if ($id -eq $script:firstId) { return "the new campaign reused id '$id'" }
        return $null
    }

    Assert-Case 'C11 an unreadable record makes Status exit 2' {
        [System.IO.File]::WriteAllText($recordPath, '{ not json')
        $code = Invoke-Campaign @('-Verb', 'Status')
        if ($code -ne 2) { return "expected exit 2, got $code" }
        return $null
    }
}
finally {
    $env:FMS_RELEASE_CAMPAIGN_ROOT = $previousRoot
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($script:failures -gt 0) {
    Write-Host "release-campaign.tests: FAIL (run=$script:caseCount skipped=0)" -ForegroundColor Red
    exit 1
}
Write-Host "release-campaign.tests: PASS (run=$script:caseCount skipped=0)" -ForegroundColor Green
exit 0
