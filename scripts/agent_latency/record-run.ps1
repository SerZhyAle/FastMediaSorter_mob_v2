#requires -Version 7.0
<#
.SYNOPSIS
    S2760: validates one agent latency record and stores it under the ticket's evidence root.

.DESCRIPTION
    A latency claim is only as good as the record behind it, so this command is the only way a
    record enters the evidence set. It does three things and refuses everything else:

      - validates the record against scripts/agent_latency/latency-record.schema.json;
      - checks that every required stage carries either a measured value or an explicit
        unavailable reason, because an omitted stage averages as zero and so turns a missing
        measurement into a favourable one;
      - preserves the client-observed fields exactly as supplied. firstResponseMs and
        modelDurationMs cannot be seen from inside this repository at all, so a value written
        here would be a guess in a measurement's shape. The repository-observed stages
        (toolDurationMs, queueWaitMs) may be measured by the caller with -ToolDurationMs and
        -QueueWaitMs, which stamp source=repository; supplying either while the record already
        carries that stage is refused rather than silently resolved.

    Nothing is written outside -OutRoot, which defaults to the ticket-bound temp/S2760.

Exit codes: 0 accepted and stored; 1 the record is invalid against the schema; 2 the record is
well-formed but missing required evidence; 3 the input could not be read or parsed.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Record,

    [string]$OutRoot = 'temp/S2760',

    [double]$ToolDurationMs = -1,

    [double]$QueueWaitMs = -1,

    [switch]$Json
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$schemaPath = Join-Path $PSScriptRoot 'latency-record.schema.json'

function Write-Fail([string]$message) {
    Write-Error $message -ErrorAction Continue
}

if (-not (Test-Path -LiteralPath $schemaPath)) {
    Write-Fail "record-run: schema not found at $schemaPath"
    exit 3
}

if (-not (Test-Path -LiteralPath $Record)) {
    Write-Fail "record-run: record file not found: $Record"
    exit 3
}

$raw = $null
try {
    $raw = Get-Content -LiteralPath $Record -Raw
} catch {
    Write-Fail "record-run: cannot read ${Record}: $($_.Exception.Message)"
    exit 3
}

$runRecord = $null
try {
    $runRecord = $raw | ConvertFrom-Json -Depth 20
} catch {
    Write-Fail "record-run: ${Record} is not valid JSON: $($_.Exception.Message)"
    exit 3
}

$schemaText = Get-Content -LiteralPath $schemaPath -Raw
$schemaErrors = @()
try {
    # -ErrorAction SilentlyContinue keeps the per-error stream out of the transcript; the boolean
    # result is what decides, and the messages are re-emitted below in one block.
    if (-not (Test-Json -Json $raw -Schema $schemaText -ErrorVariable schemaErrors -ErrorAction SilentlyContinue)) {
        Write-Fail "record-run: ${Record} does not satisfy latency-record.schema.json"
        foreach ($e in $schemaErrors) { Write-Host "  $e" }
        exit 1
    }
} catch {
    Write-Fail "record-run: schema validation could not run: $($_.Exception.Message)"
    exit 3
}

# The repository-observed stages may be measured by the caller. Refuse rather than resolve a
# collision: two sources for one stage means one of them is wrong and the script cannot tell which.
$measured = @{}
if ($ToolDurationMs -ge 0) { $measured['toolDurationMs'] = $ToolDurationMs }
if ($QueueWaitMs -ge 0) { $measured['queueWaitMs'] = $QueueWaitMs }
foreach ($stage in $measured.Keys) {
    if ($null -ne $runRecord.$stage) {
        Write-Fail "record-run: $stage was supplied on the command line and is already present in the record; remove one"
        exit 2
    }
    $runRecord | Add-Member -NotePropertyName $stage -NotePropertyValue ([pscustomobject]@{
        valueMs = $measured[$stage]
        source  = 'repository'
    }) -Force
}

$clientStages = @('firstResponseMs', 'modelDurationMs')
$repositoryStages = @('toolDurationMs', 'queueWaitMs')
$requiredStages = @('firstResponseMs', 'toolDurationMs', 'queueWaitMs', 'totalCompletionMs')

$problems = @()
foreach ($stage in $requiredStages) {
    $value = $runRecord.$stage
    if ($null -eq $value) {
        $problems += "$stage is absent; record it as a value or as unavailable with a reason"
        continue
    }
    $hasValue = $null -ne $value.valueMs
    $hasReason = $value.unavailable -eq $true -and -not [string]::IsNullOrWhiteSpace([string]$value.unavailableReason)
    if (-not $hasValue -and -not $hasReason) {
        $problems += "$stage carries neither a measurement nor an unavailable reason"
    }
    if ($hasValue -and $stage -in $clientStages -and $value.source -ne 'client') {
        $problems += "$stage is client-observed and must carry source 'client'"
    }
    if ($hasValue -and $stage -in $repositoryStages -and $value.source -ne 'repository') {
        $problems += "$stage is repository-observed and must carry source 'repository'"
    }
}

if (-not $runRecord.validationEvidence -or @($runRecord.validationEvidence).Count -eq 0) {
    $problems += 'validationEvidence is empty; a run with no evidence has no verdict to compare'
}
if (-not $runRecord.acceptanceCriteria -or @($runRecord.acceptanceCriteria).Count -eq 0) {
    $problems += 'acceptanceCriteria is empty; a route can only be compared against stated criteria'
}

if ($problems.Count -gt 0) {
    Write-Fail "record-run: ${Record} is missing required evidence"
    foreach ($p in $problems) { Write-Host "  - $p" }
    exit 2
}

$outDir = Join-Path $repoRoot (Join-Path $OutRoot 'records')
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

$safeId = ($runRecord.recordId -replace '[^A-Za-z0-9._-]', '_')
$outPath = Join-Path $outDir "$safeId.json"
$runRecord | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $outPath -Encoding utf8

if ($Json) {
    [pscustomobject]@{
        status     = 'stored'
        recordId   = $runRecord.recordId
        controlTask = $runRecord.controlTaskId
        route      = $runRecord.routeId
        path       = $outPath
    } | ConvertTo-Json -Depth 5
} else {
    Write-Host "record-run: stored $($runRecord.recordId) (control task '$($runRecord.controlTaskId)', route '$($runRecord.routeId)') -> $outPath"
}
exit 0
