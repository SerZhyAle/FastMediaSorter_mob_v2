#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-diagnostics-redaction.ps1.

.DESCRIPTION
    The gate is pointed at a fixture tree and a fixture baseline, so every case executes the real
    verdict path. The declared-surface rule gets its own cases in both directions: a payload named
    in the list is judged wherever it sits, and a look-alike that is not declared is not - a scope
    that silently narrowed would report the same green as a scan that found nothing.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test passed.
      1  at least one test failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:pass = 0
$script:fail = 0

function Test-Case([string]$Name, [scriptblock]$Body) {
    try {
        & $Body
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    catch {
        $script:fail++
        Write-Host "  FAIL  $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Assert-Equal($Expected, $Actual, [string]$What) {
    if ($Expected -ne $Actual) { throw "$What - expected: $Expected | actual: $Actual" }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gate = Join-Path $repoRoot 'scripts/quality/assert-diagnostics-redaction.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("diagnostics-redaction-fixture-{0}" -f $PID)
$modelDir = Join-Path $fixtureRoot 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model'
$loggingDir = Join-Path $fixtureRoot 'wear/src/main/java/com/sza/fastmediasorter/wear/core/logging'
$otherDir = Join-Path $fixtureRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/files'
$baseline = Join-Path $fixtureRoot 'baseline.txt'

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $modelDir -Force | Out-Null
    New-Item -ItemType Directory -Path $loggingDir -Force | Out-Null
    New-Item -ItemType Directory -Path $otherDir -Force | Out-Null
    Set-Content -LiteralPath $baseline -Value '0' -Encoding UTF8
}

function Invoke-Gate {
    $output = & $pwshExe -NoProfile -NonInteractive -File $gate -Gate -RepoRoot $fixtureRoot -BaselinePath $baseline 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

function Invoke-Report {
    $output = & $pwshExe -NoProfile -NonInteractive -File $gate -List -RepoRoot $fixtureRoot -BaselinePath $baseline 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

# The -ChangedFiles path is a SECOND verdict path, not a cheaper spelling of the first: it compares
# each changed file against its HEAD copy instead of counting the tree. It reached the closure
# untested once and crashed there, so it has its own cases below.
function Invoke-Scoped([string]$Changed) {
    $output = & $pwshExe -NoProfile -NonInteractive -File $gate -Gate -RepoRoot $fixtureRoot -BaselinePath $baseline -ChangedFiles $Changed 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

try {
    Test-Case 'an unmasked path field in a declared payload is a finding' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $modelDir 'WearLogReportPayload.kt') -Encoding UTF8 -Value @(
            'data class WearLogReportPayload(',
            '    val requestId: String,',
            '    val sourcePath: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "unmasked payload verdict - output: $($r.Output)"
    }

    Test-Case 'the same field named as masked passes' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $modelDir 'WearLogReportPayload.kt') -Encoding UTF8 -Value @(
            'data class WearLogReportPayload(',
            '    val requestId: String,',
            '    val maskedSourcePath: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "masked payload verdict - output: $($r.Output)"
    }

    Test-Case 'a readable diagnostic field is never path-shaped' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $modelDir 'WearLogReportPayload.kt') -Encoding UTF8 -Value @(
            'data class WearLogReportPayload(',
            '    val deviceModel: String,',
            '    val appVersionName: String,',
            '    val androidRelease: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "readable field verdict - output: $($r.Output)"
    }

    Test-Case 'the whole logging tree is in scope whatever the file is called' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $loggingDir 'WearCrashUploader.kt') -Encoding UTF8 -Value @(
            'data class CrashEnvelope(',
            '    val userEmail: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "logging tree verdict - output: $($r.Output)"
    }

    Test-Case 'an undeclared look-alike outside the trees is not judged' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $otherDir 'FileMoveReport.kt') -Encoding UTF8 -Value @(
            'data class FileMoveReport(',
            '    val sourcePath: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "undeclared surface verdict - output: $($r.Output)"
    }

    Test-Case 'the run reports how many surfaces it scanned' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $modelDir 'WearLogReportPayload.kt') -Encoding UTF8 -Value @(
            'data class WearLogReportPayload(val deviceModel: String)'
        )
        $r = Invoke-Report
        if ($r.Output -notmatch 'surfaces scanned 1') { throw "the run did not report its scanned surface count - output: $($r.Output)" }
    }

    Test-Case 'a baseline at the current count suppresses the existing finding' {
        Reset-Fixture
        Set-Content -LiteralPath $baseline -Value '1' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $modelDir 'WearLogReportPayload.kt') -Encoding UTF8 -Value @(
            'data class WearLogReportPayload(val sourcePath: String)'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "baselined verdict - output: $($r.Output)"
    }

    Test-Case 'one more finding than the baseline still fails' {
        Reset-Fixture
        Set-Content -LiteralPath $baseline -Value '1' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $modelDir 'WearLogReportPayload.kt') -Encoding UTF8 -Value @(
            'data class WearLogReportPayload(',
            '    val sourcePath: String,',
            '    val accountEmail: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "growth verdict - output: $($r.Output)"
    }

    Test-Case 'the changed-set path counts a new occurrence' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $modelDir 'WearLogReportPayload.kt') -Encoding UTF8 -Value @(
            'data class WearLogReportPayload(val sourcePath: String)'
        )
        $r = Invoke-Scoped 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearLogReportPayload.kt'
        Assert-Equal 1 $r.ExitCode "scoped growth verdict - output: $($r.Output)"
    }

    Test-Case 'the changed-set path survives a changed file with no finding' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $modelDir 'WearLogReportPayload.kt') -Encoding UTF8 -Value @(
            'data class WearLogReportPayload(val deviceModel: String)'
        )
        $r = Invoke-Scoped 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearLogReportPayload.kt'
        Assert-Equal 0 $r.ExitCode "scoped clean verdict - output: $($r.Output)"
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertDiagnosticsRedaction.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
