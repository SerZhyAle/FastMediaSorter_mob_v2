# Typo + Lint + Activity Checks Runner
# Usage:
#   .\scripts\utils\check-typo-lint.ps1
#   .\scripts\utils\check-typo-lint.ps1 -SkipTypo
#   .\scripts\utils\check-typo-lint.ps1 -SkipLint -FailOnActivityWarnings

param(
    [switch]$SkipTypo,
    [switch]$SkipLint,
    [switch]$SkipActivityChecks,
    [switch]$FailOnActivityWarnings,
    [ValidateSet("auto", "typos", "cspell")]
    [string]$TypoTool = "auto",
    [string]$LintTask = "lintStandardDebug"
)

$ErrorActionPreference = "Stop"

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$utilsPath = $scriptPath
$scriptsPath = Split-Path -Parent $utilsPath
$projectRoot = Split-Path -Parent $scriptsPath
$tempDir = Join-Path $projectRoot "temp"
. (Join-Path $projectRoot "scripts/utils/agent-lock.ps1")

if (-not (Test-Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $tempDir "lint-typo-activity-$timestamp.log"

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host " $Title" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}

function Write-Report {
    param([string]$Line)
    Add-Content -Path $reportPath -Value $Line
}

function Resolve-TypoTool {
    param([string]$Requested)

    if ($Requested -eq "typos" -or $Requested -eq "cspell") {
        return $Requested
    }

    if (Get-Command typos -ErrorAction SilentlyContinue) {
        return "typos"
    }

    if (Get-Command cspell -ErrorAction SilentlyContinue) {
        return "cspell"
    }

    return "none"
}

$typoExitCode = 0
$lintExitCode = 0
$activityWarnings = 0

Write-Report "FastMediaSorter v2 - Typo/Lint/Activity report"
Write-Report "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Report "Project root: $projectRoot"

Push-Location $projectRoot
try {
    if (-not $SkipTypo) {
        Write-Section "TYPO CHECK"
        $resolvedTool = Resolve-TypoTool -Requested $TypoTool
        Write-Report "Typo tool: $resolvedTool"

        switch ($resolvedTool) {
            "typos" {
                Write-Host "Running typos..." -ForegroundColor Yellow
                & typos app_v2 scripts docs
                $typoExitCode = $LASTEXITCODE
            }
            "cspell" {
                Write-Host "Running cspell..." -ForegroundColor Yellow
                & cspell "app_v2/src/main/**/*.{kt,kts,xml,md}" "scripts/**/*.{ps1,md}" "docs/**/*.md"
                $typoExitCode = $LASTEXITCODE
            }
            default {
                Write-Host "No typo tool found (typos/cspell). Skipping typo check." -ForegroundColor DarkYellow
                Write-Report "Typo check skipped: no typos/cspell command found"
                $typoExitCode = 0
            }
        }

        if ($typoExitCode -ne 0) {
            Write-Host "Typo check failed with exit code: $typoExitCode" -ForegroundColor Red
            Write-Report "Typo check: FAILED ($typoExitCode)"
        }
        else {
            Write-Host "Typo check completed." -ForegroundColor Green
            Write-Report "Typo check: OK"
        }
    }

    if (-not $SkipLint) {
        Write-Section "GRADLE LINT"
        Write-Host "Running .\\gradlew.bat $LintTask ..." -ForegroundColor Yellow
        Enter-BuildLockOrExit -Reason "check-typo-lint.ps1 ($LintTask)"
        try {
            & .\gradlew.bat $LintTask
            $lintExitCode = $LASTEXITCODE
        }
        finally {
            Exit-AgentLock -Name Build
        }

        if ($lintExitCode -ne 0) {
            Write-Host "Gradle lint failed with exit code: $lintExitCode" -ForegroundColor Red
            Write-Report "Gradle lint: FAILED ($lintExitCode)"
        }
        else {
            Write-Host "Gradle lint completed." -ForegroundColor Green
            Write-Report "Gradle lint: OK"
        }
    }

    if (-not $SkipActivityChecks) {
        Write-Section "ACTIVITY CHECKS"

        $activityFiles = Get-ChildItem -Path "app_v2/src/main/java" -Recurse -Filter "*Activity.kt"

        if (-not $activityFiles -or $activityFiles.Count -eq 0) {
            Write-Host "No Activity files found." -ForegroundColor DarkYellow
            Write-Report "Activity checks: no files found"
        }
        else {
            Write-Host "Found $($activityFiles.Count) Activity files" -ForegroundColor Yellow
            Write-Report "Activity files count: $($activityFiles.Count)"

            foreach ($file in $activityFiles) {
                $relativePath = $file.FullName.Replace($projectRoot + "\\", "")
                $lines = Get-Content -Path $file.FullName
                $lineCount = $lines.Count

                $fileWarnings = @()

                if ($lineCount -gt 1500) {
                    $fileWarnings += "File exceeds 1500 lines ($lineCount)"
                }

                $lineNumber = 0
                foreach ($line in $lines) {
                    $lineNumber++
                    if ($line -match "\bLog\.d\(") {
                        $fileWarnings += "Line ${lineNumber}: forbidden Log.d()"
                    }
                    if ($line -match "\bprintln\(") {
                        $fileWarnings += "Line ${lineNumber}: println() found"
                    }
                }

                if ($fileWarnings.Count -gt 0) {
                    $activityWarnings += $fileWarnings.Count
                    Write-Host "WARN: $relativePath" -ForegroundColor DarkYellow
                    Write-Report "WARN: $relativePath"
                    foreach ($warning in $fileWarnings) {
                        Write-Host "  - $warning" -ForegroundColor DarkYellow
                        Write-Report "  - $warning"
                    }
                }
                else {
                    Write-Report "OK: $relativePath"
                }
            }

            Write-Host "Activity checks warnings: $activityWarnings" -ForegroundColor Yellow
            Write-Report "Activity checks warnings total: $activityWarnings"
        }
    }

    if (-not $SkipActivityChecks) {
        Write-Section "GLOBAL CODE QUALITY CHECKS"
        
        $excludePaths = @("build", "temp", "V1", "v2_6", "spec_v2", "dev", ".git", ".gradle", ".idea")
        $allCodeFiles = Get-ChildItem -Path "." -Recurse -Include "*.kt", "*.java" | Where-Object {
            $path = $_.FullName.Replace($projectRoot, "")
            $isExcluded = $false
            foreach ($ex in $excludePaths) {
                if ($path -match "\\$ex\\") {
                    $isExcluded = $true
                    break
                }
            }
            -not $isExcluded
        }

        Write-Host "Scanning $($allCodeFiles.Count) code files for forbidden patterns..." -ForegroundColor Yellow
        Write-Report "Global code scan: $($allCodeFiles.Count) files"

        $globalWarnings = 0
        foreach ($file in $allCodeFiles) {
            $relativePath = $file.FullName.Replace($projectRoot + "\\", "")
            $lines = Get-Content -Path $file.FullName
            
            $fileWarnings = @()
            $lineNumber = 0
            foreach ($line in $lines) {
                $lineNumber++
                # Check for Log.d/v/i/e/w
                if ($line -match "\bLog\.(d|v|i|e|w)\(") {
                    $fileWarnings += "Line ${lineNumber}: forbidden $($matches[0]) (use Timber)"
                }
                # Check for println()
                if ($line -match "\bprintln\(") {
                    $fileWarnings += "Line ${lineNumber}: forbidden println()"
                }
            }

            if ($fileWarnings.Count -gt 0) {
                $globalWarnings += $fileWarnings.Count
                Write-Host "WARN: $relativePath" -ForegroundColor DarkYellow
                Write-Report "WARN: $relativePath"
                foreach ($warning in $fileWarnings) {
                    Write-Host "  - $warning" -ForegroundColor DarkYellow
                    Write-Report "  - $warning"
                }
            }
        }
        
        $activityWarnings += $globalWarnings
        Write-Host "Global code warnings: $globalWarnings" -ForegroundColor Yellow
        Write-Report "Global code warnings total: $globalWarnings"
    }
}
finally {
    Pop-Location
}

Write-Section "SUMMARY"
Write-Host "Report: $reportPath" -ForegroundColor DarkGray
Write-Host "Typo exit code: $typoExitCode" -ForegroundColor Gray
Write-Host "Lint exit code: $lintExitCode" -ForegroundColor Gray
Write-Host "Code quality warnings (Total): $activityWarnings" -ForegroundColor Gray

$exitCode = 0

if ($typoExitCode -ne 0) {
    $exitCode = $typoExitCode
}

if ($lintExitCode -ne 0) {
    $exitCode = $lintExitCode
}

if ($FailOnActivityWarnings -and $activityWarnings -gt 0) {
    Write-Host "Failing due to Code quality warnings (-FailOnActivityWarnings)." -ForegroundColor Red
    $exitCode = 2
}

if ($exitCode -eq 0) {
    Write-Host "Checks completed successfully." -ForegroundColor Green
}
else {
    Write-Host "Checks finished with errors. Exit code: $exitCode" -ForegroundColor Red
}

exit $exitCode
