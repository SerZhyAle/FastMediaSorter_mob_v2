#requires -Version 7.0
# Subject: scripts/builders/lib/lint-rules-test-outcome.ps1, scripts/builders/gradle-run-verdict.ps1
# Exit codes: 0 = all assertions passed; 1 = an assertion failed; 2 = fixture/harness unavailable.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
trap {
    $passVariable = Get-Variable -Name passed -Scope Script -ErrorAction SilentlyContinue
    $failVariable = Get-Variable -Name failed -Scope Script -ErrorAction SilentlyContinue
    $passCount = if ($null -ne $passVariable) { [int]$passVariable.Value } else { 0 }
    $failCount = if ($null -ne $failVariable) { [int]$failVariable.Value } else { 0 }
    $word = if ($failCount) { 'FAIL' } else { 'COULD NOT VERIFY' }
    Write-Host "  Fixture/harness error: $($_.Exception.Message)"
    Write-Host "check-lint-rules.tests: $word (run=$($passCount + $failCount) skipped=0)"
    exit $(if ($failCount) { 1 } else { 2 })
}
. (Join-Path $PSScriptRoot '../lib/lint-rules-test-outcome.ps1')
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$sandbox = [IO.Path]::GetFullPath((Join-Path $repoRoot "temp/scratch/lint-rules-verdict-tests-$PID"))
$safeRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'temp/scratch')) + [IO.Path]::DirectorySeparatorChar
if (-not $sandbox.StartsWith($safeRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe fixture root' }
New-Item -ItemType Directory -Path $sandbox -Force | Out-Null
$passed = 0
$failed = 0
function Assert-That([string]$Name, [bool]$Condition, [string]$Detail) {
    if ($Condition) { $script:passed++; Write-Host "  PASS $Name" }
    else { $script:failed++; Write-Host "  FAIL $Name - $Detail" }
}
function New-Report([string]$Name, [int]$Tests, [int]$Skipped, [int]$Failures = 0, [int]$Errors = 0) {
    $path = Join-Path $sandbox "$Name.xml"
    [IO.File]::WriteAllText($path, "<testsuite tests='$Tests' skipped='$Skipped' failures='$Failures' errors='$Errors'/>")
    return $path
}
try {
    $good = New-Report 'good' 3 0
    $red = New-Report 'red' 3 0 1
    $partial = New-Report 'partial' 3 1
    $allSkipped = New-Report 'all-skipped' 3 3
    $empty = New-Report 'empty' 0 0
    $negative = New-Report 'negative' 3 -1
    $negativeFailure = New-Report 'negative-failure' 3 0 -1
    $inconsistent = New-Report 'inconsistent' 3 3 1
    $bad = Join-Path $sandbox 'bad.xml'
    [IO.File]::WriteAllText($bad, '<broken')
    $missing = Join-Path $sandbox 'missing.xml'
    $cases = @(
        @{Name='pass';Paths=@($good);Code=0;Run=3;Skip=0},
        @{Name='assertion failure';Paths=@($red);Code=1;Run=3;Skip=0},
        @{Name='declared skip';Paths=@($partial);Code=2;Run=2;Skip=1},
        @{Name='all skipped';Paths=@($allSkipped);Code=2;Run=0;Skip=3},
        @{Name='zero tests';Paths=@($empty);Code=2;Run=0;Skip=0},
        @{Name='no reports';Paths=@();Code=2;Run=0;Skip=0},
        @{Name='missing report';Paths=@($missing);Code=2;Run=0;Skip=0},
        @{Name='malformed XML';Paths=@($bad);Code=2;Run=0;Skip=0},
        @{Name='invalid counters';Paths=@($negative);Code=2;Run=0;Skip=0},
        @{Name='negative failures';Paths=@($negativeFailure);Code=2;Run=0;Skip=0},
        @{Name='inconsistent totals';Paths=@($inconsistent);Code=2;Run=0;Skip=0},
        @{Name='failure outranks unverified';Paths=@($red,$bad);Code=1;Run=3;Skip=0}
    )
    foreach ($case in $cases) {
        $result = Get-LintRulesSuiteVerdict -ReportPaths $case.Paths
        Assert-That "$($case.Name) maps its verdict" ($result.Code -eq $case.Code) ($result | ConvertTo-Json -Compress)
        Assert-That "$($case.Name) excludes skips from run" ($result.Run -eq $case.Run -and $result.Skipped -eq $case.Skip) ($result | ConvertTo-Json -Compress)
    }
    foreach ($exitCode in @(1,4,7,130)) {
        $result = Get-LintRulesSuiteVerdict -GradleExitCode $exitCode
        $expected = if ($exitCode -eq 1) { 1 } else { 2 }
        Assert-That "Gradle exit $exitCode is mapped deliberately" ($result.Code -eq $expected) ($result | ConvertTo-Json -Compress)
    }
    $death = Get-LintRulesSuiteVerdict -GradleExitCode 1 -GradleOutput @('Test process encountered an unexpected problem')
    Assert-That 'worker death cannot verify' ($death.Code -eq 2) ($death | ConvertTo-Json -Compress)
    $stale = Get-LintRulesSuiteVerdict -ReportPaths @($good) -GradleExitCode 1 -StartedUtc ([datetime]::UtcNow.AddMinutes(1))
    Assert-That 'failed Gradle run does not count an earlier successful report' ($stale.Code -eq 1 -and $stale.Run -eq 0) ($stale | ConvertTo-Json -Compress)

    $wrapperCases = @(
        @{Name='pass';Gradle=0;Tests=3;Skip=0;Failures=0;Code=0;Run=3},
        @{Name='skip';Gradle=0;Tests=3;Skip=1;Failures=0;Code=2;Run=2},
        @{Name='report-failure';Gradle=0;Tests=3;Skip=0;Failures=1;Code=1;Run=3},
        @{Name='gradle-failure';Gradle=1;Tests=3;Skip=0;Failures=1;Code=1;Run=3},
        @{Name='unexpected-exit';Gradle=7;Tests=-1;Skip=0;Failures=0;Code=2;Run=0},
        @{Name='worker-death';Gradle=1;Tests=-1;Skip=0;Failures=0;Code=2;Run=0},
        @{Name='missing-reports';Gradle=0;Tests=-1;Skip=0;Failures=0;Code=2;Run=0}
        @{Name='release-unverified';Gradle=0;Tests=3;Skip=0;Failures=0;Code=2;Run=3;LockError=$true},
        @{Name='failure-before-release-error';Gradle=0;Tests=3;Skip=0;Failures=1;Code=1;Run=3;LockError=$true},
        @{Name='missing-library';Gradle=0;Tests=-1;Skip=0;Failures=0;Code=2;Run=0;MissingLibrary=$true}
    )
    foreach ($case in $wrapperCases) {
        $project = Join-Path $sandbox $case.Name
        foreach ($dir in @('scripts/builders/lib','scripts/utils','scripts/quality/lib','lint-rules/build/test-results/test')) {
            New-Item -ItemType Directory -Path (Join-Path $project $dir) -Force | Out-Null
        }
        foreach ($file in @('check-lint-rules.ps1','gradle-run-verdict.ps1','lib/lint-rules-test-outcome.ps1')) {
            if ($case.ContainsKey('MissingLibrary') -and $file -eq 'lib/lint-rules-test-outcome.ps1') { continue }
            Copy-Item -LiteralPath (Join-Path $PSScriptRoot "../$file") -Destination (Join-Path $project "scripts/builders/$file")
        }
        $releaseBody = if ($case.ContainsKey('LockError')) {
            "Write-Host 'fixture lock release attempted'; throw 'fixture release failed'"
        } else { "Write-Host 'fixture lock released'" }
        $lockStub = 'function Enter-BuildLockOrExit { param($Reason) }' + "`n" +
            'function Exit-AgentLock { param($Name) ' + $releaseBody + ' }'
        [IO.File]::WriteAllText((Join-Path $project 'scripts/utils/agent-lock.ps1'), $lockStub)
        [IO.File]::WriteAllText((Join-Path $project 'scripts/quality/lib/check-subject.ps1'), @'
function Write-CheckSubject { param($Axes) Write-Host 'fixture subject' }
'@)
        $producer = ''
        if ($case.Tests -ge 0) {
            $xml = "<testsuite tests='$($case.Tests)' skipped='$($case.Skip)' failures='$($case.Failures)' errors='0'/>"
            $producer = "[IO.File]::WriteAllText((Join-Path `$PSScriptRoot 'lint-rules/build/test-results/test/TEST-fixture.xml'), '$($xml.Replace("'", "''"))')"
        }
        if ($case.Name -eq 'worker-death') { $producer += "`nWrite-Host 'Test process encountered an unexpected problem'" }
        [IO.File]::WriteAllText((Join-Path $project 'write-results.ps1'), $producer)
        $batch = "@echo off`r`npwsh -NoProfile -File `"%~dp0write-results.ps1`"`r`nexit /b $($case.Gradle)`r`n"
        [IO.File]::WriteAllText((Join-Path $project 'gradlew.bat'), $batch)
        $output = @(& pwsh -NoProfile -File (Join-Path $project 'scripts/builders/check-lint-rules.ps1') 2>&1)
        $code = $LASTEXITCODE
        $tail = [string]$output[-1]
        Assert-That "wrapper $($case.Name) maps its exit" ($code -eq $case.Code) ($output -join ' | ')
        Assert-That "wrapper $($case.Name) writes canonical counts last" ($tail -match "^check-lint-rules: .+ \(run=$($case.Run) skipped=$($case.Skip)\)$") $tail
        if ($case.ContainsKey('MissingLibrary')) {
            Assert-That 'missing library never acquired the lock' (($output -join ' | ') -notmatch 'fixture lock release') ($output -join ' | ')
        } else {
            $marker = if ($case.ContainsKey('LockError')) { 'fixture lock release attempted' } else { 'fixture lock released' }
            $releaseIndex = [array]::IndexOf([string[]]$output, $marker)
            Assert-That "wrapper $($case.Name) releases before its verdict" ($releaseIndex -ge 0 -and $releaseIndex -lt $output.Count - 1) ($output -join ' | ')
        }
    }
}
finally {
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
}
Write-Host "check-lint-rules.tests: $(if ($failed) { 'FAIL' } else { 'PASS' }) (run=$($passed + $failed) skipped=0)"
exit $(if ($failed) { 1 } else { 0 })
