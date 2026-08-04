#requires -Version 7.0
<#
.SYNOPSIS
    S1332 regression suite for the `adb.ps1 log` line filter.

.DESCRIPTION
    Hermetic: drives scripts/devtest/lib/adb-log-filter.ps1 against a recorded threadtime
    fixture. No adb call, no device, no network, no writes.

    The case that matters is the last one. It reproduces the historical text-only rule inline
    and asserts it suppressed the reported line while the current rule does not, so the suite
    goes red the moment anyone reinstates a text pre-filter.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/adb-log-filter.tests/Run-Tests.ps1

    Exit codes:
      0 - every case passed
      1 - at least one case failed
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
. (Join-Path $repoRoot 'scripts/devtest/lib/adb-log-filter.ps1')

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    $e = ($Expected | Out-String).Trim()
    $a = ($Actual   | Out-String).Trim()
    if ($e -eq $a) {
        Write-Host "PASS | $Label"
        $script:passed++
    } else {
        Write-Host "FAIL | $Label -> expected: $e | actual: $a"
        $script:failed++
    }
}

$fixture = Join-Path $PSScriptRoot 'fixtures/logcat_threadtime_sample.txt'
$lines = Get-Content $fixture
$basePackage = 'com.sza.fastmediasorter'
$pkg = "$basePackage.debug"
# The same three entries adb.ps1 passes as -TextPatterns.
$textPatterns = @([regex]::Escape($pkg), [regex]::Escape($basePackage), 'FastMediaSorter')
$appPids = @(24469)

Assert-Equal 8 $lines.Count 'fixture holds all eight decision rows'

# The pid is recoverable from the Start proc line alone - the path used when the process has
# already exited and neither pidof nor ps can answer.
Assert-Equal @(24469) (Get-AppPidsFromLog -Lines $lines -BasePackage $basePackage) `
    'Get-AppPidsFromLog recovers the app pid from the capture'

$kept = Select-AppLogLines -Lines $lines -AppPids $appPids -TextPatterns $textPatterns

# Assert on presence, not only on count: a count assertion still passes if the wrong row survived.
Assert-Equal $true ([bool]($kept | Where-Object { $_ -match 'AudioWaveParticleView' })) `
    'keeps the reported Timber probe under a bare class tag'
Assert-Equal $true ([bool]($kept | Where-Object { $_ -match 'MainViewModel: boom' })) `
    'keeps an app error under a bare class tag'
Assert-Equal 2 (@($kept | Where-Object { $_ -match 'AndroidRuntime' }).Count) `
    'keeps both crash rows, including the header that names no package'
Assert-Equal $true ([bool]($kept | Where-Object { $_ -match 'Start proc' })) `
    'text arm keeps Start proc under system_server pid'
Assert-Equal $true ([bool]($kept | Where-Object { $_ -match 'ANR in' })) `
    'text arm keeps ANR under system_server pid'
Assert-Equal $false ([bool]($kept | Where-Object { $_ -match 'Finsky' })) `
    'drops another app pid'

# Re-adding the separator would make prerelease-prepare.ps1 report a crash on any device
# whose crash buffer is not empty - it greps this output for the separator text.
Assert-Equal $false ([bool]($kept | Where-Object { $_ -match 'beginning of crash' })) `
    'drops the buffer separator - prerelease crash scan depends on this'

Assert-Equal 6 $kept.Count 'keeps exactly the six app-owned or app-naming rows'

# The defect itself, stated as an assertion. Historical rule under test - NOT live code.
$oldRuleKept = @($lines | Where-Object { $line = $_; @($textPatterns | Where-Object { $line -match $_ }).Count -gt 0 })

$oldCoverage = Measure-FilterCoverage -RawLines $lines -KeptLines $oldRuleKept -Pattern 'S1277'
$newCoverage = Measure-FilterCoverage -RawLines $lines -KeptLines $kept -Pattern 'S1277'

Assert-Equal 1 $oldCoverage.suppressed 'the historical text-only rule swallowed the reported line'
Assert-Equal 0 $newCoverage.suppressed 'the current rule swallows nothing the pattern matched'

Write-Host ''
Write-Host "adb-log-filter: $script:passed passed, $script:failed failed"
if ($script:failed -gt 0) { exit 1 }
exit 0
