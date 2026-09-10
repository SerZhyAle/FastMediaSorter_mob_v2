<#
.SYNOPSIS
  S2851: measure the app_v2 unit suite's wall clock against fms.unitTestMaxParallelForks, recording
  for every run what else was building at the same time.

.DESCRIPTION
  Runs the whole unit suite once per requested fork count, in the order given, through
  check-standard-fast.ps1 - so each run takes Build.Phone the ordinary way and a sibling session
  still gets its turn between runs. The point of the script is not the running; it is the row it
  appends afterwards.

  The row carries the concurrency witness. A run's wall clock is only comparable to another run's if
  nothing else was building during it, and on this host that is not the common case: of the first
  four runs measured by hand on 2026-09-10, the 1-fork baseline had three wear runs inside its
  window and was silently unusable, while two runs of the SAME 2-fork setting on a quiet host
  measured 256 s and 369 s. Deciding contamination by hand after the fact means re-reading every
  check_fast_*.log's header and end time and doing interval arithmetic; done once per run here, it
  is a column.

  Overlap is computed from the other check_fast_*.log files: header `Date:` is the start, last write
  time is the end. That is the same pair the manual reconstruction used, and it covers every module
  and mode, so a wear compile counts as concurrency exactly like a phone one.

  Nothing here picks a value. It accumulates rows into a CSV across sessions; the value is chosen
  when enough uncontaminated repeats exist to separate the settings, which the CSV makes checkable
  rather than asserted.

.PARAMETER Forks
  The fms.unitTestMaxParallelForks values to measure, in run order, as ONE comma-separated string:
  -Forks "1,2,4". Not an [int[]] on purpose. `pwsh -File` does not split a comma list into an array -
  it binds the whole text as a single argument - so an [int[]] parameter given `1,2,4` silently
  becomes the single value 124, and the harness then spends a full suite run measuring a fork count
  nobody asked for. That happened here on 2026-09-10 before this parameter was a string.

.PARAMETER Repeats
  How many times to walk the whole -Forks list. Default 1. Repeats are interleaved rather than
  batched per setting, so a slow stretch of the host spreads across settings instead of landing on
  one of them.

.PARAMETER CsvPath
  Where rows accumulate. Default temp/S2851/fork-measurements.csv. Appends; never rewrites.

.OUTPUTS
  Exit 0 - every requested run reached a verdict and a row was appended for it. A run whose tests
           FAILED is still a measurement: this suite carries pre-existing failures, and the gate on
           comparability is the failure COUNT being equal across runs, which the row records.
  Exit 2 - could not measure: check-standard-fast.ps1 is missing, or a run produced no log to read a
           wall clock from. No row is written for such a run.
#>
param(
    [string]$Forks = "1,2,4",
    [int]$Repeats = 1,
    [string]$CsvPath
)

$ErrorActionPreference = "Stop"

$forkValues = @($Forks -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ } | ForEach-Object {
        $parsed = 0
        if (-not [int]::TryParse($_, [ref]$parsed) -or $parsed -lt 1) {
            Write-Error "measure-unit-fork-parallelism: -Forks expects positive integers separated by commas, got '$_'." -ErrorAction Continue
            exit 2
        }
        $parsed
    })
if ($forkValues.Count -eq 0) {
    Write-Error "measure-unit-fork-parallelism: -Forks resolved to no values." -ErrorAction Continue
    exit 2
}

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
$checkScript = Join-Path $projectRoot "scripts\builders\check-standard-fast.ps1"
if (-not (Test-Path -Path $checkScript)) {
    Write-Error "measure-unit-fork-parallelism: check-standard-fast.ps1 not found at $checkScript - nothing can be measured." -ErrorAction Continue
    exit 2
}

if (-not $CsvPath) {
    $CsvPath = Join-Path $projectRoot "temp\S2851\fork-measurements.csv"
}
$csvDir = Split-Path -Parent $CsvPath
if (-not (Test-Path -Path $csvDir)) {
    New-Item -ItemType Directory -Path $csvDir -Force | Out-Null
}

$logDir = Join-Path $projectRoot "temp"

# A check_fast log names its own start in the header and its end by last write. Read both once per
# call so a run's overlap test does not re-parse the directory per candidate.
function Get-CheckRunWindows {
    param([string[]]$ExcludePath)

    $excluded = @{}
    foreach ($path in $ExcludePath) { if ($path) { $excluded[$path] = $true } }

    Get-ChildItem -Path $logDir -Filter "check_fast_*.log" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($excluded.ContainsKey($_.FullName)) { return }
        # A log this cannot read is dropped from the witness, never allowed to throw: the caller is
        # holding a finished measurement at this point, and losing that run to a malformed header
        # would discard minutes of exclusive Build.Phone time. That is not hypothetical - the first
        # version threw here and did exactly that.
        try {
            $header = Get-Content -Path $_.FullName -TotalCount 8 -ErrorAction SilentlyContinue
            $dateLine = @($header | Where-Object { $_ -like 'Date:*' })
            if ($dateLine.Count -eq 0) { return }
            $raw = ($dateLine[0] -replace '^Date:\s*', '').Trim()
            $stamp = [datetime]::ParseExact($raw, 'yyyy-MM-dd HH:mm:ss', [cultureinfo]::InvariantCulture)
            $moduleLine = @($header | Where-Object { $_ -like 'Module:*' })
            $modeLine = @($header | Where-Object { $_ -like 'Mode:*' })
            [pscustomobject]@{
                Start  = $stamp
                End    = $_.LastWriteTime
                Module = if ($moduleLine.Count) { ($moduleLine[0] -replace '^Module:\s*', '').Trim() } else { 'unknown' }
                Mode   = if ($modeLine.Count) { ($modeLine[0] -replace '^Mode:\s*', '').Trim() } else { 'unknown' }
            }
        } catch {
            Write-Host "  (witness: skipped unreadable log $($_.Exception.Message))" -ForegroundColor DarkGray
        }
    }
}

$rows = @()
$exitCode = 0

foreach ($repeat in 1..$Repeats) {
    foreach ($forkCount in $forkValues) {
        $before = @(Get-ChildItem -Path $logDir -Filter "check_fast_app_v2_Unit_*.log" -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty FullName)

        Write-Host "measure-unit-fork-parallelism: repeat $repeat/$Repeats, forks=$forkCount .." -ForegroundColor Cyan
        $started = Get-Date
        & $checkScript -Mode Unit -Module app_v2 -ProjectProperty "fms.unitTestMaxParallelForks=$forkCount" -BlockThrough | Out-Null
        $checkExit = $LASTEXITCODE
        $ended = Get-Date

        $runLog = Get-ChildItem -Path $logDir -Filter "check_fast_app_v2_Unit_*.log" -ErrorAction SilentlyContinue |
            Where-Object { $before -notcontains $_.FullName } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

        if (-not $runLog) {
            Write-Error "measure-unit-fork-parallelism: forks=$forkCount produced no new log - no wall clock to record, row skipped." -ErrorAction Continue
            $exitCode = 2
            continue
        }

        $text = Get-Content -Path $runLog.FullName -ErrorAction SilentlyContinue
        # Each forked test worker prints this once on startup; the count is how often the worker was
        # recycled, which forkEvery bounds in theory and has not matched in practice.
        $jvmForks = @($text | Where-Object { $_ -like '*Sharing is only supported*' }).Count
        $failedTests = @($text | Where-Object { $_ -match ' FAILED$' }).Count
        $verdictLine = @($text | Where-Object { $_ -match 'BUILD (SUCCESSFUL|FAILED) in ' }) | Select-Object -Last 1

        # The run's own log is excluded, so a run never counts itself as its own concurrency.
        # The witness is an enrichment of a measurement already taken: a failure here costs a column,
        # never the row, so -1 marks "not witnessed" and is distinguishable from a witnessed 0.
        $overlapping = @()
        $overlapSeconds = -1
        try {
            $overlapping = @(Get-CheckRunWindows -ExcludePath @($runLog.FullName) |
                    Where-Object { $_.Start -lt $ended -and $_.End -gt $started })
            $overlapSeconds = 0
            foreach ($other in $overlapping) {
                $from = if ($other.Start -gt $started) { $other.Start } else { $started }
                $to = if ($other.End -lt $ended) { $other.End } else { $ended }
                $overlapSeconds += [int]($to - $from).TotalSeconds
            }
        } catch {
            Write-Host "  (witness unavailable: $($_.Exception.Message))" -ForegroundColor DarkYellow
        }

        $row = [pscustomobject]@{
            Stamp            = $started.ToString('yyyy-MM-dd HH:mm:ss')
            Forks            = $forkCount
            WallSeconds      = [int]($ended - $started).TotalSeconds
            JvmForks         = $jvmForks
            FailedTests      = $failedTests
            CheckExit        = $checkExit
            ConcurrentRuns   = if ($overlapSeconds -lt 0) { -1 } else { $overlapping.Count }
            ConcurrentSecs   = $overlapSeconds
            ConcurrentDetail = (($overlapping | ForEach-Object { "$($_.Module)/$($_.Mode)" }) -join ' ')
            Verdict          = if ($verdictLine) { $verdictLine.Trim() } else { 'no verdict line' }
            Log              = $runLog.Name
        }
        $rows += $row
        $row | Export-Csv -Path $CsvPath -Append -NoTypeInformation -Encoding utf8

        Write-Host ("  wall={0}s jvmForks={1} failedTests={2} concurrent={3} run(s)/{4}s" -f `
                $row.WallSeconds, $row.JvmForks, $row.FailedTests, $row.ConcurrentRuns, $row.ConcurrentSecs) -ForegroundColor DarkCyan
    }
}

Write-Host ""
Write-Host "measure-unit-fork-parallelism: $($rows.Count) row(s) appended to $CsvPath" -ForegroundColor Green
Write-Host "A row with ConcurrentRuns=0 is the only kind that compares to another run directly." -ForegroundColor DarkGray
exit $exitCode
