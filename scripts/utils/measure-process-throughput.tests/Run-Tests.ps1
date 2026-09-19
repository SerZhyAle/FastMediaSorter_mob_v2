#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/utils/measure-process-throughput.ps1 (S3308).

.DESCRIPTION
    Every later phase of S3308 reads this summary rather than re-deriving the numbers, so a wrong
    value here does not surface as a failure - it surfaces as a wrong decision about how much work
    may run at once. The cases therefore pin the two answers that are easy to get silently wrong:
    an empty window must say n/a rather than zero, and a SKIP gate row must not enter the total.

    Each case runs the script in its own child process against a throwaway fixture root passed with
    -Root, so no case reads the repository's own journals.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'measure-process-throughput.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Error "script not found: $script" -ErrorAction Continue
    exit 1
}

$pwshExe = (Get-Process -Id $PID).Path
$failures = 0

function New-FixtureRoot {
    param(
        [string[]] $RunRows = @(),
        [string[]] $GateRows = @(),
        [hashtable] $Instances,
        [object] $HoldSecondsMax,
        [object[]] $LockEvents = @(),
        [switch] $NoQueueDir
    )
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('throughput-tests-' + [guid]::NewGuid().ToString('n').Substring(0, 8))
    New-Item -ItemType Directory -Path (Join-Path $root 'temp/metrics') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'temp/AGENT-CHAT/progress') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'temp/LOCK-HANDOFF') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'docs') -Force | Out-Null
    if (-not $NoQueueDir) {
        New-Item -ItemType Directory -Path (Join-Path $root 'temp/spec-queue') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $root 'temp/spec-queue/runs-a.jsonl') -Value $RunRows -Encoding UTF8
    }
    if ($Instances -or $null -ne $HoldSecondsMax) {
        $profileDoc = [ordered]@{ runner = [ordered]@{ instances = ($(if ($Instances) { $Instances } else { @{} })) } }
        if ($null -ne $HoldSecondsMax) { $profileDoc['concurrency'] = [ordered]@{ holdSecondsMax = $HoldSecondsMax } }
        Set-Content -LiteralPath (Join-Path $root '.sza-profile.json') -Value ($profileDoc | ConvertTo-Json -Depth 6) -Encoding UTF8
    }
    $index = 0
    foreach ($event in $LockEvents) {
        $index++
        $record = [ordered]@{
            schema  = 1
            stream  = 'progress'
            at      = ([datetimeoffset]$event.At).ToUniversalTime().ToString('o')
            agent   = [ordered]@{ id = $event.Agent; name = $event.Holder }
            kind    = 'lock'
            domains = @($event.Domain)
            note    = "$($event.Verb) $($event.Domain)"
        }
        $name = '{0:D4}_lock_{1}.json' -f $index, $event.Agent
        Set-Content -LiteralPath (Join-Path $root "temp/AGENT-CHAT/progress/$name") -Value ($record | ConvertTo-Json -Depth 5) -Encoding UTF8
    }
    Set-Content -LiteralPath (Join-Path $root 'temp/metrics/gate-executions.jsonl') -Value $GateRows -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $root 'docs/BUILD_TEST_FAST_PATH.md') -Value @(
        '| target | measured | window |',
        '|---|---|---|',
        '| `a.ps1 fk` | 14.1 s | foreground |'
    ) -Encoding UTF8
    return $root
}

function Invoke-Summary {
    param([string[]] $SummaryArgs)
    $output = & $pwshExe -NoProfile -File $script @SummaryArgs 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Text     = ($output | Out-String)
    }
}

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
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

$roots = @()

try {
    Assert-Case 'T1 an empty window reports n/a for every value and exits 0' {
        $root = New-FixtureRoot
        $script:roots += $root
        $result = Invoke-Summary @('-Root', $root, '-Since', '2030-01-01')
        if ($result.ExitCode -ne 0) { return "expected exit 0, got $($result.ExitCode)" }
        $naCount = ([regex]::Matches($result.Text, 'n/a')).Count
        if ($naCount -lt 5) { return "expected five n/a values, found $naCount in: $($result.Text)" }
    }

    Assert-Case 'T2 an absent runner journal directory exits 2' {
        $root = New-FixtureRoot -NoQueueDir
        $script:roots += $root
        $result = Invoke-Summary @('-Root', $root, '-Since', '2026-01-01')
        if ($result.ExitCode -ne 2) { return "expected exit 2, got $($result.ExitCode)" }
        if ($result.Text -notmatch 'could not verify') { return "refusal did not say what was missing: $($result.Text)" }
    }

    Assert-Case 'T3 a SKIP gate row is excluded from the gate total' {
        $root = New-FixtureRoot -GateRows @(
            '{"timestampUtc":"2026-06-01T10:00:00.0000000Z","gate":"a","status":"PASS","elapsedMs":1000}',
            '{"timestampUtc":"2026-06-01T10:00:01.0000000Z","gate":"b","status":"SKIP","elapsedMs":9000}'
        )
        $script:roots += $root
        $result = Invoke-Summary @('-Root', $root, '-Since', '2026-05-01', '-Until', '2026-07-01', '-Json')
        if ($result.ExitCode -ne 0) { return "expected exit 0, got $($result.ExitCode)" }
        $payload = $result.Text | ConvertFrom-Json
        if ($payload.gateSeconds -ne 1) { return "expected 1 s of gate time, got $($payload.gateSeconds)" }
        if ($payload.gateExecutions -ne 1) { return "expected 1 counted execution, got $($payload.gateExecutions)" }
    }

    Assert-Case 'T4 the -Json object carries the same values as the text block' {
        $root = New-FixtureRoot -RunRows @(
            '{"id":"S0001","model":"opus","policy":"tiered","moved":true,"finishedAt":"2026-06-01T10:00:00"}',
            '{"id":"S0002","model":"sonnet","policy":"shape","moved":false,"finishedAt":"2026-06-01T11:00:00"}'
        ) -GateRows @(
            '{"timestampUtc":"2026-06-01T10:00:00.0000000Z","gate":"a","status":"PASS","elapsedMs":2500}'
        )
        $script:roots += $root
        $window = @('-Root', $root, '-Since', '2026-05-01', '-Until', '2026-07-01')
        $text = Invoke-Summary $window
        $json = (Invoke-Summary ($window + '-Json')).Text | ConvertFrom-Json
        if ($json.statusMoves -ne 1) { return "expected 1 status move, got $($json.statusMoves)" }
        if ($json.idleRunShare -ne 50) { return "expected a 50 % idle share, got $($json.idleRunShare)" }
        if ($json.cheapModelShare -ne 50) { return "expected a 50 % cheap share, got $($json.cheapModelShare)" }
        if ($json.gateSeconds -ne 2.5) { return "expected 2.5 s of gate time, got $($json.gateSeconds)" }
        foreach ($value in @($json.statusMoves, $json.idleRunShare, $json.cheapModelShare, $json.gateSeconds)) {
            if ($text.Text -notmatch [regex]::Escape([string]$value)) {
                return "the text block does not carry $value : $($text.Text)"
            }
        }
    }

    Assert-Case 'T5 a run written before the policy field existed lands under unknown' {
        $root = New-FixtureRoot -RunRows @(
            '{"id":"S0003","model":"opus","moved":true,"finishedAt":"2026-06-01T10:00:00"}',
            '{"id":"S0004","model":"opus","policy":"tiered","moved":true,"finishedAt":"2026-06-01T11:00:00"}'
        )
        $script:roots += $root
        $json = (Invoke-Summary @('-Root', $root, '-Since', '2026-05-01', '-Until', '2026-07-01', '-Json')).Text | ConvertFrom-Json
        $unknown = @($json.policies | Where-Object { $_.Policy -eq 'unknown' })
        if ($unknown.Count -ne 1) { return 'the policy-less run was not reported under unknown' }
        if ($unknown[0].Runs -ne 1) { return "expected 1 unknown run, got $($unknown[0].Runs)" }
        $tiered = @($json.policies | Where-Object { $_.Policy -eq 'tiered' })
        if ($tiered[0].Runs -ne 1) { return 'the policy-less run was folded into a declared policy' }
    }

    Assert-Case 'T6 a declared policy with zero runs warns and leaves the exit code at 0' {
        $root = New-FixtureRoot -RunRows @(
            '{"id":"S0005","model":"opus","policy":"tiered","moved":true,"finishedAt":"2026-06-01T10:00:00"}'
        ) -Instances @{ c = @{ modelPolicy = 'shape' } }
        $script:roots += $root
        $result = Invoke-Summary @('-Root', $root, '-Since', '2026-05-01', '-Until', '2026-07-01')
        if ($result.ExitCode -ne 0) { return "expected exit 0, got $($result.ExitCode)" }
        if ($result.Text -notmatch 'zero runs') { return "no zero runs warning: $($result.Text)" }
        if ($result.Text -notmatch "shape") { return 'the warning does not name the policy' }
    }

    Assert-Case 'T7 a declared policy that did run produces no warning' {
        $root = New-FixtureRoot -RunRows @(
            '{"id":"S0006","model":"sonnet","policy":"shape","moved":true,"finishedAt":"2026-06-01T10:00:00"}'
        ) -Instances @{ c = @{ modelPolicy = 'shape' } }
        $script:roots += $root
        $result = Invoke-Summary @('-Root', $root, '-Since', '2026-05-01', '-Until', '2026-07-01')
        if ($result.Text -match 'zero runs') { return "warned about a policy that ran: $($result.Text)" }
    }

    Assert-Case 'T8 an acquire with no release is reported as still held, a short hold is not reported' {
        $root = New-FixtureRoot -HoldSecondsMax 600 -LockEvents @(
            @{ At = '2026-06-01T10:00:00'; Verb = 'acquired'; Domain = 'Code.Scripts'; Holder = 'left-lock'; Agent = 'agent-1' },
            @{ At = '2026-06-01T10:00:30'; Verb = 'acquired'; Domain = 'Code.Phone'; Holder = 'short-lock'; Agent = 'agent-2' },
            @{ At = '2026-06-01T10:01:00'; Verb = 'released'; Domain = 'Code.Phone'; Holder = 'short-lock'; Agent = 'agent-2' }
        )
        $script:roots += $root
        $result = Invoke-Summary @('-Root', $root, '-Since', '2026-05-01', '-Until', '2030-01-01')
        if ($result.ExitCode -ne 0) { return "expected exit 0, got $($result.ExitCode)" }
        if ($result.Text -notmatch 'still held') { return "the unreleased hold was dropped: $($result.Text)" }
        if ($result.Text -notmatch 'Code\.Scripts') { return 'the report does not name the domain' }
        if ($result.Text -match 'Code\.Phone') { return 'a hold under the threshold was reported' }
    }

    Assert-Case 'T9 a profile with no concurrency block leaves the hold line n/a and exits 0' {
        $root = New-FixtureRoot -Instances @{ a = @{} } -LockEvents @(
            @{ At = '2026-06-01T10:00:00'; Verb = 'acquired'; Domain = 'Code.Scripts'; Holder = 'left-lock'; Agent = 'agent-1' }
        )
        $script:roots += $root
        $result = Invoke-Summary @('-Root', $root, '-Since', '2026-05-01', '-Until', '2030-01-01')
        if ($result.ExitCode -ne 0) { return "expected exit 0, got $($result.ExitCode)" }
        if ($result.Text -notmatch 'long domain holds\s*:\s*n/a') { return "the hold line is not n/a: $($result.Text)" }
    }
}
finally {
    foreach ($root in $roots) {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($failures -gt 0) {
    Write-Host "measure-process-throughput tests: $failures case(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host 'measure-process-throughput tests: all cases passed.' -ForegroundColor Green
exit 0
