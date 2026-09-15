#requires -Version 7.0
<#
.SYNOPSIS
    S3151: contract suite for measure-pipeline-load.ps1 over a synthetic transcript fixture.

.DESCRIPTION
    One pipeline session holds a call per bucket, a failed catalog call and two transcript lines
    of one API response repeating its usage; a non-pipeline session and a subagent transcript sit
    beside it and must not be read.

    Exit codes:
      0  every case passed.
      1  at least one case failed.
      2  cannot verify - the subject script is missing.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$subject = Join-Path $repoRoot 'scripts/metrics/measure-pipeline-load.ps1'
if (-not (Test-Path -LiteralPath $subject)) { Write-Host "cannot verify: $subject is missing."; exit 2 }
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixture = Join-Path $repoRoot "temp/scratch/pipeline-load-tests-$PID"
if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
$root = Join-Path $fixture 'transcripts'
$empty = Join-Path $fixture 'empty'
New-Item -ItemType Directory -Force -Path (Join-Path $root 'session-a/subagents'), $empty | Out-Null

function New-Line($Object) { return ($Object | ConvertTo-Json -Depth 20 -Compress) }
function New-ToolUse([string]$Id, [string]$Name, [hashtable]$ToolInput, [string]$RequestId = '') {
    return New-Line ([ordered]@{
            type = 'assistant'; requestId = $(if ($RequestId) { $RequestId } else { "req-$Id" })
            message = [ordered]@{ role = 'assistant'; content = @([ordered]@{ type = 'tool_use'; id = $Id; name = $Name; input = $ToolInput }); usage = @{ output_tokens = 10 } }
        })
}
function New-Result([string]$Id, [string]$Text, [bool]$IsError = $false) {
    return New-Line ([ordered]@{
            type = 'user'
            message = [ordered]@{ role = 'user'; content = @([ordered]@{ type = 'tool_result'; tool_use_id = $Id; content = $Text; is_error = $IsError }) }
        })
}

$calls = @(
    @('t1', 'Edit', @{ file_path = 'P:\repo\app_v2\src\main\X.kt' }, 'CODE'),
    @('t2', 'PowerShell', @{ command = 'pwsh -NoProfile -File ./a.ps1 fk' }, 'BUILD'),
    @('t3', 'PowerShell', @{ command = 'pwsh -NoProfile -File scripts/devtest/adb.ps1 shot' }, 'DEVICE'),
    @('t4', 'Read', @{ file_path = 'P:/repo/PLAN/S9999_x.md' }, 'SPEC_DOC'),
    @('t5', 'Bash', @{ command = 'pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S9999' }, 'CATALOG_CLI'),
    @('t6', 'PowerShell', @{ command = 'pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1' }, 'GATES'),
    @('t7', 'Read', @{ file_path = 'P:/repo/.claude/commands/spec-all.md' }, 'INSTRUCTIONS'),
    @('t8', 'PowerShell', @{ command = 'pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Claim' }, 'LOCKS_CHAT'),
    @('t9', 'ToolSearch', @{ query = 'select:Monitor' }, 'OTHER'),
    @('t10', 'Skill', @{ skill = 'spec-dev' }, 'INSTRUCTIONS'),
    @('t11', 'PowerShell', @{ command = 'pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S9999' }, 'CATALOG_CLI'),
    @('t12', 'Write', @{ file_path = 'wear/src/main/Y.kt' }, 'CODE')
)
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add((New-Line ([ordered]@{ type = 'user'; message = [ordered]@{ role = 'user'; content = '<command-name>/spec-all</command-name> S9999' } })))
$lines.Add((New-Line ([ordered]@{ type = 'assistant'; requestId = 'req-shared'; message = [ordered]@{ role = 'assistant'; content = @(@{ type = 'text'; text = 'hello' }); usage = @{ output_tokens = 100 } } })))
foreach ($c in $calls) {
    $requestId = if ($c[0] -eq 't1') { 'req-shared' } else { '' }
    $lines.Add((New-ToolUse $c[0] $c[1] $c[2] $requestId))
    $lines.Add((New-Result $c[0] ('x' * 5) ($c[0] -eq 't11')))
}
Set-Content -LiteralPath (Join-Path $root 'session-a.jsonl') -Value $lines -Encoding utf8
Set-Content -LiteralPath (Join-Path $root 'session-b.jsonl') -Encoding utf8 -Value @(
    (New-Line ([ordered]@{ type = 'user'; message = [ordered]@{ role = 'user'; content = 'hello, no pipeline here' } })),
    (New-ToolUse 'u1' 'Edit' @{ file_path = 'app_v2/Z.kt' })
)
Set-Content -LiteralPath (Join-Path $root 'session-a/subagents/agent-1.jsonl') -Value $lines -Encoding utf8

$failures = 0
function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) { Write-Host "  PASS  $Name" } else { Write-Host "  FAIL  $Name - $Detail"; $script:failures++ }
}

try {
    $jsonPath = Join-Path $fixture 'report.json'
    $out = & $pwshExe -NoProfile -File $subject -TranscriptRoot $root -JsonPath $jsonPath 2>&1 | Out-String
    $code = [int]$LASTEXITCODE
    Assert-That 'report run exits 0' ($code -eq 0 -and (Test-Path -LiteralPath $jsonPath)) "exit $code : $out"
    if (Test-Path -LiteralPath $jsonPath) {
        $report = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
        $byName = @{}
        foreach ($row in $report.all.buckets) { $byName[$row.name] = $row }
        $expected = @{ CODE = 2; BUILD = 1; DEVICE = 1; SPEC_DOC = 1; CATALOG_CLI = 2; GATES = 1; INSTRUCTIONS = 2; LOCKS_CHAT = 1; OTHER = 1 }
        $mismatch = @($expected.Keys | Where-Object { $byName[$_].calls -ne $expected[$_] } | ForEach-Object { "$_=$($byName[$_].calls)" })
        Assert-That 'every call lands in its bucket' ($mismatch.Count -eq 0) ($mismatch -join ', ')
        Assert-That 'only the pipeline main-tier session is read' ($report.all.sessions -eq 1 -and $report.all.calls -eq 12) "sessions $($report.all.sessions), calls $($report.all.calls)"
        Assert-That 'failure counts is_error only' ($byName['CATALOG_CLI'].failures -eq 1 -and (($report.all.buckets | Measure-Object -Property failures -Sum).Sum) -eq 1) "catalog failures $($byName['CATALOG_CLI'].failures)"
        Assert-That 'usage is deduped by requestId' ($report.all.outputTokens -eq 210) "output tokens $($report.all.outputTokens), expected 100 + 11 x 10"
        Assert-That 'result chars are summed' ($byName['CODE'].resultChars -eq 10) "CODE result chars $($byName['CODE'].resultChars)"
        Assert-That 'two code files put the session in the small set' ($report.smallCodeSessions.sessions -eq 1) "small sessions $($report.smallCodeSessions.sessions)"
    }

    $out = & $pwshExe -NoProfile -File $subject -TranscriptRoot $empty 2>&1 | Out-String
    Assert-That 'empty root exits 2' ([int]$LASTEXITCODE -eq 2) "exit $LASTEXITCODE : $out"
}
finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures) { Write-Host "measure-pipeline-load suite: $failures failure(s)"; exit 1 }
Write-Host 'measure-pipeline-load suite: PASS'
exit 0
