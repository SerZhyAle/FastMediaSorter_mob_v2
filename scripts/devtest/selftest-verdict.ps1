#requires -Version 7.0
<#
.SYNOPSIS
  S3741 - turn the JUnit XML of a device self-test run into one verdict.

.DESCRIPTION
  Reads every *.xml under each -ResultsDir (recursively), counts tests, failures, errors and skips per
  pass, lists every failed test with the first line of its message and every skipped test with its
  reason. The connected XML stores a skip without its message, so the reason comes from the pass's
  testrunner.log (the runner's own logcat lines, captured by run-device-selftest.ps1) when present.

  A skip is reported, never counted as a pass (a network test with no server proved nothing), and a
  run that produced no test at all is "could not verify", because a green verdict over zero tests is
  the one outcome that looks right while observing nothing.

  Exit codes:
    0 - at least one test ran and none failed or errored
    1 - at least one test failed or errored
    2 - could not verify: a results directory is missing, holds no JUnit XML, a pass did not complete
        (the instrumentation process died), or the run holds zero tests - and no test failed

.PARAMETER ResultsDir
  One or more directories, each holding one pass's JUnit XML. The directory name labels the pass.

.PARAMETER OutFile
  Optional path the JSON verdict is also written to.

.PARAMETER Json
  Print the JSON verdict instead of the human-readable summary.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/selftest-verdict.ps1 -ResultsDir temp/selftest/x/default,temp/selftest/x/hilt
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$ResultsDir,
    [string]$OutFile,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$verdict = [ordered]@{
    verdict = 'UNVERIFIED'
    tests = 0; passed = 0; failed = 0; skipped = 0
    passes = @(); failures = @(); skips = @(); problems = @()
}

# AndroidJUnitRunner logs "assumption failed: method(class)", then the trace between begin/end
# exception markers; the trace's first line carries the Assume message.
function Get-SkipReasons([string]$Path) {
    $reasons = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $reasons }
    $pending = $null
    $afterBegin = $false
    foreach ($line in Get-Content -LiteralPath $Path) {
        $text = $line -replace '^[A-Z]/TestRunner\(\s*\d+\):\s?', ''
        if ($text -match '^assumption failed: (.+)\(([^()]+)\)\s*$') {
            $pending = "$($Matches[2])#$($Matches[1])"
            $afterBegin = $false
        }
        elseif ($pending -and $text -match 'begin exception') {
            $afterBegin = $true
        }
        elseif ($pending -and $afterBegin -and $text.Trim()) {
            $reasons[$pending] = ($text -replace '^\S*(Exception|Error):\s*', '').Trim()
            $pending = $null
        }
    }
    return $reasons
}

foreach ($dir in $ResultsDir) {
    $label = Split-Path -Leaf $dir
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
        $verdict.problems += "results directory missing: $dir"
        continue
    }
    $files = @(Get-ChildItem -LiteralPath $dir -Recurse -File -Filter '*.xml')
    if ($files.Count -eq 0) {
        $verdict.problems += "no JUnit XML under $dir"
        continue
    }
    $pass = [ordered]@{ name = $label; tests = 0; failed = 0; skipped = 0 }
    $skipReasons = Get-SkipReasons (Join-Path $dir 'testrunner.log')
    foreach ($file in $files) {
        [xml]$doc = Get-Content -LiteralPath $file.FullName -Raw
        $systemErr = "$($doc.SelectSingleNode('//system-err')?.InnerText)"
        if ($systemErr -match 'Test run failed to complete') {
            $verdict.problems += "$label pass did not complete: $(($systemErr -split "`r?`n" | Select-Object -First 1).Trim())"
        }
        foreach ($case in $doc.SelectNodes('//testcase')) {
            $name = "$($case.classname)#$($case.name)"
            $pass.tests++
            $bad = $case.SelectSingleNode('failure') ?? $case.SelectSingleNode('error')
            $skip = $case.SelectSingleNode('skipped')
            if ($bad) {
                $pass.failed++
                $message = "$($bad.GetAttribute('message'))"
                if (-not $message) { $message = "$($bad.InnerText)" }
                $first = ($message -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -First 1)
                $verdict.failures += [ordered]@{ pass = $label; test = $name; message = "$first".Trim() }
            }
            elseif ($skip) {
                $pass.skipped++
                $reason = "$($skip.GetAttribute('message'))"
                if (-not $reason) { $reason = "$($skip.InnerText)".Trim() }
                if (-not $reason) { $reason = $skipReasons[$name] }
                if (-not $reason) { $reason = 'no reason in the report' }
                $verdict.skips += [ordered]@{ pass = $label; test = $name; reason = $reason }
            }
        }
    }
    $verdict.passes += $pass
    $verdict.tests += $pass.tests
    $verdict.failed += $pass.failed
    $verdict.skipped += $pass.skipped
}
$verdict.passed = $verdict.tests - $verdict.failed - $verdict.skipped

if ($verdict.tests -eq 0) { $verdict.problems += 'zero tests ran' }
# A failure stays a failure even beside a problem: a crash that aborts a pass is both.
$code = if ($verdict.failed -gt 0) { 1 } elseif ($verdict.problems.Count -gt 0) { 2 } else { 0 }
$verdict.verdict = switch ($code) { 0 { 'PASS' } 1 { 'FAIL' } default { 'UNVERIFIED' } }

$jsonText = $verdict | ConvertTo-Json -Depth 6
if ($OutFile) {
    $parent = Split-Path -Parent $OutFile
    if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    [System.IO.File]::WriteAllText($OutFile, $jsonText, [System.Text.UTF8Encoding]::new($false))
}
if ($Json) {
    $jsonText
}
else {
    foreach ($p in $verdict.passes) { Write-Host ("pass {0,-8} tests {1,4}  failed {2,3}  skipped {3,3}" -f $p.name, $p.tests, $p.failed, $p.skipped) }
    foreach ($f in $verdict.failures) { Write-Host "  FAIL [$($f.pass)] $($f.test) - $($f.message)" }
    foreach ($s in $verdict.skips) { Write-Host "  SKIP [$($s.pass)] $($s.test) - $($s.reason)" }
    foreach ($m in $verdict.problems) { Write-Host "  PROBLEM $m" }
    Write-Host ("selftest-verdict: {0} - {1} tests, {2} passed, {3} failed, {4} skipped" -f $verdict.verdict, $verdict.tests, $verdict.passed, $verdict.failed, $verdict.skipped)
}
exit $code
