#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-credential-encryption.ps1.

.DESCRIPTION
    The gate is pointed at a fixture tree and a fixture baseline, so every case executes the real
    verdict path. The cases that matter most are the two CREDITS - a ciphertext-named column and a
    derived property - because a credit that fires too widely turns the gate green everywhere, and
    the derived-property case was already the one that mis-read its own line numbers once.

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
$gate = Join-Path $repoRoot 'scripts/quality/assert-credential-encryption.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("credential-encryption-fixture-{0}" -f $PID)
$dbDir = Join-Path $fixtureRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db'
$uiDir = Join-Path $fixtureRoot 'app_v2/src/main/java/com/sza/fastmediasorter/ui/network'
$baseline = Join-Path $fixtureRoot 'baseline.txt'

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $dbDir -Force | Out-Null
    New-Item -ItemType Directory -Path $uiDir -Force | Out-Null
    Set-Content -LiteralPath $baseline -Value '0' -Encoding UTF8
}

function Set-Entity([string]$Directory, [string[]]$Body) {
    Set-Content -LiteralPath (Join-Path $Directory 'SampleEntity.kt') -Value $Body -Encoding UTF8
}

function Invoke-Gate {
    $output = & $pwshExe -NoProfile -NonInteractive -File $gate -Gate -RepoRoot $fixtureRoot -BaselinePath $baseline 2>&1 | Out-String
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
    Test-Case 'a plain String password column is a finding' {
        Reset-Fixture
        Set-Entity $dbDir @(
            '@Entity(tableName = "sample")',
            'data class SampleEntity(',
            '    @PrimaryKey val id: Long = 0,',
            '    val password: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "plaintext column verdict - output: $($r.Output)"
    }

    Test-Case 'a ciphertext-named column passes' {
        Reset-Fixture
        Set-Entity $dbDir @(
            '@Entity(tableName = "sample")',
            'data class SampleEntity(',
            '    @PrimaryKey val id: Long = 0,',
            '    val encryptedPassword: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "ciphertext column verdict - output: $($r.Output)"
    }

    Test-Case 'a derived get() property is not a stored column' {
        Reset-Fixture
        Set-Entity $dbDir @(
            '@Entity(tableName = "sample")',
            'data class SampleEntity(',
            '    @PrimaryKey val id: Long = 0,',
            '    val encryptedPassword: String',
            ') {',
            '    @get:Ignore',
            '    val password: String',
            '        get() = CryptoHelper.decrypt(encryptedPassword).orEmpty()',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "derived property verdict - output: $($r.Output)"
    }

    Test-Case 'a credential id is not a credential' {
        Reset-Fixture
        Set-Entity $dbDir @(
            '@Entity(tableName = "sample")',
            'data class SampleEntity(',
            '    @PrimaryKey val id: Long = 0,',
            '    val credentialsId: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "identifier verdict - output: $($r.Output)"
    }

    Test-Case 'the same field in a view model is out of scope' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $uiDir 'NetworkFormState.kt') -Encoding UTF8 -Value @(
            'data class NetworkFormState(',
            '    val password: String = ""',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "out-of-scope verdict - output: $($r.Output)"
    }

    Test-Case 'an entity declared outside the persistence tree is still judged' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $uiDir 'StrayEntity.kt') -Encoding UTF8 -Value @(
            '@Entity(tableName = "stray")',
            'data class StrayEntity(',
            '    val apiKey: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "stray entity verdict - output: $($r.Output)"
    }

    Test-Case 'a baseline at the current count suppresses the existing finding' {
        Reset-Fixture
        Set-Content -LiteralPath $baseline -Value '1' -Encoding UTF8
        Set-Entity $dbDir @(
            '@Entity(tableName = "sample")',
            'data class SampleEntity(',
            '    val password: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "baselined verdict - output: $($r.Output)"
    }

    Test-Case 'one more finding than the baseline still fails' {
        Reset-Fixture
        Set-Content -LiteralPath $baseline -Value '1' -Encoding UTF8
        Set-Entity $dbDir @(
            '@Entity(tableName = "sample")',
            'data class SampleEntity(',
            '    val password: String,',
            '    val refreshToken: String',
            ')'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode "growth verdict - output: $($r.Output)"
    }

    Test-Case 'the changed-set path counts a new occurrence' {
        Reset-Fixture
        Set-Entity $dbDir @(
            '@Entity(tableName = "sample")',
            'data class SampleEntity(val password: String)'
        )
        $r = Invoke-Scoped 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/SampleEntity.kt'
        Assert-Equal 1 $r.ExitCode "scoped growth verdict - output: $($r.Output)"
    }

    Test-Case 'the changed-set path survives a changed file with no finding' {
        Reset-Fixture
        Set-Entity $dbDir @(
            '@Entity(tableName = "sample")',
            'data class SampleEntity(val encryptedPassword: String)'
        )
        $r = Invoke-Scoped 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/SampleEntity.kt'
        Assert-Equal 0 $r.ExitCode "scoped clean verdict - output: $($r.Output)"
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertCredentialEncryption.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
