<#
.SYNOPSIS
    Measure whether one Gradle root tolerates two concurrent invocations (S2109).

.DESCRIPTION
    The repository has a single Gradle root: app_v2 and wear share one settings file, one
    daemon family, one set of caches and one machine. Splitting temp/BUILD.LOCK by module is
    therefore not a free rename - it is a bet that two gradle processes can work that root at
    the same time. This probe settles the bet with numbers instead of opinion.

    It acquires BUILD.LOCK ONCE for the whole measurement, so no sibling agent session can be
    hit by the concurrency being measured, and inside that single acquisition it runs N rounds:

      concurrent round - :wear:compileDebugKotlin and :app_v2:compileStandardDebugKotlin
                         started together, both waited on
      sequential round - the same two tasks one after the other, as the baseline

    Every task runs with --rerun-tasks. Without it the first round leaves both tasks UP-TO-DATE
    and every later round measures two no-ops racing each other, which is exactly the case that
    cannot contend - the first run of this probe produced three green rounds of which two did no
    work at all. Contention lives in the caches a real compile writes, so the probe has to pay
    for a real compile each round.

    Per round it records both exit codes, both wall times, the round wall time, the peak number
    of live java processes, and whether either output carried a Gradle lock-contention message.
    A CSV goes to -OutDir plus a summary line naming the failure count and the concurrent versus
    sequential wall time.

    The verdict this feeds is written by hand into
    PLAN/S2109_split-agent-locks-by-domain/research/01__concurrent-gradle-root.md - the probe
    reports, it does not decide.

.PARAMETER Rounds
    How many concurrent/sequential pairs to run. Default 3.

.PARAMETER OutDir
    Where the CSV and the raw gradle output go. Default temp/S2109.

.NOTES
    Exit codes:
    0 - measurement completed; read the CSV and the summary line for the result, which may
        well be "concurrency failed" - a red measurement is a completed measurement.
    1 - the probe itself failed (gradle wrapper missing, output directory unusable).
    2 - could not acquire BUILD.LOCK, so nothing was measured.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/measure-concurrent-build.ps1 -Rounds 3
#>
param(
    [int]$Rounds = 3,
    [string]$OutDir = "temp/S2109"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gradlew = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $gradlew)) {
    Write-Error "measure-concurrent-build: gradle wrapper not found at $gradlew." -ErrorAction Continue
    exit 1
}

$outPath = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repoRoot $OutDir }
try {
    if (-not (Test-Path -LiteralPath $outPath)) {
        New-Item -ItemType Directory -Path $outPath -Force | Out-Null
    }
}
catch {
    Write-Error "measure-concurrent-build: cannot create output directory $outPath - $_" -ErrorAction Continue
    exit 1
}

# Contention is what the whole measurement is about: Gradle serialises access to the caches of
# one project root with file locks, and when that wait exceeds its own ceiling it says so in
# these words rather than failing the task outright. A run can therefore be exit 0 and still be
# evidence against the split.
$contentionPatterns = @(
    'Timeout waiting to lock',
    'is currently in use by another Gradle instance'
)

$phoneTask = ':app_v2:compileStandardDebugKotlin'
$wearTask = ':wear:compileDebugKotlin'

function Start-GradleTask {
    param(
        [Parameter(Mandatory)][string]$Task,
        [Parameter(Mandatory)][string]$LogPath
    )
    return Start-Process -FilePath $gradlew `
        -ArgumentList @($Task, '--rerun-tasks', '--stacktrace') `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $LogPath `
        -RedirectStandardError "$LogPath.err" `
        -NoNewWindow -PassThru
}

function Wait-WithJvmSampling {
    param([Parameter(Mandatory)][System.Diagnostics.Process[]]$Process)
    $peak = 0
    while ($Process | Where-Object { -not $_.HasExited }) {
        $live = @(Get-Process -Name 'java' -ErrorAction SilentlyContinue).Count
        if ($live -gt $peak) { $peak = $live }
        Start-Sleep -Milliseconds 500
    }
    foreach ($p in $Process) { $p.WaitForExit() }
    return $peak
}

function Test-Contention {
    param([Parameter(Mandatory)][string[]]$LogPath)
    foreach ($path in $LogPath) {
        foreach ($candidate in @($path, "$path.err")) {
            if (-not (Test-Path -LiteralPath $candidate)) { continue }
            $text = Get-Content -LiteralPath $candidate -Raw -ErrorAction SilentlyContinue
            if ([string]::IsNullOrEmpty($text)) { continue }
            foreach ($pattern in $contentionPatterns) {
                if ($text -like "*$pattern*") { return $true }
            }
        }
    }
    return $false
}

. (Join-Path $repoRoot "scripts/utils/agent-lock.ps1")

# One acquisition for the entire measurement. The concurrency under test happens INSIDE the
# lock, so a sibling session never meets it - the probe measures gradle, not the coordination
# contract it is held under.
Enter-BuildLockOrExit -Reason "measure-concurrent-build.ps1 (S2109)"

$rows = New-Object System.Collections.Generic.List[object]
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$csvPath = Join-Path $outPath "concurrent-build-$stamp.csv"

try {
    for ($round = 1; $round -le $Rounds; $round++) {
        Write-Host "round $round/$Rounds - concurrent" -ForegroundColor Cyan
        $phoneLog = Join-Path $outPath "r$round-concurrent-phone.log"
        $wearLog = Join-Path $outPath "r$round-concurrent-wear.log"

        $roundWatch = [System.Diagnostics.Stopwatch]::StartNew()
        $phoneProc = Start-GradleTask -Task $phoneTask -LogPath $phoneLog
        $wearProc = Start-GradleTask -Task $wearTask -LogPath $wearLog
        $peak = Wait-WithJvmSampling -Process @($phoneProc, $wearProc)
        $roundWatch.Stop()

        $rows.Add([pscustomobject]@{
                round          = $round
                mode           = 'concurrent'
                phoneExitCode  = $phoneProc.ExitCode
                wearExitCode   = $wearProc.ExitCode
                roundSeconds   = [math]::Round($roundWatch.Elapsed.TotalSeconds, 1)
                peakJavaProcs  = $peak
                contention     = (Test-Contention -LogPath @($phoneLog, $wearLog))
            })

        Write-Host "round $round/$Rounds - sequential" -ForegroundColor Cyan
        $phoneSeqLog = Join-Path $outPath "r$round-sequential-phone.log"
        $wearSeqLog = Join-Path $outPath "r$round-sequential-wear.log"

        $seqWatch = [System.Diagnostics.Stopwatch]::StartNew()
        $phoneSeq = Start-GradleTask -Task $phoneTask -LogPath $phoneSeqLog
        $peakA = Wait-WithJvmSampling -Process @($phoneSeq)
        $wearSeq = Start-GradleTask -Task $wearTask -LogPath $wearSeqLog
        $peakB = Wait-WithJvmSampling -Process @($wearSeq)
        $seqWatch.Stop()

        $rows.Add([pscustomobject]@{
                round          = $round
                mode           = 'sequential'
                phoneExitCode  = $phoneSeq.ExitCode
                wearExitCode   = $wearSeq.ExitCode
                roundSeconds   = [math]::Round($seqWatch.Elapsed.TotalSeconds, 1)
                peakJavaProcs  = [math]::Max($peakA, $peakB)
                contention     = (Test-Contention -LogPath @($phoneSeqLog, $wearSeqLog))
            })
    }

    $rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8

    $concurrent = @($rows | Where-Object { $_.mode -eq 'concurrent' })
    $sequential = @($rows | Where-Object { $_.mode -eq 'sequential' })
    $failures = @($concurrent | Where-Object { $_.phoneExitCode -ne 0 -or $_.wearExitCode -ne 0 -or $_.contention }).Count
    $concurrentAvg = [math]::Round((($concurrent | Measure-Object -Property roundSeconds -Average).Average), 1)
    $sequentialAvg = [math]::Round((($sequential | Measure-Object -Property roundSeconds -Average).Average), 1)
    $peakOverall = ($rows | Measure-Object -Property peakJavaProcs -Maximum).Maximum

    Write-Host ""
    Write-Host "measure-concurrent-build: $failures of $($concurrent.Count) concurrent round(s) failed or hit lock contention." -ForegroundColor Yellow
    Write-Host "  concurrent avg: ${concurrentAvg}s | sequential avg: ${sequentialAvg}s | peak java processes: $peakOverall"
    Write-Host "  csv: $csvPath"
}
finally {
    Exit-AgentLock -Name Build
}

exit 0
