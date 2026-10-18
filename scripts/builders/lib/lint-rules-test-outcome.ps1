#requires -Version 7.0
. (Join-Path $PSScriptRoot '../gradle-run-verdict.ps1')

function Get-LintRulesSuiteVerdict {
    [CmdletBinding()]
    param(
        [string[]]$ReportPaths = @(),
        [int]$GradleExitCode = 0,
        [string[]]$GradleOutput = @(),
        [datetime]$StartedUtc = [datetime]::MinValue
    )

    $reasons = [System.Collections.Generic.List[string]]::new()
    $run = 0
    $skipped = 0
    $failed = 0
    $processFailed = $GradleExitCode -eq 1
    if ($GradleExitCode -ne 0 -and
        ($GradleExitCode -ne 1 -or (Test-GradleWorkerDeath -Lines $GradleOutput))) {
        return [pscustomobject]@{
            Code = 2; Word = 'COULD NOT VERIFY'; Run = 0; Skipped = 0
            Reasons = @("Gradle did not produce a reliable verdict (exit $GradleExitCode)")
        }
    }
    if ($processFailed) { $reasons.Add('Gradle reported a build or test failure') }
    if ($ReportPaths.Count -eq 0) { $reasons.Add('no test-result XML was produced') }
    foreach ($path in $ReportPaths) {
        try {
            # A failed compilation can leave an earlier successful test report on disk.
            if ($processFailed -and (Test-Path -LiteralPath $path) -and
                (Get-Item -LiteralPath $path).LastWriteTimeUtc -lt $StartedUtc) {
                $reasons.Add("${path}: report predates the failed Gradle invocation")
                continue
            }
            $report = Get-JUnitSuiteOutcome -ReportPath $path
            if ($report.Tests -lt 0 -or $report.Skipped -lt 0 -or $report.Failures -lt 0 -or
                $report.Errors -lt 0 -or
                ($report.Skipped + $report.Failures + $report.Errors) -gt $report.Tests) {
                $reasons.Add("${path}: inconsistent test counters")
                continue
            }
            $run += $report.Tests - $report.Skipped
            $skipped += $report.Skipped
            $failed += $report.Failures + $report.Errors
            if (-not $report.Executed) { $reasons.Add("${path}: $($report.Reason)") }
            elseif ($report.Skipped -gt 0) { $reasons.Add("${path}: $($report.Skipped) declared test(s) skipped") }
        } catch {
            $reasons.Add("${path}: unreadable test counters ($($_.Exception.Message))")
        }
    }
    $code = if ($processFailed -or $failed -gt 0) { 1 }
        elseif ($reasons.Count -gt 0 -or $run -eq 0 -or $skipped -gt 0) { 2 }
        else { 0 }
    $word = switch ($code) { 0 { 'PASS' }; 1 { 'FAIL' }; 2 { 'COULD NOT VERIFY' } }
    return [pscustomobject]@{ Code = $code; Word = $word; Run = $run; Skipped = $skipped; Reasons = @($reasons) }
}
