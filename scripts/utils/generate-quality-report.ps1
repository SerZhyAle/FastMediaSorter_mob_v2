<#
.SYNOPSIS
    Generates a quality metrics report for FastMediaSorter.
.DESCRIPTION
    Collects CI-measurable KPIs:
    - Unit test pass rate (from JUnit XML reports)
    - Lint warnings count (from lint report XML)
    - APK size (from build output)
    Compares results against thresholds from QUALITY_BASELINE.md.
    Outputs a Markdown report to dev/QUALITY_REPORT_<date>.md (or stdout).
.PARAMETER OutputFile
    Path for the generated report. Defaults to dev/QUALITY_REPORT_<YYYY-MM>.md
.PARAMETER Flavor
    Build flavor to report on. Default: standard
.EXAMPLE
    .\scripts\utils\generate-quality-report.ps1
    .\scripts\utils\generate-quality-report.ps1 -OutputFile temp/report.md -Flavor lite
#>
[CmdletBinding()]
param(
    [string]$OutputFile = "",
    [string]$Flavor = "standard"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Date        = Get-Date -Format "yyyy-MM-dd"
$Month       = Get-Date -Format "yyyy-MM"

if (-not $OutputFile) {
    $OutputFile = Join-Path $ProjectRoot "dev\QUALITY_REPORT_$Month.md"
}

# ── Baseline thresholds (sourced from dev/QUALITY_BASELINE.md) ────────────────
# Update these when QUALITY_BASELINE.md changes.
$Baseline = @{
    UnitTestTotal      = 143          # baseline count — warn if less
    UnitTestPassRate   = 1.0          # 100% required
    LintErrors         = 0            # alert threshold: > 0
    LintWarningsAlert  = 0            # alert threshold: > 0 new warnings
    ApkSizeMbTarget    = 250.0        # target
    ApkSizeMbAlert     = 280.0        # alert threshold: > 280 MB
}

# ── Helpers ───────────────────────────────────────────────────────────────────

function Get-UnitTestStats {
    $testReportDir = Join-Path $ProjectRoot "app_v2\build\test-results"
    if (-not (Test-Path $testReportDir)) {
        return @{ Total = 0; Passed = 0; Failed = 0; Skipped = 0; Found = $false }
    }

    $xmlFiles = Get-ChildItem -Path $testReportDir -Filter "*.xml" -Recurse
    $total = 0; $failed = 0; $skipped = 0

    foreach ($f in $xmlFiles) {
        try {
            [xml]$xml = Get-Content $f.FullName
            foreach ($suite in $xml.SelectNodes("//testsuite")) {
                $total   += [int]($suite.tests   -replace '[^0-9]','')  
                $failed  += [int]($suite.failures -replace '[^0-9]','')
                $failed  += [int]($suite.errors   -replace '[^0-9]','')
                $skipped += [int]($suite.skipped  -replace '[^0-9]','')
            }
        } catch { <# skip malformed XML #> }
    }

    $passed = $total - $failed - $skipped
    @{ Total = $total; Passed = $passed; Failed = $failed; Skipped = $skipped; Found = ($xmlFiles.Count -gt 0) }
}

function Get-LintStats {
    param([string]$FlavorName)
    $capitalize = $FlavorName.Substring(0,1).ToUpper() + $FlavorName.Substring(1)
    $lintXml = Join-Path $ProjectRoot "app_v2\build\reports\lint-results-${capitalize}Debug.xml"

    if (-not (Test-Path $lintXml)) {
        return @{ Errors = -1; Warnings = -1; Found = $false }
    }

    try {
        [xml]$xml = Get-Content $lintXml
        $issues   = $xml.SelectNodes("//issue")
        $errors   = ($issues | Where-Object { $_.severity -eq "Error" }).Count
        $warnings = ($issues | Where-Object { $_.severity -eq "Warning" }).Count
        @{ Errors = $errors; Warnings = $warnings; Found = $true }
    } catch {
        @{ Errors = -1; Warnings = -1; Found = $false }
    }
}

function Get-ApkSize {
    param([string]$FlavorName)
    $apkDir = Join-Path $ProjectRoot "app_v2\build\outputs\apk\${FlavorName}\debug"
    if (-not (Test-Path $apkDir)) { return "N/A" }
    $apk = Get-ChildItem -Path $apkDir -Filter "*.apk" | Select-Object -First 1
    if (-not $apk) { return "N/A" }
    $sizeMb = [math]::Round($apk.Length / 1MB, 1)
    return "${sizeMb} MB"
}

function Get-GitInfo {
    try {
        $branch = & git -C $ProjectRoot rev-parse --abbrev-ref HEAD 2>$null
        $commit = & git -C $ProjectRoot rev-parse --short HEAD 2>$null
        $count  = & git -C $ProjectRoot rev-list --count HEAD 2>$null
        @{ Branch = $branch; Commit = $commit; CommitCount = $count }
    } catch {
        @{ Branch = "unknown"; Commit = "unknown"; CommitCount = "?" }
    }
}

# ── Baseline comparison ───────────────────────────────────────────────────────

function Get-BaselineComparison {
    param($Tests, $Lint, [string]$ApkRaw)

    $results = [System.Collections.Generic.List[hashtable]]::new()

    # --- Unit tests ---
    if ($Tests.Found) {
        $passRate = if ($Tests.Total -gt 0) { $Tests.Passed / $Tests.Total } else { 0 }
        $totalOk  = $Tests.Total -ge $Baseline.UnitTestTotal

        $rateStatus = if ($passRate -lt $Baseline.UnitTestPassRate) { "❌ FAIL" }
                      elseif (-not $totalOk) { "⚠️ WARN" }
                      else { "✅ PASS" }

        $results.Add(@{
            Metric   = "Unit test pass rate"
            Current  = ("{0:P1} ({1}/{2})" -f $passRate, $Tests.Passed, $Tests.Total)
            Baseline = "100% (≥$($Baseline.UnitTestTotal) tests)"
            Status   = $rateStatus
        })
    } else {
        $results.Add(@{ Metric = "Unit test pass rate"; Current = "NOT RUN"; Baseline = "100%"; Status = "⚠️ NOT RUN" })
    }

    # --- Lint errors ---
    if ($Lint.Found) {
        $errStatus  = if ($Lint.Errors -gt $Baseline.LintErrors)    { "❌ FAIL" } else { "✅ PASS" }
        $warnStatus = if ($Lint.Warnings -gt $Baseline.LintWarningsAlert) { "⚠️ WARN" } else { "✅ PASS" }

        $results.Add(@{
            Metric   = "Lint errors"
            Current  = "$($Lint.Errors)"
            Baseline = "0"
            Status   = $errStatus
        })
        $results.Add(@{
            Metric   = "Lint warnings"
            Current  = "$($Lint.Warnings)"
            Baseline = "0 new"
            Status   = $warnStatus
        })
    } else {
        $results.Add(@{ Metric = "Lint"; Current = "NOT RUN"; Baseline = "0 errors"; Status = "⚠️ NOT RUN" })
    }

    # --- APK size ---
    if ($ApkRaw -ne "N/A") {
        $sizeMb = [double]($ApkRaw -replace ' MB','')
        $apkStatus = if    ($sizeMb -gt $Baseline.ApkSizeMbAlert)  { "❌ FAIL (> $($Baseline.ApkSizeMbAlert) MB)" }
                     elseif ($sizeMb -gt $Baseline.ApkSizeMbTarget) { "⚠️ WARN (> $($Baseline.ApkSizeMbTarget) MB)" }
                     else                                            { "✅ PASS" }
        $results.Add(@{
            Metric   = "APK size (debug)"
            Current  = $ApkRaw
            Baseline = "≤ $($Baseline.ApkSizeMbTarget) MB (alert: $($Baseline.ApkSizeMbAlert) MB)"
            Status   = $apkStatus
        })
    } else {
        $results.Add(@{ Metric = "APK size (debug)"; Current = "N/A"; Baseline = "≤ $($Baseline.ApkSizeMbTarget) MB"; Status = "⚠️ NOT FOUND" })
    }

    return $results
}

# ── Collect metrics ───────────────────────────────────────────────────────────

Write-Host "Collecting quality metrics for flavor: $Flavor..."

$tests      = Get-UnitTestStats
$lint       = Get-LintStats -FlavorName $Flavor
$apk        = Get-ApkSize   -FlavorName $Flavor
$git        = Get-GitInfo
$comparison = Get-BaselineComparison -Tests $tests -Lint $lint -ApkRaw $apk

# ── Format report ─────────────────────────────────────────────────────────────

$testPassRate = if ($tests.Total -gt 0) { "{0:P1}" -f ($tests.Passed / $tests.Total) } else { "N/A" }
$testStatus   = if ($tests.Found -and $tests.Failed -eq 0) { "✅ PASS" } elseif ($tests.Failed -gt 0) { "❌ FAIL" } else { "⚠️ NOT RUN" }
$lintStatus   = if ($lint.Found -and $lint.Errors -eq 0) { "✅ CLEAN" } elseif (-not $lint.Found) { "⚠️ NOT RUN" } else { "❌ ERRORS" }

# Build baseline comparison table rows
$cmpRows = ($comparison | ForEach-Object {
    "| $($_['Metric']) | $($_['Current']) | $($_['Baseline']) | $($_['Status']) |"
}) -join "`n"

# Overall gate status
$gatePass = -not ($comparison | Where-Object { $_['Status'] -like "*FAIL*" })
$gateStatus = if ($gatePass) { "✅ ALL GATES PASS" } else { "❌ GATE FAILURE — review table below" }

$report = @"
# Quality Report — FastMediaSorter v2

**Date**: $Date  
**Flavor**: $Flavor  
**Branch**: $($git.Branch) @ $($git.Commit)  
**Total commits**: $($git.CommitCount)

---

## Gate Status: $gateStatus

| Metric | Current | Baseline / Threshold | Status |
|--------|---------|----------------------|--------|
$cmpRows

---

## CI / Build Metrics

### Unit Tests $testStatus

| Metric | Value |
|--------|-------|
| Total tests | $($tests.Total) |
| Passed | $($tests.Passed) |
| Failed | $($tests.Failed) |
| Skipped | $($tests.Skipped) |
| Pass rate | $testPassRate |

### Lint $lintStatus

| Metric | Value |
|--------|-------|
| Errors | $(if ($lint.Found) { $lint.Errors } else { "N/A" }) |
| Warnings | $(if ($lint.Found) { $lint.Warnings } else { "N/A" }) |

### Build Artifacts

| Artifact | Value |
|----------|-------|
| APK size (${Flavor} debug) | $apk |

---

## Runtime KPIs

*Requires production Crashlytics data — see ``QUALITY_BASELINE.md`` for thresholds.*

| KPI | Value | Target | Status |
|-----|-------|--------|--------|
| Crash-free rate | TBD | ≥ 99.0% | ⏳ |
| ANR rate | TBD | ≥ 99.5% | ⏳ |
| App startup time | TBD | ≤ 2000ms | ⏳ |
| Median scan time | TBD | ≤ 3000ms | ⏳ |
| Auth success rate | TBD | ≥ 98.0% | ⏳ |
| Resource save success | TBD | ≥ 99.5% | ⏳ |

---

*Generated by ``scripts/utils/generate-quality-report.ps1`` on $Date*
"@

# ── Output ────────────────────────────────────────────────────────────────────

$report | Set-Content -Path $OutputFile -Encoding UTF8
Write-Host "Quality report written to: $OutputFile"

# Print summary to console
Write-Host ""
Write-Host "=== SUMMARY ==="
Write-Host "Gate       : $gateStatus"
Write-Host "Unit tests : $testStatus ($($tests.Passed)/$($tests.Total))"
Write-Host "Lint       : $lintStatus"
Write-Host "APK size   : $apk"
Write-Host "Branch     : $($git.Branch) @ $($git.Commit)"
