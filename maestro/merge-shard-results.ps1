<#
.SYNOPSIS
  Merges the -Json results of N concurrent maestro/run-tests.ps1 shards into one result (S4249).

.DESCRIPTION
  Each shard of a split suite writes its own result object; the verdict aggregator
  (scripts/devtest/prerelease-verdict.ps1) reads ONE. This tool joins them into an object of the
  runner's own shape - pass, total, failed, skipped, reason, launcherMode, flows - plus a shards
  array carrying each shard's device, total, failed and skipped, so the verdict reads a merged run
  exactly as it reads a sequential one.

  The merge refuses rather than guesses: exactly ExpectedCount inputs, shard indexes 1..N each once,
  and no flow name in two shards. A parallel branch that silently drops out of a verdict is the risk
  this refusal exists for (strategic S4249 section 7). The pure merge lives in lib/flow-selection.ps1.

  It also writes temp/scratch/maestro/last-failed.json from the merged flows, the list the next run
  of any shard reads to put those flows first.

  Exit codes:
    0 - merged, every judged flow passed
    2 - an input missing, unreadable, duplicated or outside the expected shard set; nothing written
    3 - merged, one or more flows failed (step / assertion)
    4 - merged, one or more flows had an execution error (outranks 3)

.PARAMETER Inputs
  Shard result files (the -Json output of maestro/run-tests.ps1 run with -ShardIndex/-ShardCount).
  One comma-separated string is accepted too, since pwsh -File binds `a,b` as one element.

.PARAMETER Output
  Path of the merged JSON result.

.PARAMETER ExpectedCount
  Number of shards the run was split into; the merge refuses any other number of inputs.

.PARAMETER LastFailedDir
  Directory receiving last-failed.json (default temp/scratch/maestro, the runner's own log dir).
  The contract suite points it at a scratch folder.

.EXAMPLE
  pwsh -NoProfile -File maestro/merge-shard-results.ps1 -Inputs temp/S0484/maestro_shard1_x.json,temp/S0484/maestro_shard2_x.json -Output temp/S0484/maestro_suite_x.json -ExpectedCount 2
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$Inputs,
    [Parameter(Mandatory)][string]$Output,
    [Parameter(Mandatory)][ValidateRange(1, 16)][int]$ExpectedCount,
    [string]$LastFailedDir
)

$ErrorActionPreference = 'Stop'
$MaestroDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $MaestroDir
. (Join-Path $MaestroDir 'lib/flow-selection.ps1')

if (-not $LastFailedDir) { $LastFailedDir = Join-Path $ProjectRoot 'temp/scratch/maestro' }

$paths = @($Inputs | ForEach-Object { ([string]$_) -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$results = @()
foreach ($path in $paths) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Write-Host "merge-shard-results: COULD NOT VERIFY - shard result missing: $path" -ForegroundColor Red
        exit 2
    }
    try {
        $results += , (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)
    } catch {
        Write-Host "merge-shard-results: COULD NOT VERIFY - shard result unreadable: $path ($($_.Exception.Message))" -ForegroundColor Red
        exit 2
    }
}

try {
    $merged = Merge-ShardResults -Results $results -ExpectedCount $ExpectedCount
} catch {
    Write-Host "merge-shard-results: COULD NOT VERIFY - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

$outDir = Split-Path -Parent $Output
if ($outDir -and -not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
Set-Content -LiteralPath $Output -Encoding utf8NoBOM -Value ($merged | ConvertTo-Json -Depth 6 -Compress)

if (-not (Test-Path -LiteralPath $LastFailedDir)) { New-Item -ItemType Directory -Path $LastFailedDir -Force | Out-Null }
Set-Content -LiteralPath (Join-Path $LastFailedDir (Get-LastFailedFileName)) -Encoding utf8NoBOM `
    -Value (ConvertTo-Json -InputObject @(Get-FailedFlowNames -Flows $merged.flows) -Compress)

$code = Get-MergedExitCode -Merged $merged
$flakyCount = @($merged.flows | Where-Object { $_.flaky }).Count
Write-Host ("merge-shard-results: {0} shard(s), {1} flow(s), {2} failed, {3} skipped, {4} flaky -> {5} (exit {6})" -f `
    $ExpectedCount, $merged.total, $merged.failed, $merged.skipped, $flakyCount, $Output, $code)
exit $code
