# Run-Tests.ps1 (S3322) - regression suite for scripts/quality/compare-macrobenchmark-runs.ps1.
#
# The gate applies scripts/quality/macrobenchmark-budgets.json to two macrobenchmark result files. A
# comparison that only ever answers PASS proves nothing, so each of its three verdicts is asserted
# against a synthetic pair - within budget, breached, and cannot-verify - plus the property the -Json
# form exists for: stdout must parse on its own, with every diagnostic on stderr.
#
# Hermetic: fixtures are written to a temp dir and removed in a finally block. Nothing under scripts/
# or benchmark/ is read for state and nothing is mutated.
#
# Usage:  pwsh -NoProfile -File scripts/quality/compare-macrobenchmark-runs.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gate = Join-Path $repoRoot 'scripts/quality/compare-macrobenchmark-runs.ps1'
$sandbox = Join-Path $env:TEMP "compare-macrobenchmark-runs.tests.$PID"

$script:pass = 0
$script:fail = 0

function Assert-That {
    param([string]$Name, [bool]$Ok, [string]$Detail)

    if ($Ok) {
        $script:pass++
        Write-Host "PASS $Name" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "FAIL $Name - $Detail" -ForegroundColor Red
    }
}

function New-RunFile {
    param([string]$Path, [double]$BrowseReadyMedian, [double]$FlingP99)

    $document = @{
        benchmarks = @(
            @{
                name = 'browseReadiness'
                className = 'com.sza.fastmediasorter.benchmark.NavigationBenchmarks'
                metrics = @{ FMS_BROWSE_READYMs = @{ median = $BrowseReadyMedian } }
                sampledMetrics = @{}
            },
            @{
                name = 'browseListFling'
                className = 'com.sza.fastmediasorter.benchmark.BrowseInteractionBenchmarks'
                metrics = @{}
                sampledMetrics = @{ frameDurationCpuMs = @{ P99 = $FlingP99 } }
            }
        )
    }
    $document | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding utf8
}

try {
    $null = New-Item -ItemType Directory -Path $sandbox -Force

    $baseline = Join-Path $sandbox 'baseline.json'
    $clean = Join-Path $sandbox 'candidate-clean.json'
    $regressed = Join-Path $sandbox 'candidate-regressed.json'
    $empty = Join-Path $sandbox 'candidate-empty.json'

    New-RunFile -Path $baseline -BrowseReadyMedian 120 -FlingP99 20
    # Within budget on both records: +4 ms / 3.3% and +1.5 ms / 7.5%.
    New-RunFile -Path $clean -BrowseReadyMedian 124 -FlingP99 21.5
    # The fling percentile doubles: past 8 ms AND past 20%, which is what a breach requires.
    New-RunFile -Path $regressed -BrowseReadyMedian 124 -FlingP99 41
    '{ "benchmarks": [] }' | Set-Content -LiteralPath $empty -Encoding utf8

    & $pwshExe -NoProfile -File $gate -Baseline $baseline -Candidate $clean | Out-Null
    Assert-That -Name 'within budget exits 0' -Ok ($LASTEXITCODE -eq 0) -Detail "exit $LASTEXITCODE"

    $breachOutput = & $pwshExe -NoProfile -File $gate -Baseline $baseline -Candidate $regressed 2>&1
    $breachExit = $LASTEXITCODE
    Assert-That -Name 'budget breach exits 1' -Ok ($breachExit -eq 1) -Detail "exit $breachExit"
    Assert-That -Name 'budget breach names the record' `
        -Ok ([bool]($breachOutput -match 'browseListFling')) -Detail 'output does not name browseListFling'

    & $pwshExe -NoProfile -File $gate -Baseline $baseline -Candidate (Join-Path $sandbox 'absent.json') 2>$null | Out-Null
    Assert-That -Name 'missing candidate exits 2' -Ok ($LASTEXITCODE -eq 2) -Detail "exit $LASTEXITCODE"

    & $pwshExe -NoProfile -File $gate -Baseline $baseline -Candidate $empty 2>$null | Out-Null
    Assert-That -Name 'no shared budgeted record exits 2' -Ok ($LASTEXITCODE -eq 2) -Detail "exit $LASTEXITCODE"

    $jsonStdout = & $pwshExe -NoProfile -File $gate -Baseline $baseline -Candidate $clean -Json 2>$null
    $parsed = $null
    try { $parsed = ($jsonStdout -join "`n") | ConvertFrom-Json } catch { $parsed = $null }
    Assert-That -Name '-Json stdout parses alone' -Ok ($null -ne $parsed) -Detail 'stdout is not a JSON document'
    if ($null -ne $parsed) {
        Assert-That -Name '-Json counts the compared records' `
            -Ok ($parsed.compared -eq 2 -and $parsed.failed -eq 0) `
            -Detail "compared=$($parsed.compared) failed=$($parsed.failed)"
    }
}
finally {
    if (Test-Path -LiteralPath $sandbox) {
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "compare-macrobenchmark-runs.tests: $script:pass passed, $script:fail failed"
if ($script:fail -gt 0) { exit 1 }
exit 0
