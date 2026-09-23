#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-log-redaction.ps1.

.DESCRIPTION
    The gate is pointed at a fixture tree and a fixture baseline, so every case below executes the
    real verdict path rather than asserting on a report line. A masking gate that has never refused
    anything cannot be told apart from one whose regexes stopped matching.

    Three properties are asserted: an unmasked credential interpolation is caught, the same call
    routed through SecretMasker passes, and a baseline line suppresses an existing finding while
    still refusing growth above it. The scope rule is asserted too, because a gate that silently
    scans nothing reports the same green as one that scanned everything.

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
$gate = Join-Path $repoRoot 'scripts/quality/assert-log-redaction.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("log-redaction-fixture-{0}" -f $PID)
$networkDir = Join-Path $fixtureRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/network'
$uiDir = Join-Path $fixtureRoot 'app_v2/src/main/java/com/sza/fastmediasorter/ui/settings'
$baseline = Join-Path $fixtureRoot 'baseline.txt'

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $networkDir -Force | Out-Null
    New-Item -ItemType Directory -Path $uiDir -Force | Out-Null
}

function Set-Baseline([int]$Value) {
    Set-Content -LiteralPath $baseline -Value "$Value" -Encoding UTF8
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
    Test-Case 'an unmasked credential interpolation is a finding' {
        Reset-Fixture
        Set-Baseline 0
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe {',
            '    fun connect(info: Info) {',
            '        Timber.d("Connecting as ${info.username}")',
            '    }',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "unmasked verdict - output: $($r.Output)"
    }

    Test-Case 'the same call routed through SecretMasker passes' {
        Reset-Fixture
        Set-Baseline 0
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe {',
            '    fun connect(info: Info) {',
            '        Timber.d("Connecting as ${SecretMasker.mask(info.username)}")',
            '    }',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "masked verdict - output: $($r.Output)"
    }

    Test-Case 'a wrapped Timber call is judged whole, not by its first line' {
        Reset-Fixture
        Set-Baseline 0
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe {',
            '    fun connect(info: Info) {',
            '        Timber.d(',
            '            "Connecting as ${info.password}"',
            '        )',
            '    }',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "wrapped-call verdict - output: $($r.Output)"
    }

    Test-Case 'a baseline at the current count suppresses the existing finding' {
        Reset-Fixture
        Set-Baseline 1
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe {',
            '    fun connect(info: Info) {',
            '        Timber.d("Connecting as ${info.username}")',
            '    }',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "baselined verdict - output: $($r.Output)"
    }

    Test-Case 'one more finding than the baseline still fails' {
        Reset-Fixture
        Set-Baseline 1
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe {',
            '    fun connect(info: Info) {',
            '        Timber.d("Connecting as ${info.username}")',
            '        Timber.d("Secret is ${info.password}")',
            '    }',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "growth verdict - output: $($r.Output)"
    }

    Test-Case 'a predicate or a length is not a credential value' {
        Reset-Fixture
        Set-Baseline 0
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe {',
            '    fun connect(info: Info) {',
            '        Timber.d("has user=${info.username.isNotBlank()} len=${info.password.length}")',
            '        Timber.d("row=${info.credentialsId} retries=$TOKEN_MAX_RETRY_ATTEMPTS")',
            '    }',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "non-value verdict - output: $($r.Output)"
    }

    Test-Case 'the same call outside the network trees is out of scope' {
        Reset-Fixture
        Set-Baseline 0
        Set-Content -LiteralPath (Join-Path $uiDir 'SettingsScreen.kt') -Encoding UTF8 -Value @(
            'class SettingsScreen {',
            '    fun show(info: Info) {',
            '        Timber.d("Connecting as ${info.username}")',
            '    }',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "out-of-scope verdict - output: $($r.Output)"
    }

    Test-Case 'the report names the file, the line and the expression' {
        Reset-Fixture
        Set-Baseline 0
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe {',
            '    fun connect(info: Info) {',
            '        Timber.d("Connecting as ${info.username}")',
            '    }',
            '}'
        )
        $r = Invoke-Report
        if ($r.Output -notmatch 'SmbProbe\.kt:3 info\.username') { throw "the report did not locate the finding - output: $($r.Output)" }
    }

    Test-Case 'the changed-set path counts a new occurrence' {
        Reset-Fixture
        Set-Baseline 0
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe { fun c(i: Info) { Timber.d("as ${i.username}") } }'
        )
        $r = Invoke-Scoped 'app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbProbe.kt'
        Assert-Equal 1 $r.ExitCode "scoped growth verdict - output: $($r.Output)"
    }

    Test-Case 'the changed-set path survives a changed file with no finding' {
        Reset-Fixture
        Set-Baseline 0
        Set-Content -LiteralPath (Join-Path $networkDir 'SmbProbe.kt') -Encoding UTF8 -Value @(
            'class SmbProbe { fun c() { Timber.d("connecting") } }'
        )
        $r = Invoke-Scoped 'app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbProbe.kt'
        Assert-Equal 0 $r.ExitCode "scoped clean verdict - output: $($r.Output)"
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertLogRedaction.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
