<#
.SYNOPSIS
    Fails when a unit-test run covered fewer test classes than the source set contains.

.DESCRIPTION
    S1244: Gradle prints "<N> tests completed, <M> failed" whatever happened - including when the
    test worker JVM died half-way and most of the suite never ran. That summary is indistinguishable
    from a finished run, so a truncated run reads as a result. It cost this project a 3x undercount
    (946 of 2960 tests) that went unnoticed because the build still went red, just for the wrong reason.

    The check does not parse the log. It compares the test classes present in the source set against
    the JUnit XML reports the run actually wrote, and names the packages that produced none.

    Exact equality is deliberately NOT required: abstract bases, helper classes and @Ignore'd classes
    legitimately produce no report. A truncation loses whole packages, which is what this looks for.

.PARAMETER Module
    Gradle module directory holding src/test. Default app_v2.

.PARAMETER TaskDir
    Test-results directory name under <Module>/build/test-results. Default testStandardDebugUnitTest.

.PARAMETER MinCoverageRatio
    Minimum reports/classes ratio before the run is called truncated. Default 0.85.

.OUTPUTS
    Exit 0 - coverage looks complete.
    Exit 1 - the run is truncated (missing packages, or ratio below the floor).
    Exit 2 - cannot check (no reports directory, or no test sources found).
#>
param(
    [string]$Module = "app_v2",
    [string]$TaskDir = "testStandardDebugUnitTest",
    [double]$MinCoverageRatio = 0.85
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path "$PSScriptRoot\..\.."
$sourceRoot = Join-Path $repoRoot "$Module\src\test"
$reportRoot = Join-Path $repoRoot "$Module\build\test-results\$TaskDir"

if (-not (Test-Path $sourceRoot)) {
    Write-Error "assert-test-suite-complete: no test sources at $sourceRoot" -ErrorAction Continue
    exit 2
}
if (-not (Test-Path $reportRoot)) {
    $msg = "assert-test-suite-complete: no reports at $reportRoot - run the suite first. " +
    '"Could not check" is not "checked and found nothing".'
    Write-Error $msg -ErrorAction Continue
    exit 2
}

# Source classes: one per *Test.kt file, package taken from its path under src/test/java.
$sourceClasses = Get-ChildItem -Path $sourceRoot -Recurse -Filter "*Test.kt" -File
if ($sourceClasses.Count -eq 0) {
    Write-Error "assert-test-suite-complete: no *Test.kt under $sourceRoot" -ErrorAction Continue
    exit 2
}

function Get-PackageFromPath([string]$fullPath, [string]$root) {
    $relative = $fullPath.Substring($root.Length).TrimStart('\', '/')
    # Strip the src/test/<lang> prefix and the file name; the rest is the package path.
    $parts = $relative -split '[\\/]' | Where-Object { $_ }
    $parts = @($parts | Select-Object -Skip 1)          # java / kotlin
    if ($parts.Count -le 1) { return "" }
    return ($parts[0..($parts.Count - 2)] -join '.')
}

$sourcePackages = @{}
foreach ($file in $sourceClasses) {
    $pkg = Get-PackageFromPath -fullPath $file.FullName -root $sourceRoot
    if ($pkg) { $sourcePackages[$pkg] = $true }
}

$reports = Get-ChildItem -Path $reportRoot -Filter "TEST-*.xml" -File
$reportPackages = @{}
foreach ($report in $reports) {
    # TEST-<fqcn>.xml -> package is the fqcn minus the class name.
    $fqcn = $report.BaseName -replace '^TEST-', ''
    $lastDot = $fqcn.LastIndexOf('.')
    if ($lastDot -gt 0) { $reportPackages[$fqcn.Substring(0, $lastDot)] = $true }
}

$missing = @($sourcePackages.Keys | Where-Object { -not $reportPackages.ContainsKey($_) } | Sort-Object)
$ratio = if ($sourceClasses.Count -gt 0) { $reports.Count / $sourceClasses.Count } else { 0 }

Write-Host "assert-test-suite-complete: $($reports.Count) report(s) for $($sourceClasses.Count) *Test.kt file(s) (ratio $([math]::Round($ratio, 2)))"

if ($missing.Count -gt 0 -or $ratio -lt $MinCoverageRatio) {
    Write-Host "assert-test-suite-complete: the run looks TRUNCATED - it did not cover the whole suite." -ForegroundColor Red
    if ($missing.Count -gt 0) {
        Write-Host "  packages with test sources but no reports:" -ForegroundColor Red
        $missing | ForEach-Object { Write-Host "    $_" }
    }
    if ($ratio -lt $MinCoverageRatio) {
        Write-Host "  coverage ratio $([math]::Round($ratio, 2)) is below the $MinCoverageRatio floor." -ForegroundColor Red
    }
    $why = 'assert-test-suite-complete: a Gradle "<N> tests completed" line is printed even when the ' +
    'worker JVM dies mid-run - treat this as a failed run, not as a result. Check the log for ' +
    'OutOfMemoryError and the test worker heap (testOptions.unitTests maxHeapSize).'
    Write-Error $why -ErrorAction Continue
    exit 1
}

Write-Host "assert-test-suite-complete: PASS - every package with test sources produced reports." -ForegroundColor Green
exit 0
