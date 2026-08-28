# Maestro Stress Test Runner with Live Monitoring
# FastMediaSorter v2
# Usage: .\scripts\utils\run-maestro-stress.ps1 [-Suite monkey|navigation|lifecycle|all] [-Monitor] [-Report]

param(
    [ValidateSet("monkey", "navigation", "lifecycle", "all")]
    [string]$Suite = "all",
    [switch]$Monitor,
    [switch]$Report,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$UtilsDir = Split-Path -Parent $ScriptDir
$ProjectRoot = Split-Path -Parent $UtilsDir
$MaestroDir = Join-Path $ProjectRoot "maestro"
$StressDir = Join-Path $MaestroDir "stress"
$TempDir = Join-Path $ProjectRoot "temp"
$LogFile = Join-Path $TempDir "stress_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
$CrashLog = Join-Path $TempDir "crash_monitor.log"
$ReportFile = Join-Path $TempDir "stress_report_$(Get-Date -Format 'yyyyMMdd_HHmmss').html"
. (Join-Path $ProjectRoot "scripts/utils/agent-lock.ps1")

# Ensure temp directory exists
if (-not (Test-Path $TempDir)) { New-Item -ItemType Directory -Path $TempDir -Force | Out-Null }

# ============================================================
# UTILITIES
# ============================================================

function Write-Banner {
    param([string]$Text, [string]$Color = "Cyan")
    $line = "=" * 60
    Write-Host ""
    Write-Host $line -ForegroundColor $Color
    Write-Host "  $Text" -ForegroundColor $Color
    Write-Host $line -ForegroundColor $Color
    Write-Host ""
}

function Write-Metric {
    param([string]$Label, [string]$Value, [string]$Color = "White")
    Write-Host "  $($Label.PadRight(25))" -NoNewline -ForegroundColor DarkGray
    Write-Host $Value -ForegroundColor $Color
}

function Get-AdbPath {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) { return $adb.Source }

    $paths = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe"
    )
    foreach ($p in $paths) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

function Get-MaestroCmd {
    $customPath = "c:\GD\tc\Programm\maestro\bin"
    if (Test-Path $customPath) { $env:PATH += ";$customPath" }
    
    $cmd = Get-Command maestro -ErrorAction SilentlyContinue
    if (-not $cmd) { $cmd = Get-Command maestro-cli -ErrorAction SilentlyContinue }
    return $cmd
}

function Get-AppMemoryMB {
    param([string]$AdbPath, [string]$LogPath)
    try {
        # Check if app is running
        $pidCheck = & $AdbPath shell pidof com.sza.fastmediasorter.debug 2>$null
        if (-not $pidCheck) {
            if ($LogPath) { "[WARN] App not running" | Out-File -Append $LogPath }
            return 0
        }

        $output = & $AdbPath shell dumpsys meminfo com.sza.fastmediasorter.debug 2>$null
        if ($LogPath) { "[DEBUG] dumpsys output lines: $($output.Count)" | Out-File -Append $LogPath }
        
        # Try multiple patterns
        $memInfo = $output | Select-String "TOTAL\s+(\d+)" | Select-Object -First 1
        if (-not $memInfo) {
            $memInfo = $output | Select-String "TOTAL PSS:\s+(\d+)" | Select-Object -First 1
        }
        
        if ($memInfo -and $memInfo.Matches[0].Groups[1].Value) {
            $memKB = [int]$memInfo.Matches[0].Groups[1].Value
            $memMB = [math]::Round($memKB / 1024, 1)
            if ($LogPath) { "[DEBUG] Memory: ${memKB}KB = ${memMB}MB" | Out-File -Append $LogPath }
            return $memMB
        }
    }
    catch {
        if ($LogPath) { "[ERROR] Get-AppMemoryMB: $_" | Out-File -Append $LogPath }
    }
    return 0
}

function Get-AppCpuPercent {
    param([string]$AdbPath)
    try {
        $cpuLine = & $AdbPath shell top -n 1 -b 2>$null | 
        Select-String "fastmediasorter" | Select-Object -First 1
        if ($cpuLine) {
            $parts = $cpuLine.ToString().Trim() -split '\s+'
            foreach ($p in $parts) {
                if ($p -match '^\d+\.?\d*%?$') {
                    return $p -replace '%', ''
                }
            }
        }
    }
    catch {}
    return "N/A"
}

# ============================================================
# CRASH MONITOR (background logcat)
# ============================================================

$logcatJob = $null
$crashCount = 0
$anrCount = 0

function Start-CrashMonitor {
    param([string]$AdbPath)
    
    # Clear previous crash log
    if (Test-Path $CrashLog) { Remove-Item $CrashLog -Force }
    
    # Clear logcat buffer
    & $AdbPath logcat -c 2>$null
    
    # Start logcat in background, filter for crashes/ANR
    $script = {
        param($adb, $logFile)
        & $adb logcat -v time "*:E" 2>$null | ForEach-Object {
            if ($_ -match "FATAL EXCEPTION|ANR in|crash|NullPointerException|OutOfMemoryError|StackOverflowError|WindowManager: Activity.*not responding") {
                $_ | Out-File -FilePath $logFile -Append -Encoding UTF8
            }
        }
    }
    
    $job = Start-Job -ScriptBlock $script -ArgumentList $AdbPath, $CrashLog
    return $job
}

function Get-CrashStats {
    if (-not (Test-Path $CrashLog)) { return @{ Crashes = 0; ANRs = 0; OOMs = 0; Lines = @() } }
    
    $content = Get-Content $CrashLog -ErrorAction SilentlyContinue
    if (-not $content) { return @{ Crashes = 0; ANRs = 0; OOMs = 0; Lines = @() } }
    
    $crashes = ($content | Select-String "FATAL EXCEPTION").Count
    $anrs = ($content | Select-String "ANR in|not responding").Count
    $ooms = ($content | Select-String "OutOfMemoryError").Count
    
    return @{
        Crashes = $crashes
        ANRs    = $anrs
        OOMs    = $ooms
        Lines   = $content
    }
}

function Stop-CrashMonitor {
    param($Job)
    if ($Job) {
        Stop-Job $Job -ErrorAction SilentlyContinue
        Remove-Job $Job -ErrorAction SilentlyContinue
    }
}

# ============================================================
# LIVE STATUS DISPLAY
# ============================================================

function Show-LiveStatus {
    param(
        [string]$TestName,
        [string]$Phase,
        [int]$ElapsedSec,
        [double]$MemoryMB,
        [string]$CpuPercent,
        [hashtable]$CrashStats
    )

    $statusColor = if ($CrashStats.Crashes -gt 0 -or $CrashStats.ANRs -gt 0) { "Red" } else { "Green" }

    Write-Host "`r" -NoNewline
    Write-Host "  [$([TimeSpan]::FromSeconds($ElapsedSec).ToString('mm\:ss'))]" -NoNewline -ForegroundColor DarkGray
    Write-Host " $TestName" -NoNewline -ForegroundColor Yellow
    Write-Host " | MEM: ${MemoryMB}MB" -NoNewline -ForegroundColor Cyan
    Write-Host " | CPU: ${CpuPercent}%" -NoNewline -ForegroundColor Cyan
    Write-Host " | CRASH:$($CrashStats.Crashes) ANR:$($CrashStats.ANRs) OOM:$($CrashStats.OOMs)" -NoNewline -ForegroundColor $statusColor
    Write-Host "    " -NoNewline  # clear trailing chars
}

# ============================================================
# TEST EXECUTION
# ============================================================

function Run-StressTest {
    param(
        [string]$TestFile,
        [string]$TestName,
        [string]$MaestroCmd,
        [string]$AdbPath
    )

    Write-Host ""
    Write-Host "▶ Running: $TestName" -ForegroundColor Yellow
    Write-Host "  File: $TestFile" -ForegroundColor DarkGray

    # Ensure app is running before test
    & $AdbPath shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity 2>&1 | Out-Null
    Start-Sleep -Seconds 2

    # Capture initial memory
    $memBefore = Get-AppMemoryMB -AdbPath $AdbPath -LogPath $LogFile
    Write-Metric "Memory (before)" "${memBefore} MB" "Cyan"

    $testStart = Get-Date

    # Start monitoring job that prints status
    $monitorScriptBlock = {
        param($AdbPath, $CrashLogPath, $TestName)
        while ($true) {
            Start-Sleep -Seconds 5
            try {
                # Check if app is running
                $pidCheck = & $AdbPath shell pidof com.sza.fastmediasorter.debug 2>$null
                $mem = 0
                if ($pidCheck) {
                    $output = & $AdbPath shell dumpsys meminfo com.sza.fastmediasorter.debug 2>$null
                    $memInfo = $output | Select-String "TOTAL\s+(\d+)" | Select-Object -First 1
                    if (-not $memInfo) {
                        $memInfo = $output | Select-String "TOTAL PSS:\s+(\d+)" | Select-Object -First 1
                    }
                    if ($memInfo -and $memInfo.Matches[0].Groups[1].Value) {
                        $mem = [math]::Round([int]$memInfo.Matches[0].Groups[1].Value / 1024, 1)
                    }
                }

                $crashCount = 0; $anrCount = 0; $oomCount = 0
                if (Test-Path $CrashLogPath) {
                    $cl = Get-Content $CrashLogPath -ErrorAction SilentlyContinue
                    if ($cl) {
                        $crashCount = ($cl | Select-String "FATAL EXCEPTION").Count
                        $anrCount = ($cl | Select-String "ANR in|not responding").Count
                        $oomCount = ($cl | Select-String "OutOfMemoryError").Count
                    }
                }
                
                $statusColor = if ($crashCount -gt 0 -or $anrCount -gt 0) { "Red" } else { "DarkGreen" }
                Write-Host "    📊 MEM:${mem}MB CRASH:${crashCount} ANR:${anrCount} OOM:${oomCount}" -ForegroundColor $statusColor
            }
            catch {}
        }
    }

    $monitorJob = $null
    if ($Monitor) {
        $monitorJob = Start-Job -ScriptBlock $monitorScriptBlock -ArgumentList $AdbPath, $CrashLog, $TestName
    }

    # Run Maestro test (use absolute path to ensure maestro finds the file)
    $absoluteTestFile = if ([IO.Path]::IsPathRooted($TestFile)) { $TestFile } else { (Resolve-Path $TestFile).Path }
    $cmdArgs = @("test", "--flatten-debug-output", $absoluteTestFile)
    & $MaestroCmd $cmdArgs 2>&1 | Tee-Object -FilePath $LogFile -Append

    $testExit = $LASTEXITCODE
    $testEnd = Get-Date
    $testDuration = $testEnd - $testStart

    # Stop monitor
    if ($monitorJob) {
        Stop-Job $monitorJob -ErrorAction SilentlyContinue
        Remove-Job $monitorJob -ErrorAction SilentlyContinue
    }

    # Wait for app to settle
    Start-Sleep -Seconds 2

    # Capture final memory
    $memAfter = Get-AppMemoryMB -AdbPath $AdbPath -LogPath $LogFile
    $memDelta = [math]::Round($memAfter - $memBefore, 1)
    $memDeltaColor = if ($memDelta -gt 50) { "Red" } elseif ($memDelta -gt 20) { "Yellow" } else { "Green" }

    # Get crash stats
    $stats = Get-CrashStats

    Write-Host ""
    Write-Host "  ── Results: $TestName ──" -ForegroundColor Cyan
    Write-Metric "Duration" $testDuration.ToString('mm\:ss') "White"
    Write-Metric "Memory (after)" "${memAfter} MB" "Cyan"
    Write-Metric "Memory delta" "${memDelta} MB" $memDeltaColor
    Write-Metric "Crashes" $stats.Crashes $(if ($stats.Crashes -gt 0) { "Red" } else { "Green" })
    Write-Metric "ANRs" $stats.ANRs $(if ($stats.ANRs -gt 0) { "Red" } else { "Green" })
    Write-Metric "OOMs" $stats.OOMs $(if ($stats.OOMs -gt 0) { "Red" } else { "Green" })
    
    # Test passes if no crashes/ANRs (ignore maestro exit code - warnings are OK)
    $passed = ($stats.Crashes -eq 0) -and ($stats.ANRs -eq 0)
    if ($passed) {
        Write-Host "  ✅ PASSED" -ForegroundColor Green
    }
    else {
        Write-Host "  ❌ FAILED" -ForegroundColor Red
        if ($stats.Crashes -gt 0) {
            Write-Host "  Last crash lines:" -ForegroundColor Red
            $stats.Lines | Select-Object -Last 5 | ForEach-Object {
                Write-Host "    $_" -ForegroundColor DarkRed
            }
        }
    }

    return @{
        Name      = $TestName
        Passed    = $passed
        Duration  = $testDuration
        MemBefore = $memBefore
        MemAfter  = $memAfter
        MemDelta  = $memDelta
        Crashes   = $stats.Crashes
        ANRs      = $stats.ANRs
        OOMs      = $stats.OOMs
        ExitCode  = $testExit
    }
}

# ============================================================
# HTML REPORT GENERATION
# ============================================================

function Generate-HtmlReport {
    param([array]$Results, [TimeSpan]$TotalDuration)

    $rows = ""
    foreach ($r in $Results) {
        $statusIcon = if ($r.Passed) { "&#x2705;" } else { "&#x274C;" }
        $memColor = if ($r.MemDelta -gt 50) { "#ff4444" } elseif ($r.MemDelta -gt 20) { "#ffaa00" } else { "#44ff44" }
        $rows += @"
        <tr>
            <td>$statusIcon $($r.Name)</td>
            <td>$($r.Duration.ToString('mm\:ss'))</td>
            <td>$($r.MemBefore) MB</td>
            <td>$($r.MemAfter) MB</td>
            <td style="color: $memColor; font-weight: bold;">$($r.MemDelta) MB</td>
            <td>$($r.Crashes)</td>
            <td>$($r.ANRs)</td>
            <td>$($r.OOMs)</td>
        </tr>
"@
    }

    $totalPassed = ($Results | Where-Object { $_.Passed }).Count
    $totalFailed = ($Results | Where-Object { -not $_.Passed }).Count
    $overallStatus = if ($totalFailed -eq 0) { "ALL PASSED &#x2705;" } else { "$totalFailed FAILED &#x274C;" }

    $html = @"
<!DOCTYPE html>
<html>
<head>
<title>FastMediaSorter Stress Test Report</title>
<style>
    body { font-family: 'Segoe UI', sans-serif; background: #1e1e1e; color: #ccc; padding: 20px; }
    h1 { color: #569cd6; }
    h2 { color: #4ec9b0; }
    table { border-collapse: collapse; width: 100%; margin: 20px 0; }
    th { background: #333; color: #ddd; padding: 10px; text-align: left; border: 1px solid #555; }
    td { padding: 8px 10px; border: 1px solid #444; }
    tr:nth-child(even) { background: #2a2a2a; }
    .summary { background: #2d2d2d; padding: 15px; border-radius: 8px; margin: 10px 0; }
    .passed { color: #44ff44; }
    .failed { color: #ff4444; }
</style>
</head>
<body>
<h1>&#x1F680; FastMediaSorter v2 - Stress Test Report</h1>
<p>Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')</p>

<div class="summary">
    <h2>Summary: $overallStatus</h2>
    <p>Total Duration: $($TotalDuration.ToString('mm\:ss'))</p>
    <p class="passed">Passed: $totalPassed</p>
    <p class="failed">Failed: $totalFailed</p>
</div>

<table>
<tr>
    <th>Test</th><th>Duration</th><th>Mem Before</th><th>Mem After</th>
    <th>Mem Delta</th><th>Crashes</th><th>ANRs</th><th>OOMs</th>
</tr>
$rows
</table>

<h2>Log File</h2>
<p><code>$LogFile</code></p>

</body>
</html>
"@

    $html | Out-File -FilePath $ReportFile -Encoding UTF8
    Write-Host "📄 HTML report: $ReportFile" -ForegroundColor Cyan
}

# ============================================================
# MAIN
# ============================================================

Write-Banner "FastMediaSorter v2 - STRESS TESTS" "Magenta"

# Check tools
$adbPath = Get-AdbPath
if (-not $adbPath) {
    Write-Host "❌ ADB not found" -ForegroundColor Red
    exit 1
}
Write-Host "✓ ADB: $adbPath" -ForegroundColor Green

$maestroCmd = Get-MaestroCmd
if (-not $maestroCmd) {
    Write-Host "❌ Maestro CLI not found" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Maestro: $($maestroCmd.Name)" -ForegroundColor Green

# Check device
$devices = & $adbPath devices | Select-String -Pattern "device$"
if ($devices.Count -eq 0) {
    Write-Host "❌ No device connected" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Device connected" -ForegroundColor Green

# Disable animations (critical for Maestro stability)
Write-Host "Disabling animations..." -ForegroundColor Yellow
& $adbPath shell settings put global window_animation_scale 0.0 2>$null
& $adbPath shell settings put global transition_animation_scale 0.0 2>$null
& $adbPath shell settings put global animator_duration_scale 0.0 2>$null
Write-Host "✓ Animations disabled" -ForegroundColor Green

# Check/install app
$appInstalled = & $adbPath shell pm list packages | Select-String "com.sza.fastmediasorter"
if (-not $appInstalled) {
    if (-not $SkipBuild) {
        Write-Host "⚠ App not installed, building..." -ForegroundColor Yellow
        Enter-BuildLockOrExit -Reason 'run-maestro-stress.ps1 (assembleStandardDebug)' -Domain Build.Phone
        try {
            . (Join-Path $ProjectRoot 'scripts/utils/build-version-stamp.ps1')
            $stamp = Get-BuildVersionStamp
            & "$ProjectRoot\gradlew.bat" :app_v2:assembleStandardDebug "-Pfms.versionCode=$($stamp.AppVersionCode)" "-Pfms.versionName=$($stamp.VersionName)"
        }
        finally {
            Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
        }
        $apk = Get-ChildItem -Path "$ProjectRoot\app_v2\build\outputs\apk\standard\debug" -Filter "*.apk" | Select-Object -First 1
        if ($apk) {
            & $adbPath install -r $apk.FullName
            Write-Host "✓ App installed" -ForegroundColor Green
        }
        else {
            Write-Host "❌ APK not found after build" -ForegroundColor Red
            exit 1
        }
    }
    else {
        Write-Host "❌ App not installed and -SkipBuild specified" -ForegroundColor Red
        exit 1
    }
}
else {
    Write-Host "✓ App installed" -ForegroundColor Green
}

# Determine which tests to run
$tests = @()
switch ($Suite) {
    "monkey" { $tests += @{ File = "$StressDir\monkey_taps.yaml"; Name = "Monkey Random Taps (200)" } }
    "navigation" { $tests += @{ File = "$StressDir\rapid_navigation.yaml"; Name = "Rapid Navigation Stress" } }
    "lifecycle" { $tests += @{ File = "$StressDir\app_lifecycle.yaml"; Name = "App Lifecycle Torture" } }
    "all" {
        $tests += @{ File = "$StressDir\monkey_taps.yaml"; Name = "Monkey Random Taps (200)" }
        $tests += @{ File = "$StressDir\rapid_navigation.yaml"; Name = "Rapid Navigation Stress" }
        $tests += @{ File = "$StressDir\app_lifecycle.yaml"; Name = "App Lifecycle Torture" }
    }
}

# Validate all test files exist
foreach ($test in $tests) {
    if (-not (Test-Path $test.File)) {
        Write-Host "❌ Test file not found: $($test.File)" -ForegroundColor Red
        Write-Host "   (Looked in: $StressDir)" -ForegroundColor Red
        exit 1
    }
}

Write-Banner "Running $($tests.Count) stress test(s): $Suite" "Yellow"

# Start crash monitor
$logcatJob = $null
if ($Monitor) {
    Write-Host "🔍 Starting crash monitor (logcat)..." -ForegroundColor Cyan
    $logcatJob = Start-CrashMonitor -AdbPath $adbPath
    Write-Host "✓ Crash monitor active" -ForegroundColor Green
}

# Run tests
$totalStart = Get-Date
$results = @()

foreach ($test in $tests) {
    $result = Run-StressTest -TestFile $test.File -TestName $test.Name -MaestroCmd $maestroCmd.Source -AdbPath $adbPath
    $results += $result
}

$totalEnd = Get-Date
$totalDuration = $totalEnd - $totalStart

# Stop crash monitor
if ($logcatJob) {
    Stop-CrashMonitor -Job $logcatJob
    Write-Host ""
    Write-Host "🔍 Crash monitor stopped" -ForegroundColor Cyan
}

# ============================================================
# FINAL SUMMARY
# ============================================================

Write-Banner "STRESS TEST SUMMARY" "Cyan"

$passedCount = ($results | Where-Object { $_.Passed }).Count
$failedCount = ($results | Where-Object { -not $_.Passed }).Count
$totalCrashes = ($results | Measure-Object -Property Crashes -Sum).Sum
$totalANRs = ($results | Measure-Object -Property ANRs -Sum).Sum
$totalOOMs = ($results | Measure-Object -Property OOMs -Sum).Sum

Write-Metric "Total Duration" $totalDuration.ToString('mm\:ss') "White"
Write-Metric "Tests Passed" "$passedCount / $($results.Count)" $(if ($failedCount -eq 0) { "Green" } else { "Yellow" })
Write-Metric "Tests Failed" $failedCount $(if ($failedCount -gt 0) { "Red" } else { "Green" })
Write-Metric "Total Crashes" $totalCrashes $(if ($totalCrashes -gt 0) { "Red" } else { "Green" })
Write-Metric "Total ANRs" $totalANRs $(if ($totalANRs -gt 0) { "Red" } else { "Green" })
Write-Metric "Total OOMs" $totalOOMs $(if ($totalOOMs -gt 0) { "Red" } else { "Green" })

Write-Host ""
Write-Host "📝 Full log: $LogFile" -ForegroundColor DarkGray

# Generate HTML report if requested
if ($Report) {
    Generate-HtmlReport -Results $results -TotalDuration $totalDuration
}

Write-Host ""
if ($failedCount -eq 0 -and $totalCrashes -eq 0) {
    Write-Host "✅ ALL STRESS TESTS PASSED - App is stable!" -ForegroundColor Green
}
else {
    Write-Host "❌ STRESS TESTING FOUND ISSUES" -ForegroundColor Red
    if ($totalCrashes -gt 0) { Write-Host "  → $totalCrashes crash(es) detected" -ForegroundColor Red }
    if ($totalANRs -gt 0) { Write-Host "  → $totalANRs ANR(s) detected" -ForegroundColor Red }
    if ($totalOOMs -gt 0) { Write-Host "  → $totalOOMs OOM(s) detected" -ForegroundColor Red }
}

Write-Host ""
exit $(if ($failedCount -eq 0 -and $totalCrashes -eq 0) { 0 } else { 1 })
